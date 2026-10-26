package com.vyonex.internal

import com.vyonex.VyonExAudioCandidate
import com.vyonex.VyonExHostServices
import com.vyonex.VyonExLogLevel
import com.vyonex.VyonExLogLevel.DEBUG
import com.vyonex.VyonExLogLevel.INFO
import com.vyonex.VyonExLogLevel.WARN
import com.vyonex.VyonExSession
import com.vyonex.VyonExStream
import com.vyonex.internal.primary.extraction.TokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.update

internal const val MAX_REMEMBERED = 64
internal const val EXCLUSION_MS = 10 * 60 * 1000L
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

    fun initialize() {
        scope.launch {
            delay(2_000)
            warmUp()
        }
    }

    fun onSessionChanged() {
        remembered.store(emptyMap())
        exclusions.store(emptyMap())
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
    suspend fun extract(videoId: String, maxKbps: Int, skipClients: Set<String>, requireM4a: Boolean): VyonExStream? {
        val session = host.session()
        val loggedIn = isLoggedIn(session.cookie)
        var visitorData = session.visitorData
        var unplayable: String? = null
        for (id in IDENTITIES) {
            if (id.profileId in skipClients || id.clientName in skipClients || isExcluded(videoId, id.profileId)) continue
            if (loggedIn && !id.supportsCookies) continue
            try {
                val token = if (id.needsPoToken) {
                    val provider = tokenProvider ?: continue
                    provider.getPoToken(videoId, visitorData ?: continue, session.cookie) ?: continue
                } else {
                    null
                }
                val pr = playerResponse(id, videoId, session, token?.visitorData ?: visitorData, token?.playerRequestToken, loggedIn)
                visitorData = visitorData ?: pr.at("responseContext", "visitorData").str
                val status = pr.at("playabilityStatus", "status").str
                if (status != "OK") {
                    val reason = "$status: ${pr.at("playabilityStatus", "reason").str}"
                    unplayable = unplayable ?: reason
                    log(DEBUG, "${id.profileId} refused ($reason)", videoId)
                    continue
                }
                val format = selectFormat(playableFormats(pr, token?.streamingDataToken, null), maxKbps, requireM4a)
                if (format == null) {
                    log(DEBUG, "${id.profileId} has no usable audio format", videoId)
                    continue
                }
                return remember(stream(videoId, format, pr, id.clientName, id.profileId, mediaHeaders(id.userAgent, session)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                log(WARN, "${id.profileId} failed: ${e.describe()}", videoId)
            }
        }
        if (unplayable != null) throw IllegalStateException("VyonEx: video $videoId is unplayable ($unplayable)")
        return null
    }

    /** The iOS `extract`: the player API first, then the watch page. */
    suspend fun extractOrWatchPage(videoId: String, maxKbps: Int, skipClients: Set<String>, requireM4a: Boolean): VyonExStream? {
        val primary = try {
            extract(videoId, maxKbps, skipClients, requireM4a)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log(INFO, "player API failed, trying the watch page: ${e.describe()}", videoId)
            null
        }
        return primary ?: watchPageStream(videoId, maxKbps, skipClients, requireM4a)
    }

    suspend fun watchPageStream(videoId: String, maxKbps: Int, skipClients: Set<String>, requireM4a: Boolean): VyonExStream? {
        if (WATCH_PAGE_PROFILES.any { it in skipClients || isExcluded(videoId, it) }) return null
        return try {
            val session = host.session()
            val (pr, formats) = watchPage(videoId, session) ?: return null
            val format = selectFormat(formats, maxKbps, requireM4a) ?: return null
            remember(stream(videoId, format, pr, "WEB", "WEB_WATCH_PAGE", watchHeaders(session)))
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
        return page.playerResponse to playableFormats(page.playerResponse, null, page.playerId)
    }

    private suspend fun playableFormats(pr: JsonObject, poToken: String?, playerId: String?): List<AudioFormat> =
        audioFormatsOf(pr).mapNotNull { json ->
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
            full?.takeIf(::isAllowedUrl)?.let { AudioFormat(json, it) }
        }

    private fun stream(
        videoId: String,
        format: AudioFormat,
        pr: JsonObject,
        clientName: String,
        profileId: String,
        headers: Map<String, String>,
    ) = VyonExStream(
        videoId = videoId,
        url = format.url,
        kbps = format.bitrate / 1000,
        mimeType = format.mimeType,
        loudnessDb = (pr.at("playerConfig", "audioConfig", "loudnessDb") as? JsonPrimitive)?.doubleOrNull,
        clientName = clientName,
        profileId = profileId,
        headers = headers,
    ).also { log(INFO, "chose $profileId itag ${format.itag} (${it.mimeType}, ${it.kbps} kbps)", videoId) }

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
