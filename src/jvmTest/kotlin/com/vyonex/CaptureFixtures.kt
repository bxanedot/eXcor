package com.vyonex

import com.vyonex.internal.DESKTOP_UA
import com.vyonex.internal.IDENTITIES
import com.vyonex.internal.at
import com.vyonex.internal.playerRequestBody
import com.vyonex.internal.str
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import kotlin.test.Test

/**
 * Opt-in capture of the logged-out YouTube responses the error, collection, search and related tests need:
 * VYONEX_CAPTURE=1 ./gradlew jvmTest --tests com.vyonex.CaptureFixtures --rerun -i
 * (VYONEX_CAPTURE_ONLY=file1.json,file2.json sends only the requests for those files).
 * Redacts before writing into fixtures/ (fixtures/README.md) and refuses to write a file the hygiene checks
 * still flag. Stops at a bot check instead of working around it. Prints file names, HTTP codes and sizes only.
 */
class CaptureFixtures {
    // Client names, numbers and versions from yt-dlp 2026.10.21 _base.py INNERTUBE_CLIENTS (web, web_music).
    private class Client(val name: String, val id: Int, val version: String, val origin: String)
    private val web = Client("WEB", 1, "2.20260708.00.00", "https://www.youtube.com")
    private val music = Client("WEB_REMIX", 67, "1.20260707.12.00", "https://music.youtube.com")
    private val http = OkHttpClient()
    private val only = System.getenv("VYONEX_CAPTURE_ONLY").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
    private fun wanted(file: String) = only.isEmpty() || file in only

