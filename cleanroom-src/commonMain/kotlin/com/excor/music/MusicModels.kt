package com.excor.music

import com.excor.catalog.ExcorArtwork
import com.excor.catalog.ExcorCatalogCapabilityStatus
import com.excor.catalog.ExcorCatalogProvider

enum class ExcorMusicKind { TRACK, ALBUM, ARTIST, PLAYLIST, MUSIC_VIDEO, EPISODE, SHOW, STATION, CHART, GENRE }

/** A provider's own identity for an item; [id] is in the provider's format. */
data class ExcorMusicRef(val provider: ExcorCatalogProvider, val kind: ExcorMusicKind, val id: String) {
    override fun toString(): String = "ExcorMusicRef(provider=$provider, kind=$kind, id=present)"
}

/**
 * Why a metadata field has no value. Every field a model leaves null or empty is listed in its `missing` map:
 * UNSUPPORTED: the provider's interface never offers it. UNAVAILABLE: offered, but absent for this item.
 * RESTRICTED: offered only with an authorization or entitlement this request lacked. UNKNOWN: the response did not say.
 */
enum class ExcorFieldStatus { UNKNOWN, UNAVAILABLE, RESTRICTED, UNSUPPORTED }

enum class ExcorMusicField {
    ORIGINAL_TITLE, ARTISTS, FEATURED_ARTISTS, ALBUM, ALBUM_ARTISTS, ISRC, UPC, TRACK_NUMBER, DISC_NUMBER, DURATION,
    RELEASE_DATE, ORIGINAL_RELEASE_DATE, GENRES, LABEL, COPYRIGHT, COMPOSERS, LANGUAGE, EXPLICIT, POPULARITY,
    FOLLOWERS, ARTWORK, MARKETS, AUDIO_TRAITS, PREVIEW, LYRICS, URL, DESCRIPTION, OWNER, SNAPSHOT, TRACK_COUNT,
}

enum class ExcorArtistRole { PRIMARY, FEATURED, COMPOSER, PERFORMER, OTHER }

enum class ExcorAvailability { AVAILABLE, REGION_RESTRICTED, UNAVAILABLE, UNKNOWN }

/** Audio variants a catalog declares for an item; a declaration, not a stream eXcor can deliver. */
enum class ExcorAudioTrait { LOSSY_STEREO, LOSSLESS, HI_RES_LOSSLESS, DOLBY_ATMOS, DOLBY_AUDIO, SPATIAL }

enum class ExcorAlbumType { ALBUM, SINGLE, EP, COMPILATION, UNKNOWN }

data class ExcorMusicArtist(
    val provider: ExcorCatalogProvider,
    val id: String?,
    val name: String,
    val role: ExcorArtistRole = ExcorArtistRole.PRIMARY,
    val url: String? = null,
) {
    override fun toString(): String =
            "ExcorMusicArtist(provider=$provider, role=$role, id=${id != null}, url=${url != null})"
}

data class ExcorMusicTrack(
    val provider: ExcorCatalogProvider,
    val id: String,
    val title: String,
    val url: String? = null,
    /** The title with the provider's version suffix, such as "Song (Live)"; equal to [title] when there is none. */
    val displayTitle: String = title,
    val version: String? = null,
    val artists: List<ExcorMusicArtist> = emptyList(),
    val albumId: String? = null,
    val albumTitle: String? = null,
    val albumArtists: List<ExcorMusicArtist> = emptyList(),
    val isrc: String? = null,
    val upc: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val durationMs: Long? = null,
    val releaseDate: String? = null,
    val genres: List<String> = emptyList(),
    val label: String? = null,
    val copyright: String? = null,
    val composers: List<String> = emptyList(),
    val language: String? = null,
    val explicit: Boolean? = null,
    /** The provider's own popularity or rank, on its own scale; not comparable across providers. */
    val popularity: Long? = null,
    val artwork: List<ExcorArtwork> = emptyList(),
    /** Markets (ISO 3166-1 alpha-2) the provider lists; null when it does not list them. */
    val markets: Set<String>? = null,
    val availability: ExcorAvailability = ExcorAvailability.UNKNOWN,
    val audioTraits: Set<ExcorAudioTrait> = emptySet(),
    val audioFormats: List<ExcorAudioFormat> = emptyList(),
    val previewAvailable: Boolean? = null,
    val hasLyrics: Boolean? = null,
    val protection: ExcorProtectionInfo? = null,
    val missing: Map<ExcorMusicField, ExcorFieldStatus> = emptyMap(),
) {
    val ref: ExcorMusicRef get() = ExcorMusicRef(provider, ExcorMusicKind.TRACK, id)
    val featuredArtists: List<ExcorMusicArtist> get() = artists.filter { it.role == ExcorArtistRole.FEATURED }
    override fun toString(): String =
            "ExcorMusicTrack(provider=$provider, artists=${artists.size}, duration=${durationMs != null}, " +
                "url=${url != null}, availability=$availability)"
}

