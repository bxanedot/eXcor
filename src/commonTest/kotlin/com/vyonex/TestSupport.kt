package com.vyonex

import com.vyonex.internal.Engine
import com.vyonex.internal.primary.extraction.TokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
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
 * media URLs with 206 (403 when the URL's `c` client is in [refusedClients]), and [failing] paths with 500.
 * Every request is added to [requests].
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
            url.encodedPath.startsWith("/s/player/dce23b30/") -> respond(fixture("player-dce23b30.js"))
            url.encodedPath == "/youtubei/v1/player" -> {
                val body = (request.body as TextContent).text
                val client = mapOf("TVHTML5" to "tv_samsung", "VISIONOS" to "visionos", "WEB" to webFixture)
                    .entries.first { "\"clientName\":\"${it.key}\"" in body }.value
                respond(fixture(playerFixture ?: "player-response-$client.json"), headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
            url.host.endsWith(".googlevideo.com") -> {
                // The probe asks for one byte with the stream's own headers.
                assertEquals("bytes=0-0", request.headers[HttpHeaders.Range])
                assertNotNull(request.headers[HttpHeaders.UserAgent])
                val refused = url.parameters["c"] in refusedClients
                respond("x", if (refused) HttpStatusCode.Forbidden else HttpStatusCode.PartialContent)
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
    val sessionCalls = Channel<Unit>(Channel.UNLIMITED)
    val logs = mutableListOf<Pair<VyonExLogLevel, String>>()

    override suspend fun session(): VyonExSession {
        sessionCalls.trySend(Unit)
        check(!failSession) { "offline test" }
        return VyonExSession(cookie = cookie, visitorData = visitorData, region = "US", language = "en")
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
