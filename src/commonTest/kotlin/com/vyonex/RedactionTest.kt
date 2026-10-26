package com.vyonex

import com.vyonex.internal.primary.extraction.PoTokenResult
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
        val text = VyonExSession("SID=SECRET-COOKIE", "SECRET-VISITOR", "US", "en").toString()
        assertNoSecrets(text)
        assertEquals("VyonExSession(cookie=present, visitorData=present, region=US, language=en)", text)
    }

    @Test
    fun streamHidesUrlTokenAndCookieHeader() {
        val stream = VyonExStream(
            videoId = "dQw4w9WgXcQ",
            url = "https://r1.googlevideo.com/videoplayback?pot=SECRET-GVS-TOKEN",
            kbps = 128,
            mimeType = "audio/webm; codecs=\"opus\"",
            loudnessDb = -1.5,
            clientName = "WEB",
            profileId = "WEB",
            headers = mapOf("User-Agent" to "ua", "Cookie" to "SID=SECRET-COOKIE"),
        )
        assertNoSecrets(stream.toString())
    }
}
