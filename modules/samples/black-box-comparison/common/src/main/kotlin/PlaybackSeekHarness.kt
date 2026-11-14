import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLConnection
import java.util.Locale
import java.util.TreeMap
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

internal const val MAX_RELAY_BYTES = 256L * 1024 * 1024
private val REDIRECTS = setOf(301, 302, 303, 307, 308)

internal class PlaybackMediaSource(
    val url: String,
    val headers: Map<String, String>,
    val relayKbps: Int = 160,
)

internal class RelayByteBudget(private val maximumBytes: Long) {
    private var consumed = 0L
    private var reserved = 0L
    private var exceeded = false

    class Reservation internal constructor(val bytes: Int) {
        internal var completed = false
    }

    init { require(maximumBytes > 0) }

    @Synchronized
    fun reserve(maximumCount: Int): Reservation? {
        require(maximumCount > 0)
        if (exceeded) return null
        val remaining = maximumBytes - consumed - reserved
        if (remaining <= 0) {
            exceeded = true
            return null
        }
        val bytes = minOf(maximumCount.toLong(), remaining).toInt()
        reserved += bytes
        return Reservation(bytes)
    }

    @Synchronized
    fun complete(reservation: Reservation, count: Int) {
        require(!reservation.completed)
        require(count in 0..reservation.bytes)
        reserved -= reservation.bytes
        consumed += count
        reservation.completed = true
        if (consumed >= maximumBytes) exceeded = true
    }

    @Synchronized
    fun isExceeded(): Boolean = exceeded

    @Synchronized
    fun consumedBytes(): Long = consumed
}

private class RelayByteLimitReached : IOException()

internal fun runInteractivePlayer(
    url: String,
    seconds: Int,
    seekSeconds: Int,
    byteLimitExceeded: () -> Boolean,
): String {
    val process = try {
        ProcessBuilder(ffplayArguments(url, seekSeconds))
            .redirectInput(ProcessBuilder.Redirect.INHERIT)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    } catch (_: Exception) {
        println("ffplay was not found on PATH or could not be started.")
        return "unavailable"
    }
    return waitForProcess(process, seconds.toLong(), byteLimitExceeded)
}

internal fun waitForProcess(process: Process, timeoutSeconds: Long, byteLimitExceeded: () -> Boolean): String {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
    return try {
        while (true) {
            if (byteLimitExceeded()) {
                stopProcess(process)
                return "byte_limit"
            }
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) {
                stopProcess(process)
                return "time_limit"
            }
            if (process.waitFor(min(TimeUnit.MILLISECONDS.toNanos(250), remaining), TimeUnit.NANOSECONDS)) {
                return if (process.exitValue() == 0) "finished" else "exit_${process.exitValue()}"
            }
        }
        "interrupted"
    } catch (_: InterruptedException) {
        process.destroyForcibly()
        Thread.currentThread().interrupt()
        "interrupted"
    }
}

private fun stopProcess(process: Process) {
    process.destroy()
    if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly()
}

internal fun ffplayArguments(url: String, seekSeconds: Int = 0): List<String> =
    listOf("ffplay", "-ss", seekSeconds.toString(), "-showmode", "1", "-loglevel", "quiet", "-autoexit", url)

internal data class ProxyStats(
    val requests: Long,
    val upstreamRequests: Long,
    val rangeRequests: Long,
    val statuses: Map<Int, Long>,
    val rangeStarts: List<Long>,
    val bytes: Long,
)

private class Stats {
    private val lock = Any()
    private var requests = 0L
    private var upstream = 0L
    private var ranges = 0L
    private val statuses = TreeMap<Int, Long>()
    private val starts = mutableListOf<Long>()
    private val bytes = AtomicLong()

    fun request(range: String?) = synchronized(lock) {
        requests++
        if (range != null) {
            ranges++
            parseRangeStart(range)?.let { if (starts.size < 1000) starts += it }
        }
    }

    fun upstream() = synchronized(lock) { upstream++ }
    fun status(value: Int) = synchronized(lock) { statuses[value] = (statuses[value] ?: 0L) + 1 }
    fun bytes(count: Int) { bytes.addAndGet(count.toLong()) }
    fun reset() = synchronized(lock) {
        ProxyStats(requests, upstream, ranges, statuses.toMap(), starts.toList(), bytes.get()).also {
            requests = 0; upstream = 0; ranges = 0; statuses.clear(); starts.clear(); bytes.set(0)
        }
    }
}

