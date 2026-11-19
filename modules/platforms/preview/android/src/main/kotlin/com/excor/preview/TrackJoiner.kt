package com.excor.preview

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import java.io.File
import java.nio.ByteBuffer

/**
 * Copies encoded samples from YouTube's separate picture and sound files (or one sound file) into one ordinary file
 * with Android's MediaExtractor and MediaMuxer. Nothing is re-encoded.
 *
 * MP4 takes H.264 with AAC. WebM takes VP9 with Opus, and only from Android 10 (API 29): Android 8 and 9 write WebM
 * with Vorbis sound only, which YouTube doesn't serve.
 */
object TrackJoiner {
    enum class Container(val extension: String, val mimeType: String) {
        MP4("mp4", "video/mp4"),
        WEBM("webm", "video/webm"),
    }

    fun canJoin(container: Container): Boolean = container == Container.MP4 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /**
     * How many samples went into the output, per input track, in [inputs] order, and how many were left out for a
     * time before zero (encoder priming), which the muxer refuses.
     */
    data class Report(val samples: List<Int>, val skipped: List<Int>)

    /**
     * Writes the first video or audio track of each of [inputs] into [output], interleaved by time. On failure the
     * partial output is deleted.
     */
    fun join(inputs: List<File>, output: File, container: Container): Report {
        require(inputs.isNotEmpty()) { "nothing to join" }
        check(canJoin(container)) { "this Android version can't write ${container.name} with Opus sound" }
        val extractors = mutableListOf<MediaExtractor>()
        val muxer = MediaMuxer(
            output.path,
            if (container == Container.MP4) MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 else MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM,
        )
        try {
            class Input(val extractor: MediaExtractor, val track: Int, val buffer: ByteBuffer) {
                var count = 0
                var skipped = 0
                var done = false
            }
            val sources = inputs.map { file ->
                val extractor = MediaExtractor().also(extractors::add)
                extractor.setDataSource(file.path)
                val index = (0 until extractor.trackCount).firstOrNull { i ->
                    val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
                    mime.startsWith("video/") || mime.startsWith("audio/")
                } ?: error("${file.name} has no audio or video track")
                extractor.selectTrack(index)
                val format = extractor.getTrackFormat(index)
                val size = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
                Input(extractor, muxer.addTrack(format), ByteBuffer.allocate(if (size > 0) size else 4 * 1024 * 1024))
            }
            muxer.start()
            val info = MediaCodec.BufferInfo()
            while (true) {
                // The input whose next sample is earliest goes next, so the output is interleaved by time.
                val next = sources.filter { !it.done }.minByOrNull { it.extractor.sampleTime } ?: break
                val time = next.extractor.sampleTime
                val size = next.extractor.readSampleData(next.buffer, 0)
                if (size < 0) {
                    next.done = true
                    continue
                }
                if (time < 0) {
                    next.skipped++
                    if (!next.extractor.advance()) next.done = true
                    continue
                }
                val flags = if ((next.extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                info.set(0, size, time, flags)
                muxer.writeSampleData(next.track, next.buffer, info)
                next.count++
                if (!next.extractor.advance()) next.done = true
            }
            check(sources.all { it.count > 0 }) { "a track had no samples" }
            muxer.stop()
            return Report(sources.map { it.count }, sources.map { it.skipped })
        } catch (failure: Exception) {
            output.delete()
            throw failure
        } finally {
            runCatching { muxer.release() }
            extractors.forEach(MediaExtractor::release)
        }
    }

    /** The number of samples in each track of [file], in track order. */
    fun sampleCounts(file: File): List<Int> {
        val tracks = MediaExtractor().let { probe -> try { probe.setDataSource(file.path); probe.trackCount } finally { probe.release() } }
        return (0 until tracks).map { track ->
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.path)
                extractor.selectTrack(track)
                val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
                var count = 0
                while (extractor.readSampleData(buffer, 0) >= 0) {
                    count++
                    if (!extractor.advance()) break
                }
                count
            } finally {
                extractor.release()
            }
        }
    }
}
