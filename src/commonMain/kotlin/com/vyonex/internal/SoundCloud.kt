package com.vyonex.internal

import com.vyonex.SoundCloudPlaylist
import com.vyonex.SoundCloudProtocol
import com.vyonex.SoundCloudStream
import com.vyonex.SoundCloudTrack
import com.vyonex.SoundCloudTranscoding
import com.vyonex.VyonExHostServices
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

// SoundCloud as yt-dlp 2026.10.26 reads it (extractor/soundcloud.py: SoundcloudBaseIE, SoundcloudIE,
// SoundcloudPlaylistBaseIE).

internal const val SOUNDCLOUD_API = "https://api-v2.soundcloud.com"
private const val CLIENT_ID_KEY = "soundcloud.clientId"

private val JsonElement?.items get() = (this as? JsonArray).orEmpty()

// The pages resolve accepts: soundcloud.com with www. or m., a path, no credentials or port.
private val SOUNDCLOUD_PAGE = Regex("""https://(?:www\.|m\.)?soundcloud\.com/[\w-]+(?:/[^\s?#]*)?(?:\?[^\s#]*)?""")
private val SCRIPT_SRC = Regex("""<script[^>]+src="(https://a-v2\.sndcdn\.com/assets/[^"]+\.js)"""")
private val CLIENT_ID = Regex("""client_id\s*:\s*"([0-9a-zA-Z]{32})"""")
// Signed stream URLs come from SoundCloud's own media hosts.
private val STREAM_URL = Regex("""https://[\w.-]+\.(?:soundcloud\.cloud|sndcdn\.com)/\S+""")
// yt-dlp _IMAGE_REPL_RE / _ARTWORK_MAP: the size is a name before the extension; SoundCloud serves JPEG for all but "original".
private val ARTWORK_SIZE = Regex("""-[0-9a-z]+\.(?:jpg|png)$""")

internal fun isSoundCloudPage(url: String) = SOUNDCLOUD_PAGE.matches(url)

internal fun isAllowedSoundCloudStream(url: String) = STREAM_URL.matches(url)

/** The 500×500 artwork of a track or set, else its uploader's picture. */
private fun artworkOf(info: JsonElement?) =
    (info.at("artwork_url").str ?: info.at("user", "avatar_url").str)?.replace(ARTWORK_SIZE, "-t500x500.jpg")

/**
 * What a track's transcodings offer (yt-dlp _extract_info_dict): encrypted ones (`ctr-`, `cbc-` and `encrypted-hls`)
 * are DRM and left out, `abr_*` presets yt-dlp finds broken, and a `/hls` URL is HLS whatever it says.
 */
internal fun soundCloudTrackOf(info: JsonElement?): SoundCloudTrack? {
    val id = info.at("id").str?.toLongOrNull() ?: return null
    var hasDrm = false
    val transcodings = info.at("media", "transcodings").items.mapNotNull { t ->
        val url = t.at("url").str?.takeIf { it.startsWith("$SOUNDCLOUD_API/media/") } ?: return@mapNotNull null
        val preset = t.at("preset").str ?: return@mapNotNull null
        val protocol = t.at("format", "protocol").str ?: "progressive"
        if (protocol.startsWith("ctr-") || protocol.startsWith("cbc-") || protocol == "encrypted-hls" || "/encrypted-hls" in url) {
            hasDrm = true
            return@mapNotNull null
        }
        if (preset.substringBefore('_') == "abr") return@mapNotNull null
        SoundCloudTranscoding(
            preset = preset,
            protocol = if (protocol == "hls" || "/hls" in url) SoundCloudProtocol.HLS else SoundCloudProtocol.PROGRESSIVE,
            mimeType = t.at("format", "mime_type").str,
            isPreview = t.at("snipped").bool == true || "/preview/" in url,
            url = url,
            secretToken = info.at("secret_token").str,
        )
    }
    return SoundCloudTrack(
        id = id,
        title = info.at("title").str,
        uploader = info.at("user", "username").str,
        uploaderUrl = info.at("user", "permalink_url").str,
        artist = info.at("publisher_metadata", "artist").str?.takeIf { it.isNotBlank() },
        durationMs = info.at("duration").str?.toLongOrNull(),
        artworkUrl = artworkOf(info),
        permalinkUrl = info.at("permalink_url").str,
        genre = info.at("genre").str?.takeIf { it.isNotBlank() },
        isBlocked = info.at("policy").str == "BLOCK",
        hasDrm = hasDrm,
        transcodings = transcodings,
    )
}

/** A set; its tracks without details (all but the first few) are filled in from [details], by ID. */
internal fun soundCloudPlaylistOf(set: JsonElement?, details: (String) -> JsonElement?): SoundCloudPlaylist? = SoundCloudPlaylist(
    id = set.at("id").str?.toLongOrNull() ?: return null,
    title = set.at("title").str,
    uploader = set.at("user", "username").str,
    setType = set.at("set_type").str?.takeIf { it.isNotBlank() } ?: "playlist",
    artworkUrl = artworkOf(set),
    permalinkUrl = set.at("permalink_url").str,
    tracks = set.at("tracks").items.mapNotNull { t ->
        soundCloudTrackOf(if (t.at("permalink_url") != null) t else t.at("id").str?.let(details))
    },
)

/** The tracks of a set that came without details, by ID. */
internal fun bareTrackIdsOf(set: JsonElement?) = set.at("tracks").items.filter { it.at("permalink_url") == null }.mapNotNull { it.at("id").str }

@OptIn(ExperimentalAtomicApi::class)
internal class SoundCloudEngine(private val host: VyonExHostServices, private val http: HttpClient) {
    private val clientId = AtomicReference<String?>(null)

    suspend fun track(url: String): SoundCloudTrack? = resolve(url)?.takeIf { it.at("kind").str == "track" }?.let(::soundCloudTrackOf)

    suspend fun playlist(url: String): SoundCloudPlaylist? {
        val set = resolve(url)?.takeIf { it.at("kind").str == "playlist" } ?: return null
        // SoundCloud's web client asks for the rest of a set in batches; 50 IDs keep the URL short.
        val details = bareTrackIdsOf(set).chunked(50)
            .flatMap { ids -> api("$SOUNDCLOUD_API/tracks", listOf("ids" to ids.joinToString(","))).items }
            .associateBy { it.at("id").str }
        return soundCloudPlaylistOf(set) { details[it] }
    }

    suspend fun stream(transcoding: SoundCloudTranscoding): SoundCloudStream {
        require(transcoding.url.startsWith("$SOUNDCLOUD_API/media/")) { "VyonEx: not a SoundCloud transcoding" }
        val url = api(transcoding.url, listOfNotNull(transcoding.secretToken?.let { "secret_token" to it })).at("url").str
        check(url != null && isAllowedSoundCloudStream(url)) { "VyonEx: SoundCloud gave no stream" }
        return SoundCloudStream(url, transcoding.protocol, transcoding.mimeType, transcoding.isPreview)
    }

    /** What a page link is (`resolve`), or null when SoundCloud has nothing there. */
    private suspend fun resolve(url: String): JsonElement? {
        require(isSoundCloudPage(url)) { "VyonEx: not a SoundCloud link" }
        return api("$SOUNDCLOUD_API/resolve", listOf("url" to url), missingIsNull = true)
    }

    /**
     * A logged-out API call with the web client's ID. A 401 or 403 means SoundCloud changed the ID: it is looked up
     * again and the call made once more (yt-dlp _call_api). Messages carry the status only, never the URL.
     */
    private suspend fun api(url: String, query: List<Pair<String, String>>, missingIsNull: Boolean = false): JsonElement? {
        for (refresh in listOf(false, true)) {
            val params = (query + ("client_id" to clientId(refresh))).joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" }
            val response = http.get(url + (if ('?' in url) "&" else "?") + params) { header(HttpHeaders.UserAgent, DESKTOP_UA) }
            val status = response.status
            if (!refresh && (status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden)) continue
            if (missingIsNull && status == HttpStatusCode.NotFound) return null
            check(status.isSuccess()) { "VyonEx: SoundCloud HTTP ${status.value}" }
            return Json.parseToJsonElement(response.bodyAsText())
        }
        error("unreachable")
    }

    private suspend fun clientId(refresh: Boolean): String {
        val store = host.configStore
        if (!refresh) {
            clientId.load()?.let { return it }
            store.getString(CLIENT_ID_KEY)?.takeIf { store.enabled }?.let { clientId.store(it); return it }
        }
        val found = findClientId() ?: error("VyonEx: no SoundCloud client ID in its web page")
        clientId.store(found)
        if (store.enabled) store.putString(CLIENT_ID_KEY, found)
        return found
    }

    /** yt-dlp _update_client_id: the ID in SoundCloud's own scripts on its home page, the last scripts first. */
    private suspend fun findClientId(): String? {
        val home = http.text("https://soundcloud.com/") { header(HttpHeaders.UserAgent, DESKTOP_UA) }
        for (src in SCRIPT_SRC.findAll(home).map { it.groupValues[1] }.toList().asReversed()) {
            val script = try {
                http.text(src) { header(HttpHeaders.UserAgent, DESKTOP_UA) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                continue
            }
            CLIENT_ID.find(script)?.let { return it.groupValues[1] }
        }
        return null
    }
}
