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
    fun keylessItunesWaitsForItsPerMinuteLimitAndTheMusicApiDoesNot() = runTest {
        val client = HttpClient(MockEngine { respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) })
        val http = MusicHttp(client, { testScheduler.currentTime })
        repeat(21) { http.getJson(ExcorCatalogProvider.APPLE_MUSIC, "https://api.music.apple.com/v1/catalog/us/songs/$it", bearer = "t") }
        assertTrue(testScheduler.currentTime < 60_000, "the Apple Music API keeps its own, faster budget")

        val start = testScheduler.currentTime
        repeat(20) { http.getJson(ExcorCatalogProvider.APPLE_MUSIC, "https://itunes.apple.com/lookup?id=$it") }
        assertEquals(start, testScheduler.currentTime, "the first 20 iTunes calls in a minute go out at once")
        http.getJson(ExcorCatalogProvider.APPLE_MUSIC, "https://itunes.apple.com/lookup?id=20")
        assertTrue(testScheduler.currentTime - start >= 60_000, "the 21st waits for the minute window")
        assertEquals(42, http.requests)
        client.close()
    }
}
