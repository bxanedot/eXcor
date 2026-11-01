package com.excor.music

import com.excor.catalog.ExcorArtwork
import com.excor.catalog.ExcorCatalogCapabilityStatus
import com.excor.catalog.ExcorCatalogError
import com.excor.catalog.ExcorCatalogException
import com.excor.catalog.ExcorCatalogProvider
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Spotify Web API with a caller-supplied OAuth access token. Since February 2026, Development Mode apps get at most 10
 * search results per request, no popularity, markets or label, and playlist items only for playlists the user owns or
 * collaborates on; [extendedQuota] tells eXcor the app is not under those limits. Fields Spotify withholds are
 * RESTRICTED, not UNAVAILABLE.
 */
internal class SpotifySource(
    private val http: MusicHttp,
    private val token: suspend () -> String?,
    private val market: String?,
    private val extendedQuota: Boolean,
) : MusicSource(ExcorCatalogProvider.SPOTIFY) {

    override suspend fun capabilities(): Map<ExcorMusicCapability, ExcorCatalogCapabilityStatus> {
        val signedIn = token() != null
        val base = if (signedIn) ExcorCatalogCapabilityStatus.SUPPORTED else ExcorCatalogCapabilityStatus.AUTHENTICATION_REQUIRED
        val catalog = setOf(
            ExcorMusicCapability.SEARCH, ExcorMusicCapability.TRACK, ExcorMusicCapability.ALBUM, ExcorMusicCapability.ARTIST,
            ExcorMusicCapability.PLAYLIST, ExcorMusicCapability.ALBUM_TRACKS, ExcorMusicCapability.ARTIST_ALBUMS,
            ExcorMusicCapability.PLAYLIST_TRACKS, ExcorMusicCapability.ISRC_LOOKUP, ExcorMusicCapability.UPC_LOOKUP,
            ExcorMusicCapability.LIBRARY, ExcorMusicCapability.PLAYBACK_STATE,
        )
        return ExcorMusicCapability.entries.associateWith {
            when (it) {
                in catalog -> base
                ExcorMusicCapability.MARKETS -> if (!signedIn) base else if (extendedQuota) ExcorCatalogCapabilityStatus.SUPPORTED else ExcorCatalogCapabilityStatus.RESTRICTED
                // Removed for Development Mode apps in February 2026.
                ExcorMusicCapability.ARTIST_TOP_TRACKS -> if (extendedQuota) base else ExcorCatalogCapabilityStatus.RESTRICTED
                ExcorMusicCapability.PROTECTED_PLAYBACK -> ExcorCatalogCapabilityStatus.RESTRICTED
                else -> ExcorCatalogCapabilityStatus.UNSUPPORTED
            }
        }
    }

    override fun parse(url: String): ExcorMusicRef? = spotifyRefOf(url)

    override fun protection(track: ExcorMusicTrack) =
        ExcorProtectionInfo(true, setOf(ExcorDrmSystem.PROVIDER_MANAGED), source = ExcorProtectionSource.PROVIDER_STATEMENT)

    override suspend fun search(query: String, kinds: Set<ExcorMusicKind>, limit: Int, page: String?): ExcorMusicSearchResult {
        val q = query(query)
        if (limit !in 1..searchLimit || kinds.isEmpty()) throw fail(ExcorCatalogError.INVALID_QUERY)
        val types = kinds.mapNotNull { SEARCH_TYPES[it] }.sorted()
        if (types.isEmpty()) throw fail(ExcorCatalogError.UNSUPPORTED)
        val scope = "search|$types|$q|$limit"
        val offset = offsetOf(scope, page)
        val body = get("search", listOf("q" to q, "type" to types.joinToString(","), "limit" to "$limit", "offset" to "$offset"))
        fun <T> pageOf(key: String, parse: (JsonObject) -> T?): ExcorMusicPage<T>? {
            val p = body.obj(key) ?: return null
            val items = p.arr("items")?.objects().orEmpty().mapNotNull(parse)
            val total = p.int("total")
            val next = if (p.str("next") != null && items.isNotEmpty() && offset + limit <= MAX_OFFSET) pageToken(scope, offset + limit) else null
            return ExcorMusicPage(items, next, total)
        }
        return ExcorMusicSearchResult(
            tracks = pageOf("tracks", ::trackOf),
            albums = pageOf("albums", ::albumOf),
            artists = pageOf("artists", ::artistOf),
            playlists = pageOf("playlists", ::playlistOf),
        )
    }

    override suspend fun track(id: String) = trackOf(get("tracks/${checked(id)}", marketParam())) ?: throw fail(ExcorCatalogError.MALFORMED_RESPONSE)
    override suspend fun album(id: String) = albumOf(get("albums/${checked(id)}", marketParam())) ?: throw fail(ExcorCatalogError.MALFORMED_RESPONSE)
    override suspend fun artist(id: String) = artistOf(get("artists/${checked(id)}")) ?: throw fail(ExcorCatalogError.MALFORMED_RESPONSE)
    override suspend fun playlist(id: String) = playlistOf(get("playlists/${checked(id)}", marketParam())) ?: throw fail(ExcorCatalogError.MALFORMED_RESPONSE)

    override suspend fun albumTracks(id: String, page: String?) = paged("albums/${checked(id)}/tracks", page, marketParam(), ::trackOf)

    override suspend fun artistAlbums(id: String, page: String?) =
        paged("artists/${checked(id)}/albums", page, listOf("include_groups" to "album,single,compilation,appears_on") + marketParam(), ::albumOf)

    override suspend fun playlistTracks(id: String, page: String?): ExcorMusicPage<ExcorMusicTrack> {
        val path = "playlists/${checked(id)}/items"
        return try {
            paged(path, page, marketParam(), ::entryTrackOf)
        } catch (e: ExcorCatalogException) {
            // Extended-quota apps keep the pre-2026 path; Development Mode apps get 403 for playlists they do not own.
            if (e.error != ExcorCatalogError.NOT_FOUND || !extendedQuota) throw e
            paged("playlists/${checked(id)}/tracks", page, marketParam(), ::entryTrackOf, scope = path)
        }
    }

    override suspend fun tracksByIsrc(isrc: String): List<ExcorMusicTrack> {
        val code = isrc.trim().uppercase().takeIf(DeezerSource.ISRC::matches) ?: throw fail(ExcorCatalogError.INVALID_QUERY)
        return search("isrc:$code", setOf(ExcorMusicKind.TRACK), searchLimit, null).tracks?.items.orEmpty()
            .filter { it.isrc.equals(code, ignoreCase = true) || it.isrc == null }
    }

    suspend fun albumByUpc(upc: String): ExcorMusicAlbum? {
        val code = upc.trim().takeIf(DeezerSource.UPC::matches) ?: throw fail(ExcorCatalogError.INVALID_QUERY)
        return search("upc:$code", setOf(ExcorMusicKind.ALBUM), 1, null).albums?.items?.firstOrNull()
    }

    override suspend fun libraryTracks(page: String?) = paged("me/tracks", page, marketParam(), ::entryTrackOf)
    override suspend fun libraryPlaylists(page: String?) = paged("me/playlists", page, emptyList(), ::playlistOf)

    /** The listener's current playback, or null when nothing is playing. Needs the user-read-playback-state scope. */
    suspend fun playbackState(): ExcorSpotifyPlaybackState? {
        val body = get("me/player", marketParam()).takeIf { it.isNotEmpty() } ?: return null
        val item = body.obj("item")
        return ExcorSpotifyPlaybackState(
            isPlaying = body.bool("is_playing") ?: false,
            progressMs = body.long("progress_ms"),
            deviceName = body.obj("device")?.str("name"),
            deviceType = body.obj("device")?.str("type"),
            track = item?.takeIf { it.str("type") == "track" }?.let(::trackOf),
        )
    }

    /**
     * Starts [uris] (tracks or episodes) or resumes when empty, on [deviceId] or the active device. Spotify requires
     * Premium and the user-modify-playback-state scope; a free account gets ACCESS_DENIED. Playback happens in the
     * Spotify client; no audio reaches eXcor.
     */
    suspend fun play(uris: List<String>, deviceId: String?) {
        val playable = setOf(ExcorMusicKind.TRACK, ExcorMusicKind.EPISODE)
        if (uris.any { !it.startsWith("spotify:") || spotifyRefOf(it)?.kind !in playable }) throw fail(ExcorCatalogError.INVALID_REQUEST)
        val body = if (uris.isEmpty()) null else "{\"uris\":[${uris.joinToString(",") { "\"$it\"" }}]}"
        command(HttpMethod.Put, "me/player/play", deviceId, body)
    }

    suspend fun pause(deviceId: String?) = command(HttpMethod.Put, "me/player/pause", deviceId)
    suspend fun next(deviceId: String?) = command(HttpMethod.Post, "me/player/next", deviceId)
    suspend fun previous(deviceId: String?) = command(HttpMethod.Post, "me/player/previous", deviceId)

    suspend fun seek(positionMs: Long, deviceId: String?) {
        if (positionMs < 0) throw fail(ExcorCatalogError.INVALID_REQUEST)
        command(HttpMethod.Put, "me/player/seek", deviceId, extra = listOf("position_ms" to "$positionMs"))
    }

    /** Adds a track or episode to the queue of the user's Spotify client (Premium). */
    suspend fun queue(uri: String, deviceId: String?) {
        if (!uri.startsWith("spotify:") || spotifyRefOf(uri)?.kind !in setOf(ExcorMusicKind.TRACK, ExcorMusicKind.EPISODE)) throw fail(ExcorCatalogError.INVALID_REQUEST)
        command(HttpMethod.Post, "me/player/queue", deviceId, extra = listOf("uri" to uri))
    }

    suspend fun setVolume(percent: Int, deviceId: String?) {
        if (percent !in 0..100) throw fail(ExcorCatalogError.INVALID_REQUEST)
        command(HttpMethod.Put, "me/player/volume", deviceId, extra = listOf("volume_percent" to "$percent"))
    }

    /**
     * Saves to or removes from the user's library (`PUT`/`DELETE /me/library`, user-library-modify and the related
     * scopes). Spotify takes up to 40 URIs per request, so longer lists are sent in batches.
     */
    suspend fun saveToLibrary(uris: List<String>) = libraryBatches(uris) { command(HttpMethod.Put, "me/library", null, extra = listOf("uris" to it.joinToString(","))) }

    suspend fun removeFromLibrary(uris: List<String>) = libraryBatches(uris) { command(HttpMethod.Delete, "me/library", null, extra = listOf("uris" to it.joinToString(","))) }

    /** Whether each URI is in the user's library, in the given order. */
    suspend fun libraryContains(uris: List<String>): List<Boolean> {
        val result = mutableListOf<Boolean>()
        libraryBatches(uris) { batch ->
            val access = token() ?: throw fail(ExcorCatalogError.AUTHENTICATION_REQUIRED)
            val url = URLBuilder("https://api.spotify.com/v1/me/library/contains").apply { parameters.append("uris", batch.joinToString(",")) }.buildString()
            val flags = (http.getJson(provider, url, bearer = access) as? kotlinx.serialization.json.JsonArray)
                ?.map { (it as? kotlinx.serialization.json.JsonPrimitive)?.content == "true" }
            if (flags == null || flags.size != batch.size) throw fail(ExcorCatalogError.MALFORMED_RESPONSE)
            result += flags
        }
        return result
    }

    private suspend fun libraryBatches(uris: List<String>, send: suspend (List<String>) -> Unit) {
        if (uris.isEmpty() || uris.any { !it.startsWith("spotify:") || spotifyRefOf(it) == null }) throw fail(ExcorCatalogError.INVALID_REQUEST)
        uris.chunked(LIBRARY_BATCH).forEach { send(it) }
    }

    /** Creates a playlist for the user (`POST /me/playlists`); a collaborative playlist must be private. */
    suspend fun createPlaylist(name: String, description: String?, public: Boolean, collaborative: Boolean): ExcorMusicPlaylist {
        if (name.isBlank() || name.length > 300 || (collaborative && public)) throw fail(ExcorCatalogError.INVALID_REQUEST)
        val body = buildJsonObject {
            put("name", name)
            put("public", public)
            put("collaborative", collaborative)
            description?.let { put("description", it) }
        }
        return playlistOf(command(HttpMethod.Post, "me/playlists", null, body.toString())) ?: throw fail(ExcorCatalogError.MALFORMED_RESPONSE)
    }

    /**
     * Adds tracks or episodes at [position] (appends when null), in batches of 100 kept in order. Returns the new
     * snapshot ID. Needs playlist-modify-public or -private.
     */
    suspend fun addToPlaylist(playlistId: String, uris: List<String>, position: Int?): String {
        val id = checked(playlistId)
        playableUris(uris)
        if (position != null && position < 0) throw fail(ExcorCatalogError.INVALID_REQUEST)
        var snapshot = ""
        uris.chunked(PLAYLIST_BATCH).forEachIndexed { i, batch ->
            val body = buildJsonObject {
                put("uris", JsonArray(batch.map(::JsonPrimitive)))
                position?.let { put("position", it + i * PLAYLIST_BATCH) }
            }
            snapshot = command(HttpMethod.Post, "playlists/$id/items", null, body.toString()).str("snapshot_id") ?: throw fail(ExcorCatalogError.MALFORMED_RESPONSE)
        }
        return snapshot
    }

    /** Removes every occurrence of [uris], checked against [snapshot] when given. Returns the new snapshot ID. */
    suspend fun removeFromPlaylist(playlistId: String, uris: List<String>, snapshot: String?): String {
        val id = checked(playlistId)
        playableUris(uris)
        var current = snapshot
        uris.chunked(PLAYLIST_BATCH).forEach { batch ->
            val body = buildJsonObject {
                put("items", JsonArray(batch.map { uri -> buildJsonObject { put("uri", uri) } }))
                current?.let { put("snapshot_id", it) }
            }
            current = command(HttpMethod.Delete, "playlists/$id/items", null, body.toString()).str("snapshot_id") ?: throw fail(ExcorCatalogError.MALFORMED_RESPONSE)
        }
        return current!!
    }

    /** Moves [length] items starting at [rangeStart] to before [insertBefore]. Returns the new snapshot ID. */
    suspend fun reorderPlaylist(playlistId: String, rangeStart: Int, insertBefore: Int, length: Int, snapshot: String?): String {
        val id = checked(playlistId)
        if (rangeStart < 0 || insertBefore < 0 || length < 1) throw fail(ExcorCatalogError.INVALID_REQUEST)
        val body = buildJsonObject {
            put("range_start", rangeStart)
            put("insert_before", insertBefore)
            put("range_length", length)
            snapshot?.let { put("snapshot_id", it) }
        }
        return command(HttpMethod.Put, "playlists/$id/items", null, body.toString()).str("snapshot_id") ?: throw fail(ExcorCatalogError.MALFORMED_RESPONSE)
    }

    /** Renames or changes a playlist the user owns; null leaves a field as it is. Collaborative needs private. */
    suspend fun updatePlaylist(playlistId: String, name: String?, description: String?, public: Boolean?, collaborative: Boolean?) {
        val id = checked(playlistId)
        if (name != null && (name.isBlank() || name.length > 300)) throw fail(ExcorCatalogError.INVALID_REQUEST)
        if (collaborative == true && public == true) throw fail(ExcorCatalogError.INVALID_REQUEST)
        if (name == null && description == null && public == null && collaborative == null) throw fail(ExcorCatalogError.INVALID_REQUEST)
        val body = buildJsonObject {
            name?.let { put("name", it) }
            description?.let { put("description", it) }
            public?.let { put("public", it) }
            collaborative?.let { put("collaborative", it) }
        }
        command(HttpMethod.Put, "playlists/$id", null, body.toString())
    }

    /** Uploads a JPEG cover (ugc-image-upload scope); Spotify takes at most 256 KB of base64, about 192 KB of JPEG. */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    suspend fun uploadPlaylistCover(playlistId: String, jpeg: ByteArray) {
        val id = checked(playlistId)
        val isJpeg = jpeg.size >= 3 && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte() && jpeg[2] == 0xFF.toByte()
        val encoded = kotlin.io.encoding.Base64.Default.encode(jpeg)
        if (!isJpeg || encoded.length > COVER_LIMIT) throw fail(ExcorCatalogError.INVALID_REQUEST)
        command(HttpMethod.Put, "playlists/$id/images", null, encoded, contentType = ContentType.Image.JPEG)
    }

    private fun playableUris(uris: List<String>) {
        val playable = setOf(ExcorMusicKind.TRACK, ExcorMusicKind.EPISODE)
        if (uris.isEmpty() || uris.any { !it.startsWith("spotify:") || spotifyRefOf(it)?.kind !in playable }) throw fail(ExcorCatalogError.INVALID_REQUEST)
    }

    suspend fun devices(): List<ExcorSpotifyDevice> = get("me/player/devices").arr("devices")?.objects().orEmpty().mapNotNull { d ->
        ExcorSpotifyDevice(d.str("id") ?: return@mapNotNull null, d.str("name") ?: "", d.str("type"), d.bool("is_active") ?: false, d.bool("is_restricted") ?: false)
    }

    suspend fun episode(id: String): ExcorPodcastEpisode = episodeOf(get("episodes/${checked(id)}", marketParam())) ?: throw fail(ExcorCatalogError.MALFORMED_RESPONSE)

    suspend fun show(id: String, page: String?): ExcorPodcastShow {
        val o = get("shows/${checked(id)}", marketParam())
        val episodes = paged("shows/${checked(id)}/episodes", page, marketParam(), ::episodeOf)
        return ExcorPodcastShow(
            id = o.str("id") ?: throw fail(ExcorCatalogError.MALFORMED_RESPONSE),
            title = o.str("name") ?: throw fail(ExcorCatalogError.MALFORMED_RESPONSE),
            description = o.str("description"),
            publisher = o.str("publisher"),
            explicit = o.bool("explicit"),
            languages = o.strings("languages"),
            totalEpisodes = o.int("total_episodes"),
            url = urlOf(o),
            artwork = imagesOf(o),
            episodes = episodes,
        )
    }

    private fun episodeOf(o: JsonObject): ExcorPodcastEpisode? {
        if (o.str("type") != null && o.str("type") != "episode") return null
        val id = o.str("id")?.takeIf(SPOTIFY_ID::matches) ?: return null
        return ExcorPodcastEpisode(
            id = id,
            title = o.str("name") ?: return null,
            description = o.str("description"),
            showId = o.obj("show")?.str("id"),
            showTitle = o.obj("show")?.str("name"),
            durationMs = o.long("duration_ms"),
            releaseDate = o.str("release_date"),
            explicit = o.bool("explicit"),
            languages = o.strings("languages").ifEmpty { listOfNotNull(o.str("language")) },
            playable = o.bool("is_playable"),
            url = urlOf(o),
            artwork = imagesOf(o),
        )
    }

    // --- requests ---

    private suspend fun command(
        method: HttpMethod,
        path: String,
        deviceId: String?,
        json: String? = null,
        extra: List<Pair<String, String>> = emptyList(),
        contentType: ContentType = ContentType.Application.Json,
    ): JsonObject {
        val access = token() ?: throw fail(ExcorCatalogError.AUTHENTICATION_REQUIRED)
        if (deviceId != null && !DEVICE_ID.matches(deviceId)) throw fail(ExcorCatalogError.INVALID_REQUEST)
        val url = URLBuilder("https://api.spotify.com/v1/$path").apply {
            extra.forEach { (k, v) -> parameters.append(k, v) }
            deviceId?.let { parameters.append("device_id", it) }
        }.buildString()
        return http.command(provider, method, url, access, json, contentType) as? JsonObject ?: JsonObject(emptyMap())
    }

    private val searchLimit get() = if (extendedQuota) 50 else 10

    private fun marketParam() = listOfNotNull(market?.let { "market" to it })

    private suspend fun get(path: String, parameters: List<Pair<String, String>> = emptyList()): JsonObject {
        val access = token() ?: throw fail(ExcorCatalogError.AUTHENTICATION_REQUIRED)
        val url = URLBuilder("https://api.spotify.com/v1/$path").apply { parameters.forEach { (k, v) -> this.parameters.append(k, v) } }.buildString()
        return http.getJson(provider, url, bearer = access) as? JsonObject ?: JsonObject(emptyMap())
    }

    private suspend fun <T> paged(path: String, page: String?, parameters: List<Pair<String, String>>, parse: (JsonObject) -> T?, scope: String = path): ExcorMusicPage<T> {
        val offset = offsetOf(scope, page)
        val body = get(path, parameters + listOf("limit" to "$PAGE", "offset" to "$offset"))
        val items = body.arr("items")?.objects() ?: throw fail(ExcorCatalogError.MALFORMED_RESPONSE)
        val next = if (body.str("next") != null && items.isNotEmpty() && offset + PAGE <= MAX_OFFSET) pageToken(scope, offset + PAGE) else null
        return ExcorMusicPage(items.mapNotNull(parse), next, body.int("total"))
    }

    private fun checked(id: String) = id.takeIf(SPOTIFY_ID::matches) ?: throw fail(ExcorCatalogError.INVALID_URL)

    // --- parsing ---

    /** Library and playlist entries wrap the item as `track` (before 2026) or `item`; episodes are skipped. */
    private fun entryTrackOf(o: JsonObject): ExcorMusicTrack? = (o.obj("item") ?: o.obj("track"))?.let(::trackOf)

    private fun trackOf(o: JsonObject): ExcorMusicTrack? {
        if (o.str("type") != null && o.str("type") != "track") return null
        val id = o.str("id")?.takeIf(SPOTIFY_ID::matches) ?: return null
        val album = o.obj("album")
        val markets = o.arr("available_markets")?.let { o.strings("available_markets").toSet() }
        val restrictions = o.obj("restrictions")?.str("reason")
        val track = ExcorMusicTrack(
            provider = provider,
            id = id,
            title = o.str("name") ?: return null,
            url = urlOf(o),
            artists = o.arr("artists")?.objects().orEmpty().mapNotNull(::creditOf),
            albumId = album?.str("id"),
            albumTitle = album?.str("name"),
            albumArtists = album?.arr("artists")?.objects().orEmpty().mapNotNull(::creditOf),
            isrc = o.obj("external_ids")?.str("isrc"),
            upc = album?.obj("external_ids")?.str("upc"),
            trackNumber = o.int("track_number"),
            discNumber = o.int("disc_number"),
            durationMs = o.long("duration_ms"),
            releaseDate = album?.str("release_date"),
            explicit = o.bool("explicit"),
            popularity = o.long("popularity"),
            artwork = album?.let(::imagesOf).orEmpty(),
            markets = markets,
            availability = when {
                restrictions == "market" -> ExcorAvailability.REGION_RESTRICTED
                restrictions != null -> ExcorAvailability.UNAVAILABLE
                o.bool("is_playable") == true -> ExcorAvailability.AVAILABLE
                o.bool("is_playable") == false -> ExcorAvailability.UNAVAILABLE
                else -> ExcorAvailability.UNKNOWN
            },
            previewAvailable = if (o.containsKey("preview_url")) o.str("preview_url") != null else null,
        )
        val restricted = if (extendedQuota) emptySet() else setOf(ExcorMusicField.POPULARITY, ExcorMusicField.MARKETS)
        // Simplified tracks (album listings) lack album and ISRC; the track endpoint has them.
        val elsewhere = if (album == null) setOf(ExcorMusicField.ALBUM, ExcorMusicField.ALBUM_ARTISTS, ExcorMusicField.ISRC, ExcorMusicField.RELEASE_DATE, ExcorMusicField.ARTWORK) else emptySet()
        return track.withMissing(TRACK_OFFERED, restricted, elsewhere + ExcorMusicField.LABEL + ExcorMusicField.GENRES)
    }

    private fun albumOf(o: JsonObject): ExcorMusicAlbum? {
        if (o.str("type") != null && o.str("type") != "album") return null
        val id = o.str("id")?.takeIf(SPOTIFY_ID::matches) ?: return null
        val tracks = o.obj("tracks")
        val album = ExcorMusicAlbum(
            provider = provider,
            id = id,
            title = o.str("name") ?: return null,
            url = urlOf(o),
            type = when (o.str("album_type")) {
                "album" -> ExcorAlbumType.ALBUM; "single" -> ExcorAlbumType.SINGLE; "compilation" -> ExcorAlbumType.COMPILATION
                else -> ExcorAlbumType.UNKNOWN
            },
            artists = o.arr("artists")?.objects().orEmpty().mapNotNull(::creditOf),
            upc = o.obj("external_ids")?.str("upc"),
            releaseDate = o.str("release_date"),
            label = o.str("label"),
            copyright = o.arr("copyrights")?.objects()?.firstNotNullOfOrNull { it.str("text") },
            genres = o.strings("genres"),
            trackCount = o.int("total_tracks"),
            popularity = o.long("popularity"),
            artwork = imagesOf(o),
            tracks = tracks?.let { t ->
                val items = t.arr("items")?.objects().orEmpty().mapNotNull(::trackOf).map { it.copy(albumId = id, albumTitle = o.str("name"), artwork = imagesOf(o)) }
                ExcorMusicPage(items, if (t.str("next") != null) pageToken("albums/$id/tracks", items.size) else null, t.int("total"))
            },
        )
        val present = buildSet {
            if (album.upc != null) add(ExcorMusicField.UPC); if (album.label != null) add(ExcorMusicField.LABEL)
            if (album.popularity != null) add(ExcorMusicField.POPULARITY); if (album.copyright != null) add(ExcorMusicField.COPYRIGHT)
            if (album.genres.isNotEmpty()) add(ExcorMusicField.GENRES); if (album.releaseDate != null) add(ExcorMusicField.RELEASE_DATE)
        }
        val applicable = setOf(ExcorMusicField.UPC, ExcorMusicField.LABEL, ExcorMusicField.POPULARITY, ExcorMusicField.COPYRIGHT, ExcorMusicField.GENRES, ExcorMusicField.RELEASE_DATE)
        val restricted = if (extendedQuota) emptySet() else setOf(ExcorMusicField.LABEL, ExcorMusicField.POPULARITY)
        val summary = !o.containsKey("copyrights")
        return album.copy(missing = missingFields(applicable, applicable, present, restricted, if (summary) applicable else emptySet()))
    }

    private fun artistOf(o: JsonObject): ExcorMusicArtistProfile? {
        if (o.str("type") != null && o.str("type") != "artist") return null
        val id = o.str("id")?.takeIf(SPOTIFY_ID::matches) ?: return null
        val profile = ExcorMusicArtistProfile(
            provider = provider,
            id = id,
            name = o.str("name") ?: return null,
            url = urlOf(o),
            genres = o.strings("genres"),
            followers = o.obj("followers")?.long("total"),
            popularity = o.long("popularity"),
            artwork = imagesOf(o),
        )
        val present = buildSet {
            if (profile.followers != null) add(ExcorMusicField.FOLLOWERS); if (profile.popularity != null) add(ExcorMusicField.POPULARITY)
            if (profile.genres.isNotEmpty()) add(ExcorMusicField.GENRES); if (profile.artwork.isNotEmpty()) add(ExcorMusicField.ARTWORK)
        }
        val applicable = setOf(ExcorMusicField.FOLLOWERS, ExcorMusicField.POPULARITY, ExcorMusicField.GENRES, ExcorMusicField.ARTWORK)
        val restricted = if (extendedQuota) emptySet() else setOf(ExcorMusicField.FOLLOWERS, ExcorMusicField.POPULARITY)
        return profile.copy(missing = missingFields(applicable, applicable, present, restricted))
    }

    private fun playlistOf(o: JsonObject): ExcorMusicPlaylist? {
        if (o.str("type") != null && o.str("type") != "playlist") return null
        val id = o.str("id")?.takeIf(SPOTIFY_ID::matches) ?: return null
        val owner = o.obj("owner")
        val entries = o.obj("items") ?: o.obj("tracks")
        val items = entries?.arr("items")?.objects()?.mapNotNull(::entryTrackOf)
        val total = entries?.int("total")
        val playlist = ExcorMusicPlaylist(
            provider = provider,
            id = id,
            title = o.str("name") ?: return null,
            url = urlOf(o),
            description = o.str("description"),
            ownerName = owner?.str("display_name"),
            ownerId = owner?.str("id"),
            collaborative = o.bool("collaborative"),
            public = o.bool("public"),
            snapshot = o.str("snapshot_id"),
            trackCount = total,
            followers = o.obj("followers")?.long("total"),
            artwork = imagesOf(o),
            tracks = items?.let { ExcorMusicPage(it, if (entries.str("next") != null) pageToken("playlists/$id/items", it.size) else null, total) },
        )
        // Development Mode returns entries only for playlists the user owns or collaborates on.
        val restricted = if (items == null && entries?.containsKey("items") != true && !extendedQuota) mapOf(ExcorMusicField.TRACK_COUNT to ExcorFieldStatus.RESTRICTED) else emptyMap()
        return playlist.copy(missing = restricted)
    }

    private fun creditOf(o: JsonObject): ExcorMusicArtist? =
        ExcorMusicArtist(provider, o.str("id")?.takeIf(SPOTIFY_ID::matches), o.str("name") ?: return null, ExcorArtistRole.PRIMARY, urlOf(o))

    private fun urlOf(o: JsonObject): String? = o.obj("external_urls")?.str("spotify")?.takeIf { httpsUrlOn(it, setOf("open.spotify.com")) != null }

    private fun imagesOf(o: JsonObject): List<ExcorArtwork> = o.arr("images")?.objects().orEmpty()
        .mapNotNull { artworkOf(it.str("url"), listOf(".scdn.co", ".spotifycdn.com"), it.int("width"), it.int("height")) }

    companion object {
        const val PAGE = 50
        val SPOTIFY_ID = Regex("[A-Za-z0-9]{22}")
        val DEVICE_ID = Regex("[A-Za-z0-9]{1,128}")
        const val LIBRARY_BATCH = 40
        const val PLAYLIST_BATCH = 100
        const val COVER_LIMIT = 256 * 1024
        val SEARCH_TYPES = mapOf(
            ExcorMusicKind.TRACK to "track", ExcorMusicKind.ALBUM to "album",
            ExcorMusicKind.ARTIST to "artist", ExcorMusicKind.PLAYLIST to "playlist",
        )
        val TRACK_OFFERED = setOf(
            ExcorMusicField.ARTISTS, ExcorMusicField.ALBUM, ExcorMusicField.ALBUM_ARTISTS, ExcorMusicField.ISRC,
            ExcorMusicField.TRACK_NUMBER, ExcorMusicField.DISC_NUMBER, ExcorMusicField.DURATION, ExcorMusicField.RELEASE_DATE,
            ExcorMusicField.EXPLICIT, ExcorMusicField.POPULARITY, ExcorMusicField.ARTWORK, ExcorMusicField.MARKETS,
            ExcorMusicField.PREVIEW, ExcorMusicField.URL, ExcorMusicField.LABEL, ExcorMusicField.GENRES,
        )
    }
}

