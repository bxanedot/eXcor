package com.excor

import com.excor.internal.primary.extraction.PoTokenResult
import com.excor.internal.primary.extraction.TokenProvider
import com.excor.music.ExcorArtistRole
import com.excor.music.ExcorMatchOptions
import com.excor.music.ExcorMatchVerdict
import com.excor.music.ExcorMusicTrack
import com.excor.music.ExcorTrackMatch
import com.excor.music.excorRankMatches
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
import kotlin.random.Random
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

    /** Video metadata, available even when YouTube withholds the streams; null when YouTube has no such video. */
    suspend fun videoInfo(videoId: String): ExcorVideoInfo? {
        require(VIDEO_ID.matches(videoId)) { "not a YouTube video ID" }
        val session = host.session()
        val body = postPlayer(buildMetadataRequest(videoId, session)) ?: return null
        decodeVideoInfo(body, videoId)?.let { return it }
        // YouTube's bot check strips videoDetails from the player response but leaves the watch page readable, so the
        // details come from there, with the player's playability kept. A response that isn't a player response at all
        // (no playabilityStatus) is still null.
        val playability = playabilityOf(body) ?: return null
        if (playability.first == "ERROR") return null
        return decodeWatchVideoInfo(postPlayer(buildNextRequest(videoId, null, session)) ?: return null, videoId, playability)
    }

    /**
     * YouTube search; [continuation] from a previous page continues it, keeping that page's filters. [filterParams] is
     * an [ExcorYouTubeSearchFilter.params] from an earlier page. Null when YouTube's answer is not a result page.
     */
    suspend fun search(query: String, continuation: String?, filterParams: String? = null): ExcorYouTubeSearchPage? {
        val q = query.trim()
        require(continuation != null || q.length in 1..200) { "query must be 1 to 200 characters" }
        require(continuation == null || continuation.length <= MAX_CONTINUATION) { "continuation too long" }
        require(filterParams == null || SEARCH_PARAMS.matches(filterParams)) { "not search filter params" }
        val body = postPlayer(buildSearchRequest(q, continuation, host.session(), filterParams)) ?: return null
        return decodeSearchPage(body)
    }

    /**
     * The YouTube videos that match a track from another provider, best first: one search for its primary artist and
     * title, each video read as a track and scored by [excorRankMatches]. DIFFERENT verdicts and live streams are
     * dropped. A music video whose intro or outro runs past the duration tolerance is rejected like any other
     * duration mismatch, which leaves the "Topic" upload of the recording.
     */
    suspend fun findOnYouTube(track: ExcorMusicTrack, options: ExcorMatchOptions): List<ExcorTrackMatch> {
        val artist = track.artists.firstOrNull { it.role == ExcorArtistRole.PRIMARY } ?: track.artists.firstOrNull()
        val query = listOfNotNull(artist?.name, track.displayTitle).joinToString(" ").take(200)
        val page = search(query, null) ?: return emptyList()
        return excorRankMatches(track, page.videos.filter { !it.isLive }.map { it.asMusicTrack() }, options).filter { it.verdict != ExcorMatchVerdict.DIFFERENT }
    }

    /** A playlist page; [continuation] from a previous page continues it. Null when YouTube's answer is not a playlist page. */
    suspend fun playlist(playlistId: String, continuation: String?): ExcorYouTubePlaylist? {
        require(PLAYLIST_ID.matches(playlistId)) { "not a YouTube playlist ID" }
        require(continuation == null || continuation.length <= MAX_CONTINUATION) { "continuation too long" }
        val body = postPlayer(buildBrowseRequest("VL$playlistId", continuation, host.session())) ?: return null
        return decodePlaylistPage(body, playlistId)
    }

    /**
     * A channel's details and first page of uploads (two requests: the home page for details and the Videos tab), or,
     * with [continuation], the next page of uploads only. Null when YouTube's answer is not a channel page, or when a
     * tab the channel has fails to load.
     */
    suspend fun channel(channelId: String, continuation: String?, tab: ExcorYouTubeChannelTab): ExcorYouTubeChannel? {
        require(CHANNEL_ID.matches(channelId)) { "not a YouTube channel ID (UC…)" }
        require(continuation == null || continuation.length <= MAX_CONTINUATION) { "continuation too long" }
        val session = host.session()
        if (continuation != null) {
            val page = decodeChannelTab(postPlayer(buildBrowseRequest(null, continuation, session)) ?: return null) ?: return null
            return ExcorYouTubeChannel(channelId, null, null, null, null, null, emptyList(), null, null, emptyList(), emptyList(), page.videos, page.continuation, page.playlists, page.posts)
        }
        val home = decodeChannelHome(postPlayer(buildBrowseRequest(channelId, null, session)) ?: return null, channelId) ?: return null
        // A channel without this tab (no Live, no Shorts) gives its details with an empty list. A tab it has that fails
        // to load gives null, so a refused or broken answer is not mistaken for an empty tab.
        val params = home.tabParams[tab] ?: return home.channel
        val page = decodeChannelTab(postPlayer(buildBrowseRequest(channelId, null, session, params)) ?: return null) ?: return null
        return home.channel.copy(videos = page.videos, continuation = page.continuation, playlists = page.playlists, posts = page.posts)
    }

    /** A channel's About panel: two requests (the channel home, then its panel). Null when YouTube gives no panel. */
    suspend fun channelAbout(channelId: String): ExcorYouTubeChannelAbout? {
        require(CHANNEL_ID.matches(channelId)) { "not a YouTube channel ID (UC…)" }
        val session = host.session()
        val home = decodeChannelHome(postPlayer(buildBrowseRequest(channelId, null, session)) ?: return null, channelId) ?: return null
        val token = home.aboutToken ?: return null
        return decodeChannelAbout(postPlayer(buildBrowseRequest(null, token, session)) ?: return null, channelId)
    }

    /** A video's chapters (one request); empty when it has none, null when YouTube's answer is not a watch page. */
    suspend fun chapters(videoId: String): List<ExcorYouTubeChapter>? {
        require(VIDEO_ID.matches(videoId)) { "not a YouTube video ID" }
        return decodeChapters(postPlayer(buildNextRequest(videoId, null, host.session())) ?: return null)
    }

    /**
     * A window of the mix for [videoId] (one request); [playlistId] defaults to the video's own mix. Null when YouTube's
     * answer has no panel for that playlist.
     */
    suspend fun mix(videoId: String, playlistId: String, index: Int?): ExcorYouTubeMix? {
        require(VIDEO_ID.matches(videoId)) { "not a YouTube video ID" }
        require(PLAYLIST_ID.matches(playlistId)) { "not a YouTube playlist ID" }
        require(index == null || index in 0..10_000) { "index out of range" }
        return decodeMix(postPlayer(buildWatchPlaylistRequest(videoId, playlistId, index, host.session())) ?: return null, playlistId)
    }

    /**
     * YouTube Music search; [continuation] continues a filtered search, [filterParams] is an
     * [ExcorYouTubeSearchFilter.params] from an earlier page. Null when YouTube Music's answer is not a result page.
     */
    suspend fun musicSearch(query: String, continuation: String?, filterParams: String?): ExcorYouTubeMusicSearchPage? {
        val q = query.trim()
        require(continuation != null || q.length in 1..200) { "query must be 1 to 200 characters" }
        require(continuation == null || continuation.length <= MAX_CONTINUATION) { "continuation too long" }
        require(filterParams == null || SEARCH_PARAMS.matches(filterParams)) { "not search filter params" }
        return decodeMusicSearch(postPlayer(buildMusicSearchRequest(q, continuation, filterParams, host.session())) ?: return null)
    }

    /** YouTube Music search completions for a partial query; empty when there are none or the request fails. */
    suspend fun musicSuggestions(input: String): List<String> {
        val q = input.trim().take(200)
        if (q.isEmpty()) return emptyList()
        return decodeMusicSuggestions(postPlayer(buildMusicSuggestionsRequest(q, host.session())) ?: return emptyList())
    }

    /** A YouTube Music artist page (UC… channel ID); null when YouTube Music's answer is not one. */
    suspend fun musicArtist(channelId: String): ExcorYouTubeMusicArtist? {
        require(CHANNEL_ID.matches(channelId)) { "not a YouTube channel ID (UC…)" }
        return decodeMusicArtist(postPlayer(buildMusicBrowseRequest(channelId, null, host.session())) ?: return null, channelId)
    }

    /** A YouTube Music playlist page; [continuation] gives its next tracks. Null when the answer is not a playlist page. */
    suspend fun musicPlaylist(playlistId: String, continuation: String?): ExcorYouTubeMusicPlaylist? {
        require(PLAYLIST_ID.matches(playlistId)) { "not a YouTube playlist ID" }
        require(continuation == null || continuation.length <= MAX_CONTINUATION) { "continuation too long" }
        val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        return decodeMusicPlaylist(postPlayer(buildMusicBrowseRequest(browseId, continuation, host.session())) ?: return null, playlistId.removePrefix("VL"))
    }

    /**
     * A window of a YouTube Music radio or watch queue: for [videoId] (its radio by default) or a queue [playlistId]
     * such as an artist's radio; [continuation] gives the next window. Null when the answer has no queue.
     */
    suspend fun musicQueue(videoId: String?, playlistId: String?, continuation: String?): ExcorYouTubeMusicQueue? {
        require(continuation != null || videoId != null || playlistId != null) { "videoId, playlistId or continuation is required" }
        require(videoId == null || VIDEO_ID.matches(videoId)) { "not a YouTube video ID" }
        require(playlistId == null || PLAYLIST_ID.matches(playlistId)) { "not a YouTube playlist ID" }
        require(continuation == null || continuation.length <= MAX_CONTINUATION) { "continuation too long" }
        return decodeMusicQueue(postPlayer(buildMusicQueueRequest(videoId, playlistId, continuation, host.session())) ?: return null)
    }

    /** Songs credited in a video's description (one request); empty when it credits none, null when not a watch page. */
    suspend fun songs(videoId: String): List<ExcorYouTubeSong>? {
        require(VIDEO_ID.matches(videoId)) { "not a YouTube video ID" }
        return decodeSongs(postPlayer(buildNextRequest(videoId, null, host.session())) ?: return null)
    }

    /** Videos YouTube suggests next to [videoId]; [continuation] gives more. Null when YouTube's answer is not a watch page. */
    suspend fun related(videoId: String, continuation: String?): ExcorYouTubeRelated? {
        require(VIDEO_ID.matches(videoId)) { "not a YouTube video ID" }
        require(continuation == null || continuation.length <= MAX_CONTINUATION) { "continuation too long" }
        return decodeRelated(postPlayer(buildNextRequest(videoId, continuation, host.session())) ?: return null)
    }

    /**
     * A page of [videoId]'s comments, YouTube's Top order or [newestFirst]. The first page takes two requests (the watch
     * page for the comments token, then the comments), three for newest first; null when comments are off or YouTube's
     * answer is not a comments page.
     */
    suspend fun comments(videoId: String, continuation: String?, newestFirst: Boolean): ExcorYouTubeCommentsPage? {
        require(VIDEO_ID.matches(videoId)) { "not a YouTube video ID" }
        require(continuation == null || continuation.length <= MAX_CONTINUATION) { "continuation too long" }
        val session = host.session()
        val token = continuation ?: commentsTokenOf(postPlayer(buildNextRequest(videoId, null, session)) ?: return null) ?: return null
        val page = decodeComments(postPlayer(buildNextRequest(null, token, session)) ?: return null) ?: return null
        // Newest first needs the sort token from the first (Top) page: one more request.
        if (!newestFirst || continuation != null) return page
        val newest = page.newestContinuation ?: return page
        return decodeComments(postPlayer(buildNextRequest(null, newest, session)) ?: return page) ?: page
    }

    /** Search completions for [query] in the session's language and region. */
    suspend fun suggestions(query: String): List<String> {
        val q = query.trim()
        require(q.length in 1..200) { "query must be 1 to 200 characters" }
        val session = host.session()
        val url = URLBuilder("https://suggestqueries-clients6.youtube.com/complete/search").apply {
            parameters.append("client", "youtube")
            parameters.append("ds", "yt")
            parameters.append("hl", session.language.ifBlank { "en" })
            parameters.append("gl", session.region.ifBlank { "US" })
            parameters.append("q", q)
        }.buildString()
        return decodeSuggestions(getText(url, youtubeHeaders(session, DESKTOP_UA), 64_000) ?: return emptyList())
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

    /**
     * InnerTube POSTs are reads, so a transient failure is retried: up to [INNERTUBE_ATTEMPTS] attempts on HTTP 429,
     * 500, 502, 503, 504 or a transport error, waiting a Retry-After of at most 5 s or an exponential backoff with
     * jitter. Any other status is final. Cancellation is never retried.
     */
    private suspend fun postPlayer(request: PlayerRequest): String? {
        var attempt = 1
        while (true) {
            val outcome = try {
                postOnce(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (attempt >= INNERTUBE_ATTEMPTS) throw failure
                PostOutcome.Retry(null)
            }
            when (outcome) {
                is PostOutcome.Done -> return outcome.body
                is PostOutcome.Retry -> {
                    if (attempt >= INNERTUBE_ATTEMPTS) return null
                    val backoff = 300L * (1L shl (attempt - 1)) + Random.nextLong(0, 200)
                    delay(outcome.retryAfterMs ?: backoff)
                    attempt++
                }
            }
        }
    }

    private sealed interface PostOutcome {
        class Done(val body: String?) : PostOutcome
        class Retry(val retryAfterMs: Long?) : PostOutcome
    }

    private suspend fun postOnce(request: PlayerRequest): PostOutcome {
        val response = http.post(request.url) {
            request.headers.forEach { (name, value) -> header(name, value) }
            contentType(ContentType.Application.Json)
            header(HttpHeaders.ContentLength, request.body.encodeToByteArray().size.toString())
            setBody(request.body)
        }
        if (!response.status.isSuccess()) {
            response.bodyAsChannel().cancel()
            val status = response.status.value
            if (status !in RETRYABLE_STATUSES) return PostOutcome.Done(null)
            val retryAfter = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()?.takeIf { it in 0..5 }?.times(1_000)
            return PostOutcome.Retry(retryAfter)
        }
        if ((response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L) > PLAYER_RESPONSE_LIMIT) {
            response.bodyAsChannel().cancel()
            return PostOutcome.Done(null)
        }
        return PostOutcome.Done(response.bodyAsText().takeIf { it.length <= PLAYER_RESPONSE_LIMIT })
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
        const val INNERTUBE_ATTEMPTS = 3
        val RETRYABLE_STATUSES = setOf(429, 500, 502, 503, 504)
        const val MAX_CIPHER_LENGTH = 16_384
        const val EXCLUSION_MS = 10 * 60 * 1000L
        const val MAX_REMEMBERED_URLS = 64
        const val MAX_MEMORY_PLAYER_DATA = 3
    }
}
