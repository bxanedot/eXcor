package com.excor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Synthetic channel browse responses in YouTube's shape.
class YouTubeChannelTest {
    private val home = """
        {"metadata":{"channelMetadataRenderer":{"title":"Mira Vale","description":"Official channel\n","externalId":"UCaaaaaaaaaaaaaaaaaaaaaa",
           "keywords":"\"mira vale\" synth \"glass river\"","isFamilySafe":true,"availableCountryCodes":["FR","DE"],
           "vanityChannelUrl":"http://www.youtube.com/@miravale",
           "avatar":{"thumbnails":[{"url":"https://yt3.googleusercontent.com/a=s900","width":900,"height":900},{"url":"https://evil.example/a.jpg"}]}}},
         "header":{"pageHeaderRenderer":{"content":{"pageHeaderViewModel":{"metadata":{"contentMetadataViewModel":{"metadataRows":[
           {"metadataParts":[{"text":{"content":"@miravale"}}]},{"metadataParts":[{"text":{"content":"1.2M subscribers"}},{"text":{"content":"84 videos"}}]}]}}}}}},
         "contents":{"twoColumnBrowseResultsRenderer":{"tabs":[
           {"tabRenderer":{"title":"Home","selected":true,"endpoint":{"browseEndpoint":{"browseId":"UCaaaaaaaaaaaaaaaaaaaaaa","params":"EghmZWF0dXJlZA%3D%3D"}}}},
           {"tabRenderer":{"title":"Videos","endpoint":{"browseEndpoint":{"browseId":"UCaaaaaaaaaaaaaaaaaaaaaa","params":"EgZ2aWRlb3M%3D"}}}},
           {"expandableTabRenderer":{"title":"Search"}}]}}}
    """.trimIndent()

    private fun item(id: String) = """{"richItemRenderer":{"content":{"lockupViewModel":{"contentId":"$id","contentType":"LOCKUP_CONTENT_TYPE_VIDEO",
        "contentImage":{"thumbnailViewModel":{"overlays":[{"thumbnailBottomOverlayViewModel":{"badges":[{"thumbnailBadgeViewModel":{"text":"2:37"}}]}}]}},
        "metadata":{"lockupMetadataViewModel":{"title":{"content":"Video $id"},"metadata":{"contentMetadataViewModel":{"metadataRows":[
          {"metadataParts":[{"text":{"content":"3.5M views"}},{"text":{"content":"8 months ago"}}]}]}}}}}}}}"""

    @Test
    fun homePageGivesDetailsAndTheVideosTabParams() {
        val home = decodeChannelHome(home, "UCaaaaaaaaaaaaaaaaaaaaaa")!!
        val c = home.channel
        assertEquals("Mira Vale", c.title)
        assertEquals("Official channel", c.description)
        assertEquals("@miravale", c.handle)
        assertEquals("1.2M subscribers", c.subscribersText)
        assertEquals("84 videos", c.videoCountText)
        assertEquals(listOf("mira vale", "synth", "glass river"), c.keywords)
        assertEquals(setOf("FR", "DE"), c.availableCountries)
        assertEquals(1, c.avatars.size)
        assertEquals(listOf("Home", "Videos"), c.tabs)
        assertEquals("EgZ2aWRlb3M%3D", home.videosParams)
        assertNull(decodeChannelHome(this.home, "UCbbbbbbbbbbbbbbbbbbbbbb"))
    }

    @Test
    fun videosTabAndContinuation() {
        val tab = """{"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"title":"Videos","selected":true,"content":{"richGridRenderer":{"contents":[
            ${item("AAAAAAAAAAA")},${item("BBBBBBBBBBB")},
            {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"4qmFtab"}}}}]}}}}]}}}"""
        val (videos, next) = decodeChannelVideos(tab)!!
        assertEquals(listOf("AAAAAAAAAAA", "BBBBBBBBBBB"), videos.map { it.videoId })
        assertEquals(157L, videos.first().durationSeconds)
        assertEquals("3.5M views", videos.first().viewsText)
        assertEquals("8 months ago", videos.first().publishedText)
        assertNull(videos.first().channelId)
        assertEquals("4qmFtab", next)
        val more = """{"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[${item("CCCCCCCCCCC")}]}}]}"""
        val (videos2, next2) = decodeChannelVideos(more)!!
        assertEquals("CCCCCCCCCCC", videos2.single().videoId)
        assertNull(next2)
        assertTrue("\"params\":\"p\"" in buildBrowseRequest("UCaaaaaaaaaaaaaaaaaaaaaa", null, ExcorSession(null, null, "US", "en"), "p").body)
    }
}
