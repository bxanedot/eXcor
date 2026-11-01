package com.excor.music

import com.excor.catalog.ExcorCatalogCapabilityStatus
import com.excor.catalog.ExcorCatalogError
import com.excor.catalog.ExcorCatalogException
import com.excor.catalog.ExcorCatalogProvider
import com.excor.catalog.ExcorCatalogTokenProvider
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** The Apple Music user token MusicKit returns after the listener consents; null when not signed in. */
fun interface ExcorMusicUserTokenProvider {
    suspend fun musicUserToken(): String?
}

/**
 * How [ExcorMusic] reaches the providers. [tokens] answers SPOTIFY and DEEZER with the user's OAuth access token
 * and APPLE_MUSIC with the developer token; eXcor holds none of them and embeds no credentials.
 */
data class ExcorMusicConfig(
    val tokens: ExcorCatalogTokenProvider? = null,
    val appleMusicUserToken: ExcorMusicUserTokenProvider? = null,
    val appleStorefront: String = "us",
    val spotifyMarket: String? = null,
    val spotifyExtendedQuota: Boolean = false,
    val adapters: List<ExcorProtectedPlaybackAdapter> = emptyList(),
    val platform: ExcorPlatformCapabilities = ExcorPlatformCapabilities(),
    val lyrics: ExcorLyricsProvider? = null,
    val matchOptions: ExcorMatchOptions = ExcorMatchOptions(),
)

data class ExcorMultiSearchResult(
    val results: Map<ExcorCatalogProvider, ExcorMusicSearchResult>,
    val errors: Map<ExcorCatalogProvider, ExcorCatalogException>,
)

data class ExcorCrossProviderMatches(
    val matches: Map<ExcorCatalogProvider, List<ExcorTrackMatch>>,
    val errors: Map<ExcorCatalogProvider, ExcorCatalogException>,
)

/**
 * What a track's provider declares about its audio. Declared, not delivered: eXcor returns no stream for these
 * catalogs, and [protection] says why. Null booleans mean the provider does not say.
 */
data class ExcorAudioCapabilities(
    val provider: ExcorCatalogProvider,
    val traits: Set<ExcorAudioTrait>,
    val formats: List<ExcorAudioFormat>,
    val lossless: Boolean?,
    val hiResLossless: Boolean?,
    val spatial: Boolean?,
    val previewAvailable: Boolean?,
    val protection: ExcorProtectionInfo?,
)

/** Where an enriched field came from. [confidence] is the match confidence; 1 for the track's own provider. */
data class ExcorFieldSource(val provider: ExcorCatalogProvider, val id: String, val confidence: Double)

data class ExcorEnrichedTrack(val track: ExcorMusicTrack, val sources: Map<ExcorMusicField, ExcorFieldSource>)

class ExcorPlaybackException(val status: ExcorPlaybackStatus) : Exception("eXcor playback not possible: $status")

/**
 * Spotify, Apple Music and Deezer behind one API. Providers are created on first use. Catalog reads are metadata;
 * protected audio is only ever handed to a host [ExcorProtectedPlaybackAdapter].
 */
class ExcorMusic internal constructor(httpClient: HttpClient, private val config: ExcorMusicConfig, nowMs: () -> Long) {
    private val http = MusicHttp(httpClient, nowMs)

    private val spotify by lazy {
        SpotifySource(http, { token(ExcorCatalogProvider.SPOTIFY) }, config.spotifyMarket, config.spotifyExtendedQuota)
    }
    private val deezer by lazy { DeezerSource(http) { token(ExcorCatalogProvider.DEEZER) } }
    private val apple by lazy {
        AppleMusicSource(http, { token(ExcorCatalogProvider.APPLE_MUSIC) }, { checked(ExcorCatalogProvider.APPLE_MUSIC) { config.appleMusicUserToken?.musicUserToken() } }, config.appleStorefront)
    }
    private val engine = ExcorSyncEngine()

