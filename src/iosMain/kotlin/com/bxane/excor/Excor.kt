package com.bxane.excor

import com.bxane.excor.internal.AppleMusicEngine
import com.bxane.excor.internal.Engine
import com.bxane.excor.internal.SoundCloudEngine
import com.bxane.excor.internal.mergePairOf
import com.bxane.excor.internal.parseRef
import com.bxane.excor.internal.primary.extraction.TokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout

interface ExcorHost : ExcorHostServices {
    val tokenProvider: TokenProvider? get() = null
}

class Excor(host: ExcorHost) {
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
    val soundCloud = ExcorSoundCloud(SoundCloudEngine(host, http))

    /** Apple Music metadata (songs, albums, artists, search) from Apple's iTunes Search API; no audio. */
    val appleMusic = ExcorAppleMusic(AppleMusicEngine(host, http))

    fun initialize() = engine.initialize()

    fun initializeFallback() {}

    /** What a YouTube or YouTube Music link, or a bare ID, points at; null if it is neither. Makes no request. */
    fun parse(input: String): ExcorRef? = parseRef(input)

    @Throws(Throwable::class)
    suspend fun audioCandidates(videoId: String): List<ExcorAudioCandidate> = engine.audioCandidates(videoId)

    @Throws(Throwable::class)
    suspend fun extract(
        videoId: String,
        maxKbps: Int,
        skipClients: Set<String> = emptySet(),
        requireM4a: Boolean = false,
    ): ExcorStream? = engine.extractOrWatchPage(videoId, maxKbps, skipClients, requireM4a)

    @Throws(Throwable::class)
    suspend fun extractVideo(
        videoId: String,
        maxHeight: Int = 1080,
        skipClients: Set<String> = emptySet(),
        requireAvc: Boolean = false,
    ): ExcorVideoStream? = engine.extractVideo(videoId, maxHeight, skipClients, requireAvc)

    @Throws(Throwable::class)
    suspend fun metadata(videoId: String): ExcorMetadata? = engine.metadata(videoId)

    /** The video's chapters (from YouTube's chapter list, else timestamps in its description); empty when it has none. */
    @Throws(Throwable::class)
    suspend fun chapters(videoId: String): List<ExcorChapter> = engine.chapters(videoId)

    /** A video's public comments, top first or [newestFirst]; empty when comments are off. Logged out. */
    @Throws(Throwable::class)
    suspend fun comments(videoId: String, newestFirst: Boolean = false): ExcorCommentPage = engine.comments(videoId, newestFirst)

    /** The next page of comments, or a comment's replies (its `repliesContinuation`). */
    @Throws(Throwable::class)
    suspend fun commentsMore(continuation: String): ExcorCommentPage = engine.commentsMore(continuation)

    /** The text of a caption track from [metadata] in [format], translated into [translateTo] when given. */
    @Throws(Throwable::class)
    suspend fun captionText(track: ExcorCaptionTrack, format: ExcorCaptionFormat, translateTo: String? = null): String =
        engine.captionText(track, format, translateTo)

    @Throws(Throwable::class)
    suspend fun formats(videoId: String, skipClients: Set<String> = emptySet()): ExcorFormats? = engine.formats(videoId, skipClients)

    /** The HLS or DASH manifest of a live stream (or of any video whose client offers one); null when none answers. */
    @Throws(Throwable::class)
    suspend fun manifest(videoId: String, skipClients: Set<String> = emptySet()): ExcorManifest? = engine.manifest(videoId, skipClients)

    /** Downloads [format] into [sink] from byte [from] in 10 MiB ranged requests; returns the position reached. */
    @Throws(Throwable::class)
    suspend fun download(
        format: ExcorFormat,
        headers: Map<String, String>,
        sink: ExcorSink,
        from: Long = 0,
        onProgress: ((written: Long, total: Long?) -> Unit)? = null,
    ): Long = engine.download(format, headers, sink, from, onProgress)

    /** The best video and audio of [formats] that merge into one [container] file, at most [maxHeight] tall. */
    fun mergePair(formats: ExcorFormats, maxHeight: Int, container: ExcorContainer): ExcorMergePair? =
        mergePairOf(formats.formats, maxHeight, container)

    /** A playlist and its first page of videos; null when YouTube shows no such playlist. Logged out. */
    @Throws(Throwable::class)
    suspend fun playlist(playlistId: String): ExcorPlaylist? = engine.playlist(playlistId)

