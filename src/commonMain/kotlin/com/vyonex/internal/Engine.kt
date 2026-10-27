package com.vyonex.internal

import com.vyonex.VyonExAudioCandidate
import com.vyonex.VyonExFormat
import com.vyonex.VyonExFormats
import com.vyonex.VyonExAlbum
import com.vyonex.VyonExArtist
import com.vyonex.VyonExHostServices
import com.vyonex.VyonExListPage
import com.vyonex.VyonExLogLevel
import com.vyonex.VyonExLogLevel.DEBUG
import com.vyonex.VyonExLogLevel.INFO
import com.vyonex.VyonExLogLevel.WARN
import com.vyonex.VyonExSession
import com.vyonex.VyonExMetadata
import com.vyonex.VyonExMix
import com.vyonex.VyonExPlaylist
import com.vyonex.VyonExSearchPage
import com.vyonex.VyonExSink
import com.vyonex.VyonExStream
import com.vyonex.VyonExUnplayableException
import com.vyonex.VyonExVideoStream
import com.vyonex.internal.primary.extraction.TokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.update

internal const val MAX_REMEMBERED = 64
internal const val EXCLUSION_MS = 10 * 60 * 1000L
// yt-dlp _video.py CHUNK_SIZE.
internal const val DOWNLOAD_CHUNK_BYTES = 10L shl 20
private val WATCH_PAGE_PROFILES = listOf("WEB", "WEB_WATCH_PAGE")

