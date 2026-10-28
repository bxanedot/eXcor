package com.vyonex

import com.vyonex.internal.Engine
import com.vyonex.internal.SoundCloudEngine
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

    /** SoundCloud's public tracks and sets, on the same host and network client. */
    val soundCloud = VyonExSoundCloud(SoundCloudEngine(host, http))

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

    /** The video's chapters (from YouTube's chapter list, else timestamps in its description); empty when it has none. */
    @Throws(Throwable::class)
    suspend fun chapters(videoId: String): List<VyonExChapter> = engine.chapters(videoId)

    /** A video's public comments, top first or [newestFirst]; empty when comments are off. Logged out. */
    @Throws(Throwable::class)
    suspend fun comments(videoId: String, newestFirst: Boolean = false): VyonExCommentPage = engine.comments(videoId, newestFirst)

    /** The next page of comments, or a comment's replies (its `repliesContinuation`). */
    @Throws(Throwable::class)
    suspend fun commentsMore(continuation: String): VyonExCommentPage = engine.commentsMore(continuation)

    /** The text of a caption track from [metadata] in [format], translated into [translateTo] when given. */
    @Throws(Throwable::class)
    suspend fun captionText(track: VyonExCaptionTrack, format: VyonExCaptionFormat, translateTo: String? = null): String =
        engine.captionText(track, format, translateTo)

    @Throws(Throwable::class)
    suspend fun formats(videoId: String, skipClients: Set<String> = emptySet()): VyonExFormats? = engine.formats(videoId, skipClients)

    /** The HLS or DASH manifest of a live stream (or of any video whose client offers one); null when none answers. */
    @Throws(Throwable::class)
    suspend fun manifest(videoId: String, skipClients: Set<String> = emptySet()): VyonExManifest? = engine.manifest(videoId, skipClients)

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

    /** A YouTube channel (`UC…`): title, handle, description, pictures and its tabs; null when there is none. Logged out. */
    @Throws(Throwable::class)
    suspend fun channel(channelId: String): VyonExChannel? = engine.channel(channelId)

    /** The first page of a channel tab: its videos, Shorts, live streams or playlists. */
    @Throws(Throwable::class)
    suspend fun channelTab(channelId: String, tab: VyonExChannelTab): VyonExChannelPage = engine.channelTab(channelId, tab)

    /** The next page of a channel tab. */
    @Throws(Throwable::class)
    suspend fun channelTabMore(continuation: String): VyonExChannelPage = engine.channelTabMore(continuation)

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

    /**
     * YouTube's search results (videos, playlists, channels; Shorts apart in `shorts`), then `searchMore` for more, and
     * the filters YouTube offers for them. Logged out.
     */
    @Throws(Throwable::class)
    suspend fun search(query: String): VyonExSearchPage = engine.search(query)

    /** The search for one [type] of result only. */
    @Throws(Throwable::class)
    suspend fun search(query: String, type: VyonExSearchType): VyonExSearchPage = engine.search(query, type.params)

    /** The search with [filter], one of a page's `filters`, added to those already applied. */
    @Throws(Throwable::class)
    suspend fun search(query: String, filter: VyonExSearchFilter): VyonExSearchPage =
        engine.search(query, requireNotNull(filter.params) { "VyonEx: the filter ${filter.label} cannot be asked (selected or not available)" })

    /** What YouTube's search box suggests for [query], in the session's language; empty for an empty query. */
    @Throws(Throwable::class)
    suspend fun suggestions(query: String): List<String> = engine.suggestions(query)

    @Throws(Throwable::class)
    suspend fun searchMore(continuation: String): VyonExSearchPage = engine.searchMore(continuation)

    /** YouTube Music's top results: songs, videos, albums, artists, playlists (one page). Logged out. */
    @Throws(Throwable::class)
    suspend fun searchMusic(query: String): VyonExSearchPage = engine.searchMusic(query)

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
