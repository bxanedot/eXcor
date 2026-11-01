package com.excor.music

import com.excor.catalog.ExcorArtwork
import com.excor.catalog.ExcorCatalogCapabilityStatus
import com.excor.catalog.ExcorCatalogError
import com.excor.catalog.ExcorCatalogProvider
import io.ktor.http.URLBuilder
import kotlinx.serialization.json.JsonObject

/**
 * Apple Music. With a developer token (a JWT the host signs on its server) it reads the Apple Music API: songs with
 * ISRC, composers, audio variants and lyrics availability, albums with UPC and label, playlists, charts, and, with a
 * Music-User-Token from MusicKit, the user's library. Without one it falls back to the keyless iTunes Search API,
 * which has no ISRC, playlists or audio variants; those fields are then RESTRICTED. Preview audio is never returned.
 */
internal class AppleMusicSource(
    private val http: MusicHttp,
    private val developerToken: suspend () -> String?,
    private val userToken: suspend () -> String?,
    private val storefront: String,
) : MusicSource(ExcorCatalogProvider.APPLE_MUSIC) {

    init {
        require(STOREFRONT.matches(storefront)) { "storefront must be an ISO 3166-1 alpha-2 code in lower case" }
    }

    override suspend fun capabilities(): Map<ExcorMusicCapability, ExcorCatalogCapabilityStatus> {
        val api = developerToken() != null
        val user = api && userToken() != null
        val keyless = setOf(
            ExcorMusicCapability.SEARCH, ExcorMusicCapability.TRACK, ExcorMusicCapability.ALBUM, ExcorMusicCapability.ARTIST,
            ExcorMusicCapability.ALBUM_TRACKS, ExcorMusicCapability.ARTIST_ALBUMS,
        )
        val withToken = setOf(
            ExcorMusicCapability.PLAYLIST, ExcorMusicCapability.PLAYLIST_TRACKS, ExcorMusicCapability.ISRC_LOOKUP,
            ExcorMusicCapability.UPC_LOOKUP, ExcorMusicCapability.CHARTS, ExcorMusicCapability.AUDIO_TRAITS,
            ExcorMusicCapability.LYRICS_AVAILABILITY, ExcorMusicCapability.PREVIEW_AVAILABILITY,
        )
        return ExcorMusicCapability.entries.associateWith {
            when (it) {
                in keyless -> ExcorCatalogCapabilityStatus.SUPPORTED
                in withToken -> if (api) ExcorCatalogCapabilityStatus.SUPPORTED else ExcorCatalogCapabilityStatus.AUTHENTICATION_REQUIRED
                ExcorMusicCapability.LIBRARY -> if (user) ExcorCatalogCapabilityStatus.SUPPORTED else ExcorCatalogCapabilityStatus.AUTHENTICATION_REQUIRED
                ExcorMusicCapability.PROTECTED_PLAYBACK -> ExcorCatalogCapabilityStatus.RESTRICTED
                else -> ExcorCatalogCapabilityStatus.UNSUPPORTED
            }
        }
    }

    override fun parse(url: String): ExcorMusicRef? {
        val parsed = httpsUrlOn(url, setOf("music.apple.com")) ?: return null
        val segments = parsed.encodedPath.split('/').filter(String::isNotEmpty)
        if (segments.size !in 3..4 || !STOREFRONT.matches(segments[0])) return null
        val id = segments.last()
        val song = parsed.parameters["i"]
        return when (segments[1]) {
            "song" -> ExcorMusicRef(provider, ExcorMusicKind.TRACK, id.takeIf(CATALOG_ID::matches) ?: return null)
            "album" -> if (song != null) {
                ExcorMusicRef(provider, ExcorMusicKind.TRACK, song.takeIf(CATALOG_ID::matches) ?: return null)
            } else {
                ExcorMusicRef(provider, ExcorMusicKind.ALBUM, id.takeIf(CATALOG_ID::matches) ?: return null)
            }
            "artist" -> ExcorMusicRef(provider, ExcorMusicKind.ARTIST, id.takeIf(CATALOG_ID::matches) ?: return null)
            "playlist" -> ExcorMusicRef(provider, ExcorMusicKind.PLAYLIST, id.takeIf(PLAYLIST_ID::matches) ?: return null)
            else -> null
        }
    }

    override fun protection(track: ExcorMusicTrack) =
        ExcorProtectionInfo(true, setOf(ExcorDrmSystem.PROVIDER_MANAGED), source = ExcorProtectionSource.PROVIDER_STATEMENT)

    override suspend fun search(query: String, kinds: Set<ExcorMusicKind>, limit: Int, page: String?): ExcorMusicSearchResult {
        val q = query(query)
        if (developerToken() == null) return itunesSearch(q, kinds, limit, page)
        if (limit !in 1..25 || kinds.isEmpty()) throw fail(ExcorCatalogError.INVALID_QUERY)
        val types = kinds.mapNotNull { API_TYPES[it] }.sorted()
        if (types.isEmpty()) throw fail(ExcorCatalogError.UNSUPPORTED)
        val scope = "search|$types|$q|$limit"
        val offset = offsetOf(scope, page)
        val results = api("catalog/$storefront/search", listOf("term" to q, "types" to types.joinToString(","), "limit" to "$limit", "offset" to "$offset", "extend" to "audioVariants")).obj("results")
        fun <T> pageOf(key: String, parse: (JsonObject) -> T?): ExcorMusicPage<T>? {
            val p = results?.obj(key) ?: return if (key in types) ExcorMusicPage(emptyList()) else null
            val items = p.arr("data")?.objects().orEmpty().mapNotNull(parse)
            return ExcorMusicPage(items, if (p.str("next") != null && items.isNotEmpty()) pageToken(scope, offset + limit) else null)
        }
        return ExcorMusicSearchResult(pageOf("songs", ::songOf), pageOf("albums", ::albumOf), pageOf("artists", ::artistOf), pageOf("playlists", ::playlistOf))
    }

    override suspend fun track(id: String): ExcorMusicTrack {
        val catalogId = id.takeIf(CATALOG_ID::matches) ?: throw fail(ExcorCatalogError.INVALID_URL)
        if (developerToken() == null) {
            return itunesLookup(listOf("id" to catalogId)).firstNotNullOfOrNull(::itunesTrackOf) ?: throw fail(ExcorCatalogError.NOT_FOUND)
        }
        return first(api("catalog/$storefront/songs/$catalogId", listOf("include" to "albums,artists", "extend" to "audioVariants")), ::songOf)
    }

    override suspend fun album(id: String): ExcorMusicAlbum {
        val catalogId = id.takeIf(CATALOG_ID::matches) ?: throw fail(ExcorCatalogError.INVALID_URL)
        if (developerToken() == null) {
            val results = itunesLookup(listOf("id" to catalogId, "entity" to "song", "limit" to "200"))
            val album = results.firstNotNullOfOrNull(::itunesAlbumOf) ?: throw fail(ExcorCatalogError.NOT_FOUND)
            val tracks = results.mapNotNull(::itunesTrackOf).filter { it.albumId == catalogId }
            return album.copy(tracks = ExcorMusicPage(tracks, total = album.trackCount))
        }
        return first(api("catalog/$storefront/albums/$catalogId", listOf("include" to "artists", "extend" to "audioVariants")), ::albumOf)
    }

    override suspend fun artist(id: String): ExcorMusicArtistProfile {
        val catalogId = id.takeIf(CATALOG_ID::matches) ?: throw fail(ExcorCatalogError.INVALID_URL)
        if (developerToken() == null) {
            return itunesLookup(listOf("id" to catalogId)).firstNotNullOfOrNull(::itunesArtistOf) ?: throw fail(ExcorCatalogError.NOT_FOUND)
        }
        return first(api("catalog/$storefront/artists/$catalogId"), ::artistOf)
    }

    override suspend fun playlist(id: String): ExcorMusicPlaylist {
        val playlistId = id.takeIf(PLAYLIST_ID::matches) ?: throw fail(ExcorCatalogError.INVALID_URL)
        requireApi()
        return first(api("catalog/$storefront/playlists/$playlistId", listOf("extend" to "audioVariants")), ::playlistOf)
    }

    override suspend fun albumTracks(id: String, page: String?): ExcorMusicPage<ExcorMusicTrack> {
        val catalogId = id.takeIf(CATALOG_ID::matches) ?: throw fail(ExcorCatalogError.INVALID_URL)
        if (developerToken() == null) {
            if (page != null) throw fail(ExcorCatalogError.INVALID_PAGE_TOKEN)
            return album(catalogId).tracks ?: ExcorMusicPage(emptyList())
        }
        return paged("catalog/$storefront/albums/$catalogId/tracks", page, 100, ::songOf)
    }

    override suspend fun artistAlbums(id: String, page: String?): ExcorMusicPage<ExcorMusicAlbum> {
        val catalogId = id.takeIf(CATALOG_ID::matches) ?: throw fail(ExcorCatalogError.INVALID_URL)
        if (developerToken() == null) {
            if (page != null) throw fail(ExcorCatalogError.INVALID_PAGE_TOKEN)
            val albums = itunesLookup(listOf("id" to catalogId, "entity" to "album", "limit" to "200")).mapNotNull(::itunesAlbumOf)
            return ExcorMusicPage(albums)
        }
        return paged("catalog/$storefront/artists/$catalogId/albums", page, 25, ::albumOf)
    }

    override suspend fun playlistTracks(id: String, page: String?): ExcorMusicPage<ExcorMusicTrack> {
        val playlistId = id.takeIf(PLAYLIST_ID::matches) ?: throw fail(ExcorCatalogError.INVALID_URL)
        requireApi()
        return paged("catalog/$storefront/playlists/$playlistId/tracks", page, 100, ::songOf)
    }

    override suspend fun tracksByIsrc(isrc: String): List<ExcorMusicTrack> {
        val code = isrc.trim().uppercase().takeIf(DeezerSource.ISRC::matches) ?: throw fail(ExcorCatalogError.INVALID_QUERY)
        requireApi()
        return api("catalog/$storefront/songs", listOf("filter[isrc]" to code, "extend" to "audioVariants")).arr("data")?.objects().orEmpty().mapNotNull(::songOf)
    }

    suspend fun albumByUpc(upc: String): ExcorMusicAlbum? {
        val code = upc.trim().takeIf(DeezerSource.UPC::matches) ?: throw fail(ExcorCatalogError.INVALID_QUERY)
        requireApi()
        return api("catalog/$storefront/albums", listOf("filter[upc]" to code)).arr("data")?.objects()?.firstNotNullOfOrNull(::albumOf)
    }

    override suspend fun libraryTracks(page: String?) = paged("me/library/songs", page, 100, ::songOf, user = true)
    override suspend fun libraryPlaylists(page: String?) = paged("me/library/playlists", page, 100, ::playlistOf, user = true)

    suspend fun recentlyPlayed(): List<ExcorMusicTrack> =
        api("me/recent/played/tracks", listOf("types" to "songs", "limit" to "30"), user = true).arr("data")?.objects().orEmpty().mapNotNull(::songOf)

    /** Top songs in this storefront, optionally for one genre ID. */
    suspend fun chartSongs(genre: String? = null, limit: Int = 25): List<ExcorMusicTrack> {
        requireApi()
        if (limit !in 1..50 || (genre != null && !CATALOG_ID.matches(genre))) throw fail(ExcorCatalogError.INVALID_QUERY)
        val body = api("catalog/$storefront/charts", listOfNotNull("types" to "songs", "limit" to "$limit", genre?.let { "genre" to it }))
        return body.obj("results")?.arr("songs")?.objects().orEmpty().flatMap { it.arr("data")?.objects().orEmpty() }.mapNotNull(::songOf)
    }

    // --- Apple Music API ---

    private suspend fun requireApi() {
        if (developerToken() == null) throw fail(ExcorCatalogError.AUTHENTICATION_REQUIRED)
    }

    private suspend fun api(path: String, parameters: List<Pair<String, String>> = emptyList(), user: Boolean = false): JsonObject {
        val developer = developerToken() ?: throw fail(ExcorCatalogError.AUTHENTICATION_REQUIRED)
        val headers = if (user) mapOf("Music-User-Token" to (userToken() ?: throw fail(ExcorCatalogError.AUTHENTICATION_REQUIRED))) else emptyMap()
        val url = URLBuilder("https://api.music.apple.com/v1/$path").apply { parameters.forEach { (k, v) -> this.parameters.append(k, v) } }.buildString()
        return http.getJson(provider, url, bearer = developer, headers = headers).asObject(provider)
    }

    private fun <T> first(body: JsonObject, parse: (JsonObject) -> T?): T =
        body.arr("data")?.objects()?.firstNotNullOfOrNull(parse) ?: throw fail(ExcorCatalogError.NOT_FOUND)

    private suspend fun <T> paged(path: String, page: String?, limit: Int, parse: (JsonObject) -> T?, user: Boolean = false): ExcorMusicPage<T> {
        val offset = offsetOf(path, page)
        val body = api(path, listOf("limit" to "$limit", "offset" to "$offset", "extend" to "audioVariants"), user)
        val items = body.arr("data")?.objects() ?: throw fail(ExcorCatalogError.MALFORMED_RESPONSE)
        val next = body.str("next")?.let { Regex("[?&]offset=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?.takeIf { it > offset && it <= MAX_OFFSET }?.let { pageToken(path, it) }
        return ExcorMusicPage(items.mapNotNull(parse), next, body.obj("meta")?.int("total"))
    }

    private fun songOf(o: JsonObject): ExcorMusicTrack? {
        val type = o.str("type")
        if (type != "songs" && type != "library-songs") return null
        val id = o.str("id") ?: return null
        val a = o.obj("attributes") ?: return null
        val relations = o.obj("relationships")
        val artistData = relations?.obj("artists")?.arr("data")?.objects().orEmpty()
        val artists = artistData.mapNotNull { r -> r.obj("attributes")?.str("name")?.let { ExcorMusicArtist(provider, r.str("id"), it) } }
            .ifEmpty { listOfNotNull(a.str("artistName")?.let { ExcorMusicArtist(provider, artistData.singleOrNull()?.str("id"), it) }) }
        val traits = traitsOf(a.strings("audioVariants"))
        val track = ExcorMusicTrack(
            provider = provider,
            id = id,
            title = a.str("name") ?: return null,
            url = a.str("url")?.takeIf { httpsUrlOn(it, setOf("music.apple.com")) != null },
            artists = artists,
            albumId = relations?.obj("albums")?.arr("data")?.objects()?.firstOrNull()?.str("id"),
            albumTitle = a.str("albumName"),
            isrc = a.str("isrc"),
            trackNumber = a.int("trackNumber"),
            discNumber = a.int("discNumber"),
            durationMs = a.long("durationInMillis"),
            releaseDate = a.str("releaseDate"),
            genres = a.strings("genreNames").filter { it != "Music" },
            composers = listOfNotNull(a.str("composerName")),
            language = a.str("audioLocale"),
            explicit = when (a.str("contentRating")) { "explicit" -> true; "clean" -> false; else -> null },
            artwork = artworkOfTemplate(a.obj("artwork")),
            availability = if (a.obj("playParams") != null) ExcorAvailability.AVAILABLE else ExcorAvailability.UNAVAILABLE,
            audioTraits = traits,
            audioFormats = formatsOf(traits),
            // Only whether previews exist; their URLs are never read.
            previewAvailable = a.arr("previews")?.isNotEmpty(),
            hasLyrics = a.bool("hasLyrics"),
        )
        val audioUnknown = if (a.containsKey("audioVariants")) emptySet() else setOf(ExcorMusicField.AUDIO_TRAITS)
        return track.withMissing(API_TRACK_OFFERED, elsewhere = audioUnknown + setOf(ExcorMusicField.LABEL, ExcorMusicField.COPYRIGHT, ExcorMusicField.ALBUM_ARTISTS))
    }

    private fun albumOf(o: JsonObject): ExcorMusicAlbum? {
        val type = o.str("type")
        if (type != "albums" && type != "library-albums") return null
        val id = o.str("id") ?: return null
        val a = o.obj("attributes") ?: return null
        val name = a.str("name") ?: return null
        val tracks = o.obj("relationships")?.obj("tracks")
        val traits = traitsOf(a.strings("audioVariants"))
        return ExcorMusicAlbum(
            provider = provider,
            id = id,
            title = name,
            url = a.str("url")?.takeIf { httpsUrlOn(it, setOf("music.apple.com")) != null },
            type = when {
                a.bool("isCompilation") == true -> ExcorAlbumType.COMPILATION
                a.bool("isSingle") == true -> ExcorAlbumType.SINGLE
                name.endsWith(" - EP") -> ExcorAlbumType.EP
                a.containsKey("isSingle") -> ExcorAlbumType.ALBUM
                else -> ExcorAlbumType.UNKNOWN
            },
            artists = listOfNotNull(a.str("artistName")?.let { ExcorMusicArtist(provider, null, it) }),
            upc = a.str("upc"),
            releaseDate = a.str("releaseDate"),
            label = a.str("recordLabel"),
            copyright = a.str("copyright"),
            genres = a.strings("genreNames").filter { it != "Music" },
            explicit = when (a.str("contentRating")) { "explicit" -> true; "clean" -> false; else -> null },
            trackCount = a.int("trackCount"),
            artwork = artworkOfTemplate(a.obj("artwork")),
            audioTraits = traits,
            tracks = tracks?.let { t ->
                val items = t.arr("data")?.objects().orEmpty().mapNotNull(::songOf)
                ExcorMusicPage(items, if (t.str("next") != null) pageToken("catalog/$storefront/albums/$id/tracks", items.size) else null)
            },
        )
    }

    private fun artistOf(o: JsonObject): ExcorMusicArtistProfile? {
        val type = o.str("type")
        if (type != "artists" && type != "library-artists") return null
        val a = o.obj("attributes") ?: return null
        return ExcorMusicArtistProfile(
            provider = provider,
            id = o.str("id") ?: return null,
            name = a.str("name") ?: return null,
            url = a.str("url")?.takeIf { httpsUrlOn(it, setOf("music.apple.com")) != null },
            genres = a.strings("genreNames").filter { it != "Music" },
            artwork = artworkOfTemplate(a.obj("artwork")),
            missing = mapOf(ExcorMusicField.FOLLOWERS to ExcorFieldStatus.UNSUPPORTED, ExcorMusicField.POPULARITY to ExcorFieldStatus.UNSUPPORTED),
        )
    }

    private fun playlistOf(o: JsonObject): ExcorMusicPlaylist? {
        val type = o.str("type")
        if (type != "playlists" && type != "library-playlists") return null
        val id = o.str("id") ?: return null
        val a = o.obj("attributes") ?: return null
        val tracks = o.obj("relationships")?.obj("tracks")
        return ExcorMusicPlaylist(
            provider = provider,
            id = id,
            title = a.str("name") ?: return null,
            url = a.str("url")?.takeIf { httpsUrlOn(it, setOf("music.apple.com")) != null },
            description = a.obj("description")?.let { it.str("standard") ?: it.str("short") },
            ownerName = a.str("curatorName"),
            public = a.bool("isPublic"),
            snapshot = a.str("lastModifiedDate") ?: a.str("dateAdded"),
            artwork = artworkOfTemplate(a.obj("artwork")),
            tracks = tracks?.let { t ->
                val items = t.arr("data")?.objects().orEmpty().mapNotNull(::songOf)
                ExcorMusicPage(items, if (t.str("next") != null) pageToken("catalog/$storefront/playlists/$id/tracks", items.size) else null)
            },
            missing = mapOf(ExcorMusicField.FOLLOWERS to ExcorFieldStatus.UNSUPPORTED),
        )
    }

    /** Apple's artwork URL is a template with `{w}` and `{h}`; offered at a few sizes up to the original. */
    private fun artworkOfTemplate(o: JsonObject?): List<ExcorArtwork> {
        val template = o?.str("url") ?: return emptyList()
        val width = o.int("width") ?: return emptyList()
        val height = o.int("height") ?: return emptyList()
        return (listOf(300, 600, 1200).filter { it < width } + width).mapNotNull { w ->
            val h = (height.toLong() * w / width).toInt()
            artworkOf(template.replace("{w}", "$w").replace("{h}", "$h").replace("{f}", "jpg"), listOf(".mzstatic.com"), w, h)
        }
    }

    // --- iTunes Search API fallback ---

    private suspend fun itunesLookup(parameters: List<Pair<String, String>>): List<JsonObject> {
        val url = URLBuilder("https://itunes.apple.com/lookup").apply {
            parameters.forEach { (k, v) -> this.parameters.append(k, v) }
            this.parameters.append("country", storefront)
        }.buildString()
        return http.getJson(provider, url).asObject(provider).arr("results")?.objects() ?: throw fail(ExcorCatalogError.MALFORMED_RESPONSE)
    }

    private suspend fun itunesSearch(q: String, kinds: Set<ExcorMusicKind>, limit: Int, page: String?): ExcorMusicSearchResult {
        if (limit !in 1..200) throw fail(ExcorCatalogError.INVALID_QUERY)
        val scope = "itunes|${kinds.sorted()}|$q|$limit"
        val offset = offsetOf(scope, page)
        suspend fun run(entity: String): List<JsonObject> {
            val url = URLBuilder("https://itunes.apple.com/search").apply {
                listOf("term" to q, "media" to "music", "entity" to entity, "limit" to "$limit", "offset" to "$offset", "country" to storefront)
                    .forEach { (k, v) -> parameters.append(k, v) }
            }.buildString()
            return http.getJson(provider, url).asObject(provider).arr("results")?.objects().orEmpty()
        }
        fun <T> pageOf(items: List<T>) = ExcorMusicPage(items, if (items.size >= limit) pageToken(scope, offset + limit) else null)
        return ExcorMusicSearchResult(
            tracks = if (ExcorMusicKind.TRACK in kinds) pageOf(run("song").mapNotNull(::itunesTrackOf)) else null,
            albums = if (ExcorMusicKind.ALBUM in kinds) pageOf(run("album").mapNotNull(::itunesAlbumOf)) else null,
            artists = if (ExcorMusicKind.ARTIST in kinds) pageOf(run("musicArtist").mapNotNull(::itunesArtistOf)) else null,
            playlists = if (ExcorMusicKind.PLAYLIST in kinds) throw fail(ExcorCatalogError.AUTHENTICATION_REQUIRED) else null,
        )
    }

    private fun itunesTrackOf(o: JsonObject): ExcorMusicTrack? {
        if (o.str("wrapperType") != "track" || o.str("kind") != "song") return null
        val track = ExcorMusicTrack(
            provider = provider,
            id = o.long("trackId")?.toString() ?: return null,
            title = o.str("trackName") ?: return null,
            url = o.str("trackViewUrl")?.takeIf { httpsUrlOn(it, setOf("music.apple.com")) != null }?.substringBefore("&uo="),
            artists = listOfNotNull(o.str("artistName")?.let { ExcorMusicArtist(provider, o.long("artistId")?.toString(), it) }),
            albumId = o.long("collectionId")?.toString(),
            albumTitle = o.str("collectionName"),
            trackNumber = o.int("trackNumber"),
            discNumber = o.int("discNumber"),
            durationMs = o.long("trackTimeMillis"),
            releaseDate = o.str("releaseDate")?.substringBefore('T'),
            genres = listOfNotNull(o.str("primaryGenreName")),
            explicit = when (o.str("trackExplicitness")) { "explicit" -> true; "cleaned", "notExplicit" -> false; else -> null },
            artwork = itunesArtwork(o),
            availability = when (o.bool("isStreamable")) { true -> ExcorAvailability.AVAILABLE; false -> ExcorAvailability.UNAVAILABLE; null -> ExcorAvailability.UNKNOWN },
        )
        return track.withMissing(ITUNES_TRACK_OFFERED, restricted = API_TRACK_OFFERED - ITUNES_TRACK_OFFERED)
    }

    private fun itunesAlbumOf(o: JsonObject): ExcorMusicAlbum? {
        if (o.str("wrapperType") != "collection") return null
        return ExcorMusicAlbum(
            provider = provider,
            id = o.long("collectionId")?.toString() ?: return null,
            title = o.str("collectionName") ?: return null,
            url = o.str("collectionViewUrl")?.takeIf { httpsUrlOn(it, setOf("music.apple.com")) != null }?.substringBefore("?uo="),
            artists = listOfNotNull(o.str("artistName")?.let { ExcorMusicArtist(provider, o.long("artistId")?.toString(), it) }),
            releaseDate = o.str("releaseDate")?.substringBefore('T'),
            copyright = o.str("copyright"),
            genres = listOfNotNull(o.str("primaryGenreName")),
            explicit = when (o.str("collectionExplicitness")) { "explicit" -> true; "cleaned", "notExplicit" -> false; else -> null },
            trackCount = o.int("trackCount"),
            artwork = itunesArtwork(o),
            missing = mapOf(ExcorMusicField.UPC to ExcorFieldStatus.RESTRICTED, ExcorMusicField.LABEL to ExcorFieldStatus.RESTRICTED, ExcorMusicField.AUDIO_TRAITS to ExcorFieldStatus.RESTRICTED),
        )
    }

    private fun itunesArtistOf(o: JsonObject): ExcorMusicArtistProfile? {
        if (o.str("wrapperType") != "artist") return null
        return ExcorMusicArtistProfile(
            provider = provider,
            id = o.long("artistId")?.toString() ?: return null,
            name = o.str("artistName") ?: return null,
            url = o.str("artistLinkUrl")?.takeIf { httpsUrlOn(it, setOf("music.apple.com")) != null }?.substringBefore("?uo="),
            genres = listOfNotNull(o.str("primaryGenreName")),
            missing = mapOf(ExcorMusicField.ARTWORK to ExcorFieldStatus.RESTRICTED),
        )
    }

    private fun itunesArtwork(o: JsonObject): List<ExcorArtwork> = listOf(30, 60, 100).mapNotNull { px ->
        artworkOf(o.str("artworkUrl$px"), listOf(".mzstatic.com"), px, px)
    }

    companion object {
        val STOREFRONT = Regex("[a-z]{2}")
        val CATALOG_ID = Regex("[1-9][0-9]{0,14}")
        val PLAYLIST_ID = Regex("pl\\.[A-Za-z0-9-]{1,64}")
        val API_TYPES = mapOf(
            ExcorMusicKind.TRACK to "songs", ExcorMusicKind.ALBUM to "albums",
            ExcorMusicKind.ARTIST to "artists", ExcorMusicKind.PLAYLIST to "playlists",
        )
        val API_TRACK_OFFERED = setOf(
            ExcorMusicField.ARTISTS, ExcorMusicField.ALBUM, ExcorMusicField.ISRC, ExcorMusicField.TRACK_NUMBER,
            ExcorMusicField.DISC_NUMBER, ExcorMusicField.DURATION, ExcorMusicField.RELEASE_DATE, ExcorMusicField.GENRES,
            ExcorMusicField.COMPOSERS, ExcorMusicField.LANGUAGE, ExcorMusicField.EXPLICIT, ExcorMusicField.ARTWORK,
            ExcorMusicField.AUDIO_TRAITS, ExcorMusicField.PREVIEW, ExcorMusicField.LYRICS, ExcorMusicField.URL,
            ExcorMusicField.LABEL, ExcorMusicField.COPYRIGHT, ExcorMusicField.ALBUM_ARTISTS,
        )
        val ITUNES_TRACK_OFFERED = setOf(
            ExcorMusicField.ARTISTS, ExcorMusicField.ALBUM, ExcorMusicField.TRACK_NUMBER, ExcorMusicField.DISC_NUMBER,
            ExcorMusicField.DURATION, ExcorMusicField.RELEASE_DATE, ExcorMusicField.GENRES, ExcorMusicField.EXPLICIT,
            ExcorMusicField.ARTWORK, ExcorMusicField.URL,
        )
    }
}

/** Apple's audio variant names, matched loosely since MusicKit and the REST API spell them differently. */
internal fun traitsOf(variants: List<String>): Set<ExcorAudioTrait> = variants.mapNotNull { raw ->
    when (raw.lowercase().filter(Char::isLetter)) {
        "lossystereo" -> ExcorAudioTrait.LOSSY_STEREO
        "lossless" -> ExcorAudioTrait.LOSSLESS
        "hireslossless", "highresolutionlossless" -> ExcorAudioTrait.HI_RES_LOSSLESS
        "dolbyatmos", "atmos" -> ExcorAudioTrait.DOLBY_ATMOS
        "dolbyaudio" -> ExcorAudioTrait.DOLBY_AUDIO
        "spatial", "spatialaudio" -> ExcorAudioTrait.SPATIAL
        else -> null
    }
}.toSet()

/**
 * Formats Apple documents for a variant: Lossless and Hi-Res Lossless are ALAC (MusicKit's AudioVariant docs give
 * "up to" 24-bit/48 kHz and 24-bit/192 kHz, so exact rates stay unstated). Lossy and Atmos codecs are not named per
 * track, so they give no format.
 */
internal fun formatsOf(traits: Set<ExcorAudioTrait>): List<ExcorAudioFormat> =
    if (ExcorAudioTrait.LOSSLESS in traits || ExcorAudioTrait.HI_RES_LOSSLESS in traits) {
        listOf(ExcorAudioFormat(ExcorAudioCodec.ALAC, ExcorAudioContainer.MP4))
    } else {
        emptyList()
    }