internal fun printStats(label: String, stats: ProxyStats, byteLimitExceeded: Boolean) {
    println("$label requests=${stats.requests} upstreamRequests=${stats.upstreamRequests} rangeRequests=${stats.rangeRequests} rangeStarts=${stats.rangeStarts.take(16).ifEmpty { listOf("none") }} httpStatuses=${stats.statuses.ifEmpty { mapOf(0 to 0L) }} bytesRelayed=${stats.bytes} relayByteLimitExceeded=$byteLimitExceeded relayByteLimitBytes=$MAX_RELAY_BYTES")
}

internal class LoopbackMediaProxy(source: PlaybackMediaSource) : AutoCloseable {
    private val uri = URI(source.url.also { require(isAllowedMediaUrl(it)) })
    private val headers = safeRequestHeaders(source.headers)
    private val kbps = source.relayKbps.coerceIn(64, 20_000)
    private val stats = Stats()
    private val byteBudget = RelayByteBudget(MAX_RELAY_BYTES)
    val byteLimitExceeded: Boolean get() = byteBudget.isExceeded()
    private val path = "/media/${UUID.randomUUID().toString().replace("-", "")}"
    private val pool = Executors.newFixedThreadPool(4) { r -> Thread(r, "comparison-media-proxy").apply { isDaemon = true } }
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        setExecutor(this@LoopbackMediaProxy.pool)
        createContext(path) { handle(it) }
        start()
    }
    val localUrl = "http://127.0.0.1:${server.address.port}$path"

    private fun handle(exchange: HttpExchange) {
        try {
            val range = exchange.requestHeaders.getFirst("Range")
            stats.request(range)
            if (!exchange.remoteAddress.address.isLoopbackAddress || exchange.requestURI.rawPath != path ||
                exchange.requestURI.rawQuery != null || exchange.requestURI.rawFragment != null) {
                sendEmpty(exchange, 404); return
            }
            if (exchange.requestMethod !in setOf("GET", "HEAD")) {
                exchange.responseHeaders.set("Allow", "GET, HEAD"); sendEmpty(exchange, 405); return
            }
            if (range != null && !isValidRange(range)) {
                exchange.responseHeaders.set("Content-Range", "bytes */*"); sendEmpty(exchange, 416); return
            }
            if (byteBudget.isExceeded()) { stats.status(413); sendEmpty(exchange, 413); return }

            var target = uri
            for (redirect in 0..3) {
                if (!isAllowedMediaUrl(target)) { sendEmpty(exchange, 502); return }
                val connection = target.toURL().openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 25_000
                connection.useCaches = false
                connection.requestMethod = exchange.requestMethod
                headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                if (range != null) connection.setRequestProperty("Range", range)
                try {
                    stats.upstream()
                    val code = connection.responseCode
                    stats.status(code)
                    if (code in REDIRECTS) {
                        val location = connection.getHeaderField("Location")
                        if (location.isNullOrBlank() || redirect == 3) { sendEmpty(exchange, 502); return }
                        val next = try { target.resolve(location) } catch (_: Exception) { sendEmpty(exchange, 502); return }
                        if (!isAllowedMediaUrl(next)) { sendEmpty(exchange, 502); return }
                        target = next
                        continue
                    }
                    if (code != 200 && code != 206) { sendEmpty(exchange, code.takeIf { it in 400..599 } ?: 502); return }
                    safeResponseHeaders(connection, exchange)
                    if (exchange.requestMethod == "HEAD") { exchange.sendResponseHeaders(code, -1); return }
                    val length = connection.getHeaderField("Content-Length")?.toLongOrNull()?.takeIf { it >= 0 } ?: 0L
                    exchange.sendResponseHeaders(code, length)
                    connection.inputStream.use { input -> exchange.responseBody.use { output -> relay(input, output) } }
                    return
                } finally { connection.disconnect() }
            }
            sendEmpty(exchange, 502)
        } catch (_: RelayByteLimitReached) {
            // Headers are sent; close this response and reject later requests locally.
        } catch (_: Exception) {
            try { sendEmpty(exchange, 502) } catch (_: Exception) {}
        } finally { exchange.close() }
    }

    private fun relay(input: java.io.InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(16 * 1024)
        val started = System.nanoTime()
        val rate = kbps * 1000.0 * 1.25 / 8.0
        var sent = 0L
        while (true) {
            val reservation = byteBudget.reserve(buffer.size) ?: throw RelayByteLimitReached()
            val count = try {
                input.read(buffer, 0, reservation.bytes)
            } catch (failure: Exception) {
                byteBudget.complete(reservation, 0)
                throw failure
            }
            byteBudget.complete(reservation, count.coerceAtLeast(0))
            if (count < 0) break
            if (count == 0) continue
            val due = (sent + count) / rate * 1_000_000_000.0
            while (due > System.nanoTime() - started) {
                try { Thread.sleep(min(100L, max(1L, ceil((due - (System.nanoTime() - started)) / 1_000_000.0).toLong()))) }
                catch (_: InterruptedException) { Thread.currentThread().interrupt(); throw IOException("relay interrupted") }
            }
            output.write(buffer, 0, count)
            output.flush()
            sent += count
            stats.bytes(count)
        }
    }

    fun snapshotAndReset() = stats.reset()
    override fun close() { server.stop(0); pool.shutdownNow() }
}

