package com.vyonex

import com.vyonex.VyonExResultKind.ALBUM
import com.vyonex.VyonExResultKind.ARTIST
import com.vyonex.VyonExResultKind.PLAYLIST
import com.vyonex.VyonExResultKind.VIDEO
import com.vyonex.internal.Engine
import com.vyonex.internal.musicSearchOf
import com.vyonex.internal.webSearchMoreOf
import com.vyonex.internal.webSearchOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchTest {
    @Test
    fun youTubeSearchFromCapture() {
        val page = webSearchOf(fixtureJson("search-web.json"))
        // 16 videos and 3 playlists; shelves ("Latest from …"), Shorts grids and the channel card are left out.
        assertEquals(19, page.results.size)
        assertEquals(16, page.results.count { it.kind == VIDEO })
        assertEquals(
            VyonExSearchResult(
                VIDEO, "5SZYz7lZRRI", "Rick Astley - Never Gonna Give You Up (Official Music Video) (1987)", "Super Top Songs",
                "UCuRJJMKpFIdyAsBVaWfK7BQ", 213, page.results[0].thumbnails,
            ),
            page.results[0],
        )
        assertEquals(2, page.results[0].thumbnails.size)
        val playlist = page.results.first { it.kind == PLAYLIST }
        assertEquals(
            listOf("PL2MI040U_GXobmpXtTwBF7oHBGT5BETSD", "RickAstley - Greatest Hits", "RickAstleyVEVO", "UC38IQsAvIsxxjztdMZQtwHA"),
            listOf(playlist.id, playlist.title, playlist.channelName, playlist.channelId),
        )
        assertEquals(2, playlist.thumbnails.size)
        val token = assertNotNull(page.continuation)
        // The next page comes under onResponseReceivedCommands (yt-dlp; not captured): built here from the captured sections.
        val sections = fixtureJson("search-web.json")["contents"]!!.jsonObject["twoColumnSearchResultsRenderer"]!!.jsonObject["primaryContents"]!!
            .jsonObject["sectionListRenderer"]!!.jsonObject["contents"]
        val more = Json.parseToJsonElement("""{"onResponseReceivedCommands":[{"appendContinuationItemsAction":{"continuationItems":$sections}}]}""")
        assertEquals(VyonExSearchPage(page.results, token), webSearchMoreOf(more.jsonObject))
    }

    @Test
    fun youTubeMusicSearchFromCapture() {
        val page = musicSearchOf(fixtureJson("search-music.json"))
        // The top result card, then rows of every kind but profiles and podcasts, told apart by their endpoints.
        assertEquals(27, page.results.size)
        assertEquals(mapOf(VIDEO to 15, PLAYLIST to 6, ARTIST to 3, ALBUM to 3), page.results.groupingBy { it.kind }.eachCount())
        assertEquals(VyonExSearchResult(VIDEO, "dQw4w9WgXcQ", "Never Gonna Give You Up", "Rick Astley", "UCwZEU0wAwIyZb4x5G_KJp2w", 214, page.results[0].thumbnails), page.results[0])
        val song = page.results.first { it.id == "lYBUbBu4W08" }
        assertEquals(listOf<Any?>("Rick Astley", "UCwZEU0wAwIyZb4x5G_KJp2w", null), listOf(song.channelName, song.channelId, song.durationSeconds))
        val artist = page.results.first { it.kind == ARTIST }
        assertEquals(listOf<Any?>("UCwZEU0wAwIyZb4x5G_KJp2w", "Rick Astley", null), listOf(artist.id, artist.title, artist.channelName))
        assertEquals(listOf("MPREb_bFKC3lKP9qh", "MPREb_6LzcG8wF8Gq", "MPREb_71pLy1ThWIq"), page.results.filter { it.kind == ALBUM }.map { it.id })
        assertTrue(page.results.filter { it.kind == PLAYLIST }.none { it.id.startsWith("VL") })
        assertNull(page.continuation)
    }

    @Test
    fun searchesAreLoggedOutCalls() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = Engine(FakeHost().apply { cookie = "SID=secret" }, tokenProvider = null, store = null, http = HttpClient(MockEngine { request ->
            requests += request
            val music = request.url.host == "music.youtube.com"
            respond(fixture(if (music) "search-music.json" else "search-web.json"), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }))
        assertEquals(19, engine.search("rick astley").results.size)
        assertEquals(27, engine.searchMusic("never gonna give you up").results.size)
        assertEquals(
            listOf("www.youtube.com/youtubei/v1/search", "music.youtube.com/youtubei/v1/search"),
            requests.map { it.url.host + it.url.encodedPath },
        )
        val bodies = requests.map { (it.body as TextContent).text }
        assertTrue("\"query\":\"rick astley\"" in bodies[0] && "\"clientName\":\"WEB\"" in bodies[0], bodies[0])
        assertTrue("\"query\":\"never gonna give you up\"" in bodies[1] && "\"clientName\":\"WEB_REMIX\"" in bodies[1], bodies[1])
        assertTrue(requests.all { it.headers[HttpHeaders.Cookie] == null })
        assertFailsWith<IllegalArgumentException> { engine.search("  ") }
        assertEquals(2, requests.size)
    }
}