    /** Provider-specific Deezer catalog features: charts, genres, radios, related artists, top tracks. */
    val deezerCatalog by lazy { ExcorDeezerCatalog(deezer) }
    /** Provider-specific Spotify features: UPC lookup and playback state. */
    val spotifyCatalog by lazy { ExcorSpotifyCatalog(spotify) }
    /** Provider-specific Apple Music features: charts, recently played, UPC lookup. */
    val appleMusicCatalog by lazy { ExcorAppleMusicCatalog(apple) }

    private val clock = nowMs

    /** Spotify sign-in for your app's client ID (Authorization Code with PKCE). */
    fun spotifyAuth(clientId: String) = ExcorSpotifyAuth(http, clientId, clock)

    /** Requests sent so far by the music providers, for benchmarks and diagnostics. */
    val requestCount: Int get() = http.requests

    private fun source(provider: ExcorCatalogProvider): MusicSource = when (provider) {
        ExcorCatalogProvider.SPOTIFY -> spotify
        ExcorCatalogProvider.DEEZER -> deezer
        ExcorCatalogProvider.APPLE_MUSIC -> apple
        ExcorCatalogProvider.YOUTUBE_MUSIC -> throw ExcorCatalogException(provider, ExcorCatalogError.RESTRICTED)
    }

    /** A Spotify, Apple Music or Deezer link (or `spotify:` URI); null when none matches. No request. */
    fun parse(url: String): ExcorMusicRef? = spotifyRefOf(url) ?: deezer.parse(url) ?: apple.parse(url)

    @Throws(Throwable::class)
    suspend fun capabilities(provider: ExcorCatalogProvider): List<ExcorMusicCapabilityStatus> =
        source(provider).capabilities().map { (capability, status) -> ExcorMusicCapabilityStatus(provider, capability, status) }

    /** One provider; [page] is a `next` token from an earlier page of the same search. */
    @Throws(Throwable::class)
    suspend fun search(query: String, provider: ExcorCatalogProvider, kinds: Set<ExcorMusicKind> = setOf(ExcorMusicKind.TRACK), limit: Int = 10, page: String? = null): ExcorMusicSearchResult =
        source(provider).search(query, kinds, limit.coerceAtMost(searchCap(provider)), page)

    /** Several providers at once; one provider failing is reported in `errors`, not thrown. */
    @Throws(Throwable::class)
    suspend fun search(query: String, providers: Set<ExcorCatalogProvider> = MUSIC_PROVIDERS, kinds: Set<ExcorMusicKind> = setOf(ExcorMusicKind.TRACK), limit: Int = 10): ExcorMultiSearchResult {
        val outcomes = coroutineScope {
            providers.map { p -> async { p to runCatchingCatalog { search(query, p, kinds, limit) } } }.awaitAll()
        }
        return ExcorMultiSearchResult(
            outcomes.mapNotNull { (p, r) -> r.getOrNull()?.let { p to it } }.toMap(),
            outcomes.mapNotNull { (p, r) -> (r.exceptionOrNull() as? ExcorCatalogException)?.let { p to it } }.toMap(),
        )
    }

    @Throws(Throwable::class) suspend fun track(ref: ExcorMusicRef) = source(ref.provider).track(ref.expect(ExcorMusicKind.TRACK))
    @Throws(Throwable::class) suspend fun album(ref: ExcorMusicRef) = source(ref.provider).album(ref.expect(ExcorMusicKind.ALBUM))
    @Throws(Throwable::class) suspend fun artist(ref: ExcorMusicRef) = source(ref.provider).artist(ref.expect(ExcorMusicKind.ARTIST))
    @Throws(Throwable::class) suspend fun playlist(ref: ExcorMusicRef) = source(ref.provider).playlist(ref.expect(ExcorMusicKind.PLAYLIST))

    @Throws(Throwable::class) suspend fun track(url: String) = track(refOf(url))
    @Throws(Throwable::class) suspend fun album(url: String) = album(refOf(url))
    @Throws(Throwable::class) suspend fun artist(url: String) = artist(refOf(url))
    @Throws(Throwable::class) suspend fun playlist(url: String) = playlist(refOf(url))

