package com.excor

import com.excor.catalog.ExcorMusicCatalog
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
    private val engine = ExcorEngine(
        host = host,
        tokenProvider = host.tokenProvider,
        http = client,
        persistentPlayerData = PlayerDataDiskStore(host.filesDir) { host.configStore.enabled },
    )

    fun init() = engine.initialize()
    fun initializeFallback() = engine.initializeFallback()
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
