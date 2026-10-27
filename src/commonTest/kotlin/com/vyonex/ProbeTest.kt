package com.vyonex

import com.vyonex.internal.probeRefusal
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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

    @Test
    fun refusedUrlIsNotReturnedAndItsProfileIsExcluded() = runTest {
        val engine = fixtureEngine(host, refusedClients = setOf("VISIONOS"))
        assertNull(engine.extract("dQw4w9WgXcQ", 160, setOf("TVHTML5"), false))
        assertTrue(engine.isExcluded("dQw4w9WgXcQ", "VISIONOS"))
        assertTrue(host.logs.any { it.second == "VyonEx: VISIONOS URL refused (HTTP 403)" }, "${host.logs}")
    }

    @Test
    fun answeringUrlIsReturnedAndRemembered() = runTest {
        val engine = fixtureEngine(host)
        val stream = assertNotNull(engine.extract("dQw4w9WgXcQ", 160, setOf("TVHTML5"), false))
        assertEquals("VISIONOS", stream.profileId)
        // VISIONOS has no response-wide loudnessDb; itag 251 carries its own.
        assertEquals(0.97999954, stream.loudnessDb)
        assertEquals(stream.headers, engine.headersFor(stream.url))
        assertFalse(engine.isExcluded("dQw4w9WgXcQ", "VISIONOS"))
    }

    @Test
    fun onlyARangedMediaAnswerCounts() {
        val range = "bytes 0-0/3433514"
        assertNull(probeRefusal(HttpStatusCode.PartialContent, range, "audio/webm"))
        // An unexpected media type, or none, still counts: only error pages are turned away.
        assertNull(probeRefusal(HttpStatusCode.PartialContent, range, null))
        assertNull(probeRefusal(HttpStatusCode.PartialContent, range, "application/octet-stream"))
        // A 200 ignored the range (and would have sent the whole file).
        assertEquals("HTTP 200", probeRefusal(HttpStatusCode.OK, null, "audio/webm"))
        assertEquals("HTTP 403", probeRefusal(HttpStatusCode.Forbidden, null, "text/plain"))
        assertEquals("range not honoured: no Content-Range", probeRefusal(HttpStatusCode.PartialContent, null, "audio/webm"))
        assertEquals("range not honoured: bytes 0-99/3433514", probeRefusal(HttpStatusCode.PartialContent, "bytes 0-99/3433514", "audio/webm"))
        assertEquals("range not honoured: bytes 0-0/0", probeRefusal(HttpStatusCode.PartialContent, "bytes 0-0/0", "audio/webm"))
        assertEquals("not media: text/html", probeRefusal(HttpStatusCode.PartialContent, range, "text/html; charset=utf-8"))
        assertEquals("not media: application/json", probeRefusal(HttpStatusCode.PartialContent, range, "application/json"))
    }

    @Test
    fun aSuccessThatIgnoredTheRangeIsRefused() = runTest {
        val engine = fixtureEngine(host, mediaAnswer = HttpStatusCode.OK to headersOf(HttpHeaders.ContentType, "audio/webm"))
        assertNull(engine.extract("dQw4w9WgXcQ", 160, setOf("TVHTML5"), false))
        assertTrue(engine.isExcluded("dQw4w9WgXcQ", "VISIONOS"))
        assertTrue(host.logs.any { it.second == "VyonEx: VISIONOS URL refused (HTTP 200)" }, "${host.logs}")
    }

    @Test
    fun anErrorPageWithA206IsRefused() = runTest {
        val page = headersOf(HttpHeaders.ContentRange to listOf("bytes 0-0/512"), HttpHeaders.ContentType to listOf("text/html"))
        val engine = fixtureEngine(host, mediaAnswer = HttpStatusCode.PartialContent to page)
        assertNull(engine.extract("dQw4w9WgXcQ", 160, setOf("TVHTML5"), false))
        assertTrue(host.logs.any { it.second == "VyonEx: VISIONOS URL refused (not media: text/html)" }, "${host.logs}")
    }

    @Test
    fun refusedIdentityFallsThroughToTheNext() = runTest(timeout = 5.minutes) {
        // TV_SAMSUNG's ciphered URLs are decoded with the captured player before being probed.
        val engine = fixtureEngine(host, refusedClients = setOf("TVHTML5"))
        val stream = assertNotNull(engine.extract("dQw4w9WgXcQ", 160, emptySet(), false))
        assertEquals("VISIONOS", stream.profileId)
        assertTrue(engine.isExcluded("dQw4w9WgXcQ", "TV_SAMSUNG"))
    }
}
