package com.excor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Synthetic search responses in YouTube's shape.
class YouTubeSearchTest {
    private val first = """
        {"estimatedResults":"1234","contents":{"twoColumnSearchResultsRenderer":{"primaryContents":{"sectionListRenderer":{"contents":[
          {"itemSectionRenderer":{"contents":[
            {"channelRenderer":{"channelId":"UCaaaaaaaaaaaaaaaaaaaaaa","title":{"simpleText":"Mira Vale"},"subscriberCountText":{"simpleText":"@miravale"},
              "videoCountText":{"simpleText":"1.2M subscribers"},"thumbnail":{"thumbnails":[{"url":"//yt3.ggpht.com/a=s88","width":88,"height":88}]}}},
            {"videoRenderer":{"videoId":"AAAAAAAAAAA","title":{"runs":[{"text":"Glass "},{"text":"River"}]},
              "ownerText":{"runs":[{"text":"Mira Vale","navigationEndpoint":{"browseEndpoint":{"browseId":"UCaaaaaaaaaaaaaaaaaaaaaa"}}}]},
              "lengthText":{"simpleText":"1:02:03"},"viewCountText":{"simpleText":"909,721,464 views"},"publishedTimeText":{"simpleText":"2 years ago"},
              "ownerBadges":[{"metadataBadgeRenderer":{"tooltip":"Official Artist Channel"}}],
              "thumbnail":{"thumbnails":[{"url":"https://i.ytimg.com/vi/AAAAAAAAAAA/hq.jpg","width":360,"height":202},{"url":"https://evil.example/x.jpg"}]}}},
            {"videoRenderer":{"videoId":"BBBBBBBBBBB","title":{"runs":[{"text":"Live now"}]},"viewCountText":{"runs":[{"text":"1,234"},{"text":" watching"}]},
              "badges":[{"metadataBadgeRenderer":{"label":"LIVE"}}]}},
            {"lockupViewModel":{"contentId":"RDsynthetic","contentType":"LOCKUP_CONTENT_TYPE_PLAYLIST","metadata":{"lockupMetadataViewModel":{"title":{"content":"Mix - Mira Vale"}}}}},
            {"lockupViewModel":{"contentId":"AAAAAAAAAAA","contentType":"LOCKUP_CONTENT_TYPE_VIDEO"}},
            {"gridShelfViewModel":{}}
          ]}},
          {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"EpwDEhBTeW50aGV0aWM","request":"CONTINUATION_REQUEST_TYPE_SEARCH"}}}}
        ]}}}}}
    """.trimIndent()

    @Test
    fun firstPageGivesVideosChannelsPlaylistsAndAContinuation() {
        val page = decodeSearchPage(first)!!
        val video = page.videos.first()
        assertEquals("Glass River", video.title)
        assertEquals("UCaaaaaaaaaaaaaaaaaaaaaa", video.channelId)
        assertEquals(3_723L, video.durationSeconds)
        assertEquals(909_721_464L, video.viewCount)
        assertTrue(video.officialArtistChannel)
        assertEquals(1, video.thumbnails.size)
        val live = page.videos[1]
        assertTrue(live.isLive)
        assertNull(live.viewCount)
        assertNull(live.durationSeconds)
        val channel = page.channels.single()
        assertEquals("@miravale", channel.handle)
        assertEquals("1.2M subscribers", channel.subscribersText)
        assertEquals("https://yt3.ggpht.com/a=s88", channel.thumbnails.single().url)
        assertEquals(listOf(true), page.playlists.map { it.isMix })
        assertEquals("EpwDEhBTeW50aGV0aWM", page.continuation)
        assertEquals(1_234L, page.estimatedResults)
    }

    @Test
    fun continuationPagesAndBadInput() {
        val next = """{"onResponseReceivedCommands":[{"appendContinuationItemsAction":{"continuationItems":[
            {"itemSectionRenderer":{"contents":[{"videoRenderer":{"videoId":"CCCCCCCCCCC","title":{"simpleText":"Next"}}}]}}]}}]}"""
        val page = decodeSearchPage(next)!!
        assertEquals("CCCCCCCCCCC", page.videos.single().videoId)
        assertNull(page.continuation)
        assertNull(decodeSearchPage("""{"responseContext":{}}"""))
        assertNull(clockSeconds("LIVE"))
        assertEquals(340L, clockSeconds("5:40"))
        val request = buildSearchRequest("glass river", null, ExcorSession(null, null, "GB", "en"))
        assertTrue("\"query\":\"glass river\"" in request.body && "continuation" !in request.body)
        assertTrue("\"continuation\":\"tok\"" in buildSearchRequest("", "tok", ExcorSession(null, null, "", "")).body)
    }
}
