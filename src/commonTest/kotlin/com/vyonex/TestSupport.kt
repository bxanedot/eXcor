package com.vyonex

import com.vyonex.internal.Engine
import com.vyonex.internal.primary.extraction.TokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.channels.Channel
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal fun fixture(name: String) = SystemFileSystem.source(Path(FIXTURES, name)).buffered().use { it.readString() }

internal fun fixtureJson(name: String): JsonObject = Json.parseToJsonElement(fixture(name)).jsonObject

/**
 * An engine served by the fixtures: player requests are answered by client name (WEB with [webFixture]),
 * media URLs with a 206 for their first byte as googlevideo sends it (403 when the URL's `c` client is in
 * [refusedClients], [mediaAnswer] when set), and [failing] paths with 500. Every request is added to [requests].
 */
internal fun fixtureEngine(
    host: FakeHost,
    refusedClients: Set<String> = emptySet(),
    failing: Set<String> = emptySet(),
    tokenProvider: TokenProvider? = null,
    webFixture: String = "web",
    requests: MutableList<HttpRequestData> = mutableListOf(),
    /** When set, the one player response every client gets. */
    playerFixture: String? = null,
    /** When set, how every media URL answers: a status and its headers. */
    mediaAnswer: Pair<HttpStatusCode, Headers>? = null,
    /** When set, the player response every client gets, as text (for responses built in a test). */
    playerBody: String? = null,
    /** When set, answers `browse` requests from their body. */
    browse: ((String) -> String)? = null,
    /** When set, answers watch-next (`next`) requests from their body. */
    next: ((String) -> String)? = null,
    /** How manifest URLs answer: a status and a body. */
    manifestAnswer: Pair<HttpStatusCode, String> = HttpStatusCode.OK to "#EXTM3U\n#EXT-X-INDEPENDENT-SEGMENTS\n",
) = Engine(
    host,
    tokenProvider = tokenProvider,
    http = HttpClient(MockEngine { request ->
        requests += request
        val url = request.url
        when {
            url.encodedPath in failing -> respond("", HttpStatusCode.InternalServerError)
            url.encodedPath == "/watch" -> respond(fixture("watch-page.html"))
            url.encodedPath == "/iframe_api" -> respond(fixture("iframe_api.js"))
            url.encodedPath == "/youtubei/v1/browse" && browse != null ->
                respond(browse((request.body as TextContent).text), headers = headersOf(HttpHeaders.ContentType, "application/json"))
            url.encodedPath == "/youtubei/v1/next" && next != null ->
                respond(next((request.body as TextContent).text), headers = headersOf(HttpHeaders.ContentType, "application/json"))
            url.encodedPath == "/api/timedtext" -> respond("WEBVTT\n\n00:00.000 --> 00:01.000\nNever gonna\n")
            url.encodedPath.startsWith("/s/player/dce23b30/") -> respond(fixture("player-dce23b30.js"))
            url.encodedPath == "/youtubei/v1/player" -> {
                val body = (request.body as TextContent).text
                // The embedded player is answered with the VISIONOS capture, whose formats have direct URLs.
                val client = mapOf("TVHTML5" to "tv_samsung", "VISIONOS" to "visionos", "WEB" to webFixture, "WEB_EMBEDDED_PLAYER" to "visionos")
                    .entries.first { "\"clientName\":\"${it.key}\"" in body }.value
                respond(playerBody ?: fixture(playerFixture ?: "player-response-$client.json"), headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
            url.host == "manifest.googlevideo.com" -> respond(manifestAnswer.second, manifestAnswer.first)
            url.host.endsWith(".googlevideo.com") -> {
                // The probe asks for one byte with the stream's own headers.
                assertEquals("bytes=0-0", request.headers[HttpHeaders.Range])
                assertNotNull(request.headers[HttpHeaders.UserAgent])
                when {
                    url.parameters["c"] in refusedClients -> respond("", HttpStatusCode.Forbidden)
                    mediaAnswer != null -> respond("x", mediaAnswer.first, mediaAnswer.second)
                    else -> respond(
                        "x",
                        HttpStatusCode.PartialContent,
                        headersOf(HttpHeaders.ContentRange to listOf("bytes 0-0/3433514"), HttpHeaders.ContentType to listOf("audio/webm")),
                    )
                }
            }
            else -> error("unexpected request to ${url.host}${url.encodedPath}")
        }
    }),
    store = null,
)

internal class FakeHost : VyonExHostServices {
    var now = 1_000_000L
    var enabled = true
    /** Lets warm-up be observed without it reaching the network. */
    var failSession = false
    var cookie: String? = null
    var visitorData: String? = null
    var region = "US"
    var language = "en"
    val sessionCalls = Channel<Unit>(Channel.UNLIMITED)
    val logs = mutableListOf<Pair<VyonExLogLevel, String>>()

    override suspend fun session(): VyonExSession {
        sessionCalls.trySend(Unit)
        check(!failSession) { "offline test" }
        return VyonExSession(cookie = cookie, visitorData = visitorData, region = region, language = language)
    }

    override val configStore = object : VyonExConfigStore {
        override val enabled get() = this@FakeHost.enabled
        override fun getString(key: String): String? = null
        override fun getLong(key: String): Long? = null
        override fun putString(key: String, value: String) {}
        override fun putLong(key: String, value: Long) {}
    }

    override fun nowMs() = now

    override fun log(level: VyonExLogLevel, message: String, trackId: String?) {
        logs += level to message
    }
}
