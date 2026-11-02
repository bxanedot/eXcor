package com.excor.music

import com.excor.catalog.ExcorCatalogError
import com.excor.catalog.ExcorCatalogException
import com.excor.catalog.ExcorCatalogProvider.APPLE_MUSIC
import com.excor.catalog.ExcorCatalogProvider.DEEZER
import com.excor.catalog.ExcorCatalogProvider.SPOTIFY
import com.excor.catalog.ExcorCatalogTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

// All responses below are synthetic: invented IDs, names and codes in the shape each provider documents or returns.
class ProvidersTest {
    private class Server(val routes: MutableMap<String, (HttpRequestData) -> Pair<Int, String>> = mutableMapOf()) {
        val requests = mutableListOf<HttpRequestData>()
        val client = HttpClient(MockEngine { request ->
            requests += request
            val key = request.url.host + request.url.encodedPath
            val route = routes[key] ?: routes.entries.firstOrNull { key.startsWith(it.key.removeSuffix("*")) && it.key.endsWith("*") }?.value
            val (status, body) = route?.invoke(request) ?: (404 to "{}")
            reply(status, body)
        })
        private fun MockRequestHandleScope.reply(status: Int, body: String) =
            respond(body, HttpStatusCode.fromValue(status), headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8"))
    }

    private fun music(server: Server, tokens: Map<com.excor.catalog.ExcorCatalogProvider, String> = emptyMap(), config: ExcorMusicConfig = ExcorMusicConfig()) =
        ExcorMusic(server.client, config.copy(tokens = ExcorCatalogTokenProvider { tokens[it] }), nowMs = { 0L })

    private val deezerTrack = """
        {"id":1001,"readable":true,"title":"Glass River (Live)","title_short":"Glass River","title_version":"(Live)",
         "isrc":"QZAAA2400001","link":"https://www.deezer.com/track/1001","duration":201,"track_position":3,"disk_number":1,
         "rank":420000,"release_date":"2024-05-01","explicit_lyrics":false,"preview":"https://cdnt-preview.dzcdn.net/synthetic.mp3",
         "available_countries":["FR","DE"],"track_token":"SYNTHETIC-TOKEN-NEVER-READ",
         "contributors":[{"id":11,"name":"Mira Vale","role":"Main","link":"https://www.deezer.com/artist/11"},{"id":12,"name":"Oskar Lind","role":"Featured"}],
         "artist":{"id":11,"name":"Mira Vale"},
         "album":{"id":501,"title":"Night Ferry","cover_small":"https://cdn-images.dzcdn.net/images/cover/x/56x56.jpg","cover_xl":"https://cdn-images.dzcdn.net/images/cover/x/1000x1000.jpg","cover_big":"http://insecure.example/x.jpg"},
         "type":"track"}
    """.trimIndent()

    @Test
    fun deezerTrackKeepsMetadataAndDropsPlaybackSecrets() = runTest {
        val server = Server(mutableMapOf("api.deezer.com/track/1001" to { _ -> 200 to deezerTrack }))
        val track = music(server).track("https://www.deezer.com/en/track/1001")
        assertEquals("Glass River", track.title)
        assertEquals("Glass River (Live)", track.displayTitle)
        assertEquals("(Live)", track.version)
        assertEquals(listOf(ExcorArtistRole.PRIMARY, ExcorArtistRole.FEATURED), track.artists.map { it.role })
        assertEquals("QZAAA2400001", track.isrc)
        assertEquals(201_000L, track.durationMs)
        assertEquals(setOf("FR", "DE"), track.markets)
        assertEquals(ExcorAvailability.AVAILABLE, track.availability)
        assertEquals(true, track.previewAvailable)
        assertEquals(listOf(56, 1000), track.artwork.map { it.width }) // the http cover is refused
        assertEquals(ExcorFieldStatus.UNKNOWN, track.missing[ExcorMusicField.LABEL])
        assertEquals(ExcorFieldStatus.UNSUPPORTED, track.missing[ExcorMusicField.COMPOSERS])
        val text = track.toString()
        assertFalse("SYNTHETIC-TOKEN" in text)
        assertFalse("preview" in text.lowercase().replace("previewavailable", ""))
        val drm = music(server).drmInfo(track)!!
        assertTrue(drm.protected)
        assertEquals(setOf(ExcorDrmSystem.PROVIDER_MANAGED), drm.systems)
    }

