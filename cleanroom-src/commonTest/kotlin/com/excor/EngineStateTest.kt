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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