data class ExcorMusicAlbum(
    val provider: ExcorCatalogProvider,
    val id: String,
    val title: String,
    val url: String? = null,
    val type: ExcorAlbumType = ExcorAlbumType.UNKNOWN,
    val artists: List<ExcorMusicArtist> = emptyList(),
    val upc: String? = null,
    val releaseDate: String? = null,
    val label: String? = null,
    val copyright: String? = null,
    val genres: List<String> = emptyList(),
    val explicit: Boolean? = null,
    val trackCount: Int? = null,
    val popularity: Long? = null,
    val artwork: List<ExcorArtwork> = emptyList(),
    val audioTraits: Set<ExcorAudioTrait> = emptySet(),
    /** The first page of tracks when the response carried them; see [ExcorMusic.albumTracks] for the rest. */
    val tracks: ExcorMusicPage<ExcorMusicTrack>? = null,
    val missing: Map<ExcorMusicField, ExcorFieldStatus> = emptyMap(),
) {
    override fun toString(): String =
            "ExcorMusicAlbum(provider=$provider, type=$type, artists=${artists.size}, tracks=${tracks?.items?.size ?: "missing"}, " +
                "hasNext=${tracks?.next != null}, artwork=${artwork.size})"
}

data class ExcorMusicArtistProfile(
    val provider: ExcorCatalogProvider,
    val id: String,
    val name: String,
    val url: String? = null,
    val genres: List<String> = emptyList(),
    val followers: Long? = null,
    val popularity: Long? = null,
    val albumCount: Int? = null,
    val artwork: List<ExcorArtwork> = emptyList(),
    val missing: Map<ExcorMusicField, ExcorFieldStatus> = emptyMap(),
) {
    override fun toString(): String =
            "ExcorMusicArtistProfile(provider=$provider, genres=${genres.size}, albumCount=${albumCount ?: "unknown"}, " +
                "artwork=${artwork.size})"
}

data class ExcorMusicPlaylist(
    val provider: ExcorCatalogProvider,
    val id: String,
    val title: String,
    val url: String? = null,
    val description: String? = null,
    val ownerName: String? = null,
    val ownerId: String? = null,
    val collaborative: Boolean? = null,
    val public: Boolean? = null,
    /** The provider's version marker (Spotify snapshot_id, Deezer checksum, Apple lastModifiedDate). */
    val snapshot: String? = null,
    val trackCount: Int? = null,
    val followers: Long? = null,
    val artwork: List<ExcorArtwork> = emptyList(),
    val tracks: ExcorMusicPage<ExcorMusicTrack>? = null,
    val missing: Map<ExcorMusicField, ExcorFieldStatus> = emptyMap(),
) {
    override fun toString(): String =
        "ExcorMusicPlaylist(provider=$provider, trackPageItems=${tracks?.items?.size ?: "missing"}, " +
            "trackPageHasNext=${tracks?.next != null}, artwork=${artwork.size})"
}

/** One page; [next] is an opaque token for the following page, null on the last. [total] is the provider's count. */
data class ExcorMusicPage<T>(val items: List<T>, val next: String? = null, val total: Int? = null) {
    override fun toString(): String =
        "ExcorMusicPage(items=${items.size}, next=${if (next == null) "missing" else "present"}, total=$total)"
}

data class ExcorMusicSearchResult(
    val tracks: ExcorMusicPage<ExcorMusicTrack>? = null,
    val albums: ExcorMusicPage<ExcorMusicAlbum>? = null,
    val artists: ExcorMusicPage<ExcorMusicArtistProfile>? = null,
    val playlists: ExcorMusicPage<ExcorMusicPlaylist>? = null,
) {
    override fun toString(): String =
            "ExcorMusicSearchResult(tracks=${tracks?.items?.size ?: "missing"}, albums=${albums?.items?.size ?: "missing"}, " +
                "artists=${artists?.items?.size ?: "missing"}, playlists=${playlists?.items?.size ?: "missing"})"
}

enum class ExcorMusicCapability {
    SEARCH, TRACK, ALBUM, ARTIST, PLAYLIST, ALBUM_TRACKS, ARTIST_ALBUMS, PLAYLIST_TRACKS, ISRC_LOOKUP, UPC_LOOKUP,
    LIBRARY, CHARTS, GENRES, RADIOS, RELATED_ARTISTS, ARTIST_TOP_TRACKS, AUDIO_TRAITS, LYRICS_AVAILABILITY, MARKETS,
    PREVIEW_AVAILABILITY, PLAYBACK_STATE, PROTECTED_PLAYBACK,
}

data class ExcorMusicCapabilityStatus(
    val provider: ExcorCatalogProvider,
    val capability: ExcorMusicCapability,
    val status: ExcorCatalogCapabilityStatus,
)

/**
 * The [missing] map for an item: each field in [offered] that is not [present] is UNAVAILABLE, or RESTRICTED when in
 * [restricted]; fields only another request would give ([elsewhere], such as a track's label on its album) are UNKNOWN;
 * each field the provider never offers is UNSUPPORTED.
 */