data class ExcorSpotifyPlaybackState(
    val isPlaying: Boolean,
    val progressMs: Long?,
    val deviceName: String?,
    val deviceType: String?,
    val track: ExcorMusicTrack?,
)

/**
 * A Spotify link, `spotify:` URI, or a 32-hex-digit GID (as some tools print) converted to the base-62 ID.
 * Short links (spotify.link) are not followed.
 */
fun spotifyRefOf(value: String): ExcorMusicRef? {
    val text = value.trim()
    val kinds = mapOf(
        "track" to ExcorMusicKind.TRACK, "album" to ExcorMusicKind.ALBUM, "artist" to ExcorMusicKind.ARTIST,
        "playlist" to ExcorMusicKind.PLAYLIST, "episode" to ExcorMusicKind.EPISODE, "show" to ExcorMusicKind.SHOW,
    )
    if (text.startsWith("spotify:")) {
        val parts = text.split(':')
        if (parts.size != 3) return null
        val kind = kinds[parts[1]] ?: return null
        val id = parts[2].let { if (GID.matches(it)) spotifyIdOfGid(it) else it }.takeIf(SpotifySource.SPOTIFY_ID::matches) ?: return null
        return ExcorMusicRef(ExcorCatalogProvider.SPOTIFY, kind, id)
    }
    val url = httpsUrlOn(text, setOf("open.spotify.com")) ?: return null
    val segments = url.encodedPath.split('/').filter(String::isNotEmpty)
    val rest = if (segments.firstOrNull()?.matches(Regex("intl-[A-Za-z]{2}(-[A-Za-z]{2})?")) == true) segments.drop(1) else segments
    if (rest.size != 2) return null
    val kind = kinds[rest[0]] ?: return null
    val id = rest[1].takeIf(SpotifySource.SPOTIFY_ID::matches) ?: return null
    return ExcorMusicRef(ExcorCatalogProvider.SPOTIFY, kind, id)
}

