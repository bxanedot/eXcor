package com.excor.music

import com.excor.catalog.ExcorArtwork
import com.excor.catalog.ExcorCatalogCapabilityStatus
import com.excor.catalog.ExcorCatalogError
import com.excor.catalog.ExcorCatalogProvider
import io.ktor.http.URLBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject

/**
 * Deezer's public API (api.deezer.com): catalog reads without credentials, and the user's library with a Deezer
 * OAuth access token from [token]. Full tracks play only in Deezer's own apps; the API's 30-second previews are noted
 * as available, never returned.
 */
internal class DeezerSource(
    private val http: MusicHttp,
    private val token: suspend () -> String?,
) : MusicSource(ExcorCatalogProvider.DEEZER) {

    override suspend fun capabilities(): Map<ExcorMusicCapability, ExcorCatalogCapabilityStatus> {
        val library = if (token() != null) ExcorCatalogCapabilityStatus.SUPPORTED else ExcorCatalogCapabilityStatus.AUTHENTICATION_REQUIRED
        val supported = setOf(
            ExcorMusicCapability.SEARCH, ExcorMusicCapability.TRACK, ExcorMusicCapability.ALBUM, ExcorMusicCapability.ARTIST,
            ExcorMusicCapability.PLAYLIST, ExcorMusicCapability.ALBUM_TRACKS, ExcorMusicCapability.ARTIST_ALBUMS,
            ExcorMusicCapability.PLAYLIST_TRACKS, ExcorMusicCapability.ISRC_LOOKUP, ExcorMusicCapability.UPC_LOOKUP,
            ExcorMusicCapability.CHARTS, ExcorMusicCapability.GENRES, ExcorMusicCapability.RADIOS,
            ExcorMusicCapability.RELATED_ARTISTS, ExcorMusicCapability.ARTIST_TOP_TRACKS, ExcorMusicCapability.MARKETS,
            ExcorMusicCapability.PREVIEW_AVAILABILITY,
        )
        return ExcorMusicCapability.entries.associateWith {
            when (it) {
                in supported -> ExcorCatalogCapabilityStatus.SUPPORTED
                ExcorMusicCapability.LIBRARY -> library
                // A host adapter can delegate to Deezer's own SDK; eXcor ships none.
                ExcorMusicCapability.PROTECTED_PLAYBACK -> ExcorCatalogCapabilityStatus.RESTRICTED
                else -> ExcorCatalogCapabilityStatus.UNSUPPORTED
            }
        }
    }

    override fun parse(url: String): ExcorMusicRef? {
        val parsed = httpsUrlOn(url, HOSTS) ?: return null
        val segments = parsed.encodedPath.split('/').filter(String::isNotEmpty)
        val rest = if (segments.firstOrNull()?.let(LANGUAGE::matches) == true) segments.drop(1) else segments
        if (rest.size != 2 || !ID.matches(rest[1])) return null
        val kind = when (rest[0]) {
            "track" -> ExcorMusicKind.TRACK
            "album" -> ExcorMusicKind.ALBUM
            "artist" -> ExcorMusicKind.ARTIST
            "playlist" -> ExcorMusicKind.PLAYLIST
            else -> return null
        }
        return ExcorMusicRef(provider, kind, rest[1])
    }

    override fun protection(track: ExcorMusicTrack) =
        ExcorProtectionInfo(true, setOf(ExcorDrmSystem.PROVIDER_MANAGED), source = ExcorProtectionSource.PROVIDER_STATEMENT)

    override suspend fun search(query: String, kinds: Set<ExcorMusicKind>, limit: Int, page: String?): ExcorMusicSearchResult {
        val q = query(query)
        if (limit !in 1..100) throw fail(ExcorCatalogError.INVALID_QUERY)
        val offset = offsetOf("search|${kinds.sorted()}|$q", page)
        fun next(total: Int?, count: Int) = if (count > 0 && total != null && offset + limit < total) pageToken("search|${kinds.sorted()}|$q", offset + limit) else null
        return coroutineScope {
            val tracks = if (ExcorMusicKind.TRACK in kinds) async { list("search/track", listOf("q" to q), offset, limit) } else null
            val albums = if (ExcorMusicKind.ALBUM in kinds) async { list("search/album", listOf("q" to q), offset, limit) } else null
            val artists = if (ExcorMusicKind.ARTIST in kinds) async { list("search/artist", listOf("q" to q), offset, limit) } else null
            val playlists = if (ExcorMusicKind.PLAYLIST in kinds) async { list("search/playlist", listOf("q" to q), offset, limit) } else null
            ExcorMusicSearchResult(
                tracks = tracks?.await()?.let { (items, total) -> ExcorMusicPage(items.mapNotNull(::trackOf), next(total, items.size), total) },
                albums = albums?.await()?.let { (items, total) -> ExcorMusicPage(items.mapNotNull(::albumOf), next(total, items.size), total) },
                artists = artists?.await()?.let { (items, total) -> ExcorMusicPage(items.mapNotNull(::artistOf), next(total, items.size), total) },
                playlists = playlists?.await()?.let { (items, total) -> ExcorMusicPage(items.mapNotNull(::playlistOf), next(total, items.size), total) },
            )
        }
    }

    override suspend fun track(id: String) = trackOf(get("track/${checked(id)}")) ?: throw fail(ExcorCatalogError.MALFORMED_RESPONSE)
    override suspend fun album(id: String) = albumOf(get("album/${checked(id)}")) ?: throw fail(ExcorCatalogError.MALFORMED_RESPONSE)
    override suspend fun artist(id: String) = artistOf(get("artist/${checked(id)}")) ?: throw fail(ExcorCatalogError.MALFORMED_RESPONSE)
    override suspend fun playlist(id: String) = playlistOf(get("playlist/${checked(id)}")) ?: throw fail(ExcorCatalogError.MALFORMED_RESPONSE)

    override suspend fun albumTracks(id: String, page: String?) = tracks("album/${checked(id)}/tracks", page)
    override suspend fun playlistTracks(id: String, page: String?) = tracks("playlist/${checked(id)}/tracks", page)
    override suspend fun artistAlbums(id: String, page: String?): ExcorMusicPage<ExcorMusicAlbum> {
        val path = "artist/${checked(id)}/albums"
        val offset = offsetOf(path, page)
        val (items, total) = list(path, emptyList(), offset, PAGE)
        return ExcorMusicPage(items.mapNotNull(::albumOf), nextOf(path, offset, items.size, total), total)
    }

    override suspend fun tracksByIsrc(isrc: String): List<ExcorMusicTrack> {
        val code = isrc.trim().uppercase().takeIf(ISRC::matches) ?: throw fail(ExcorCatalogError.INVALID_QUERY)
        return try {
            listOfNotNull(trackOf(get("track/isrc:$code")))
        } catch (e: com.excor.catalog.ExcorCatalogException) {
            if (e.error == ExcorCatalogError.NOT_FOUND) emptyList() else throw e
        }
    }

    suspend fun albumByUpc(upc: String): ExcorMusicAlbum? {
        val code = upc.trim().takeIf(UPC::matches) ?: throw fail(ExcorCatalogError.INVALID_QUERY)
        return try {
            albumOf(get("album/upc:$code"))
        } catch (e: com.excor.catalog.ExcorCatalogException) {
            if (e.error == ExcorCatalogError.NOT_FOUND) null else throw e
        }
    }

    override suspend fun libraryTracks(page: String?) = tracks("user/me/tracks", page, authorized = true)

    override suspend fun libraryPlaylists(page: String?): ExcorMusicPage<ExcorMusicPlaylist> {
        val path = "user/me/playlists"
        val offset = offsetOf(path, page)
        val (items, total) = list(path, emptyList(), offset, PAGE, authorized = true)
        return ExcorMusicPage(items.mapNotNull(::playlistOf), nextOf(path, offset, items.size, total), total)
    }

    suspend fun chartTracks(genreId: String, page: String?) = tracks("chart/${checked(genreId, allowZero = true)}/tracks", page)
    suspend fun artistTopTracks(id: String, page: String?) = tracks("artist/${checked(id)}/top", page)
    suspend fun radioTracks(id: String) = tracks("radio/${checked(id)}/tracks", null)

    suspend fun relatedArtists(id: String): List<ExcorMusicArtistProfile> =
        list("artist/${checked(id)}/related", emptyList(), 0, PAGE).first.mapNotNull(::artistOf)

    suspend fun genres(): List<ExcorDeezerGenre> = list("genre", emptyList(), 0, PAGE).first.mapNotNull { o ->
        val id = o.long("id")?.toString() ?: return@mapNotNull null
        ExcorDeezerGenre(id, o.str("name") ?: return@mapNotNull null, pictures(o, "picture"))
    }

    suspend fun radios(): List<ExcorDeezerRadio> = list("radio", emptyList(), 0, PAGE).first.mapNotNull { o ->
        val id = o.long("id")?.toString() ?: return@mapNotNull null
        ExcorDeezerRadio(id, o.str("title") ?: return@mapNotNull null, o.str("description"), pictures(o, "picture"))
    }

    // --- requests ---

    private suspend fun get(path: String, parameters: List<Pair<String, String>> = emptyList(), authorized: Boolean = false): JsonObject {
        val access = if (authorized) token() ?: throw fail(ExcorCatalogError.AUTHENTICATION_REQUIRED) else null
        val url = URLBuilder("https://api.deezer.com/$path").apply {
            parameters.forEach { (k, v) -> this.parameters.append(k, v) }
            // Deezer takes the token as a query parameter; such URLs are never cached or logged.
            access?.let { this.parameters.append("access_token", it) }
        }.buildString()
        val body = http.getJson(provider, url, cacheable = access == null).asObject(provider)
        body.obj("error")?.let { error ->
            val type = error.str("type").orEmpty()
            throw fail(
                when {
                    error.int("code") == 4 || "quota" in type.lowercase() -> ExcorCatalogError.RATE_LIMITED
                    type == "DataException" -> ExcorCatalogError.NOT_FOUND
                    type == "OAuthException" -> if (authorized) ExcorCatalogError.INVALID_CREDENTIAL else ExcorCatalogError.AUTHENTICATION_REQUIRED
                    type == "ParameterException" || type == "MissingParameterException" -> ExcorCatalogError.INVALID_REQUEST
                    else -> ExcorCatalogError.PROVIDER_UNAVAILABLE
                },
            )
        }
        return body
    }

    private suspend fun list(path: String, parameters: List<Pair<String, String>>, offset: Int, limit: Int, authorized: Boolean = false): Pair<List<JsonObject>, Int?> {
        val body = get(path, parameters + listOf("index" to offset.toString(), "limit" to limit.toString()), authorized)
        val data = body.arr("data") ?: throw fail(ExcorCatalogError.MALFORMED_RESPONSE)
        return data.objects() to body.int("total")
    }

    private suspend fun tracks(path: String, page: String?, authorized: Boolean = false): ExcorMusicPage<ExcorMusicTrack> {
        val offset = offsetOf(path, page)
        val (items, total) = list(path, emptyList(), offset, PAGE, authorized)
        return ExcorMusicPage(items.mapNotNull(::trackOf), nextOf(path, offset, items.size, total), total)
    }

    private fun nextOf(scope: String, offset: Int, count: Int, total: Int?): String? {
        val next = offset + count
        val more = if (total != null) next < total else count == PAGE
        return if (count > 0 && more && next <= MAX_OFFSET) pageToken(scope, next) else null
    }

    private fun checked(id: String, allowZero: Boolean = false): String =
        id.takeIf { ID.matches(it) || (allowZero && it == "0") } ?: throw fail(ExcorCatalogError.INVALID_URL)

    // --- parsing ---

    private fun trackOf(o: JsonObject): ExcorMusicTrack? {
        if (o.str("type") != null && o.str("type") != "track") return null
        val id = o.long("id")?.takeIf { it > 0 }?.toString() ?: return null
        val title = o.str("title") ?: return null
        val album = o.obj("album")
        val contributors = o.arr("contributors")?.objects().orEmpty().mapNotNull { c ->
            val name = c.str("name") ?: return@mapNotNull null
            val role = when (c.str("role")) { "Main" -> ExcorArtistRole.PRIMARY; "Featured" -> ExcorArtistRole.FEATURED; else -> ExcorArtistRole.OTHER }
            ExcorMusicArtist(provider, c.long("id")?.toString(), name, role, linkOf(c))
        }
        val artists = contributors.ifEmpty { listOfNotNull(o.obj("artist")?.let(::creditOf)) }
        val countries = o.arr("available_countries")?.let { o.strings("available_countries").toSet() }
        val readable = o.bool("readable")
        val availability = when {
            readable == true -> ExcorAvailability.AVAILABLE
            readable == false && countries?.isNotEmpty() == true -> ExcorAvailability.REGION_RESTRICTED
            readable == false -> ExcorAvailability.UNAVAILABLE
            else -> ExcorAvailability.UNKNOWN
        }
        val track = ExcorMusicTrack(
            provider = provider,
            id = id,
            title = o.str("title_short") ?: title,
            url = linkOf(o),
            displayTitle = title,
            version = o.str("title_version"),
            artists = artists,
            albumId = album?.long("id")?.toString(),
            albumTitle = album?.str("title"),
            isrc = o.str("isrc"),
            trackNumber = o.int("track_position"),
            discNumber = o.int("disk_number"),
            durationMs = o.long("duration")?.times(1_000),
            releaseDate = o.str("release_date")?.takeUnless { it.startsWith("0000") },
            explicit = o.bool("explicit_lyrics"),
            popularity = o.long("rank"),
            artwork = album?.let { pictures(it, "cover") }.orEmpty(),
            markets = countries,
            availability = availability,
            previewAvailable = (o["preview"] as? kotlinx.serialization.json.JsonPrimitive)?.let { it.content.isNotBlank() },
        )
        // Lists and searches carry a summary; only the track endpoint (with contributors) says what is really absent.
        val full = o["contributors"] != null
        return track.withMissing(TRACK_OFFERED, elsewhere = if (full) TRACK_ON_ALBUM else TRACK_OFFERED)
    }

    private fun albumOf(o: JsonObject): ExcorMusicAlbum? {
        if (o.str("type") != null && o.str("type") != "album") return null
        val id = o.long("id")?.takeIf { it > 0 }?.toString() ?: return null
        val title = o.str("title") ?: return null
        val artists = o.arr("contributors")?.objects().orEmpty().mapNotNull(::creditOf)
            .ifEmpty { listOfNotNull(o.obj("artist")?.let(::creditOf)) }
        val tracks = o.obj("tracks")?.arr("data")?.objects()?.mapNotNull(::trackOf)
        val album = ExcorMusicAlbum(
            provider = provider,
            id = id,
            title = title,
            url = linkOf(o),
            type = when (o.str("record_type")) {
                "album" -> ExcorAlbumType.ALBUM; "single" -> ExcorAlbumType.SINGLE; "ep" -> ExcorAlbumType.EP
                "compile", "compilation" -> ExcorAlbumType.COMPILATION; else -> ExcorAlbumType.UNKNOWN
            },
            artists = artists,
            upc = o.str("upc"),
            releaseDate = o.str("release_date")?.takeUnless { it.startsWith("0000") },
            label = o.str("label"),
            genres = o.obj("genres")?.arr("data")?.objects().orEmpty().mapNotNull { it.str("name") },
            explicit = o.bool("explicit_lyrics"),
            trackCount = o.int("nb_tracks"),
            popularity = o.long("fans"),
            artwork = pictures(o, "cover"),
            tracks = tracks?.let { ExcorMusicPage(it, if (o.int("nb_tracks")?.let { n -> n > it.size } == true) pageToken("album/$id/tracks", it.size) else null, o.int("nb_tracks")) },
        )
        val present = buildSet {
            if (album.upc != null) add(ExcorMusicField.UPC); if (album.label != null) add(ExcorMusicField.LABEL)
            if (album.genres.isNotEmpty()) add(ExcorMusicField.GENRES); if (album.releaseDate != null) add(ExcorMusicField.RELEASE_DATE)
            if (album.artwork.isNotEmpty()) add(ExcorMusicField.ARTWORK); if (album.explicit != null) add(ExcorMusicField.EXPLICIT)
        }
        val applicable = setOf(ExcorMusicField.UPC, ExcorMusicField.LABEL, ExcorMusicField.GENRES, ExcorMusicField.RELEASE_DATE, ExcorMusicField.ARTWORK, ExcorMusicField.EXPLICIT, ExcorMusicField.COPYRIGHT)
        // Search results carry only a summary; the album endpoint has the rest.
        val summary = o["upc"] == null
        return album.copy(missing = missingFields(applicable, applicable - ExcorMusicField.COPYRIGHT, present, elsewhere = if (summary) applicable - ExcorMusicField.COPYRIGHT else emptySet()))
    }

    private fun artistOf(o: JsonObject): ExcorMusicArtistProfile? {
        if (o.str("type") != null && o.str("type") != "artist") return null
        val id = o.long("id")?.takeIf { it > 0 }?.toString() ?: return null
        return ExcorMusicArtistProfile(
            provider = provider,
            id = id,
            name = o.str("name") ?: return null,
            url = linkOf(o),
            followers = o.long("nb_fan"),
            albumCount = o.int("nb_album"),
            artwork = pictures(o, "picture"),
            missing = mapOf(ExcorMusicField.GENRES to ExcorFieldStatus.UNSUPPORTED, ExcorMusicField.POPULARITY to ExcorFieldStatus.UNSUPPORTED),
        )
    }

    private fun playlistOf(o: JsonObject): ExcorMusicPlaylist? {
        if (o.str("type") != null && o.str("type") != "playlist") return null
        val id = o.long("id")?.takeIf { it > 0 }?.toString() ?: return null
        val owner = o.obj("creator") ?: o.obj("user")
        val tracks = o.obj("tracks")?.arr("data")?.objects()?.mapNotNull(::trackOf)
        val count = o.int("nb_tracks")
        return ExcorMusicPlaylist(
            provider = provider,
            id = id,
            title = o.str("title") ?: return null,
            url = linkOf(o),
            description = o.str("description"),
            ownerName = owner?.str("name"),
            ownerId = owner?.long("id")?.toString(),
            collaborative = o.bool("collaborative"),
            public = o.bool("public"),
            snapshot = o.str("checksum"),
            trackCount = count,
            followers = o.long("fans"),
            artwork = pictures(o, "picture"),
            tracks = tracks?.let { ExcorMusicPage(it, if (count != null && count > it.size) pageToken("playlist/$id/tracks", it.size) else null, count) },
        )
    }

    private fun creditOf(o: JsonObject): ExcorMusicArtist? {
        val name = o.str("name") ?: return null
        val role = if (o.str("role") == "Featured") ExcorArtistRole.FEATURED else ExcorArtistRole.PRIMARY
        return ExcorMusicArtist(provider, o.long("id")?.toString(), name, role, linkOf(o))
    }

    private fun linkOf(o: JsonObject): String? = o.str("link")?.takeIf { httpsUrlOn(it, HOSTS) != null }

    private fun pictures(o: JsonObject, prefix: String): List<ExcorArtwork> = listOf("small" to 56, "medium" to 250, "big" to 500, "xl" to 1000)
        .mapNotNull { (size, px) -> artworkOf(o.str("${prefix}_$size"), listOf(".dzcdn.net"), px, px) }

    companion object {
        const val PAGE = 50
        val HOSTS = setOf("www.deezer.com", "deezer.com")
        val ID = Regex("[1-9][0-9]{0,18}")
        val LANGUAGE = Regex("[a-z]{2}(-[a-z]{2})?")
        val ISRC = Regex("[A-Z]{2}[A-Z0-9]{3}[0-9]{7}")
        val UPC = Regex("[0-9]{12,14}")
        val TRACK_OFFERED = setOf(
            ExcorMusicField.ARTISTS, ExcorMusicField.ALBUM, ExcorMusicField.ISRC, ExcorMusicField.TRACK_NUMBER,
            ExcorMusicField.DISC_NUMBER, ExcorMusicField.DURATION, ExcorMusicField.RELEASE_DATE, ExcorMusicField.EXPLICIT,
            ExcorMusicField.POPULARITY, ExcorMusicField.ARTWORK, ExcorMusicField.MARKETS, ExcorMusicField.PREVIEW,
            ExcorMusicField.URL, ExcorMusicField.GENRES, ExcorMusicField.LABEL, ExcorMusicField.ALBUM_ARTISTS,
        )
        // On the album, one more request away; see ExcorMusic.metadata.
        val TRACK_ON_ALBUM = setOf(ExcorMusicField.GENRES, ExcorMusicField.LABEL, ExcorMusicField.ALBUM_ARTISTS)
    }
}

data class ExcorDeezerGenre(val id: String, val name: String, val artwork: List<ExcorArtwork>)

data class ExcorDeezerRadio(val id: String, val title: String, val description: String?, val artwork: List<ExcorArtwork>)
