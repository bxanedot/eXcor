import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.InnerTubeHttpException
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.StreamResolveException
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.models.YouTubeLocale
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.plugin
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.net.SocketTimeoutException
import java.net.URI
import java.time.Instant
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.exitProcess

private const val ADAPTER_VERSION = "0.1.3-innertubex0.11.6"
private const val MAX_RANGE_BYTES = 65_536
private val videoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")
private val commitPattern = Regex("^(?:[0-9a-f]{40}|[0-9a-f]{64})$")
private val safeIdPattern = Regex("^[A-Za-z0-9_-]{1,64}$")
private val localePattern = Regex("^[a-z]{2,3}(?:-[A-Z]{2})?$")
private val safeMimePattern = Regex("^[A-Za-z0-9.+-]{1,80}/[A-Za-z0-9.+-]{1,80}$")
private val safeCodecPattern = Regex("^[A-Za-z0-9._+\\-]{1,80}$")
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
    val fullDecode: Boolean,
    val decoderVersion: String?,
    val playerSeek: Boolean = false,
)

internal fun parseOptions(args: List<String>): AdapterOptions {
    if (args == listOf("--help")) throw HelpRequested
    val allowed = setOf(
        "--video-id", "--comparison-id", "--main-commit", "--run-date-utc",
        "--network-category", "--content-category", "--locale", "--timeout-seconds",
        "--phase", "--sample-index",
        "--decoder-version",
    )
    val values = linkedMapOf<String, String>()
    var rangeProbes = false
    var fullDecode = false
    var playerSeek = false
    var index = 0
    while (index < args.size) {
        val key = args[index]
        if (key == "--player-seek") {
            if (playerSeek) throw SafeUsageException("invalid arguments")
            playerSeek = true
            index += 1
            continue
        }
        if (key == "--range-probes") {
            if (rangeProbes) throw SafeUsageException("invalid arguments")
            rangeProbes = true
            index += 1
            continue
        }
        if (key == "--full-decode") {
            if (fullDecode) throw SafeUsageException("invalid arguments")
            fullDecode = true
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
    val decoderVersion = values["--decoder-version"]

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
        (decoderVersion != null && (!fullDecode || !Regex("^ffmpeg-[A-Za-z0-9._+\\-]{1,64}$").matches(decoderVersion))) ||
        timeout !in 1..30 ||
        phase !in setOf("cold", "warm") ||
        sampleIndex < 1 ||
        (playerSeek && (rangeProbes || fullDecode))
    ) {
        throw SafeUsageException("invalid option value")
    }
    return AdapterOptions(videoId, comparisonId, mainCommit, runDateUtc, os, network, content, locale, timeout, phase, sampleIndex, rangeProbes, fullDecode, decoderVersion, playerSeek)
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

private data class SampleOutcome(val status: String, val failureClass: String?)

internal data class ComparisonSample(
    val options: AdapterOptions,
    val outcome: String,
    val failureClass: String?,
    val extractMs: Double? = null,
    val firstMediaByteMs: Double? = null,
    val requestCount: Long = 0,
    val mediaRequestCount: Long = 0,
    val rangeRequests: Long = 0,
    val bytesRead: Long = 0,
    val heapDeltaBytes: Long? = null,
    val codec: String? = null,
    val mimeType: String? = null,
    val rangeProbeMethod: String = "NOT_TESTED",
    val rangeForwardProbe: String = "NOT_TESTED",
    val rangeBackwardProbe: String = "NOT_TESTED",
    val decodeMs: Double? = null,
    val decode: String = "NOT_TESTED",
) {
    fun toJson(): String =
        """
        {"schemaVersion":1,"comparison":{"id":${jsonString(options.comparisonId)},"mainCommit":${jsonString(options.mainCommit)},"runDateUtc":${jsonString(options.runDateUtc)},"os":${jsonString(options.os)},"networkCategory":${jsonString(options.networkCategory)},"contentCategory":${jsonString(options.contentCategory)},"sessionCategory":"signed_out","timeoutSeconds":${options.timeoutSeconds},"locale":${jsonString(options.locale)},"decoderVersion":${jsonString(options.decoderVersion)}},"adapter":"innertubex","adapterVersion":"$ADAPTER_VERSION","runtime":"jvm","lane":"direct_audio","phase":${jsonString(options.phase)},"sampleIndex":${options.sampleIndex},"videoId":${jsonString(options.videoId)},"outcome":${jsonString(outcome)},"failureClass":${jsonString(failureClass)},"measurements":{"metadataMs":null,"extractMs":${jsonNumber(extractMs)},"startupMs":null,"firstMediaByteMs":${jsonNumber(firstMediaByteMs)},"decodeMs":${jsonNumber(decodeMs)},"requestCount":$requestCount,"mediaRequestCount":$mediaRequestCount,"bytesRead":$bytesRead,"rangeRequests":$rangeRequests,"heapDeltaBytes":${heapDeltaBytes ?: "null"},"backwardSeek":"NOT_TESTED","forwardSeek":"NOT_TESTED","decode":${jsonString(decode)},"codec":${jsonString(codec)},"mimeType":${jsonString(mimeType)},"rangeProbeMethod":${jsonString(rangeProbeMethod)},"rangeForwardProbe":${jsonString(rangeForwardProbe)},"rangeBackwardProbe":${jsonString(rangeBackwardProbe)}}}
        """.trimIndent()
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

private fun durationMs(startedAtNs: Long, endedAtNs: Long = System.nanoTime()): Double =
    (endedAtNs - startedAtNs).coerceAtLeast(0) / 1_000_000.0

private fun remainingBudgetMs(deadlineNs: Long): Long =
    ((deadlineNs - System.nanoTime()).coerceAtLeast(0) / 1_000_000).coerceAtLeast(1)

internal fun newHttpClient(
    timeoutSeconds: Int,
    followRedirects: Boolean,
    jsonContentNegotiation: Boolean = false,
): HttpClient = HttpClient(CIO) {
    if (jsonContentNegotiation) install(ContentNegotiation) { json() }
    expectSuccess = false
    this.followRedirects = followRedirects
    install(HttpTimeout) {
        requestTimeoutMillis = timeoutSeconds * 1_000L
        connectTimeoutMillis = minOf(15_000L, timeoutSeconds * 1_000L)
        socketTimeoutMillis = minOf(20_000L, timeoutSeconds * 1_000L)
    }
}

private fun attachRequestCounter(client: HttpClient, counter: AtomicLong) {
    client.plugin(HttpSend).intercept { request ->
        counter.incrementAndGet()
        execute(request)
    }
}

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
    val firstByteAtNs: Long?,
    val contentLength: Long?,
)

private data class InitialRangeResult(
    val outcome: SampleOutcome,
    val firstByteAtNs: Long?,
    val finalUrl: String?,
    val totalBytes: Long?,
)

private data class DecodeAttempt(
    val outcome: SampleOutcome?,
    val result: String = "NOT_TESTED",
    val decodeMs: Double? = null,
)

internal fun rangeResponseValidForTest(code: Int, contentRange: String?, bytesRead: Int): Boolean =
    rangeResponseValid(code, contentRange, bytesRead)

private fun rangeResponseValid(code: Int, contentRange: String?, bytesRead: Int): Boolean {
    if (code !in setOf(200, 206) || bytesRead !in 1..MAX_RANGE_BYTES) return false
    if (code == 200) return true
    val match = contentRange?.let { Regex("^bytes 0-([0-9]+)/([0-9]+|\\*)$", RegexOption.IGNORE_CASE).matchEntire(it) }
        ?: return false
    val end = match.groupValues[1].toLongOrNull() ?: return false
    val totalField = match.groupValues[2]
    val total = if (totalField == "*") null else totalField.toLongOrNull() ?: return false
    return end in 0 until MAX_RANGE_BYTES.toLong() && bytesRead.toLong() == end + 1 && (total == null || total > end)
}

private suspend fun executeRangeRequest(
    client: HttpClient,
    url: String,
    headers: Map<String, String>,
    requestCount: AtomicLong,
    bytesCounter: AtomicLong,
    startByte: Long = 0,
    maxBytes: Int = MAX_RANGE_BYTES,
): RangeResponse {
    requestCount.incrementAndGet()
    val response = client.get(url) {
        header(HttpHeaders.Range, "bytes=$startByte-${startByte + maxBytes - 1}")
        headers.forEach { (name, value) -> header(name, value) }
    }
    val code = response.status.value
    val location = response.headers[HttpHeaders.Location]
    val contentRange = response.headers[HttpHeaders.ContentRange]
    val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (code !in setOf(200, 206)) {
        response.bodyAsChannel().cancel()
        return RangeResponse(code, location, contentRange, 0, null, contentLength)
    }

    val channel: ByteReadChannel = response.bodyAsChannel()
    val buffer = ByteArray(8192)
    var total = 0
    var zeroReads = 0
    var firstByteAtNs: Long? = null
    try {
        while (total < maxBytes) {
            val count = channel.readAvailable(buffer, 0, minOf(buffer.size, maxBytes - total))
            if (count < 0) break
            if (count == 0) {
                zeroReads += 1
                if (zeroReads > 128) break
                yield()
                continue
            }
            zeroReads = 0
            if (firstByteAtNs == null) firstByteAtNs = System.nanoTime()
            total += count
        }
    } finally {
        channel.cancel()
    }
    if (total > 0) bytesCounter.addAndGet(total.toLong())
    return RangeResponse(code, location, contentRange, total, firstByteAtNs, contentLength)
}

private suspend fun probeInitialRange(
    client: HttpClient,
    initialUrl: String,
    headers: Map<String, String>,
    mediaRequestCount: AtomicLong,
    bytesRead: AtomicLong,
): InitialRangeResult {
    if (!allowedMediaUrl(initialUrl)) return InitialRangeResult(SampleOutcome("FAIL", "PARSE"), null, null, null)
    var current = initialUrl
    repeat(4) { hop ->
        val response = try {
            executeRangeRequest(client, current, headers, mediaRequestCount, bytesRead)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return InitialRangeResult(classifyThrowableOutcome(error), null, null, null)
        }
        if (response.code in redirectStatuses) {
            if (hop == 3 || response.location.isNullOrBlank()) return InitialRangeResult(SampleOutcome("FAIL", "HTTP"), response.firstByteAtNs, null, null)
            current = try {
                URI(current).resolve(response.location).toString()
            } catch (_: Exception) {
                return InitialRangeResult(SampleOutcome("FAIL", "HTTP"), response.firstByteAtNs, null, null)
            }
            if (!allowedMediaUrl(current)) return InitialRangeResult(SampleOutcome("FAIL", "HTTP"), response.firstByteAtNs, null, null)
            return@repeat
        }
        if (response.code == 401 || response.code == 403) return InitialRangeResult(SampleOutcome("BLOCKED", "ACCESS_DENIED"), response.firstByteAtNs, current, null)
        if (!rangeResponseValid(response.code, response.contentRange, response.bytesRead)) {
            return InitialRangeResult(SampleOutcome("FAIL", "HTTP"), response.firstByteAtNs, current, null)
        }
        val totalBytes = HttpRangeProbe.totalBytes(response.code, response.contentRange, response.contentLength)
        return InitialRangeResult(SampleOutcome("PASS", null), response.firstByteAtNs, current, totalBytes)
    }
    return InitialRangeResult(SampleOutcome("FAIL", "HTTP"), null, null, null)
}

private suspend fun probeOffsetRange(
    client: HttpClient,
    initialUrl: String,
    headers: Map<String, String>,
    requestCount: AtomicLong,
    bytesRead: AtomicLong,
    startByte: Long,
    totalBytes: Long,
): String {
    if (!HttpRangeProbe.isAvailable(startByte, totalBytes)) {
        return "UNAVAILABLE"
    }
    var current = initialUrl
    repeat(4) { hop ->
        val response = try {
            executeRangeRequest(
                client, current, headers, requestCount, bytesRead,
                startByte = startByte, maxBytes = HttpRangeProbe.PROBE_BYTES,
            )
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

private suspend fun decodeFullAudio(
    client: HttpClient,
    initialUrl: String,
    headers: Map<String, String>,
    expectedBytes: Long,
    mediaRequestCount: AtomicLong,
    bytesRead: AtomicLong,
    deadlineNs: Long,
): DecodeAttempt {
    if (expectedBytes !in 1..FfmpegAudioDecoder.MAX_INPUT_BYTES) return DecodeAttempt(null, "UNSUPPORTED")
    if (!allowedMediaUrl(initialUrl)) return DecodeAttempt(SampleOutcome("FAIL", "PARSE"))
    val decoder = FfmpegAudioDecoder.start() ?: return DecodeAttempt(null, "UNSUPPORTED")
    try {
        var current = initialUrl
        for (hop in 0..3) {
            if (!allowedMediaUrl(current)) return DecodeAttempt(SampleOutcome("FAIL", "HTTP"))
            mediaRequestCount.incrementAndGet()
            val response = client.get(current) {
                headers.forEach { (name, value) -> header(name, value) }
                header(HttpHeaders.AcceptEncoding, "identity")
                header(HttpHeaders.Range, "bytes=0-")
            }
            val code = response.status.value
            if (code in redirectStatuses) {
                val location = response.headers[HttpHeaders.Location]
                response.bodyAsChannel().cancel()
                if (hop == 3 || location.isNullOrBlank()) return DecodeAttempt(SampleOutcome("FAIL", "HTTP"))
                current = try {
                    URI(current).resolve(location).toString()
                } catch (_: Exception) {
                    return DecodeAttempt(SampleOutcome("FAIL", "HTTP"))
                }
                if (!allowedMediaUrl(current)) return DecodeAttempt(SampleOutcome("FAIL", "HTTP"))
                continue
            }
            if (code == 401 || code == 403) {
                response.bodyAsChannel().cancel()
                return DecodeAttempt(SampleOutcome("BLOCKED", "ACCESS_DENIED"))
            }
            if (!HttpRangeProbe.fullResponseIsComplete(
                    code, response.headers[HttpHeaders.ContentRange], expectedBytes,
                )
            ) {
                response.bodyAsChannel().cancel()
                return DecodeAttempt(SampleOutcome("FAIL", "HTTP"))
            }
            val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (contentLength != null && contentLength != expectedBytes) {
                response.bodyAsChannel().cancel()
                return DecodeAttempt(SampleOutcome("FAIL", "HTTP"))
            }

            val channel = response.bodyAsChannel()
            val buffer = ByteArray(32_768)
            var total = 0L
            var zeroReads = 0
            try {
                while (true) {
                    val count = channel.readAvailable(buffer, 0, buffer.size)
                    if (count < 0) break
                    if (count == 0) {
                        zeroReads += 1
                        if (zeroReads > 128) break
                        yield()
                        continue
                    }
                    zeroReads = 0
                    if (count.toLong() > expectedBytes - total || count.toLong() > FfmpegAudioDecoder.MAX_INPUT_BYTES - total) {
                        return DecodeAttempt(SampleOutcome("FAIL", "HTTP"))
                    }
                    total += count
                    bytesRead.addAndGet(count.toLong())
                    when (decoder.write(buffer, count)) {
                        "PASS" -> Unit
                        "LIMIT" -> return DecodeAttempt(null, "UNSUPPORTED")
                        else -> return DecodeAttempt(SampleOutcome("FAIL", "DECODE"), "FAIL")
                    }
                }
            } finally {
                channel.cancel()
            }
            if (total != expectedBytes) return DecodeAttempt(SampleOutcome("FAIL", "HTTP"))

            val decoded = decoder.finish(remainingBudgetMs(deadlineNs))
            return when (decoded.status) {
                "PASS" -> DecodeAttempt(SampleOutcome("PASS", null), "PASS", decoded.elapsedMs)
                "TIMEOUT" -> DecodeAttempt(SampleOutcome("FAIL", "TIMEOUT"), "FAIL", decoded.elapsedMs)
                else -> DecodeAttempt(SampleOutcome("FAIL", "DECODE"), "FAIL", decoded.elapsedMs)
            }
        }
        return DecodeAttempt(SampleOutcome("FAIL", "HTTP"))
    } catch (error: CancellationException) {
        decoder.cancel()
        throw error
    } catch (error: Exception) {
        val failure = classifyThrowable(error)
        val outcome = when (failure) {
            "ACCESS_DENIED" -> SampleOutcome("BLOCKED", failure)
            "TIMEOUT", "HTTP" -> SampleOutcome("FAIL", failure)
            else -> SampleOutcome("FAIL", "HTTP")
        }
        return DecodeAttempt(outcome)
    } finally {
        decoder.cancel()
    }
}

private fun safeMime(mime: String?): String? = mime?.substringBefore(';')?.trim()?.takeIf(safeMimePattern::matches)

private fun safeCodec(codec: String?): String? = codec?.takeIf(safeCodecPattern::matches)

private fun directStreamOutcome(stream: ExtractedStream): SampleOutcome? = when {
    stream.sabrBootstrap != null -> SampleOutcome("UNSUPPORTED", "UNSUPPORTED_TRANSPORT")
    stream.audioUrl.isBlank() -> SampleOutcome("UNSUPPORTED", "UNSUPPORTED_TRANSPORT")
    safeMime(stream.mimeType)?.startsWith("audio/", ignoreCase = true) == false -> SampleOutcome("UNSUPPORTED", "UNSUPPORTED_TRANSPORT")
    !allowedMediaUrl(stream.audioUrl) -> SampleOutcome("FAIL", "PARSE")
    else -> null
}

internal fun directStreamOutcomeForTest(url: String, hasSabrBootstrap: Boolean, mimeType: String?): String? = when {
    hasSabrBootstrap -> "UNSUPPORTED"
    url.isBlank() -> "UNSUPPORTED"
    safeMime(mimeType)?.startsWith("audio/", ignoreCase = true) == false -> "UNSUPPORTED"
    !allowedMediaUrl(url) -> "FAIL"
    else -> null
}

private fun classifyThrowable(error: Throwable): String {
    var current: Throwable? = error
    var hasHttpStatus = false
    repeat(12) {
        when (current) {
            is InnerTubeHttpException -> {
                val status = current.status.value
                if (status == 401 || status == 403) return "ACCESS_DENIED"
                hasHttpStatus = true
            }
            is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException -> return "TIMEOUT"
        }
        current = current?.cause
    }
    return if (hasHttpStatus) "HTTP" else "OTHER"
}

private fun classifyStreamError(error: StreamResolveException): SampleOutcome {
    val causeClass = classifyThrowable(error)
    if (causeClass == "ACCESS_DENIED") return SampleOutcome("BLOCKED", causeClass)
    if (causeClass == "HTTP" || causeClass == "TIMEOUT") return SampleOutcome("FAIL", causeClass)
    return when (error.reason) {
        StreamResolveException.Reason.AGE_RESTRICTED -> SampleOutcome("BLOCKED", "ACCESS_DENIED")
        StreamResolveException.Reason.NO_PLAYABLE_STREAM -> SampleOutcome("UNSUPPORTED", "UNSUPPORTED_TRANSPORT")
        StreamResolveException.Reason.EXPLICIT_UNSUPPORTED -> SampleOutcome("FAIL", "OTHER")
        StreamResolveException.Reason.NETWORK -> SampleOutcome("FAIL", "OTHER")
        StreamResolveException.Reason.NO_MUSIC_VIDEO -> SampleOutcome("FAIL", "OTHER")
        StreamResolveException.Reason.UNAVAILABLE -> SampleOutcome("FAIL", "OTHER")
        StreamResolveException.Reason.UNKNOWN -> SampleOutcome("FAIL", "OTHER")
    }
}

internal fun classifyErrorForTest(error: StreamResolveException): String = classifyStreamError(error).status

private fun classifyThrowableOutcome(error: Throwable): SampleOutcome {
    val failure = classifyThrowable(error)
    return if (failure == "ACCESS_DENIED") SampleOutcome("BLOCKED", failure) else SampleOutcome("FAIL", failure)
}

private suspend fun runSample(options: AdapterOptions): ComparisonSample {
    val apiRequestCount = AtomicLong()
    val mediaRequestCount = AtomicLong()
    val bytesRead = AtomicLong()
    val timeoutMs = options.timeoutSeconds.toLong() * 1_000L
    val deadlineNs = System.nanoTime() + timeoutMs * 1_000_000L
    val apiClient = newHttpClient(options.timeoutSeconds, followRedirects = true, jsonContentNegotiation = true)
    attachRequestCounter(apiClient, apiRequestCount)
    val mediaClient = newHttpClient(options.timeoutSeconds, followRedirects = false)

    val innerTube = InnerTube(httpClient = apiClient)
    val configStore = RemotePlayerConfigStore(
        httpClient = apiClient,
        repository = PlayerConfigRepository.disabled(),
    )
    val cipher = YouTubeCipherService(apiClient, configStore)
    val extractor = InnerTubeExtractor(
        configParser = YtConfigParserImpl(apiClient, innerTube, configStore, cipherService = cipher),
        cipherService = cipher,
        innerTube = innerTube,
    )
    val language = options.locale.substringBefore('-')
    val region = options.locale.substringAfter('-', "US")
    innerTube.locale = YouTubeLocale(hl = language, gl = region)

    try {
        if (options.phase == "warm") {
            val warmOutcome = try {
                withTimeout(remainingBudgetMs(deadlineNs)) {
                    val warmStream = extractor.extract(
                        videoId = options.videoId,
                        hints = ContentHints(wantVideo = false).withStreamCapabilities(
                            allowHls = false,
                            allowSabr = false,
                            allowBoundedRange = true,
                        ),
                        audioQuality = AudioQuality.HIGH,
                    )
                    if (warmStream == null) SampleOutcome("UNSUPPORTED", "UNSUPPORTED_TRANSPORT") else null
                }
            } catch (error: TimeoutCancellationException) {
                SampleOutcome("FAIL", "TIMEOUT")
            } catch (error: CancellationException) {
                throw error
            } catch (error: StreamResolveException) {
                classifyStreamError(error)
            } catch (error: Exception) {
                classifyThrowableOutcome(error)
            }
            if (warmOutcome != null) {
                return ComparisonSample(
                    options = options,
                    outcome = warmOutcome.status,
                    failureClass = warmOutcome.failureClass,
                    requestCount = apiRequestCount.get(),
                )
            }
            apiRequestCount.set(0)
        }

        val sampleStarted = System.nanoTime()
        val heapBefore = usedHeapBytes()
        var extractMs: Double? = null
        var outcome = SampleOutcome("FAIL", "OTHER")
        var codec: String? = null
        var mimeType: String? = null
        var firstMediaByteAtNs: Long? = null
        var rangeProbeMethod = "NOT_TESTED"
        var rangeForwardProbe = "NOT_TESTED"
        var rangeBackwardProbe = "NOT_TESTED"
        var decodeMs: Double? = null
        var decode = "NOT_TESTED"
        var decodeStartedAtNs: Long? = null

        try {
            withTimeout(remainingBudgetMs(deadlineNs)) {
                val extractStarted = System.nanoTime()
                val stream = try {
                    extractor.extract(
                        videoId = options.videoId,
                        hints = ContentHints(wantVideo = false).withStreamCapabilities(
                            allowHls = false,
                            allowSabr = false,
                            allowBoundedRange = true,
                        ),
                        audioQuality = AudioQuality.HIGH,
                    )
                } finally {
                    extractMs = durationMs(extractStarted)
                }
                if (stream == null) {
                    outcome = SampleOutcome("UNSUPPORTED", "UNSUPPORTED_TRANSPORT")
                    return@withTimeout
                }
                mimeType = safeMime(stream.mimeType)
                codec = safeCodec(stream.codecs)
                val directOutcome = directStreamOutcome(stream)
                if (directOutcome != null) {
                    outcome = directOutcome
                    return@withTimeout
                }

                val initialRange = probeInitialRange(
                    client = mediaClient,
                    initialUrl = stream.audioUrl,
                    headers = mediaHeaders(stream.headers),
                    mediaRequestCount = mediaRequestCount,
                    bytesRead = bytesRead,
                )
                outcome = initialRange.outcome
                firstMediaByteAtNs = initialRange.firstByteAtNs
                if (initialRange.outcome.status == "PASS" && options.rangeProbes) {
                    rangeProbeMethod = "HTTP_BYTE_RANGE"
                    val totalBytes = initialRange.totalBytes
                    val finalUrl = initialRange.finalUrl
                    if (totalBytes == null || finalUrl == null || totalBytes < HttpRangeProbe.MIN_TOTAL_BYTES) {
                        rangeForwardProbe = "UNAVAILABLE"
                        rangeBackwardProbe = "UNAVAILABLE"
                    } else {
                        rangeForwardProbe = probeOffsetRange(
                            mediaClient, finalUrl, mediaHeaders(stream.headers), mediaRequestCount, bytesRead,
                            startByte = HttpRangeProbe.forwardStart(totalBytes), totalBytes = totalBytes,
                        )
                        rangeBackwardProbe = probeOffsetRange(
                            mediaClient, finalUrl, mediaHeaders(stream.headers), mediaRequestCount, bytesRead,
                            startByte = HttpRangeProbe.backwardStart(totalBytes), totalBytes = totalBytes,
                        )
                    }
                }
                if (initialRange.outcome.status == "PASS" && options.fullDecode) {
                    val totalBytes = initialRange.totalBytes
                    val finalUrl = initialRange.finalUrl
                    if (options.decoderVersion == null || totalBytes == null || finalUrl == null) {
                        decode = "UNSUPPORTED"
                    } else {
                        decodeStartedAtNs = System.nanoTime()
                        decode = "FAIL"
                        val attempt = decodeFullAudio(
                            mediaClient,
                            finalUrl,
                            mediaHeaders(stream.headers),
                            totalBytes,
                            mediaRequestCount,
                            bytesRead,
                            deadlineNs,
                        )
                        decode = attempt.result
                        if (attempt.result != "UNSUPPORTED") {
                            decodeMs = durationMs(decodeStartedAtNs)
                        }
                        if (attempt.outcome != null) outcome = attempt.outcome
                    }
                }
            }
        } catch (error: TimeoutCancellationException) {
            outcome = SampleOutcome("FAIL", "TIMEOUT")
            decodeStartedAtNs?.let {
                decode = "FAIL"
                decodeMs = durationMs(it)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: StreamResolveException) {
            outcome = classifyStreamError(error)
        } catch (error: Exception) {
            outcome = classifyThrowableOutcome(error)
        }

        return ComparisonSample(
            options = options,
            outcome = outcome.status,
            failureClass = outcome.failureClass,
            extractMs = extractMs,
            firstMediaByteMs = firstMediaByteAtNs?.let { durationMs(sampleStarted, it) },
            requestCount = apiRequestCount.get() + mediaRequestCount.get(),
            mediaRequestCount = mediaRequestCount.get(),
            rangeRequests = mediaRequestCount.get(),
            bytesRead = bytesRead.get(),
            heapDeltaBytes = usedHeapBytes() - heapBefore,
            codec = codec,
            mimeType = mimeType,
            rangeProbeMethod = rangeProbeMethod,
            rangeForwardProbe = rangeForwardProbe,
            rangeBackwardProbe = rangeBackwardProbe,
            decodeMs = decodeMs,
            decode = decode,
        )
    } finally {
        try {
            cipher.dispose()
        } catch (_: Exception) {
        }
        innerTube.close()
        apiClient.close()
        mediaClient.close()
    }
}

private suspend fun runPlayerSeek(options: AdapterOptions) {
    val apiClient = newHttpClient(options.timeoutSeconds, followRedirects = true, jsonContentNegotiation = true)
    val mediaClient = newHttpClient(options.timeoutSeconds, followRedirects = false)
    val innerTube = InnerTube(httpClient = apiClient)
    val configStore = RemotePlayerConfigStore(
        httpClient = apiClient,
        repository = PlayerConfigRepository.disabled(),
    )
    val cipher = YouTubeCipherService(apiClient, configStore)
    val extractor = InnerTubeExtractor(
        configParser = YtConfigParserImpl(apiClient, innerTube, configStore, cipherService = cipher),
        cipherService = cipher,
        innerTube = innerTube,
    )
    val language = options.locale.substringBefore('-')
    val region = options.locale.substringAfter('-', "US")
    innerTube.locale = YouTubeLocale(hl = language, gl = region)
    try {
        val started = System.nanoTime()
        val stream = try {
            withTimeout(options.timeoutSeconds * 1_000L) {
                extractor.extract(
                    videoId = options.videoId,
                    hints = ContentHints(wantVideo = false).withStreamCapabilities(
                        allowHls = false,
                        allowSabr = false,
                        allowBoundedRange = true,
                    ),
                    audioQuality = AudioQuality.HIGH,
                )
            }
        } catch (_: TimeoutCancellationException) {
            println("player_seek=stream_resolution_timeout")
            return
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            println("player_seek=stream_resolution_failed")
            return
        }
        if (stream == null) {
            println("player_seek=no_direct_audio_stream")
            return
        }
        val directOutcome = directStreamOutcome(stream)
        if (directOutcome != null) {
            println("player_seek=unavailable outcome=${directOutcome.status} failureClass=${directOutcome.failureClass ?: "none"}")
            return
        }
        println("player_seek=ready adapter=innertubex videoId=${options.videoId} mime=${safeMime(stream.mimeType) ?: "unknown"} lookupMs=${durationMs(started)}")
        println("Focus ffplay and press Right then Left Arrow before its ${options.timeoutSeconds} second limit.")
        val proxy = try {
            LoopbackMediaProxy(PlaybackMediaSource(stream.audioUrl, mediaHeaders(stream.headers), 160))
        } catch (_: Exception) {
            println("player_seek=proxy_unavailable")
            return
        }
        proxy.use {
            val result = runInteractivePlayer(proxy.localUrl, options.timeoutSeconds, 0) { proxy.byteLimitExceeded }
            println("player_seek_result=$result")
            printStats("player_seek", proxy.snapshotAndReset(), proxy.byteLimitExceeded)
        }
    } finally {
        try { cipher.dispose() } catch (_: Exception) {}
        innerTube.close()
        apiClient.close()
        mediaClient.close()
    }
}

fun main(args: Array<String>) {
    val options = try {
        parseOptions(args.toList())
    } catch (_: HelpRequested) {
        System.err.println("Usage: --video-id ID --comparison-id ID --main-commit SHA --run-date-utc UTC --network-category home|mobile|ci|other --content-category CATEGORY --phase cold|warm --sample-index N [--locale en-US] [--timeout-seconds 30] [--range-probes | --full-decode | --player-seek]")
        return
    } catch (error: SafeUsageException) {
        System.err.println("comparison adapter: ${error.message}")
        exitProcess(2)
    }
    try {
        if (options.playerSeek) runBlocking { runPlayerSeek(options) }
        else println(runBlocking { runSample(options) }.toJson())
    } catch (_: CancellationException) {
        System.err.println("comparison adapter cancelled")
        exitProcess(130)
    } catch (_: Exception) {
        System.err.println("comparison adapter failed before producing a sample")
        exitProcess(1)
    }
}