    @Test
    fun deezerErrorsInA200BodyAreTyped() = runTest {
        val server = Server(mutableMapOf(
            "api.deezer.com/track/9" to { _ -> 200 to """{"error":{"type":"DataException","message":"no data","code":800}}""" },
            "api.deezer.com/track/8" to { _ -> 200 to """{"error":{"type":"Exception","message":"Quota limit exceeded","code":4}}""" },
            "api.deezer.com/track/isrc:QZAAA2400009" to { _ -> 200 to """{"error":{"type":"DataException","message":"no data","code":800}}""" },
        ))
        val m = music(server)
        assertEquals(ExcorCatalogError.NOT_FOUND, assertFailsWith<ExcorCatalogException> { m.track(ExcorMusicRef(DEEZER, ExcorMusicKind.TRACK, "9")) }.error)
        assertEquals(ExcorCatalogError.RATE_LIMITED, assertFailsWith<ExcorCatalogException> { m.track(ExcorMusicRef(DEEZER, ExcorMusicKind.TRACK, "8")) }.error)
        assertEquals(emptyList(), m.tracksByIsrc(DEEZER, "qzaaa2400009"))
        assertEquals(ExcorCatalogError.INVALID_URL, assertFailsWith<ExcorCatalogException> { m.track(ExcorMusicRef(DEEZER, ExcorMusicKind.TRACK, "../user/me")) }.error)
        assertEquals(ExcorCatalogError.INVALID_REQUEST, assertFailsWith<ExcorCatalogException> { m.track(ExcorMusicRef(DEEZER, ExcorMusicKind.ALBUM, "9")) }.error)
    }

    @Test
    fun deezerPagingTokensAreScopedAndAnonymousReadsCached() = runTest {
        val server = Server(mutableMapOf("api.deezer.com/search/track" to { r ->
            val index = r.url.parameters["index"]
            200 to """{"data":[${deezerTrack.replace("1001", if (index == "0") "1001" else "1002")}],"total":2,"next":"https://api.deezer.com/search/track?index=1"}"""
        }))
        val m = music(server)
        val first = m.search("glass river", DEEZER, limit = 1)
        val next = first.tracks!!.next!!
        assertEquals(2, first.tracks!!.total)
        val second = m.search("glass river", DEEZER, limit = 1, page = next)
        assertEquals("1002", second.tracks!!.items.single().id)
        assertNull(second.tracks!!.next)
        assertEquals(ExcorCatalogError.INVALID_PAGE_TOKEN, assertFailsWith<ExcorCatalogException> { m.search("other query", DEEZER, limit = 1, page = next) }.error)
        assertEquals(ExcorCatalogError.INVALID_PAGE_TOKEN, assertFailsWith<ExcorCatalogException> { m.search("glass river", DEEZER, limit = 1, page = "DEEZER|x|99999999") }.error)
        val before = server.requests.size
        m.search("glass river", DEEZER, limit = 1)
        assertEquals(before, server.requests.size)
        assertEquals(before, m.requestCount)
    }

    @Test
    fun deezerLibraryNeedsAToken() = runTest {
        val server = Server(mutableMapOf("api.deezer.com/user/me/tracks" to { r ->
            assertEquals("deezer-user-token", r.url.parameters["access_token"])
            200 to """{"data":[$deezerTrack],"total":1}"""
        }))
        assertEquals(ExcorCatalogError.AUTHENTICATION_REQUIRED, assertFailsWith<ExcorCatalogException> { music(server).libraryTracks(DEEZER) }.error)
        assertEquals(0, server.requests.size)
        val m = music(server, mapOf(DEEZER to "deezer-user-token"))
        assertEquals("1001", m.libraryTracks(DEEZER).items.single().id)
        m.libraryTracks(DEEZER)
        assertEquals(2, server.requests.size) // never cached
    }

    private val spotifyTrack = """
        {"type":"track","id":"0123456789abcdefABCDEF","name":"Glass River","duration_ms":200500,"explicit":false,
         "track_number":3,"disc_number":1,"external_ids":{"isrc":"QZAAA2400001"},"is_playable":true,
         "external_urls":{"spotify":"https://open.spotify.com/track/0123456789abcdefABCDEF"},
         "artists":[{"id":"1111111111111111111111","name":"Mira Vale","external_urls":{"spotify":"https://open.spotify.com/artist/1111111111111111111111"}}],
         "album":{"id":"2222222222222222222222","name":"Night Ferry","release_date":"2024-05-01","album_type":"album",
           "artists":[{"id":"1111111111111111111111","name":"Mira Vale"}],
           "images":[{"url":"https://i.scdn.co/image/synthetic","width":640,"height":640},{"url":"https://evil.example/i.jpg","width":64,"height":64}]}}
    """.trimIndent()

