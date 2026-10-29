package com.excor

import com.excor.internal.primary.extraction.PoTokenResult
import com.excor.internal.primary.extraction.TokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class ExcorEngine(
    private val host: ExcorHostServices,
    private val tokenProvider: TokenProvider?,
    private val http: HttpClient,
    private val persistentPlayerData: PlayerDataStore? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val state = MutableStateFlow(EngineState())

    fun initialize() {
        scope.launch {
            delay(2_000)
            warmUp()
        }
    }

    fun initializeFallback() = Unit

    fun onSessionChanged() {
        state.update { it.copy(remembered = emptyMap(), exclusions = emptyMap()) }
        if (host.configStore.enabled) scope.launch { warmUp() }
    }

    fun headersFor(url: String): Map<String, String>? = state.value.remembered[url]?.headers

    fun exclude(videoId: String, profileId: String) {
        val expiresAt = host.nowMs() + EXCLUSION_MS
        state.update { old ->
            val profiles = old.exclusions[videoId].orEmpty() + (profileId to expiresAt)
            old.copy(exclusions = old.exclusions + (videoId to profiles))
        }
        log(ExcorLogLevel.WARN, "excluded profile $profileId for video $videoId for ten minutes", videoId)
    }

    fun onRefused(url: String): String? {
        var found: ExcorStream? = null
        while (found == null) {
            val old = state.value
            val current = old.remembered[url] ?: return null
            val profiles = old.exclusions[current.videoId].orEmpty() +
                (current.profileId to (host.nowMs() + EXCLUSION_MS))
            val next = old.copy(
                remembered = old.remembered - url,
                exclusions = old.exclusions + (current.videoId to profiles),
                playerData = emptyMap(),
            )
            if (state.compareAndSet(old, next)) found = current
        }
        val current = found
        log(ExcorLogLevel.WARN, "media URL refused; excluded profile ${current.profileId}", current.videoId)
        scope.launch { clearPlayerData() }
        return current.videoId
    }

    suspend fun extract(
        videoId: String,
        maxKbps: Int,
        skipClients: Set<String>,
        requireM4a: Boolean,
        watchFallback: Boolean,
    ): ExcorStream? {
        require(VIDEO_ID.matches(videoId)) { "Excor video ID is invalid" }
        val session = host.session()
        if (!watchFallback) return extractPrimary(videoId, maxKbps, skipClients, requireM4a, session)
        val primary = try {
            extractPrimary(videoId, maxKbps, skipClients, requireM4a, session)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (primary != null) return primary
        return try {
            extractFromWatchPage(videoId, maxKbps, skipClients, requireM4a, session)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    suspend fun audioCandidates(videoId: String): List<ExcorAudioCandidate> {
        if (!VIDEO_ID.matches(videoId)) return emptyList()
        return try {
            val session = host.session()
            val page = fetchWatchPage(videoId, session) ?: return emptyList()
            val formats = playableFormats(page.playerResponse, page.playerResponse.playerScriptUrl, null, session)
            formats.filter { it.audioType.startsWith("audio/") && isAllowedPlaybackUrl(it.url) }
                .map { format ->
                    val audioFormat = when {
                        format.audioType == "audio/mp4" -> ExcorAudioFormat.M4A
                        format.audioType == "audio/webm" && "opus" in format.mimeType.lowercase() -> ExcorAudioFormat.WEBM_OPUS
                        else -> ExcorAudioFormat.OTHER
                    }
                    ExcorAudioCandidate(format.url, format.candidateKbps, format.mimeType, audioFormat)
                }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun extractPrimary(
        videoId: String,
        maxKbps: Int,
        skipClients: Set<String>,
        requireM4a: Boolean,
        session: ExcorSession,
    ): ExcorStream? {
        val page = fetchWatchPage(videoId, session) ?: return null
        val apiKey = page.apiKey ?: return null
        val playerUrl = page.playerResponse.playerScriptUrl
        val excluded = activeExclusions(videoId)
        val identities = selectClients(session, skipClients, excluded)
        val playerData = if (identities.any(ClientIdentity::requiresPlayerScript) && playerUrl != null) {
            loadPlayerData(playerUrl, session)
        } else null
        var playabilityReason: String? = null

        for (identity in identities) {
            val token = if (identity.requiresStreamingToken) {
                val provider = tokenProvider ?: continue
                try {
                    provider.getPoToken(videoId, session.visitorData.orEmpty(), session.cookie)
                        ?.takeIf { it.playerRequestToken.isNotBlank() && it.streamingDataToken.isNotBlank() }
                        ?: continue
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    continue
                }
            } else null
            if (identity.requiresPlayerScript && playerData?.entry?.signatureTimestamp == null) continue

            val request = buildPlayerRequest(
                identity, videoId, session, apiKey,
                playerData?.entry?.signatureTimestamp, token,
            ) ?: continue
            val body = try {
                postPlayer(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                continue
            } ?: continue
            val response = decodePlayerResponse(body) ?: continue
            if (response.playabilityStatus != "OK") {
                playabilityReason = response.playabilityReason
                    ?: response.playabilityStatus?.let { "YouTube reports playback status $it" }
                continue
            }
            val responsePlayerUrl = response.playerScriptUrl ?: playerUrl
            val formats = playableFormats(response, responsePlayerUrl, token?.streamingDataToken, session)
            val headers = mediaHeaders(identity.userAgent, session)
            for (format in rankAudioFormats(formats, maxKbps, requireM4a)) {
                if (!isPlayable(format.url, headers)) continue
                val stream = ExcorStream(
                    videoId = videoId,
                    url = format.url,
                    kbps = format.kbps,
                    mimeType = format.mimeType,
                    loudnessDb = response.loudnessDb,
                    clientName = identity.name,
                    profileId = identity.profileId,
                    headers = headers,
                )
                remember(stream)
                log(ExcorLogLevel.INFO, "selected ${format.mimeType} from ${identity.name}", videoId)
                return stream
            }
        }
        if (playabilityReason != null) throw IllegalStateException("Excor $playabilityReason for video $videoId")
        return null
    }

    private suspend fun extractFromWatchPage(
        videoId: String,
        maxKbps: Int,
        skipClients: Set<String>,
        requireM4a: Boolean,
        session: ExcorSession,
    ): ExcorStream? {
        val skipped = skipClients.mapTo(mutableSetOf()) { it.lowercase() }
        val excluded = activeExclusions(videoId)
        if ("web" in skipped || "web_watch_page" in skipped || "web" in excluded || "web_watch_page" in excluded) return null
        val page = fetchWatchPage(videoId, session) ?: return null
        if (page.playerResponse.playabilityStatus != "OK") return null
        val formats = playableFormats(page.playerResponse, page.playerResponse.playerScriptUrl, null, session)
        val headers = watchHeaders(session)
        for (format in rankAudioFormats(formats, maxKbps, requireM4a)) {
            if (!isPlayable(format.url, headers)) continue
            val stream = ExcorStream(
                videoId, format.url, format.kbps, format.mimeType,
                page.playerResponse.loudnessDb, "WEB", "WEB_WATCH_PAGE", headers,
            )
            remember(stream)
            log(ExcorLogLevel.INFO, "watch-page fallback selected ${format.mimeType}", videoId)
            return stream
        }
        return null
    }

    private suspend fun playableFormats(
        response: PlayerResponse,
        playerUrl: String?,
        streamingToken: String?,
        session: ExcorSession,
    ): List<PlayerFormat> {
        val usable = response.formats.mapNotNull { format ->
            val cipher = parseCipher(format.signatureCipher)
            val url = format.url.ifBlank { cipher?.url.orEmpty() }
            if (!isAllowedPlaybackUrl(url)) null else Triple(format, cipher, url)
        }
        val signatureInputs = usable.mapNotNull { it.second?.encryptedSignature }.distinct()
        val nInputs = usable.mapNotNull { (_, _, url) -> queryParameter(url, "n") }.distinct()
        val player = if (signatureInputs.isNotEmpty() || nInputs.isNotEmpty()) {
            if (playerUrl == null) null else loadPlayerData(playerUrl, session)
        } else null
        val solved = if (signatureInputs.isNotEmpty() || nInputs.isNotEmpty()) {
            solvePlayerJs(
                playerScript = player?.source,
                preparedPlayer = player?.entry?.preparedPlayer,
                signatures = signatureInputs,
                throttling = nInputs,
            )
        } else null
        if (player != null && solved?.preparedPlayer != null && solved.preparedPlayer != player.entry.preparedPlayer) {
            savePlayerData(player.url, player.entry.copy(preparedPlayer = solved.preparedPlayer))
        }

        return usable.mapNotNull { (format, cipher, baseUrl) ->
            var url = baseUrl
            val encrypted = cipher?.encryptedSignature
            if (encrypted != null) {
                val signature = solved?.signatures?.get(encrypted) ?: return@mapNotNull null
                url = setQueryParameter(url, cipher.signatureParameter, signature)
            } else cipher?.signedSignature?.let { signature ->
                url = setQueryParameter(url, cipher.signatureParameter, signature)
            }
            queryParameter(url, "n")?.let { challenge ->
                val transformed = solved?.throttling?.get(challenge) ?: return@mapNotNull null
                url = setQueryParameter(url, "n", transformed)
            }
            if (streamingToken != null) url = setQueryParameter(url, "pot", streamingToken)
            format.copy(url = url).takeIf { isAllowedPlaybackUrl(it.url) }
        }
    }

    private suspend fun loadPlayerData(playerUrl: String, session: ExcorSession): LoadedPlayer? {
        val url = safePlayerUrl(playerUrl) ?: return null
        val cached = state.value.playerData[url]
        if (cached?.signatureTimestamp != null && cached.preparedPlayer != null) {
            return LoadedPlayer(url, cached, null)
        }
        val disk = try {
            if (host.configStore.enabled) persistentPlayerData?.read(url) else null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            log(ExcorLogLevel.WARN, "could not read player cache")
            null
        }
        if (disk?.signatureTimestamp != null && disk.preparedPlayer != null) {
            savePlayerData(url, disk, persist = false)
            return LoadedPlayer(url, disk, null)
        }
        val script = getText(url, youtubeHeaders(session, DESKTOP_UA), PLAYER_SCRIPT_LIMIT) ?: return null
        val entry = PlayerCacheEntry(
            signatureTimestamp = signatureTimestamp(script),
            preparedPlayer = solvePlayerJs(script, null, emptyList(), emptyList())?.preparedPlayer,
        )
        savePlayerData(url, entry)
        return LoadedPlayer(url, entry, script)
    }

    private suspend fun fetchWatchPage(videoId: String, session: ExcorSession): WatchPage? {
        val url = URLBuilder("https://www.youtube.com/watch").apply {
            parameters.append("v", videoId)
        }.buildString()
        val source = getText(url, watchHeaders(session), WATCH_PAGE_LIMIT) ?: return null
        return parseWatchPage(source)
    }

    private suspend fun postPlayer(request: PlayerRequest): String? {
        val response = http.post(request.url) {
            request.headers.forEach { (name, value) -> header(name, value) }
            contentType(ContentType.Application.Json)
            header(HttpHeaders.ContentLength, request.body.encodeToByteArray().size.toString())
            setBody(request.body)
        }
        if (!response.status.isSuccess()) {
            response.bodyAsChannel().cancel()
            return null
        }
        if ((response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L) > PLAYER_RESPONSE_LIMIT) {
            response.bodyAsChannel().cancel()
            return null
        }
        return response.bodyAsText().takeIf { it.length <= PLAYER_RESPONSE_LIMIT }
    }

    private suspend fun getText(url: String, headers: Map<String, String>, limit: Int): String? {
        val response = http.get(url) { headers.forEach { (name, value) -> header(name, value) } }
        if (!response.status.isSuccess() ||
            (response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L) > limit
        ) {
            response.bodyAsChannel().cancel()
            return null
        }
        return response.bodyAsText().takeIf { it.length <= limit }
    }

    private suspend fun isPlayable(url: String, headers: Map<String, String>): Boolean {
        if (!isAllowedPlaybackUrl(url)) return false
        val response = try {
            http.get(url) {
                headers.forEach { (name, value) -> header(name, value) }
                header(HttpHeaders.Range, "bytes=0-1023")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return false
        }
        response.bodyAsChannel().cancel()
        return response.status.value == 200 || response.status.value == 206
    }

    private suspend fun warmUp() {
        try {
            val session = host.session()
            try {
                tokenProvider?.prewarm(session.cookie)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                log(ExcorLogLevel.WARN, "token-provider warm-up failed")
            }
            val page = fetchWatchPage("dQw4w9WgXcQ", session)
            val scriptUrl = page?.playerResponse?.playerScriptUrl
            if (scriptUrl != null) {
                try {
                    loadPlayerData(scriptUrl, session)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    log(ExcorLogLevel.WARN, "player-script warm-up failed")
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            log(ExcorLogLevel.WARN, "background warm-up failed")
        }
    }

    private suspend fun activeExclusions(videoId: String): Set<String> {
        val now = host.nowMs()
        var active = emptySet<String>()
        state.update { old ->
            val profiles = old.exclusions[videoId].orEmpty().filterValues { it > now }
            active = profiles.keys
            old.copy(exclusions = if (profiles.isEmpty()) old.exclusions - videoId else old.exclusions + (videoId to profiles))
        }
        return active.mapTo(mutableSetOf()) { it.lowercase() }
    }

    private fun remember(stream: ExcorStream) {
        state.update { old ->
            val entries = if (old.remembered.size >= MAX_REMEMBERED_URLS) linkedMapOf() else old.remembered.toMutableMap()
            entries[stream.url] = stream
            old.copy(remembered = entries)
        }
    }

    private suspend fun savePlayerData(url: String, value: PlayerCacheEntry, persist: Boolean = true) {
        state.update { old ->
            val entries = old.playerData.toMutableMap()
            entries[url] = value
            while (entries.size > MAX_MEMORY_PLAYER_DATA) entries.remove(entries.keys.first())
            old.copy(playerData = entries)
        }
        if (persist && host.configStore.enabled) {
            try {
                persistentPlayerData?.write(url, value)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                log(ExcorLogLevel.WARN, "could not persist player cache")
            }
        }
    }

    private suspend fun clearPlayerData() {
        state.update { it.copy(playerData = emptyMap()) }
        try {
            persistentPlayerData?.clear()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            log(ExcorLogLevel.WARN, "could not clear player cache")
        }
    }

    private fun log(level: ExcorLogLevel, message: String, trackId: String? = null) =
        host.log(level, "Excor $message", trackId)

    private fun mediaHeaders(userAgent: String, session: ExcorSession): Map<String, String> = buildMap {
        put("User-Agent", userAgent)
        put("Accept", "*/*")
        put("Accept-Language", acceptLanguage(session))
        put("Referer", "https://www.youtube.com/")
        put("Origin", "https://www.youtube.com")
        session.visitorData?.takeIf(String::isNotBlank)?.let { put("X-Goog-Visitor-Id", it) }
        session.cookie?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
    }

    private fun watchHeaders(session: ExcorSession): Map<String, String> = youtubeHeaders(session, DESKTOP_UA)

    private fun youtubeHeaders(session: ExcorSession, userAgent: String): Map<String, String> = buildMap {
        put("User-Agent", userAgent)
        put("Accept", "*/*")
        put("Accept-Language", acceptLanguage(session))
        put("Referer", "https://www.youtube.com/")
        put("Origin", "https://www.youtube.com")
        session.visitorData?.takeIf(String::isNotBlank)?.let { put("X-Goog-Visitor-Id", it) }
        session.cookie?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
    }

    private fun acceptLanguage(session: ExcorSession): String {
        val language = session.language.ifBlank { "en" }
        val locale = if (session.region.isBlank()) language else "$language-${session.region}"
        return "$locale,$language;q=0.9"
    }

    private data class EngineState(
        val remembered: Map<String, ExcorStream> = emptyMap(),
        val exclusions: Map<String, Map<String, Long>> = emptyMap(),
        val playerData: Map<String, PlayerCacheEntry> = emptyMap(),
    )

    private data class LoadedPlayer(
        val url: String,
        val entry: PlayerCacheEntry,
        val source: String?,
    )

    private data class CipherLink(
        val url: String,
        val encryptedSignature: String?,
        val signedSignature: String?,
        val signatureParameter: String,
    )

    private fun parseCipher(value: String?): CipherLink? {
        if (value == null || value.length > MAX_CIPHER_LENGTH) return null
        val parameters = runCatching { Url("https://localhost/?$value").parameters }.getOrNull() ?: return null
        val url = parameters["url"] ?: return null
        return CipherLink(
            url = url,
            encryptedSignature = parameters["s"],
            signedSignature = parameters["sig"] ?: parameters["signature"],
            signatureParameter = parameters["sp"]?.takeIf(String::isNotBlank) ?: "signature",
        )
    }

    private fun queryParameter(url: String, name: String): String? =
        runCatching { Url(url).parameters[name] }.getOrNull()

    private fun safePlayerUrl(value: String): String? {
        val absolute = when {
            value.startsWith("//") -> "https:$value"
            value.startsWith('/') -> "https://www.youtube.com$value"
            else -> value
        }
        val parsed = runCatching { Url(absolute) }.getOrNull() ?: return null
        if (parsed.protocol.name != "https" || parsed.port != 443 ||
            parsed.user != null || parsed.password != null ||
            parsed.host !in setOf("www.youtube.com", "youtube.com") ||
            !parsed.encodedPath.startsWith("/s/player/")
        ) return null
        return absolute
    }

    private fun updateState(block: (EngineState) -> EngineState) {
        while (true) {
            val old = state.value
            val next = block(old)
            if (state.compareAndSet(old, next)) return
        }
    }

    private companion object {
        val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
        const val DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36"
        const val WATCH_PAGE_LIMIT = 5_000_000
        const val PLAYER_SCRIPT_LIMIT = 2_000_000
        const val PLAYER_RESPONSE_LIMIT = 2_000_000
        const val MAX_CIPHER_LENGTH = 16_384
        const val EXCLUSION_MS = 10 * 60 * 1000L
        const val MAX_REMEMBERED_URLS = 64
        const val MAX_MEMORY_PLAYER_DATA = 3
    }
}
