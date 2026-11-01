package com.excor

import com.excor.catalog.ExcorMusicCatalog
import com.excor.music.ExcorMusic
import com.excor.music.ExcorMusicConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout

class Excor(host: ExcorHost) {
    private val client = HttpClient(Darwin) {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 20_000
        }
        engine {
            configureRequest { setTimeoutInterval(30.0) }
        }
    }
    val musicCatalog = ExcorMusicCatalog(client, host.catalogTokenProvider)
    /** Spotify, Apple Music and Deezer metadata, matching, sync and authorized-playback decisions. */
    val music = ExcorMusic(client, host.musicConfig ?: ExcorMusicConfig(tokens = host.catalogTokenProvider), host::nowMs)
    private val engine = ExcorEngine(host, host.tokenProvider, client)

    fun initialize() = engine.initialize()
    fun initializeFallback() = engine.initializeFallback()
    /** Title, channel, duration, views, dates and playability; null when YouTube has no such video. */
    @Throws(Throwable::class)
    suspend fun videoInfo(videoId: String): ExcorVideoInfo? = engine.videoInfo(videoId)
    suspend fun audioCandidates(videoId: String): List<ExcorAudioCandidate> = engine.audioCandidates(videoId)
    suspend fun extract(
        videoId: String,
        maxKbps: Int,
        skipClients: Set<String> = emptySet(),
        requireM4a: Boolean = false,
    ): ExcorStream? = engine.extract(videoId, maxKbps, skipClients, requireM4a, watchFallback = true)
    fun headersFor(url: String): Map<String, String>? = engine.headersFor(url)
    fun onRefused(url: String): String? = engine.onRefused(url)
    fun exclude(videoId: String, profileId: String) = engine.exclude(videoId, profileId)
    fun onSessionChanged() = engine.onSessionChanged()
}
