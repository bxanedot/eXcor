package com.vyonex.internal

import com.vyonex.MusicCatalogCredit
import com.vyonex.MusicCatalogImage
import com.vyonex.MusicCatalogItem
import com.vyonex.MusicCatalogKind
import com.vyonex.MusicCatalogProvider
import com.vyonex.MusicCatalogRef
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

// Independent implementations of the documented Spotify oEmbed/Web API and Deezer public catalog endpoints.
// No DRM, previews, streaming links or audio data is requested or returned.
private val SPOTIFY_LINK =
    Regex("""https://open\.spotify\.com/(?:intl-[a-z]{2}/)?(track|album|artist|playlist|show|episode)/([A-Za-z0-9]{22})/?(?:[?#].*)?""")
private val SPOTIFY_URI = Regex("""spotify:(track|album|artist|playlist|show|episode):([A-Za-z0-9]{22})""")
private val DEEZER_LINK =
    Regex("""https://(?:www\.)?deezer\.com/(?:[a-z]{2}/)?(track|album|artist|playlist)/(\d+)/?(?:[?#].*)?""")

private fun kindOf(value: String): MusicCatalogKind = when (value) {
    "track" -> MusicCatalogKind.TRACK
    "album" -> MusicCatalogKind.ALBUM
    "artist" -> MusicCatalogKind.ARTIST
    "playlist" -> MusicCatalogKind.PLAYLIST
    "show" -> MusicCatalogKind.SHOW
    else -> MusicCatalogKind.EPISODE
}

internal fun musicCatalogRefOf(input: String): MusicCatalogRef? {
    val text = input.trim()
    SPOTIFY_LINK.matchEntire(text)?.let {
        val (kind, id) = it.destructured
        return MusicCatalogRef(MusicCatalogProvider.SPOTIFY, kindOf(kind), id, "https://open.spotify.com/$kind/$id")
    }
    SPOTIFY_URI.matchEntire(text)?.let {
        val (kind, id) = it.destructured
        return MusicCatalogRef(MusicCatalogProvider.SPOTIFY, kindOf(kind), id, "https://open.spotify.com/$kind/$id")
    }
    DEEZER_LINK.matchEntire(text)?.let {
        val (kind, id) = it.destructured
        return MusicCatalogRef(MusicCatalogProvider.DEEZER, kindOf(kind), id, "https://www.deezer.com/$kind/$id")
    }
    return null
}