    @Throws(Throwable::class)
    suspend fun albumTracks(ref: ExcorMusicRef, page: String? = null) = source(ref.provider).albumTracks(ref.expect(ExcorMusicKind.ALBUM), page)
    @Throws(Throwable::class)
    suspend fun artistAlbums(ref: ExcorMusicRef, page: String? = null) = source(ref.provider).artistAlbums(ref.expect(ExcorMusicKind.ARTIST), page)
    @Throws(Throwable::class)
    suspend fun playlistTracks(ref: ExcorMusicRef, page: String? = null) = source(ref.provider).playlistTracks(ref.expect(ExcorMusicKind.PLAYLIST), page)

    @Throws(Throwable::class)
    suspend fun tracksByIsrc(provider: ExcorCatalogProvider, isrc: String): List<ExcorMusicTrack> = source(provider).tracksByIsrc(isrc)

    @Throws(Throwable::class)
    suspend fun libraryTracks(provider: ExcorCatalogProvider, page: String? = null) = source(provider).libraryTracks(page)
    @Throws(Throwable::class)
    suspend fun libraryPlaylists(provider: ExcorCatalogProvider, page: String? = null) = source(provider).libraryPlaylists(page)

    /**
     * The track completed from its own provider: the full track when [track] is a summary, then its album for label,
     * genres, album artists, UPC and copyright. Only empty fields are filled, all from the same provider.
     */
    @Throws(Throwable::class)
    suspend fun metadata(track: ExcorMusicTrack): ExcorMusicTrack {
        val src = source(track.provider)
        // Album-level fields alone do not need the track again; a summary (search or list entry) does.
        val summary = track.missing.any { (field, status) -> status == ExcorFieldStatus.UNKNOWN && field !in ALBUM_FIELDS }
        val full = if (summary) runCatchingCatalog { src.track(track.id) }.getOrNull() ?: track else track
        val albumId = full.albumId ?: return full
        val album = runCatchingCatalog { src.album(albumId) }.getOrNull() ?: return full
        val filled = full.copy(
            label = full.label ?: album.label,
            genres = full.genres.ifEmpty { album.genres },
            albumArtists = full.albumArtists.ifEmpty { album.artists },
            upc = full.upc ?: album.upc,
            copyright = full.copyright ?: album.copyright,
            releaseDate = full.releaseDate ?: album.releaseDate,
            artwork = full.artwork.ifEmpty { album.artwork },
        )
        val nowPresent = filled.presentFields()
        return filled.copy(missing = filled.missing.filterKeys { it !in nowPresent }.mapValues { (field, status) ->
            // Asked the album and it had nothing: no longer unknown.
            if (status == ExcorFieldStatus.UNKNOWN && field in ALBUM_FIELDS) ExcorFieldStatus.UNAVAILABLE else status
        })
    }

    /**
     * Fills empty fields of [track] from matches on other providers at or above [minConfidence], recording each
     * field's source. Present fields are never replaced.
     */
    fun enrich(track: ExcorMusicTrack, matches: List<ExcorTrackMatch>, minConfidence: Double = config.matchOptions.likelyThreshold): ExcorEnrichedTrack {
        var result = track
        val sources = mutableMapOf<ExcorMusicField, ExcorFieldSource>()
        for (match in matches.filter { it.verdict != ExcorMatchVerdict.DIFFERENT && it.confidence >= minConfidence && it.substitutions.isEmpty() }.sortedByDescending { it.confidence }) {
            val c = match.candidate
            val source = ExcorFieldSource(c.provider, c.id, match.confidence)
            fun <T> take(field: ExcorMusicField, current: T?, offered: T?, empty: (T) -> Boolean = { false }): T? =
                if ((current == null || empty(current)) && offered != null && !empty(offered)) { sources[field] = source; offered } else current
            result = result.copy(
                isrc = take(ExcorMusicField.ISRC, result.isrc, c.isrc),
                upc = take(ExcorMusicField.UPC, result.upc, c.upc),
                label = take(ExcorMusicField.LABEL, result.label, c.label),
                genres = take(ExcorMusicField.GENRES, result.genres, c.genres, List<String>::isEmpty).orEmpty(),
                composers = take(ExcorMusicField.COMPOSERS, result.composers, c.composers, List<String>::isEmpty).orEmpty(),
                language = take(ExcorMusicField.LANGUAGE, result.language, c.language),
                releaseDate = take(ExcorMusicField.RELEASE_DATE, result.releaseDate, c.releaseDate),
                artwork = take(ExcorMusicField.ARTWORK, result.artwork, c.artwork, List<com.excor.catalog.ExcorArtwork>::isEmpty).orEmpty(),
            )
        }
        val present = result.presentFields()
        return ExcorEnrichedTrack(result.copy(missing = result.missing.filterKeys { it !in present }), sources)
    }

