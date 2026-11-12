import com.excor.Excor
import com.excor.ExcorRequestException
import com.excor.ExcorStream
import com.excor.excorDefaultHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.exitProcess
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val ADAPTER_VERSION = "0.1.0-excor0.2.0"
private val videoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")
private val commitPattern = Regex("^(?:[0-9a-f]{40}|[0-9a-f]{64})$")
private val safeIdPattern = Regex("^[A-Za-z0-9_-]{1,64}$")
private val localePattern = Regex("^[a-z]{2,3}(?:-[A-Z]{2})?$")
private val safeMimePattern = Regex("^[A-Za-z0-9.+-]{1,80}/[A-Za-z0-9.+-]{1,80}$")
private val safeCodecPattern = Regex("^[A-Za-z0-9._+\\-]{1,80}$")
private val accessStatuses = setOf("LOGIN_REQUIRED", "AGE_CHECK_REQUIRED", "CONTENT_CHECK_REQUIRED", "REQUIRES_AUTH", "UNPLAYABLE")
private val redirectStatuses = setOf(301, 302, 303, 307, 308)

internal data class AdapterOptions(
    val videoId: String,
    val comparisonId: String,
    val mainCommit: String,
    val runDateUtc: String,
    val os: String,
    val networkCategory: String,
    val contentCategory: String,
    val locale: String,
    val timeoutSeconds: Int,
    val phase: String,
    val sampleIndex: Int,
    val rangeProbes: Boolean,
)

internal fun parseOptions(args: List<String>): AdapterOptions {
    if (args == listOf("--help")) throw HelpRequested
    val allowed = setOf(
        "--video-id", "--comparison-id", "--main-commit", "--run-date-utc",
        "--network-category", "--content-category", "--locale", "--timeout-seconds",
        "--phase", "--sample-index",
    )
    val values = linkedMapOf<String, String>()
    var rangeProbes = false
    var index = 0
    while (index < args.size) {
        val key = args[index]
        if (key == "--range-probes") {
            if (rangeProbes) throw SafeUsageException("invalid arguments")
            rangeProbes = true
            index += 1
            continue
        }
        if (key !in allowed || values.containsKey(key) || index + 1 >= args.size) {
            throw SafeUsageException("invalid arguments")
        }
        values[key] = args[index + 1]
        index += 2
    }

    fun required(name: String) = values[name] ?: throw SafeUsageException("missing required option")
    val videoId = required("--video-id")
    val comparisonId = required("--comparison-id")
    val mainCommit = required("--main-commit")
    val runDateUtc = required("--run-date-utc")
    val os = detectOs()
    val network = required("--network-category")
    val content = required("--content-category")
    val locale = values["--locale"] ?: "en-US"
    val timeout = when (val value = values["--timeout-seconds"]) {
        null -> 30
        else -> value.toIntOrNull() ?: throw SafeUsageException("invalid timeout")
    }
    val phase = required("--phase")
    val sampleIndex = required("--sample-index").toIntOrNull() ?: 0

    if (!videoIdPattern.matches(videoId) || !safeIdPattern.matches(comparisonId) || !commitPattern.matches(mainCommit)) {
        throw SafeUsageException("invalid identifier")
    }
    if (runCatching { Instant.parse(runDateUtc) }.isFailure || !runDateUtc.endsWith("Z")) {
        throw SafeUsageException("invalid UTC run date")
    }
    if (os !in setOf("windows", "macos", "linux") ||
        network !in setOf("home", "mobile", "ci", "other") ||
        content !in setOf("stable_public_video", "music_video", "live", "unavailable_case", "other_public") ||
        !localePattern.matches(locale) ||
        timeout !in 1..30 ||
        phase !in setOf("cold", "warm") ||
        sampleIndex < 1
    ) {
        throw SafeUsageException("invalid option value")
    }
    return AdapterOptions(videoId, comparisonId, mainCommit, runDateUtc, os, network, content, locale, timeout, phase, sampleIndex, rangeProbes)
}

private object HelpRequested : RuntimeException()
private class SafeUsageException(message: String) : RuntimeException(message)

