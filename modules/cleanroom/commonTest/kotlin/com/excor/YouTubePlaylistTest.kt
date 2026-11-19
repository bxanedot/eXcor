package com.excor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Synthetic browse responses in YouTube's shape.
class YouTubePlaylistTest {
    private fun lockup(id: String, title: String, duration: String?) = """
        {"lockupViewModel":{"contentId":"$id","contentType":"LOCKUP_CONTENT_TYPE_VIDEO",
          "contentImage":{"thumbnailViewModel":{"image":{"sources":[{"url":"https://i.ytimg.com/vi/$id/hq.jpg","width":168,"height":94},{"url":"https://evil.example/x.jpg"}]},
            "overlays":[{"thumbnailBottomOverlayViewModel":{"badges":[{"thumbnailBadgeViewModel":{"text":"${duration ?: "LIVE"}"}}]}}]}},
          "metadata":{"lockupMetadataViewModel":{"title":{"content":"$title"},"metadata":{"contentMetadataViewModel":{"metadataRows":[
            {"metadataParts":[{"text":{"content":"Mira Vale","commandRuns":[{"onTap":{"innertubeCommand":{"browseEndpoint":{"browseId":"UCaaaaaaaaaaaaaaaaaaaaaa"}}}}]}}]},
            {"metadataParts":[{"text":{"content":"773M views"}},{"text":{"content":"2 years ago"}}]}]}}}}}}
    """.trimIndent()

    private val first = """
        {"header":{"pageHeaderRenderer":{"pageTitle":"Synthetic Hits","content":{"pageHeaderViewModel":{"metadata":{"contentMetadataViewModel":{"metadataRows":[
            {"metadataParts":[{"avatarStack":{"avatarStackViewModel":{"text":{"content":"by Synthetic Music","commandRuns":[{"onTap":{"innertubeCommand":{"browseEndpoint":{"browseId":"UCbbbbbbbbbbbbbbbbbbbbbb"}}}}]}}}}]}]}}}}}},
         "microformat":{"microformatDataRenderer":{"title":"Synthetic Hits","description":"Made up."}},
         "sidebar":{"playlistSidebarRenderer":{"items":[{"playlistSidebarPrimaryInfoRenderer":{"stats":[
            {"runs":[{"text":"182"},{"text":" videos"}]},{"simpleText":"1,045,675,306 views"},{"runs":[{"text":"Last updated on "},{"text":"Jul 17, 2026"}]}]}}]}},
         "contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
            {"itemSectionRenderer":{"contents":[${lockup("AAAAAAAAAAA", "Glass River", "3:55")},${lockup("BBBBBBBBBBB", "Live Now", null)},
              {"lockupViewModel":{"contentId":"PLnested","contentType":"LOCKUP_CONTENT_TYPE_PLAYLIST"}}]}},
            {"continuationItemViewModel":{"continuationCommand":{"innertubeCommand":{"continuationCommand":{"token":"4qmFsynthetic"}}}}}]}}}}]}}}
    """.trimIndent()

    @Test
    fun firstPageGivesDetailsVideosAndContinuation() {
        val page = decodePlaylistPage(first, "PLsynthetic")!!
        assertEquals("Synthetic Hits", page.title)
        assertEquals("Made up.", page.description)
        assertEquals("Synthetic Music", page.ownerName)
        assertEquals("UCbbbbbbbbbbbbbbbbbbbbbb", page.ownerChannelId)
        assertEquals(182, page.videoCount)
        assertEquals(1_045_675_306L, page.viewCount)
        assertEquals("Last updated on Jul 17, 2026", page.lastUpdatedText)
        assertEquals(listOf("AAAAAAAAAAA", "BBBBBBBBBBB"), page.videos.map { it.videoId })
        val v = page.videos.first()
        assertEquals(235L, v.durationSeconds)
        assertEquals("UCaaaaaaaaaaaaaaaaaaaaaa", v.channelId)
        assertEquals("773M views", v.viewsText)
        assertEquals("2 years ago", v.publishedText)
        assertEquals(1, v.thumbnails.size)
        assertNull(page.videos[1].durationSeconds)
        assertEquals("4qmFsynthetic", page.continuation)
    }

    @Test
    fun continuationPageAndRequests() {
        val next = """{"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[${lockup("CCCCCCCCCCC", "Next", "1:00")}]}}]}"""
        val page = decodePlaylistPage(next, "PLsynthetic")!!
        assertEquals("CCCCCCCCCCC", page.videos.single().videoId)
        assertNull(page.title)
        assertNull(page.continuation)
        assertNull(decodePlaylistPage("""{"responseContext":{}}""", "PLsynthetic"))
        assertTrue("\"browseId\":\"VLPLsynthetic\"" in buildBrowseRequest("VLPLsynthetic", null, ExcorSession(null, null, "US", "en")).body)
        assertTrue("\"continuation\":\"tok\"" in buildBrowseRequest(null, "tok", ExcorSession(null, null, "US", "en")).body)
    }
}
