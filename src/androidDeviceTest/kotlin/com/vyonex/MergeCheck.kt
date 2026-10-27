package com.vyonex

import android.app.Activity
import android.app.Instrumentation
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Bundle
import java.io.File

/**
 * Runs [VyonExMerge] on a device or emulator: `adb shell am instrument -w com.vyonex.test/com.vyonex.MergeCheck`
 * prints "MergeCheck: OK" or the failure. A plain Instrumentation, as androidx.test's runner needs JUnit 4 (EPL-1.0).
 * The clips are synthetic and laid out as YouTube serves its formats (fragmented MP4, WebM); see PROVENANCE.md.
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
            Triple(VyonExContainer.MP4, "video-avc.mp4", "audio-aac.m4a"),
            Triple(VyonExContainer.WEBM, "video-vp9.webm", "audio-opus.webm"),
        ).map { (container, video, audio) ->
            val output = File(dir, "merged-$container")
            VyonExMerge.merge(clip(video), clip(audio), output, container)
            // Every sample of both inputs, in one file with one track each.
            val expected = samplesOf(clip(video)) + samplesOf(clip(audio))
            val actual = samplesOf(output)
            check(actual == expected) { "$container: $actual, expected $expected" }
            "$container: $actual"
        }
        // VP9 does not go into MP4 without re-encoding: refused, and no file is left behind.
        val output = File(dir, "merged-mismatch")
        val error = runCatching { VyonExMerge.merge(clip("video-vp9.webm"), clip("audio-aac.m4a"), output, VyonExContainer.MP4) }.exceptionOrNull()
        check(error is IllegalArgumentException && !output.exists()) { "mismatch: $error, output left: ${output.exists()}" }
        return lines.joinToString("\n")
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