private fun detectOs(): String {
    val name = System.getProperty("os.name", "").lowercase(Locale.ROOT)
    return when {
        "windows" in name -> "windows"
        "mac" in name || "darwin" in name -> "macos"
        "linux" in name -> "linux"
        else -> throw SafeUsageException("unsupported operating system")
    }
}

private class RequestCounter : EventListener.Factory {
    private val count = AtomicLong()
    override fun create(call: Call): EventListener = object : EventListener() {
        override fun callStart(call: Call) {
            count.incrementAndGet()
        }
    }
    fun get(): Long = count.get()
    fun reset() = count.set(0)
}

private data class MediaMetrics(
    val requests: AtomicLong = AtomicLong(),
    val ranges: AtomicLong = AtomicLong(),
    val bytes: AtomicLong = AtomicLong(),
    val firstByteAtNs: AtomicLong = AtomicLong(0),
) {
    fun reset() {
        requests.set(0)
        ranges.set(0)
        bytes.set(0)
        firstByteAtNs.set(0)
    }
}

private data class SampleOutcome(val status: String, val failureClass: String?)

internal data class ComparisonSample(
    val options: AdapterOptions,
    val outcome: String,
    val failureClass: String?,
    val metadataMs: Double? = null,
    val extractMs: Double? = null,
    val firstMediaByteMs: Double? = null,
    val requestCount: Long = 0,
    val mediaRequestCount: Long = 0,
    val bytesRead: Long = 0,
    val rangeRequests: Long = 0,
    val heapDeltaBytes: Long? = null,
    val codec: String? = null,
    val mimeType: String? = null,
    val rangeProbeMethod: String = "NOT_TESTED",
    val rangeForwardProbe: String = "NOT_TESTED",
    val rangeBackwardProbe: String = "NOT_TESTED",
) {
    fun toJson(): String {
        val decoder = "null"
        return """
            {"schemaVersion":1,"comparison":{"id":${jsonString(options.comparisonId)},"mainCommit":${jsonString(options.mainCommit)},"runDateUtc":${jsonString(options.runDateUtc)},"os":${jsonString(options.os)},"networkCategory":${jsonString(options.networkCategory)},"contentCategory":${jsonString(options.contentCategory)},"sessionCategory":"signed_out","timeoutSeconds":${options.timeoutSeconds},"locale":${jsonString(options.locale)},"decoderVersion":$decoder},"adapter":"excor","adapterVersion":"$ADAPTER_VERSION","runtime":"jvm","lane":"direct_audio","phase":${jsonString(options.phase)},"sampleIndex":${options.sampleIndex},"videoId":${jsonString(options.videoId)},"outcome":${jsonString(outcome)},"failureClass":${jsonString(failureClass)},"measurements":{"metadataMs":${jsonNumber(metadataMs)},"extractMs":${jsonNumber(extractMs)},"startupMs":null,"firstMediaByteMs":${jsonNumber(firstMediaByteMs)},"decodeMs":null,"requestCount":$requestCount,"mediaRequestCount":$mediaRequestCount,"bytesRead":$bytesRead,"rangeRequests":$rangeRequests,"heapDeltaBytes":${heapDeltaBytes ?: "null"},"backwardSeek":"NOT_TESTED","forwardSeek":"NOT_TESTED","decode":"NOT_TESTED","codec":${jsonString(codec)},"mimeType":${jsonString(mimeType)},"rangeProbeMethod":${jsonString(rangeProbeMethod)},"rangeForwardProbe":${jsonString(rangeForwardProbe)},"rangeBackwardProbe":${jsonString(rangeBackwardProbe)}}}
        """.trimIndent()
    }
}

internal fun jsonString(value: String?): String {
    if (value == null) return "null"
    val escaped = buildString {
        for (char in value) {
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append("\\u%04x".format(Locale.ROOT, char.code)) else append(char)
            }
        }
    }
    return "\"$escaped\""
}

private fun jsonNumber(value: Double?): String = value?.takeIf { it.isFinite() }?.let {
    String.format(Locale.ROOT, "%.3f", it)
} ?: "null"

private fun usedHeapBytes(): Long = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

