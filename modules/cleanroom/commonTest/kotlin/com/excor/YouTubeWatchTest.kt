package com.excor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Synthetic `next` responses in YouTube's shape.
class YouTubeWatchTest {
    private val watch = """
        {"contents":{"twoColumnWatchNextResults":{
          "results":{"results":{"contents":[{"videoPrimaryInfoRenderer":{}},
            {"itemSectionRenderer":{"sectionIdentifier":"comment-item-section","contents":[
              {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"Eg0Scommentstoken"}}}}]}}]}},
          "secondaryResults":{"secondaryResults":{"results":[
            {"lockupViewModel":{"contentId":"AAAAAAAAAAA","contentType":"LOCKUP_CONTENT_TYPE_VIDEO","metadata":{"lockupMetadataViewModel":{"title":{"content":"Related one"}}}}},
            {"lockupViewModel":{"contentId":"RDmix","contentType":"LOCKUP_CONTENT_TYPE_PLAYLIST"}},
            {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"relatedmore"}}}}]}}}}}
    """.trimIndent()

    private val comments = """
        {"onResponseReceivedEndpoints":[
          {"reloadContinuationItemsCommand":{"continuationItems":[{"commentsHeaderRenderer":{"countText":{"runs":[{"text":"2,461,182"},{"text":" Comments"}]},"sortMenu":{"sortFilterSubMenuRenderer":{"subMenuItems":[
            {"title":"Top","selected":true,"serviceEndpoint":{"continuationCommand":{"token":"toptoken"}}},
            {"title":"Neueste","selected":false,"serviceEndpoint":{"continuationCommand":{"token":"newesttoken"}}}]}}}}]}},
          {"reloadContinuationItemsCommand":{"continuationItems":[
            {"commentThreadRenderer":{"commentViewModel":{"commentViewModel":{"commentKey":"k1"}}}},
            {"commentThreadRenderer":{"commentViewModel":{"commentViewModel":{"commentKey":"k2"}}}},
            {"commentThreadRenderer":{"commentViewModel":{"commentViewModel":{"commentKey":"missing"}}}},
            {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"commentsnext"}}}}]}}],
         "frameworkUpdates":{"entityBatchUpdate":{"mutations":[
           {"payload":{"commentEntityPayload":{"key":"k2","properties":{"commentId":"c2","content":{"content":"Second"},"publishedTime":"1 day ago"},
             "author":{"displayName":"@b","channelId":"UCbbbbbbbbbbbbbbbbbbbbbb","isCreator":true},"toolbar":{"likeCountNotliked":"","replyCount":""}}}},
           {"payload":{"commentEntityPayload":{"key":"k1","properties":{"commentId":"c1","content":{"content":"First"},"publishedTime":"6 years ago"},
             "author":{"displayName":"@a","channelId":"UCaaaaaaaaaaaaaaaaaaaaaa","isVerified":true},"toolbar":{"likeCountNotliked":"220K","replyCount":"611"}}}},
           {"payload":{"triStateButtonStateEntityPayload":{"key":"x"}}}]}}}
    """.trimIndent()

    @Test
    fun relatedVideosAndTheCommentsToken() {
        val related = decodeRelated(watch)!!
        assertEquals(listOf("AAAAAAAAAAA"), related.videos.map { it.videoId })
        assertEquals("relatedmore", related.continuation)
        assertEquals("Eg0Scommentstoken", commentsTokenOf(watch))
        assertNull(commentsTokenOf("""{"contents":{"twoColumnWatchNextResults":{"results":{"results":{"contents":[]}}}}}"""))
    }

    @Test
    fun commentsFollowThreadOrderAndSkipUnknownKeys() {
        val page = decodeComments(comments)!!
        assertEquals(listOf("c1", "c2"), page.comments.map { it.commentId })
        val first = page.comments.first()
        assertEquals("First", first.text)
        assertEquals("@a", first.authorName)
        assertTrue(first.authorVerified)
        assertEquals("220K", first.likesText)
        assertEquals(611, first.replyCount)
        assertTrue(page.comments[1].authorIsCreator)
        assertNull(page.comments[1].likesText)
        assertNull(page.comments[1].replyCount)
        assertEquals("2,461,182 Comments", page.totalText)
        assertEquals("commentsnext", page.continuation)
        assertEquals("newesttoken", page.newestContinuation) // by position, whatever the localized title
        assertNull(decodeComments("""{"responseContext":{}}"""))
    }

    @Test
    fun repliesPagesAndSuggestions() {
        val thread = comments.replace("""{"commentThreadRenderer":{"commentViewModel":{"commentViewModel":{"commentKey":"k1"}}}}""",
            """{"commentThreadRenderer":{"commentViewModel":{"commentViewModel":{"commentKey":"k1"}},"replies":{"commentRepliesRenderer":{"contents":[
              {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"repliestoken"}}}}]}}}}""")
        assertEquals("repliestoken", decodeComments(thread)!!.comments.first().repliesContinuation)
        val replies = comments.replace("{\"reloadContinuationItemsCommand\":{\"continuationItems\":[{\"commentsHeaderRenderer\"", "{\"x\":{\"continuationItems\":[{\"commentsHeaderRenderer\"")
            .replace("""{"commentThreadRenderer":{"commentViewModel":{"commentViewModel":{"commentKey":"k2"}}}}""", """{"commentViewModel":{"commentKey":"k2"}}""")
        assertTrue(decodeComments(replies)!!.comments.any { it.commentId == "c2" })
        assertEquals(listOf("daft punk", "daft punk around the world"),
            decodeSuggestions("""window.google.ac.h(["daft pu",[["daft punk",0,[512],{"zao":"Daft Punk"}],["daft punk around the world",0,[512]]],{"k":1}])"""))
        assertEquals(emptyList(), decodeSuggestions("not jsonp"))
    }

