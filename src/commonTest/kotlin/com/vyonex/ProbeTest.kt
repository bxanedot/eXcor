package com.vyonex

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
        assertNull(engine.extract("dQw4w9WgXcQ", 160, setOf("TV_SAMSUNG"), false))
        assertTrue(engine.isExcluded("dQw4w9WgXcQ", "VISIONOS"))
        assertTrue(host.logs.any { it.second == "VyonEx: VISIONOS URL refused (HTTP 403)" }, "${host.logs}")
    }

    @Test
    fun answeringUrlIsReturnedAndRemembered() = runTest {
        val engine = fixtureEngine(host)
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
        val engine = fixtureEngine(host, refusedClients = setOf("TVHTML5"))
        val stream = assertNotNull(engine.extract("dQw4w9WgXcQ", 160, emptySet(), false))
        assertEquals("VISIONOS", stream.profileId)
        assertTrue(engine.isExcluded("dQw4w9WgXcQ", "TV_SAMSUNG"))
    }
}