    @Test
    fun spotifyUsesTheHostTokenAndMarksWithheldFieldsRestricted() = runTest {
        val server = Server(mutableMapOf(
            "api.spotify.com/v1/tracks/0123456789abcdefABCDEF" to { r ->
                assertEquals("Bearer spotify-token", r.headers[HttpHeaders.Authorization])
                assertEquals("SE", r.url.parameters["market"])
                200 to spotifyTrack
            },
            "api.spotify.com/v1/search" to { r ->
                assertEquals("10", r.url.parameters["limit"]) // Development Mode cap
                assertEquals("album,track", r.url.parameters["type"])
                200 to """{"tracks":{"items":[$spotifyTrack],"total":1,"next":null},"albums":{"items":[],"total":0,"next":null}}"""
            },
        ))
        assertEquals(ExcorCatalogError.AUTHENTICATION_REQUIRED, assertFailsWith<ExcorCatalogException> { music(server).track("spotify:track:0123456789abcdefABCDEF") }.error)
        assertEquals(0, server.requests.size)

        val m = music(server, mapOf(SPOTIFY to "spotify-token"), ExcorMusicConfig(spotifyMarket = "SE"))
        val track = m.track("https://open.spotify.com/intl-de/track/0123456789abcdefABCDEF")
        assertEquals("QZAAA2400001", track.isrc)
        assertEquals("Mira Vale", track.albumArtists.single().name)
        assertEquals(1, track.artwork.size)
        assertEquals(ExcorAvailability.AVAILABLE, track.availability)
        assertEquals(ExcorFieldStatus.RESTRICTED, track.missing[ExcorMusicField.POPULARITY])
        assertEquals(ExcorFieldStatus.RESTRICTED, track.missing[ExcorMusicField.MARKETS])
        val search = m.search("glass river", SPOTIFY, setOf(ExcorMusicKind.TRACK, ExcorMusicKind.ALBUM), limit = 50)
        assertEquals(1, search.tracks!!.items.size)
        assertEquals(0, search.albums!!.items.size)
    }

    @Test
    fun spotifyPlaylistEntriesInEitherShapeAndOneRetryAfter429() = runTest {
        var calls = 0
        val server = Server(mutableMapOf("api.spotify.com/v1/playlists/3333333333333333333333/items" to { _ ->
            calls++
            if (calls == 1) 429 to """{"error":{"status":429,"message":"Too many requests","reason":"QUOTA_EXCEEDED"}}"""
            else 200 to """{"items":[{"item":$spotifyTrack},{"track":$spotifyTrack},{"item":{"type":"episode","id":"4444444444444444444444"}}],"total":3,"next":null}"""
        }))
        // A 429 without Retry-After is not retried blindly.
        val m = music(server, mapOf(SPOTIFY to "t"))
        val ref = ExcorMusicRef(SPOTIFY, ExcorMusicKind.PLAYLIST, "3333333333333333333333")
        assertEquals(ExcorCatalogError.RATE_LIMITED, assertFailsWith<ExcorCatalogException> { m.playlistTracks(ref) }.error)
        assertEquals(2, m.playlistTracks(ref).items.size)
    }

