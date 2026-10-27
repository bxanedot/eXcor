package com.vyonex

import com.vyonex.internal.EXCLUSION_MS
import com.vyonex.internal.Engine
import com.vyonex.internal.MAX_REMEMBERED
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineTest {
    private lateinit var host: FakeHost
    private lateinit var engine: Engine

    // Fresh per test (TestNG reuses the instance). Every test stays offline: nothing reaches the HTTP client.
    @BeforeTest
    fun setUp() {
        host = FakeHost()
        engine = Engine(host, tokenProvider = null, http = HttpClient(), store = null)
    }

    private fun stream(i: Int) = VyonExStream(
        videoId = "video$i",
        url = "https://r$i.googlevideo.com/videoplayback?id=$i",
        kbps = 128,
        mimeType = "audio/webm; codecs=\"opus\"",
        loudnessDb = null,
        clientName = "TVHTML5",
        profileId = "TV_SAMSUNG",
        headers = mapOf("User-Agent" to "agent$i"),
    )

    @Test
    fun remembersHeadersByUrl() {
        engine.remember(stream(1))
        assertEquals(mapOf("User-Agent" to "agent1"), engine.headersFor(stream(1).url))
        assertNull(engine.headersFor(stream(2).url))
    }

    @Test
    fun clearsAllRememberedStreamsWhenFull() {
        repeat(MAX_REMEMBERED) { engine.remember(stream(it)) }
        assertTrue((0 until MAX_REMEMBERED).all { engine.headersFor(stream(it).url) != null })
        engine.remember(stream(0)) // already known: no clearing
        assertTrue(engine.headersFor(stream(1).url) != null)
        engine.remember(stream(MAX_REMEMBERED))
        assertTrue((0 until MAX_REMEMBERED).all { engine.headersFor(stream(it).url) == null })
        assertTrue(engine.headersFor(stream(MAX_REMEMBERED).url) != null)
    }

    @Test
    fun exclusionLastsTenMinutes() {
        engine.exclude("video1", "VISIONOS")
        assertTrue(engine.isExcluded("video1", "VISIONOS"))
        assertFalse(engine.isExcluded("video1", "TV_SAMSUNG"))
        assertFalse(engine.isExcluded("video2", "VISIONOS"))
        host.now += EXCLUSION_MS - 1
        assertTrue(engine.isExcluded("video1", "VISIONOS"))
        host.now += 1
        assertFalse(engine.isExcluded("video1", "VISIONOS"))
        assertEquals(VyonExLogLevel.WARN to "VyonEx: excluding VISIONOS for 10 minutes", host.logs.single())
    }

    @Test
    fun onRefusedForgetsAndExcludesRememberedStreams() {
        engine.remember(stream(1))
        assertEquals("video1", engine.onRefused(stream(1).url))
        assertNull(engine.headersFor(stream(1).url))
        assertTrue(engine.isExcluded("video1", "TV_SAMSUNG"))
        assertNull(engine.onRefused(stream(1).url))
        assertNull(engine.onRefused("https://r9.googlevideo.com/videoplayback"))
    }

    @Test
    fun onSessionChangedForgetsAndWarmsUpOnlyWhenEnabled() = runTest {
        host.failSession = true
        engine.remember(stream(1))
        engine.exclude("video1", "VISIONOS")
        host.enabled = false
        engine.onSessionChanged()
        assertNull(engine.headersFor(stream(1).url))
        assertFalse(engine.isExcluded("video1", "VISIONOS"))
        withContext(Dispatchers.Default) {
            assertNull(withTimeoutOrNull(500) { host.sessionCalls.receive() }, "no warm-up when disabled")
            host.enabled = true
            engine.onSessionChanged()
            withTimeout(5_000) { host.sessionCalls.receive() }
        }
    }

    @Test
    fun closeStopsBackgroundWork() = runTest {
        host.failSession = true
        engine.close()
        engine.onSessionChanged()
        withContext(Dispatchers.Default) {
            assertNull(withTimeoutOrNull(500) { host.sessionCalls.receive() }, "no warm-up after close")
        }
    }

    @Test
    fun skippedExcludedAndTokenlessIdentitiesAreNotTried() = runTest {
        engine.exclude("video1", "TV_SAMSUNG")
        // VISIONOS is skipped by profile, WEB has no token provider: no request is made at all.
        assertNull(engine.extract("video1", 160, setOf("VISIONOS"), false))
        assertNull(engine.extract("video1", 160, setOf("TVHTML5", "VISIONOS"), false))
    }

    @Test
    fun watchPageFallbackHonoursSkipAndExclusion() = runTest {
        assertNull(engine.watchPageStream("video1", 160, setOf("WEB"), false))
        assertNull(engine.watchPageStream("video1", 160, setOf("WEB_WATCH_PAGE"), false))
        engine.exclude("video1", "WEB_WATCH_PAGE")
        assertNull(engine.extractOrWatchPage("video1", 160, setOf("TV_SAMSUNG", "VISIONOS"), false))
    }
}