    /** Lyrics from the host's licensed provider; null without one or when it has none. */
    @Throws(Throwable::class)
    suspend fun lyrics(track: ExcorMusicTrack): ExcorLyrics? = config.lyrics?.lyrics(track)

    fun audioCapabilities(track: ExcorMusicTrack): ExcorAudioCapabilities {
        val traits = track.audioTraits
        val declared = traits.isNotEmpty()
        return ExcorAudioCapabilities(
            provider = track.provider,
            traits = traits,
            formats = track.audioFormats,
            lossless = if (declared) ExcorAudioTrait.LOSSLESS in traits || ExcorAudioTrait.HI_RES_LOSSLESS in traits else null,
            hiResLossless = if (declared) ExcorAudioTrait.HI_RES_LOSSLESS in traits else null,
            spatial = if (declared) traits.any { it == ExcorAudioTrait.DOLBY_ATMOS || it == ExcorAudioTrait.SPATIAL || it == ExcorAudioTrait.DOLBY_AUDIO } else null,
            previewAvailable = track.previewAvailable,
            protection = drmInfo(track),
        )
    }

    /** What the provider states about full-track protection. */
    fun drmInfo(track: ExcorMusicTrack): ExcorProtectionInfo? = track.protection ?: runCatching { source(track.provider).protection(track) }.getOrNull()

    /**
     * Candidates for [track] on other providers: by ISRC where the provider supports it, then by title and artist,
     * each scored with [excorMatchTrack]. DIFFERENT candidates are dropped. This is for discovery and metadata, not
     * for replacing one provider's recording with another's.
     */
    @Throws(Throwable::class)
    suspend fun matchAcrossProviders(track: ExcorMusicTrack, providers: Set<ExcorCatalogProvider> = MUSIC_PROVIDERS - track.provider, options: ExcorMatchOptions = config.matchOptions): ExcorCrossProviderMatches {
        val outcomes = coroutineScope {
            (providers - track.provider - ExcorCatalogProvider.YOUTUBE_MUSIC).map { p ->
                async {
                    p to runCatchingCatalog {
                        val byIsrc = track.isrc?.let { isrc ->
                            if (source(p).capabilities()[ExcorMusicCapability.ISRC_LOOKUP] == ExcorCatalogCapabilityStatus.SUPPORTED) source(p).tracksByIsrc(isrc) else null
                        }.orEmpty()
                        val byText = if (byIsrc.isEmpty()) {
                            val artist = track.artists.firstOrNull { it.role == ExcorArtistRole.PRIMARY }?.name.orEmpty()
                            search("${excorParseTitle(track.title).base} $artist".trim(), p, setOf(ExcorMusicKind.TRACK), 10).tracks?.items.orEmpty()
                        } else {
                            emptyList()
                        }
                        excorRankMatches(track, (byIsrc + byText).distinctBy { it.id }, options).filter { it.verdict != ExcorMatchVerdict.DIFFERENT }
                    }
                }
            }.awaitAll()
        }
        return ExcorCrossProviderMatches(
            outcomes.mapNotNull { (p, r) -> r.getOrNull()?.let { p to it } }.toMap(),
            outcomes.mapNotNull { (p, r) -> (r.exceptionOrNull() as? ExcorCatalogException)?.let { p to it } }.toMap(),
        )
    }

