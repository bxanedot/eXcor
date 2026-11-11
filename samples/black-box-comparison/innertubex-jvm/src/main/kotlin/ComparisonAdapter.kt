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
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.plugin
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.header
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

private const val ADAPTER_VERSION = "0.1.0-innertubex0.11.6"
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
)

internal fun parseOptions(args: List<String>): AdapterOptions {
    if (args == listOf("--help")) throw HelpRequested
    val allowed = setOf(
        "--video-id", "--comparison-id", "--main-commit", "--run-date-utc",
        "--network-category", "--content-category", "--locale", "--timeout-seconds",
        "--phase", "--sample-index",
    )
    val values = linkedMapOf<String, String>()
    var index = 0
    while (index < args.size) {
        val key = args[index]
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
    return AdapterOptions(videoId, comparisonId, mainCommit, runDateUtc, os, network, content, locale, timeout, phase, sampleIndex)
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
    val bytesRead: Long = 0,
    val heapDeltaBytes: Long? = null,
    val codec: String? = null,
    val mimeType: String? = null,
) {
    fun toJson(): String =
        """
        {"schemaVersion":1,"comparison":{"id":${jsonString(options.comparisonId)},"mainCommit":${jsonString(options.mainCommit)},"runDateUtc":${jsonString(options.runDateUtc)},"os":${jsonString(options.os)},"networkCategory":${jsonString(options.networkCategory)},"contentCategory":${jsonString(options.contentCategory)},"sessionCategory":"signed_out","timeoutSeconds":${options.timeoutSeconds},"locale":${jsonString(options.locale)},"decoderVersion":null},"adapter":"innertubex","adapterVersion":"$ADAPTER_VERSION","runtime":"jvm","lane":"direct_audio","phase":${jsonString(options.phase)},"sampleIndex":${options.sampleIndex},"videoId":${jsonString(options.videoId)},"outcome":${jsonString(outcome)},"failureClass":${jsonString(failureClass)},"measurements":{"metadataMs":null,"extractMs":${jsonNumber(extractMs)},"startupMs":null,"firstMediaByteMs":${jsonNumber(firstMediaByteMs)},"decodeMs":null,"requestCount":$requestCount,"mediaRequestCount":$mediaRequestCount,"bytesRead":$bytesRead,"rangeRequests":$mediaRequestCount,"heapDeltaBytes":${heapDeltaBytes ?: "null"},"backwardSeek":"NOT_TESTED","forwardSeek":"NOT_TESTED","decode":"NOT_TESTED","codec":${jsonString(codec)},"mimeType":${jsonString(mimeType)}}}
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

private fun newHttpClient(timeoutSeconds: Int, followRedirects: Boolean): HttpClient = HttpClient(CIO) {
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
): RangeResponse {
    requestCount.incrementAndGet()
    val response = client.get(url) {
        header(HttpHeaders.Range, "bytes=0-${MAX_RANGE_BYTES - 1}")
        headers.forEach { (name, value) -> header(name, value) }
    }
    val code = response.status.value
    val location = response.headers[HttpHeaders.Location]
    val contentRange = response.headers[HttpHeaders.ContentRange]
    if (code !in setOf(200, 206)) {
        response.bodyAsChannel().cancel()
        return RangeResponse(code, location, contentRange, 0, null)
    }

    val channel: ByteReadChannel = response.bodyAsChannel()
    val buffer = ByteArray(8192)
    var total = 0
    var zeroReads = 0
    var firstByteAtNs: Long? = null
    try {
        while (total < MAX_RANGE_BYTES) {
            val count = channel.readAvailable(buffer, 0, minOf(buffer.size, MAX_RANGE_BYTES - total))
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
    return RangeResponse(code, location, contentRange, total, firstByteAtNs)
}

private suspend fun probeInitialRange(
    client: HttpClient,
    initialUrl: String,
    headers: Map<String, String>,
    mediaRequestCount: AtomicLong,
    bytesRead: AtomicLong,
): Pair<SampleOutcome, Long?> {
    if (!allowedMediaUrl(initialUrl)) return SampleOutcome("FAIL", "PARSE") to null
    var current = initialUrl
    repeat(4) { hop ->
        val response = try {
            executeRangeRequest(client, current, headers, mediaRequestCount, bytesRead)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return classifyThrowableOutcome(error) to null
        }
        if (response.code in redirectStatuses) {
            if (hop == 3 || response.location.isNullOrBlank()) return SampleOutcome("FAIL", "HTTP") to null
            current = try {
                URI(current).resolve(response.location).toString()
            } catch (_: Exception) {
                return SampleOutcome("FAIL", "HTTP") to null
            }
            if (!allowedMediaUrl(current)) return SampleOutcome("FAIL", "HTTP") to null
            return@repeat
        }
        if (response.code == 401 || response.code == 403) return SampleOutcome("BLOCKED", "ACCESS_DENIED") to null
        if (!rangeResponseValid(response.code, response.contentRange, response.bytesRead)) {
            return SampleOutcome("FAIL", "HTTP") to response.firstByteAtNs
        }
        return SampleOutcome("PASS", null) to response.firstByteAtNs
    }
    return SampleOutcome("FAIL", "HTTP") to null
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
    val apiClient = newHttpClient(options.timeoutSeconds, followRedirects = true)
    val mediaClient = newHttpClient(options.timeoutSeconds, followRedirects = false)
    attachRequestCounter(apiClient, apiRequestCount)

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

                val (rangeOutcome, firstByteAtNs) = probeInitialRange(
                    client = mediaClient,
                    initialUrl = stream.audioUrl,
                    headers = mediaHeaders(stream.headers),
                    mediaRequestCount = mediaRequestCount,
                    bytesRead = bytesRead,
                )
                outcome = rangeOutcome
                firstMediaByteAtNs = firstByteAtNs
            }
        } catch (error: TimeoutCancellationException) {
            outcome = SampleOutcome("FAIL", "TIMEOUT")
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
            bytesRead = bytesRead.get(),
            heapDeltaBytes = usedHeapBytes() - heapBefore,
            codec = codec,
            mimeType = mimeType,
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

fun main(args: Array<String>) {
    val options = try {
        parseOptions(args.toList())
    } catch (_: HelpRequested) {
        System.err.println("Usage: --video-id ID --comparison-id ID --main-commit SHA --run-date-utc UTC --network-category home|mobile|ci|other --content-category CATEGORY --phase cold|warm --sample-index N [--locale en-US] [--timeout-seconds 30]")
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
