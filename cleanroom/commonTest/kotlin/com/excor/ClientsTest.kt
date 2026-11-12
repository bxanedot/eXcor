package com.excor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClientsTest {
    @Test
    fun loggedOutOrderMatchesYtDlp() {
        assertEquals(
            listOf("TVHTML5", "VISIONOS", "WEB"),
            selectClients(session(), emptySet(), emptySet()).map(ClientIdentity::name),
        )
    }

    @Test
    fun loggedInOrderDropsTheTvClientAndKeepsVisionOs() {
        // Live 2026-11-04, signed in with a web session: TVHTML5 answered "The page needs to be reloaded." for every
        // video, so it is not tried; VISIONOS, which works signed out, stays.
        val clients = selectClients(session(cookie = "session"), emptySet(), emptySet())
        assertEquals(listOf("VISIONOS", "WEB_EMBEDDED_PLAYER", "WEB"), clients.map(ClientIdentity::name))
    }

    @Test
    fun skipMatchesNameOrProfile() {
        assertEquals(
            listOf("VISIONOS"),
            selectClients(session(), setOf("TVHTML5", "web"), emptySet()).map(ClientIdentity::name),
        )
        assertEquals(
            listOf("TVHTML5", "WEB"),
            selectClients(session(), emptySet(), setOf("visionos")).map(ClientIdentity::name),
        )
    }

    @Test
    fun attemptsNeverExceedThree() {
        assertTrue(selectClients(session(), emptySet(), emptySet()).size <= CLIENT_ATTEMPT_LIMIT)
        assertTrue(selectClients(session(cookie = "session"), emptySet(), emptySet()).size <= CLIENT_ATTEMPT_LIMIT)
        assertEquals(3, CLIENT_ATTEMPT_LIMIT)
    }

    @Test
    fun webClientsRequireStreamingTokens() {
        val clients = selectClients(session(), emptySet(), emptySet())
        assertEquals(listOf(false, false, true), clients.map(ClientIdentity::requiresStreamingToken))
    }

    private fun session(cookie: String? = null) = ExcorSession(cookie, "visitor", "US", "en")
}
