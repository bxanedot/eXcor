package com.excor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlayerModelsTest {
    @Test
    fun malformedResponseReturnsNoFormats() {
        assertNull(decodePlayerResponse("{"))
        assertNull(decodePlayerResponse("[]"))
    }

    @Test
    fun readsScriptStatusAndAudioFormats() {
        val response = decodePlayerResponse(
            """{"playabilityStatus":{"status":"OK"},"assets":{"js":"/s/player/test/base.js"},"streamingData":{"formats":[{"url":"https://rr1.googlevideo.com/videoplayback?itag=140","mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":128000}],"adaptiveFormats":[{"url":"https://rr1.googlevideo.com/videoplayback?itag=251","mimeType":"audio/webm; codecs=\"opus\"","averageBitrate":96000,"audioChannels":2,"audioSampleRate":"48000"}]}}""",
        )

        assertNotNull(response)
        assertEquals("OK", response.playabilityStatus)
        assertEquals("/s/player/test/base.js", response.playerScriptUrl)
        assertEquals(2, response.formats.size)
        assertEquals(96, response.formats.last().kbps)
        assertEquals(2, response.formats.last().channels)
        assertEquals(48_000, response.formats.last().sampleRate)
    }

    @Test
    fun readsAndBoundsUnplayableReason() {
        val response = decodePlayerResponse(
            """{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Sign in to confirm your age"}}""",
        )

        assertEquals("LOGIN_REQUIRED", response?.playabilityStatus)
        assertEquals("Sign in to confirm your age", response?.playabilityReason)
    }
    @Test
    fun redactsUrlsAndCredentialsInUntrustedPlayabilityText() {
        val response = assertNotNull(
            decodePlayerResponse(
                """{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Visit https://rr1.googlevideo.com/videoplayback?sig=secret-url Cookie: SID=secret-cookie; visitorData=secret-visitor Authorization: Bearer secret-auth token=secret-token"}}""",
            ),
        )
        val safe = assertNotNull(response.playabilityReason)
        listOf("secret-url", "secret-cookie", "secret-visitor", "secret-auth", "secret-token").forEach {
            assertFalse(it in safe, "A playability reason exposed an untrusted value")
        }
        assertEquals("LOGIN_REQUIRED", response.playabilityStatus)
    }

    @Test
    fun boundsAndRemovesControlsFromPlayerDiagnostics() {
        val reason = "x".repeat(250)
        val status = "S".repeat(100)
        val response = assertNotNull(
            decodePlayerResponse("""{"playabilityStatus":{"status":"$status","reason":"$reason"}}"""),
        )
        assertEquals("S".repeat(64), response.playabilityStatus)
        assertEquals("x".repeat(200), response.playabilityReason)
        assertEquals("beforeafter", safePlaybackReason("before\r\nafter"))
    }

}
