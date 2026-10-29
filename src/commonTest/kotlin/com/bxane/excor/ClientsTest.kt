package com.bxane.excor

import com.bxane.excor.internal.ClientHealth
import com.bxane.excor.internal.DEMOTE_MS
import com.bxane.excor.internal.LOGGED_IN_ORDER
import com.bxane.excor.internal.LOGGED_OUT_ORDER
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClientsTest {
    private fun List<com.bxane.excor.internal.Identity>.ids() = map { it.profileId }

    @Test
    fun ordersFollowYtDlpThenTheTvFallbacks() {
        assertEquals(listOf("TV_SAMSUNG", "VISIONOS", "WEB", "TV", "TV_DOWNGRADED"), LOGGED_OUT_ORDER.ids())
        assertEquals(listOf("WEB_EMBEDDED", "TV_SAMSUNG", "WEB", "TV", "TV_DOWNGRADED"), LOGGED_IN_ORDER.ids())
        // Cookies only go where they are taken; the fallbacks need no PO token.
        assertTrue(LOGGED_IN_ORDER.all { it.supportsCookies })
        assertTrue((LOGGED_OUT_ORDER + LOGGED_IN_ORDER).filter { it.needsPoToken }.ids().all { it == "WEB" })
    }

    @Test
    fun aClientThatKeepsFailingIsTriedLastForAWhile() {
        var now = 0L
        val health = ClientHealth { now }
        repeat(2) { health.failed("TV_SAMSUNG") }
        assertEquals(LOGGED_OUT_ORDER.ids(), health.ordered(LOGGED_OUT_ORDER).ids())
        health.failed("TV_SAMSUNG")
        assertEquals(listOf("VISIONOS", "WEB", "TV", "TV_DOWNGRADED", "TV_SAMSUNG"), health.ordered(LOGGED_OUT_ORDER).ids())
        // The demotion runs out, and the count starts over.
        now += DEMOTE_MS + 1
        assertEquals(LOGGED_OUT_ORDER.ids(), health.ordered(LOGGED_OUT_ORDER).ids())
        health.failed("TV_SAMSUNG")
        assertEquals(LOGGED_OUT_ORDER.ids(), health.ordered(LOGGED_OUT_ORDER).ids())
        // A success clears the count.
        repeat(2) { health.failed("VISIONOS") }
        health.succeeded("VISIONOS")
        health.failed("VISIONOS")
        assertEquals(LOGGED_OUT_ORDER.ids(), health.ordered(LOGGED_OUT_ORDER).ids())
    }

    @Test
    fun refusedMediaAcrossVideosDemotesTheClient() = runTest {
        val host = FakeHost()
        val engine = fixtureEngine(host, refusedClients = setOf("VISIONOS"))
        for (video in listOf("video1", "video2", "video3")) assertNull(engine.extract(video, 160, setOf("TVHTML5"), false))
        assertTrue(engine.health.isDemoted("VISIONOS"))
        // A new session (another account or network) starts afresh.
        engine.onSessionChanged()
        assertTrue(!engine.health.isDemoted("VISIONOS"))
    }
}
