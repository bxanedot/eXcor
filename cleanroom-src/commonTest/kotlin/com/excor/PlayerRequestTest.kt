package com.excor

import com.excor.internal.primary.extraction.PoTokenResult
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class PlayerRequestTest {
    @Test
    fun requestUsesCurrentSessionAndCorrectToken() {
        val session = ExcorSession("SID=session", "visitor-current", "CA", "fr-CA")
        val client = selectClients(session, emptySet(), emptySet()).last()
        val poToken = PoTokenResult("player-secret", "stream-secret", "visitor-current")
        val request = buildPlayerRequest(client, "abcdefghijk", session, "api key", 12345, poToken)

        assertNotNull(request)
        assertEquals("api key", Url(request.url).parameters["key"])
        assertEquals("SID=session", request.headers["Cookie"])
        assertEquals("visitor-current", request.headers["X-Goog-Visitor-Id"])
        assertEquals(client.userAgent, request.headers["User-Agent"])
        assertEquals(client.clientId.toString(), request.headers["X-YouTube-Client-Name"])

        val root = Json.parseToJsonElement(request.body) as JsonObject
        val context = root["context"] as JsonObject
        val clientData = context["client"] as JsonObject
        assertEquals("fr-CA", clientData["hl"]?.toString()?.trim('"'))
        assertEquals("CA", clientData["gl"]?.toString()?.trim('"'))
        assertEquals("visitor-current", clientData["visitorData"]?.toString()?.trim('"'))
        val playback = root["playbackContext"] as JsonObject
        val content = playback["contentPlaybackContext"] as JsonObject
        assertEquals("12345", content["signatureTimestamp"]?.toString())
        val integrity = root["serviceIntegrityDimensions"] as JsonObject
        assertEquals("player-secret", integrity["poToken"]?.toString()?.trim('"'))
        assertFalse("stream-secret" in request.body)
    }

    @Test
    fun webRequestNeedsSignatureTimestamp() {
        val session = ExcorSession(null, "visitor", "US", "en")
        val web = selectClients(session, emptySet(), emptySet()).last()
        assertEquals(null, buildPlayerRequest(web, "abcdefghijk", session, "key", null, null))
    }

    @Test
    fun diagnosticStringsDoNotExposeSessionOrRequestSecrets() {
        val session = ExcorSession("SID=secret-cookie", "secret-visitor", "US", "en")
        val client = selectClients(session, emptySet(), emptySet()).first()
        val request = buildPlayerRequest(
            client, "abcdefghijk", session, "secret-api-key", 12345,
            PoTokenResult("secret-player-token", "secret-stream-token", "secret-visitor"),
        )
        assertNotNull(request)

        listOf(
            "secret-cookie", "secret-visitor", "secret-api-key", "secret-player-token", "secret-stream-token",
        ).forEach { secret ->
            assertFalse(secret in session.toString())
            assertFalse(secret in request.toString())
        }
    }
}
