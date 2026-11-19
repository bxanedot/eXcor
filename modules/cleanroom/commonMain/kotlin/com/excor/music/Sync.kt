package com.excor.music

import com.excor.catalog.ExcorCatalogError
import com.excor.catalog.ExcorCatalogException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** One entry of a playlist or library; [key] identifies the item within its provider. */
data class ExcorSyncEntry(val key: String, val track: ExcorMusicTrack? = null, val addedAt: String? = null) {
    override fun toString(): String =
            "ExcorSyncEntry(key=present, track=${track != null}, addedAt=${addedAt != null})"
}

data class ExcorSyncPage(val entries: List<ExcorSyncEntry>, val next: String?, val snapshot: String? = null) {
    override fun toString(): String =
            "ExcorSyncPage(entries=${entries.size}, next=${next != null}, snapshot=${snapshot != null})"
}

/** Pages of a collection in the provider's order; [cursor] null asks for the first page. */
fun interface ExcorSyncSource {
    @Throws(Throwable::class)
    suspend fun page(cursor: String?): ExcorSyncPage
}

/**
 * What a previous sync saw: the snapshot marker and the keys in order. An interrupted sync keeps [resumeCursor] and
 * the keys read so far in [partialKeys], so the next run continues from there. [encode] gives a string to persist.
 */
data class ExcorSyncState(
    val snapshot: String?,
    val keys: List<String>,
    val resumeCursor: String? = null,
    val partialKeys: List<String> = emptyList(),
    val partialSnapshot: String? = null,
) {
    fun encode(): String = buildJsonObject {
        put("v", 1)
        snapshot?.let { put("snapshot", it) }
        putJsonArray("keys") { keys.forEach { add(JsonPrimitive(it)) } }
        resumeCursor?.let { put("resume", it) }
        partialSnapshot?.let { put("partialSnapshot", it) }
        putJsonArray("partial") { partialKeys.forEach { add(JsonPrimitive(it)) } }
    }.toString()

    companion object {
        /** Null for a string that is not an encoded state, so a corrupt store starts a full sync instead of failing. */
        fun decode(value: String): ExcorSyncState? = runCatching {
            val o = Json.parseToJsonElement(value).jsonObject
            if ((o["v"] as? JsonPrimitive)?.contentOrNull != "1") return null
            fun text(name: String) = (o[name] as? JsonPrimitive)?.contentOrNull
            fun list(name: String) = (o[name] as? JsonArray).orEmpty().map { (it as JsonPrimitive).content }
            ExcorSyncState(text("snapshot"), list("keys"), text("resume"), list("partial"), text("partialSnapshot"))
        }.getOrNull()
    }
    override fun toString(): String =
            "ExcorSyncState(snapshot=${snapshot != null}, keys=${keys.size}, resumeCursor=${resumeCursor != null}, " +
                "partialKeys=${partialKeys.size}, partialSnapshot=${partialSnapshot != null})"
}

data class ExcorSyncResult(
    val state: ExcorSyncState,
    val unchanged: Boolean,
    val added: List<String>,
    val removed: List<String>,
    /** Keys still present whose relative order changed (outside the longest run left in place). */
    val moved: List<String>,
    /** Keys that appeared more than once; only the first occurrence is kept. */
    val duplicates: List<String>,
    val complete: Boolean,
    val pagesFetched: Int,
    /** Why the sync stopped early; the state then holds the resume point. */
    val error: Throwable? = null,
) {
    override fun toString(): String =
            "ExcorSyncResult(unchanged=$unchanged, added=${added.size}, removed=${removed.size}, moved=${moved.size}, " +
                "duplicates=${duplicates.size}, complete=$complete, pagesFetched=$pagesFetched, error=${error != null})"
}

data class ExcorRetryPolicy(val maxAttempts: Int = 3, val initialDelayMs: Long = 500, val maxDelayMs: Long = 8_000)

/**
 * Provider-agnostic incremental sync. Memory holds keys, not tracks: entries go to [onPage] as they arrive. A
 * [currentSnapshot] equal to the previous complete snapshot answers without a request. If the snapshot changes while
 * paging, the sync restarts once from the first page.
 */