private fun stopClient(client: OkHttpClient) {
    client.dispatcher.cancelAll()
    client.dispatcher.executorService.shutdown()
    client.connectionPool.evictAll()
}

private fun durationMs(startedAtNs: Long, endedAtNs: Long = System.nanoTime()): Double =
    (endedAtNs - startedAtNs).coerceAtLeast(0) / 1_000_000.0

private fun remainingBudgetMs(deadlineNs: Long): Long =
    ((deadlineNs - System.nanoTime()).coerceAtLeast(0) / 1_000_000).coerceAtLeast(1)

private fun allowedMediaUrl(value: String): Boolean {
    return try {
        val uri = URI(value)
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        val hostAllowed = host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "googlevideo.com" || host.endsWith(".googlevideo.com")
        uri.scheme.equals("https", ignoreCase = true) &&
            (uri.port == -1 || uri.port == 443) && hostAllowed &&
            uri.rawPath == "/videoplayback" && uri.rawUserInfo == null && uri.rawFragment == null
    } catch (_: Exception) {
        false
    }
}

private fun mediaHeaders(input: Map<String, String>): Map<String, String> {
    val allowed = mapOf(
        "user-agent" to "User-Agent", "accept" to "Accept", "accept-language" to "Accept-Language",
        "referer" to "Referer", "origin" to "Origin",
    )
    return buildMap {
        input.forEach { (name, value) ->
            val key = allowed[name.lowercase(Locale.ROOT)] ?: return@forEach
            if (value.length <= 4096 && value.all { it.code in 0x20..0x7e }) put(key, value)
        }
    }
}

internal fun mediaUrlAllowedForTest(value: String): Boolean = allowedMediaUrl(value)

internal fun mediaHeadersForTest(input: Map<String, String>): Map<String, String> = mediaHeaders(input)

private data class RangeResponse(
    val code: Int,
    val location: String?,
    val contentRange: String?,
    val bytesRead: Int,
    val contentLength: Long?,
)

private data class InitialRangeResult(
    val outcome: SampleOutcome?,
    val finalUrl: String?,
    val totalBytes: Long?,
)

private suspend fun executeRangeRequest(
    client: OkHttpClient,
    url: String,
    headers: Map<String, String>,
    metrics: MediaMetrics,
    startByte: Long = 0,
    maxBytes: Int = 65_536,
): RangeResponse = suspendCancellableCoroutine { continuation ->
    val endByte = startByte + maxBytes - 1
    val builder = Request.Builder().url(url).header("Range", "bytes=$startByte-$endByte")
    headers.forEach { (name, value) -> builder.header(name, value) }
    val call = client.newCall(builder.build())
    metrics.requests.incrementAndGet()
    metrics.ranges.incrementAndGet()
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, error: IOException) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val result = response.use { res ->
                    if (res.code !in setOf(200, 206)) {
                        RangeResponse(res.code, res.header("Location"), res.header("Content-Range"), 0, res.header("Content-Length")?.toLongOrNull())
                    } else {
                        val body = res.body
                        if (body == null) {
                            RangeResponse(res.code, res.header("Location"), res.header("Content-Range"), 0, res.header("Content-Length")?.toLongOrNull())
                        } else {
                            val buffer = ByteArray(8192)
                            var total = 0
                            var firstAt = 0L
                            body.byteStream().use { input ->
                                while (total < maxBytes) {
                                    val count = input.read(buffer, 0, minOf(buffer.size, maxBytes - total))
                                    if (count < 0) break
                                    if (count == 0) continue
                                    if (firstAt == 0L) firstAt = System.nanoTime()
                                    total += count
                                }
                            }
                            if (total > 0) {
                                metrics.bytes.addAndGet(total.toLong())
                                if (firstAt > 0) metrics.firstByteAtNs.compareAndSet(0, firstAt)
                            }
                            RangeResponse(res.code, res.header("Location"), res.header("Content-Range"), total, res.header("Content-Length")?.toLongOrNull())
                        }
                    }
                }
                if (continuation.isActive) continuation.resume(result)
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    })
}

