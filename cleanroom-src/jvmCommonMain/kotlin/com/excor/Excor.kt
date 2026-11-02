package com.excor

import com.excor.catalog.ExcorMusicCatalog
import com.excor.music.ExcorMusic
import com.excor.music.ExcorMusicConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.util.concurrent.TimeUnit

class Excor(host: ExcorHost) {
    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 20_000
        }
        engine {
            preconfigured = host.httpClient
            config {
                followRedirects(false)
                followSslRedirects(false)
                callTimeout(30, TimeUnit.SECONDS)
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(20, TimeUnit.SECONDS)
            }
        }
    }
    val musicCatalog = ExcorMusicCatalog(client, host.catalogTokenProvider)
    /** Spotify, Apple Music and Deezer metadata, matching, sync and authorized-playback decisions. */
    val music = ExcorMusic(client, host.musicConfig ?: ExcorMusicConfig(tokens = host.catalogTokenProvider), host::nowMs)
    private val engine = ExcorEngine(
        host = host,
        tokenProvider = host.tokenProvider,
        http = client,
        persistentPlayerData = PlayerDataDiskStore(host.filesDir) { host.configStore.enabled },
    )

    fun init() = engine.initialize()
    fun initializeFallback() = engine.initializeFallback()
    /** Title, channel, duration, views, dates and playability; null when YouTube has no such video. */
    @Throws(Throwable::class)
    suspend fun videoInfo(videoId: String): ExcorVideoInfo? = engine.videoInfo(videoId)
    /** YouTube search: videos, Shorts, channels and playlists; pass `page.continuation` back for more, or a `page.filters` entry's params to filter. */
    @Throws(Throwable::class)
    suspend fun search(query: String, continuation: String? = null, filterParams: String? = null): ExcorYouTubeSearchPage? = engine.search(query, continuation, filterParams)
    /** A YouTube playlist: details and videos; pass `page.continuation` back for more videos. */
    @Throws(Throwable::class)
    suspend fun playlist(playlistId: String, continuation: String? = null): ExcorYouTubePlaylist? = engine.playlist(playlistId, continuation)
    /**
     * A YouTube channel (UC… ID): details plus one tab (uploads by default; Shorts, Live or Playlists on request).
     * Pass `channel.continuation` back for more of that tab.
     */
    @Throws(Throwable::class)
    suspend fun channel(channelId: String, continuation: String? = null, tab: ExcorYouTubeChannelTab = ExcorYouTubeChannelTab.VIDEOS): ExcorYouTubeChannel? =
        engine.channel(channelId, continuation, tab)
    /** A channel's About panel: description, counts, join date, country and links. */
    @Throws(Throwable::class)
    suspend fun channelAbout(channelId: String): ExcorYouTubeChannelAbout? = engine.channelAbout(channelId)
    /** A video's chapters, in start order; empty when it has none. */
    @Throws(Throwable::class)
    suspend fun chapters(videoId: String): List<ExcorYouTubeChapter>? = engine.chapters(videoId)
    /** Videos YouTube suggests next to a video; pass `related.continuation` back for more. */
    @Throws(Throwable::class)
    suspend fun related(videoId: String, continuation: String? = null): ExcorYouTubeRelated? = engine.related(videoId, continuation)

    /** A video's comments, Top order or [newestFirst]; pass `page.continuation` back for more. */
    @Throws(Throwable::class)
    suspend fun comments(videoId: String, continuation: String? = null, newestFirst: Boolean = false): ExcorYouTubeCommentsPage? =
        engine.comments(videoId, continuation, newestFirst)
    /** YouTube search completions for a partial query. */
    @Throws(Throwable::class)
    suspend fun suggestions(query: String): List<String> = engine.suggestions(query)
    suspend fun audioCandidates(videoId: String): List<ExcorAudioCandidate> = engine.audioCandidates(videoId)
    suspend fun extract(
        videoId: String,
        maxKbps: Int,
        skipClients: Set<String> = emptySet(),
        requireM4a: Boolean = false,
    ): ExcorStream? = engine.extract(videoId, maxKbps, skipClients, requireM4a, watchFallback = false)
    fun headersFor(url: String): Map<String, String>? = engine.headersFor(url)
    fun onRefused(url: String): String? = engine.onRefused(url)
    fun exclude(videoId: String, profileId: String) = engine.exclude(videoId, profileId)
    fun onSessionChanged() = engine.onSessionChanged()
}
