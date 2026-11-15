import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

internal const val VIDEO_DECODER_RELAY_KBPS = 20_000
private const val MAX_PROBE_OUTPUT = 8192

internal fun printSeekResult(
    proxy: LoopbackMediaProxy, mime: String?, seconds: Int, video: Boolean, decoder: Boolean, label: String,
) {
    val result = if (decoder) {
        runVideoDecoderSeek(proxy, mime, seconds)
    } else {
        println("Focus ffplay and press Right then Left Arrow before its $seconds second limit.")
        runInteractivePlayer(proxy.localUrl, seconds, 0, showVideo = video) { proxy.byteLimitExceeded }
    }
    println("${label}_result=$result")
    printStats(label, proxy.snapshotAndReset(), proxy.byteLimitExceeded)
}

private fun runVideoDecoderSeek(proxy: LoopbackMediaProxy, mime: String?, timeoutSeconds: Int): String {
    val format = when (mime?.lowercase()) {
        "video/mp4" -> "mov"
        "video/webm" -> "matroska"
        else -> return "unsupported_container"
    }
    val process = try {
        ProcessBuilder(
            "ffprobe", "-v", "error", "-protocol_whitelist", "http,tcp", "-f", format,
            "-select_streams", "v:0", "-show_frames",
            "-show_entries", "frame=best_effort_timestamp_time:frame_side_data=",
            "-of", "default=nw=1", "-read_intervals", "%+#32,30%+#32,10%+#32", proxy.localUrl,
        ).redirectErrorStream(true).apply {
            environment().remove("http_proxy")
            environment().remove("HTTP_PROXY")
        }.start()
    } catch (_: Exception) {
        return "unavailable"
    }
    val output = FutureTask { process.inputStream.use { it.readNBytes(MAX_PROBE_OUTPUT + 1) } }
    Thread(output, "video-decoder-seek-output").apply { isDaemon = true; start() }
    val result = waitForProcess(process, timeoutSeconds.toLong()) { proxy.byteLimitExceeded }
    if (result != "finished") return result
    val bytes = try {
        output.get(1, TimeUnit.SECONDS)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        return "interrupted"
    } catch (_: Exception) {
        return "output_unavailable"
    }
    if (bytes.size > MAX_PROBE_OUTPUT) return "output_limit"
    val starts = decodedSeekStarts(bytes.toString(Charsets.UTF_8)) ?: return "seek_unverified"
    return "decoded_seek_pass frameStartsSeconds=$starts"
}

internal fun decodedSeekStarts(output: String): List<Double>? {
    // The seek can land on an earlier keyframe. Keep the three windows disjoint.
    val windows = listOf(0.0..3.0, 24.0..34.0, 4.0..14.0)
    var phase = 0
    val starts = mutableListOf<Double>()
    for (line in output.lineSequence().filter { it.isNotBlank() }) {
        val seconds = line.removePrefix("best_effort_timestamp_time=").takeIf { it != line }
            ?.toDoubleOrNull() ?: return null
        if (seconds !in windows[phase]) {
            if (starts.size <= phase || phase == windows.lastIndex) return null
            phase++
            if (seconds !in windows[phase]) return null
        }
        if (starts.size == phase) starts += seconds
    }
    return starts.takeIf { it.size == windows.size }
}
