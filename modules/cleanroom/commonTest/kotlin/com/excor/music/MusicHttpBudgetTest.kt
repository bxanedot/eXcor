package com.excor.music

import com.excor.catalog.ExcorCatalogProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MusicHttpBudgetTest {
    @Test
    fun keylessItunesBudgetIgnoresWallClockJumpsAndTheMusicApiDoesNot() = runTest {
        var wallClockOffset = 0L
        val client = HttpClient(MockEngine { respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) })
        val http = MusicHttp(
            client,
            { testScheduler.currentTime + wallClockOffset },
            requestTimeMs = { testScheduler.currentTime },
        )
        repeat(21) { http.getJson(ExcorCatalogProvider.APPLE_MUSIC, "https://api.music.apple.com/v1/catalog/us/songs/$it", bearer = "t") }
        assertTrue(testScheduler.currentTime < 60_000, "the Apple Music API keeps its own, faster budget")

        val start = testScheduler.currentTime
        repeat(20) { http.getJson(ExcorCatalogProvider.APPLE_MUSIC, "https://itunes.apple.com/lookup?id=$it") }
        assertEquals(start, testScheduler.currentTime, "the first 20 iTunes calls in a minute go out at once")
        wallClockOffset = 60_000
        http.getJson(ExcorCatalogProvider.APPLE_MUSIC, "https://itunes.apple.com/lookup?id=20")
        assertEquals(60_000, testScheduler.currentTime - start, "a wall-clock jump forward must not release the budget early")

        repeat(19) { http.getJson(ExcorCatalogProvider.APPLE_MUSIC, "https://itunes.apple.com/lookup?id=${it + 21}") }
        wallClockOffset = -60_000
        val nextWindow = testScheduler.currentTime
        http.getJson(ExcorCatalogProvider.APPLE_MUSIC, "https://itunes.apple.com/lookup?id=40")
        assertEquals(60_000, testScheduler.currentTime - nextWindow, "a wall-clock rollback must not extend the budget window")
        assertEquals(62, http.requests)
        client.close()
    }

    @Test
    fun explicitCacheOptInCannotReuseAuthorizedResponses() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine {
            requests++
            respond(
                """{"request":$requests}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })
        val http = MusicHttp(client, { testScheduler.currentTime })
        val url = "https://api.music.apple.com/v1/catalog/us/songs/42"

        http.getJson(ExcorCatalogProvider.APPLE_MUSIC, url, bearer = "private", cacheable = true)
        http.getJson(
            ExcorCatalogProvider.APPLE_MUSIC,
            url,
            headers = mapOf("Music-User-Token" to "private"),
            cacheable = true,
        )
        http.getJson(ExcorCatalogProvider.APPLE_MUSIC, url)
        http.getJson(ExcorCatalogProvider.APPLE_MUSIC, url)

        assertEquals(3, requests)
        client.close()
    }
}
