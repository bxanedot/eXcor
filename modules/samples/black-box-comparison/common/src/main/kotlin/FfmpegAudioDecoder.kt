import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal data class FfmpegDecodeResult(
    val status: String,
    val elapsedMs: Double?,
)

internal class FfmpegAudioDecoder private constructor(
    private val process: Process,
    private val startedAtNs: Long,
) {
    private val inputClosed = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private var bytesWritten = 0L

    fun write(bytes: ByteArray, length: Int): String {
        if (cancelled.get() || length !in 1..bytes.size) return "ERROR"
        if (bytesWritten > MAX_INPUT_BYTES - length) return "LIMIT"
        return try {
            process.outputStream.write(bytes, 0, length)
            bytesWritten += length
            "PASS"
        } catch (_: Exception) {
            "ERROR"
        }
    }

    fun finish(timeoutMs: Long): FfmpegDecodeResult {
        closeInput()
        val completed = try {
            process.waitFor(timeoutMs.coerceAtLeast(1), TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!completed) {
            process.destroyForcibly()
            return FfmpegDecodeResult("TIMEOUT", elapsedMs())
        }
        return FfmpegDecodeResult(
            status = if (process.exitValue() == 0) "PASS" else "FAIL",
            elapsedMs = elapsedMs(),
        )
    }

    fun cancel() {
        cancelled.set(true)
        if (process.isAlive) process.destroyForcibly()
        closeInput()
    }

    private fun closeInput() {
        if (inputClosed.compareAndSet(false, true)) {
            try {
                process.outputStream.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun elapsedMs(): Double =
        (System.nanoTime() - startedAtNs).coerceAtLeast(0) / 1_000_000.0

    companion object {
        const val MAX_INPUT_BYTES = 256L * 1024 * 1024

        fun start(): FfmpegAudioDecoder? = try {
            val process = ProcessBuilder(
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-xerror",
                "-i", "pipe:0", "-map", "0:a:0", "-f", "null", "-",
            )
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            FfmpegAudioDecoder(process, System.nanoTime())
        } catch (_: Exception) {
            null
        }
    }
}