    @Test
    fun spotifyIdentifiers() {
        assertEquals(ExcorMusicRef(SPOTIFY, ExcorMusicKind.ALBUM, "2222222222222222222222"), spotifyRefOf("https://open.spotify.com/album/2222222222222222222222?si=x"))
        assertEquals(ExcorMusicKind.EPISODE, spotifyRefOf("spotify:episode:2222222222222222222222")!!.kind)
        assertNull(spotifyRefOf("http://open.spotify.com/track/2222222222222222222222"))
        assertNull(spotifyRefOf("https://open.spotify.com.evil.example/track/2222222222222222222222"))
        assertNull(spotifyRefOf("spotify:track:short"))
        assertEquals("0000000000000000000000", spotifyIdOfGid("00000000000000000000000000000000"))
        assertEquals("0000000000000000000001", spotifyIdOfGid("00000000000000000000000000000001"))
        assertEquals("000000000000000000000z", spotifyIdOfGid("00000000000000000000000000000023"))
        assertEquals("0000000000000000000010", spotifyIdOfGid("0000000000000000000000000000003e"))
        // Published GID/ID pairs (librespot's test data).
        assertEquals("5sWHDYs0csV6RS48xBl0tH", spotifyIdOfGid("b39fe8081e1f4c54be38e8d6f9f12bb9"))
        assertEquals("4GNcXTGWmnZ3ySrqvol3o4", spotifyIdOfGid("9a1b1cfbc6f244569ae0356c77bbe9d8"))
        val id = spotifyIdOfGid("ffffffffffffffffffffffffffffffff")
        assertEquals(22, id.length)
        assertEquals(spotifyRefOf("spotify:track:ffffffffffffffffffffffffffffffff")!!.id, id)
    }

    private val appleSong = """
        {"data":[{"id":"7001","type":"songs","attributes":{"name":"Glass River","artistName":"Mira Vale","albumName":"Night Ferry",
          "isrc":"QZAAA2400001","durationInMillis":200900,"trackNumber":3,"discNumber":1,"releaseDate":"2024-05-01",
          "genreNames":["Electronic","Music"],"composerName":"M. Vale & O. Lind","audioLocale":"en-US","contentRating":"explicit",
          "hasLyrics":true,"isAppleDigitalMaster":true,"audioVariants":["dolby-atmos","lossless","hi-res-lossless","lossy-stereo"],
          "previews":[{"url":"https://audio-ssl.itunes.apple.com/synthetic-preview.m4a"}],"playParams":{"id":"7001","kind":"song"},
          "url":"https://music.apple.com/us/album/night-ferry/7000?i=7001",
          "artwork":{"url":"https://is1-ssl.mzstatic.com/image/thumb/synthetic/{w}x{h}bb.jpg","width":3000,"height":3000}},
          "relationships":{"albums":{"data":[{"id":"7000","type":"albums"}]},"artists":{"data":[{"id":"7100","type":"artists","attributes":{"name":"Mira Vale"}}]}}}]}
    """.trimIndent()

    @Test
    fun appleMusicApiGivesAudioVariantsWithoutPreviewUrls() = runTest {
        val server = Server(mutableMapOf(
            "api.music.apple.com/v1/catalog/us/songs/7001" to { r ->
                assertEquals("Bearer apple-developer-token", r.headers[HttpHeaders.Authorization])
                assertNull(r.headers["Music-User-Token"])
                200 to appleSong
            },
            "api.music.apple.com/v1/me/library/songs" to { r ->
                assertEquals("apple-user-token", r.headers["Music-User-Token"])
                200 to appleSong.replace("\"songs\"", "\"library-songs\"")
            },
        ))
        val m = music(server, mapOf(APPLE_MUSIC to "apple-developer-token"), ExcorMusicConfig(appleMusicUserToken = { "apple-user-token" }))
        val track = m.track("https://music.apple.com/us/album/night-ferry/7000?i=7001")
        assertEquals(setOf(ExcorAudioTrait.DOLBY_ATMOS, ExcorAudioTrait.LOSSLESS, ExcorAudioTrait.HI_RES_LOSSLESS, ExcorAudioTrait.LOSSY_STEREO), track.audioTraits)
        assertEquals(listOf(ExcorAudioCodec.ALAC), track.audioFormats.map { it.codec })
        assertEquals(listOf("Electronic"), track.genres)
        assertEquals(listOf("M. Vale & O. Lind"), track.composers)
        assertEquals("en-US", track.language)
        assertEquals(true, track.explicit)
        assertEquals(true, track.hasLyrics)
        assertEquals(true, track.previewAvailable)
        assertEquals("7000", track.albumId)
        assertEquals("7100", track.artists.single().id)
        assertEquals(listOf(300, 600, 1200, 3000), track.artwork.map { it.width })
        assertFalse("synthetic-preview" in track.toString())
        val caps = m.audioCapabilities(track)
        assertEquals(true, caps.lossless)
        assertEquals(true, caps.hiResLossless)
        assertEquals(true, caps.spatial)
        assertEquals(1, m.libraryTracks(APPLE_MUSIC).items.size)
    }

