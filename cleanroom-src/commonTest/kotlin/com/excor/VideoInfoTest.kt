package com.excor

import com.excor.catalog.ExcorCatalogProvider
import com.excor.music.ExcorAvailability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// A synthetic player response in the shape YouTube returns; no captured data.
class VideoInfoTest {
    private val response = """
        {"playabilityStatus":{"status":"UNPLAYABLE","reason":"Video unavailable"},
         "videoDetails":{"videoId":"AAAAAAAAAAA","title":"Mira Vale - Glass River (Official Video) [4K Remaster]","lengthSeconds":"201",
           "keywords":["synth","river"],"channelId":"UCxxxxxxxxxxxxxxxxxxxxxx","viewCount":"12345","author":"Mira Vale","isPrivate":false,
           "isLiveContent":false,"shortDescription":"A synthetic video.",
           "thumbnail":{"thumbnails":[{"url":"https://i.ytimg.com/vi/AAAAAAAAAAA/hqdefault.jpg","width":480,"height":360},{"url":"http://i.ytimg.com/x.jpg"},{"url":"https://evil.example/x.jpg"}]}},
         "microformat":{"playerMicroformatRenderer":{"category":"Music","publishDate":"2024-05-01T00:00:00-07:00","uploadDate":"2024-05-01T00:00:00-07:00",
           "isFamilySafe":true,"isUnlisted":false,"likeCount":"678","ownerProfileUrl":"http://www.youtube.com/@MiraVale","availableCountries":["FR","DE"],"isShortsEligible":false}}}
    """.trimIndent()

    @Test
    fun metadataComesThroughWhenStreamsAreWithheld() {
        val info = decodeVideoInfo(response, "AAAAAAAAAAA")!!
        assertEquals("Mira Vale", info.author)
        assertEquals("@MiraVale", info.channelHandle)
        assertEquals(201L, info.durationSeconds)
        assertEquals(12_345L, info.viewCount)
        assertEquals(678L, info.likeCount)
        assertEquals(listOf("synth", "river"), info.keywords)
        assertEquals("Music", info.category)
        assertEquals(setOf("FR", "DE"), info.availableCountries)
        assertEquals(1, info.thumbnails.size) // http and foreign hosts refused
        assertFalse(info.playable)
        assertEquals("Video unavailable", info.playabilityReason)
        assertNull(decodeVideoInfo(response, "BBBBBBBBBBB")) // a response for another video
        assertNull(decodeVideoInfo("""{"playabilityStatus":{"status":"ERROR"}}""", "AAAAAAAAAAA"))
        assertNull(decodeVideoInfo("not json", "AAAAAAAAAAA"))
    }

    @Test
    fun asMusicTrackDropsVideoTagsAndSplitsArtist() {
        val track = decodeVideoInfo(response, "AAAAAAAAAAA")!!.asMusicTrack()
        assertEquals(ExcorCatalogProvider.YOUTUBE_MUSIC, track.provider)
        assertEquals("Glass River", track.title)
        assertEquals("Mira Vale", track.artists.single().name)
        assertEquals(201_000L, track.durationMs)
        assertEquals("2024-05-01", track.releaseDate)
        assertEquals(ExcorAvailability.UNAVAILABLE, track.availability)

        val topic = decodeVideoInfo(response.replace("\"author\":\"Mira Vale\"", "\"author\":\"Mira Vale - Topic\"")
            .replace("Mira Vale - Glass River (Official Video) [4K Remaster]", "Glass River (Live at Somewhere)"), "AAAAAAAAAAA")!!.asMusicTrack()
        assertEquals("Mira Vale", topic.artists.single().name)
        assertEquals("Glass River (Live at Somewhere)", topic.title) // a real version stays
    }

    @Test
    fun metadataRequestAsksForNoStreams() {
        val request = buildMetadataRequest("AAAAAAAAAAA", ExcorSession(null, "visitor", "GB", "en"))
        assertTrue(request.url.startsWith("https://www.youtube.com/youtubei/v1/player"))
        assertTrue("\"videoId\":\"AAAAAAAAAAA\"" in request.body && "signatureTimestamp" !in request.body && "poToken" !in request.body)
        assertEquals("visitor", request.headers["X-Goog-Visitor-Id"])
        assertFalse("Cookie" in request.headers)
    }

    @Test
    fun watchPageFillsInWhenThePlayerWithholdsDetails() {
        val botChecked = """{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Sign in to confirm you\u2019re not a bot"}}"""
        assertNull(decodeVideoInfo(botChecked, "AAAAAAAAAAA"))
        assertEquals("LOGIN_REQUIRED" to "Sign in to confirm you\u2019re not a bot", playabilityOf(botChecked))
        assertNull(playabilityOf("""{"responseContext":{}}"""))

        fun watch(views: String, live: Boolean = false) = """{"currentVideoEndpoint":{"watchEndpoint":{"videoId":"AAAAAAAAAAA"}},
          "contents":{"twoColumnWatchNextResults":{"results":{"results":{"contents":[
            {"videoPrimaryInfoRenderer":{"title":{"runs":[{"text":"Glass River"}]},"dateText":{"simpleText":"Apr 23, 2005"},
              "viewCount":{"videoViewCountRenderer":{"viewCount":{"simpleText":"$views"},"isLive":$live}}}},
            {"videoSecondaryInfoRenderer":{"owner":{"videoOwnerRenderer":{"title":{"runs":[{"text":"Mira Vale",
              "navigationEndpoint":{"browseEndpoint":{"browseId":"UCaaaaaaaaaaaaaaaaaaaaaa","canonicalBaseUrl":"/@miravale"}}}]}}},
              "attributedDescription":{"content":"Recorded live."}}}]}}}}}"""
        val info = decodeWatchVideoInfo(watch("447,551,399 views"), "AAAAAAAAAAA", playabilityOf(botChecked))!!
        assertEquals("Glass River", info.title)
        assertEquals("Mira Vale", info.author)
        assertEquals("UCaaaaaaaaaaaaaaaaaaaaaa", info.channelId)
        assertEquals("@miravale", info.channelHandle)
        assertEquals(447_551_399L, info.viewCount)
        assertEquals("Recorded live.", info.description)
        assertEquals("Apr 23, 2005", info.publishedText)
        assertNull(info.durationSeconds)
        assertTrue(info.fromWatchPage && !info.playable && info.playabilityStatus == "LOGIN_REQUIRED")
        // A live stream's count is of viewers watching now, not views.
        val live = decodeWatchVideoInfo(watch("1,234 watching now", live = true), "AAAAAAAAAAA", null)!!
        assertTrue(live.isLive && live.viewCount == null)
        assertNull(decodeWatchVideoInfo(watch("1 view"), "BBBBBBBBBBB", null))
    }
}