internal fun missingFields(
    applicable: Set<ExcorMusicField>,
    offered: Set<ExcorMusicField>,
    present: Set<ExcorMusicField>,
    restricted: Set<ExcorMusicField> = emptySet(),
    elsewhere: Set<ExcorMusicField> = emptySet(),
): Map<ExcorMusicField, ExcorFieldStatus> = (applicable - present).associateWith { field ->
    when (field) {
        in restricted -> ExcorFieldStatus.RESTRICTED
        in elsewhere -> ExcorFieldStatus.UNKNOWN
        in offered -> ExcorFieldStatus.UNAVAILABLE
        else -> ExcorFieldStatus.UNSUPPORTED
    }
}

internal val TRACK_FIELDS = setOf(
    ExcorMusicField.ARTISTS, ExcorMusicField.ALBUM, ExcorMusicField.ALBUM_ARTISTS, ExcorMusicField.ISRC,
    ExcorMusicField.TRACK_NUMBER, ExcorMusicField.DISC_NUMBER, ExcorMusicField.DURATION, ExcorMusicField.RELEASE_DATE,
    ExcorMusicField.GENRES, ExcorMusicField.LABEL, ExcorMusicField.COPYRIGHT, ExcorMusicField.COMPOSERS,
    ExcorMusicField.LANGUAGE, ExcorMusicField.EXPLICIT, ExcorMusicField.POPULARITY, ExcorMusicField.ARTWORK,
    ExcorMusicField.MARKETS, ExcorMusicField.AUDIO_TRAITS, ExcorMusicField.PREVIEW, ExcorMusicField.LYRICS,
    ExcorMusicField.URL,
)

internal fun ExcorMusicTrack.presentFields(): Set<ExcorMusicField> = buildSet {
    if (artists.isNotEmpty()) add(ExcorMusicField.ARTISTS)
    if (albumTitle != null) add(ExcorMusicField.ALBUM)
    if (albumArtists.isNotEmpty()) add(ExcorMusicField.ALBUM_ARTISTS)
    if (isrc != null) add(ExcorMusicField.ISRC)
    if (trackNumber != null) add(ExcorMusicField.TRACK_NUMBER)
    if (discNumber != null) add(ExcorMusicField.DISC_NUMBER)
    if (durationMs != null) add(ExcorMusicField.DURATION)
    if (releaseDate != null) add(ExcorMusicField.RELEASE_DATE)
    if (genres.isNotEmpty()) add(ExcorMusicField.GENRES)
    if (label != null) add(ExcorMusicField.LABEL)
    if (copyright != null) add(ExcorMusicField.COPYRIGHT)
    if (composers.isNotEmpty()) add(ExcorMusicField.COMPOSERS)
    if (language != null) add(ExcorMusicField.LANGUAGE)
    if (explicit != null) add(ExcorMusicField.EXPLICIT)
    if (popularity != null) add(ExcorMusicField.POPULARITY)
    if (artwork.isNotEmpty()) add(ExcorMusicField.ARTWORK)
    if (markets != null) add(ExcorMusicField.MARKETS)
    if (audioTraits.isNotEmpty()) add(ExcorMusicField.AUDIO_TRAITS)
    if (previewAvailable != null) add(ExcorMusicField.PREVIEW)
    if (hasLyrics != null) add(ExcorMusicField.LYRICS)
    if (url != null) add(ExcorMusicField.URL)
}

/** Fills [ExcorMusicTrack.missing] from the provider's offered and restricted fields. */
internal fun ExcorMusicTrack.withMissing(
    offered: Set<ExcorMusicField>,
    restricted: Set<ExcorMusicField> = emptySet(),
    elsewhere: Set<ExcorMusicField> = emptySet(),
): ExcorMusicTrack = copy(missing = missingFields(TRACK_FIELDS, offered, presentFields(), restricted, elsewhere))

/** A podcast episode (Spotify or Deezer); kept apart from music tracks. */
data class ExcorPodcastEpisode(
    val id: String,
    val title: String,
    val description: String?,
    val showId: String?,
    val showTitle: String?,
    val durationMs: Long?,
    val releaseDate: String?,
    val explicit: Boolean?,
    val languages: List<String>,
    val playable: Boolean?,
    val url: String?,
    val artwork: List<ExcorArtwork>,
    val provider: ExcorCatalogProvider = ExcorCatalogProvider.SPOTIFY,
) {
    override fun toString(): String =
            "ExcorPodcastEpisode(provider=$provider, duration=${durationMs != null}, playable=$playable, artwork=${artwork.size}, url=${url != null})"
}

data class ExcorPodcastShow(
    val id: String,
    val title: String,
    val description: String?,
    /** Spotify withholds it from Development Mode apps since February 2026; Deezer does not give one. */
    val publisher: String?,
    val explicit: Boolean?,
    val languages: List<String>,
    val totalEpisodes: Int?,
    val url: String?,
    val artwork: List<ExcorArtwork>,
    val episodes: ExcorMusicPage<ExcorPodcastEpisode>,
    val provider: ExcorCatalogProvider = ExcorCatalogProvider.SPOTIFY,
) {
    override fun toString(): String =
            "ExcorPodcastShow(provider=$provider, episodes=${episodes.items.size}, hasNext=${episodes.next != null}, artwork=${artwork.size})"
}
