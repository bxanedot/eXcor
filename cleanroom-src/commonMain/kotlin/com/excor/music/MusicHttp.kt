package com.excor.music

import com.excor.catalog.ExcorCatalogError
import com.excor.catalog.ExcorCatalogException
import com.excor.catalog.ExcorCatalogProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The music providers' shared HTTP: bounded bodies, JSON only, one retry after a short Retry-After, a per-provider
 * request budget, and a small cache for anonymous catalog reads. Authorized responses are never cached, so one
 * account's data cannot reach another.
 */
internal class MusicHttp(
    private val http: HttpClient,
    private val nowMs: () -> Long,
    private val cacheEntries: Int = 256,
    private val cacheTtlMs: Long = 5 * 60_000,
) {
    private val budgets = ExcorCatalogProvider.entries.associateWith { RequestBudget(budgetOf(it), nowMs) }
    private val itunesBudget = RequestBudget(ITUNES_BUDGET, nowMs)
    private val cache = LinkedHashMap<String, Pair<Long, JsonElement>>()
    private val cacheLock = Mutex()
    /** Requests sent so far, for benchmarks. */
    val requests: Int get() = budgets.values.sumOf { it.taken } + itunesBudget.taken

    suspend fun getJson(
        provider: ExcorCatalogProvider,
        url: String,
        bearer: String? = null,
        headers: Map<String, String> = emptyMap(),
        cacheable: Boolean = bearer == null && headers.isEmpty(),
    ): JsonElement {
        val cacheable = cacheable &&
            bearer == null &&
            headers.isEmpty() &&
            currentCoroutineContext()[FreshReadsKey] == null
        if (cacheable) cached(url)?.let { return it }
        val element = send(provider, url) {
            http.get(url) {
                header(HttpHeaders.Accept, "application/json")
                bearer?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                headers.forEach { (k, v) -> header(k, v) }
            }
        }
        if (cacheable) store(url, element)
        return element
    }

    suspend fun postForm(provider: ExcorCatalogProvider, url: String, form: Map<String, String>, headers: Map<String, String> = emptyMap()): JsonElement =
        send(provider, url) {
            http.post(url) {
                header(HttpHeaders.Accept, "application/json")
                headers.forEach { (k, v) -> header(k, v) }
                setBody(FormDataContent(Parameters.build { form.forEach { (k, v) -> append(k, v) } }))
            }
        }

    /** A command (PUT, POST) with an optional JSON body; an empty success returns an empty object. Never cached. */
    suspend fun command(
        provider: ExcorCatalogProvider,
        method: HttpMethod,
        url: String,
        bearer: String,
        json: String? = null,
        contentType: ContentType = ContentType.Application.Json,
    ): JsonElement =
        send(provider, url) {
            http.request(url) {
                this.method = method
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.Authorization, "Bearer $bearer")
                setBody(TextContent(json ?: "", contentType))
            }
        }

    private suspend fun send(provider: ExcorCatalogProvider, url: String, call: suspend () -> HttpResponse): JsonElement {
        var retried = false
        // The keyless iTunes Search/Lookup API has its own published limit, separate from the Apple Music API's.
        val budget = if (runCatching { Url(url).host }.getOrNull().equals(ITUNES_HOST, ignoreCase = true)) itunesBudget else budgets.getValue(provider)
        while (true) {
            budget.take()
            val response = try {
                call()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                throw ExcorCatalogException(provider, ExcorCatalogError.NETWORK_FAILURE)
            }
            val status = response.status.value
            if (status == 429 && !retried) {
                val wait = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()
                response.bodyAsChannel().cancel()
                if (wait != null && wait in 0..MAX_RETRY_AFTER_S) {
                    retried = true
                    delay(wait * 1_000)
                    continue
                }
                throw ExcorCatalogException(provider, ExcorCatalogError.RATE_LIMITED, status)
            }
            return read(provider, response)
        }
    }

    private suspend fun read(provider: ExcorCatalogProvider, response: HttpResponse): JsonElement {
        val status = response.status.value
        if (status == 204) {
            response.bodyAsChannel().cancel()
            return JsonObject(emptyMap())
        }
        if (!response.status.isSuccess()) {
            response.bodyAsChannel().cancel()
            throw ExcorCatalogException(provider, errorOf(status), status)
        }
        if (response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let { it > MAX_BYTES } == true) {
            response.bodyAsChannel().cancel()
            throw ExcorCatalogException(provider, ExcorCatalogError.RESPONSE_TOO_LARGE)
        }
        val type = response.headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim()?.lowercase()
        if (type == null && response.headers[HttpHeaders.ContentLength] == "0") {
            response.bodyAsChannel().cancel()
            return JsonObject(emptyMap())
        }
        if (type != "application/json" && type != "text/javascript") {
            response.bodyAsChannel().cancel()
            throw ExcorCatalogException(provider, ExcorCatalogError.MALFORMED_RESPONSE)
        }
        val channel = response.bodyAsChannel()
        val bytes = try {
            channel.readBuffer(MAX_BYTES + 1L).readByteArray()
        } finally {
            channel.cancel()
        }
        if (bytes.size > MAX_BYTES) throw ExcorCatalogException(provider, ExcorCatalogError.RESPONSE_TOO_LARGE)
        // Commands such as Spotify's library writes succeed with an empty body.
        if (bytes.all { it.toInt().toChar().isWhitespace() }) return JsonObject(emptyMap())
        return try {
            Json.parseToJsonElement(bytes.decodeToString())
        } catch (_: Exception) {
            throw ExcorCatalogException(provider, ExcorCatalogError.MALFORMED_RESPONSE)
        }
    }

    private suspend fun cached(url: String): JsonElement? = cacheLock.withLock {
        val (at, value) = cache.remove(url) ?: return@withLock null
        // A wall-clock rollback must not keep a cached catalog response alive indefinitely.
        val age = nowMs() - at
        if (age < 0 || age > cacheTtlMs) return@withLock null
        cache[url] = at to value // move to the most recent end
        value
    }

    private suspend fun store(url: String, value: JsonElement) = cacheLock.withLock {
        cache.remove(url)
        cache[url] = nowMs() to value
        while (cache.size > cacheEntries) cache.remove(cache.keys.first())
    }

    companion object {
        const val MAX_BYTES = 4 * 1024 * 1024
        const val MAX_RETRY_AFTER_S = 10L

        fun errorOf(status: Int): ExcorCatalogError = when (status) {
            400 -> ExcorCatalogError.INVALID_REQUEST
            401 -> ExcorCatalogError.AUTHENTICATION_REQUIRED
            403 -> ExcorCatalogError.ACCESS_DENIED
            404 -> ExcorCatalogError.NOT_FOUND
            429 -> ExcorCatalogError.RATE_LIMITED
            else -> ExcorCatalogError.PROVIDER_UNAVAILABLE
        }

        // shortcut: 50 per 5 s is Deezer's commonly cited limit (its docs need a login); Spotify and the Apple Music API
        // publish no figure. Tune if 429s appear.
        private fun budgetOf(provider: ExcorCatalogProvider) = when (provider) {
            ExcorCatalogProvider.DEEZER -> 50 to 5_000L
            else -> 20 to 1_000L
        }

        // Apple's Search API page: "limited to approximately 20 calls per minute (subject to change)". Over it, Apple
        // has been seen answering 403, so waiting here keeps matching from failing as access denied.
        private const val ITUNES_HOST = "itunes.apple.com"
        private val ITUNES_BUDGET = 20 to 60_000L
    }
}

/** A sliding-window budget: at most `limit.first` requests per `limit.second` ms, waiting rather than failing. */
internal class RequestBudget(private val limit: Pair<Int, Long>, private val nowMs: () -> Long) {
    private val sent = ArrayDeque<Long>()
    private val lock = Mutex()
    var taken = 0
        private set

    suspend fun take() {
        while (true) {
            val wait = lock.withLock {
                val now = nowMs()
                while (sent.isNotEmpty() && now - sent.first() >= limit.second) sent.removeFirst()
                if (sent.size < limit.first) { sent.addLast(now); taken++; 0L } else limit.second - (now - sent.first())
            }
            if (wait <= 0) return
            delay(wait)
        }
    }
}

/** In a coroutine with this element, reads skip the cache; sync needs current snapshots. */
internal object FreshReads : kotlin.coroutines.AbstractCoroutineContextElement(FreshReadsKey)

internal object FreshReadsKey : kotlin.coroutines.CoroutineContext.Key<FreshReads>

internal fun JsonElement.objectOrNull(): JsonObject? = this as? JsonObject
