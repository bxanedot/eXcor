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
        assertEquals(options.rangeProbes, false)
        assertTrue(options.os in setOf("windows", "macos", "linux"))
    }

    @Test
    fun acceptsOptInRangeProbesWithoutTreatingThemAsPlayerSeeks() {
        val options = parseOptions(validArgs() + "--range-probes")
        assertTrue(options.rangeProbes)
        val json = ComparisonSample(options, "PASS", null).toJson()
        assertTrue(json.contains("\"rangeProbeMethod\":\"NOT_TESTED\""))
        assertTrue(json.contains("\"backwardSeek\":\"NOT_TESTED\""))
    }

    @Test
    fun fullVideoDecodeUsesTheDirectVideoReportLane() {
        val options = runCatching {
            parseOptions(validArgs() + listOf("--full-video-decode", "--decoder-version", "ffmpeg-7.1"))
        }
        assertTrue(options.isSuccess, "full-video decoding should be accepted by the paired adapter")
        val json = ComparisonSample(options.getOrThrow(), "PASS", null).toJson()
        assertTrue(json.contains("\"lane\":\"direct_video\""))
        assertTrue(json.contains("\"decoderVersion\":\"ffmpeg-7.1\""))
        assertEquals(FfmpegMediaDecoder.arguments(true).dropWhile { it != "-map" }.take(2), listOf("-map", "0:v:0"))
        assertEquals(FfmpegMediaDecoder.arguments(false).dropWhile { it != "-map" }.take(2), listOf("-map", "0:a:0"))
        assertTrue(parseOptions(validArgs() + listOf("--range-probes", "--full-video-decode")).rangeProbes)
    }

    @Test
    fun fullVideoDecodeCannotOverlapFullAudioDecodeOrPlayerSeek() {
        val videoDecode = validArgs() + "--full-video-decode"
        val options = runCatching { parseOptions(videoDecode) }
        assertTrue(options.isSuccess, "full-video decoding should be accepted by the adapter")
        assertFalse(runCatching { parseOptions(videoDecode + listOf("--full-decode")) }.isSuccess)
        assertFalse(runCatching { parseOptions(videoDecode + listOf("--player-seek-video")) }.isSuccess)
        assertFalse(runCatching { parseOptions(videoDecode + listOf("--player-seek")) }.isSuccess)
    }

    @Test
    fun acceptsExplicitPlayerSeekMode() {
        val parsed = parseOptions(validArgs() + "--player-seek")
        assertTrue(parsed.playerSeek)
        val video = parseOptions(validArgs() + "--player-seek-video")
        assertTrue(video.playerSeekVideo)
        assertFalse(runCatching { parseOptions(validArgs() + listOf("--player-seek", "--player-seek")) }.isSuccess)
        assertFalse(runCatching { parseOptions(validArgs() + listOf("--player-seek", "--player-seek-video")) }.isSuccess)
        assertFalse(runCatching { parseOptions(validArgs() + listOf("--player-seek", "--full-decode")) }.isSuccess)
        assertFalse(runCatching { parseOptions(validArgs() + listOf("--player-seek-video", "--full-decode")) }.isSuccess)
    }

    @Test
    fun sharedPlayerPassesOnlyTheLoopbackAddressAndRejectsUntrustedHosts() {
        val proxy = LoopbackMediaProxy(
            PlaybackMediaSource(
                "https://r1---sn-ab5l6.googlevideo.com/videoplayback?token=signed",
                mapOf("Cookie" to "private", "User-Agent" to "eXcor comparison"),
            ),
        )
        try {
            val audioArgs = ffplayArguments(proxy.localUrl)
            val videoArgs = ffplayArguments(proxy.localUrl, showVideo = true)
            assertTrue(proxy.localUrl.startsWith("http://127.0.0.1:"))
            assertFalse(audioArgs.any { "googlevideo" in it || "token=" in it })
            assertEquals(videoArgs[videoArgs.indexOf("-showmode") + 1], "0")
            assertEquals(audioArgs[audioArgs.indexOf("-showmode") + 1], "1")
        } finally {
            proxy.close()
        }

        val rejected = runCatching {
            LoopbackMediaProxy(PlaybackMediaSource("https://example.test/videoplayback", emptyMap()))
        }
        assertTrue(rejected.isFailure)
    }

    @Test
    fun fullMediaRangeMustCoverTheKnownObjectFromByteZero() {
        assertTrue(HttpRangeProbe.fullResponseIsComplete(200, null, 100))
        assertTrue(HttpRangeProbe.fullResponseIsComplete(206, "bytes 0-99/100", 100))
        assertFalse(HttpRangeProbe.fullResponseIsComplete(206, "bytes 0-98/100", 100))
        assertFalse(HttpRangeProbe.fullResponseIsComplete(206, "bytes 1-100/100", 100))
        assertFalse(HttpRangeProbe.fullResponseIsComplete(206, "bytes 0-99/101", 100))
        assertFalse(HttpRangeProbe.fullResponseIsComplete(403, null, 100))
        assertFalse(HttpRangeProbe.fullResponseIsComplete(206, "bytes 0-0/1", 0))
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
    fun mapsMissingStreamToMetadataPlayabilityFallback() {
        assertEquals(missingStreamOutcome("UNPLAYABLE").status, "BLOCKED")
        assertEquals(missingStreamOutcome("OK").status, "FAIL")
    }

    @Test
    fun classifiesOnlyExplicitAccessChallengesAsBlocked() {
        val botCheck = classifyThrownErrorForTest(IllegalStateException("eXcor sign in to confirm you're not a bot"))
        assertEquals(botCheck.status, "BLOCKED")
        assertEquals(botCheck.failureClass, "ACCESS_DENIED")
        val ageCheck = classifyThrownErrorForTest(IllegalStateException("eXcor sign in to confirm your age"))
        assertEquals(ageCheck.status, "BLOCKED")
        assertEquals(classifyThrownErrorForTest(IllegalStateException("eXcor: page was unavailable")).status, "FAIL")
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

    @Test
    fun offsetRangeProbeRequiresTheRequestedBytesAndReportsHttpOnlyResults() {
        assertEquals(HttpRangeProbe.forwardStart(100_000), 75_000L)
        assertEquals(HttpRangeProbe.backwardStart(100_000), 25_000L)
        assertTrue(HttpRangeProbe.isAvailable(75_000, 100_000))
        assertEquals(HttpRangeProbe.totalBytes(200, null, 100_000), 100_000L)
        assertEquals(HttpRangeProbe.totalBytes(206, "bytes 0-4095/100000", 4096), 100_000L)
        assertEquals(HttpRangeProbe.totalBytes(206, "bytes 0-4095/*", 4096), null)
        assertEquals(HttpRangeProbe.classifyResponse(206, "bytes 75000-79095/100000", 4096, 75000, 100000), "PASS")
        assertEquals(HttpRangeProbe.classifyResponse(200, null, 4096, 75000, 100000), "IGNORED")
        assertEquals(HttpRangeProbe.classifyResponse(403, null, 0, 75000, 100000), "ACCESS_DENIED")
        assertEquals(HttpRangeProbe.classifyResponse(206, "bytes 75000-79095/99999", 4096, 75000, 100000), "ERROR")
        assertEquals(HttpRangeProbe.classifyResponse(206, "bytes 75000-79095/100000", 4095, 75000, 100000), "ERROR")
        assertEquals(HttpRangeProbe.classifyResponse(206, "bytes 3000-7095/10000", 4096, 3000, 10000), "UNAVAILABLE")
    }
}
