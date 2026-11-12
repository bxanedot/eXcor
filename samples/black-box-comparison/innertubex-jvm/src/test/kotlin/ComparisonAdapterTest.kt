import com.metrolist.innertubex.InnerTubeHttpException
import com.metrolist.innertubex.extraction.StreamDiagnostics
import com.metrolist.innertubex.extraction.StreamResolveException
import io.ktor.http.HttpStatusCode
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
    fun parsesNormalizedComparisonOptions() {
        val options = parseOptions(validArgs())
        assertEquals(options.videoId, "dQw4w9WgXcQ")
        assertEquals(options.phase, "cold")
        assertEquals(options.timeoutSeconds, 30)
        assertFalse(options.rangeProbes)
        assertEquals(options.locale, "en-US")
    }

    @Test
    fun acceptsOptInRangeProbesWithoutCallingThemPlaybackSeeks() {
        val options = parseOptions(validArgs() + "--range-probes")
        assertTrue(options.rangeProbes)
        val json = ComparisonSample(options, "PASS", null).toJson()
        assertTrue(json.contains("\"rangeProbeMethod\":\"NOT_TESTED\""))
        assertTrue(json.contains("\"forwardSeek\":\"NOT_TESTED\""))
    }

    @Test
    fun rejectsIdentifierAndTimeoutProblemsWithoutEchoingInput() {
        val badId = validArgs().toMutableList().also { it[1] = "https://media.example/private?token=secret" }
        val idError = try {
            parseOptions(badId)
            throw AssertionError("invalid video ID was accepted")
        } catch (failure: RuntimeException) {
            failure
        }
        assertFalse(idError.message.orEmpty().contains("media.example"))
        assertFalse(idError.message.orEmpty().contains("secret"))

        val timeoutError = try {
            parseOptions(validArgs() + listOf("--timeout-seconds", "60"))
            throw AssertionError("over-limit timeout was accepted")
        } catch (failure: RuntimeException) {
            failure
        }
        assertEquals(timeoutError.message, "invalid option value")
    }

    @Test
    fun emitsNormalizedJsonWithoutMediaUrlOrCredentials() {
        val options = parseOptions(validArgs())
        val sample = ComparisonSample(
            options = options,
            outcome = "PASS",
            failureClass = null,
            extractMs = 12.5,
            firstMediaByteMs = 20.0,
            requestCount = 2,
            mediaRequestCount = 1,
            bytesRead = 1024,
        )
        val json = sample.toJson()
        assertTrue(json.startsWith("{"))
        assertTrue(json.endsWith("}"))
        assertFalse(json.contains('\n'))
        assertTrue(json.contains("\"adapter\":\"innertubex\""))
        assertTrue(json.contains("\"lane\":\"direct_audio\""))
        assertTrue(json.contains("\"backwardSeek\":\"NOT_TESTED\""))
        assertTrue(json.contains("\"decode\":\"NOT_TESTED\""))
        assertFalse(json.contains("videoplayback"))
        assertFalse(json.contains("token="))
        assertFalse(json.contains("Authorization"))
    }

    @Test
    fun mediaUrlMustUseAnApprovedHttpsHostAndPath() {
        assertTrue(mediaUrlAllowedForTest("https://r1---sn-ab5l6.googlevideo.com/videoplayback?token=private"))
        assertFalse(mediaUrlAllowedForTest("http://r1---sn-ab5l6.googlevideo.com/videoplayback"))
        assertFalse(mediaUrlAllowedForTest("https://googlevideo.com.attacker.example/videoplayback"))
        assertFalse(mediaUrlAllowedForTest("https://user@googlevideo.com/videoplayback"))
        assertFalse(mediaUrlAllowedForTest("https://googlevideo.com:444/videoplayback"))
        assertFalse(mediaUrlAllowedForTest("https://googlevideo.com/other"))
    }

    @Test
    fun mediaHeadersDropCookiesAuthorizationAndUnsafeValues() {
        val forwarded = mediaHeadersForTest(
            mapOf(
                "User-Agent" to "comparison adapter",
                "Cookie" to "SID=private-cookie",
                "Authorization" to "Bearer private-token",
                "Referer" to "https://youtube.com/watch\r\nCookie: injected",
                "Accept-Language" to "en-US",
            ),
        )
        assertEquals(forwarded.keys, setOf("User-Agent", "Accept-Language"))
        assertFalse(forwarded.values.any { it.contains("private") || it.contains('\r') || it.contains('\n') })
    }

    @Test
    fun boundedRangeResponseRequiresConsistentPartialContent() {
        assertTrue(rangeResponseValidForTest(206, "bytes 0-65535/100000", 65_536))
        assertTrue(rangeResponseValidForTest(206, "bytes 0-99/*", 100))
        assertTrue(rangeResponseValidForTest(200, null, 1024))
        assertFalse(rangeResponseValidForTest(206, "bytes 0-99/1000", 99))
        assertFalse(rangeResponseValidForTest(206, "bytes 0-65536/100000", 65_537))
        assertFalse(rangeResponseValidForTest(206, null, 1024))
    }

    @Test
    fun offsetRangeProbeRequiresTheRequestedBytesAndReportsHttpOnlyResults() {
        assertEquals(offsetRangeResponseResultForTest(206, "bytes 75000-79095/100000", 4096, 75000, 100000), "PASS")
        assertEquals(offsetRangeResponseResultForTest(200, null, 4096, 75000, 100000), "IGNORED")
        assertEquals(offsetRangeResponseResultForTest(403, null, 0, 75000, 100000), "ACCESS_DENIED")
        assertEquals(offsetRangeResponseResultForTest(206, "bytes 75000-79095/99999", 4096, 75000, 100000), "ERROR")
        assertEquals(offsetRangeResponseResultForTest(206, "bytes 75000-79095/100000", 4095, 75000, 100000), "ERROR")
        assertEquals(offsetRangeResponseResultForTest(206, "bytes 3000-7095/10000", 4096, 3000, 10000), "UNAVAILABLE")
    }

    @Test
    fun directAudioLaneDoesNotCountSabrOrUnsafeStreamsAsPasses() {
        assertEquals(
            directStreamOutcomeForTest("https://r1.googlevideo.com/videoplayback", true, "audio/webm"),
            "UNSUPPORTED",
        )
        assertEquals(directStreamOutcomeForTest("", false, "audio/webm"), "UNSUPPORTED")
        assertEquals(directStreamOutcomeForTest("http://example.com/videoplayback", false, "audio/webm"), "FAIL")
        assertEquals(directStreamOutcomeForTest("https://r1.googlevideo.com/videoplayback", false, "audio/webm"), null)
    }

    @Test
    fun classifiesAccessDenialsWithoutExposingExceptionMessages() {
        val secret = "https://googlevideo.com/videoplayback?token=secret"
        val ageRestricted = StreamResolveException(
            StreamResolveException.Reason.AGE_RESTRICTED,
            secret,
            IllegalStateException(secret),
            StreamDiagnostics(emptyList(), false),
        )
        assertEquals(classifyErrorForTest(ageRestricted), "BLOCKED")

        val forbidden = InnerTubeHttpException(secret, HttpStatusCode.Forbidden)
        val networkFailure = StreamResolveException(
            StreamResolveException.Reason.NETWORK,
            secret,
            forbidden,
            StreamDiagnostics(emptyList(), false),
        )
        assertEquals(classifyErrorForTest(networkFailure), "BLOCKED")
        assertFalse(ComparisonSample(parseOptions(validArgs()), "BLOCKED", "ACCESS_DENIED").toJson().contains("secret"))
    }

    @Test
    fun jsonStringEscapesControlsAndQuotes() {
        assertEquals(jsonString("a\"b\\c\n"), "\"a\\\"b\\\\c\\n\"")
        assertEquals(jsonString(null), "null")
    }
}