    @Test
    fun appleWithoutDeveloperTokenFallsBackToItunes() = runTest {
        val server = Server(mutableMapOf("itunes.apple.com/lookup" to { r ->
            assertEquals("us", r.url.parameters["country"])
            200 to """{"resultCount":1,"results":[{"wrapperType":"track","kind":"song","trackId":7001,"trackName":"Glass River","artistName":"Mira Vale",
              "artistId":7100,"collectionId":7000,"collectionName":"Night Ferry","trackTimeMillis":200900,"trackNumber":3,"discNumber":1,
              "releaseDate":"2024-05-01T07:00:00Z","primaryGenreName":"Electronic","trackExplicitness":"notExplicit","isStreamable":true,
              "previewUrl":"https://audio-ssl.itunes.apple.com/synthetic-preview.m4a",
              "trackViewUrl":"https://music.apple.com/us/album/night-ferry/7000?i=7001&uo=4",
              "artworkUrl100":"https://is1-ssl.mzstatic.com/image/thumb/synthetic/100x100bb.jpg"}]}"""
        }))
        val m = music(server)
        val track = m.track(ExcorMusicRef(APPLE_MUSIC, ExcorMusicKind.TRACK, "7001"))
        assertEquals("2024-05-01", track.releaseDate)
        assertEquals(false, track.explicit)
        assertEquals(ExcorFieldStatus.RESTRICTED, track.missing[ExcorMusicField.ISRC])
        assertEquals(ExcorFieldStatus.RESTRICTED, track.missing[ExcorMusicField.AUDIO_TRAITS])
        assertNull(track.previewAvailable)
        assertFalse("synthetic-preview" in track.toString())
        assertEquals(null, m.audioCapabilities(track).lossless)
        assertEquals(ExcorCatalogError.AUTHENTICATION_REQUIRED, assertFailsWith<ExcorCatalogException> { m.tracksByIsrc(APPLE_MUSIC, "QZAAA2400001") }.error)
    }

    @Test
    fun crossProviderMatchingUsesIsrcAndReportsErrorsPerProvider() = runTest {
        val server = Server(mutableMapOf(
            "api.deezer.com/track/isrc:QZAAA2400001" to { _ -> 200 to deezerTrack.replace("\"title\":\"Glass River (Live)\"", "\"title\":\"Glass River\"").replace("\"(Live)\"", "\"\"") },
        ))
        val m = music(server)
        val source = ExcorMusicTrack(SPOTIFY, "0123456789abcdefABCDEF", "Glass River", isrc = "QZAAA2400001", durationMs = 200_500,
            artists = listOf(ExcorMusicArtist(SPOTIFY, null, "Mira Vale")), explicit = false)
        val result = m.matchAcrossProviders(source)
        val deezer = result.matches[DEEZER]!!.single()
        assertEquals(ExcorMatchVerdict.SAME_RECORDING, deezer.verdict)
        // Apple Music without a developer token: ISRC lookup unavailable, text search on iTunes returns 404 here.
        assertTrue(APPLE_MUSIC in result.errors)

        val enriched = m.enrich(source.copy(isrc = null), listOf(deezer))
        assertEquals("QZAAA2400001", enriched.track.isrc)
        assertEquals(DEEZER, enriched.sources[ExcorMusicField.ISRC]!!.provider)
        assertEquals(source.durationMs, enriched.track.durationMs) // present fields are kept
    }