internal fun isAllowedMediaUrl(value: String): Boolean =
    try { isAllowedMediaUrl(URI(value)) } catch (_: Exception) { false }

private fun isAllowedMediaUrl(uri: URI): Boolean {
    val host = uri.host?.lowercase(Locale.ROOT) ?: return false
    val hostAllowed = host == "youtube.com" || host.endsWith(".youtube.com") || host == "googlevideo.com" || host.endsWith(".googlevideo.com")
    return uri.scheme.equals("https", true) && (uri.port == -1 || uri.port == 443) && hostAllowed &&
        uri.rawPath == "/videoplayback" && uri.rawUserInfo == null && uri.rawFragment == null
}

internal fun safeRequestHeaders(input: Map<String, String>): Map<String, String> {
    val allowed = mapOf("user-agent" to "User-Agent", "accept" to "Accept",
        "accept-language" to "Accept-Language", "referer" to "Referer", "origin" to "Origin")
    return buildMap {
        input.forEach { (name, value) ->
            val key = allowed[name.lowercase(Locale.ROOT)] ?: return@forEach
            if (value.length <= 4096 && value.all { it.code in 0x20..0x7e }) put(key, value)
        }
    }
}

internal fun isValidRange(value: String): Boolean {
    if (value.length > 64) return false
    val match = Regex("^bytes=(?:([0-9]{1,20})-([0-9]{0,20})|-([0-9]{1,20}))$").matchEntire(value) ?: return false
    val suffixLength = match.groupValues[3].takeIf { it.isNotEmpty() }?.toLongOrNull()
    if (suffixLength != null) return suffixLength > 0
    val start = match.groupValues[1].toLongOrNull() ?: return false
    val end = match.groupValues[2].takeIf(String::isNotEmpty)?.toLongOrNull()
    return end == null || start <= end
}

internal fun parseRangeStart(value: String): Long? =
    if (isValidRange(value)) value.removePrefix("bytes=").substringBefore('-').toLongOrNull() else null

private fun safeResponseHeaders(connection: URLConnection, exchange: HttpExchange) {
    connection.getHeaderField("Content-Type")?.substringBefore(';')?.trim()
        ?.takeIf { it.matches(Regex("^[A-Za-z0-9.+-]{1,80}/[A-Za-z0-9.+-]{1,80}$")) }
        ?.let { exchange.responseHeaders.set("Content-Type", it) }
    connection.getHeaderField("Content-Length")?.takeIf { it.matches(Regex("^[0-9]{1,20}$")) }
        ?.let { exchange.responseHeaders.set("Content-Length", it) }
    connection.getHeaderField("Content-Range")?.takeIf {
        it.matches(Regex("^bytes [0-9]{1,20}-[0-9]{1,20}/(?:[0-9]{1,20}|\\*)$"))
    }?.let { exchange.responseHeaders.set("Content-Range", it) }
    connection.getHeaderField("Accept-Ranges")?.takeIf { it.equals("bytes", true) || it.equals("none", true) }
        ?.let { exchange.responseHeaders.set("Accept-Ranges", it) }
}

private fun sendEmpty(exchange: HttpExchange, status: Int) { exchange.sendResponseHeaders(status, -1) }
