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
}