    @Test
    fun capture() {
        if (System.getenv("VYONEX_CAPTURE") != "1") return
        // The unplayable cases of the live survey (QUESTIONS item 2), asked as extract asks VISIONOS.
        mapOf(
            "age" to "qpgTC9MDx1o", "not-found" to "AAAAAAAAAAA", "live" to "jfKfPfyJRdk",
            "upcoming" to "uEMx1aH5j8k", "blocked" to "17-7M-m3YRY",
        ).forEach { (case, videoId) -> player("player-unplayable-$case.json", videoId) }
        // A video with dubbed audio tracks (Codex's candidate, issue #3), for preferring the original track.
        player("player-multi-audio.json", "v9QtM6qnG50")
        // A playlist with over 400 videos (yt-dlp's tests), so it has a second page.
        val playlist = call("browse", web, "browse-playlist.json") { put("browseId", "VLPLsyOSbh5bs16vubvKePAQ1x3PhKavfBIl") }
        val token = playlist?.let { Regex(""""continuationCommand":\{"token":"([^"]+)"""").find(it)?.groupValues?.get(1) }
        when {
            token != null -> call("browse", web, "browse-playlist-continuation.json") { put("continuation", token) }
            wanted("browse-playlist-continuation.json") -> println("browse-playlist-continuation.json: no continuation token, skipped")
        }
        // An album, its track playlist and an artist page (IDs from yt-dlp's tests and the existing captures).
        call("browse", music, "browse-album-music.json") { put("browseId", "MPREb_gTAcphH99wE") }
        call("browse", web, "browse-album-playlist.json") { put("browseId", "VLOLAK5uy_l1m0thk3g31NmIIz_vMIbWtyv7eZixlH0") }
        call("browse", music, "browse-artist-music.json") { put("browseId", "UCuAXFkgsw1L7xaCfnd5JJOw") }
        call("next", web, "next-mix.json") { put("videoId", "dQw4w9WgXcQ"); put("playlistId", "RDdQw4w9WgXcQ") }
        call("next", web, "next-related.json") { put("videoId", "dQw4w9WgXcQ") }
        call("search", web, "search-web.json") { put("query", "rick astley") }
        // Filtered searches, with the params YouTube's own search page sends for its "Type" filters.
        call("search", web, "search-web-channels.json") { put("query", "rick astley"); put("params", VyonExSearchType.CHANNELS.params) }
        call("search", web, "search-web-shorts.json") { put("query", "rick astley"); put("params", VyonExSearchType.SHORTS.params) }
        // Search suggestions, asked as YouTube's own search box asks them.
        get("https://suggestqueries-clients6.youtube.com/complete/search?ds=yt&client=youtube&hl=en&gl=us&q=rick+ast&xhr=t&ie=utf-8&oe=utf-8", "suggest-web.json")
        // A channel and its tabs; each tab's params come from the channel response, picked by the tab's URL.
        val channel = call("browse", web, "browse-channel.json") { put("browseId", "UCuAXFkgsw1L7xaCfnd5JJOw") }
        for (tab in listOf("videos", "shorts", "playlists", "posts")) {
            val params = channel?.let { Regex(""""url":"/@[^"/]+/$tab".*?"params":"([^"]+)"""").find(it)?.groupValues?.get(1) }
            val page = params?.let { call("browse", web, "browse-channel-$tab.json") { put("browseId", "UCuAXFkgsw1L7xaCfnd5JJOw"); put("params", it) } }
            if (tab == "videos") {
                // The grid's last item is its next page.
                val grid = page?.let { Json.parseToJsonElement(it) }.at("contents", "twoColumnBrowseResultsRenderer", "tabs")
                val more = (grid as? JsonArray).orEmpty().firstNotNullOfOrNull { t ->
                    (t.at("tabRenderer", "content", "richGridRenderer", "contents") as? JsonArray)?.lastOrNull()
                        ?.at("continuationItemRenderer", "continuationEndpoint", "continuationCommand", "token").str
                }
                more?.let { call("browse", web, "browse-channel-videos-continuation.json") { put("continuation", it) } }
            }
            if (tab == "posts") {
                // The posts list (not the channel header) ends in its next page's token.
                val tabs = page?.let { Json.parseToJsonElement(it) }.at("contents", "twoColumnBrowseResultsRenderer", "tabs")
                val more = (tabs as? JsonArray).orEmpty().firstNotNullOfOrNull { t ->
                    ((t.at("tabRenderer", "content", "sectionListRenderer", "contents") as? JsonArray)?.firstOrNull()
                        ?.at("itemSectionRenderer", "contents") as? JsonArray)?.lastOrNull()
                        ?.at("continuationItemRenderer", "continuationEndpoint", "continuationCommand", "token").str
                }
                more?.let { call("browse", web, "browse-channel-posts-continuation.json") { put("continuation", it) } }
            }
        }
        // Comments: the watch-next response's comment section holds its first page's continuation.
        val next = call("next", web, "next-watch.json") { put("videoId", "dQw4w9WgXcQ") }
        val sections = next?.let { Json.parseToJsonElement(it) }
            .at("contents", "twoColumnWatchNextResults", "results", "results", "contents")
        val comments = (sections as? JsonArray).orEmpty().firstNotNullOfOrNull { section ->
            section.at("itemSectionRenderer").takeIf { it.at("sectionIdentifier").str == "comment-item-section" }
                ?.at("contents")?.let { (it as? JsonArray)?.firstOrNull() }
                ?.at("continuationItemRenderer", "continuationEndpoint", "continuationCommand", "token").str
        }
        val page = comments?.let { call("next", web, "next-comments.json") { put("continuation", it) } }
        // One thread's replies: the first thread whose replies section holds a continuation.
        val replies = page?.let { Regex(""""commentRepliesRenderer":\{"contents":\[\{"continuationItemRenderer":\{.*?"token":"([^"]+)"""").find(it)?.groupValues?.get(1) }
        replies?.let { call("next", web, "next-comment-replies.json") { put("continuation", it) } }
        call("search", music, "search-music.json") { put("query", "never gonna give you up") }
        soundCloud()
        appleMusic()
        // Bandcamp: a track page and an album page from yt-dlp's tests. Bandcamp answers some networks (the cloud build
        // machine, 2026-10-28) with a "Client Challenge" page instead; that is a bot check, so the capture stops there.
        bandcamp("bandcamp-track.html", "https://relapsealumni.bandcamp.com/track/hail-to-fire")
        bandcamp("bandcamp-album.html", "https://jstrecords.bandcamp.com/album/entropy-ep")
    }

    /**
     * Apple's iTunes Search API (documented, no key), metadata only: a song, an album with its tracks, an artist with
     * albums, a song search. Song previews (audio) are replaced before writing.
     */
    private fun appleMusic() {
        val api = "https://itunes.apple.com"
        get("$api/lookup?id=1773293184&country=us", "apple-song.json")
        get("$api/lookup?id=1773292758&entity=song&country=us", "apple-album.json")
        get("$api/lookup?id=669771&entity=album&limit=5&country=us", "apple-artist.json")
        get("$api/search?term=never+gonna+give+you+up&media=music&entity=song&limit=5&country=us", "apple-search.json")
    }

    private fun bandcamp(file: String, url: String) {
        if (!wanted(file)) return
        val (code, page) = http.newCall(Request.Builder().url(url).header("User-Agent", DESKTOP_UA).build()).execute().use { it.code to it.body.string() }
        check("<title>Client Challenge" !in page) { "$file: Bandcamp answered with a client challenge (a bot check); stopping (not worked around)" }
        if (code != 200) return println("$file: HTTP $code, skipped")
        val redacted = redactCapture(page)
        check(!hasPublicAddress(redacted) && !hasCookieValue(redacted) && !hasBandcampStreamUrl(redacted)) { "$file: redaction incomplete, not written" }
        File(FIXTURES, file).writeText(redacted)
        println("$file: ${redacted.length} chars")
    }

    /**
     * SoundCloud, as yt-dlp asks it: the web client's ID from the home page's scripts (kept in memory only), a track and
     * a set through `resolve`, and the set's tracks it sent without details through `tracks?ids=`. No stream URL.
     */
    private fun soundCloud() {
        val files = listOf("soundcloud-track.json", "soundcloud-playlist.json", "soundcloud-playlist-tracks.json")
        if (files.none(::wanted)) return
        fun text(url: String) = http.newCall(Request.Builder().url(url).header("User-Agent", DESKTOP_UA).build()).execute().use { it.body.string() }
        val home = text("https://soundcloud.com/")
        val clientId = Regex("""<script[^>]+src="([^"]+)"""").findAll(home).map { it.groupValues[1] }.toList().asReversed()
            .firstNotNullOfOrNull { Regex("""client_id\s*:\s*"([0-9a-zA-Z]{32})"""").find(text(it))?.groupValues?.get(1) }
            ?: return println("soundcloud: no client ID in the home page's scripts, skipped")
        val api = "https://api-v2.soundcloud.com"
        get("$api/resolve?url=https%3A%2F%2Fsoundcloud.com%2Fethmusic%2Flostin-powers-she-so-heavy&client_id=$clientId", "soundcloud-track.json")
        val set = get("$api/resolve?url=https%3A%2F%2Fsoundcloud.com%2Fthe-concept-band%2Fsets%2Fthe-royal-concept-ep&client_id=$clientId", "soundcloud-playlist.json")
        val bare = (set?.let { Json.parseToJsonElement(it) }.at("tracks") as? JsonArray).orEmpty()
            .filter { it.at("permalink_url") == null }.mapNotNull { it.at("id").str }
        if (bare.isNotEmpty()) get("$api/tracks?ids=${bare.joinToString("%2C")}&client_id=$clientId", "soundcloud-playlist-tracks.json")
    }

    private fun player(file: String, videoId: String) {
        val id = IDENTITIES.first { it.profileId == "VISIONOS" }
        val session = VyonExSession(cookie = null, visitorData = null, region = "US", language = "en")
        post(
            "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            playerRequestBody(id, videoId, session, null, null, null),
            mapOf("User-Agent" to id.userAgent, "X-YouTube-Client-Name" to "${id.clientId}", "X-YouTube-Client-Version" to id.clientVersion),
            file,
        )
    }

    private fun call(endpoint: String, client: Client, file: String, fields: JsonObjectBuilder.() -> Unit): String? {
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", client.name)
                    put("clientVersion", client.version)
                    put("hl", "en")
                    put("gl", "US")
                }
            }
            fields()
        }.toString()
        val headers = mapOf(
            "User-Agent" to DESKTOP_UA, "X-YouTube-Client-Name" to "${client.id}", "X-YouTube-Client-Version" to client.version,
            "Origin" to client.origin, "Referer" to "${client.origin}/",
        )
        return post("${client.origin}/youtubei/v1/$endpoint?prettyPrint=false", body, headers, file)
    }

    private fun post(url: String, body: String, headers: Map<String, String>, file: String) =
        send(Request.Builder().url(url).post(body.toRequestBody("application/json".toMediaType())), headers, file)

    private fun get(url: String, file: String) = send(Request.Builder().url(url), mapOf("User-Agent" to DESKTOP_UA), file)

    private fun send(builder: Request.Builder, headers: Map<String, String>, file: String): String? {
        if (!wanted(file)) return null
        val request = builder.apply { headers.forEach { (name, value) -> header(name, value) } }.build()
        val (code, text) = http.newCall(request).execute().use { it.code to it.body.string() }
        check("not a bot" !in text) { "$file: YouTube asked to confirm this is not a bot; stopping (not worked around)" }
        if (code != 200) {
            println("$file: HTTP $code, skipped")
            return null
        }
        // Comment authors are people: their names, channels and pictures are replaced, consistently, before writing.
        val redacted = redactCapture(if (file.startsWith("next-comment")) anonymizeCommentAuthors(text) else text)
        check(
            !hasPublicAddress(redacted) && !hasCookieValue(redacted) && !hasVisitorId(redacted) && !hasHeartbeatData(redacted) &&
                !hasSoundCloudSecrets(redacted) && !hasAudioPreview(redacted),
        ) {
            "$file: redaction incomplete, not written"
        }
        File(FIXTURES, file).writeText(redacted)
        println("$file: ${redacted.length} chars")
        return redacted
    }
}

private val COMMENT_AUTHOR = Regex(""""author":\{"channelId":"(UC[\w-]{22})","displayName":"([^"]*)","avatarThumbnailUrl":"([^"]*)"""")

/** Each comment author's channel ID, handle and picture become `UC…AUTHOR01`, `@author01` and a placeholder picture. */
internal fun anonymizeCommentAuthors(text: String): String {
    var out = text
    COMMENT_AUTHOR.findAll(text).map { it.destructured }.distinctBy { it.component1() }.forEachIndexed { i, (channelId, name, avatar) ->
        val n = (i + 1).toString().padStart(2, '0')
        out = out.replace(channelId, "UC" + "AUTHOR$n".padStart(22, 'x'))
        if (name.isNotEmpty()) out = out.replace(name, "@author$n")
        if (avatar.isNotEmpty()) out = out.replace(avatar, "https://yt3.ggpht.com/author$n")
    }
    return out
}

