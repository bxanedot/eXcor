package com.vyonex

import com.vyonex.internal.Engine
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
        // A mix is not a playlist page.
        assertFailsWith<IllegalArgumentException> { engine.playlist("RDdQw4w9WgXcQ") }
        assertEquals(2, requests.size)
    }
}
