package com.bxane.excor

import android.app.Activity
import android.app.Instrumentation
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import java.io.File

/**
 * Runs [ExcorMerge] on a device or emulator: `adb shell am instrument -w com.bxane.excor.test/com.bxane.excor.MergeCheck`
 * prints "MergeCheck: OK" or the failure. A plain Instrumentation, as androidx.test's runner needs JUnit 4 (EPL-1.0).
 * The clips are synthetic and laid out as YouTube serves its formats (fragmented MP4, WebM) and as SoundCloud's HLS
 * downloads come out (joined fMP4 segments); see PROVENANCE.md.
 */
class MergeCheck : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = runCatching { checks() }
        val report = result.fold({ "$it\nMergeCheck: OK\n" }, { "MergeCheck: FAILED\n${it.stackTraceToString()}" })
        finish(if (result.isSuccess) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply { putString(REPORT_KEY_STREAMRESULT, report) })
    }

    private fun checks(): String {
        val dir = context.cacheDir
        fun clip(name: String) = File(dir, name).also { file ->
            javaClass.classLoader!!.getResourceAsStream("clips/$name")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        }
        val lines = listOf(
            Triple(ExcorContainer.MP4, "video-avc.mp4", "audio-aac.m4a"),
            Triple(ExcorContainer.WEBM, "video-vp9.webm", "audio-opus.webm"),
        ).map { (container, video, audio) ->
            val output = File(dir, "merged-$container")
            if (!ExcorMerge.supports(container)) {
                // Android 8 and 9 write no Opus into WebM: refused up front, nothing left behind.
                val error = runCatching { ExcorMerge.merge(clip(video), clip(audio), output, container) }.exceptionOrNull()
                check(error is UnsupportedOperationException && !output.exists()) { "$container: $error, output left: ${output.exists()}" }
                return@map "$container: refused on API ${Build.VERSION.SDK_INT}, as expected"
            }
            ExcorMerge.merge(clip(video), clip(audio), output, container)
            // Every sample of both inputs, in one file with one track each.
            val expected = samplesOf(clip(video)) + samplesOf(clip(audio))
            val actual = samplesOf(output)
            check(actual == expected) { "$container: $actual, expected $expected" }
            "$container: $actual"
        }
        // VP9 does not go into MP4 without re-encoding: refused, and no file is left behind.
        val output = File(dir, "merged-mismatch")
        val error = runCatching { ExcorMerge.merge(clip("video-vp9.webm"), clip("audio-aac.m4a"), output, ExcorContainer.MP4) }.exceptionOrNull()
        check(error is IllegalArgumentException && !output.exists()) { "mismatch: $error, output left: ${output.exists()}" }
        // Audio alone: AAC into a plain MP4, Opus into Ogg (Android 10 on), every sample kept. audio-aac-hls.m4a is HLS
        // segments joined as SoundCloud's downloads are: an init segment, then styp, sidx, moof and mdat per segment.
        val audioLines = listOf("audio-aac.m4a", "audio-aac-hls.m4a", "audio-opus.webm").map { name ->
            val remuxed = File(dir, "remuxed-$name")
            if (name.endsWith(".webm") && !ExcorMerge.supports(ExcorContainer.WEBM)) {
                val refused = runCatching { ExcorMerge.remuxAudio(clip(name), remuxed) }.exceptionOrNull()
                check(refused is UnsupportedOperationException && !remuxed.exists()) { "$name: $refused, output left: ${remuxed.exists()}" }
                return@map "$name alone: refused on API ${Build.VERSION.SDK_INT}, as expected"
            }
            ExcorMerge.remuxAudio(clip(name), remuxed)
            val expected = samplesOf(clip(name))
            val actual = samplesOf(remuxed)
            check(actual == expected) { "$name alone: $actual, expected $expected" }
            "$name alone: $actual"
        }
        return (lines + audioLines).joinToString("\n")
    }

    /** Each track's MIME type and sample count. */
    private fun samplesOf(file: File): Map<String, Int> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val counts = IntArray(extractor.trackCount)
            counts.indices.forEach(extractor::selectTrack)
            while (extractor.sampleTrackIndex >= 0) {
                counts[extractor.sampleTrackIndex]++
                extractor.advance()
            }
            return counts.indices.associate { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty() to counts[it] }
        } finally {
            extractor.release()
        }
    }
}
