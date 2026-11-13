package com.excor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The host must not receive or retain old-session streams after an auth/session switch. */
class SessionGenerationTest {
    @Test
    fun nextExtractionUsesOnlyTheReplacementSession() = runTest {
        val playerCredentials = mutableListOf<Pair<String?, String?>>()
        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/watch" -> respond("", HttpStatusCode.TooManyRequests)
                "/youtubei/v1/player" -> {
                    playerCredentials += (request.headers[HttpHeaders.Cookie] to request.headers[HttpHeaders.Authorization])
                    respond(
                        fixture("player-response.json"), HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                "/videoplayback" -> respond("x", HttpStatusCode.PartialContent)
                else -> respond("", HttpStatusCode.NotFound)
            }
        }) { followRedirects = false }
        try {
            val host = Host().apply {
                currentSession = ExcorSession("SID=synthetic-old; SAPISID=synthetic-old-auth", "synthetic-old-visitor", "US", "en")
            }
            val engine = ExcorEngine(host, null, client)
            assertNotNull(engine.extract(VIDEO_ID, 128, emptySet(), false, false))

            host.currentSession = ExcorSession("SID=synthetic-new; SAPISID=synthetic-new-auth", "synthetic-new-visitor", "US", "en")
            engine.onSessionChanged()
            assertNotNull(engine.extract(VIDEO_ID, 128, emptySet(), false, false))

            assertEquals(2, playerCredentials.size)
            assertEquals("SID=synthetic-old; SAPISID=synthetic-old-auth", playerCredentials[0].first)
            assertEquals("SID=synthetic-new; SAPISID=synthetic-new-auth", playerCredentials[1].first)
            assertFalse(playerCredentials[1].first.orEmpty().contains("synthetic-old"))
            assertNotNull(playerCredentials[0].second)
            assertNotNull(playerCredentials[1].second)
            assertFalse(playerCredentials[0].second == playerCredentials[1].second)
        } finally {
            client.close()
        }
    }

    @Test
    fun sessionSwitchWhilePlayerRequestIsPendingInvalidatesTheExtraction() = runTest {
        val playerReached = CompletableDeferred<Unit>()
        val releasePlayer = CompletableDeferred<Unit>()
        var mediaCalls = 0
        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/watch" -> respond("", HttpStatusCode.TooManyRequests)
                "/youtubei/v1/player" -> {
                    playerReached.complete(Unit)
                    releasePlayer.await()
                    respond(
                        fixture("player-response.json"), HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                "/videoplayback" -> {
                    mediaCalls++
                    respond("x", HttpStatusCode.PartialContent)
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }) { followRedirects = false }
        try {
            val engine = ExcorEngine(Host(), null, client)
            val pending = async { engine.extract(VIDEO_ID, 128, emptySet(), false, false) }
            playerReached.await()
            engine.onSessionChanged()
            releasePlayer.complete(Unit)
            assertFailsWith<CancellationException> { pending.await() }
            assertEquals(0, mediaCalls, "stale player responses must never start a media probe")
        } finally {
            releasePlayer.complete(Unit)
            client.close()
        }
    }

    @Test
    fun sessionSwitchWhileMediaProbeIsPendingNeverRestoresTheOldUrl() = runTest {
        val probeReached = CompletableDeferred<Unit>()
        val releaseProbe = CompletableDeferred<Unit>()
        var probedUrl: String? = null
        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/watch" -> respond("", HttpStatusCode.TooManyRequests)
                "/youtubei/v1/player" -> respond(
                    fixture("player-response.json"), HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
                "/videoplayback" -> {
                    probedUrl = request.url.toString()
                    probeReached.complete(Unit)
                    releaseProbe.await()
                    respond("x", HttpStatusCode.PartialContent)
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }) { followRedirects = false }
        try {
            val engine = ExcorEngine(Host(), null, client)
            val pending = async { engine.extract(VIDEO_ID, 128, emptySet(), false, false) }
            probeReached.await()
            val url = assertNotNull(probedUrl)
            engine.onSessionChanged()
            releaseProbe.complete(Unit)
            assertFailsWith<CancellationException> { pending.await() }
            assertNull(engine.headersFor(url), "previous account's stream headers must not reappear")
        } finally {
            releaseProbe.complete(Unit)
            client.close()
        }
    }

    @Test
    fun sessionSwitchAlsoInvalidatesPendingFormatLists() = runTest {
        val probeReached = CompletableDeferred<Unit>()
        val releaseProbe = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/watch" -> respond("", HttpStatusCode.TooManyRequests)
                "/youtubei/v1/player" -> respond(
                    fixture("player-response-video.json"), HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
                "/videoplayback" -> {
                    probeReached.complete(Unit)
                    releaseProbe.await()
                    respond("x", HttpStatusCode.PartialContent)
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }) { followRedirects = false }
        try {
            val engine = ExcorEngine(Host(), null, client)
            val pending = async { engine.formats(VIDEO_ID, emptySet()) }
            probeReached.await()
            engine.onSessionChanged()
            releaseProbe.complete(Unit)
            assertFailsWith<CancellationException> { pending.await() }
        } finally {
            releaseProbe.complete(Unit)
            client.close()
        }
    }

    private class Host : ExcorHostServices {
        var currentSession = ExcorSession(null, "synthetic-visitor", "US", "en")

        override val configStore = object : ExcorConfigStore {
            override val enabled = false
            override fun getString(key: String): String? = null
            override fun getLong(key: String): Long? = null
            override fun putString(key: String, value: String) = Unit
            override fun putLong(key: String, value: Long) = Unit
        }
        override suspend fun session() = currentSession
        override fun nowMs(): Long = 0L
        override fun log(level: ExcorLogLevel, message: String, trackId: String?) = Unit
    }

    private companion object {
        const val VIDEO_ID = "abcdefghijk"
    }
}
