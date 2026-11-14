package com.excor.internal

import com.excor.AppleMusicAlbum
import com.excor.AppleMusicArtist
import com.excor.AppleMusicRef
import com.excor.AppleMusicSong
import com.excor.ExcorHostServices
import com.excor.ExcorThumbnail
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

// Apple's iTunes Search API as its documentation describes it (performance-partners.apple.com/search-api, read
// 2026-10-28): /lookup by ID (with entity=song for an album's songs, entity=album for an artist's albums) and /search.

private const val ITUNES_API = "https://itunes.apple.com"

private val JsonElement?.items get() = (this as? JsonArray).orEmpty()

// music.apple.com/<country>/<album|song|artist>/<name>/<id>, a song in an album as ?i=<id>; the name may be missing.
private val APPLE_MUSIC_LINK =
    Regex("""https://(?:geo\.)?music\.apple\.com/([a-z]{2})/(album|song|artist)(?:/[^/?#]+)?/(\d+)/?(?:\?([^#]*))?(?:#.*)?""")
private val COUNTRY = Regex("""[A-Za-z]{2}""")
// The size part of Apple's artwork URLs ("…/100x100bb.jpg"); its image server makes other sizes from the same URL.
private val ARTWORK_SIZE = Regex("""/100x100bb\.(jpg|png)$""")
private val ARTWORK_SIZES = listOf(100, 600, 1200, 3000)
// An artist page's share image (og:image), on Apple's image server: its last part is the size and crop ("1200x630cw.png").
private val OG_IMAGE = Regex("""<meta property="og:image" content="([^"]+)"""")
private val OG_IMAGE_WIDTH = Regex("""<meta property="og:image:width" content="(\d+)"""")
private val OG_IMAGE_HEIGHT = Regex("""<meta property="og:image:height" content="(\d+)"""")
private val ARTIST_IMAGE = Regex("""https://is\d+-ssl\.mzstatic\.com/image/thumb/[^"\s?#]+/\d+x\d+[a-z]{2}\.(?:png|jpg)""")
private val ARTIST_IMAGE_SIZES = listOf(600, 1200, 3000)
// The page's head holds its meta tags (about 6 KB); the rest (over 500 KB) is not read.
private const val ARTIST_PAGE_HEAD_MAX = 256 * 1024

internal fun appleMusicRefOf(url: String): AppleMusicRef? {
    val m = APPLE_MUSIC_LINK.matchEntire(url.trim()) ?: return null
    val (country, kind, id, query) = m.destructured
    val song = query.split('&').firstOrNull { it.startsWith("i=") }?.removePrefix("i=")?.toLongOrNull()
    return when {
        kind == "album" && song != null -> AppleMusicRef.Song(song, country)
        kind == "album" -> id.toLongOrNull()?.let { AppleMusicRef.Album(it, country) }
        kind == "song" -> id.toLongOrNull()?.let { AppleMusicRef.Song(it, country) }
        else -> id.toLongOrNull()?.let { AppleMusicRef.Artist(it, country) }
    }
}

/** The cover at 100 px (documented) and the larger sizes made from its URL; only the 100 px one without that URL form. */
internal fun appleArtworkOf(result: JsonElement?): List<ExcorThumbnail> {
    val url = result.at("artworkUrl100").str ?: return emptyList()
    if (!ARTWORK_SIZE.containsMatchIn(url)) return listOf(ExcorThumbnail(url, 100, 100))
    return ARTWORK_SIZES.map { size -> ExcorThumbnail(url.replace(ARTWORK_SIZE) { "/${size}x${size}bb.${it.groupValues[1]}" }, size, size) }
}

private fun JsonElement?.long(key: String) = at(key).str?.toLongOrNull()

private fun JsonElement?.int(key: String) = at(key).str?.toIntOrNull()

internal fun appleSongOf(r: JsonElement?): AppleMusicSong? {
    if (r.at("wrapperType").str != "track" || r.at("kind").str != "song") return null
    return AppleMusicSong(
        id = r.long("trackId") ?: return null,
        title = r.at("trackName").str,
        artist = r.at("artistName").str,
        artistId = r.long("artistId"),
        album = r.at("collectionName").str,
        albumId = r.long("collectionId"),
        durationMs = r.long("trackTimeMillis"),
        trackNumber = r.int("trackNumber"),
        trackCount = r.int("trackCount"),
        discNumber = r.int("discNumber"),
        discCount = r.int("discCount"),
        releaseDate = r.at("releaseDate").str,
        genre = r.at("primaryGenreName").str,
        isExplicit = r.at("trackExplicitness").str == "explicit",
        artwork = appleArtworkOf(r),
        url = r.at("trackViewUrl").str?.substringBefore("&uo="),
    )
}

internal fun appleAlbumOf(r: JsonElement?, songs: List<AppleMusicSong> = emptyList()): AppleMusicAlbum? {
    if (r.at("wrapperType").str != "collection") return null
    return AppleMusicAlbum(
        id = r.long("collectionId") ?: return null,
        title = r.at("collectionName").str,
        artist = r.at("artistName").str,
        artistId = r.long("artistId"),
        trackCount = r.int("trackCount"),
        releaseDate = r.at("releaseDate").str,
        genre = r.at("primaryGenreName").str,
        copyright = r.at("copyright").str,
        isExplicit = r.at("collectionExplicitness").str == "explicit",
        artwork = appleArtworkOf(r),
        url = r.at("collectionViewUrl").str?.substringBefore("?uo="),
        songs = songs,
    )
}

/** An album lookup with entity=song: the album first, then its songs, put in disc and track order. */
internal fun appleAlbumWithSongsOf(results: List<JsonElement>): AppleMusicAlbum? {
    val album = results.firstOrNull { it.at("wrapperType").str == "collection" } ?: return null
    val songs = results.mapNotNull(::appleSongOf).sortedWith(compareBy({ it.discNumber ?: 0 }, { it.trackNumber ?: 0 }))
    return appleAlbumOf(album, songs)
}

/** An artist lookup with entity=album: the artist first, then their albums. */
internal fun appleArtistOf(results: List<JsonElement>): AppleMusicArtist? {
    val artist = results.firstOrNull { it.at("wrapperType").str == "artist" } ?: return null
    return AppleMusicArtist(
        id = artist.long("artistId") ?: return null,
        name = artist.at("artistName").str,
        genre = artist.at("primaryGenreName").str,
        url = artist.at("artistLinkUrl").str?.substringBefore("?uo="),
        albums = results.mapNotNull { appleAlbumOf(it) },
    )
}

/**
 * An artist's picture from their Apple Music page's share image (`og:image`): as the page gives it (a wide crop) and as
 * squares at 600, 1200 and 3000 px made from the same URL. Empty when the page has none on Apple's image server.
 */
internal fun artistImagesOf(head: String): List<ExcorThumbnail> {
    val url = OG_IMAGE.find(head)?.groupValues?.get(1)?.replace("&amp;", "&")?.takeIf { ARTIST_IMAGE.matches(it) } ?: return emptyList()
    val width = OG_IMAGE_WIDTH.find(head)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val height = OG_IMAGE_HEIGHT.find(head)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val base = url.substringBeforeLast('/')
    return listOf(ExcorThumbnail(url, width, height)) + ARTIST_IMAGE_SIZES.map { ExcorThumbnail("$base/${it}x${it}cc.jpg", it, it) }
}

internal class AppleMusicEngine(private val host: ExcorHostServices, private val http: HttpClient) {
    /** An artist's picture, from their Apple Music page (see [artistImagesOf]); empty for an unknown artist. */
    suspend fun artistImages(id: Long, country: String?): List<ExcorThumbnail> {
        require(id > 0) { "Excor: not an Apple Music ID" }
        val store = storeOf(country)
        val head = http.prepareGet("https://music.apple.com/$store/artist/$id") { header(HttpHeaders.UserAgent, DESKTOP_UA) }.execute { response ->
            if (response.status == HttpStatusCode.NotFound) return@execute null
            check(response.status.isSuccess()) { "Excor: Apple Music page HTTP ${response.status.value}" }
            val channel = response.bodyAsChannel()
            val text = StringBuilder()
            while (!channel.isClosedForRead && "</head>" !in text && text.length < ARTIST_PAGE_HEAD_MAX) {
                text.append(channel.readBuffer(16L * 1024).readByteArray().decodeToString())
            }
            text.toString()
        } ?: return emptyList()
        return artistImagesOf(head)
    }

    suspend fun song(id: Long, country: String?): AppleMusicSong? =
        lookup(id, country, emptyList()).firstNotNullOfOrNull(::appleSongOf)

    suspend fun album(id: Long, country: String?): AppleMusicAlbum? =
        appleAlbumWithSongsOf(lookup(id, country, listOf("entity" to "song")))

    suspend fun artist(id: Long, country: String?, albumLimit: Int): AppleMusicArtist? {
        require(albumLimit in 1..200) { "Excor: albumLimit is 1 to 200" }
        return appleArtistOf(lookup(id, country, listOf("entity" to "album", "limit" to albumLimit.toString())))
    }

    suspend fun searchSongs(term: String, country: String?, limit: Int): List<AppleMusicSong> =
        search(term, country, limit, "song").mapNotNull(::appleSongOf)

    suspend fun searchAlbums(term: String, country: String?, limit: Int): List<AppleMusicAlbum> =
        search(term, country, limit, "album").mapNotNull { appleAlbumOf(it) }

    private suspend fun lookup(id: Long, country: String?, extra: List<Pair<String, String>>): List<JsonElement> {
        require(id > 0) { "Excor: not an Apple Music ID" }
        return call("lookup", listOf("id" to id.toString()) + extra, country)
    }

    private suspend fun search(term: String, country: String?, limit: Int, entity: String): List<JsonElement> {
        require(term.isNotBlank()) { "Excor: empty search" }
        require(limit in 1..200) { "Excor: limit is 1 to 200" }
        return call("search", listOf("term" to term.trim(), "media" to "music", "entity" to entity, "limit" to limit.toString()), country)
    }

    private suspend fun storeOf(country: String?): String {
        val store = (country ?: host.session().region).lowercase()
        require(COUNTRY.matches(store)) { "Excor: $store is not a two-letter country code" }
        return store
    }

    /** One API call's `results`. A refusal (Apple allows about 20 calls a minute) throws with its status only. */
    private suspend fun call(endpoint: String, params: List<Pair<String, String>>, country: String?): List<JsonElement> {
        val store = storeOf(country)
        val query = (params + ("country" to store)).joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" }
        val response = http.get("$ITUNES_API/$endpoint?$query") { header(HttpHeaders.UserAgent, DESKTOP_UA) }
        check(response.status.isSuccess()) { "Excor: Apple's Search API HTTP ${response.status.value}" }
        return Json.parseToJsonElement(response.bodyAsText()).at("results").items
    }
}
