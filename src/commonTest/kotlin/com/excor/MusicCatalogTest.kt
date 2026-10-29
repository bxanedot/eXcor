package com.excor

import com.excor.internal.MusicCatalogEngine
import com.excor.internal.deezerItemOf
import com.excor.internal.musicCatalogRefOf
import com.excor.internal.spotifyEmbedOf
import com.excor.internal.spotifyItemOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Hand-written minimal API-shaped data, not copied platform responses, and no preview/audio URLs.
private const val SPOTIFY_ID = "4uLU6hMCjMI75M1A2tKUQC"
private const val ARTIST_ID = "0TnOYISbd1XYRBk9myaseg"
private val spotifyTrack = """
{
 "id":"$SPOTIFY_ID","name":"Example song","duration_ms":212000,"explicit":false,
 "track_number":2,"disc_number":1,"external_ids":{"isrc":"GBARL0000001"},
 "artists":[{"id":"$ARTIST_ID","name":"Example artist"}],
 "album":{"id":"4aawyAB9vmqN3uQ7FjRGTy","name":"Example album",
 "release_date":"2020-01","images":[{"url":"https://i.scdn.co/image/cover","width":640,"height":640}]}
}
""".trimIndent()
private val deezerTrack = """
{
 "id":3135556,"title":"Example song","duration":212,"isrc":"GBARL0000001",
 "track_position":2,"disk_number":1,"explicit_lyrics":false,
 "artist":{"id":27,"name":"Example artist","picture_xl":"https://cdn-images.dzcdn.net/artist.jpg"},
 "album":{"id":302127,"title":"Example album","cover_xl":"https://cdn-images.dzcdn.net/cover.jpg",
 "cover_medium":"https://cdn-images.dzcdn.net/cover-250.jpg"}
}
""".trimIndent()
private val deezerAlbum = """
{
 "id":302127,"title":"Example album","release_date":"2001-03-12",
 "nb_tracks":2,"upc":"123456789012","label":"Example label",
 "cover_xl":"https://cdn-images.dzcdn.net/cover.jpg",
 "artist":{"id":27,"name":"Example artist"},
 "genres":{"data":[{"name":"Electronic"}]},
 "tracks":{"data":[{"id":3135556,"title":"One","duration":100,"artist":{"id":27,"name":"Example artist"}},
                   {"id":3135557,"title":"Two","duration":120,"artist":{"id":27,"name":"Example artist"}}]}
}
""".trimIndent()

class MusicCatalogTest {
    @Test
    fun strictLinksAndSpotifyUri() {
        val expected = MusicCatalogRef(MusicCatalogProvider.SPOTIFY, MusicCatalogKind.TRACK, SPOTIFY_ID,
            "https://open.spotify.com/track/$SPOTIFY_ID")
        assertEquals(expected, musicCatalogRefOf("https://open.spotify.com/intl-de/track/$SPOTIFY_ID?si=code"))
        assertEquals(expected, musicCatalogRefOf("spotify:track:$SPOTIFY_ID"))
        assertEquals(MusicCatalogRef(MusicCatalogProvider.DEEZER, MusicCatalogKind.ALBUM, "302127",
            "https://www.deezer.com/album/302127"), musicCatalogRefOf("https://www.deezer.com/de/album/302127"))
        assertEquals(MusicCatalogKind.ARTIST, musicCatalogRefOf("https://open.spotify.com/artist/$ARTIST_ID")?.kind)
        listOf(
            "http://open.spotify.com/track/$SPOTIFY_ID", "https://open.spotify.com.evil.test/track/$SPOTIFY_ID",
            "https://open.spotify.com/track/not-an-id", "https://spotify.link/short",
            "https://www.deezer.com/track/1/../../evil", "https://deezer.com/track/1@other.test",
            "https://www.deezer.com/track/1?\nHost:example.test"
        ).forEach { assertNull(musicCatalogRefOf(it), it) }
    }

    @Test
    fun spotifyWithoutTokenIsPreviewOnlyAndIncludesImage() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MusicCatalogEngine(HttpClient(MockEngine { request ->
            requests += request
            respond("""{"title":"Example song","thumbnail_url":"https://i.scdn.co/image/example",
              "thumbnail_width":300,"thumbnail_height":300,"html":"<iframe></iframe>"}""")
        }))
        val item = assertNotNull(engine.get(assertNotNull(musicCatalogRefOf("https://open.spotify.com/track/$SPOTIFY_ID")), null))
        assertEquals("Example song", item.title)
        assertFalse(item.fullyEnriched)
        assertEquals(listOf(MusicCatalogImage("https://i.scdn.co/image/example", 300, 300)), item.images)
        assertNull(item.isrc)
        assertEquals("open.spotify.com", requests.single().url.host)
        assertEquals("https://open.spotify.com/track/$SPOTIFY_ID", requests.single().url.parameters["url"])
        assertNull(requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun spotifyAuthenticatedTrackHasAlbumArtworkAndIsrc() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MusicCatalogEngine(HttpClient(MockEngine { request ->
            requests += request
            respond(spotifyTrack)
        }))
        val ref = assertNotNull(musicCatalogRefOf("spotify:track:$SPOTIFY_ID"))
        val item = assertNotNull(engine.get(ref, "test-access-token"))
        assertTrue(item.fullyEnriched)
        assertEquals(212000L, item.durationMs)
        assertEquals("GBARL0000001", item.isrc)
        assertEquals("Example artist", item.artists.single().name)
        assertEquals("Example album", item.album)
        assertEquals("2020-01", item.releaseDate)
        assertEquals(640, item.images.single().width)
        assertEquals("api.spotify.com", requests.single().url.host)
        assertEquals("Bearer test-access-token", requests.single().headers[HttpHeaders.Authorization])
        assertFalse(item.toString().contains("test-access-token"))
    }

