package com.excor

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
    private val engine = ExcorEngine(host, host.tokenProvider, client)

    fun initialize() = engine.initialize()
    fun initializeFallback() = engine.initializeFallback()
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
