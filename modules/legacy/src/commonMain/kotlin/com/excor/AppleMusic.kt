package com.excor

import com.excor.internal.AppleMusicEngine
import com.excor.internal.appleMusicRefOf

/** What an Apple Music link points at; see `ExcorAppleMusic.parse`. [country] is the store in the link ("us"). */
sealed class AppleMusicRef {
    abstract val id: Long
    abstract val country: String

    data class Song(override val id: Long, override val country: String) : AppleMusicRef()
    data class Album(override val id: Long, override val country: String) : AppleMusicRef()
    data class Artist(override val id: Long, override val country: String) : AppleMusicRef()
}

/**
 * A song in the Apple Music catalog: metadata only. [releaseDate] is ISO 8601 as Apple writes it; [artwork] is the
 * cover at 100 px and the larger sizes Apple's image server makes from it. [url] is its Apple Music page.
 */
data class AppleMusicSong(
    val id: Long,
    val title: String?,
    val artist: String?,
    val artistId: Long?,
    val album: String?,
    val albumId: Long?,
    val durationMs: Long?,
    val trackNumber: Int?,
    val trackCount: Int?,
    val discNumber: Int?,
    val discCount: Int?,
    val releaseDate: String?,
    val genre: String?,
    val isExplicit: Boolean,
    val artwork: List<ExcorThumbnail>,
    val url: String?,
)

/** An album: its metadata and, from `ExcorAppleMusic.album`, its songs in order. */
data class AppleMusicAlbum(
    val id: Long,
    val title: String?,
    val artist: String?,
    val artistId: Long?,
    val trackCount: Int?,
    val releaseDate: String?,
    val genre: String?,
    val copyright: String?,
    val isExplicit: Boolean,
    val artwork: List<ExcorThumbnail>,
    val url: String?,
    val songs: List<AppleMusicSong>,
)

/** An artist and albums they are on, theirs or others' (pictures: `ExcorAppleMusic.artistImages`). */
data class AppleMusicArtist(val id: Long, val name: String?, val genre: String?, val url: String?, val albums: List<AppleMusicAlbum>)

/**
 * Apple Music metadata through Apple's documented iTunes Search API (no key, no account): songs, albums and artists
 * by ID or Apple Music link, and song and album search. Metadata only: no audio, and song previews are not read.
 * Apple allows about 20 calls a minute; a refusal throws with its HTTP status. [country] is a two-letter store code
 * ("us"); null takes the session's region.
 */
class ExcorAppleMusic internal constructor(private val engine: AppleMusicEngine) {
    /** What a `music.apple.com` link points at (a song, album or artist); null for anything else. Makes no request. */
    fun parse(url: String): AppleMusicRef? = appleMusicRefOf(url)

    @Throws(Throwable::class)
    suspend fun song(id: Long, country: String?): AppleMusicSong? = engine.song(id, country)

    /** An album with its songs. */
    @Throws(Throwable::class)
    suspend fun album(id: Long, country: String?): AppleMusicAlbum? = engine.album(id, country)

    /** An artist with up to [albumLimit] albums (1 to 200). */
    @Throws(Throwable::class)
    suspend fun artist(id: Long, country: String?, albumLimit: Int): AppleMusicArtist? = engine.artist(id, country, albumLimit)

    /**
     * An artist's picture (the search API has none): from their Apple Music page's share image, as the page gives it
     * (a wide crop, 1200 × 630) and as squares at 600, 1200 and 3000 px. Empty for an artist without one or unknown.
     */
    @Throws(Throwable::class)
    suspend fun artistImages(id: Long, country: String?): List<ExcorThumbnail> = engine.artistImages(id, country)

    /** Songs matching [term], best first; [limit] is 1 to 200. */
    @Throws(Throwable::class)
    suspend fun searchSongs(term: String, country: String?, limit: Int): List<AppleMusicSong> = engine.searchSongs(term, country, limit)

    /** Albums matching [term] (without their songs), best first; [limit] is 1 to 200. */
    @Throws(Throwable::class)
    suspend fun searchAlbums(term: String, country: String?, limit: Int): List<AppleMusicAlbum> = engine.searchAlbums(term, country, limit)
}
