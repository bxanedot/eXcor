package com.excor.music

import com.excor.catalog.ExcorCatalogError
import com.excor.catalog.ExcorCatalogException
import com.excor.catalog.ExcorCatalogProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SyncAuthTest {
    private class Pages(var keys: List<String>, var snapshot: String = "s1", val pageSize: Int = 2) : ExcorSyncSource {
        var calls = 0
        var failAt: Int? = null
        var failures = 0
        var error = ExcorCatalogError.RATE_LIMITED
        override suspend fun page(cursor: String?): ExcorSyncPage {
            calls++
            val start = cursor?.toInt() ?: 0
            if (failAt == start && failures > 0) { failures--; throw ExcorCatalogException(ExcorCatalogProvider.SPOTIFY, error) }
            val end = minOf(start + pageSize, keys.size)
            return ExcorSyncPage(keys.subList(start, end).map { ExcorSyncEntry(it) }, if (end < keys.size) end.toString() else null, snapshot)
        }
    }

    private val engine = ExcorSyncEngine(ExcorRetryPolicy(maxAttempts = 3, initialDelayMs = 1))

    @Test
    fun firstSyncThenIncrementalChanges() = runTest {
        val source = Pages(listOf("a", "b", "c", "d", "e"))
        val first = engine.sync(source, null)
        assertTrue(first.complete)
        assertEquals(listOf("a", "b", "c", "d", "e"), first.added)
        assertEquals(3, first.pagesFetched)

        source.keys = listOf("b", "a", "c", "e", "f")
        source.snapshot = "s2"
        val second = engine.sync(source, first.state, currentSnapshot = "s2")
        assertEquals(listOf("f"), second.added)
        assertEquals(listOf("d"), second.removed)
        assertEquals(1, second.moved.size)
        assertFalse(second.unchanged)
        assertEquals(listOf("b", "a", "c", "e", "f"), second.state.keys)
    }

    @Test
    fun unchangedSnapshotNeedsNoRequest() = runTest {
        val source = Pages(listOf("a", "b", "c"))
        val first = engine.sync(source, null)
        source.calls = 0
        val again = engine.sync(source, ExcorSyncState.decode(first.state.encode()), currentSnapshot = "s1")
        assertTrue(again.unchanged)
        assertEquals(0, source.calls)
    }

    @Test
    fun duplicatesAreDroppedAndReported() = runTest {
        val seen = mutableListOf<String>()
        val result = engine.sync(Pages(listOf("a", "b", "a", "c", "b")), null) { page -> seen += page.entries.map { it.key } }
        assertEquals(listOf("a", "b", "c"), seen)
        assertEquals(listOf("a", "b"), result.duplicates)
    }

    @Test
    fun repeatedPageCursorStopsWithoutFetchingTheSamePageForever() = runTest {
        var calls = 0
        var cursorACalls = 0
        val source = object : ExcorSyncSource {
            override suspend fun page(cursor: String?): ExcorSyncPage {
                calls++
                return when (cursor) {
                    null -> ExcorSyncPage(listOf(ExcorSyncEntry("a")), "a", "s1")
                    "a" -> if (++cursorACalls == 1) {
                        ExcorSyncPage(listOf(ExcorSyncEntry("b")), "b", "s1")
                    } else {
                        ExcorSyncPage(listOf(ExcorSyncEntry("d")), null, "s1")
                    }
                    else -> ExcorSyncPage(listOf(ExcorSyncEntry("c")), "a", "s1")
                }
            }
        }

        val result = engine.sync(source, null)

        assertFalse(result.complete)
        assertEquals(listOf("a", "b", "c"), result.state.partialKeys)
        assertEquals("a", result.state.resumeCursor)
        assertEquals(3, calls)
        assertEquals(ExcorCatalogError.PROVIDER_UNAVAILABLE, (result.error as ExcorCatalogException).error)
    }

    @Test
    fun retriesThenResumesAfterAnInterruption() = runTest {
        val source = Pages(listOf("a", "b", "c", "d", "e"))
        source.failAt = 2; source.failures = 2
        assertTrue(engine.sync(source, null).complete) // two rate limits, then success

        source.failures = 5
        val broken = engine.sync(source, null)
        assertFalse(broken.complete)
        assertNotNull(broken.error)
        assertEquals("2", broken.state.resumeCursor)
        assertEquals(listOf("a", "b"), broken.state.partialKeys)

        source.failures = 0
        source.calls = 0
        val resumed = engine.sync(source, ExcorSyncState.decode(broken.state.encode()))
        assertTrue(resumed.complete)
        assertEquals(2, source.calls) // pages at 2 and 4 only
        assertEquals(listOf("a", "b", "c", "d", "e"), resumed.state.keys)
    }

    @Test
    fun nonRetryableErrorStopsAtOnce() = runTest {
        val source = Pages(listOf("a", "b"))
        source.failAt = 0; source.failures = 9; source.error = ExcorCatalogError.AUTHENTICATION_REQUIRED
        val result = engine.sync(source, null)
        assertEquals(1, source.calls)
        assertEquals(ExcorCatalogError.AUTHENTICATION_REQUIRED, (result.error as ExcorCatalogException).error)
    }

    @Test
    fun snapshotChangeMidSyncRestartsOnce() = runTest {
        val source = object : ExcorSyncSource {
            var call = 0
            override suspend fun page(cursor: String?): ExcorSyncPage {
                call++
                return when {
                    call == 1 -> ExcorSyncPage(listOf(ExcorSyncEntry("a")), "1", "old")
                    cursor == null -> ExcorSyncPage(listOf(ExcorSyncEntry("x")), "1", "new")
                    else -> ExcorSyncPage(listOf(ExcorSyncEntry("y")), null, "new")
                }
            }
        }
        val result = engine.sync(source, null)
        assertTrue(result.complete)
        assertEquals(listOf("x", "y"), result.state.keys)
        assertEquals("new", result.state.snapshot)
    }

    @Test
    fun movedKeysAreTheMinimum() {
        assertEquals(emptyList(), movedKeys(listOf("a", "b", "c"), listOf("a", "b", "c")))
        assertEquals(listOf("c"), movedKeys(listOf("a", "b", "c"), listOf("c", "a", "b")))
        assertEquals(2, movedKeys(listOf("a", "b", "c", "d"), listOf("d", "c", "a", "b")).size)
        assertNull(ExcorSyncState.decode("not json"))
    }

    @Test
    fun sha256AndPkceKnownAnswers() {
        fun hex(b: ByteArray) = b.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hex(sha256(ByteArray(0))))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex(sha256("abc".encodeToByteArray())))
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            hex(sha256("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray())),
        )
        // RFC 7636 appendix B.
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", ExcorPkce.of("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk").challenge)
        val created = ExcorPkce.create()
        assertEquals(64, created.verifier.length)
        assertFalse(created.verifier in created.toString())
    }

    private class MemoryStore : ExcorTokenStore {
        val tokens = mutableMapOf<ExcorCatalogProvider, ExcorOAuthToken>()
        override suspend fun read(provider: ExcorCatalogProvider) = tokens[provider]
        override suspend fun write(provider: ExcorCatalogProvider, token: ExcorOAuthToken) { tokens[provider] = token }
        override suspend fun delete(provider: ExcorCatalogProvider) { tokens.remove(provider) }
    }

    @Test
    fun tokenManagerRefreshesKeepsProvidersApartAndForgetsRevokedTokens() = runTest {
        val store = MemoryStore()
        var now = 1_000_000L
        var refreshes = 0
        var revoke = false
        val manager = ExcorTokenManager(store, { provider, token ->
            refreshes++
            if (revoke) throw ExcorCatalogException(provider, ExcorCatalogError.INVALID_CREDENTIAL)
            assertEquals("refresh-1", token.refreshToken)
            ExcorOAuthToken("access-2", expiresAtMs = now + 3_600_000)
        }, { now })

        assertNull(manager.accessToken(ExcorCatalogProvider.SPOTIFY))
        manager.connect(ExcorCatalogProvider.SPOTIFY, ExcorOAuthToken("access-1", "refresh-1", now + 3_600_000))
        assertEquals("access-1", manager.accessToken(ExcorCatalogProvider.SPOTIFY))
        assertNull(manager.accessToken(ExcorCatalogProvider.DEEZER))

        now += 3_590_000 // within the refresh margin
        assertEquals("access-2", manager.accessToken(ExcorCatalogProvider.SPOTIFY))
        assertEquals(1, refreshes)
        assertEquals("refresh-1", store.tokens[ExcorCatalogProvider.SPOTIFY]?.refreshToken)
        assertFalse("access-2" in store.tokens.getValue(ExcorCatalogProvider.SPOTIFY).toString())

        now += 3_600_000
        revoke = true
        assertFailsWith<ExcorCatalogException> { manager.accessToken(ExcorCatalogProvider.SPOTIFY) }
        assertFalse(manager.isConnected(ExcorCatalogProvider.SPOTIFY))
    }
}
