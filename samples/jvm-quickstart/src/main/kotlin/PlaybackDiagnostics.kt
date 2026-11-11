import com.excor.Excor
import com.excor.ExcorRequestException
import com.excor.ExcorStream
import com.excor.excorDefaultHost
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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private data class PlaybackOptions(val videoId: String, val seconds: Int, val fullDecode: Boolean)

internal suspend fun runPlaybackDiagnostics(args: List<String>) {
    val options = parseOptions(args) ?: return
    val excor = Excor(excorDefaultHost(region = "US", language = "en"))
    val started = System.nanoTime()
    val info = try {
        excor.videoInfo(options.videoId)
    } catch (e: ExcorRequestException) {
        println("Metadata request failed: ${e.failure}, HTTP ${e.statusCode ?: "none"}.")
        return
    } catch (_: Exception) {
        println("Metadata request failed; details suppressed.")
        return
    }
    val status = classifyPlayabilityStatus(info?.playabilityStatus)
    if (status != null && status != "OK") {
        println("YouTube did not offer playback (status=$status).")
        return
    }
    val stream = try {
        excor.extract(options.videoId, maxKbps = 160)
    } catch (e: ExcorRequestException) {
        println("Stream request failed: ${e.failure}, HTTP ${e.statusCode ?: "none"}.")
        return
    } catch (_: Exception) {
        println("Stream extraction failed; details suppressed.")
        return
    }
    if (stream == null) {
        println(if (status == "OK") "YouTube returned OK, but no eligible direct audio stream was found."
            else "No eligible direct audio stream was returned.")
        return
    }
    if (!isAllowedMediaUrl(stream.url)) {
        println("The selected stream URL did not pass the local HTTPS media allowlist.")
        return
    }
    val client = stream.clientName.takeIf { it.matches(Regex("^[A-Za-z0-9_-]{1,40}$")) } ?: "unknown"
    val mime = stream.mimeType.substringBefore(';').takeIf {
        it.matches(Regex("^[A-Za-z0-9.+-]{1,80}/[A-Za-z0-9.+-]{1,80}$"))
    } ?: "unknown"
    println("stream_ready videoId=${options.videoId} client=$client mime=$mime bitrateKbps=${stream.kbps.coerceAtLeast(0)} lookupMs=${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)}")
    println("Signed-out direct audio. The media URL and request headers stay in this process.")
    println("Use Right Arrow to seek forward, Left Arrow to seek backward, and q to stop.")

    val proxy = try { LoopbackMediaProxy(stream) } catch (_: Exception) {
        println("Could not start the loopback-only media proxy.")
        return
    }
    try {
        val playerStart = System.nanoTime()
        val playerResult = runPlayer(proxy.localUrl, options.seconds)
        println("player_result=$playerResult elapsedMs=${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - playerStart)}")
        printStats("player", proxy.snapshotAndReset())
        if (options.fullDecode) runFullDecode(proxy, options.videoId, info?.durationSeconds)
    } finally {
        proxy.close()
    }
}

private fun parseOptions(args: List<String>): PlaybackOptions? {
    fun usage() = println("Usage: --playback VIDEO_ID [--seconds N] [--full-decode] (requires ffplay; decode also requires ffmpeg and ffprobe)")
    if (args.isEmpty() || !args[0].matches(Regex("^[A-Za-z0-9_-]{11}$"))) {
        println("A valid 11-character YouTube video ID is required.")
        usage()
        return null
    }
    var seconds = 60
    var fullDecode = false
    var i = 1
    while (i < args.size) {
        when (args[i]) {
            "--seconds" -> {
                seconds = args.getOrNull(i + 1)?.toIntOrNull() ?: 0
                if (seconds !in 1..3600) { println("--seconds must be between 1 and 3600."); return null }
                i += 2
            }
            "--full-decode" -> { fullDecode = true; i++ }
            else -> { println("Unknown playback option."); usage(); return null }
        }
    }
    return PlaybackOptions(args[0], seconds, fullDecode)
}

private fun runPlayer(url: String, seconds: Int): String {
    val process = try {
        ProcessBuilder("ffplay", "-nodisp", "-loglevel", "quiet", "-autoexit", url)
            .redirectInput(ProcessBuilder.Redirect.INHERIT)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    } catch (_: Exception) {
        println("ffplay was not found on PATH or could not be started.")
        return "unavailable"
    }
    return try {
        if (process.waitFor(seconds.toLong(), TimeUnit.SECONDS)) {
            if (process.exitValue() == 0) "finished" else "exit_${process.exitValue()}"
        } else {
            process.destroy()
            if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly()
            "time_limit"
        }
    } catch (_: InterruptedException) {
        process.destroyForcibly()
        Thread.currentThread().interrupt()
        "interrupted"
    }
}

