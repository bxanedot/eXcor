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
           {"tabRenderer":{"title":"Home","selected":true,"endpoint":{"commandMetadata":{"webCommandMetadata":{"url":"/@miravale/featured"}},"browseEndpoint":{"browseId":"UCaaaaaaaaaaaaaaaaaaaaaa","params":"EghmZWF0dXJlZA%3D%3D"}}}},
           {"tabRenderer":{"title":"Vidéos","endpoint":{"commandMetadata":{"webCommandMetadata":{"url":"/@miravale/videos"}},"browseEndpoint":{"browseId":"UCaaaaaaaaaaaaaaaaaaaaaa","params":"EgZ2aWRlb3M%3D"}}}},
           {"tabRenderer":{"title":"Shorts","endpoint":{"commandMetadata":{"webCommandMetadata":{"url":"/@miravale/shorts"}},"browseEndpoint":{"browseId":"UCaaaaaaaaaaaaaaaaaaaaaa","params":"EgZzaG9ydHM%3D"}}}},
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
        assertEquals(listOf("Home", "Vidéos", "Shorts"), c.tabs)
        // Matched by address, so a localized title ("Vidéos") still finds the Videos tab.
        assertEquals("EgZ2aWRlb3M%3D", home.tabParams[ExcorYouTubeChannelTab.VIDEOS])
        assertEquals("EgZzaG9ydHM%3D", home.tabParams[ExcorYouTubeChannelTab.SHORTS])
        assertNull(home.tabParams[ExcorYouTubeChannelTab.LIVE])
        assertNull(decodeChannelHome(this.home, "UCbbbbbbbbbbbbbbbbbbbbbb"))
    }

    @Test
    fun videosTabAndContinuation() {
        val tab = """{"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"title":"Videos","selected":true,"content":{"richGridRenderer":{"contents":[
            ${item("AAAAAAAAAAA")},${item("BBBBBBBBBBB")},
            {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"4qmFtab"}}}}]}}}}]}}}"""
        val page = decodeChannelTab(tab)!!
        val videos = page.videos
        val next = page.continuation
        assertEquals(listOf("AAAAAAAAAAA", "BBBBBBBBBBB"), videos.map { it.videoId })
        assertEquals(157L, videos.first().durationSeconds)
        assertEquals("3.5M views", videos.first().viewsText)
        assertEquals("8 months ago", videos.first().publishedText)
        assertNull(videos.first().channelId)
        assertEquals("4qmFtab", next)
        val more = """{"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[${item("CCCCCCCCCCC")}]}}]}"""
        val page2 = decodeChannelTab(more)!!
        val videos2 = page2.videos
        val next2 = page2.continuation
        assertEquals("CCCCCCCCCCC", videos2.single().videoId)
        assertNull(next2)
        assertTrue("\"params\":\"p\"" in buildBrowseRequest("UCaaaaaaaaaaaaaaaaaaaaaa", null, ExcorSession(null, null, "US", "en"), "p").body)
    }

    @Test
    fun shortsAndPlaylistsTabs() {
        val shorts = """{"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"selected":true,"content":{"richGridRenderer":{"contents":[
            {"richItemRenderer":{"content":{"shortsLockupViewModel":{"onTap":{"innertubeCommand":{"reelWatchEndpoint":{"videoId":"SSSSSSSSSSS",
              "thumbnail":{"thumbnails":[{"url":"https://i.ytimg.com/vi/SSSSSSSSSSS/frame0.jpg","width":1080,"height":1920}]}}}},
              "overlayMetadata":{"primaryText":{"content":"A short"},"secondaryText":{"content":"216K views"}}}}}}]}}}}]}}}"""
        val s = decodeChannelTab(shorts)!!.videos.single()
        assertEquals("SSSSSSSSSSS", s.videoId)
        assertEquals("A short", s.title)
        assertEquals("216K views", s.viewsText)
        assertNull(s.durationSeconds)
        assertEquals(1, s.thumbnails.size)
        val playlists = """{"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"selected":true,"content":{"sectionListRenderer":{"contents":[
            {"itemSectionRenderer":{"contents":[{"gridRenderer":{"items":[
              {"lockupViewModel":{"contentId":"PLsyntheticlist","contentType":"LOCKUP_CONTENT_TYPE_PLAYLIST","metadata":{"lockupMetadataViewModel":{"title":{"content":"Remixes"}}}}},
              {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"plmore"}}}}]}}]}}]}}}}]}}}"""
        val page = decodeChannelTab(playlists)!!
        assertEquals(listOf("PLsyntheticlist"), page.playlists.map { it.playlistId })
        assertTrue(page.videos.isEmpty())
        assertEquals("plmore", page.continuation)

        val live = """{"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"selected":true,"content":{"richGridRenderer":{"contents":[
            {"richItemRenderer":{"content":{"lockupViewModel":{"contentId":"LLLLLLLLLLL","contentType":"LOCKUP_CONTENT_TYPE_VIDEO",
              "contentImage":{"thumbnailViewModel":{"overlays":[{"thumbnailBottomOverlayViewModel":{"badges":[{"thumbnailBadgeViewModel":{"text":"EN DIRECT","badgeStyle":"THUMBNAIL_OVERLAY_BADGE_STYLE_LIVE"}}]}}]}},
              "metadata":{"lockupMetadataViewModel":{"title":{"content":"Radio"},"metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[{"text":{"content":"2K watching"}}]}]}}}}}}}},
            ${item("AAAAAAAAAAA")}]}}}}]}}}"""
        val (stream, upload) = decodeChannelTab(live)!!.videos
        assertTrue(stream.isLive && stream.durationSeconds == null && stream.viewsText == "2K watching")
        assertTrue(!upload.isLive && upload.durationSeconds == 157L)
    }

    @Test
    fun aboutPanel() {
        val withPanel = home.replace(
            "\"pageHeaderViewModel\":{",
            "\"pageHeaderViewModel\":{\"description\":{\"descriptionPreviewViewModel\":{\"rendererContext\":{\"commandContext\":{\"onTap\":{\"innertubeCommand\":" +
                "{\"showEngagementPanelEndpoint\":{\"engagementPanel\":{\"content\":{\"sectionListRenderer\":{\"contents\":[{\"itemSectionRenderer\":{\"contents\":[" +
                "{\"continuationItemRenderer\":{\"continuationEndpoint\":{\"continuationCommand\":{\"token\":\"4qmFsgJgAbout\"}}}}]}}]}}}}}}}}}},",
        )
        assertEquals("4qmFsgJgAbout", decodeChannelHome(withPanel, "UCaaaaaaaaaaaaaaaaaaaaaa")!!.aboutToken)
        assertNull(decodeChannelHome(home, "UCaaaaaaaaaaaaaaaaaaaaaa")!!.aboutToken)

        fun link(title: String, url: String) = """{"channelExternalLinkViewModel":{"title":{"content":"$title"},"link":{"content":"x","commandRuns":[{"onTap":{"innertubeCommand":{"urlEndpoint":{"url":"$url"}}}}]}}}"""
        val about = """{"onResponseReceivedEndpoints":[{"appendContinuationItemsAction":{"continuationItems":[{"aboutChannelRenderer":{"metadata":{"aboutChannelViewModel":{
            "channelId":"UCaaaaaaaaaaaaaaaaaaaaaa","description":"Official channel\n","subscriberCountText":"1.2M subscribers","videoCountText":"84 videos",
            "viewCountText":"6,066,806,347 views","joinedDateText":{"content":"Joined Sep 10, 2007"},"country":"France","links":[
              ${link("Site", "https://www.youtube.com/redirect?event=channel_description&redir_token=SECRET&q=https%3A%2F%2Fmiravale.example%2Ftour")},
              ${link("Subscribe", "https://www.youtube.com/user/miravale?sub_confirmation=1")},
              ${link("Script", "https://www.youtube.com/redirect?q=javascript%3Aalert(1)")},
              ${link("Empty", "https://www.youtube.com/redirect?event=x")}]}}}}]}}]}"""
        val panel = decodeChannelAbout(about, "UCaaaaaaaaaaaaaaaaaaaaaa")!!
        assertEquals("Official channel", panel.description)
        assertEquals(6_066_806_347L, panel.viewCount)
        assertEquals("Joined Sep 10, 2007", panel.joinedText)
        assertEquals("France", panel.country)
        assertEquals(
            listOf(ExcorYouTubeChannelLink("Site", "https://miravale.example/tour"), ExcorYouTubeChannelLink("Subscribe", "https://www.youtube.com/user/miravale?sub_confirmation=1")),
            panel.links,
        )
        assertTrue(panel.links.none { "SECRET" in it.url })
        assertNull(decodeChannelAbout(about, "UCbbbbbbbbbbbbbbbbbbbbbb"))
        assertNull(decodeChannelAbout("""{"responseContext":{}}""", "UCaaaaaaaaaaaaaaaaaaaaaa"))
    }
}
