package com.excor.catalog

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

internal class SpotifyProvider(
    private val http: HttpClient,
    private val tokenProvider: ExcorCatalogTokenProvider?,
) {
    private val json = Json
    val hasTokenProvider: Boolean get() = tokenProvider != null

    suspend fun search(
        query: String,
        kind: ExcorCatalogKind,
        limit: Int,
        pageToken: String?,
    ): ExcorCatalogPage {
        val term = query.trim()
        if (term.isEmpty() || term.length > MAX_QUERY_LENGTH || limit !in 1..MAX_LIMIT) {
            throw error(ExcorCatalogError.INVALID_QUERY)
        }
        val scope = "search|${kind.name}|$term"
        val offset = pageOffset(scope, limit, pageToken)
        val root = getJson(
            "search",
            listOf(
                "q" to term,
                "type" to searchType(kind),
                "limit" to limit.toString(),
                "offset" to offset.toString(),
            ),
        )
        return parsePage(root, kind, scope, limit, offset)
    }

    suspend fun lookup(value: String): ExcorCatalogItem? {
        val reference = parseSpotifyUrl(value)
        val result = getJson("${resourcePath(reference.kind)}/${reference.id}", emptyList())
        return parseItem(result, reference.kind)
    }

    suspend fun albumTracks(albumId: String, pageToken: String?): ExcorCatalogPage {
        validateId(albumId)
        val scope = "album-tracks|$albumId"
        val offset = pageOffset(scope, MAX_LIMIT, pageToken)
        val root = getJson(
            "albums/$albumId/tracks",
            listOf("limit" to MAX_LIMIT.toString(), "offset" to offset.toString()),
        )
        return parsePage(root, ExcorCatalogKind.TRACK, scope, MAX_LIMIT, offset, albumId)
    }

    suspend fun artistAlbums(artistId: String, pageToken: String?): ExcorCatalogPage {
        validateId(artistId)
        val scope = "artist-albums|$artistId"
        val offset = pageOffset(scope, MAX_LIMIT, pageToken)
        val root = getJson(
            "artists/$artistId/albums",
            listOf(
                "include_groups" to "album,single,compilation,appears_on",
                "limit" to MAX_LIMIT.toString(),
                "offset" to offset.toString(),
            ),
        )
        val page = parsePage(root, ExcorCatalogKind.ALBUM, scope, MAX_LIMIT, offset)
        return page.copy(items = page.items.filter { album -> album.artists.any { it.id == artistId } })
    }

    private suspend fun getJson(path: String, parameters: List<Pair<String, String>>): JsonObject {
        val token = accessToken()
        val url = URLBuilder("https://api.spotify.com/v1/$path").apply {
            parameters.forEach { (name, value) -> this.parameters.append(name, value) }
        }.buildString()
        val response = try {
            http.get(url) {
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw error(ExcorCatalogError.NETWORK_FAILURE)
        }

        if (!response.status.isSuccess()) {
            response.bodyAsChannel().cancel()
            val category = when (response.status.value) {
                400 -> ExcorCatalogError.INVALID_REQUEST
                401 -> ExcorCatalogError.AUTHENTICATION_REQUIRED
                403 -> ExcorCatalogError.ACCESS_DENIED
                404 -> ExcorCatalogError.NOT_FOUND
                429 -> ExcorCatalogError.RATE_LIMITED
                else -> ExcorCatalogError.PROVIDER_UNAVAILABLE
            }
            throw error(category, response.status.value)
        }
        if (response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let { it > MAX_RESPONSE_BYTES } == true) {
            response.bodyAsChannel().cancel()
            throw error(ExcorCatalogError.RESPONSE_TOO_LARGE)
        }
        val contentType = response.headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim()
        if (contentType != "application/json") {
            response.bodyAsChannel().cancel()
            throw error(ExcorCatalogError.MALFORMED_RESPONSE)
        }

        val channel = response.bodyAsChannel()
        val bytes = try {
            channel.readBuffer(MAX_RESPONSE_BYTES + 1L).readByteArray()
        } finally {
            channel.cancel()
        }
        if (bytes.size > MAX_RESPONSE_BYTES) throw error(ExcorCatalogError.RESPONSE_TOO_LARGE)
        val root = try {
            json.parseToJsonElement(bytes.decodeToString()).jsonObject
        } catch (_: SerializationException) {
            throw error(ExcorCatalogError.MALFORMED_RESPONSE)
        } catch (_: IllegalArgumentException) {
            throw error(ExcorCatalogError.MALFORMED_RESPONSE)
        }
        return root
    }

    private suspend fun accessToken(): String {
        val value = try {
            tokenProvider?.accessToken(ExcorCatalogProvider.SPOTIFY)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw error(ExcorCatalogError.CREDENTIAL_PROVIDER_FAILED)
        } ?: throw error(ExcorCatalogError.AUTHENTICATION_REQUIRED)
        if (value.isBlank()) throw error(ExcorCatalogError.AUTHENTICATION_REQUIRED)
        if (value != value.trim() || value.length > MAX_TOKEN_LENGTH || value.any { it <= ' ' || it == '\u007f' }) {
            throw error(ExcorCatalogError.INVALID_CREDENTIAL)
        }
        return value
    }

    private fun parsePage(
        root: JsonObject,
        kind: ExcorCatalogKind,
        scope: String,
        limit: Int,
        offset: Int,
        albumId: String? = null,
    ): ExcorCatalogPage {
        val key = when (kind) {
            ExcorCatalogKind.TRACK -> "tracks"
            ExcorCatalogKind.ALBUM -> "albums"
            ExcorCatalogKind.ARTIST -> "artists"
        }
        val page = (root[key] as? JsonObject) ?: root.takeIf { it["items"] is JsonArray }
            ?: throw error(ExcorCatalogError.MALFORMED_RESPONSE)
        val rawItems = page.array("items") ?: throw error(ExcorCatalogError.MALFORMED_RESPONSE)
        val items = rawItems.mapNotNull { (it as? JsonObject)?.let { item -> parseItem(item, kind, albumId) } }
        val pageOffset = page.int("offset") ?: offset
        val pageLimit = page.int("limit") ?: limit
        val total = page.int("total") ?: throw error(ExcorCatalogError.MALFORMED_RESPONSE)
        if (pageOffset != offset || pageLimit != limit || total < 0) {
            throw error(ExcorCatalogError.MALFORMED_RESPONSE)
        }
        val nextOffset = pageOffset + pageLimit
        val next = if (items.isNotEmpty() && nextOffset < total && nextOffset <= MAX_OFFSET) {
            pageToken(scope, pageLimit, nextOffset)
        } else {
            null
        }
        return ExcorCatalogPage(items, nextPageToken = next)
    }

    private fun parseItem(
        value: JsonObject,
        kind: ExcorCatalogKind,
        albumIdOverride: String? = null,
    ): ExcorCatalogItem? {
        val id = value.text("id")?.takeIf(SPOTIFY_ID::matches) ?: return null
        val title = value.text("name")?.takeIf(String::isNotBlank) ?: return null
        val album = value.obj("album")
        val artists = (value.array("artists") ?: album?.array("artists")).orEmpty()
            .mapNotNull { (it as? JsonObject)?.let(::parseArtistCredit) }
        val images = (value.array("images") ?: album?.array("images")).orEmpty()
            .mapNotNull { (it as? JsonObject)?.let(::parseArtwork) }
        val albumId = albumIdOverride ?: album?.text("id")?.takeIf(SPOTIFY_ID::matches)
        val albumTitle = album?.text("name")
        return ExcorCatalogItem(
            provider = ExcorCatalogProvider.SPOTIFY,
            kind = kind,
            id = id,
            title = title,
            url = value.spotifyUrl(),
            artists = artists,
            albumId = albumId,
            albumTitle = albumTitle,
            durationMs = value.long("duration_ms"),
            releaseDate = (album ?: value).text("release_date")?.substringBefore('T'),
            trackNumber = value.int("track_number"),
            discNumber = value.int("disc_number"),
            trackCount = album?.int("total_tracks") ?: value.int("total_tracks"),
            isExplicit = value.boolean("explicit"),
            genres = value.stringArray("genres"),
            artwork = images,
        )
    }

    private fun parseArtistCredit(value: JsonObject): ExcorCatalogArtist? {
        val id = value.text("id")?.takeIf(SPOTIFY_ID::matches) ?: return null
        val name = value.text("name")?.takeIf(String::isNotBlank) ?: return null
        return ExcorCatalogArtist(ExcorCatalogProvider.SPOTIFY, id, name, value.spotifyUrl())
    }

    private fun parseArtwork(value: JsonObject): ExcorArtwork? {
        val url = value.text("url") ?: return null
        val image = runCatching { Url(url) }.getOrNull() ?: return null
        if (image.protocol.name != "https" || image.port != 443 || image.host != "i.scdn.co") return null
        return ExcorArtwork(url, value.int("width"), value.int("height"))
    }

    private fun JsonObject.spotifyUrl(): String? {
        val value = obj("external_urls")?.text("spotify") ?: return null
        val url = runCatching { Url(value) }.getOrNull() ?: return null
        return value.takeIf { url.protocol.name == "https" && url.port == 443 && url.host == "open.spotify.com" }
    }

    private fun parseSpotifyUrl(value: String): SpotifyReference {
        val url = runCatching { Url(value) }.getOrNull() ?: throw error(ExcorCatalogError.INVALID_URL)
        if (url.protocol.name != "https" || url.port != 443 || url.user != null || url.password != null || url.host != "open.spotify.com") {
            throw error(ExcorCatalogError.INVALID_URL)
        }
        val segments = url.encodedPath.split('/').filter(String::isNotEmpty)
        val kindIndex = segments.indexOfFirst { it in SPOTIFY_RESOURCE_TYPES }
        if (kindIndex !in 0..1 || (kindIndex == 1 && !SPOTIFY_LOCALE.matches(segments.first()))) {
            throw error(ExcorCatalogError.INVALID_URL)
        }
        val type = segments.getOrNull(kindIndex) ?: throw error(ExcorCatalogError.INVALID_URL)
        if (segments.size != kindIndex + 2) throw error(ExcorCatalogError.INVALID_URL)
        val id = segments.getOrNull(kindIndex + 1)?.takeIf(SPOTIFY_ID::matches)
            ?: throw error(ExcorCatalogError.INVALID_URL)
        val kind = when (type) {
            "track" -> ExcorCatalogKind.TRACK
            "album" -> ExcorCatalogKind.ALBUM
            "artist" -> ExcorCatalogKind.ARTIST
            else -> throw error(ExcorCatalogError.UNSUPPORTED)
        }
        return SpotifyReference(kind, id)
    }

    private fun pageOffset(scope: String, limit: Int, token: String?): Int {
        if (token == null) return 0
        val prefix = "SPOTIFY|$scope|$limit|"
        if (token.length > MAX_PAGE_TOKEN_LENGTH || !token.startsWith(prefix)) {
            throw error(ExcorCatalogError.INVALID_PAGE_TOKEN)
        }
        return token.removePrefix(prefix).toIntOrNull()?.takeIf { it in 0..MAX_OFFSET }
            ?: throw error(ExcorCatalogError.INVALID_PAGE_TOKEN)
    }

    private fun pageToken(scope: String, limit: Int, offset: Int) = "SPOTIFY|$scope|$limit|$offset"

    private fun validateId(id: String) {
        if (!SPOTIFY_ID.matches(id)) throw error(ExcorCatalogError.INVALID_URL)
    }

    private fun searchType(kind: ExcorCatalogKind): String = when (kind) {
        ExcorCatalogKind.TRACK -> "track"
        ExcorCatalogKind.ALBUM -> "album"
        ExcorCatalogKind.ARTIST -> "artist"
    }

    private fun resourcePath(kind: ExcorCatalogKind): String = when (kind) {
        ExcorCatalogKind.TRACK -> "tracks"
        ExcorCatalogKind.ALBUM -> "albums"
        ExcorCatalogKind.ARTIST -> "artists"
    }

    private fun error(kind: ExcorCatalogError, statusCode: Int? = null) =
        ExcorCatalogException(ExcorCatalogProvider.SPOTIFY, kind, statusCode)

    private data class SpotifyReference(val kind: ExcorCatalogKind, val id: String)

    private companion object {
        val SPOTIFY_ID = Regex("[A-Za-z0-9]{22}")
        val SPOTIFY_LOCALE = Regex("intl-[A-Za-z]{2}")
        val SPOTIFY_RESOURCE_TYPES = setOf("track", "album", "artist")
        const val MAX_QUERY_LENGTH = 200
        const val MAX_LIMIT = 50
        const val MAX_OFFSET = 10_000
        const val MAX_PAGE_TOKEN_LENGTH = 1_024
        const val MAX_TOKEN_LENGTH = 4_096
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    }
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull

private fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.longOrNull

private fun JsonObject.boolean(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.stringArray(name: String): List<String> = array(name).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
