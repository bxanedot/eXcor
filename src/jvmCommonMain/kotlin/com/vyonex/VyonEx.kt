package com.vyonex

import com.vyonex.internal.Engine
import com.vyonex.internal.mergePairOf
import com.vyonex.internal.parseRef
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
        store = host.filesDir?.let { DiskPlayerStore(File(it, "vyonex-players")) { host.configStore.enabled } },
    )

    fun init() = engine.initialize()

    fun initializeFallback() {}

    /** What a YouTube or YouTube Music link, or a bare ID, points at; null if it is neither. Makes no request. */
    fun parse(input: String): VyonExRef? = parseRef(input)

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

    /** The video's chapters (from YouTube's chapter list, else timestamps in its description); empty when it has none. */
    suspend fun chapters(videoId: String): List<VyonExChapter> = engine.chapters(videoId)

    /** The text of a caption track from [metadata] in [format], translated into [translateTo] when given. */
    suspend fun captionText(track: VyonExCaptionTrack, format: VyonExCaptionFormat, translateTo: String? = null): String =
        engine.captionText(track, format, translateTo)

    suspend fun formats(videoId: String, skipClients: Set<String> = emptySet()): VyonExFormats? = engine.formats(videoId, skipClients)

    /** The HLS or DASH manifest of a live stream (or of any video whose client offers one); null when none answers. */
    suspend fun manifest(videoId: String, skipClients: Set<String> = emptySet()): VyonExManifest? = engine.manifest(videoId, skipClients)

    /** Downloads [format] into [sink] from byte [from] in 10 MiB ranged requests; returns the position reached. */
    suspend fun download(
        format: VyonExFormat,
        headers: Map<String, String>,
        sink: VyonExSink,
        from: Long = 0,
        onProgress: ((written: Long, total: Long?) -> Unit)? = null,
    ): Long = engine.download(format, headers, sink, from, onProgress)

    /** The best video and audio of [formats] that merge into one [container] file, at most [maxHeight] tall. */
    fun mergePair(formats: VyonExFormats, maxHeight: Int, container: VyonExContainer): VyonExMergePair? =
        mergePairOf(formats.formats, maxHeight, container)

    /** A playlist and its first page of videos; null when YouTube shows no such playlist. Logged out. */
    suspend fun playlist(playlistId: String): VyonExPlaylist? = engine.playlist(playlistId)

    /** The next page of a list, from the previous page's `continuation`. */
    suspend fun playlistMore(continuation: String): VyonExListPage = engine.playlistMore(continuation)

    /** A YouTube Music album page (`MPREb_…`, from `parse`): title, artist, year, tracks; null when there is none. */
    suspend fun album(browseId: String): VyonExAlbum? = engine.album(browseId)

    /** A YouTube Music artist page (`UC…` channel ID): top songs, playlists of all songs and videos, releases. */
    suspend fun artist(channelId: String): VyonExArtist? = engine.artist(channelId)

    /** A mix (`RD…`) and its first videos; [videoId] picks where it starts. */
    suspend fun mix(playlistId: String, videoId: String? = null): VyonExMix? = engine.mix(playlistId, videoId)

    /** More of a mix, from the previous page's `continuation`; mixes keep going until YouTube has nothing new. */
    suspend fun mixMore(continuation: String): VyonExListPage = engine.mixMore(continuation)

    /** The videos YouTube recommends beside [videoId] (logged out), then `relatedMore` for more. */
    suspend fun related(videoId: String): VyonExListPage = engine.related(videoId)

    suspend fun relatedMore(continuation: String): VyonExListPage = engine.relatedMore(continuation)

    /** YouTube's search results (videos and playlists), then `searchMore` for more. Logged out. */
    suspend fun search(query: String): VyonExSearchPage = engine.search(query)

    suspend fun searchMore(continuation: String): VyonExSearchPage = engine.searchMore(continuation)

    /** YouTube Music's top results: songs, videos, albums, artists, playlists (one page). Logged out. */
    suspend fun searchMusic(query: String): VyonExSearchPage = engine.searchMusic(query)

    fun headersFor(url: String): Map<String, String>? = engine.headersFor(url)

    fun onRefused(url: String): String? = engine.onRefused(url)

    fun exclude(videoId: String, profileId: String) = engine.exclude(videoId, profileId)

    fun onSessionChanged() = engine.onSessionChanged()

    /**
     * Stops background work and frees the loaded player; do not use this instance afterwards. The Ktor client
     * stays open: closing its OkHttp engine would shut down the dispatcher and connection pool it shares with
     * [VyonExHost.httpClient].
     */
    fun close() = engine.close()
}

/**
 * Prepared players on disk: written to a temp file and renamed, newest three kept. A failing disk just means no cache.
 * While [enabled] is false nothing is read or written (BRIEF 4.7: everything works without persistence).
 */
internal class DiskPlayerStore(private val dir: File, private val enabled: () -> Boolean) : PlayerStore {
    override suspend fun read(key: String): String? = if (!enabled()) null else withContext(Dispatchers.IO) {
        runCatching { file(key).takeIf { it.isFile }?.readText() }.getOrNull()
    }

    override suspend fun write(key: String, value: String) = if (!enabled()) Unit else withContext(Dispatchers.IO) {
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

    // Deleting even while disabled, so a refused player is not read back once the store is enabled again.
    override suspend fun delete(key: String) = withContext(Dispatchers.IO) {
        file(key).delete()
        Unit
    }

    private fun file(key: String) = File(dir, "$key.js")
}
