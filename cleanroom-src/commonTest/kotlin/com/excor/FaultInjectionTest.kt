package com.excor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Synthetic, deterministic failure cases. No credentials, expiring stream URLs or network access are required.
 */
class FaultInjectionTest {
    @Test
    fun maxAcceptedRetryAfterIsFiveSeconds() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            if (calls == 1) respond(
                "", HttpStatusCode.TooManyRequests,
                headersOf(HttpHeaders.RetryAfter, "5"),
            ) else respond(EMPTY_CONTINUATION, HttpStatusCode.OK)
        })
        try {
            val engine = ExcorEngine(FaultHost(), null, client)
            val start = testScheduler.currentTime
            assertNotNull(engine.search("q", "continuation"))
            assertEquals(2, calls)
            assertEquals(5_000L, testScheduler.currentTime - start)
        } finally {
            client.close()
        }
    }

    @Test
    fun retryAfterAboveLimitFallsBackToBoundedBackoff() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            if (calls == 1) respond(
                "", HttpStatusCode.TooManyRequests,
                headersOf(HttpHeaders.RetryAfter, "6"),
            ) else respond(EMPTY_CONTINUATION, HttpStatusCode.OK)
        })
        try {
            val engine = ExcorEngine(FaultHost(), null, client)
            val start = testScheduler.currentTime
            assertNotNull(engine.search("q", "continuation"))
            assertEquals(2, calls)
            assertTrue(testScheduler.currentTime - start in 300L..499L)
        } finally {
            client.close()
        }
    }

    @Test
    fun cancellationDuringRetryAfterStopsFurtherRequests() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            respond("", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "5"))
        })
        try {
            val engine = ExcorEngine(FaultHost(), null, client)
            assertFailsWith<TimeoutCancellationException> {
                withTimeout(250L) { engine.search("q", "continuation") }
            }
            assertEquals(1, calls, "cancellation must stop scheduled retries")
            assertEquals(250L, testScheduler.currentTime)
        } finally {
            client.close()
        }
    }

    @Test
    fun declaredOversizeAndActualOversizeResponsesAreRejected() = runTest {
        var calls = 0
        val tooLarge = "x".repeat(2_000_001)
        val client = HttpClient(MockEngine {
            calls++
            if (calls == 1) {
                respond(
                    "small body", HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentLength, "2000001"),
                )
            } else {
                respond(tooLarge, HttpStatusCode.OK)
            }
        })
        try {
            val engine = ExcorEngine(FaultHost(), null, client)
            assertNull(engine.search("q", "continuation"), "oversized declared Content-Length must fail closed")
            assertNull(engine.search("q", "continuation"), "oversized actual response must fail closed")
            assertEquals(2, calls, "oversized successful responses are not retried")
        } finally {
            client.close()
        }
    }

    @Test
    fun deniedMediaRedirectNeverVisitsForeignHostWithSessionCredentials() = runTest {
        val visited = mutableListOf<String>()
        val host = FaultHost(cookie = "SID=synthetic-secret", visitor = "synthetic-visitor")
        val client = HttpClient(MockEngine { request ->
            visited += request.url.host
            when (request.url.encodedPath) {
                "/watch" -> respond("", HttpStatusCode.TooManyRequests)
                "/youtubei/v1/player" -> respond(
                    fixture("player-response.json"), HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
                "/videoplayback" -> respond(
                    "", HttpStatusCode.Found,
                    headersOf(HttpHeaders.Location, "https://untrusted.invalid/videoplayback?token=synthetic-secret"),
                )
                else -> respond("", HttpStatusCode.NotFound)
            }
        }) { followRedirects = false }
        try {
            val engine = ExcorEngine(host, null, client)
            assertNull(engine.extract("abcdefghijk", 128, emptySet(), false, false))
            assertTrue(visited.any { it.endsWith(".googlevideo.com") })
            assertFalse("untrusted.invalid" in visited, "never send a session header to a foreign redirect host")
            assertTrue(visited.all { it == "www.youtube.com" || it.endsWith(".googlevideo.com") })
            assertFalse(host.messages.any { "synthetic-secret" in it || "synthetic-visitor" in it })
        } finally {
            client.close()
        }
    }

    private class FaultHost(
        private val cookie: String? = null,
        private val visitor: String? = null,
    ) : ExcorHostServices {
        val messages = mutableListOf<String>()
        override val configStore = object : ExcorConfigStore {
            override val enabled: Boolean = false
            override fun getString(key: String): String? = null
            override fun getLong(key: String): Long? = null
            override fun putString(key: String, value: String) = Unit
            override fun putLong(key: String, value: Long) = Unit
        }
        override suspend fun session() = ExcorSession(cookie, visitor, "US", "en")
        override fun nowMs(): Long = 0L
        override fun log(level: ExcorLogLevel, message: String, trackId: String?) {
            messages += message
        }
    }

    private companion object {
        const val EMPTY_CONTINUATION =
            """{"onResponseReceivedCommands":[{"appendContinuationItemsAction":{"continuationItems":[]}}]}"""
    }
}