    @Test
    fun playlistConversionMatchesEachTrackAndKeepsPerTrackErrors() = runTest {
        val song = """{"wrapperType":"track","kind":"song","trackId":7001,"trackName":"Glass River (Live)","artistName":"Mira Vale","artistId":7100,
            "collectionId":7000,"collectionName":"Night Ferry","trackTimeMillis":201000,"trackExplicitness":"notExplicit",
            "trackViewUrl":"https://music.apple.com/us/album/night-ferry/7000?i=7001"}"""
        val server = Server(mutableMapOf(
            "api.deezer.com/playlist/900" to { _ -> 200 to """{"id":900,"title":"Mix","checksum":"c1","nb_tracks":3,"type":"playlist"}""" },
            "api.deezer.com/playlist/900/tracks" to { r ->
                val ids = if (r.url.parameters["index"] == "0") listOf("1001", "1002") else listOf("1003")
                // Track 1002 has its own title, so its search is a separate (uncached) request.
                200 to """{"data":[${ids.joinToString(",") { id -> deezerTrack.replace("1001", id).let { if (id == "1002") it.replace("Glass River", "Paper Moon") else it } }}],"total":3}"""
            },
            "itunes.apple.com/search" to { r -> if (r.url.parameters["term"].orEmpty().contains("paper", ignoreCase = true)) 403 to "{}" else 200 to """{"resultCount":1,"results":[$song]}""" },
        ))
        val m = music(server)
        val ref = m.parse("https://www.deezer.com/playlist/900")!!
        val reported = mutableListOf<String>()
        val all = m.convertPlaylist(ref, APPLE_MUSIC) { reported += it.source.id }
        assertEquals(listOf("1001", "1002", "1003"), all.tracks.map { it.source.id })
        assertEquals(listOf("1001", "1002", "1003"), reported)
        assertEquals("7001", all.tracks[0].best!!.candidate.id)
        assertEquals(ExcorCatalogError.ACCESS_DENIED, all.tracks[1].error!!.error)
        assertNull(all.tracks[1].best)
        assertEquals("7001", all.tracks[2].best!!.candidate.id)
        assertFalse(all.truncated)

        // Stopping at the page boundary with another page left is still truncated.
        val two = m.convertPlaylist(ref, APPLE_MUSIC, maxTracks = 2)
        assertEquals(2, two.tracks.size)
        assertTrue(two.truncated)
        assertFailsWith<IllegalArgumentException> { m.convertPlaylist(ref, DEEZER) }
    }

    @Test
    fun playlistSyncShortCircuitsOnAnUnchangedChecksum() = runTest {
        val server = Server(mutableMapOf(
            "api.deezer.com/playlist/900" to { _ -> 200 to """{"id":900,"title":"Mix","checksum":"c1","nb_tracks":2,"type":"playlist","creator":{"id":5,"name":"Ed"}}""" },
            "api.deezer.com/playlist/900/tracks" to { _ -> 200 to """{"data":[$deezerTrack,${deezerTrack.replace("1001", "1002")}],"total":2,"checksum":"c1"}""" },
        ))
        val m = music(server)
        val ref = m.parse("https://www.deezer.com/playlist/900")!!
        val first = m.syncPlaylist(ref, null)
        assertEquals(listOf("1001", "1002"), first.added)
        val requests = server.requests.size
        val second = m.syncPlaylist(ref, first.state)
        assertTrue(second.unchanged)
        assertEquals(requests + 1, server.requests.size) // the fresh playlist metadata only; no track pages
    }

    @Test
    fun protectedPlaybackGoesOnlyThroughAnAdapter() = runTest {
        val track = ExcorMusicTrack(SPOTIFY, "0123456789abcdefABCDEF", "Glass River", availability = ExcorAvailability.AVAILABLE)
        val m = music(Server())
        val e = assertFailsWith<ExcorPlaybackException> { m.play(track) }
        assertEquals(ExcorPlaybackStatus.PROTECTED_REQUIRES_CLIENT, e.status)
        var played = false
        val adapter = object : ExcorProtectedPlaybackAdapter {
            override val provider = SPOTIFY
            override suspend fun isReady() = true
            override suspend fun isEntitled(track: ExcorMusicTrack): Boolean? = null
            override suspend fun play(track: ExcorMusicTrack) { played = true }
        }
        val withAdapter = ExcorMusic(Server().client, ExcorMusicConfig(adapters = listOf(adapter)), nowMs = { 0L })
        withAdapter.play(track)
        assertTrue(played)
    }

    @Test
    fun capabilitiesFollowCredentials() = runTest {
        val anonymous = music(Server())
        fun status(list: List<ExcorMusicCapabilityStatus>, c: ExcorMusicCapability) = list.single { it.capability == c }.status
        val spotify = anonymous.capabilities(SPOTIFY)
        assertEquals(com.excor.catalog.ExcorCatalogCapabilityStatus.AUTHENTICATION_REQUIRED, status(spotify, ExcorMusicCapability.SEARCH))
        val deezer = anonymous.capabilities(DEEZER)
        assertEquals(com.excor.catalog.ExcorCatalogCapabilityStatus.SUPPORTED, status(deezer, ExcorMusicCapability.SEARCH))
        assertEquals(com.excor.catalog.ExcorCatalogCapabilityStatus.AUTHENTICATION_REQUIRED, status(deezer, ExcorMusicCapability.LIBRARY))
        assertEquals(com.excor.catalog.ExcorCatalogCapabilityStatus.UNSUPPORTED, status(deezer, ExcorMusicCapability.AUDIO_TRAITS))
        val apple = anonymous.capabilities(APPLE_MUSIC)
        assertEquals(com.excor.catalog.ExcorCatalogCapabilityStatus.SUPPORTED, status(apple, ExcorMusicCapability.TRACK))
        assertEquals(com.excor.catalog.ExcorCatalogCapabilityStatus.AUTHENTICATION_REQUIRED, status(apple, ExcorMusicCapability.ISRC_LOOKUP))
        assertEquals(ExcorCatalogError.INVALID_CREDENTIAL, assertFailsWith<ExcorCatalogException> {
            music(Server(), mapOf(SPOTIFY to "bad token")).capabilities(SPOTIFY)
        }.error)
    }