class ExcorSyncEngine(private val retry: ExcorRetryPolicy = ExcorRetryPolicy()) {
    @Throws(Throwable::class)
    suspend fun sync(
        source: ExcorSyncSource,
        previous: ExcorSyncState?,
        currentSnapshot: String? = null,
        onPage: suspend (ExcorSyncPage) -> Unit = {},
    ): ExcorSyncResult {
        val before = previous?.keys.orEmpty()
        if (previous != null && previous.resumeCursor == null && currentSnapshot != null && currentSnapshot == previous.snapshot) {
            return ExcorSyncResult(previous, true, emptyList(), emptyList(), emptyList(), emptyList(), true, 0)
        }
        var restarted = false
        var pages = 0
        val resuming = previous?.resumeCursor != null &&
            (currentSnapshot == null || currentSnapshot == previous.partialSnapshot)
        var cursor = if (resuming) previous!!.resumeCursor else null
        val keys = ArrayList<String>(if (resuming) previous!!.partialKeys else emptyList())
        val seen = HashSet(keys)
        val requestedCursors = mutableSetOf<String?>()
        val duplicates = linkedSetOf<String>()
        var snapshot = if (resuming) previous!!.partialSnapshot else null
        while (true) {
            val requestCursor = cursor.takeUnless { it == FIRST_PAGE }
            if (!requestedCursors.add(requestCursor)) {
                val error = ExcorCatalogException(null, ExcorCatalogError.PROVIDER_UNAVAILABLE)
                val partial = ExcorSyncState(previous?.snapshot, before, cursor ?: FIRST_PAGE, keys, snapshot)
                return ExcorSyncResult(partial, false, emptyList(), emptyList(), emptyList(), duplicates.toList(), false, pages, error)
            }
            val page = try {
                fetch(source, cursor)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Throwable) {
                val partial = ExcorSyncState(previous?.snapshot, before, cursor ?: FIRST_PAGE, keys, snapshot)
                return ExcorSyncResult(partial, false, emptyList(), emptyList(), emptyList(), duplicates.toList(), false, pages, e)
            }
            pages++
            if (snapshot == null) snapshot = page.snapshot
            else if (page.snapshot != null && page.snapshot != snapshot) {
                if (restarted) {
                    val e = ExcorCatalogException(null, ExcorCatalogError.PROVIDER_UNAVAILABLE)
                    val partial = ExcorSyncState(previous?.snapshot, before, null, emptyList(), null)
                    return ExcorSyncResult(partial, false, emptyList(), emptyList(), emptyList(), emptyList(), false, pages, e)
                }
                restarted = true
                keys.clear(); seen.clear(); duplicates.clear()
                snapshot = null
                cursor = null
                requestedCursors.clear()
                continue
            }
            val fresh = page.entries.filter { entry ->
                val first = seen.add(entry.key)
                if (first) keys += entry.key else duplicates += entry.key
                first
            }
            onPage(page.copy(entries = fresh))
            cursor = page.next ?: break
        }
        val state = ExcorSyncState(snapshot ?: currentSnapshot, keys)
        val beforeSet = before.toSet()
        val afterSet = keys.toSet()
        val added = keys.filter { it !in beforeSet }
        val removed = before.filter { it !in afterSet }
        val moved = movedKeys(before.filter { it in afterSet }, keys.filter { it in beforeSet })
        val unchanged = previous != null && added.isEmpty() && removed.isEmpty() && moved.isEmpty()
        return ExcorSyncResult(state, unchanged, added, removed, moved, duplicates.toList(), true, pages)
    }

    private suspend fun fetch(source: ExcorSyncSource, cursor: String?): ExcorSyncPage {
        var wait = retry.initialDelayMs
        var attempt = 1
        while (true) {
            try {
                return source.page(cursor.takeUnless { it == FIRST_PAGE })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: ExcorCatalogException) {
                if (attempt >= retry.maxAttempts || e.error !in RETRYABLE) throw e
            }
            delay(wait)
            wait = (wait * 2).coerceAtMost(retry.maxDelayMs)
            attempt++
        }
    }

    private companion object {
        // A resume point before any page was read.
        const val FIRST_PAGE = "\u0000first"
        val RETRYABLE = setOf(ExcorCatalogError.RATE_LIMITED, ExcorCatalogError.PROVIDER_UNAVAILABLE, ExcorCatalogError.NETWORK_FAILURE)
    }
}

/**
 * Keys of [after] that are outside a longest subsequence kept in [before]'s order: the fewest moves that explain the
 * new order. Both lists hold the same distinct keys. O(n log n).
 */
internal fun movedKeys(before: List<String>, after: List<String>): List<String> {
    val position = before.withIndex().associate { it.value to it.index }
    val sequence = after.map { position.getValue(it) }
    // Patience sorting with back-pointers.
    val tails = IntArray(sequence.size)
    val previous = IntArray(sequence.size) { -1 }
    var length = 0
    for (i in sequence.indices) {
        var lo = 0
        var hi = length
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sequence[tails[mid]] < sequence[i]) lo = mid + 1 else hi = mid
        }
        if (lo > 0) previous[i] = tails[lo - 1]
        tails[lo] = i
        if (lo == length) length++
    }
    val kept = HashSet<Int>()
    var k = if (length > 0) tails[length - 1] else -1
    while (k >= 0) { kept += k; k = previous[k] }
    return after.filterIndexed { i, _ -> i !in kept }
}
