import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class ComparisonAdapterTest {
    private fun validArgs() = listOf(
        "--video-id", "dQw4w9WgXcQ",
        "--comparison-id", "run-20261111-home-01",
        "--main-commit", "a".repeat(40),
        "--run-date-utc", "2026-11-11T18:30:00Z",
        "--network-category", "home",
        "--content-category", "music_video",
        "--phase", "cold",
        "--sample-index", "1",
    )

    @Test
    fun parsesOnlyNormalizedComparisonOptions() {
        val options = parseOptions(validArgs())
        assertEquals(options.videoId, "dQw4w9WgXcQ")
        assertEquals(options.phase, "cold")
        assertEquals(options.timeoutSeconds, 30)
        assertTrue(options.os in setOf("windows", "macos", "linux"))
    }

    @Test
    fun rejectsInvalidIdentifiersWithoutEchoingThem() {
        val args = validArgs().toMutableList()
        args[1] = "https://video.example/private?token=secret"
        val error = try {
            parseOptions(args)
            throw AssertionError("invalid video ID was accepted")
        } catch (failure: RuntimeException) {
            failure
        }
        assertFalse(error.message.orEmpty().contains("video.example"))
        assertFalse(error.message.orEmpty().contains("secret"))
    }

    @Test
    fun rejectsTimeoutsAboveTheSdkRequestLimit() {
        val args = validArgs() + listOf("--timeout-seconds", "60")
        val error = try {
            parseOptions(args)
            throw AssertionError("over-limit timeout was accepted")
        } catch (failure: RuntimeException) {
            failure
        }
        assertEquals(error.message, "invalid option value")
    }

    @Test
    fun emitsOneSchemaShapedLineWithoutMediaUrls() {
        val sample = runSampleForTest(parseOptions(validArgs()))
        val json = sample.toJson()
        assertTrue(json.startsWith("{"))
        assertTrue(json.endsWith("}"))
        assertFalse(json.contains('\n'))
        assertTrue(json.contains("\"adapter\":\"excor\""))
        assertTrue(json.contains("\"backwardSeek\":\"NOT_TESTED\""))
        assertTrue(json.contains("\"decode\":\"NOT_TESTED\""))
        assertFalse(json.contains("videoplayback"))
        assertFalse(json.contains("token="))
    }

    @Test
    fun jsonStringEscapesControlCharactersAndQuotes() {
        assertEquals(jsonString("a\"b\\c\n"), "\"a\\\"b\\\\c\\n\"")
        assertEquals(jsonString(null), "null")
    }

    @Test
    fun mediaProbeUrlMustStayOnValidatedHttpsHostsAndPath() {
        assertTrue(mediaUrlAllowedForTest("https://r1---sn-ab5l6.googlevideo.com/videoplayback?token=private"))
        assertFalse(mediaUrlAllowedForTest("http://r1---sn-ab5l6.googlevideo.com/videoplayback"))
        assertFalse(mediaUrlAllowedForTest("https://googlevideo.com.attacker.example/videoplayback"))
        assertFalse(mediaUrlAllowedForTest("https://user@googlevideo.com/videoplayback"))
        assertFalse(mediaUrlAllowedForTest("https://googlevideo.com/other"))
    }

    @Test
    fun mediaProbeDropsCookiesAuthorizationAndHeaderInjection() {
        val forwarded = mediaHeadersForTest(
            mapOf(
                "User-Agent" to "comparison adapter",
                "Cookie" to "SID=private-cookie",
                "Authorization" to "Bearer private-token",
                "Referer" to "https://youtube.com/watch\r\nCookie: injected",
            ),
        )
        assertEquals(forwarded.keys, setOf("User-Agent"))
        assertFalse(forwarded.values.any { it.contains("private") || it.contains('\r') || it.contains('\n') })
    }
}
