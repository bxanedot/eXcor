package com.excor.music

import com.excor.catalog.ExcorCatalogProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

/** Synthetic catalog responses; no real media, sessions or network access. */
class MusicCacheClockTest {
    @Test
    fun clockRollbackInvalidatesCachedCatalogResponse() = runTest {
        var clock = 1_000L
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            respond(
                """{"revision":$calls}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })
        try {
            val music = MusicHttp(client, nowMs = { clock }, cacheTtlMs = 60_000)
            val url = "https://api.deezer.com/track/123"
            val provider = ExcorCatalogProvider.DEEZER
            assertEquals(Json.parseToJsonElement("""{"revision":1}"""), music.getJson(provider, url))
            assertEquals(Json.parseToJsonElement("""{"revision":1}"""), music.getJson(provider, url))
            assertEquals(1, calls, "an unchanged clock must preserve the cache hit")

            clock = 500L
            assertEquals(Json.parseToJsonElement("""{"revision":2}"""), music.getJson(provider, url))
            assertEquals(2, calls, "a clock rollback must invalidate the old entry")
            assertEquals(Json.parseToJsonElement("""{"revision":2}"""), music.getJson(provider, url))
            assertEquals(2, calls, "the refreshed entry remains cacheable")
        } finally {
            client.close()
        }
    }
}