private fun runFullDecode(proxy: LoopbackMediaProxy, videoId: String, expected: Long?) {
    val duration = runProbe(proxy.localUrl)
    if (duration == null) {
        println("full_decode=unavailable; ffprobe returned no duration.")
        printStats("full_decode", proxy.snapshotAndReset())
        return
    }
    val comparison = expected?.let { if (abs(duration - it) <= max(2.0, it * 0.02)) "match" else "different" }
        ?: "metadata_unavailable"
    println("probe_duration=${String.format(Locale.ROOT, "%.2f", duration)} metadataDurationSeconds=${expected ?: "unknown"} comparison=$comparison")
    val process = try {
        ProcessBuilder("ffmpeg", "-nostdin", "-v", "error", "-i", proxy.localUrl, "-map", "0:a:0", "-f", "null", "-")
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    } catch (_: Exception) {
        println("ffmpeg was not found on PATH or could not be started.")
        printStats("full_decode", proxy.snapshotAndReset())
        return
    }
    val started = System.nanoTime()
    val result = try {
        if (process.waitFor(30, TimeUnit.MINUTES)) {
            if (process.exitValue() == 0) "ok" else "failed"
        } else {
            process.destroy()
            if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly()
            "timeout"
        }
    } catch (_: InterruptedException) {
        process.destroyForcibly()
        Thread.currentThread().interrupt()
        "interrupted"
    }
    println("full_decode=$result videoId=$videoId elapsedMs=${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)}")
    printStats("full_decode", proxy.snapshotAndReset())
}

private fun runProbe(url: String): Double? {
    val process = try {
        ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1", url)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
    } catch (_: Exception) { return null }
    return try {
        if (!process.waitFor(60, TimeUnit.SECONDS)) { process.destroyForcibly(); return null }
        if (process.exitValue() != 0) return null
        process.inputStream.bufferedReader().use { it.readLine()?.take(32)?.toDoubleOrNull()?.takeIf { n -> n.isFinite() && n > 0 } }
    } catch (_: Exception) { process.destroyForcibly(); null }
}

private data class ProxyStats(
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

private fun printStats(label: String, s: ProxyStats) {
    println("$label requests=${s.requests} upstreamRequests=${s.upstreamRequests} rangeRequests=${s.rangeRequests} rangeStarts=${s.rangeStarts.distinct().take(16).ifEmpty { listOf("none") }} httpStatuses=${s.statuses.ifEmpty { mapOf(0 to 0L) }} bytesRelayed=${s.bytes}")
}

private class LoopbackMediaProxy(stream: ExcorStream) : AutoCloseable {
    private val uri = URI(stream.url.also { require(isAllowedMediaUrl(it)) })
    private val headers = safeRequestHeaders(stream.headers)
    private val kbps = stream.kbps.coerceIn(64, 20_000)
    private val stats = Stats()
    private val path = "/media/${UUID.randomUUID().toString().replace("-", "")}"
    private val pool = Executors.newFixedThreadPool(4) { r -> Thread(r, "excor-media-proxy").apply { isDaemon = true } }
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        setExecutor(this@LoopbackMediaProxy.pool)
        createContext(path) { handle(it) }
        start()
    }
    val localUrl = "http://127.0.0.1:${server.address.port}$path"

    private fun handle(x: HttpExchange) {
        try {
            val range = x.requestHeaders.getFirst("Range")
            stats.request(range)
            if (!x.remoteAddress.address.isLoopbackAddress || x.requestURI.rawPath != path ||
                x.requestURI.rawQuery != null || x.requestURI.rawFragment != null) {
                sendEmpty(x, 404); return
            }
            if (x.requestMethod !in setOf("GET", "HEAD")) {
                x.responseHeaders.set("Allow", "GET, HEAD"); sendEmpty(x, 405); return
            }
            if (range != null && !isValidRange(range)) {
                x.responseHeaders.set("Content-Range", "bytes */*"); sendEmpty(x, 416); return
            }

            var target = uri
            for (redirect in 0..3) {
                if (!isAllowedMediaUrl(target)) { sendEmpty(x, 502); return }
                val c = target.toURL().openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 15_000
                c.readTimeout = 25_000
                c.useCaches = false
                c.requestMethod = x.requestMethod
                headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                if (range != null) c.setRequestProperty("Range", range)
                try {
                    stats.upstream()
                    val code = c.responseCode
                    stats.status(code)
                    if (code in REDIRECTS) {
                        val location = c.getHeaderField("Location")
                        if (location.isNullOrBlank() || redirect == 3) { sendEmpty(x, 502); return }
                        val next = try { target.resolve(location) } catch (_: Exception) { sendEmpty(x, 502); return }
                        if (!isAllowedMediaUrl(next)) { sendEmpty(x, 502); return }
                        target = next
                        continue
                    }
                    if (code != 200 && code != 206) { sendEmpty(x, code.takeIf { it in 400..599 } ?: 502); return }
                    safeResponseHeaders(c, x)
                    if (x.requestMethod == "HEAD") { x.sendResponseHeaders(code, -1); return }
                    val length = c.getHeaderField("Content-Length")?.toLongOrNull()?.takeIf { it >= 0 } ?: 0L
                    x.sendResponseHeaders(code, length)
                    c.inputStream.use { input -> x.responseBody.use { output -> relay(input, output) } }
                    return
                } finally { c.disconnect() }
            }
            sendEmpty(x, 502)
        } catch (_: Exception) {
            try { sendEmpty(x, 502) } catch (_: Exception) {}
        } finally { x.close() }
    }

    private fun relay(input: java.io.InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(16 * 1024)
        val started = System.nanoTime()
        val rate = kbps * 1000.0 * 1.25 / 8.0
        var sent = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (n == 0) continue
            val due = (sent + n) / rate * 1_000_000_000.0
            while (due > System.nanoTime() - started) {
                try { Thread.sleep(min(100L, max(1L, ceil((due - (System.nanoTime() - started)) / 1_000_000.0).toLong()))) }
                catch (_: InterruptedException) { Thread.currentThread().interrupt(); throw IOException("relay interrupted") }
            }
            output.write(buffer, 0, n)
            output.flush()
            sent += n
            stats.bytes(n)
        }
    }

    fun snapshotAndReset() = stats.reset()
    override fun close() { server.stop(0); pool.shutdownNow() }
}

