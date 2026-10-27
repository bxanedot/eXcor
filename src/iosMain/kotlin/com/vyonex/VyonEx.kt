package com.vyonex

import com.vyonex.internal.Engine
import com.vyonex.internal.mergePairOf
import com.vyonex.internal.parseRef
import com.vyonex.internal.primary.extraction.TokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout

interface VyonExHost : VyonExHostServices {
    val tokenProvider: TokenProvider? get() = null
}

class VyonEx(host: VyonExHost) {
    // Own Darwin client; caches stay in memory on iOS.
    private val http = HttpClient(Darwin) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 20_000
        }
    }
    private val engine = Engine(host = host, tokenProvider = host.tokenProvider, http = http, store = null)

    fun initialize() = engine.initialize()

    fun initializeFallback() {}

    /** What a YouTube or YouTube Music link, or a bare ID, points at; null if it is neither. Makes no request. */
    fun parse(input: String): VyonExRef? = parseRef(input)

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

    @Throws(Throwable::class)
    suspend fun formats(videoId: String, skipClients: Set<String> = emptySet()): VyonExFormats? = engine.formats(videoId, skipClients)

    /** Downloads [format] into [sink] from byte [from] in 10 MiB ranged requests; returns the position reached. */
    @Throws(Throwable::class)
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
    @Throws(Throwable::class)
    suspend fun playlist(playlistId: String): VyonExPlaylist? = engine.playlist(playlistId)

    /** The next page of a list, from the previous page's `continuation`. */
    @Throws(Throwable::class)
    suspend fun playlistMore(continuation: String): VyonExListPage = engine.playlistMore(continuation)

    /** A YouTube Music album page (`MPREb_…`, from `parse`): title, artist, year, tracks; null when there is none. */
    @Throws(Throwable::class)
    suspend fun album(browseId: String): VyonExAlbum? = engine.album(browseId)

    /** A YouTube Music artist page (`UC…` channel ID): top songs, playlists of all songs and videos, releases. */
    @Throws(Throwable::class)
    suspend fun artist(channelId: String): VyonExArtist? = engine.artist(channelId)

    /** A mix (`RD…`) and its first videos; [videoId] picks where it starts. */
    @Throws(Throwable::class)
    suspend fun mix(playlistId: String, videoId: String? = null): VyonExMix? = engine.mix(playlistId, videoId)

    /** More of a mix, from the previous page's `continuation`; mixes keep going until YouTube has nothing new. */
    @Throws(Throwable::class)
    suspend fun mixMore(continuation: String): VyonExListPage = engine.mixMore(continuation)

    /** The videos YouTube recommends beside [videoId] (logged out), then `relatedMore` for more. */
    @Throws(Throwable::class)
    suspend fun related(videoId: String): VyonExListPage = engine.related(videoId)

    @Throws(Throwable::class)
    suspend fun relatedMore(continuation: String): VyonExListPage = engine.relatedMore(continuation)

    fun headersFor(url: String): Map<String, String>? = engine.headersFor(url)

    fun onRefused(url: String): String? = engine.onRefused(url)

    fun exclude(videoId: String, profileId: String) = engine.exclude(videoId, profileId)

    fun onSessionChanged() = engine.onSessionChanged()

    /** Stops background work, frees the loaded player and closes the HTTP client; do not use this instance afterwards. */
    fun close() {
        engine.close()
        http.close()
    }
}