/** Everything behind the platform `VyonEx` classes. */
@OptIn(ExperimentalAtomicApi::class)
internal class Engine(
    private val host: VyonExHostServices,
    private val tokenProvider: TokenProvider?,
    private val http: HttpClient,
    store: PlayerStore?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    internal val players = Players(http, store)
    private val remembered = AtomicReference(emptyMap<String, VyonExStream>())
    private val exclusions = AtomicReference(emptyMap<String, Long>())
    internal val health = ClientHealth(host::nowMs)

    fun initialize() {
        scope.launch {
            delay(2_000)
            warmUp()
        }
    }

    /** Stops background work and frees the loaded player. The engine must not be used afterwards. */
    fun close() {
        scope.cancel()
        // Under the players' lock, so a JS call in flight finishes first.
        CoroutineScope(Dispatchers.Default).launch { players.close() }
    }

    fun onSessionChanged() {
        remembered.store(emptyMap())
        exclusions.store(emptyMap())
        health.clear()
        if (host.configStore.enabled) scope.launch { warmUp() }
    }

    private suspend fun warmUp() {
        try {
            val session = host.session()
            players.current()
            tokenProvider?.prewarm(session.cookie)
            log(DEBUG, "warm-up done")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log(WARN, "warm-up failed: ${e.describe()}")
        }
    }

    /** Player API extraction. Throws when YouTube says the video cannot be played. */
    suspend fun extract(videoId: String, maxKbps: Int, skipClients: Set<String>, requireM4a: Boolean): VyonExStream? =
        viaIdentities(videoId, skipClients) { id, pr, poToken, headers ->
            val format = selectFormat(playableFormats(audioFormatsOf(pr), poToken, null, ::AudioFormat), maxKbps, requireM4a)
            if (format == null) log(DEBUG, "${id.profileId} has no usable audio format", videoId)
            format?.let { accept(videoId, it, pr, id.clientName, id.profileId, headers) }
        }

    /** A video stream and, for video-only formats, the audio to play with it. Throws like [extract]. */
    suspend fun extractVideo(videoId: String, maxHeight: Int, skipClients: Set<String>, requireAvc: Boolean): VyonExVideoStream? =
        viaIdentities(videoId, skipClients) { id, pr, poToken, headers ->
            val audio = selectFormat(playableFormats(audioFormatsOf(pr), poToken, null, ::AudioFormat), 160, requireAvc)
            val videos = playableFormats(videoFormatsOf(pr), poToken, null, ::VideoFormat)
            // Video-only formats need an audio track; without one only combined formats can play.
            val video = selectVideo(if (audio == null) videos.filter { it.muxed } else videos, maxHeight, requireAvc)
            if (video == null) {
                log(DEBUG, "${id.profileId} has no usable video format", videoId)
                return@viaIdentities null
            }
            if (!answers(video.url, headers, videoId, id.profileId)) return@viaIdentities null
            val audioStream = if (video.muxed) null else accept(videoId, audio!!, pr, id.clientName, id.profileId, headers) ?: return@viaIdentities null
            log(INFO, "chose ${id.profileId} video itag ${video.itag} (${video.width}x${video.height}@${video.fps}, ${video.mimeType})", videoId)
            // Remembered like audio streams, so headersFor and onRefused work for the video URL too.
            remember(VyonExStream(videoId, video.url, video.bitrate / 1000, video.mimeType, null, id.clientName, id.profileId, headers))
            VyonExVideoStream(
                videoId, video.url, video.width, video.height, video.fps, video.bitrate / 1000, video.mimeType,
                audioStream, id.clientName, id.profileId, headers,
            )
        }

    /** Every playable format of the first identity whose URLs answer, for downloads. Throws like [extract]. */
    suspend fun formats(videoId: String, skipClients: Set<String>): VyonExFormats? =
        viaIdentities(videoId, skipClients) { id, pr, poToken, headers ->
            val all = playableFormats(audioFormatsOf(pr) + videoFormatsOf(pr), poToken, null, ::formatOf)
            if (all.isEmpty()) log(DEBUG, "${id.profileId} has no usable format", videoId)
            // One probe stands for the list: a response's formats share its signature and address checks.
            all.firstOrNull()?.takeIf { answers(it.url, headers, videoId, id.profileId) }
                ?.let { VyonExFormats(videoId, all, id.clientName, id.profileId, headers) }
        }

    /**
     * Writes [format] to [sink] from byte [from] in ranged requests of [chunkBytes], as yt-dlp does (YouTube slows
     * long single requests), and returns the position reached. A refused request throws: list the formats again
     * and resume from the bytes already written.
     */
    suspend fun download(
        format: VyonExFormat,
        headers: Map<String, String>,
        sink: VyonExSink,
        from: Long,
        onProgress: ((Long, Long?) -> Unit)?,
        chunkBytes: Long = DOWNLOAD_CHUNK_BYTES,
    ): Long {
        require(isAllowedUrl(format.url)) { "VyonEx: not a YouTube media URL" }
        var position = from
        var total = format.contentLength
        while (total == null || position < total) {
            val last = (position + chunkBytes - 1).let { if (total != null) minOf(it, total - 1) else it }
            val response = http.get(format.url) {
                headers.forEach { (name, value) -> header(name, value) }
                header(HttpHeaders.Range, "bytes=$position-$last")
            }
            // A 200 is the whole file, which is only right when starting from the beginning.
            val whole = response.status == HttpStatusCode.OK && position == 0L
            check(response.status == HttpStatusCode.PartialContent || whole) { "VyonEx: download refused (HTTP ${response.status.value})" }
            total = total ?: response.headers[HttpHeaders.ContentRange]?.substringAfterLast('/')?.toLongOrNull()
            val bytes = response.bodyAsBytes()
            sink.write(bytes)
            position += bytes.size
            onProgress?.invoke(position, total)
            if (whole || bytes.isEmpty() || (total == null && bytes.size < chunkBytes)) break
        }
        return position
    }

    /** Everything YouTube says about the video: WEB reports the most (microformat), VISIONOS needs no player script. */
    suspend fun metadata(videoId: String): VyonExMetadata? {
        val session = host.session()
        for (id in listOf("WEB", "VISIONOS").map(::identity)) {
            try {
                // Cookies go only to clients that take them.
                val asked = if (id.supportsCookies) session else session.copy(cookie = null)
                val pr = playerResponse(id, videoId, asked, session.visitorData, null, isLoggedIn(asked.cookie))
                if (pr["videoDetails"] != null) return metadataOf(videoId, pr)
                log(DEBUG, "${id.profileId} has no video details (${pr.at("playabilityStatus", "status").str})", videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                log(WARN, "${id.profileId} metadata failed: ${e.describe()}", videoId)
            }
        }
        return null
    }

    /**
     * Tries the identities in order, one player request each, until [pick] returns a result.
     * Throws when YouTube said the video cannot be played and nothing was found.
     */
    private suspend fun <T : Any> viaIdentities(
        videoId: String,
        skipClients: Set<String>,
        pick: suspend (id: Identity, pr: JsonObject, poToken: String?, headers: Map<String, String>) -> T?,
    ): T? {
        val session = host.session()
        val loggedIn = isLoggedIn(session.cookie)
        var visitorData = session.visitorData
        var unplayable: VyonExUnplayableException? = null
        for (id in health.ordered(if (loggedIn) LOGGED_IN_ORDER else LOGGED_OUT_ORDER)) {
            if (id.profileId in skipClients || id.clientName in skipClients || isExcluded(videoId, id.profileId)) continue
            try {
                val token = if (id.needsPoToken) {
                    val provider = tokenProvider ?: continue
                    provider.getPoToken(videoId, visitorData ?: continue, session.cookie) ?: continue
                } else {
                    null
                }
                val pr = playerResponse(id, videoId, session, token?.visitorData ?: visitorData, token?.playerRequestToken, loggedIn)
                visitorData = visitorData ?: pr.at("responseContext", "visitorData").str
                val refusal = unplayableOf(videoId, pr)
                if (refusal != null) {
                    unplayable = unplayable ?: refusal
                    log(DEBUG, "${id.profileId} refused (${refusal.status}: ${refusal.youtubeReason})", videoId)
                    continue
                }
                pick(id, pr, token?.streamingDataToken, mediaHeaders(id.userAgent, session))?.let {
                    health.succeeded(id.profileId)
                    return it
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                log(WARN, "${id.profileId} failed: ${e.describe()}", videoId)
                health.failed(id.profileId)
            }
        }
        unplayable?.let { throw it }
        return null
    }

    /** The iOS `extract`: the player API first, then the watch page; YouTube's refusal stands if neither plays. */
    suspend fun extractOrWatchPage(videoId: String, maxKbps: Int, skipClients: Set<String>, requireM4a: Boolean): VyonExStream? {
        var refusal: VyonExUnplayableException? = null
        val primary = try {
            extract(videoId, maxKbps, skipClients, requireM4a)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            refusal = e as? VyonExUnplayableException
            log(INFO, "player API failed, trying the watch page: ${e.describe()}", videoId)
            null
        }
        return primary ?: watchPageStream(videoId, maxKbps, skipClients, requireM4a) ?: refusal?.let { throw it }
    }

    suspend fun watchPageStream(videoId: String, maxKbps: Int, skipClients: Set<String>, requireM4a: Boolean): VyonExStream? {
        if (WATCH_PAGE_PROFILES.any { it in skipClients || isExcluded(videoId, it) }) return null
        return try {
            val session = host.session()
            val (pr, formats) = watchPage(videoId, session) ?: return null
            val format = selectFormat(formats, maxKbps, requireM4a) ?: return null
            accept(videoId, format, pr, "WEB", "WEB_WATCH_PAGE", watchHeaders(session))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log(WARN, "watch page failed: ${e.describe()}", videoId)
            null
        }
    }

    suspend fun audioCandidates(videoId: String): List<VyonExAudioCandidate> = try {
        watchPage(videoId, host.session())?.second.orEmpty().map {
            VyonExAudioCandidate(it.url, (it.averageBitrate ?: it.bitrate) / 1000, it.mimeType, audioFormatOf(it.mimeType))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        log(WARN, "audio candidates failed: ${e.describe()}", videoId)
        emptyList()
    }

    /** A playlist and its first page; null when YouTube shows no such playlist (missing or private). */
    suspend fun playlist(playlistId: String): VyonExPlaylist? {
        // yt-dlp: a mix is not a playlist page; it is read from the watch-next endpoint instead.
        require(!playlistId.startsWith("RD")) { "VyonEx: $playlistId is a mix, not a playlist" }
        return playlistOf(playlistId, innertube("browse") { put("browseId", "VL$playlistId") })
    }

    suspend fun playlistMore(continuation: String): VyonExListPage = continuationPageOf(innertube("browse") { put("continuation", continuation) })

    /** A YouTube Music album page; null when YouTube shows no such album. */
    suspend fun album(browseId: String): VyonExAlbum? {
        require(browseId.startsWith("MPREb_")) { "VyonEx: $browseId is not an album page" }
        return albumOf(browseId, innertube("browse", MUSIC_WEB, MUSIC_ORIGIN) { put("browseId", browseId) })
    }

    /** A YouTube Music artist page; null when YouTube shows no artist for the channel. */
    suspend fun artist(channelId: String): VyonExArtist? {
        require(channelId.startsWith("UC")) { "VyonEx: $channelId is not a channel ID" }
        return artistOf(channelId, innertube("browse", MUSIC_WEB, MUSIC_ORIGIN) { put("browseId", channelId) })
    }

    /** A mix (`RD…`) and its first videos; [videoId] picks where it starts (a video's own mix, RD + ID, starts there). */
    suspend fun mix(playlistId: String, videoId: String? = null): VyonExMix? {
        require(playlistId.startsWith("RD")) { "VyonEx: $playlistId is not a mix" }
        val seed = videoId ?: playlistId.removePrefix("RD").takeIf { it.length == 11 }
        return mixOf(innertube("next") { put("playlistId", playlistId); seed?.let { put("videoId", it) } })
    }

    /** The mix's next videos, from the previous page's continuation. */
    suspend fun mixMore(continuation: String): VyonExListPage {
        val parts = continuation.split(' ')
        require(parts.size == 4 && parts[0].startsWith("RD") && parts[2].toIntOrNull() != null) { "VyonEx: not a mix continuation" }
        val (playlistId, videoId, index, params) = parts
        return mixMoreOf(
            innertube("next") { put("playlistId", playlistId); put("videoId", videoId); put("index", index.toInt()); put("params", params) },
            after = videoId,
        )
    }

    /** The videos YouTube shows beside [videoId] (its "up next" recommendations, logged out). */
    suspend fun related(videoId: String): VyonExListPage = relatedOf(innertube("next") { put("videoId", videoId) })

    suspend fun relatedMore(continuation: String): VyonExListPage = continuationPageOf(innertube("next") { put("continuation", continuation) })

    /** YouTube's search results for [query] (videos and playlists), logged out. */
    suspend fun search(query: String): VyonExSearchPage {
        require(query.isNotBlank()) { "VyonEx: empty search" }
        return webSearchOf(innertube("search") { put("query", query) })
    }

    suspend fun searchMore(continuation: String): VyonExSearchPage = webSearchMoreOf(innertube("search") { put("continuation", continuation) })

    /** YouTube Music's top results for [query]: songs, videos, albums, artists and playlists, logged out. */
    suspend fun searchMusic(query: String): VyonExSearchPage {
        require(query.isNotBlank()) { "VyonEx: empty search" }
        return musicSearchOf(innertube("search", MUSIC_WEB, MUSIC_ORIGIN) { put("query", query) })
    }

    /** A logged-out InnerTube call (WEB unless told otherwise): no cookie is sent, so lists are the public ones. */
    private suspend fun innertube(
        endpoint: String,
        id: Identity = identity("WEB"),
        origin: String = YT_ORIGIN,
        fields: JsonObjectBuilder.() -> Unit,
    ): JsonObject {
        val session = host.session()
        val response = http.post("$origin/youtubei/v1/$endpoint?prettyPrint=false") {
            contentType(ContentType.Application.Json)
            setBody(innertubeBody(id, session, fields))
            header(HttpHeaders.UserAgent, id.userAgent)
            header("X-YouTube-Client-Name", id.clientId.toString())
            header("X-YouTube-Client-Version", id.clientVersion)
            header(HttpHeaders.Origin, origin)
            session.visitorData?.let { header("X-Goog-Visitor-Id", it) }
        }
        check(response.status.isSuccess()) { "VyonEx: $endpoint HTTP ${response.status.value}" }
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    fun headersFor(url: String): Map<String, String>? = remembered.load()[url]?.headers

    fun onRefused(url: String): String? {
        var hit: VyonExStream? = null
        remembered.update { streams ->
            hit = streams[url]
            streams - url
        }
        val refused = hit ?: return null
        exclude(refused.videoId, refused.profileId)
        scope.launch { players.invalidate() }
        return refused.videoId
    }

    fun exclude(videoId: String, profileId: String) {
        log(WARN, "excluding $profileId for 10 minutes", videoId)
        health.failed(profileId)
        val now = host.nowMs()
        exclusions.update { all -> all.filterValues { it > now } + ("$videoId/$profileId" to now + EXCLUSION_MS) }
    }

    internal fun isExcluded(videoId: String, profileId: String) =
        (exclusions.load()["$videoId/$profileId"] ?: 0) > host.nowMs()

    internal fun remember(stream: VyonExStream): VyonExStream {
        remembered.update { streams ->
            (if (streams.size >= MAX_REMEMBERED && stream.url !in streams) emptyMap() else streams) + (stream.url to stream)
        }
        return stream
    }

    private suspend fun playerResponse(
        id: Identity,
        videoId: String,
        session: VyonExSession,
        visitorData: String?,
        poToken: String?,
        loggedIn: Boolean,
    ): JsonObject {
        val sts = if (id.needsPlayerJs) players.current().sts else null
        val response = http.post("$YT_ORIGIN/youtubei/v1/player?prettyPrint=false") {
            contentType(ContentType.Application.Json)
            setBody(playerRequestBody(id, videoId, session, visitorData, sts, poToken))
            header(HttpHeaders.UserAgent, id.userAgent)
            header("X-YouTube-Client-Name", id.clientId.toString())
            header("X-YouTube-Client-Version", id.clientVersion)
            header(HttpHeaders.Origin, YT_ORIGIN)
            if (visitorData != null) header("X-Goog-Visitor-Id", visitorData)
            session.cookie?.let { cookie ->
                header(HttpHeaders.Cookie, cookie)
                if (loggedIn) {
                    sidAuthorization(cookie, host.nowMs())?.let { header(HttpHeaders.Authorization, it) }
                    header("X-Origin", YT_ORIGIN)
                }
            }
        }
        check(response.status.isSuccess()) { "player API HTTP ${response.status.value}" }
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    private suspend fun watchPage(videoId: String, session: VyonExSession): Pair<JsonObject, List<AudioFormat>>? {
        val url = "$YT_ORIGIN/watch?v=${videoId.encodeURLParameter()}" +
            "&hl=${session.language.encodeURLParameter()}&gl=${session.region.encodeURLParameter()}"
        val html = http.text(url) {
            watchHeaders(session).forEach { (name, value) -> header(name, value) }
            // Non-login cookies (consent) still matter for the page itself.
            session.cookie?.let { header(HttpHeaders.Cookie, it) }
        }
        val page = parseWatchPage(html) ?: return null
        if (page.playerResponse.at("playabilityStatus", "status").str != "OK") return null
        return page.playerResponse to playableFormats(audioFormatsOf(page.playerResponse), null, page.playerId, ::AudioFormat)
    }

    private suspend fun <F> playableFormats(
        formats: List<JsonObject>,
        poToken: String?,
        playerId: String?,
        make: (JsonObject, String) -> F,
    ): List<F> =
        formats.mapNotNull { json ->
            val url = try {
                playableUrl(json) { type, challenge ->
                    (if (playerId != null) players.get(playerId) else players.current()).js.solve(type, challenge)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                log(DEBUG, "could not decode itag ${json["itag"].str}: ${e.describe()}")
                null
            }
            val full = if (url != null && poToken != null) "$url&pot=${poToken.encodeURLParameter()}" else url
            full?.takeIf(::isAllowedUrl)?.let { make(json, it) }
        }

    /**
     * BRIEF 4.2 only counts a format "whose URL works", so the URL is probed for one byte with the
     * stream's own headers, and only a ranged answer counts ([probeRefusal]). The answer is streamed
     * and its body never read: a server ignoring the range would otherwise send the whole file. A
     * refusal excludes the profile, as onRefused would.
     */
    private suspend fun answers(url: String, headers: Map<String, String>, videoId: String, profileId: String): Boolean {
        val refusal = http.prepareGet(url) {
            headers.forEach { (name, value) -> header(name, value) }
            header(HttpHeaders.Range, "bytes=0-0")
        }.execute { probeRefusal(it.status, it.headers[HttpHeaders.ContentRange], it.headers[HttpHeaders.ContentType]) }
        if (refusal == null) return true
        log(WARN, "$profileId URL refused ($refusal)", videoId)
        exclude(videoId, profileId)
        return false
    }

    /** The audio stream for [format] once its URL answers, remembered for headersFor and onRefused. */
    private suspend fun accept(
        videoId: String,
        format: AudioFormat,
        pr: JsonObject,
        clientName: String,
        profileId: String,
        headers: Map<String, String>,
    ): VyonExStream? {
        if (!answers(format.url, headers, videoId, profileId)) return null
        log(INFO, "chose $profileId itag ${format.itag} (${format.mimeType}, ${format.bitrate / 1000} kbps)", videoId)
        return remember(
            VyonExStream(
                videoId = videoId,
                url = format.url,
                kbps = format.bitrate / 1000,
                mimeType = format.mimeType,
                // Formats carry their own value (enablePerFormatLoudness); VISIONOS has no response-wide one.
                loudnessDb = format.loudnessDb ?: (pr.at("playerConfig", "audioConfig", "loudnessDb") as? JsonPrimitive)?.doubleOrNull,
                clientName = clientName,
                profileId = profileId,
                headers = headers,
            ),
        )
    }

    private fun mediaHeaders(userAgent: String, session: VyonExSession) = buildMap {
        put("User-Agent", userAgent)
        if (isLoggedIn(session.cookie)) put("Cookie", session.cookie!!)
    }

    private fun watchHeaders(session: VyonExSession) = buildMap {
        put("User-Agent", DESKTOP_UA)
        put("Accept", "*/*")
        put("Accept-Language", "${session.language}-${session.region},${session.language};q=0.9")
        put("Referer", "$YT_ORIGIN/")
        put("Origin", YT_ORIGIN)
        if (isLoggedIn(session.cookie)) put("Cookie", session.cookie!!)
    }

    private fun log(level: VyonExLogLevel, message: String, trackId: String? = null) =
        host.log(level, "VyonEx: $message", trackId)
}

private fun Throwable.describe() = "${this::class.simpleName}: $message"

private val FIRST_BYTE = Regex("""bytes 0-0/(\d+)""")

/**
 * Why a probe's answer does not count, or null when it does: a 206 for exactly the first byte of a
 * file that has bytes. A 200 means the range was ignored, and an error or block page (HTML, JSON,
 * text) can come back with a success status. Media types are not checked one by one, so an
 * unexpected but real media type still counts.
 */
internal fun probeRefusal(status: HttpStatusCode, contentRange: String?, contentType: String?): String? {
    val type = contentType?.substringBefore(';')?.trim()?.lowercase()
    return when {
        status != HttpStatusCode.PartialContent -> "HTTP ${status.value}"
        (FIRST_BYTE.matchEntire(contentRange?.trim().orEmpty())?.groupValues?.get(1)?.toLongOrNull() ?: 0) <= 0 ->
            "range not honoured: ${contentRange ?: "no Content-Range"}"
        type != null && (type.startsWith("text/") || type == "application/json") -> "not media: $type"
        else -> null
    }
}
