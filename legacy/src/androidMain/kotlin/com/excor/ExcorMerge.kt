package com.excor

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import java.io.File
import java.nio.ByteBuffer

/**
 * Merges a downloaded video-only file and audio-only file (a [ExcorMergePair]) into one file on the device. The
 * encoded samples are copied as they are, so nothing is re-encoded and quality stays as downloaded.
 */
object ExcorMerge {
    // The video and audio codecs of each container, as mergePair picks them.
    private val CODECS = mapOf(
        ExcorContainer.WEBM to (MediaFormat.MIMETYPE_VIDEO_VP9 to MediaFormat.MIMETYPE_AUDIO_OPUS),
        ExcorContainer.MP4 to (MediaFormat.MIMETYPE_VIDEO_AVC to MediaFormat.MIMETYPE_AUDIO_AAC),
    )

    /**
     * Whether this device can write [container]. Android's WebM writer takes Opus only from Android 10 (API 29), so
     * Android 8 and 9 merge MP4 alone, which YouTube offers up to 1080p; choose the container before downloading.
     */
    fun supports(container: ExcorContainer) = container == ExcorContainer.MP4 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Writes [output] from [video] and [audio] (as downloaded for a [ExcorMergePair]); on failure no [output] is left. */
    fun merge(video: File, audio: File, output: File, container: ExcorContainer) {
        val sources = mutableListOf<Source>()
        try {
            if (!supports(container)) {
                throw UnsupportedOperationException("Excor: Android ${Build.VERSION.SDK_INT} cannot write Opus into WebM (Android 10 can); use MP4")
            }
            sources += Source(video, "video/")
            sources += Source(audio, "audio/")
            require(sources.map { it.mime } == CODECS.getValue(container).toList()) {
                "Excor: ${sources[0].mime} + ${sources[1].mime} cannot go into $container without re-encoding"
            }
            write(
                sources,
                output,
                if (container == ExcorContainer.WEBM) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
            )
        } catch (e: Throwable) {
            output.delete()
            throw e
        } finally {
            sources.forEach { it.extractor.release() }
        }
    }

    /**
     * Writes [output] from a downloaded audio-only format: AAC into a plain MP4 (an .m4a file), Opus into Ogg (an .opus
     * file). YouTube serves them as fragmented MP4 and WebM, which many music players read badly. Ogg needs Android 10,
     * like Opus in WebM ([supports] WEBM); before that only AAC can be saved. On failure no [output] is left.
     */
    fun remuxAudio(audio: File, output: File) {
        var source: Source? = null
        try {
            source = Source(audio, "audio/")
            val format = when (source.mime) {
                MediaFormat.MIMETYPE_AUDIO_AAC -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                MediaFormat.MIMETYPE_AUDIO_OPUS -> if (supports(ExcorContainer.WEBM)) {
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG
                } else {
                    throw UnsupportedOperationException("Excor: Android ${Build.VERSION.SDK_INT} cannot write Ogg (Android 10 can); save AAC")
                }
                else -> throw IllegalArgumentException("Excor: ${source.mime} cannot be saved without re-encoding")
            }
            write(listOf(source), output, format)
        } catch (e: Throwable) {
            output.delete()
            throw e
        } finally {
            source?.extractor?.release()
        }
    }

    private fun write(sources: List<Source>, output: File, outputFormat: Int) {
        val muxer = MediaMuxer(output.path, outputFormat)
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
                    ?: throw IllegalArgumentException("Excor: no $kind track in ${file.name}")
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
