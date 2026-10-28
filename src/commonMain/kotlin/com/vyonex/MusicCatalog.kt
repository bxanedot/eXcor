package com.vyonex

import com.vyonex.internal.MusicCatalogEngine
import com.vyonex.internal.musicCatalogRefOf

/** External catalogs supply metadata and images, not playable audio or download URLs. */
enum class MusicCatalogProvider { SPOTIFY, DEEZER }
enum class MusicCatalogKind { TRACK, ALBUM, ARTIST, PLAYLIST, SHOW, EPISODE }

/** A validated HTTPS catalog link. Resolving short redirect links is deliberately outside this parser. */
data class MusicCatalogRef(
    val provider: MusicCatalogProvider,
    val kind: MusicCatalogKind,
    val id: String,
    val url: String,
)

/** Artwork is returned unmodified; unknown dimensions are null rather than guessed. */
data class MusicCatalogImage(val url: String, val width: Int?, val height: Int?)

/** Artists and contributors can carry their own artist pictures when the catalog supplies them. */
data class MusicCatalogCredit(
    val id: String?,
    val name: String?,
    val url: String?,
    val images: List<MusicCatalogImage> = emptyList(),
    val role: String? = null,
)

/**
 * A provider-neutral snapshot. Not every catalog exposes every property.
 * [tracks] can be a partial first page; [tracksComplete] says when the count confirms completeness.
 * [fullyEnriched] is false for Spotify's keyless oEmbed link preview.
 *
 * No previews, streams, audio URLs or DRM-related information are exposed here.
 * Artwork and other visual content remain subject to each provider's attribution and usage terms.
 */
data class MusicCatalogItem(
    val provider: MusicCatalogProvider,
    val kind: MusicCatalogKind,
    val id: String,
    val title: String?,
    val url: String,
    val images: List<MusicCatalogImage> = emptyList(),
    val artists: List<MusicCatalogCredit> = emptyList(),
    val album: String? = null,
    val albumId: String? = null,
    val durationMs: Long? = null,
    val releaseDate: String? = null,
    val genres: List<String> = emptyList(),
    val explicit: Boolean? = null,
    val isrc: String? = null,
    val upc: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val trackCount: Int? = null,
    val label: String? = null,
    val copyright: String? = null,
    val description: String? = null,
    val popularity: Int? = null,
    val fanCount: Long? = null,
    val tracks: List<MusicCatalogItem> = emptyList(),
    val tracksComplete: Boolean? = null,
    val fullyEnriched: Boolean = true,
)

/**
 * Cross-platform music metadata from Spotify and Deezer.
 *
 * Spotify without a token: official oEmbed link preview (name and image only).
 * Spotify with a caller-supplied OAuth access token: tracks, albums, artists and search from its Web API.
 * Deezer: public track, album, artist, playlist lookups and catalog search, without credentials.
 *
 * Tokens are used only as Authorization headers and are never retained by this object.
 * The calling application is responsible for OAuth and provider-specific attribution.
 */
class VyonExMusicCatalog internal constructor(private val engine: MusicCatalogEngine) {
    /** Recognizes supported canonical Spotify and Deezer HTTPS links locally; no request. */
    fun parse(url: String): MusicCatalogRef? = musicCatalogRefOf(url)

    /** Null for an invalid or missing link; server errors and quota refusals throw. */
    @Throws(Throwable::class)
    suspend fun lookup(url: String, spotifyAccessToken: String? = null): MusicCatalogItem? =
        parse(url)?.let { get(it, spotifyAccessToken) }

    /** A parsed catalog reference; the reference is revalidated to prevent arbitrary request destinations. */
    @Throws(Throwable::class)
    suspend fun get(ref: MusicCatalogRef, spotifyAccessToken: String? = null): MusicCatalogItem? =
        engine.get(ref, spotifyAccessToken)

    /** Public Deezer search. Only TRACK, ALBUM and ARTIST are supported; limit is 1–100. */
    @Throws(Throwable::class)
    suspend fun searchDeezer(term: String, kind: MusicCatalogKind, limit: Int = 25): List<MusicCatalogItem> =
        engine.searchDeezer(term, kind, limit)

    /** Authenticated Spotify catalog search. Only TRACK, ALBUM and ARTIST are supported; limit is 1–10. */
    @Throws(Throwable::class)
    suspend fun searchSpotify(
        term: String,
        kind: MusicCatalogKind,
        accessToken: String,
        limit: Int = 20,
    ): List<MusicCatalogItem> = engine.searchSpotify(term, kind, accessToken, limit)
}
