package com.vyonex

import com.vyonex.internal.AppleMusicEngine
import com.vyonex.internal.appleAlbumWithSongsOf
import com.vyonex.internal.appleArtistOf
import com.vyonex.internal.appleMusicRefOf
import com.vyonex.internal.appleSongOf
import com.vyonex.internal.artistImagesOf
import com.vyonex.internal.appleArtworkOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun results(file: String): List<JsonElement> = fixtureJson(file)["results"] as JsonArray

class AppleMusicTest {
    @Test
    fun aSongFromCapture() {
        val song = assertNotNull(appleSongOf(results("apple-song.json").single()))
        assertEquals(
            listOf<Any?>(1773293184L, "Never Gonna Give You Up", "Rick Astley", 669771L, "The Best Of Me: Never Edition", 1773292758L, 213960L, 2, 38, 1, 1),
            listOf(song.id, song.title, song.artist, song.artistId, song.album, song.albumId, song.durationMs, song.trackNumber, song.trackCount, song.discNumber, song.discCount),
        )
        assertEquals(listOf<Any?>("1987-01-01T12:00:00Z", "Pop", false), listOf(song.releaseDate, song.genre, song.isExplicit))
        assertEquals("https://music.apple.com/us/album/never-gonna-give-you-up/1773292758?i=1773293184", song.url)
        // The cover at 100 px and the larger sizes made from its URL.
        assertEquals(listOf(100, 600, 1200, 3000), song.artwork.map { it.width })
        assertTrue(song.artwork.all { it.url.startsWith("https://is1-ssl.mzstatic.com/image/thumb/") && it.url.endsWith("/${it.width}x${it.width}bb.jpg") })
        // Metadata only: the capture's song preview is gone, and nothing here carries one.
        assertFalse("audio" in song.toString())
    }

    @Test
    fun anAlbumWithItsSongsInOrder() {
        val album = assertNotNull(appleAlbumWithSongsOf(results("apple-album.json")))
        assertEquals(listOf<Any?>(1773292758L, "The Best Of Me: Never Edition", "Rick Astley", 38), listOf(album.id, album.title, album.artist, album.trackCount))
        assertEquals("℗ 2024 BMG Rights Management (UK) Limited", album.copyright)
        assertEquals("https://music.apple.com/us/album/the-best-of-me-never-edition/1773292758", album.url)
        assertEquals(38, album.songs.size)
        assertEquals((1..38).toList(), album.songs.map { it.trackNumber })
        assertEquals("Never Gonna Give You Up", album.songs[1].title)
        assertEquals(4, album.artwork.size)
    }

    @Test
    fun anArtistWithAlbums() {
        val artist = assertNotNull(appleArtistOf(results("apple-artist.json")))
        assertEquals(listOf<Any?>(669771L, "Rick Astley", "Pop"), listOf(artist.id, artist.name, artist.genre))
        assertEquals(5, artist.albums.size)
        assertTrue(artist.albums.all { it.songs.isEmpty() })
        // Apple lists the albums an artist appears on, also under another artist's name.
        assertEquals(listOf(476671L, 669771L, 669771L, 669771L, 669771L), artist.albums.map { it.artistId })
    }

    @Test
    fun searchResultsAreSongs() {
        val songs = results("apple-search.json").mapNotNull(::appleSongOf)
        assertEquals(5, songs.size)
        assertTrue(songs.all { it.artist != null && it.durationMs != null })
    }

    @Test
    fun links() {
        assertEquals(AppleMusicRef.Song(1773293184, "us"), appleMusicRefOf("https://music.apple.com/us/album/never-gonna-give-you-up/1773292758?i=1773293184&uo=4"))
        assertEquals(AppleMusicRef.Album(1773292758, "gb"), appleMusicRefOf("https://music.apple.com/gb/album/the-best-of-me-never-edition/1773292758"))
        assertEquals(AppleMusicRef.Album(1773292758, "us"), appleMusicRefOf("https://music.apple.com/us/album/1773292758"))
        assertEquals(AppleMusicRef.Song(1773293184, "de"), appleMusicRefOf("https://music.apple.com/de/song/never-gonna-give-you-up/1773293184"))
        assertEquals(AppleMusicRef.Artist(669771, "us"), appleMusicRefOf(" https://geo.music.apple.com/us/artist/rick-astley/669771 "))
        listOf(
            "https://music.apple.com/us/playlist/todays-hits/pl.f4d106fed2bd41149aaacabb233eb5eb",
            "http://music.apple.com/us/album/x/1", "https://music.apple.com.evil.example/us/album/x/1",
            "https://music.apple.com/usa/album/x/1", "https://itunes.apple.com/lookup?id=1",
        ).forEach { assertNull(appleMusicRefOf(it), it) }
    }