private suspend fun probeInitialRange(
    client: OkHttpClient,
    stream: ExcorStream,
    metrics: MediaMetrics,
): InitialRangeResult {
    var current = stream.url
    val headers = mediaHeaders(stream.headers)
    if (!allowedMediaUrl(current)) return InitialRangeResult(SampleOutcome("FAIL", "PARSE"), null, null)
    repeat(4) { hop ->
        val response = try {
            executeRangeRequest(client, current, headers, metrics)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return InitialRangeResult(SampleOutcome("FAIL", if (hasTimeout(error)) "TIMEOUT" else "OTHER"), null, null)
        }
        if (response.code in redirectStatuses) {
            if (hop == 3 || response.location.isNullOrBlank()) return InitialRangeResult(SampleOutcome("FAIL", "HTTP"), null, null)
            val next = try { URI(current).resolve(response.location).toString() } catch (_: Exception) { return InitialRangeResult(SampleOutcome("FAIL", "HTTP"), null, null) }
            if (!allowedMediaUrl(next)) return InitialRangeResult(SampleOutcome("FAIL", "HTTP"), null, null)
            current = next
            return@repeat
        }
        if (response.code == 401 || response.code == 403) return InitialRangeResult(SampleOutcome("BLOCKED", "ACCESS_DENIED"), current, null)
        if (response.code !in setOf(200, 206) || response.bytesRead == 0) return InitialRangeResult(SampleOutcome("FAIL", "HTTP"), current, null)
        if (response.code == 206) {
            val range = response.contentRange?.let {
                Regex("^bytes 0-([0-9]+)/([0-9]+|\\*)$").matchEntire(it)
            } ?: return InitialRangeResult(SampleOutcome("FAIL", "HTTP"), current, null)
            val end = range.groupValues[1].toLongOrNull() ?: return InitialRangeResult(SampleOutcome("FAIL", "HTTP"), current, null)
            val totalField = range.groupValues[2]
            val totalBytes = if (totalField == "*") {
                null
            } else {
                totalField.toLongOrNull() ?: return InitialRangeResult(SampleOutcome("FAIL", "HTTP"), current, null)
            }
            if (end !in 0..65_535 || response.bytesRead.toLong() != end + 1 || (totalBytes != null && totalBytes <= end)) {
                return InitialRangeResult(SampleOutcome("FAIL", "HTTP"), current, null)
            }
        }
        val totalBytes = HttpRangeProbe.totalBytes(response.code, response.contentRange, response.contentLength)
        return InitialRangeResult(null, current, totalBytes)
    }
    return InitialRangeResult(SampleOutcome("FAIL", "HTTP"), null, null)
}

private suspend fun probeOffsetRange(
    client: OkHttpClient,
    initialUrl: String,
    headers: Map<String, String>,
    metrics: MediaMetrics,
    startByte: Long,
    totalBytes: Long,
): String {
    if (!HttpRangeProbe.isAvailable(startByte, totalBytes)) {
        return "UNAVAILABLE"
    }
    var current = initialUrl
    repeat(4) { hop ->
        val response = try {
            executeRangeRequest(client, current, headers, metrics, startByte, HttpRangeProbe.PROBE_BYTES)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return "ERROR"
        }
        if (response.code in redirectStatuses) {
            if (hop == 3 || response.location.isNullOrBlank()) return "ERROR"
            current = try { URI(current).resolve(response.location).toString() } catch (_: Exception) { return "ERROR" }
            if (!allowedMediaUrl(current)) return "ERROR"
            return@repeat
        }
        return HttpRangeProbe.classifyResponse(response.code, response.contentRange, response.bytesRead, startByte, totalBytes)
    }
    return "ERROR"
}

private fun hasTimeout(error: Throwable): Boolean {
    var cause: Throwable? = error
    repeat(8) {
        if (cause is SocketTimeoutException || cause is TimeoutCancellationException) return true
        cause = cause?.cause
    }
    return false
}

private fun classifyPlayability(status: String?): SampleOutcome? {
    if (status == null || status == "OK") return null
    return if (status in accessStatuses) SampleOutcome("BLOCKED", "ACCESS_DENIED") else SampleOutcome("FAIL", "OTHER")
}

