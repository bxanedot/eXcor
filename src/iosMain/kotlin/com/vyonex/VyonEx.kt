package com.vyonex

import com.vyonex.internal.Engine
import com.vyonex.internal.primary.extraction.TokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout

interface VyonExHost : VyonExHostServices {
    val tokenProvider: TokenProvider? get() = null
}

class VyonEx(host: VyonExHost) {
    // Own Darwin client; caches stay in memory on iOS.
    private val engine = Engine(
        host = host,
        tokenProvider = host.tokenProvider,
        http = HttpClient(Darwin) {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 20_000
            }
        },
        store = null,
    )

    fun initialize() = engine.initialize()

    fun initializeFallback() {}

    @Throws(Throwable::class)
    suspend fun audioCandidates(videoId: String): List<VyonExAudioCandidate> = engine.audioCandidates(videoId)

    @Throws(Throwable::class)
    suspend fun extract(
        videoId: String,
        maxKbps: Int,
        skipClients: Set<String> = emptySet(),
        requireM4a: Boolean = false,
    ): VyonExStream? = engine.extractOrWatchPage(videoId, maxKbps, skipClients, requireM4a)

    @Throws(Throwable::class)
    suspend fun extractVideo(
        videoId: String,
        maxHeight: Int = 1080,
        skipClients: Set<String> = emptySet(),
        requireAvc: Boolean = false,
    ): VyonExVideoStream? = engine.extractVideo(videoId, maxHeight, skipClients, requireAvc)

    @Throws(Throwable::class)
    suspend fun metadata(videoId: String): VyonExMetadata? = engine.metadata(videoId)

    fun headersFor(url: String): Map<String, String>? = engine.headersFor(url)

    fun onRefused(url: String): String? = engine.onRefused(url)

    fun exclude(videoId: String, profileId: String) = engine.exclude(videoId, profileId)

    fun onSessionChanged() = engine.onSessionChanged()
}
