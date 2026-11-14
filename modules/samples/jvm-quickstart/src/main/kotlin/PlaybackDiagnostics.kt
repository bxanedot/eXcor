import com.excor.Excor
import com.excor.ExcorRequestException
import com.excor.excorDefaultHost
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

private data class PlaybackOptions(val videoId: String, val seconds: Int, val seekSeconds: Int, val fullDecode: Boolean)
internal suspend fun runPlaybackDiagnostics(args: List<String>) {
    val options = parseOptions(args) ?: return
    val host = excorDefaultHost(region = "US", language = "en")
    val excor = Excor(host)
    try {
        runPlaybackDiagnostics(excor, options)
    } finally {
        excor.close()
        host.httpClient.connectionPool.evictAll()
        host.httpClient.dispatcher.executorService.shutdown()
    }
}

private suspend fun runPlaybackDiagnostics(excor: Excor, options: PlaybackOptions) {
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
    println("ffplay will start at ${options.seekSeconds}s. Focus the waveform window to seek with Right/Left Arrow; press q to stop.")

    val proxy = try { LoopbackMediaProxy(PlaybackMediaSource(stream.url, stream.headers, stream.kbps)) } catch (_: Exception) {
        println("Could not start the loopback-only media proxy.")
        return
    }
    try {
        val playerStart = System.nanoTime()
        val playerResult = runInteractivePlayer(proxy.localUrl, options.seconds, options.seekSeconds) { proxy.byteLimitExceeded }
        println("player_result=$playerResult elapsedMs=${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - playerStart)}")
        printStats("player", proxy.snapshotAndReset(), proxy.byteLimitExceeded)
        if (options.fullDecode) runFullDecode(proxy, options.videoId, info?.durationSeconds)
    } finally {
        proxy.close()
    }
}

private fun parseOptions(args: List<String>): PlaybackOptions? {
    fun usage() = println("Usage: --playback VIDEO_ID [--seconds N] [--seek-seconds N] [--full-decode] (requires ffplay; decode also requires ffmpeg and ffprobe)")
    if (args.isEmpty() || !args[0].matches(Regex("^[A-Za-z0-9_-]{11}$"))) {
        println("A valid 11-character YouTube video ID is required.")
        usage()
        return null
    }
    var seconds = 60
    var seekSeconds = 0
    var fullDecode = false
    var i = 1
    while (i < args.size) {
        when (args[i]) {
            "--seconds" -> {
                seconds = args.getOrNull(i + 1)?.toIntOrNull() ?: 0
                if (seconds !in 1..3600) { println("--seconds must be between 1 and 3600."); return null }
                i += 2
            }
            "--seek-seconds" -> {
                seekSeconds = args.getOrNull(i + 1)?.toIntOrNull() ?: -1
                if (seekSeconds !in 0..3600) { println("--seek-seconds must be between 0 and 3600."); return null }
                i += 2
            }
            "--full-decode" -> { fullDecode = true; i++ }
            else -> { println("Unknown playback option."); usage(); return null }
        }
    }
    return PlaybackOptions(args[0], seconds, seekSeconds, fullDecode)
}

private fun runFullDecode(proxy: LoopbackMediaProxy, videoId: String, expected: Long?) {
    val duration = runProbe(proxy.localUrl) { proxy.byteLimitExceeded }
    if (duration == null) {
        val result = if (proxy.byteLimitExceeded) "byte_limit" else "unavailable"
        println("full_decode=$result; ffprobe returned no duration.")
        printStats("full_decode", proxy.snapshotAndReset(), proxy.byteLimitExceeded)
        return
    }
    val comparison = expected?.let { if (abs(duration - it) <= max(2.0, it * 0.02)) "match" else "different" }
        ?: "metadata_unavailable"
    println("probe_duration=${String.format(Locale.ROOT, "%.2f", duration)} metadataDurationSeconds=${expected ?: "unknown"} comparison=$comparison")
    if (proxy.byteLimitExceeded) {
        println("full_decode=byte_limit videoId=$videoId")
        printStats("full_decode", proxy.snapshotAndReset(), true)
        return
    }
    val process = try {
        ProcessBuilder("ffmpeg", "-nostdin", "-v", "error", "-i", proxy.localUrl, "-map", "0:a:0", "-f", "null", "-")
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    } catch (_: Exception) {
        println("ffmpeg was not found on PATH or could not be started.")
        printStats("full_decode", proxy.snapshotAndReset(), proxy.byteLimitExceeded)
        return
    }
    val started = System.nanoTime()
    val result = waitForProcess(process, TimeUnit.MINUTES.toSeconds(30)) { proxy.byteLimitExceeded }
    val boundedResult = when {
        result == "finished" -> "ok"
        result.startsWith("exit_") -> "failed"
        result == "time_limit" -> "timeout"
        else -> result
    }
    println("full_decode=$boundedResult videoId=$videoId elapsedMs=${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)}")
    printStats("full_decode", proxy.snapshotAndReset(), proxy.byteLimitExceeded)
}

private fun runProbe(url: String, byteLimitExceeded: () -> Boolean): Double? {
    val process = try {
        ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1", url)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
    } catch (_: Exception) { return null }
    return try {
        if (waitForProcess(process, 60, byteLimitExceeded) != "finished") return null
        process.inputStream.bufferedReader().use { it.readLine()?.take(32)?.toDoubleOrNull()?.takeIf { n -> n.isFinite() && n > 0 } }
    } catch (_: Exception) { process.destroyForcibly(); null }
}

internal fun classifyPlayabilityStatus(status: String?): String? {
    if (status == null) return null
    if (status == "OK") return status
    val known = setOf(
        "LOGIN_REQUIRED", "AGE_CHECK_REQUIRED", "CONTENT_CHECK_REQUIRED", "UNPLAYABLE",
        "LIVE_STREAM_OFFLINE", "VIDEO_NOT_FOUND", "ERROR", "REQUIRES_AUTH",
    )
    return if (status in known) status else "OTHER"
}