private val REDIRECTS = setOf(301, 302, 303, 307, 308)

internal fun isAllowedMediaUrl(value: String): Boolean =
    try { isAllowedMediaUrl(URI(value)) } catch (_: Exception) { false }

private fun isAllowedMediaUrl(u: URI): Boolean {
    val h = u.host?.lowercase(Locale.ROOT) ?: return false
    val hostOk = h == "youtube.com" || h.endsWith(".youtube.com") || h == "googlevideo.com" || h.endsWith(".googlevideo.com")
    return u.scheme.equals("https", true) && (u.port == -1 || u.port == 443) && hostOk &&
        u.rawPath == "/videoplayback" && u.rawUserInfo == null && u.rawFragment == null
}

internal fun safeRequestHeaders(input: Map<String, String>): Map<String, String> {
    val allow = mapOf("user-agent" to "User-Agent", "accept" to "Accept",
        "accept-language" to "Accept-Language", "referer" to "Referer", "origin" to "Origin")
    return buildMap {
        input.forEach { (name, value) ->
            val key = allow[name.lowercase(Locale.ROOT)] ?: return@forEach
            if (value.length <= 4096 && value.all { it.code in 0x20..0x7e }) put(key, value)
        }
    }
}

internal fun isValidRange(value: String): Boolean {
    if (value.length > 64) return false
    val match = Regex("^bytes=(?:([0-9]{1,20})-([0-9]{0,20})|-([0-9]{1,20}))$").matchEntire(value) ?: return false
    val suffixLength = match.groupValues[3].takeIf(String::isNotEmpty)?.toLongOrNull()
    if (suffixLength != null) return suffixLength > 0
    val start = match.groupValues[1].toLongOrNull() ?: return false
    val end = match.groupValues[2].takeIf(String::isNotEmpty)?.toLongOrNull()
    return end == null || start <= end
}

internal fun parseRangeStart(value: String): Long? =
    if (isValidRange(value)) value.removePrefix("bytes=").substringBefore('-').toLongOrNull() else null

internal fun classifyPlayabilityStatus(status: String?): String? {
    if (status == null) return null
    if (status == "OK") return status
    val known = setOf(
        "LOGIN_REQUIRED", "AGE_CHECK_REQUIRED", "CONTENT_CHECK_REQUIRED", "UNPLAYABLE",
        "LIVE_STREAM_OFFLINE", "VIDEO_NOT_FOUND", "ERROR", "REQUIRES_AUTH",
    )
    return if (status in known) status else "OTHER"
}

private fun safeResponseHeaders(c: URLConnection, x: HttpExchange) {
    c.getHeaderField("Content-Type")?.substringBefore(';')?.trim()
        ?.takeIf { it.matches(Regex("^[A-Za-z0-9.+-]{1,80}/[A-Za-z0-9.+-]{1,80}$")) }
        ?.let { x.responseHeaders.set("Content-Type", it) }
    c.getHeaderField("Content-Length")?.takeIf { it.matches(Regex("^[0-9]{1,20}$")) }
        ?.let { x.responseHeaders.set("Content-Length", it) }
    c.getHeaderField("Content-Range")?.takeIf {
        it.matches(Regex("^bytes [0-9]{1,20}-[0-9]{1,20}/(?:[0-9]{1,20}|\\*)$"))
    }?.let { x.responseHeaders.set("Content-Range", it) }
    c.getHeaderField("Accept-Ranges")?.takeIf { it.equals("bytes", true) || it.equals("none", true) }
        ?.let { x.responseHeaders.set("Accept-Ranges", it) }
}

private fun sendEmpty(x: HttpExchange, status: Int) { x.sendResponseHeaders(status, -1) }
