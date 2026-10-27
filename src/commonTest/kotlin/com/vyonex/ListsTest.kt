package com.vyonex

import com.vyonex.internal.Engine
import com.vyonex.internal.albumOf
import com.vyonex.internal.artistOf
import com.vyonex.internal.continuationPageOf
import com.vyonex.internal.durationOf
import com.vyonex.internal.playlistOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListsTest {
    @Test
    fun playlistPageFromCapture() {
        val playlist = assertNotNull(playlistOf("PLsyOSbh5bs16vubvKePAQ1x3PhKavfBIl", fixtureJson("browse-playlist.json")))
        assertEquals("2018 Chinese New Singles (11/6 updated)", playlist.title)
        assertEquals("Included the new singles in six months\n(sort by date)", playlist.description)
        assertEquals("LBK" to "UC21nz3_MesPLqtDqwdvnoxA", playlist.ownerName to playlist.ownerChannelId)
        val items = playlist.firstPage.items
        assertEquals(100, items.size)
        assertEquals(
            VyonExListItem(
                "XyNHhf1cklQ", "DIOR大穎 - 欲落未落 Almost,But Still Here - (Official MV）", "DIOR大穎", "UC-affZZext8Q2x63gzLqROg", 260,
                items[0].thumbnails,
            ),
            items[0],
        )
        assertEquals(4, items[0].thumbnails.size)
        // Every video has a duration and a channel name; collaborations take the first credited channel.
        assertTrue(items.all { it.durationSeconds != null && it.channelName != null })
        assertEquals("理想混蛋 Bestards" to "UCZn0sWyHi1OVNfug5l4G3BQ", items.first { it.videoId == "ZoQ1Gu31R5M" }.let { it.channelName to it.channelId })
        assertNotNull(playlist.firstPage.continuation)
    }

    @Test
    fun nextPageAndAGeneratedAlbumPlaylist() {
        val next = continuationPageOf(fixtureJson("browse-playlist-continuation.json"))
        assertEquals(100, next.items.size)
        assertEquals("uub_qJXT3tI" to 230L, next.items[0].videoId to next.items[0].durationSeconds)
        assertNotNull(next.continuation)

        // An album's playlist (OLAK5uy_…): 50 tracks on one page, no owner, no next page.
        val album = assertNotNull(playlistOf("OLAK5uy_l1m0thk3g31NmIIz_vMIbWtyv7eZixlH0", fixtureJson("browse-album-playlist.json")))
        assertEquals("Album - Royalty Free Music Library V2 (50 Songs)", album.title)
        assertNull(album.description)
        assertNull(album.ownerName)
        assertEquals(50, album.firstPage.items.size)
        assertEquals("Royalty Free Music Crew - Topic", album.firstPage.items[0].channelName)
        assertNull(album.firstPage.continuation)
    }

    @Test
    fun youTubeMusicAlbumPage() {
        val album = assertNotNull(albumOf("MPREb_gTAcphH99wE", fixtureJson("browse-album-music.json")))
        assertEquals("Royalty Free Music Library V2 (50 Songs)", album.title)
        assertEquals("Royalty Free Music" to "UC9ALqqC4aIeG5iDs7i90Bfw", album.artist to album.artistChannelId)
        assertEquals(2015, album.year)
        // The same tracks as the album's playlist (captured separately as browse-album-playlist.json).
        assertEquals("OLAK5uy_l1m0thk3g31NmIIz_vMIbWtyv7eZixlH0", album.playlistId)
        assertEquals(4, album.thumbnails.size)
        assertEquals(50, album.tracks.size)
        // Rows without credits of their own are the album artist's.
        assertEquals(
            VyonExListItem("XNEnEBrHws8", "The Chill Zone", "Royalty Free Music", "UC9ALqqC4aIeG5iDs7i90Bfw", 268, emptyList()),
            album.tracks.first(),
        )
        assertEquals("Cq4CADymLKU" to 215L, album.tracks.last().let { it.videoId to it.durationSeconds })
        val playlist = assertNotNull(playlistOf("OLAK5uy_l1m0thk3g31NmIIz_vMIbWtyv7eZixlH0", fixtureJson("browse-album-playlist.json")))
        assertEquals(playlist.firstPage.items.map { it.videoId }, album.tracks.map { it.videoId })
        assertNull(albumOf("MPREb_x", fixtureJson("browse-playlist.json")))
    }

    @Test
    fun youTubeMusicArtistPage() {
        val artist = assertNotNull(artistOf("UCuAXFkgsw1L7xaCfnd5JJOw", fixtureJson("browse-artist-music.json")))
        assertEquals("Rick Astley", artist.name)
        assertTrue(artist.description!!.startsWith("Richard Paul Astley is an English singer"))
        assertEquals(3, artist.thumbnails.size)
        assertEquals(listOf("lYBUbBu4W08", "i_Q88T1HI_w", "3BFTio5296w", "ptiRKQnpx8M", "OFMthc9YkOw"), artist.topSongs.map { it.videoId })
        val song = artist.topSongs.first()
        assertEquals(listOf("Never Gonna Give You Up", "Rick Astley", "UCwZEU0wAwIyZb4x5G_KJp2w"), listOf(song.title, song.channelName, song.channelId))
        assertEquals(2, song.thumbnails.size)
        // All songs and all videos as playlists, told apart by what the shelves link to, not by their titles.
        assertEquals("OLAK5uy_mYtM509QyBQ8g6U9moiAUud3rg6oswhHg", artist.songsPlaylistId)
        assertEquals("OLAK5uy_kTAuc2ZOu5ihiGDql2Ax8X7zSYEe_z8ns", artist.videosPlaylistId)
        // Ten albums then ten singles and EPs; featured-on and user playlists are not releases.
        assertEquals(20, artist.releases.size)
        assertEquals(VyonExRelease("MPREb_Me5NUhDIBWQ", "Are We There Yet?", 2023, artist.releases[0].thumbnails), artist.releases[0])
        assertEquals("MPREb_SslJVn92SLw" to 2026, artist.releases[10].let { it.browseId to it.year })
        assertTrue(artist.releases.all { it.browseId.startsWith("MPREb_") })
        assertNull(artistOf("UCx", fixtureJson("browse-album-music.json")))
    }

    @Test
    fun durations() {
        assertEquals(260L, durationOf("4:20"))
        assertEquals(3723L, durationOf("1:02:03"))
        listOf(null, "LIVE", "SHORTS", "4", "1:2:3:4", "a:b").forEach { assertNull(durationOf(it), it) }
    }

    @Test
    fun requestsAreLoggedOutBrowseCalls() = runTest {
        val host = FakeHost().apply { cookie = "SID=secret; LOGIN_INFO=x"; visitorData = "visitor" }
        val requests = mutableListOf<HttpRequestData>()
        val engine = Engine(host, tokenProvider = null, store = null, http = HttpClient(MockEngine { request ->
            requests += request
            val body = (request.body as TextContent).text
            respond(
                fixture(if ("\"continuation\"" in body) "browse-playlist-continuation.json" else "browse-playlist.json"),
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }))
        val playlist = assertNotNull(engine.playlist("PLsyOSbh5bs16vubvKePAQ1x3PhKavfBIl"))
        engine.playlistMore(playlist.firstPage.continuation!!)
        assertEquals(listOf("/youtubei/v1/browse", "/youtubei/v1/browse"), requests.map { it.url.encodedPath })
        val bodies = requests.map { (it.body as TextContent).text }
        assertTrue("\"browseId\":\"VLPLsyOSbh5bs16vubvKePAQ1x3PhKavfBIl\"" in bodies[0])
        assertTrue("\"continuation\":\"${playlist.firstPage.continuation}\"" in bodies[1])
        // Lists are public ones: the session's cookie is never sent.
        assertTrue(requests.all { it.headers[HttpHeaders.Cookie] == null && it.headers[HttpHeaders.Authorization] == null })
        assertEquals(listOf("visitor", "visitor"), requests.map { it.headers["X-Goog-Visitor-Id"] })
        // A mix is not a playlist page, nor a playlist an album page.
        assertFailsWith<IllegalArgumentException> { engine.playlist("RDdQw4w9WgXcQ") }
        assertFailsWith<IllegalArgumentException> { engine.album("PLsyOSbh5bs16vubvKePAQ1x3PhKavfBIl") }
        assertFailsWith<IllegalArgumentException> { engine.artist("MPREb_gTAcphH99wE") }
        assertEquals(2, requests.size)
    }

    @Test
    fun albumsAskYouTubeMusic() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = Engine(FakeHost().apply { cookie = "SID=secret" }, tokenProvider = null, store = null, http = HttpClient(MockEngine { request ->
            requests += request
            respond(fixture("browse-album-music.json"), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }))
        assertEquals(50, assertNotNull(engine.album("MPREb_gTAcphH99wE")).tracks.size)
        val request = requests.single()
        assertEquals("music.youtube.com/youtubei/v1/browse", request.url.host + request.url.encodedPath)
        val body = (request.body as TextContent).text
        assertTrue("\"clientName\":\"WEB_REMIX\"" in body && "\"browseId\":\"MPREb_gTAcphH99wE\"" in body, body)
        assertEquals("67" to "https://music.youtube.com", request.headers["X-YouTube-Client-Name"] to request.headers[HttpHeaders.Origin])
        assertNull(request.headers[HttpHeaders.Cookie])
    }
}
