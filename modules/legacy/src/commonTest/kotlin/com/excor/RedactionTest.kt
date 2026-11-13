package com.excor

import com.excor.internal.primary.extraction.PoTokenResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RedactionTest {
    private val secrets = listOf("SECRET-COOKIE", "SECRET-VISITOR", "SECRET-PLAYER-TOKEN", "SECRET-GVS-TOKEN")

    private fun assertNoSecrets(text: String) = secrets.forEach { assertFalse(it in text, text) }

    @Test
    fun poTokenResultPrintsOnlyPresence() {
        val text = PoTokenResult("SECRET-PLAYER-TOKEN", "", "SECRET-VISITOR").toString()
        assertNoSecrets(text)
        assertEquals("PoTokenResult(playerRequestToken=present, streamingDataToken=missing, visitorData=present)", text)
    }

    @Test
    fun sessionHidesCookieAndVisitorData() {
        val text = ExcorSession("SID=SECRET-COOKIE", "SECRET-VISITOR", "US", "en").toString()
        assertNoSecrets(text)
        assertEquals("ExcorSession(cookie=present, visitorData=present, region=US, language=en)", text)
    }

    @Test
    fun streamHidesUrlTokenAndCookieHeader() = assertNoSecrets(stream().toString())

    @Test
    fun videoStreamHidesUrlTokenAndCookieHeader() {
        val video = ExcorVideoStream(
            videoId = "dQw4w9WgXcQ",
            url = "https://r1.googlevideo.com/videoplayback?pot=SECRET-GVS-TOKEN&itag=137",
            width = 1920, height = 1080, fps = 25, kbps = 4334,
            mimeType = "video/mp4; codecs=\"avc1.640028\"",
            audio = stream(),
            clientName = "WEB",
            profileId = "WEB",
            headers = mapOf("User-Agent" to "ua", "Cookie" to "SID=SECRET-COOKIE"),
        )
        assertNoSecrets(video.toString())
    }

    @Test
    fun captionTrackHidesSignedUrl() =
        assertNoSecrets(ExcorCaptionTrack("https://www.youtube.com/api/timedtext?signature=SECRET-GVS-TOKEN", "en", "English", false).toString())

    private fun stream() = ExcorStream(
        videoId = "dQw4w9WgXcQ",
        url = "https://r1.googlevideo.com/videoplayback?pot=SECRET-GVS-TOKEN",
        kbps = 128,
        mimeType = "audio/webm; codecs=\"opus\"",
        loudnessDb = -1.5,
        clientName = "WEB",
        profileId = "WEB",
        headers = mapOf("User-Agent" to "ua", "Cookie" to "SID=SECRET-COOKIE"),
    )
}