    /**
     * Incremental playlist sync. The playlist's snapshot (one request) short-circuits when unchanged; otherwise pages
     * stream to [onPage]. Persist `result.state.encode()` and pass it back as [previous].
     */
    @Throws(Throwable::class)
    suspend fun syncPlaylist(ref: ExcorMusicRef, previous: ExcorSyncState?, onPage: suspend (ExcorSyncPage) -> Unit = {}): ExcorSyncResult = withContext(FreshReads) {
        val snapshot = playlist(ref).snapshot
        val src = source(ref.provider)
        engine.sync({ cursor ->
            val page = src.playlistTracks(ref.id, cursor)
            ExcorSyncPage(page.items.map { ExcorSyncEntry(it.id, it) }, page.next, snapshot)
        }, previous, snapshot, onPage)
    }

    /** Incremental sync of the user's saved tracks on [provider]. */
    @Throws(Throwable::class)
    suspend fun syncLibrary(provider: ExcorCatalogProvider, previous: ExcorSyncState?, onPage: suspend (ExcorSyncPage) -> Unit = {}): ExcorSyncResult = withContext(FreshReads) {
        val src = source(provider)
        engine.sync({ cursor ->
            val page = src.libraryTracks(cursor)
            ExcorSyncPage(page.items.map { ExcorSyncEntry(it.id, it) }, page.next)
        }, previous, null, onPage)
    }

    @Throws(Throwable::class)
    suspend fun playbackOptions(track: ExcorMusicTrack): ExcorPlaybackOption =
        excorPlaybackOptionFor(track, drmInfo(track), config.adapters, config.platform)

    /** Plays [track] through a ready, entitled host adapter, or throws [ExcorPlaybackException] saying why not. */
    @Throws(Throwable::class)
    suspend fun play(track: ExcorMusicTrack) {
        val option = playbackOptions(track)
        val adapter = option.adapter ?: throw ExcorPlaybackException(option.status)
        adapter.play(track)
    }

    // --- helpers ---

    private fun refOf(url: String) = parse(url) ?: throw ExcorCatalogException(null, ExcorCatalogError.INVALID_URL)

    private fun ExcorMusicRef.expect(kind: ExcorMusicKind): String {
        if (this.kind != kind) throw ExcorCatalogException(provider, ExcorCatalogError.INVALID_REQUEST)
        return id
    }

    private suspend fun token(provider: ExcorCatalogProvider): String? = checked(provider) { config.tokens?.accessToken(provider) }

    private suspend fun checked(provider: ExcorCatalogProvider, read: suspend () -> String?): String? {
        val value = try {
            read()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw ExcorCatalogException(provider, ExcorCatalogError.CREDENTIAL_PROVIDER_FAILED)
        } ?: return null
        if (value.isEmpty() || value.length > 8_192 || value.any { it <= ' ' || it == '\u007f' }) {
            throw ExcorCatalogException(provider, ExcorCatalogError.INVALID_CREDENTIAL)
        }
        return value
    }

    private suspend fun searchCap(provider: ExcorCatalogProvider) = when (provider) {
        ExcorCatalogProvider.SPOTIFY -> if (config.spotifyExtendedQuota) 50 else 10
        ExcorCatalogProvider.APPLE_MUSIC -> if (token(provider) != null) 25 else 200
        ExcorCatalogProvider.DEEZER -> 100
        ExcorCatalogProvider.YOUTUBE_MUSIC -> 0
    }

    private suspend fun <T> runCatchingCatalog(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: ExcorCatalogException) {
        Result.failure(e)
    }

    companion object {
        val MUSIC_PROVIDERS = setOf(ExcorCatalogProvider.SPOTIFY, ExcorCatalogProvider.APPLE_MUSIC, ExcorCatalogProvider.DEEZER)
        private val ALBUM_FIELDS = setOf(ExcorMusicField.LABEL, ExcorMusicField.GENRES, ExcorMusicField.ALBUM_ARTISTS, ExcorMusicField.COPYRIGHT)
    }
}