private fun classifyRequestError(error: ExcorRequestException): SampleOutcome = when {
    error.statusCode == 401 || error.statusCode == 403 -> SampleOutcome("BLOCKED", "ACCESS_DENIED")
    error.statusCode != null || error.failure.name in setOf("RATE_LIMITED", "SERVER_ERROR") -> SampleOutcome("FAIL", "HTTP")
    hasTimeout(error) -> SampleOutcome("FAIL", "TIMEOUT")
    else -> SampleOutcome("FAIL", "OTHER")
}

private suspend fun warmup(excor: Excor, options: AdapterOptions): SampleOutcome? {
    val info = excor.videoInfo(options.videoId)
    classifyPlayability(info?.playabilityStatus)?.let { return it }
    if (info?.playabilityStatus != null && info.playabilityStatus != "OK") return SampleOutcome("FAIL", "OTHER")
    if (excor.extract(options.videoId, maxKbps = 160) == null) return SampleOutcome("FAIL", "OTHER")
    return null
}

private fun codecFrom(mime: String?): String? {
    if (mime == null) return null
    val raw = Regex("(?i)codecs\\s*=\\s*\"?([A-Za-z0-9._+\\-]{1,80})")
        .find(mime)?.groupValues?.get(1)
    return raw?.takeIf(safeCodecPattern::matches)
}

private fun baseMime(mime: String?): String? = mime?.substringBefore(';')?.trim()?.takeIf(safeMimePattern::matches)

