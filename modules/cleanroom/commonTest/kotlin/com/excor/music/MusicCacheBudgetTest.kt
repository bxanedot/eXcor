package com.excor.music

import com.excor.catalog.ExcorCatalogProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Anonymous synthetic JSON only; no network or credentials. */
class MusicCacheBudgetTest {
    @Test
    fun defaultCacheEvictsLargeResponsesBeforeRetainingOverSixteenMiB() = runTest {
        var calls = 0
        val body = """{"value":"${"x".repeat(4 * 1024 * 1024 - 100)}"}"""
        val client = HttpClient(MockEngine {
            calls++
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val music = MusicHttp(client, nowMs = { 0L })
            val provider = ExcorCatalogProvider.DEEZER
            repeat(5) { music.getJson(provider, "https://api.deezer.com/track/$it") }
            music.getJson(provider, "https://api.deezer.com/track/4")
            assertEquals(5, calls, "the most recent response remains cached")
            music.getJson(provider, "https://api.deezer.com/track/0")
            assertEquals(6, calls, "the oldest large response must be evicted by the byte budget")
        } finally {
            client.close()
        }
    }

    @Test
    fun utf8BytesRatherThanCharacterCountDetermineEviction() = runTest {
        val fixture = CacheFixture(bytes = 19) // Each response has nine characters but ten UTF-8 bytes.
        try {
            fixture.read(0)
            fixture.read(1)
            fixture.read(1)
            assertEquals(2, fixture.calls)
            fixture.read(0)
            assertEquals(3, fixture.calls)
        } finally { fixture.close() }
    }

    @Test
    fun byteEvictionPreservesTheMostRecentlyReadEntry() = runTest {
        val fixture = CacheFixture(bytes = 20)
        try {
            fixture.read(0)
            fixture.read(1)
            fixture.read(0)
            fixture.read(2)
            fixture.read(0)
            assertEquals(3, fixture.calls)
            fixture.read(1)
            assertEquals(4, fixture.calls)
        } finally { fixture.close() }
    }

    @Test
    fun expiryAndClockRollbackReleaseTheRemovedEntriesByteWeight() = runTest {
        for (rollback in listOf(false, true)) {
            val fixture = CacheFixture(bytes = 20, ttlMs = 10)
            try {
                fixture.read(0)
                fixture.clock = 5
                fixture.read(1)
                fixture.clock = if (rollback) 3 else 11
                fixture.read(if (rollback) 1 else 0)
                assertEquals(3, fixture.calls)
                fixture.read(if (rollback) 0 else 1)
                assertEquals(3, fixture.calls, "a removed entry's weight must not evict a still-valid neighbor")
            } finally { fixture.close() }
        }
    }

    @Test
    fun zeroBudgetsAndResponsesLargerThanTheBudgetAreNotCached() = runTest {
        for ((bytes, entries) in listOf(0 to 256, 9 to 256, 20 to 0)) {
            val fixture = CacheFixture(bytes, entries)
            try {
                fixture.read(0)
                fixture.read(0)
                assertEquals(2, fixture.calls)
            } finally { fixture.close() }
        }
    }

    @Test
    fun entryLimitStillAppliesWhenTheByteBudgetHasSpace() = runTest {
        val fixture = CacheFixture(bytes = 20, entries = 1)
        try {
            fixture.read(0)
            fixture.read(1)
            fixture.read(0)
            assertEquals(3, fixture.calls)
        } finally { fixture.close() }
    }

    @Test
    fun concurrentReplacementDoesNotDoubleCountTheSameUrl() = runTest {
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            if (calls == 1) {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
            respond(SMALL_BODY, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val music = MusicHttp(client, nowMs = { 0L }, cacheResponseBytes = 20)
            val provider = ExcorCatalogProvider.DEEZER
            val first = async { music.getJson(provider, "https://api.deezer.com/track/0") }
            firstEntered.await()
            music.getJson(provider, "https://api.deezer.com/track/0")
            releaseFirst.complete(Unit)
            first.await()
            music.getJson(provider, "https://api.deezer.com/track/1")
            music.getJson(provider, "https://api.deezer.com/track/0")
            assertEquals(3, calls, "replacing the first URL must leave space for the second URL")
        } finally {
            releaseFirst.complete(Unit)
            client.close()
        }
    }

    @Test
    fun aThrowingHostClockDoesNotCorruptTheByteBudget() = runTest {
        val fixture = CacheFixture(bytes = 20)
        try {
            fixture.read(0)
            fixture.read(1)
            fixture.failClock = true
            assertFailsWith<IllegalStateException> { fixture.read(0) }
            fixture.failClock = false
            fixture.read(2)
            fixture.read(1)
            assertEquals(3, fixture.calls, "a failed clock lookup must not leave phantom cached bytes")
        } finally { fixture.close() }
    }

    private class CacheFixture(bytes: Int, entries: Int = 256, ttlMs: Long = 300_000) {
        var calls = 0
        var clock = 0L
        var failClock = false
        private val client = HttpClient(MockEngine {
            calls++
            respond(SMALL_BODY, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        private val music = MusicHttp(client, nowMs = { check(!failClock) { "synthetic clock failure" }; clock }, cacheEntries = entries,
            cacheTtlMs = ttlMs, cacheResponseBytes = bytes)

        suspend fun read(id: Int) {
            music.getJson(ExcorCatalogProvider.DEEZER, "https://api.deezer.com/track/$id")
        }
        fun close() = client.close()
    }

    private companion object {
        const val SMALL_BODY = """{"v":"é"}"""
    }
}