private fun JsonElement?.field(key: String): JsonElement? = (this as? JsonObject)?.get(key)
private fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
private fun JsonElement?.intValue(): Int? = (this as? JsonPrimitive)?.intOrNull
private fun JsonElement?.longValue(): Long? = (this as? JsonPrimitive)?.longOrNull
private fun JsonElement?.boolValue(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull
private fun JsonElement?.items(): List<JsonElement> = (this as? JsonArray)?.toList().orEmpty()
private fun JsonElement?.str(key: String) = field(key).string()
private fun JsonElement?.int(key: String) = field(key).intValue()
private fun JsonElement?.long(key: String) = field(key).longValue()
private fun JsonElement?.bool(key: String) = field(key).boolValue()
private fun JsonElement?.names(): List<String> = items().mapNotNull { it.string() }

private fun catalogImage(url: String?, width: Int?, height: Int?): MusicCatalogImage? =
    url?.takeIf { it.startsWith("https://") }?.let { MusicCatalogImage(it, width, height) }

private fun spotifyImages(element: JsonElement?): List<MusicCatalogImage> =
    element.items().mapNotNull { catalogImage(it.str("url"), it.int("width"), it.int("height")) }.distinctBy { it.url }

private fun deezerImages(element: JsonElement?, prefix: String): List<MusicCatalogImage> =
    listOf("xl", "big", "medium", "small").mapNotNull { size ->
        // The Deezer API exposes size variants but does not report exact dimensions in the response.
        catalogImage(element.str("${prefix}_$size"), null, null)
    }.distinctBy { it.url }

private fun spotifyCredit(element: JsonElement): MusicCatalogCredit =
    MusicCatalogCredit(
        id = element.str("id"),
        name = element.str("name"),
        url = element.field("external_urls").str("spotify"),
        images = spotifyImages(element.field("images")),
    )

private fun deezerCredit(element: JsonElement, role: String? = null): MusicCatalogCredit =
    MusicCatalogCredit(
        id = element.str("id"),
        name = element.str("name"),
        url = element.str("link")?.takeIf { it.startsWith("https://") },
        images = deezerImages(element, "picture"),
        role = role ?: element.str("role"),
    )

private fun deezerRef(kind: MusicCatalogKind, id: String): MusicCatalogRef =
    MusicCatalogRef(MusicCatalogProvider.DEEZER, kind, id, "https://www.deezer.com/${kind.name.lowercase()}/$id")
private fun spotifyRef(kind: MusicCatalogKind, id: String): MusicCatalogRef =
    MusicCatalogRef(MusicCatalogProvider.SPOTIFY, kind, id, "https://open.spotify.com/${kind.name.lowercase()}/$id")

/** Results are parsed as data. All URLs we contact are constructed from validated IDs, never from API fields. */
internal fun deezerItemOf(obj: JsonElement, kind: MusicCatalogKind, fallbackAlbum: JsonElement? = null): MusicCatalogItem? {
    val id = obj.str("id") ?: return null
    val ref = deezerRef(kind, id)
    val artist = obj.field("artist")
    val credits = obj.field("contributors").items().map { deezerCredit(it) }
        .ifEmpty { listOfNotNull(artist?.let(::deezerCredit)) }
    val album = obj.field("album") ?: fallbackAlbum
    val image = when (kind) {
        MusicCatalogKind.TRACK -> deezerImages(album, "cover")
        MusicCatalogKind.ALBUM -> deezerImages(obj, "cover")
        else -> deezerImages(obj, "picture")
    }
    val count = obj.int("nb_tracks")
    val tracks = if (kind == MusicCatalogKind.ALBUM || kind == MusicCatalogKind.PLAYLIST) {
        obj.field("tracks").field("data").items().mapNotNull { deezerItemOf(it, MusicCatalogKind.TRACK, if (kind == MusicCatalogKind.ALBUM) obj else null) }
    } else emptyList()
    val genres = obj.field("genres").field("data").items().mapNotNull { it.str("name") }
    return MusicCatalogItem(
        provider = ref.provider,
        kind = kind,
        id = id,
        title = obj.str("title") ?: obj.str("name"),
        url = ref.url,
        images = image,
        artists = when (kind) {
            MusicCatalogKind.ARTIST -> listOf(deezerCredit(obj))
            MusicCatalogKind.ALBUM -> credits.ifEmpty { listOfNotNull(artist?.let(::deezerCredit)) }
            else -> credits
        },
        album = if (kind == MusicCatalogKind.TRACK) album.str("title") else null,
        albumId = if (kind == MusicCatalogKind.TRACK) album.str("id") else null,
        durationMs = obj.long("duration")?.times(1000),
        releaseDate = obj.str("release_date") ?: if (kind == MusicCatalogKind.TRACK) album.str("release_date") else null,
        genres = genres,
        explicit = obj.bool("explicit_lyrics"),
        isrc = obj.str("isrc"),
        upc = obj.str("upc"),
        trackNumber = obj.int("track_position"),
        discNumber = obj.int("disk_number"),
        trackCount = count,
        label = obj.str("label"),
        copyright = obj.str("copyright"),
        description = obj.str("description"),
        fanCount = obj.long("nb_fan"),
        tracks = tracks,
        tracksComplete = if (count != null && obj.field("tracks") != null) tracks.size >= count else null,
    )
}

internal fun spotifyItemOf(obj: JsonElement, kind: MusicCatalogKind, fallbackAlbum: JsonElement? = null): MusicCatalogItem? {
    val id = obj.str("id") ?: return null
    val ref = spotifyRef(kind, id)
    val album = obj.field("album") ?: fallbackAlbum
    val artists = obj.field("artists").items().map(::spotifyCredit).ifEmpty {
        if (kind == MusicCatalogKind.ARTIST) listOf(spotifyCredit(obj)) else emptyList()
    }
    val images = when (kind) {
        MusicCatalogKind.TRACK -> spotifyImages(album.field("images"))
        else -> spotifyImages(obj.field("images"))
    }
    val tracks = if (kind == MusicCatalogKind.ALBUM) obj.field("tracks").field("items").items().mapNotNull {
        spotifyItemOf(it, MusicCatalogKind.TRACK, obj)
    } else emptyList()
    val count = obj.int("total_tracks")
    return MusicCatalogItem(
        provider = ref.provider,
        kind = kind,
        id = id,
        title = obj.str("name"),
        url = ref.url,
        images = images,
        artists = artists,
        album = if (kind == MusicCatalogKind.TRACK) album.str("name") else null,
        albumId = if (kind == MusicCatalogKind.TRACK) album.str("id") else null,
        durationMs = obj.long("duration_ms"),
        releaseDate = obj.str("release_date") ?: if (kind == MusicCatalogKind.TRACK) album.str("release_date") else null,
        genres = obj.field("genres").names(),
        explicit = obj.bool("explicit"),
        isrc = obj.field("external_ids").str("isrc"),
        upc = obj.field("external_ids").str("upc"),
        trackNumber = obj.int("track_number"),
        discNumber = obj.int("disc_number"),
        trackCount = count,
        label = obj.str("label"),
        copyright = obj.field("copyrights").items().firstOrNull()?.str("text"),
        description = obj.str("description"),
        popularity = obj.int("popularity"),
        fanCount = obj.field("followers").long("total"),
        tracks = tracks,
        tracksComplete = if (count != null && obj.field("tracks") != null) tracks.size >= count else null,
    )
}

internal fun spotifyEmbedOf(obj: JsonElement, ref: MusicCatalogRef): MusicCatalogItem =
    MusicCatalogItem(
        provider = ref.provider,
        kind = ref.kind,
        id = ref.id,
        title = obj.str("title"),
        url = ref.url,
        images = listOfNotNull(catalogImage(obj.str("thumbnail_url"), obj.int("thumbnail_width"), obj.int("thumbnail_height"))),
        fullyEnriched = false,
    )

/** HTTP failure gives provider and code only; never puts URLs, session data or tokens in an exception. */
internal class MusicCatalogHttpError(provider: MusicCatalogProvider, status: Int) :
    IllegalStateException("VyonEx: ${provider.name} metadata HTTP $status")

internal class MusicCatalogEngine(private val http: HttpClient) {
    suspend fun get(ref: MusicCatalogRef, spotifyAccessToken: String?): MusicCatalogItem? {
        require(musicCatalogRefOf(ref.url) == ref) { "VyonEx: invalid music catalog reference" }
        return when (ref.provider) {
            MusicCatalogProvider.DEEZER -> {
                val json = read(MusicCatalogProvider.DEEZER, "https://api.deezer.com/${ref.kind.name.lowercase()}/${ref.id}")
                    ?: return null
                if (deezerError(json)) return null
                deezerItemOf(json, ref.kind)
            }
            MusicCatalogProvider.SPOTIFY -> {
                if (spotifyAccessToken.isNullOrBlank() ||
                    ref.kind !in setOf(MusicCatalogKind.TRACK, MusicCatalogKind.ALBUM, MusicCatalogKind.ARTIST)
                ) {
                    val url = "https://open.spotify.com/oembed?url=${ref.url.encodeURLParameter()}"
                    val obj = read(MusicCatalogProvider.SPOTIFY, url) ?: return null
                    spotifyEmbedOf(obj, ref)
                } else {
                    val token = validToken(spotifyAccessToken)
                    val type = when (ref.kind) {
                        MusicCatalogKind.TRACK -> "tracks"
                        MusicCatalogKind.ALBUM -> "albums"
                        else -> "artists"
                    }
                    read(MusicCatalogProvider.SPOTIFY, "https://api.spotify.com/v1/$type/${ref.id}", token)
                        ?.let { spotifyItemOf(it, ref.kind) }
                }
            }
        }
    }

    suspend fun searchDeezer(term: String, kind: MusicCatalogKind, limit: Int): List<MusicCatalogItem> {
        require(term.isNotBlank()) { "VyonEx: empty music search" }
        require(limit in 1..100) { "VyonEx: Deezer limit is 1 to 100" }
        require(kind in setOf(MusicCatalogKind.TRACK, MusicCatalogKind.ALBUM, MusicCatalogKind.ARTIST)) {
            "VyonEx: unsupported Deezer search kind"
        }
        val part = if (kind == MusicCatalogKind.TRACK) "search" else "search/${kind.name.lowercase()}"
        val json = read(MusicCatalogProvider.DEEZER, "https://api.deezer.com/$part?q=${term.trim().encodeURLParameter()}&limit=$limit")
            ?: return emptyList()
        if (deezerError(json)) return emptyList()
        return json.field("data").items().mapNotNull { deezerItemOf(it, kind) }
    }

    suspend fun searchSpotify(term: String, kind: MusicCatalogKind, accessToken: String, limit: Int): List<MusicCatalogItem> {
        require(term.isNotBlank()) { "VyonEx: empty music search" }
        require(limit in 1..10) { "VyonEx: Spotify limit is 1 to 10" }
        require(kind in setOf(MusicCatalogKind.TRACK, MusicCatalogKind.ALBUM, MusicCatalogKind.ARTIST)) {
            "VyonEx: unsupported Spotify search kind"
        }
        val token = validToken(accessToken)
        val type = kind.name.lowercase()
        val json = read(MusicCatalogProvider.SPOTIFY, "https://api.spotify.com/v1/search?q=${term.trim().encodeURLParameter()}&type=$type&limit=$limit", token)
            ?: return emptyList()
        return json.field("${type}s").field("items").items().mapNotNull { spotifyItemOf(it, kind) }
    }

    /** Deezer can report errors with HTTP 200; no-data (800) is an absent item, not an exception. */
    private fun deezerError(json: JsonElement): Boolean {
        val error = json.field("error") ?: return false
        if (error.int("code") == 800) return true
        val code = error.int("code") ?: 0
        throw IllegalStateException("VyonEx: DEEZER metadata error $code")
    }

    private fun validToken(raw: String): String {
        require(raw.isNotBlank() && raw == raw.trim() && '\n' !in raw && '\r' !in raw) {
            "VyonEx: invalid Spotify access token"
        }
        return raw
    }

    private suspend fun read(provider: MusicCatalogProvider, url: String, token: String? = null): JsonElement? {
        val response = http.get(url) {
            if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) throw MusicCatalogHttpError(provider, response.status.value)
        return Json.parseToJsonElement(response.bodyAsText())
    }
}
