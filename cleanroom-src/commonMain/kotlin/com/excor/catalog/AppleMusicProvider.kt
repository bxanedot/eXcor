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
import kotlinx.io.readByteArray

internal class AppleMusicProvider(private val http: HttpClient) {
    private val json = Json

    suspend fun search(query: String, kind: ExcorCatalogKind, limit: Int, pageToken: String?): ExcorCatalogPage {
        val term = query.trim()
        if (term.isEmpty() || term.length > MAX_QUERY_LENGTH || limit !in 1..MAX_LIMIT) {
            throw error(ExcorCatalogError.INVALID_QUERY)
        }
        val offset = pageOffset("search:${kind.name}:$term", pageToken)
        val body = getJson(
            "search",
            listOf(
                "term" to term,
                "media" to "music",
                "entity" to searchEntity(kind),
                "limit" to limit.toString(),
                "offset" to offset.toString(),
            ),
        )
        val results = body.results().mapNotNull { parseItem(it, kind) }
        val next = if (results.size >= limit) pageToken("search:${kind.name}:$term", offset + limit) else null
        return ExcorCatalogPage(results, nextPageToken = next)
    }

    suspend fun lookup(url: String): ExcorCatalogItem? {
        val reference = parseAppleUrl(url)
        val entity = if (reference.kind == ExcorCatalogKind.ARTIST) null else "song"
        val params = buildList {
            add("id" to reference.id)
            if (entity != null) add("entity" to entity)
            add("limit" to MAX_LIMIT.toString())
        }
        return getJson("lookup", params).results()
            .asSequence()
            .mapNotNull { parseItem(it) }
            .firstOrNull { it.kind == reference.kind && it.id == reference.id }
    }

    suspend fun albumTracks(albumId: String, pageToken: String?): ExcorCatalogPage {
        if (pageToken != null) throw error(ExcorCatalogError.INVALID_PAGE_TOKEN)
        validateId(albumId)
        val results = getJson(
            "lookup",
            listOf("id" to albumId, "entity" to "song", "limit" to MAX_LIMIT.toString()),
        ).results()
        val tracks = results.mapNotNull { parseItem(it, ExcorCatalogKind.TRACK) }
            .filter { it.albumId == albumId }
            .sortedWith(compareBy({ it.discNumber ?: 1 }, { it.trackNumber ?: Int.MAX_VALUE }))
        return ExcorCatalogPage(tracks, truncated = tracks.size == MAX_LIMIT)
    }

    suspend fun artistAlbums(artistId: String, pageToken: String?): ExcorCatalogPage {
        if (pageToken != null) throw error(ExcorCatalogError.INVALID_PAGE_TOKEN)
        validateId(artistId)
        val results = getJson(
            "lookup",
            listOf("id" to artistId, "entity" to "album", "limit" to MAX_LIMIT.toString()),
        ).results()
        val albums = results.mapNotNull { parseItem(it, ExcorCatalogKind.ALBUM) }
            .filter { album -> album.artists.any { it.id == artistId } }
        return ExcorCatalogPage(albums, truncated = albums.size == MAX_LIMIT)
    }

