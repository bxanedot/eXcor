package com.vyonex

import com.vyonex.VyonExResultKind.ALBUM
import com.vyonex.VyonExResultKind.ARTIST
import com.vyonex.VyonExResultKind.CHANNEL
import com.vyonex.VyonExResultKind.PLAYLIST
import com.vyonex.VyonExResultKind.VIDEO
import com.vyonex.internal.Engine
import com.vyonex.internal.musicSearchOf
import com.vyonex.internal.suggestionsOf
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
        // The next page has no filters; the same Shorts are read the same way.
        assertEquals(page.copy(filters = emptyList()), webSearchMoreOf(more.jsonObject))
        // Three Shorts grids, one Short in two of them.
        assertEquals(62, page.shorts.size)
        assertEquals(VyonExSearchResult(VIDEO, "6oHkCfhlDnc", page.shorts[0].title, null, null, null, page.shorts[0].thumbnails), page.shorts[0])
        assertTrue(page.shorts[0].title!!.startsWith("Hits for decades"))
        assertEquals(2, page.shorts[0].thumbnails.size)
        // YouTube's filters, group by group, with the params its own search page sends for each.
        assertEquals(listOf("Type", "Duration", "Upload date", "Features", "Prioritize"), page.filters.mapNotNull { it.group }.distinct())
        assertEquals(VyonExSearchFilter("Type", "Channels", VyonExSearchType.CHANNELS.params, false), page.filters.first { it.label == "Channels" })
        assertEquals(
            VyonExSearchType.entries.map { it.params },
            page.filters.filter { it.group == "Type" }.map { it.params },
        )
        assertEquals(VyonExSearchFilter("Duration", "Under 3 minutes", "EgIYBA%3D%3D", false), page.filters.first { it.group == "Duration" })
        assertEquals(VyonExSearchFilter("Prioritize", "Relevance", null, true), page.filters.first { it.label == "Relevance" })
        assertEquals(1, page.filters.count { it.selected })
    }

    @Test
    fun channelSearchFromCapture() {
        val page = webSearchOf(fixtureJson("search-web-channels.json"))
        assertEquals(20, page.results.size)
        assertTrue(page.results.all { it.kind == CHANNEL && it.id.startsWith("UC") })
        assertEquals(VyonExSearchResult(CHANNEL, "UCuAXFkgsw1L7xaCfnd5JJOw", "Rick Astley", null, null, null, page.results[0].thumbnails), page.results[0])
        assertEquals(listOf(88, 176), page.results[0].thumbnails.map { it.width })
        assertEquals("Rick Astley - Topic", page.results[1].title)
        assertNotNull(page.continuation)
        // With Channels applied: it is selected, durations do not go with it, and the sort order keeps it.
        assertEquals(VyonExSearchFilter("Type", "Channels", null, true), page.filters.first { it.label == "Channels" })
        assertTrue(page.filters.filter { it.group == "Duration" }.all { it.params == null && !it.selected })
        assertEquals("CAMSAhAC", page.filters.first { it.label == "Popularity" }.params)
    }

    @Test
    fun shortsSearchFromCapture() {
        val page = webSearchOf(fixtureJson("search-web-shorts.json"))
        assertEquals(25, page.shorts.size)
        assertEquals(listOf("6oHkCfhlDnc", "5Dj2DppA4aI"), page.shorts.take(2).map { it.id })
        assertTrue(page.shorts.all { it.kind == VIDEO && it.title != null })
        // Besides the grids, one mix (a playlist).
        assertEquals(listOf(PLAYLIST to "RDEMt6MUSQI0t_Ts2EDUDWGRhQ"), page.results.map { it.kind to it.id })
    }

    @Test
    fun suggestionsFromCapture() {
        val text = fixture("suggest-web.json")
        val suggestions = suggestionsOf(text)
        assertEquals(14, suggestions.size)
        assertEquals(listOf("rick astley never gonna give you up", "rick astley", "rick astley together forever"), suggestions.take(3))
        // The JSONP form YouTube's search box gets without xhr=t reads the same.
        assertEquals(suggestions, suggestionsOf("window.google.ac.h($text)"))
        assertEquals(emptyList(), suggestionsOf("""["q",[],{}]"""))
        assertEquals(emptyList(), suggestionsOf("{}"))
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

    @Test
    fun filteredSearchesAndSuggestionsAreLoggedOutCalls() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = Engine(FakeHost().apply { cookie = "SID=secret"; language = "ja"; region = "JP" }, tokenProvider = null, store = null, http = HttpClient(MockEngine { request ->
            requests += request
            val file = if (request.url.host == "suggestqueries-clients6.youtube.com") "suggest-web.json" else "search-web-channels.json"
            respond(fixture(file), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }))
        assertEquals(20, engine.search("rick astley", VyonExSearchType.CHANNELS.params).results.size)
        assertTrue("\"params\":\"EgIQAg%3D%3D\"" in (requests[0].body as TextContent).text)
        assertEquals(14, engine.suggestions("東京 タワー").size)
        val url = requests[1].url
        assertEquals("https://suggestqueries-clients6.youtube.com/complete/search", url.toString().substringBefore('?'))
        assertEquals(
            mapOf("ds" to "yt", "client" to "youtube", "hl" to "ja", "gl" to "jp", "q" to "東京 タワー", "xhr" to "t", "ie" to "utf-8", "oe" to "utf-8"),
            url.parameters.entries().associate { it.key to it.value.single() },
        )
        assertTrue(requests.all { it.headers[HttpHeaders.Cookie] == null })
        // No question, no request.
        assertEquals(emptyList(), engine.suggestions(" "))
        assertEquals(2, requests.size)
    }
}