class ExcorDeezerCatalog internal constructor(private val source: DeezerSource) {
    /** Top tracks overall ("0") or for a genre ID. */
    @Throws(Throwable::class) suspend fun chartTracks(genreId: String = "0", page: String? = null) = source.chartTracks(genreId, page)
    @Throws(Throwable::class) suspend fun genres() = source.genres()
    @Throws(Throwable::class) suspend fun radios() = source.radios()
    @Throws(Throwable::class) suspend fun radioTracks(radioId: String) = source.radioTracks(radioId)
    @Throws(Throwable::class) suspend fun artistTopTracks(artistId: String, page: String? = null) = source.artistTopTracks(artistId, page)
    @Throws(Throwable::class) suspend fun relatedArtists(artistId: String) = source.relatedArtists(artistId)
    @Throws(Throwable::class) suspend fun albumByUpc(upc: String) = source.albumByUpc(upc)
}

class ExcorSpotifyCatalog internal constructor(private val source: SpotifySource) {
    @Throws(Throwable::class) suspend fun albumByUpc(upc: String) = source.albumByUpc(upc)
    /** Needs the user-read-playback-state scope; null when nothing is playing. */
    @Throws(Throwable::class) suspend fun playbackState() = source.playbackState()
    /** Remote control of the user's Spotify client (Premium, user-modify-playback-state). [uris] are `spotify:` URIs. */
    @Throws(Throwable::class) suspend fun play(uris: List<String> = emptyList(), deviceId: String? = null) = source.play(uris, deviceId)
    @Throws(Throwable::class) suspend fun pause(deviceId: String? = null) { source.pause(deviceId) }
    @Throws(Throwable::class) suspend fun next(deviceId: String? = null) { source.next(deviceId) }
    @Throws(Throwable::class) suspend fun previous(deviceId: String? = null) { source.previous(deviceId) }
    @Throws(Throwable::class) suspend fun seek(positionMs: Long, deviceId: String? = null) = source.seek(positionMs, deviceId)
    @Throws(Throwable::class) suspend fun devices() = source.devices()
    @Throws(Throwable::class) suspend fun queue(uri: String, deviceId: String? = null) = source.queue(uri, deviceId)
    @Throws(Throwable::class) suspend fun setVolume(percent: Int, deviceId: String? = null) = source.setVolume(percent, deviceId)
    /** `spotify:` URIs of tracks, albums, episodes, shows, artists or playlists; batched by 40. */
    @Throws(Throwable::class) suspend fun saveToLibrary(uris: List<String>) = source.saveToLibrary(uris)
    @Throws(Throwable::class) suspend fun removeFromLibrary(uris: List<String>) = source.removeFromLibrary(uris)
    @Throws(Throwable::class) suspend fun libraryContains(uris: List<String>) = source.libraryContains(uris)
    @Throws(Throwable::class) suspend fun episode(id: String) = source.episode(id)
    @Throws(Throwable::class) suspend fun show(id: String, page: String? = null) = source.show(id, page)
}

class ExcorAppleMusicCatalog internal constructor(private val source: AppleMusicSource) {
    @Throws(Throwable::class) suspend fun albumByUpc(upc: String) = source.albumByUpc(upc)
    @Throws(Throwable::class) suspend fun chartSongs(genreId: String? = null, limit: Int = 25) = source.chartSongs(genreId, limit)
    /** Needs a Music-User-Token. */
    @Throws(Throwable::class) suspend fun recentlyPlayed() = source.recentlyPlayed()
    /** Needs a Music-User-Token. */
    @Throws(Throwable::class) suspend fun recommendations() = source.recommendations()
    @Throws(Throwable::class) suspend fun musicVideo(id: String) = source.musicVideo(id)
    @Throws(Throwable::class) suspend fun station(id: String) = source.station(id)
}
