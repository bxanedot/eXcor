package com.vyonex

import com.vyonex.internal.Engine
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** BRIEF 4.2: only a format "whose URL works" may be returned. Media answers depend on the URL's `c` client. */
class ProbeTest {
    private val host = FakeHost()

    private fun engine(refusedClients: Set<String>) = Engine(
        host,
        tokenProvider = null,
        http = HttpClient(MockEngine { request ->
            val url = request.url
            when {
                url.encodedPath == "/iframe_api" -> respond(fixture("iframe_api.js"))
                url.encodedPath.startsWith("/s/player/dce23b30/") -> respond(fixture("player-dce23b30.js"))
                url.encodedPath == "/youtubei/v1/player" -> {
                    val tv = "\"TVHTML5\"" in (request.body as TextContent).text
                    respond(
                        fixture(if (tv) "player-response-tv_samsung.json" else "player-response-visionos.json"),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                url.host.endsWith(".googlevideo.com") -> {
                    // The probe asks for one byte with the stream's own headers.
                    assertEquals("bytes=0-0", request.headers[HttpHeaders.Range])
                    assertNotNull(request.headers[HttpHeaders.UserAgent])
                    val refused = url.parameters["c"] in refusedClients
                    respond("x", if (refused) HttpStatusCode.Forbidden else HttpStatusCode.PartialContent)
                }
                else -> error("unexpected request to ${url.host}${url.encodedPath}")
            }
        }),
        store = null,
    )

    @Test
    fun refusedUrlIsNotReturnedAndItsProfileIsExcluded() = runTest {
        val engine = engine(refusedClients = setOf("VISIONOS"))
        assertNull(engine.extract("dQw4w9WgXcQ", 160, setOf("TV_SAMSUNG"), false))
        assertTrue(engine.isExcluded("dQw4w9WgXcQ", "VISIONOS"))
        assertTrue(host.logs.any { it.second == "VyonEx: VISIONOS URL refused (HTTP 403)" }, "${host.logs}")
    }

    @Test
    fun answeringUrlIsReturnedAndRemembered() = runTest {
        val engine = engine(refusedClients = emptySet())
        val stream = assertNotNull(engine.extract("dQw4w9WgXcQ", 160, setOf("TV_SAMSUNG"), false))
        assertEquals("VISIONOS", stream.profileId)
        // VISIONOS has no response-wide loudnessDb; itag 251 carries its own.
        assertEquals(0.97999954, stream.loudnessDb)
        assertEquals(stream.headers, engine.headersFor(stream.url))
        assertFalse(engine.isExcluded("dQw4w9WgXcQ", "VISIONOS"))
    }

    @Test
    fun refusedIdentityFallsThroughToTheNext() = runTest(timeout = 5.minutes) {
        // TV_SAMSUNG's ciphered URLs are decoded with the captured player before being probed.
        val engine = engine(refusedClients = setOf("TVHTML5"))
        val stream = assertNotNull(engine.extract("dQw4w9WgXcQ", 160, emptySet(), false))
        assertEquals("VISIONOS", stream.profileId)
        assertTrue(engine.isExcluded("dQw4w9WgXcQ", "TV_SAMSUNG"))
    }
}