    @Test
    fun spotifyPkceSignInAndRefresh() = runTest {
        var refreshCalls = 0
        val server = Server(mutableMapOf("accounts.spotify.com/api/token" to { r ->
            val body = (r.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes().decodeToString()
            when {
                "grant_type=authorization_code" in body -> {
                    assertTrue("code_verifier=" in body && "client_id=abc123" in body && "code=the-code" in body)
                    200 to """{"access_token":"a1","token_type":"Bearer","expires_in":3600,"refresh_token":"r1","scope":"user-library-read playlist-read-private"}"""
                }
                refreshCalls++ == 0 -> 200 to """{"access_token":"a2","token_type":"Bearer","expires_in":3600}"""
                else -> 400 to """{"error":"invalid_grant"}"""
            }
        }))
        val auth = ExcorMusic(server.client, ExcorMusicConfig(), nowMs = { 1_000L }).spotifyAuth("abc123")
        val request = auth.authorizationUrl("com.example.app://callback", setOf("user-library-read"))
        assertTrue(request.url.startsWith("https://accounts.spotify.com/authorize?"))
        assertTrue("code_challenge_method=S256" in request.url && "state=${request.state}" in request.url)
        assertFalse(request.pkce.verifier in request.url)
        assertFalse(request.pkce.verifier in request.toString())

        assertEquals(ExcorCatalogError.INVALID_REQUEST, assertFailsWith<ExcorCatalogException> { auth.exchange(request, "the-code", "forged") }.error)
        assertEquals(0, server.requests.size)
        val token = auth.exchange(request, "the-code", request.state)
        assertEquals("r1", token.refreshToken)
        assertEquals(3_601_000L, token.expiresAtMs)
        assertEquals(setOf("user-library-read", "playlist-read-private"), token.scopes)

        val renewed = auth.refresher.refresh(SPOTIFY, token)
        assertEquals("a2", renewed.accessToken)
        assertEquals("r1", renewed.refreshToken) // not rotated, so kept
        assertEquals(ExcorCatalogError.INVALID_CREDENTIAL, assertFailsWith<ExcorCatalogException> { auth.refresher.refresh(SPOTIFY, token) }.error)
        assertTrue(excorDeezerAuthorizationUrl("12345", "https://example.invalid/cb").startsWith("https://connect.deezer.com/oauth/auth.php?app_id=12345"))
    }

    @Test
    fun metadataCompletesFromTheAlbumWithOneRequest() = runTest {
        val server = Server(mutableMapOf(
            "api.deezer.com/track/1001" to { _ -> 200 to deezerTrack },
            "api.deezer.com/album/501" to { _ -> 200 to """{"id":501,"title":"Night Ferry","upc":"000000000017","label":"Synthetic Records","type":"album",
                "genres":{"data":[{"id":1,"name":"Electro"}]},"contributors":[{"id":11,"name":"Mira Vale","role":"Main"}],"release_date":"2024-05-01","record_type":"album"}""" },
        ))
        val m = music(server)
        val track = m.track(ExcorMusicRef(DEEZER, ExcorMusicKind.TRACK, "1001"))
        val before = server.requests.size
        val full = m.metadata(track)
        assertEquals(before + 1, server.requests.size)
        assertEquals("Synthetic Records", full.label)
        assertEquals(listOf("Electro"), full.genres)
        assertEquals("000000000017", full.upc)
        assertEquals("Mira Vale", full.albumArtists.single().name)
        assertNull(full.missing[ExcorMusicField.LABEL])
    }
}