    @Test
    fun spotifyArtistImageAndDeezerArtistImages() {
        val spotify = assertNotNull(spotifyItemOf(Json.parseToJsonElement("""
            {"id":"$ARTIST_ID","name":"Example artist","genres":["pop","dance"],
             "followers":{"total":1234},"images":[{"url":"https://i.scdn.co/image/artist","width":320,"height":320}]}
        """), MusicCatalogKind.ARTIST))
        assertEquals("pop", spotify.genres.first())
        assertEquals(1234L, spotify.fanCount)
        assertEquals(320, spotify.images.single().width)
        val deezer = assertNotNull(deezerItemOf(Json.parseToJsonElement("""
            {"id":27,"name":"Example artist","nb_fan":1234,"picture_xl":"https://cdn-images.dzcdn.net/artist.jpg"}
        """), MusicCatalogKind.ARTIST))
        assertNull(deezer.images.single().width)
        assertEquals("Example artist", deezer.artists.single().name)
    }

    @Test
    fun deezerTrackAndFullAlbumShape() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MusicCatalogEngine(HttpClient(MockEngine { request ->
            requests += request
            respond(if (request.url.encodedPath.contains("album")) deezerAlbum else deezerTrack)
        }))
        val track = assertNotNull(engine.get(assertNotNull(musicCatalogRefOf("https://www.deezer.com/track/3135556")), null))
        assertEquals("GBARL0000001", track.isrc)
        assertEquals(212000L, track.durationMs)
        assertEquals(2, track.trackNumber)
        assertEquals(2, track.images.size)
        assertNull(track.artists.single().images.single().width)
        val album = assertNotNull(engine.get(assertNotNull(musicCatalogRefOf("https://www.deezer.com/album/302127")), null))
        assertEquals("2001-03-12", album.releaseDate)
        assertEquals(listOf("Electronic"), album.genres)
        assertEquals("Example label", album.label)
        assertEquals("123456789012", album.upc)
        assertEquals(2, album.tracks.size)
        assertEquals(true, album.tracksComplete)
        assertTrue(requests.all { it.url.host == "api.deezer.com" })
    }

    @Test
    fun searchAndValidationBeforeRequest() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MusicCatalogEngine(HttpClient(MockEngine { request ->
            requests += request
            respond(if (request.url.host == "api.spotify.com") """
                {"tracks":{"items":[$spotifyTrack]}}
            """ else """{"data":[$deezerTrack]}""")
        }))
        assertEquals(1, engine.searchSpotify(" Example ", MusicCatalogKind.TRACK, "token", 5).size)
        assertEquals(1, engine.searchDeezer(" Example ", MusicCatalogKind.TRACK, 5).size)
        assertEquals("Example", requests[0].url.parameters["q"])
        assertEquals("Example", requests[1].url.parameters["q"])
        val before = requests.size
        assertFailsWith<IllegalArgumentException> { engine.searchSpotify("", MusicCatalogKind.TRACK, "token", 5) }
        assertFailsWith<IllegalArgumentException> { engine.searchDeezer("song", MusicCatalogKind.EPISODE, 5) }
        assertFailsWith<IllegalArgumentException> { engine.searchDeezer("song", MusicCatalogKind.TRACK, 101) }
        assertFailsWith<IllegalArgumentException> { engine.searchSpotify("song", MusicCatalogKind.TRACK, "bad\ntoken", 5) }
        assertFailsWith<IllegalArgumentException> {
            engine.get(MusicCatalogRef(MusicCatalogProvider.DEEZER, MusicCatalogKind.TRACK, "1", "https://evil.test/track/1"), null)
        }
        assertEquals(before, requests.size)
    }

    @Test
    fun errorsDoNotExposeRemoteBodyOrToken() = runTest {
        val refused = MusicCatalogEngine(HttpClient(MockEngine { respond("SECRET", HttpStatusCode.TooManyRequests) }))
        val error = assertFailsWith<IllegalStateException> {
            refused.get(assertNotNull(musicCatalogRefOf("spotify:track:$SPOTIFY_ID")), "sensitive-token")
        }
        assertEquals("eXcor: SPOTIFY metadata HTTP 429", error.message)
        val missing = MusicCatalogEngine(HttpClient(MockEngine { respond("""{"error":{"code":800,"message":"No data"}}""") }))
        assertNull(missing.get(assertNotNull(musicCatalogRefOf("https://www.deezer.com/artist/999")), null))
        val quota = MusicCatalogEngine(HttpClient(MockEngine { respond("""{"error":{"code":4,"message":"private message"}}""") }))
        assertEquals("eXcor: DEEZER metadata error 4", assertFailsWith<IllegalStateException> {
            quota.searchDeezer("x", MusicCatalogKind.TRACK, 2)
        }.message)
    }

    @Test
    fun noAudioOrMalformedImagesLeakFromRecords() {
        val input = Json.parseToJsonElement("""{"title":"Example","thumbnail_url":"http://bad.test/photo",
            "thumbnail_width":300,"thumbnail_height":300,"preview":"https://audio.test/file.mp3"}""")
        val ref = assertNotNull(musicCatalogRefOf("spotify:album:4aawyAB9vmqN3uQ7FjRGTy"))
        val record = spotifyEmbedOf(input, ref)
        assertTrue(record.images.isEmpty())
        assertFalse(record.toString().contains("audio.test"))
    }
}