private suspend fun runSample(options: AdapterOptions): ComparisonSample {
    val requestCounter = RequestCounter()
    val timeout = options.timeoutSeconds.toLong()
    val http = OkHttpClient.Builder()
        .eventListenerFactory(requestCounter)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(minOf(15L, timeout), TimeUnit.SECONDS)
        .readTimeout(minOf(20L, timeout), TimeUnit.SECONDS)
        .callTimeout(minOf(30L, timeout), TimeUnit.SECONDS)
        .build()
    val mediaClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(minOf(15L, timeout), TimeUnit.SECONDS)
        .readTimeout(minOf(20L, timeout), TimeUnit.SECONDS)
        .callTimeout(minOf(30L, timeout), TimeUnit.SECONDS)
        .build()
    val region = options.locale.substringAfterLast('-').takeIf { it.length == 2 } ?: "US"
    val excor = Excor(excorDefaultHost(httpClient = http, region = region, language = options.locale))
    val metrics = MediaMetrics()
    val deadlineNs = System.nanoTime() + timeout * 1_000_000_000

    try {
    if (options.phase == "warm") {
        val warmFailure = try {
            withTimeout(remainingBudgetMs(deadlineNs)) { warmup(excor, options) }
        } catch (error: TimeoutCancellationException) {
            SampleOutcome("FAIL", "TIMEOUT")
        } catch (error: CancellationException) {
            throw error
        } catch (error: ExcorRequestException) {
            classifyRequestError(error)
        } catch (error: Exception) {
            SampleOutcome("FAIL", if (hasTimeout(error)) "TIMEOUT" else "OTHER")
        }
        if (warmFailure != null) {
            return ComparisonSample(options, warmFailure.status, warmFailure.failureClass,
                requestCount = requestCounter.get(), mediaRequestCount = 0, bytesRead = 0, rangeRequests = 0)
        }
        requestCounter.reset()
        metrics.reset()
    }

    val sampleStarted = System.nanoTime()
    val heapBefore = usedHeapBytes()
    var metadataMs: Double? = null
    var extractMs: Double? = null
    var outcome = SampleOutcome("FAIL", "OTHER")
    var mime: String? = null
    var codec: String? = null
    var rangeProbeMethod = "NOT_TESTED"
    var rangeForwardProbe = "NOT_TESTED"
    var rangeBackwardProbe = "NOT_TESTED"
    try {
        withTimeout(remainingBudgetMs(deadlineNs)) {
            val metadataStarted = System.nanoTime()
            val info = try {
                excor.videoInfo(options.videoId)
            } finally {
                metadataMs = durationMs(metadataStarted)
            }
            val refusal = classifyPlayability(info?.playabilityStatus)
            if (refusal != null) {
                outcome = refusal
                return@withTimeout
            }
            if (info?.playabilityStatus != null && info.playabilityStatus != "OK") {
                outcome = SampleOutcome("FAIL", "OTHER")
                return@withTimeout
            }

            val extractStarted = System.nanoTime()
            val stream = try {
                excor.extract(options.videoId, maxKbps = 160)
            } finally {
                extractMs = durationMs(extractStarted)
            }
            if (stream == null) {
                outcome = SampleOutcome("FAIL", "OTHER")
                return@withTimeout
            }
            mime = baseMime(stream.mimeType)
            codec = codecFrom(stream.mimeType)
            val initialRange = probeInitialRange(mediaClient, stream, metrics)
            if (initialRange.outcome != null) {
                outcome = initialRange.outcome
            } else {
                outcome = SampleOutcome("PASS", null)
                if (options.rangeProbes) {
                    rangeProbeMethod = "HTTP_BYTE_RANGE"
                    val totalBytes = initialRange.totalBytes
                    val finalUrl = initialRange.finalUrl
                    if (totalBytes == null || finalUrl == null || totalBytes < HttpRangeProbe.MIN_TOTAL_BYTES) {
                        rangeForwardProbe = "UNAVAILABLE"
                        rangeBackwardProbe = "UNAVAILABLE"
                    } else {
                        rangeForwardProbe = probeOffsetRange(
                            mediaClient, finalUrl, mediaHeaders(stream.headers), metrics,
                            startByte = HttpRangeProbe.forwardStart(totalBytes), totalBytes = totalBytes,
                        )
                        rangeBackwardProbe = probeOffsetRange(
                            mediaClient, finalUrl, mediaHeaders(stream.headers), metrics,
                            startByte = HttpRangeProbe.backwardStart(totalBytes), totalBytes = totalBytes,
                        )
                    }
                }
            }
        }
    } catch (error: TimeoutCancellationException) {
        outcome = SampleOutcome("FAIL", "TIMEOUT")
    } catch (error: CancellationException) {
        throw error
    } catch (error: ExcorRequestException) {
        outcome = classifyRequestError(error)
    } catch (error: Exception) {
        outcome = SampleOutcome("FAIL", if (hasTimeout(error)) "TIMEOUT" else "OTHER")
    }

    val firstAt = metrics.firstByteAtNs.get().takeIf { it > 0 }
    return ComparisonSample(
        options = options,
        outcome = outcome.status,
        failureClass = outcome.failureClass,
        metadataMs = metadataMs,
        extractMs = extractMs,
        firstMediaByteMs = firstAt?.let { durationMs(sampleStarted, it) },
        requestCount = requestCounter.get() + metrics.requests.get(),
        mediaRequestCount = metrics.requests.get(),
        bytesRead = metrics.bytes.get(),
        rangeRequests = metrics.ranges.get(),
        heapDeltaBytes = usedHeapBytes() - heapBefore,
        codec = codec,
        mimeType = mime,
        rangeProbeMethod = rangeProbeMethod,
        rangeForwardProbe = rangeForwardProbe,
        rangeBackwardProbe = rangeBackwardProbe,
    )
    } finally {
        stopClient(http)
        stopClient(mediaClient)
    }
}

internal fun runSampleForTest(options: AdapterOptions, outcome: String = "PASS"): ComparisonSample =
    ComparisonSample(options, outcome, null)

fun main(args: Array<String>) {
    val options = try {
        parseOptions(args.toList())
    } catch (_: HelpRequested) {
        System.err.println("Usage: --video-id ID --comparison-id ID --main-commit SHA --run-date-utc UTC --network-category home|mobile|ci|other --content-category CATEGORY --phase cold|warm --sample-index N [--locale en-US] [--timeout-seconds 30] [--range-probes]")
        return
    } catch (error: SafeUsageException) {
        System.err.println("comparison adapter: ${error.message}")
        exitProcess(2)
    }
    try {
        val sample = runBlocking { runSample(options) }
        println(sample.toJson())
    } catch (_: CancellationException) {
        System.err.println("comparison adapter cancelled")
        exitProcess(130)
    } catch (_: Exception) {
        System.err.println("comparison adapter failed before producing a sample")
        exitProcess(1)
    }
}