    @Test
    fun chaptersFromThePlayerBar() {
        fun chapter(title: String, start: String) = """{"chapterRenderer":{"title":{"simpleText":"$title"},"timeRangeStartMillis":$start}}"""
        val watch = """{"contents":{"twoColumnWatchNextResults":{}},"playerOverlays":{"playerOverlayRenderer":{"decoratedPlayerBarRenderer":{"decoratedPlayerBarRenderer":{"playerBar":
            {"multiMarkersPlayerBarRenderer":{"markersMap":[{"key":"HEATSEEKER","value":{"heatmap":{}}},{"key":"DESCRIPTION_CHAPTERS","value":{"chapters":[
              ${chapter("Finding a product", "235000")},${chapter("Intro", "0")},${chapter("  ", "1000")},${chapter("Bad", "-5")}]}}]}}}}}}}"""
        assertEquals(listOf(ExcorYouTubeChapter("Intro", 0), ExcorYouTubeChapter("Finding a product", 235_000)), decodeChapters(watch))
        assertEquals(emptyList(), decodeChapters("""{"contents":{"twoColumnWatchNextResults":{}}}"""))
        assertNull(decodeChapters("""{"responseContext":{}}"""))
    }

    @Test
    fun songsFromTheMusicCardList() {
        fun list(icon: String, cards: String) = """{"horizontalCardListRenderer":{"header":{"richListHeaderRenderer":{"title":{"simpleText":"Musique"}}},
            "footerButton":{"buttonViewModel":{"iconName":"$icon"}},"cards":[$cards]}}"""
        val song = """{"videoAttributeViewModel":{"title":"Glass River","subtitle":"Mira Vale","secondarySubtitle":{"content":"Tides"},
            "overflowMenuOnTap":{"innertubeCommand":{"confirmDialogEndpoint":{"content":{"confirmDialogRenderer":{"dialogMessages":[{"runs":[
              {"text":"Song","bold":true},{"text":": ","bold":true},{"text":"Glass River"},{"text":"\n\n"},
              {"text":"Writers","bold":true},{"text":": ","bold":true},{"text":"A. Vale, B. Stone"}]}]}}}}}}}"""
        val watch = """{"contents":{"twoColumnWatchNextResults":{}},"engagementPanels":[{"engagementPanelSectionListRenderer":{"content":{"structuredDescriptionContentRenderer":{"items":[
            ${list("PERSON", """{"videoAttributeViewModel":{"title":"Not a song"}}""")},
            ${list("MUSIC", "$song,{\"videoAttributeViewModel\":{\"title\":\" \"}}")}]}}}}]}"""
        val songs = decodeSongs(watch)!!
        val s = songs.single()
        assertEquals("Glass River", s.title)
        assertEquals("Mira Vale", s.artist)
        assertEquals("Tides", s.album)
        assertEquals(mapOf("Song" to "Glass River", "Writers" to "A. Vale, B. Stone"), s.credits)
        val track = s.asMusicTrack("AAAAAAAAAAA", 320_000)
        assertEquals("Tides", track.albumTitle)
        assertEquals(listOf("Mira Vale"), track.artists.map { it.name })
        assertEquals(emptyList(), decodeSongs("""{"contents":{"twoColumnWatchNextResults":{}}}"""))
        assertNull(decodeSongs("""{"responseContext":{}}"""))
    }

    @Test
    fun mixWindowAndItsContinuation() {
        fun item(id: String, index: Int) = """{"playlistPanelVideoRenderer":{"videoId":"$id","title":{"simpleText":"Song $id"},
            "longBylineText":{"runs":[{"text":"Mira Vale","navigationEndpoint":{"browseEndpoint":{"browseId":"UCaaaaaaaaaaaaaaaaaaaaaa"}}}]},
            "lengthText":{"simpleText":"4:02"},"thumbnail":{"thumbnails":[{"url":"https://i.ytimg.com/vi/$id/hq.jpg","width":168,"height":94}]},
            "navigationEndpoint":{"watchEndpoint":{"videoId":"$id","playlistId":"RDAAAAAAAAAAA","index":$index}}}}"""
        fun panel(infinite: Boolean) = """{"contents":{"twoColumnWatchNextResults":{"playlist":{"playlist":{"playlistId":"RDAAAAAAAAAAA","title":"Mix - Glass River",
            "isInfinite":$infinite,"contents":[${item("AAAAAAAAAAA", 0)},${item("BBBBBBBBBBB", 1)},{"playlistPanelVideoRenderer":{"videoId":"bad"}}]}}}}}"""
        val mix = decodeMix(panel(true), "RDAAAAAAAAAAA")!!
        assertEquals("Mix - Glass River", mix.title)
        assertEquals(listOf("AAAAAAAAAAA", "BBBBBBBBBBB"), mix.videos.map { it.videoId })
        assertEquals(242L, mix.videos[1].durationSeconds)
        assertEquals("UCaaaaaaaaaaaaaaaaaaaaaa", mix.videos[1].channelId)
        assertEquals("BBBBBBBBBBB" to 1, mix.nextVideoId to mix.nextIndex)
        val finite = decodeMix(panel(false), "RDAAAAAAAAAAA")!!
        assertNull(finite.nextVideoId)
        assertNull(decodeMix(panel(true), "RDBBBBBBBBBBB"))
        val request = buildWatchPlaylistRequest("BBBBBBBBBBB", "RDAAAAAAAAAAA", 1, ExcorSession(null, null, "", "")).body
        assertTrue("\"playlistId\":\"RDAAAAAAAAAAA\"" in request && "\"index\":1" in request)
    }
}
