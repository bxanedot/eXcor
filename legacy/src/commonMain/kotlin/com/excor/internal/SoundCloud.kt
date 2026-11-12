package com.excor.internal

import com.excor.SoundCloudPlaylist
import com.excor.SoundCloudProtocol
import com.excor.SoundCloudStream
import com.excor.SoundCloudTrack
import com.excor.SoundCloudTranscoding
import com.excor.ExcorHostServices
import com.excor.ExcorSink
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

// SoundCloud as yt-dlp 2026.10.26 reads it (extractor/soundcloud.py: SoundcloudBaseIE, SoundcloudIE,
// SoundcloudPlaylistBaseIE).

internal const val SOUNDCLOUD_API = "https://api-v2.soundcloud.com"
private const val CLIENT_ID_KEY = "soundcloud.clientId"
private const val COPY_BYTES = 256L * 1024

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
internal class SoundCloudEngine(private val host: ExcorHostServices, private val http: HttpClient) {
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
        require(transcoding.url.startsWith("$SOUNDCLOUD_API/media/")) { "Excor: not a SoundCloud transcoding" }
        val url = api(transcoding.url, listOfNotNull(transcoding.secretToken?.let { "secret_token" to it })).at("url").str
        check(url != null && isAllowedSoundCloudStream(url)) { "Excor: SoundCloud gave no stream" }
        return SoundCloudStream(url, transcoding.protocol, transcoding.mimeType, transcoding.isPreview)
    }

    /**
     * Writes [transcoding] to [sink] as one file, from a fresh stream: a progressive one as it comes, an HLS one as its
     * init segment and then its segments in order (for fragmented MP4, an `.m4a` file). [onProgress] runs from 0 to 1:
     * by bytes, or by segments for HLS. Returns the bytes written. Every URL must be on SoundCloud's media hosts.
     */
    suspend fun download(transcoding: SoundCloudTranscoding, sink: ExcorSink, onProgress: ((Float) -> Unit)?): Long {
        val stream = stream(transcoding)
        if (stream.protocol == SoundCloudProtocol.PROGRESSIVE) return copy(stream.url, sink, onProgress)
        val playlist = mediaPlaylistOf(http.text(stream.url) { header(HttpHeaders.UserAgent, DESKTOP_UA) }, stream.url)
        val parts = listOfNotNull(playlist.init) + playlist.segments
        check(parts.all(::isAllowedSoundCloudStream)) { "Excor: SoundCloud's playlist points elsewhere" }
        var written = 0L
        parts.forEachIndexed { i, part ->
            written += copy(part, sink, null)
            onProgress?.invoke((i + 1).toFloat() / parts.size)
        }
        return written
    }

    /** Streams [url] into [sink] in pieces, so a long file is never held whole. */
    private suspend fun copy(url: String, sink: ExcorSink, onProgress: ((Float) -> Unit)?): Long =
        http.prepareGet(url) { header(HttpHeaders.UserAgent, DESKTOP_UA) }.execute { response ->
            check(response.status == HttpStatusCode.OK) { "Excor: SoundCloud stream HTTP ${response.status.value}" }
            val total = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            val channel = response.bodyAsChannel()
            var written = 0L
            while (!channel.isClosedForRead) {
                val piece = channel.readBuffer(COPY_BYTES).readByteArray()
                if (piece.isEmpty()) continue
                sink.write(piece)
                written += piece.size
                if (total != null && total > 0) onProgress?.invoke((written.toFloat() / total).coerceAtMost(1f))
            }
            check(total == null || written == total) { "Excor: SoundCloud stream ended early" }
            written
        }

    /** What a page link is (`resolve`), or null when SoundCloud has nothing there. */
    private suspend fun resolve(url: String): JsonElement? {
        require(isSoundCloudPage(url)) { "Excor: not a SoundCloud link" }
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
            check(status.isSuccess()) { "Excor: SoundCloud HTTP ${status.value}" }
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
        val found = findClientId() ?: error("Excor: no SoundCloud client ID in its web page")
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
