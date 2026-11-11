import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class PlaybackDiagnosticsTest {
    @Test
    fun mediaUrlAllowlistRejectsHostAndPathConfusion() {
        assertTrue(isAllowedMediaUrl("https://r1---sn-ab5l6.googlevideo.com/videoplayback?range=1"))
        assertTrue(isAllowedMediaUrl("https://m.youtube.com/videoplayback?token=private"))
        assertFalse(isAllowedMediaUrl("http://r1---sn-ab5l6.googlevideo.com/videoplayback"))
        assertFalse(isAllowedMediaUrl("https://youtube.com.attacker.example/videoplayback"))
        assertFalse(isAllowedMediaUrl("https://googlevideo.com.attacker.example/videoplayback"))
        assertFalse(isAllowedMediaUrl("https://youtube.com/other"))
        assertFalse(isAllowedMediaUrl("https://user@youtube.com/videoplayback"))
        assertFalse(isAllowedMediaUrl("https://youtube.com:444/videoplayback"))
        assertFalse(isAllowedMediaUrl("https://youtube.com/videoplayback/extra"))
        assertFalse(isAllowedMediaUrl("https://youtube.com/videoplayback#fragment"))
    }

    @Test
    fun requestHeadersDropCredentialsVisitorIdsAndControlCharacters() {
        val forwarded = safeRequestHeaders(
            mapOf(
                "User-Agent" to "eXcor playback runner",
                "Accept" to "*/*",
                "Cookie" to "SID=secret-cookie",
                "Authorization" to "Bearer secret-token",
                "X-Goog-Visitor-Id" to "secret-visitor-id",
                "Referer" to "https://youtube.com/watch?v=valid1234567\r\nCookie: injected",
            ),
        )

        assertEquals(forwarded.keys, setOf("User-Agent", "Accept"))
        assertEquals(forwarded["User-Agent"], "eXcor playback runner")
        assertFalse(forwarded.values.any { it.contains("secret") || it.contains("\r") || it.contains("\n") })
    }

    @Test
    fun rangeParserAcceptsSingleByteRangesAndRejectsMalformedOrOverflowingValues() {
        assertTrue(isValidRange("bytes=0-"))
        assertTrue(isValidRange("bytes=15-99"))
        assertTrue(isValidRange("bytes=-1024"))
        assertEquals(parseRangeStart("bytes=15-99"), 15L)
        assertNull(parseRangeStart("bytes=-1024"))

        assertFalse(isValidRange("bytes=1-2,4-5"))
        assertFalse(isValidRange("bytes=99-15"))
        assertFalse(isValidRange("bytes=-0"))
        assertFalse(isValidRange("bytes=9223372036854775808-"))
        assertFalse(isValidRange("bytes=" + "1".repeat(65) + "-"))
    }

    @Test
    fun playabilityClassificationNeverReturnsUnknownRemoteText() {
        assertEquals(classifyPlayabilityStatus("OK"), "OK")
        assertEquals(classifyPlayabilityStatus("LOGIN_REQUIRED"), "LOGIN_REQUIRED")
        assertEquals(classifyPlayabilityStatus("UNRECOGNIZED:https://example.test/?token=secret"), "OTHER")
        assertNull(classifyPlayabilityStatus(null))
    }
}
