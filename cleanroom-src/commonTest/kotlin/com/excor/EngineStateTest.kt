package com.excor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineStateTest {
    @Test
    fun refusalForgetsUrlAndExpiresAfterTenMinutes() = runTestEngine {
        val first = engine.extract(VIDEO_ID, 128, SKIP, false, false)
        assertNotNull(first)
        assertEquals(VIDEO_ID, engine.onRefused(first.url))
        assertNull(engine.headersFor(first.url))

        assertNull(engine.extract(VIDEO_ID, 128, SKIP, false, false))
        assertEquals(1, requests.player)

        host.now = 10 * 60 * 1000L + 1
        assertNotNull(engine.extract(VIDEO_ID, 128, SKIP, false, false))
        assertEquals(2, requests.player)
    }

    @Test
    fun sessionChangeForgetsRememberedStreamsAndExclusions() = runTestEngine {
        val first = engine.extract(VIDEO_ID, 128, SKIP, false, false)
        assertNotNull(first)
        engine.exclude(VIDEO_ID, "VISIONOS")
        engine.onSessionChanged()

        assertNull(engine.headersFor(first.url))
        assertNotNull(engine.extract(VIDEO_ID, 128, SKIP, false, false))
        assertEquals(2, requests.player)
    }

    @Test
    fun theSixtyFifthStreamClearsTheRememberedSet() = runTestEngine {
        var firstUrl: String? = null
        var lastUrl: String? = null
        repeat(65) { index ->
            val stream = engine.extract(VIDEO_ID, 128, SKIP, false, false)
            assertNotNull(stream)
            if (index == 0) firstUrl = stream.url
            lastUrl = stream.url
        }

        assertNull(engine.headersFor(firstUrl!!))
        assertNotNull(engine.headersFor(lastUrl!!))
    }

    @Test
    fun watchPageReturnsEveryAllowedAudioCandidate() = runTestEngine {
        val candidates = engine.audioCandidates(VIDEO_ID)

        assertEquals(1, candidates.size)
        assertEquals(127, candidates.single().kbps)
        assertEquals(ExcorAudioFormat.M4A, candidates.single().format)
        assertEquals(0, requests.player)
    }

    @Test
    fun iosFallbackUsesWatchPageHeadersAndProfile() = runTestEngine {
        val fallback = engine.extract(
            VIDEO_ID,
            128,
            setOf("TVHTML5", "VISIONOS"),
            requireM4a = false,
            watchFallback = true,
        )

        assertNotNull(fallback)
        assertEquals("WEB", fallback.clientName)
        assertEquals("WEB_WATCH_PAGE", fallback.profileId)
        assertEquals("en-US,en;q=0.9", fallback.headers["Accept-Language"])
        assertEquals(0, requests.player)
    }

    private fun runTestEngine(block: suspend TestEngine.() -> Unit) = kotlinx.coroutines.test.runTest {
        val host = FakeHost()
        val requests = RequestCounts()
        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/watch" -> respond(
                    fixture("watch-page.html"), HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
                )
                "/youtubei/v1/player" -> {
                    requests.player++
                    val fixture = fixture("player-response.json")
                    val unique = fixture.replace("range=0-1023", "range=0-${requests.player}")
                    respond(unique, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
                "/videoplayback" -> respond("", HttpStatusCode.PartialContent)
                else -> respond("", HttpStatusCode.NotFound)
            }
        })
        val testEngine = TestEngine(
            engine = ExcorEngine(host, null, client),
            host = host,
            requests = requests,
        )
        try {
            testEngine.block()
        } finally {
            client.close()
        }
    }

    @Test
    fun aChannelTabThatFailsToLoadIsNotReportedAsEmpty() = runTest {
        val channelId = "UCaaaaaaaaaaaaaaaaaaaaaa"
        val home = """{"metadata":{"channelMetadataRenderer":{"title":"Mira Vale","externalId":"$channelId"}},
            "contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"title":"Playlists","endpoint":{
              "commandMetadata":{"webCommandMetadata":{"url":"/@miravale/playlists"}},"browseEndpoint":{"browseId":"$channelId","params":"EglwbGF5bGlzdHM%3D"}}}}]}}}"""
        val client = HttpClient(MockEngine { request ->
            val body = (request.body as TextContent).text
            if ("\"params\"" in body) respond("", HttpStatusCode.ServiceUnavailable)
            else respond(home, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val engine = ExcorEngine(FakeHost(), null, client)
            // Retried, then reported as a server failure rather than an empty tab.
            assertEquals(ExcorRequestFailure.SERVER_ERROR, assertFailsWith<ExcorRequestException> { engine.channel(channelId, null, ExcorYouTubeChannelTab.PLAYLISTS) }.failure)
            // A tab the channel does not have is an empty list, not a failure.
            assertEquals(emptyList(), assertNotNull(engine.channel(channelId, null, ExcorYouTubeChannelTab.LIVE)).videos)
        } finally {
            client.close()
        }
    }

    @Test
    fun videoInfoFallsBackToTheWatchPageWhenThePlayerWithholdsDetails() = runTest {
        val watch = """{"currentVideoEndpoint":{"watchEndpoint":{"videoId":"$VIDEO_ID"}},"contents":{"twoColumnWatchNextResults":{"results":{"results":{"contents":[
            {"videoPrimaryInfoRenderer":{"title":{"runs":[{"text":"Glass River"}]}}}]}}}}}"""
        var player = """{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"bot check"}}"""
        var nextCalls = 0
        val client = HttpClient(MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/youtubei/v1/player" -> player
                "/youtubei/v1/next" -> { nextCalls++; watch }
                else -> return@MockEngine respond("", HttpStatusCode.NotFound)
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val engine = ExcorEngine(FakeHost(), null, client)
            val info = assertNotNull(engine.videoInfo(VIDEO_ID))
            assertEquals("Glass River", info.title)
            assertEquals("LOGIN_REQUIRED", info.playabilityStatus)
            assertEquals(true, info.fromWatchPage)
            // A removed video (ERROR) is not looked up on the watch page.
            player = """{"playabilityStatus":{"status":"ERROR","reason":"Video unavailable"}}"""
            assertNull(engine.videoInfo(VIDEO_ID))
            assertEquals(1, nextCalls)
        } finally {
            client.close()
        }
    }

    @Test
    fun innerTubeReadsRetryTransientFailuresAndStopAtTheLimit() = runTest {
        val statuses = ArrayDeque<HttpStatusCode>()
        var calls = 0
        var retryAfter: String? = null
        val ok = """{"onResponseReceivedCommands":[{"appendContinuationItemsAction":{"continuationItems":[]}}]}"""
        val client = HttpClient(MockEngine {
            calls++
            val status = statuses.removeFirstOrNull() ?: HttpStatusCode.OK
            val headers = if (status == HttpStatusCode.TooManyRequests && retryAfter != null) headersOf(HttpHeaders.RetryAfter, retryAfter!!) else headersOf()
            respond(if (status == HttpStatusCode.OK) ok else "", status, headers)
        })
        try {
            val engine = ExcorEngine(FakeHost(), null, client)
            statuses += listOf(HttpStatusCode.ServiceUnavailable, HttpStatusCode.BadGateway)
            assertNotNull(engine.search("q", "token"))
            assertEquals(3, calls)

            calls = 0
            retryAfter = "2"
            val start = testScheduler.currentTime
            statuses += HttpStatusCode.TooManyRequests
            assertNotNull(engine.search("q", "token"))
            assertEquals(2, calls)
            assertEquals(2_000, testScheduler.currentTime - start, "a short Retry-After is honored")

            calls = 0
            statuses += List(3) { HttpStatusCode.ServiceUnavailable }
            val server = assertFailsWith<ExcorRequestException> { engine.search("q", "token") }
            assertEquals(ExcorRequestFailure.SERVER_ERROR to 503, server.failure to server.statusCode)
            assertEquals(3, calls, "three attempts at most")

            calls = 0
            retryAfter = null
            statuses += List(3) { HttpStatusCode.TooManyRequests }
            assertEquals(ExcorRequestFailure.RATE_LIMITED, assertFailsWith<ExcorRequestException> { engine.search("q", "token") }.failure)
            assertEquals(3, calls)

            calls = 0
            statuses += HttpStatusCode.BadRequest
            assertNull(engine.search("q", "token"))
            assertEquals(1, calls, "a client error is final")
        } finally {
            client.close()
        }
    }

    @Test
    fun aConnectionThatKeepsFailingIsANetworkFailure() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine { calls++; throw IllegalStateException("connection reset") })
        try {
            val engine = ExcorEngine(FakeHost(), null, client)
            val failure = assertFailsWith<ExcorRequestException> { engine.search("q", "token") }
            assertEquals(ExcorRequestFailure.NETWORK, failure.failure)
            assertEquals(3, calls)
            // The message carries the failure kind only.
            assertEquals("YouTube request failed: NETWORK", failure.message)
        } finally {
            client.close()
        }
    }

    @Test
    fun extractionWorksWithoutTheWatchPageAndFollowsOnlyAllowedMediaRedirects() = runTest {
        var redirectTo = "https://rr5---sn.googlevideo.com/videoplayback"
        val requestedHosts = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                // YouTube's captcha answer for the HTML page; clients that need no player script go on without it.
                "/watch" -> respond("", HttpStatusCode.TooManyRequests)
                "/youtubei/v1/player" -> respond(fixture("player-response.json"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                "/videoplayback" -> {
                    requestedHosts += request.url.host
                    if (request.url.host == "rr1---sn.googlevideo.com") {
                        respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "$redirectTo?${request.url.encodedQuery}"))
                    } else {
                        respond("x", HttpStatusCode.PartialContent)
                    }
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }) {
            // As Excor configures its client: redirects are never followed by the client itself.
            followRedirects = false
        }
        try {
            val engine = ExcorEngine(FakeHost(), null, client)
            val stream = assertNotNull(engine.extract(VIDEO_ID, 128, emptySet(), false, false))
            assertEquals("VISIONOS", stream.clientName)
            assertEquals(listOf("rr1---sn.googlevideo.com", "rr5---sn.googlevideo.com"), requestedHosts)

            // A redirect off the playback allowlist is never followed (a fresh engine, so nothing is remembered).
            requestedHosts.clear()
            redirectTo = "https://evil.example/videoplayback"
            assertNull(ExcorEngine(FakeHost(), null, client).extract(VIDEO_ID, 128, emptySet(), false, false))
            assertEquals(listOf("rr1---sn.googlevideo.com"), requestedHosts)
        } finally {
            client.close()
        }
    }

    @Test
    fun videoExtractionPairsTheBestPlayableVideoWithAudioInTheSameContainer() = runTest {
        val failingItags = mutableSetOf<String>()
        val probedItags = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/watch" -> respond("", HttpStatusCode.TooManyRequests)
                "/youtubei/v1/player" -> respond(fixture("player-response-video.json"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                "/videoplayback" -> {
                    val itag = request.url.parameters["itag"].orEmpty()
                    probedItags += itag
                    if (itag in failingItags) respond("", HttpStatusCode.Forbidden) else respond("x", HttpStatusCode.PartialContent)
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }) { followRedirects = false }
        val all = setOf(ExcorVideoCodec.H264, ExcorVideoCodec.VP9, ExcorVideoCodec.AV1)
        try {
            val best = assertNotNull(ExcorEngine(FakeHost(), null, client).extractVideo(VIDEO_ID, 1080, all, 160, emptySet()))
            assertEquals("VISIONOS", best.clientName)
            assertEquals(1080, best.video.height)
            assertEquals(ExcorVideoCodec.H264, best.video.codec)
            assertEquals(0L..740L, best.video.initRange)
            assertFalse(best.video.containsAudio)
            // MP4 video gets M4A audio even though the Opus track has the higher bitrate.
            assertEquals("audio/mp4", assertNotNull(best.audio).mimeType.substringBefore(';'))
            assertEquals(best.video.headers, best.audio?.headers)

            // VP9 only: WebM video with Opus audio.
            val vp9 = assertNotNull(ExcorEngine(FakeHost(), null, client).extractVideo(VIDEO_ID, 1080, setOf(ExcorVideoCodec.VP9), 160, emptySet()))
            assertEquals(ExcorVideoCodec.VP9, vp9.video.codec)
            assertEquals("audio/webm", assertNotNull(vp9.audio).mimeType.substringBefore(';'))

            // A video that fails its probe gives way to the next candidate.
            failingItags += setOf("137", "248", "399")
            val fallback = assertNotNull(ExcorEngine(FakeHost(), null, client).extractVideo(VIDEO_ID, 1080, all, 160, emptySet()))
            assertEquals(720, fallback.video.height)
            assertEquals(60, fallback.video.fps)

            // With no playable separate audio, only the progressive format is usable, and it carries its own sound.
            failingItags += setOf("140", "251")
            probedItags.clear()
            val progressive = assertNotNull(ExcorEngine(FakeHost(), null, client).extractVideo(VIDEO_ID, 1080, all, 160, emptySet()))
            assertEquals(360, progressive.video.height)
            assertTrue(progressive.video.containsAudio)
            assertNull(progressive.audio)
            // Each audio track is probed once, and no picture-only video is probed without audio to go with it.
            assertEquals(listOf("140", "251", "18"), probedItags)

            // Nothing playable at all.
            failingItags += "18"
            assertNull(ExcorEngine(FakeHost(), null, client).extractVideo(VIDEO_ID, 1080, all, 160, emptySet()))
        } finally {
            client.close()
        }
    }

    private data class TestEngine(
        val engine: ExcorEngine,
        val host: FakeHost,
        val requests: RequestCounts,
    )

    private class RequestCounts(var player: Int = 0)

    private class FakeHost : ExcorHostServices {
        override val configStore = object : ExcorConfigStore {
            override val enabled = true
            override fun getString(key: String): String? = null
            override fun getLong(key: String): Long? = null
            override fun putString(key: String, value: String) = Unit
            override fun putLong(key: String, value: Long) = Unit
        }
        var now = 0L
        override suspend fun session() = ExcorSession(null, "visitor", "US", "en")
        override fun nowMs() = now
        override fun log(level: ExcorLogLevel, message: String, trackId: String?) = Unit
    }

    private companion object {
        const val VIDEO_ID = "abcdefghijk"
        val SKIP = setOf("TVHTML5", "WEB")
    }
}
