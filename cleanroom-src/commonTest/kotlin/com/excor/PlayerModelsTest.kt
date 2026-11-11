package com.excor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerModelsTest {
    @Test
    fun playerFormatDiagnosticOmitsUntrustedMimeText() {
        val marker = "synthetic-private-mime-value"
        val format = PlayerFormat(
            url = "https://rr1.googlevideo.com/videoplayback?signature=synthetic-private-signature",
            mimeType = marker,
            bitrate = 128_000,
            averageBitrate = null,
            channels = 2,
            sampleRate = 48_000,
        )

        val rendered = format.toString()
        assertFalse(marker in rendered, "Untrusted MIME text escaped through PlayerFormat.toString")
        assertFalse("googlevideo.com" in rendered)
        assertTrue("mimeType=true" in rendered)
    }

    @Test
    fun watchPageAndCacheEntryStringsOmitUntrustedValues() {
        val secret = "synthetic-player-secret"
        val url = "https://rr1.googlevideo.com/videoplayback?pot=$secret&sig=synthetic-signature"
        val page = WatchPage(
            apiKey = "synthetic-api-key",
            playerResponse = PlayerResponse(
                playabilityStatus = "LOGIN_REQUIRED",
                playabilityReason = "Authorization: Bearer $secret $url",
                formats = listOf(
                    PlayerFormat(url, "audio/webm", 128_000, null, 2, 48_000, signatureCipher = "url=$url&s=$secret"),
                ),
                playerScriptUrl = "https://www.youtube.com/s/player/test.js?key=synthetic-script-key",
                loudnessDb = null,
            ),
        )
        val cacheEntry = PlayerCacheEntry(12345, "synthetic-prepared-player")
        val rendered = page.toString() + cacheEntry.toString()

        listOf(
            secret,
            "synthetic-signature",
            "rr1.googlevideo.com",
            "synthetic-api-key",
            "synthetic-script-key",
            "synthetic-prepared-player",
        ).forEach { assertFalse(it in rendered, "A diagnostic data class exposed an untrusted value") }
    }

    @Test
    fun playerResponseStringOmitsURLsAndCredentialText() {
        val url = "https://rr1.googlevideo.com/videoplayback?pot=secret-stream&sig=secret-signature"
        val response = PlayerResponse(
            playabilityStatus = "LOGIN_REQUIRED",
            playabilityReason = "Authorization: Bearer secret-auth $url",
            formats = listOf(
                PlayerFormat(
                    url = url,
                    mimeType = "audio/webm",
                    bitrate = 128_000,
                    averageBitrate = null,
                    channels = 2,
                    sampleRate = 48_000,
                    signatureCipher = "url=$url&s=secret-cipher",
                ),
            ),
            playerScriptUrl = "https://www.youtube.com/s/player/test.js?key=secret-script",
            loudnessDb = null,
            hlsManifestUrl = "https://manifest.googlevideo.com/api/manifest/hls_variant/secret/file/index.m3u8?pot=secret-stream",
            isLive = true,
        )

        val output = response.toString()
        listOf(
            "rr1.googlevideo.com",
            "secret-stream",
            "secret-signature",
            "secret-auth",
            "secret-cipher",
            "secret-script",
            "manifest.googlevideo.com",
        ).forEach { assertFalse(it in output, "PlayerResponse exposed a sensitive value") }
    }

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
    fun readsHlsManifestAndLiveFlag() {
        val url = "https://manifest.googlevideo.com/api/manifest/hls_variant/test/file/index.m3u8?signature=synthetic"
        val response = assertNotNull(decodePlayerResponse(
            """{"playabilityStatus":{"status":"OK"},"videoDetails":{"isLiveContent":true},"streamingData":{"hlsManifestUrl":"$url"}}""",
        ))

        assertEquals(url, response.hlsManifestUrl)
        assertEquals(true, response.isLive)
        assertFalse(url in response.toString())
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
                """{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Visit https://rr1.googlevideo.com/videoplayback?sig=secret-url Cookie: SID=secret-cookie; visitorData=secret-visitor; X-Goog-Visitor-Id: secret-visitor-id Authorization: Bearer secret-auth token=secret-token"}}""",
            ),
        )
        val safe = assertNotNull(response.playabilityReason)
        listOf("secret-url", "secret-cookie", "secret-visitor", "secret-visitor-id", "secret-auth", "secret-token").forEach {
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
