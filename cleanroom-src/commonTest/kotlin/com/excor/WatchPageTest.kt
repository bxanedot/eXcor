package com.excor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WatchPageTest {
    @Test
    fun readsApiKeyAndEmbeddedPlayerResponse() {
        val result = parseWatchPage(fixture("watch-page.html"))

        assertEquals("fixture-key", result?.apiKey)
        assertEquals("OK", result?.playerResponse?.playabilityStatus)
        assertEquals("/s/player/fixture/base.js", result?.playerResponse?.playerScriptUrl)
    }

    @Test
    fun rejectsMissingPlayerResponse() {
        assertNull(parseWatchPage("<html>no player data</html>"))
    }
}