    private suspend fun getJson(path: String, parameters: List<Pair<String, String>>): JsonObject {
        val url = URLBuilder("https://itunes.apple.com/$path").apply {
            parameters.forEach { (name, value) -> this.parameters.append(name, value) }
        }.buildString()
        val response = try {
            http.get(url) { header(HttpHeaders.Accept, "application/json") }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw error(ExcorCatalogError.NETWORK_FAILURE)
        }

        if (!response.status.isSuccess()) {
            response.bodyAsChannel().cancel()
            val category = when (response.status.value) {
                401 -> ExcorCatalogError.AUTHENTICATION_REQUIRED
                403 -> ExcorCatalogError.ACCESS_DENIED
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
        if (root["results"] !is JsonArray) throw error(ExcorCatalogError.MALFORMED_RESPONSE)
        return root
    }

    private fun JsonObject.results(): List<JsonObject> =
        (this["results"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    private fun parseItem(value: JsonObject, expected: ExcorCatalogKind? = null): ExcorCatalogItem? {
        val kind = when {
            value.text("wrapperType") == "artist" -> ExcorCatalogKind.ARTIST
            value.text("wrapperType") == "collection" && value.text("collectionType").equals("Album", true) -> ExcorCatalogKind.ALBUM
            value.text("wrapperType") == "track" && value.text("kind") == "song" -> ExcorCatalogKind.TRACK
            else -> return null
        }
        if (expected != null && kind != expected) return null
        val idKey = if (kind == ExcorCatalogKind.ALBUM) "collectionId" else if (kind == ExcorCatalogKind.ARTIST) "artistId" else "trackId"
        val titleKey = when (kind) {
            ExcorCatalogKind.TRACK -> "trackName"
            ExcorCatalogKind.ALBUM -> "collectionName"
            ExcorCatalogKind.ARTIST -> "artistName"
        }
        val id = value.text(idKey)?.takeIf(String::isNotBlank) ?: return null
        val title = value.text(titleKey)?.takeIf(String::isNotBlank) ?: return null
        val artistId = value.text("artistId")
        val artistName = value.text("artistName")
        val artists = if (kind != ExcorCatalogKind.ARTIST && artistId != null && artistName != null) {
            listOf(
                ExcorCatalogArtist(
                    provider = ExcorCatalogProvider.APPLE_MUSIC,
                    id = artistId,
                    name = artistName,
                    url = value.text("artistViewUrl"),
                ),
            )
        } else {
            emptyList()
        }
        val artworkUrl = value.text("artworkUrl100")
        val artwork = artworkUrl?.let(::appleArtwork).let(::listOfNotNull)
        return ExcorCatalogItem(
            provider = ExcorCatalogProvider.APPLE_MUSIC,
            kind = kind,
            id = id,
            title = title,
            url = when (kind) {
                ExcorCatalogKind.TRACK -> value.text("trackViewUrl")
                ExcorCatalogKind.ALBUM -> value.text("collectionViewUrl")
                ExcorCatalogKind.ARTIST -> value.text("artistLinkUrl")
            },
            artists = artists,
            albumId = value.text("collectionId"),
            albumTitle = value.text("collectionName"),
            durationMs = value.long("trackTimeMillis"),
            releaseDate = value.text("releaseDate")?.substringBefore('T'),
            trackNumber = value.int("trackNumber"),
            discNumber = value.int("discNumber"),
            trackCount = value.int("trackCount"),
            isExplicit = value.boolean("isExplicit") ?: value.text("trackExplicitness")?.let { it == "explicit" },
            genres = value.text("primaryGenreName")?.let(::listOf) ?: emptyList(),
            artwork = artwork,
        )
    }

    private fun appleArtwork(value: String): ExcorArtwork? {
        val url = runCatching { Url(value) }.getOrNull() ?: return null
        if (url.protocol.name != "https" || url.port != 443 ||
            !APPLE_IMAGE_HOSTS.matches(url.host)
        ) return null
        val size = ARTWORK_SIZE.find(url.encodedPath)
        return ExcorArtwork(value, size?.groupValues?.get(1)?.toIntOrNull(), size?.groupValues?.get(2)?.toIntOrNull())
    }

    private fun parseAppleUrl(value: String): AppleReference {
        val url = runCatching { Url(value) }.getOrNull() ?: throw error(ExcorCatalogError.INVALID_URL)
        if (url.protocol.name != "https" || url.port != 443 || url.user != null || url.password != null || url.host != "music.apple.com") {
            throw error(ExcorCatalogError.INVALID_URL)
        }
        val segments = url.encodedPath.split('/').filter(String::isNotEmpty)
        val artistIndex = segments.indexOf("artist")
        if (artistIndex >= 0) {
            val id = segments.drop(artistIndex + 1).lastOrNull()?.takeIf(NUMERIC_ID::matches)
                ?: throw error(ExcorCatalogError.INVALID_URL)
            return AppleReference(ExcorCatalogKind.ARTIST, id)
        }
        val albumIndex = segments.indexOf("album")
        if (albumIndex >= 0) {
            val albumId = segments.drop(albumIndex + 1).lastOrNull()?.takeIf(NUMERIC_ID::matches)
                ?: throw error(ExcorCatalogError.INVALID_URL)
            val trackId = url.parameters["i"]
            return if (trackId != null && NUMERIC_ID.matches(trackId)) {
                AppleReference(ExcorCatalogKind.TRACK, trackId)
            } else {
                AppleReference(ExcorCatalogKind.ALBUM, albumId)
            }
        }
        val songIndex = segments.indexOf("song")
        if (songIndex >= 0) {
            val id = segments.drop(songIndex + 1).lastOrNull()?.takeIf(NUMERIC_ID::matches)
                ?: throw error(ExcorCatalogError.INVALID_URL)
            return AppleReference(ExcorCatalogKind.TRACK, id)
        }
        throw error(ExcorCatalogError.INVALID_URL)
    }

    private fun pageOffset(scope: String, token: String?): Int {
        if (token == null) return 0
        val prefix = "APPLE_MUSIC|$scope|"
        if (!token.startsWith(prefix) || token.length > MAX_PAGE_TOKEN_LENGTH) throw error(ExcorCatalogError.INVALID_PAGE_TOKEN)
        return token.removePrefix(prefix).toIntOrNull()?.takeIf { it in 0..MAX_OFFSET }
            ?: throw error(ExcorCatalogError.INVALID_PAGE_TOKEN)
    }

    private fun pageToken(scope: String, offset: Int): String = "APPLE_MUSIC|$scope|$offset"

    private fun validateId(id: String) {
        if (!NUMERIC_ID.matches(id)) throw error(ExcorCatalogError.INVALID_URL)
    }

    private fun searchEntity(kind: ExcorCatalogKind): String = when (kind) {
        ExcorCatalogKind.TRACK -> "musicTrack"
        ExcorCatalogKind.ALBUM -> "album"
        ExcorCatalogKind.ARTIST -> "musicArtist"
    }

    private fun error(kind: ExcorCatalogError, statusCode: Int? = null) =
        ExcorCatalogException(ExcorCatalogProvider.APPLE_MUSIC, kind, statusCode)

    private data class AppleReference(val kind: ExcorCatalogKind, val id: String)

    private companion object {
        val APPLE_IMAGE_HOSTS = Regex("is[0-9]+-ssl\\.mzstatic\\.com")
        val ARTWORK_SIZE = Regex("/(\\d+)x(\\d+)bb\\.[A-Za-z0-9]+$")
        val NUMERIC_ID = Regex("[0-9]{1,20}")
        const val MAX_QUERY_LENGTH = 200
        const val MAX_LIMIT = 200
        const val MAX_OFFSET = 1_000_000
        const val MAX_PAGE_TOKEN_LENGTH = 1_024
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    }
}

private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull

private fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.longOrNull

private fun JsonObject.boolean(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull
