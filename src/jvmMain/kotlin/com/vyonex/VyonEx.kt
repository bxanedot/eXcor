package com.vyonex

import com.vyonex.internal.Engine
import com.vyonex.internal.PlayerStore
import com.vyonex.internal.primary.extraction.TokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

interface VyonExHost : VyonExHostServices {
    val tokenProvider: TokenProvider? get() = null
    val filesDir: File? get() = null
    val httpClient: OkHttpClient
}

class VyonEx(host: VyonExHost) {
    private val engine = Engine(
        host = host,
        tokenProvider = host.tokenProvider,
        // Built on the host's client so cookies, proxies and interceptors are shared.
        http = HttpClient(OkHttp) {
            engine { preconfigured = host.httpClient }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 20_000
            }
        },
        store = host.filesDir?.let { DiskPlayerStore(File(it, "vyonex-players")) },
    )

    fun init() = engine.initialize()

    fun initializeFallback() {}

    suspend fun audioCandidates(videoId: String): List<VyonExAudioCandidate> = engine.audioCandidates(videoId)

    suspend fun extract(
        videoId: String,
        maxKbps: Int,
        skipClients: Set<String> = emptySet(),
        requireM4a: Boolean = false,
    ): VyonExStream? = engine.extract(videoId, maxKbps, skipClients, requireM4a)

    suspend fun extractVideo(
        videoId: String,
        maxHeight: Int = 1080,
        skipClients: Set<String> = emptySet(),
        requireAvc: Boolean = false,
    ): VyonExVideoStream? = engine.extractVideo(videoId, maxHeight, skipClients, requireAvc)

    suspend fun metadata(videoId: String): VyonExMetadata? = engine.metadata(videoId)

    fun headersFor(url: String): Map<String, String>? = engine.headersFor(url)

    fun onRefused(url: String): String? = engine.onRefused(url)

    fun exclude(videoId: String, profileId: String) = engine.exclude(videoId, profileId)

    fun onSessionChanged() = engine.onSessionChanged()
}

/** Prepared players on disk: written to a temp file and renamed, newest three kept. A failing disk just means no cache. */
internal class DiskPlayerStore(private val dir: File) : PlayerStore {
    override suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        runCatching { file(key).takeIf { it.isFile }?.readText() }.getOrNull()
    }

    override suspend fun write(key: String, value: String) = withContext(Dispatchers.IO) {
        runCatching {
            dir.mkdirs()
            val tmp = File.createTempFile("player", ".tmp", dir)
            try {
                tmp.writeText(value)
                Files.move(tmp.toPath(), file(key).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                tmp.delete()
            }
            dir.listFiles { f -> f.name.endsWith(".js") }.orEmpty()
                .sortedByDescending { it.lastModified() }.drop(3).forEach { it.delete() }
        }
        Unit
    }

    override suspend fun delete(key: String) = withContext(Dispatchers.IO) {
        file(key).delete()
        Unit
    }

    private fun file(key: String) = File(dir, "$key.js")
}