private val GID = Regex("[0-9a-fA-F]{32}")

/** A 128-bit GID in hex as Spotify's 22-character base-62 ID (digits, then lower case, then upper case). */
fun spotifyIdOfGid(gid: String): String {
    require(GID.matches(gid))
    val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    var number = gid.chunked(2).map { it.toInt(16) }.toIntArray()
    val digits = StringBuilder()
    while (number.any { it != 0 }) {
        var remainder = 0
        val quotient = IntArray(number.size)
        for (i in number.indices) {
            val acc = remainder * 256 + number[i]
            quotient[i] = acc / 62
            remainder = acc % 62
        }
        digits.append(alphabet[remainder])
        number = quotient
    }
    return digits.reverse().toString().padStart(22, '0')
}

data class ExcorSpotifyDevice(val id: String, val name: String, val type: String?, val active: Boolean, val restricted: Boolean)

/** A Spotify podcast episode; kept apart from music tracks. */
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
)

data class ExcorPodcastShow(
    val id: String,
    val title: String,
    val description: String?,
    /** Withheld for Development Mode apps since February 2026. */
    val publisher: String?,
    val explicit: Boolean?,
    val languages: List<String>,
    val totalEpisodes: Int?,
    val url: String?,
    val artwork: List<ExcorArtwork>,
    val episodes: ExcorMusicPage<ExcorPodcastEpisode>,
)
