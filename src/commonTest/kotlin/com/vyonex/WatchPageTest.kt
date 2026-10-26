package com.vyonex

import com.vyonex.internal.at
import com.vyonex.internal.audioFormatsOf
import com.vyonex.internal.parseWatchPage
import com.vyonex.internal.playerIdOf
import com.vyonex.internal.str
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WatchPageTest {
    @Test
    fun readsPlayerResponseAndPlayerFromPage() {
        val page = assertNotNull(parseWatchPage(fixture("watch-page-assembled.html")))
        assertEquals("dce23b30", page.playerId)
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
}
