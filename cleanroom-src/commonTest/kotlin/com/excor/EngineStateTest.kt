package com.excor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
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
