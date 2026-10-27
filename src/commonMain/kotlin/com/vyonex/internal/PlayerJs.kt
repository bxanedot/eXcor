package com.vyonex.internal

import com.dokar.quickjs.QuickJs
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive

/** Signature timestamp the player API expects for this player script. */
internal fun signatureTimestamp(playerCode: String): Int? =
    Regex("""(?:signatureTimestamp|sts)\s*:\s*(\d{5})""").find(playerCode)?.groupValues?.get(1)?.toInt()

// Parsing the player recurses deeper than QuickJS's default 256 KB stack limit allows on iOS, so
// all JS runs on a thread with a large stack and a matching limit (the limit stays well below it).
internal const val JS_THREAD_STACK_BYTES = 16L shl 20
private const val JS_STACK_LIMIT_BYTES = 8L shl 20

/** Runs [block] on a fresh thread with [JS_THREAD_STACK_BYTES] of stack. */
internal expect suspend fun <T> onJsThread(block: suspend () -> T): T

private fun newQuickJs() = QuickJs.create(Dispatchers.Default).apply { maxStackSize = JS_STACK_LIMIT_BYTES }

/**
 * Turns the raw player script into a self-contained script that exposes its `n` and `sig`
 * functions, using yt-dlp's EJS solver. Parsing the player is the expensive step, so the
 * result is what gets cached per player version.
 */
internal suspend fun preparePlayer(playerCode: String): String = onJsThread {
    val js = newQuickJs()
    try {
        js.evaluate<Any?>("$EJS_LIB\nObject.assign(globalThis, lib);\n$EJS_CORE\n0")
        js.evaluate<String>(
            "jsc({type:'player',player:${quote(playerCode)},requests:[],output_preprocessed:true}).preprocessed_player",
        )
    } finally {
        js.close()
    }
}

/** A loaded prepared player. QuickJS contexts are single-threaded, hence the lock. */
internal class PlayerJs private constructor(private val js: QuickJs) {
    private val lock = Mutex()

    suspend fun solve(type: String, challenge: String): String = lock.withLock {
        onJsThread { js.evaluate<String>("_vx.$type(${quote(challenge)})") }
    }

    suspend fun close() = lock.withLock { js.close() }

    companion object {
        suspend fun load(prepared: String): PlayerJs = onJsThread {
            val js = newQuickJs()
            try {
                js.evaluate<Any?>("var _vx={n:null,sig:null};(function(_result){\n$prepared\n})(_vx);0")
            } catch (e: Throwable) {
                js.close()
                throw e
            }
            PlayerJs(js)
        }
    }
}

private fun quote(s: String) = JsonPrimitive(s).toString()

/** Platform storage for prepared players (JVM only; iOS keeps them in memory). */
internal interface PlayerStore {
    suspend fun read(key: String): String?
    suspend fun write(key: String, value: String)
    suspend fun delete(key: String)
}

internal class Player(val id: String, val sts: Int?, val js: PlayerJs)

/** Fetches and prepares player scripts, keeping one version loaded at a time. */
internal class Players(private val http: HttpClient, private val store: PlayerStore?) {
    private val lock = Mutex()
    private var currentId: String? = null
    private var loaded: Player? = null

    /** The player version YouTube currently serves, found through the iframe API as yt-dlp does. */
    suspend fun current(): Player {
        val id = lock.withLock { currentId }
            ?: playerIdOf(http.text("$YT_ORIGIN/iframe_api")) ?: throw IllegalStateException("no player version in iframe API")
        return get(id).also { lock.withLock { currentId = id } }
    }

    suspend fun get(id: String): Player = lock.withLock {
        loaded?.takeIf { it.id == id }?.let { return@withLock it }
        // The key names the solver version, so an upgrade never reuses another version's output.
        val key = "$id-ejs-0.8.0"
        val prepared = store?.read(key) ?: run {
            // yt-dlp also always uses the main variant, so every page shares one cache entry.
            val code = http.text("$YT_ORIGIN/s/player/$id/player_ias.vflset/en_US/base.js")
            "${signatureTimestamp(code) ?: ""}\n${preparePlayer(code)}".also { store?.write(key, it) }
        }
        val player = Player(id, prepared.substringBefore('\n').toIntOrNull(), PlayerJs.load(prepared.substringAfter('\n')))
        loaded?.js?.close()
        loaded = player
        player
    }

    suspend fun invalidate() = lock.withLock {
        loaded?.let {
            it.js.close()
            store?.delete("${it.id}-ejs-0.8.0")
        }
        loaded = null
        currentId = null
    }

    /** Frees the loaded player without touching the store. */
    suspend fun close() = lock.withLock {
        loaded?.js?.close()
        loaded = null
        currentId = null
    }
}

internal suspend fun HttpClient.text(url: String, block: HttpRequestBuilder.() -> Unit = {}): String {
    val response = get(url, block)
    // Only the path: query strings are not logged.
    check(response.status.isSuccess()) { "HTTP ${response.status.value} for ${Url(url).encodedPath}" }
    return response.bodyAsText()
}
