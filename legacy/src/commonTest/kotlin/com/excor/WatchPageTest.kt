package com.excor

import com.excor.internal.Engine
import com.excor.internal.at
import com.excor.internal.audioFormatsOf
import com.excor.internal.parseWatchPage
import com.excor.internal.playerIdOf
import com.excor.internal.str
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchPageTest {
    // A real logged-out page, captured on the owner's network on 2026-10-27 (see fixtures/README.md).
    @Test
    fun readsPlayerResponseAndPlayerFromPage() {
        val page = assertNotNull(parseWatchPage(fixture("watch-page.html")))
        assertEquals("6b2fce74", page.playerId)
        assertEquals("OK", page.playerResponse.at("playabilityStatus", "status").str)
        assertEquals("dQw4w9WgXcQ", page.playerResponse.at("videoDetails", "videoId").str)
        assertEquals(listOf("140", "249", "250", "251"), audioFormatsOf(page.playerResponse).map { it["itag"].str })
    }

    @Test
    fun blockedPageHasNoPlayerResponse() = assertNull(parseWatchPage(fixture("watch-page-blocked.html")))

    @Test
    fun readsPlayerVersionFromIframeApi() = assertEquals("dce23b30", playerIdOf(fixture("iframe_api.js")))

    @Test
    fun sabrOnlyResponsesHaveNoUrls() {
        // WEB without a PO token: audio formats exist but carry neither url nor signatureCipher.
        val formats = audioFormatsOf(fixtureJson("player-response-web.json"))
        assertEquals(true, formats.isNotEmpty())
        assertEquals(true, formats.all { it["url"] == null && it["signatureCipher"] == null })
    }

    @Test
    fun sabrOnlyPageYieldsNoStreamAndNoCandidates() = runTest {
        // The real page's audio formats have neither url nor signatureCipher (SABR-only), which the brief rules out.
        val host = FakeHost()
        val unexpected = mutableListOf<String>()
        val engine = Engine(host, tokenProvider = null, http = HttpClient(MockEngine { request ->
            if (request.url.encodedPath != "/watch") unexpected += request.url.encodedPath
            respond(fixture("watch-page.html"))
        }), store = null)
        assertNull(engine.watchPageStream("dQw4w9WgXcQ", 160, emptySet(), false))
        assertEquals(emptyList(), engine.audioCandidates("dQw4w9WgXcQ"))
        assertEquals(emptyList(), unexpected)
        assertTrue(host.logs.none { it.first == ExcorLogLevel.WARN }, "${host.logs}")
    }
}
