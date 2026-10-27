package com.vyonex

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Merges a downloaded video-only file and audio-only file (a [VyonExMergePair]) into one file on the device. The
 * encoded samples are copied as they are, so nothing is re-encoded and quality stays as downloaded.
 */
object VyonExMerge {
    // The video and audio codecs of each container, as mergePair picks them.
    private val CODECS = mapOf(
        VyonExContainer.WEBM to (MediaFormat.MIMETYPE_VIDEO_VP9 to MediaFormat.MIMETYPE_AUDIO_OPUS),
        VyonExContainer.MP4 to (MediaFormat.MIMETYPE_VIDEO_AVC to MediaFormat.MIMETYPE_AUDIO_AAC),
    )

    /** Writes [output] from [video] and [audio] (as downloaded for a [VyonExMergePair]); on failure no [output] is left. */
    fun merge(video: File, audio: File, output: File, container: VyonExContainer) {
        val sources = mutableListOf<Source>()
        try {
            sources += Source(video, "video/")
            sources += Source(audio, "audio/")
            require(sources.map { it.mime } == CODECS.getValue(container).toList()) {
                "VyonEx: ${sources[0].mime} + ${sources[1].mime} cannot go into $container without re-encoding"
            }
            write(sources, output, container)
        } catch (e: Throwable) {
            output.delete()
            throw e
        } finally {
            sources.forEach { it.extractor.release() }
        }
    }

    private fun write(sources: List<Source>, output: File, container: VyonExContainer) {
        val muxer = MediaMuxer(
            output.path,
            if (container == VyonExContainer.WEBM) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
        )
        try {
            val tracks = sources.map { muxer.addTrack(it.format) }
            muxer.start()
            // A 4K key frame can run to megabytes; take what the files declare, at least 8 MiB.
            val buffer = ByteBuffer.allocate(maxOf(8 shl 20, sources.maxOf { it.maxSampleBytes }))
            val info = MediaCodec.BufferInfo()
            val done = BooleanArray(sources.size)
            // Interleaved by time, so the result can be played while it streams.
            while (!done.all { it }) {
                val next = sources.indices.filter { !done[it] }.minBy { sources[it].extractor.sampleTime }
                val extractor = sources[next].extractor
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) {
                    done[next] = true
                    continue
                }
                val keyFrame = extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                info.set(0, size, extractor.sampleTime, if (keyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                muxer.writeSampleData(tracks[next], buffer, info)
                extractor.advance()
            }
            muxer.stop()
        } finally {
            muxer.release()
        }
    }

    /** The first track of [kind] ("video/" or "audio/") in [file], selected for reading. */
    private class Source(file: File, kind: String) {
        val extractor = MediaExtractor()
        val format: MediaFormat
        val mime: String
        val maxSampleBytes: Int

        init {
            try {
                extractor.setDataSource(file.path)
                val track = (0 until extractor.trackCount)
                    .firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith(kind) }
                    ?: throw IllegalArgumentException("VyonEx: no $kind track in ${file.name}")
                extractor.selectTrack(track)
                format = extractor.getTrackFormat(track)
                mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                maxSampleBytes = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
            } catch (e: Throwable) {
                extractor.release()
                throw e
            }
        }
    }
}