    /** The next page of a list, from the previous page's `continuation`. */
    @Throws(Throwable::class)
    suspend fun playlistMore(continuation: String): ExcorListPage = engine.playlistMore(continuation)

    /** A YouTube Music album page (`MPREb_…`, from `parse`): title, artist, year, tracks; null when there is none. */
    @Throws(Throwable::class)
    suspend fun album(browseId: String): ExcorAlbum? = engine.album(browseId)

    /** A YouTube channel (`UC…`): title, handle, description, pictures and its tabs; null when there is none. Logged out. */
    @Throws(Throwable::class)
    suspend fun channel(channelId: String): ExcorChannel? = engine.channel(channelId)

    /** The first page of a channel tab: its videos, Shorts, live streams or playlists. */
    @Throws(Throwable::class)
    suspend fun channelTab(channelId: String, tab: ExcorChannelTab): ExcorChannelPage = engine.channelTab(channelId, tab)

    /** The next page of a channel tab. */
    @Throws(Throwable::class)
    suspend fun channelTabMore(continuation: String): ExcorChannelPage = engine.channelTabMore(continuation)

    /** A channel's community posts, first page (logged out); empty when it has none. Then `channelPostsMore`. */
    @Throws(Throwable::class)
    suspend fun channelPosts(channelId: String): ExcorPostPage = engine.channelPosts(channelId)

    @Throws(Throwable::class)
    suspend fun channelPostsMore(continuation: String): ExcorPostPage = engine.channelPostsMore(continuation)

    /** A YouTube Music artist page (`UC…` channel ID): top songs, playlists of all songs and videos, releases. */
    @Throws(Throwable::class)
    suspend fun artist(channelId: String): ExcorArtist? = engine.artist(channelId)

    /** A mix (`RD…`) and its first videos; [videoId] picks where it starts. */
    @Throws(Throwable::class)
    suspend fun mix(playlistId: String, videoId: String? = null): ExcorMix? = engine.mix(playlistId, videoId)

    /** More of a mix, from the previous page's `continuation`; mixes keep going until YouTube has nothing new. */
    @Throws(Throwable::class)
    suspend fun mixMore(continuation: String): ExcorListPage = engine.mixMore(continuation)

    /** The videos YouTube recommends beside [videoId] (logged out), then `relatedMore` for more. */
    @Throws(Throwable::class)
    suspend fun related(videoId: String): ExcorListPage = engine.related(videoId)

    @Throws(Throwable::class)
    suspend fun relatedMore(continuation: String): ExcorListPage = engine.relatedMore(continuation)

    /**
     * YouTube's search results (videos, playlists, channels; Shorts apart in `shorts`), then `searchMore` for more, and
     * the filters YouTube offers for them. Logged out.
     */
    @Throws(Throwable::class)
    suspend fun search(query: String): ExcorSearchPage = engine.search(query)

    /** The search for one [type] of result only. */
    @Throws(Throwable::class)
    suspend fun search(query: String, type: ExcorSearchType): ExcorSearchPage = engine.search(query, type.params)

    /** The search with [filter], one of a page's `filters`, added to those already applied. */
    @Throws(Throwable::class)
    suspend fun search(query: String, filter: ExcorSearchFilter): ExcorSearchPage =
        engine.search(query, requireNotNull(filter.params) { "Excor: the filter ${filter.label} cannot be asked (selected or not available)" })

    /** What YouTube's search box suggests for [query], in the session's language; empty for an empty query. */
    @Throws(Throwable::class)
    suspend fun suggestions(query: String): List<String> = engine.suggestions(query)

    @Throws(Throwable::class)
    suspend fun searchMore(continuation: String): ExcorSearchPage = engine.searchMore(continuation)

    /** YouTube Music's top results: songs, videos, albums, artists, playlists (one page). Logged out. */
    @Throws(Throwable::class)
    suspend fun searchMusic(query: String): ExcorSearchPage = engine.searchMusic(query)

    /**
     * For videos with dubbed audio, the track to play and list: a language code from `ExcorMetadata.audioTracks`
     * ("en", "es-US"). Videos without it keep their original track, as does null (the default).
     */
    fun preferAudioLanguage(code: String?) = engine.preferAudioLanguage(code)

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