    @Test
    fun artworkWithoutTheSizeFormStaysAsGiven() {
        val odd = Json.parseToJsonElement("""{"artworkUrl100":"https://is1-ssl.mzstatic.com/image/cover.jpg"}""")
        assertEquals(listOf(VyonExThumbnail("https://is1-ssl.mzstatic.com/image/cover.jpg", 100, 100)), appleArtworkOf(odd))
        assertEquals(emptyList(), appleArtworkOf(Json.parseToJsonElement("{}")))
    }

    private fun engine(requests: MutableList<HttpRequestData>, status: HttpStatusCode = HttpStatusCode.OK) =
        AppleMusicEngine(FakeHost().apply { cookie = "SID=secret"; region = "GB" }, HttpClient(MockEngine { request ->
            requests += request
            val file = when {
                request.url.encodedPath == "/search" -> "apple-search.json"
                request.url.parameters["entity"] == "song" -> "apple-album.json"
                request.url.parameters["entity"] == "album" -> "apple-artist.json"
                else -> "apple-song.json"
            }
            respond(fixture(file), status, headersOf(HttpHeaders.ContentType, "text/javascript; charset=utf-8"))
        }))

    @Test
    fun callsAsAppleDocumentsThem() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = engine(requests)
        assertEquals("Never Gonna Give You Up", engine.song(1773293184, "US")?.title)
        assertEquals(38, engine.album(1773292758, null)?.songs?.size)
        assertEquals(5, engine.artist(669771, "us", 5)?.albums?.size)
        assertEquals(5, engine.searchSongs(" never gonna give you up ", null, 5).size)
        assertEquals(
            listOf(
                mapOf("id" to "1773293184", "country" to "us"),
                mapOf("id" to "1773292758", "entity" to "song", "country" to "gb"),
                mapOf("id" to "669771", "entity" to "album", "limit" to "5", "country" to "us"),
                mapOf("term" to "never gonna give you up", "media" to "music", "entity" to "song", "limit" to "5", "country" to "gb"),
            ),
            requests.map { r -> r.url.parameters.entries().associate { it.key to it.value.single() } },
        )
        assertTrue(requests.all { it.url.host == "itunes.apple.com" && it.headers[HttpHeaders.Cookie] == null })
    }

    @Test
    fun badArgumentsAndRefusals() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = engine(requests)
        assertFailsWith<IllegalArgumentException> { engine.song(0, null) }
        assertFailsWith<IllegalArgumentException> { engine.song(1, "usa") }
        assertFailsWith<IllegalArgumentException> { engine.searchSongs(" ", null, 5) }
        assertFailsWith<IllegalArgumentException> { engine.searchSongs("x", null, 201) }
        assertFailsWith<IllegalArgumentException> { engine.artist(1, null, 0) }
        assertEquals(0, requests.size)
        // Apple's rate limit answers 403: thrown with the status, no URL.
        val e = assertFailsWith<IllegalStateException> { engine(mutableListOf(), HttpStatusCode.Forbidden).song(1, null) }
        assertEquals("VyonEx: Apple's Search API HTTP 403", e.message)
        // An ID Apple has nothing for is null.
        val empty = AppleMusicEngine(FakeHost(), HttpClient(MockEngine { respond("""{"resultCount":0,"results":[]}""") }))
        assertNull(empty.song(1, null))
        assertNull(empty.album(1, null))
        assertNull(empty.artist(1, null, 5))
    }

    @Test
    fun artistImagesFromThePagesShareImage() {
        val images = artistImagesOf(fixture("apple-artist-page-head.html"))
        assertEquals(listOf(1200 to 630, 600 to 600, 1200 to 1200, 3000 to 3000), images.map { it.width to it.height })
        assertTrue(images[0].url.endsWith("/1200x630cw.png"))
        assertTrue(images.drop(1).all { it.url.endsWith("/${it.width}x${it.width}cc.jpg") })
        assertTrue(images.all { it.url.startsWith("https://is1-ssl.mzstatic.com/image/thumb/AMCArtistImages") })
        // Only Apple's image server is taken; a page without a share image has no pictures.
        assertEquals(emptyList(), artistImagesOf("""<meta property="og:image" content="https://evil.example/image/thumb/a/1200x630cw.png">"""))
        assertEquals(emptyList(), artistImagesOf("""<head><title>x</title></head>"""))
    }

    @Test
    fun artistImagesReadOnlyThePagesHead() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        // The head, then a large body that must not be needed.
        val page = fixture("apple-artist-page-head.html") + "<body>" + "x".repeat(400_000) + "</body>"
        val engine = AppleMusicEngine(FakeHost(), HttpClient(MockEngine { request ->
            requests += request
            if (request.url.encodedPath.endsWith("/1")) respond("", HttpStatusCode.NotFound) else respond(page)
        }))
        assertEquals(4, engine.artistImages(669771, "us").size)
        assertEquals("https://music.apple.com/us/artist/669771", requests.single().url.toString())
        assertNull(requests.single().headers[HttpHeaders.Cookie])
        // An artist Apple has no page for has no pictures.
        assertEquals(emptyList(), engine.artistImages(1, "us"))
        assertFailsWith<IllegalArgumentException> { engine.artistImages(0, "us") }
    }
}
