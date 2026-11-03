package com.excor.preview

import android.app.Activity
import android.app.Instrumentation
import android.os.Build
import android.os.Bundle
import java.io.File

/**
 * Checks [TrackJoiner] on a real Android runtime, with the device's own MediaExtractor and MediaMuxer. A plain
 * Instrumentation, so no test framework is needed: CI runs
 * `adb shell am instrument -w com.excor.preview.test/com.excor.preview.JoinCheck` and looks for `result=ok`.
 *
 * The assets are two-second synthetic clips laid out the way YouTube serves separate tracks, made with ffmpeg:
 * - `video-h264.mp4`: testsrc 64x64 at 30 fps, libx264, fragmented MP4 (60 frames);
 * - `audio-aac.m4a`: a 440 Hz sine, AAC 32 kbps, fragmented MP4 (88 frames);
 * - `video-vp9.webm`: testsrc 64x64 at 30 fps, libvpx-vp9, WebM (60 frames);
 * - `audio-opus.webm`: a 440 Hz sine, libopus 24 kbps, WebM (101 frames).
 *
 * Each join must keep every sample of every input (less any the joiner reports skipped for a time before zero), and
 * the output must read back with the same counts. Android 8 and 9 must refuse WebM up front.
 */
class JoinCheck : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        super.onStart()
        val results = Bundle()
        val lines = mutableListOf("api=${Build.VERSION.SDK_INT}")
        val ok = try {
            val work = File(targetContext.cacheDir, "join-check").apply { deleteRecursively(); mkdirs() }
            fun asset(name: String) = File(work, name).also { file -> context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } } }

            fun verify(label: String, inputs: List<String>, container: TrackJoiner.Container) {
                val files = inputs.map(::asset)
                val expected = files.map { TrackJoiner.sampleCounts(it).single() }
                val output = File(work, "$label.${container.extension}")
                val report = TrackJoiner.join(files, output, container)
                val kept = expected.zip(report.skipped) { all, skipped -> all - skipped }
                val readBack = TrackJoiner.sampleCounts(output)
                lines += "$label: inputs=$expected written=${report.samples} skipped=${report.skipped} readBack=$readBack"
                check(report.samples == kept) { "$label wrote ${report.samples}, expected $kept" }
                check(readBack.sorted() == kept.sorted()) { "$label reads back $readBack, expected $kept" }
                check(report.skipped.all { it <= 2 }) { "$label skipped ${report.skipped} samples" }
            }

            verify("mp4", listOf("video-h264.mp4", "audio-aac.m4a"), TrackJoiner.Container.MP4)
            verify("m4a", listOf("audio-aac.m4a"), TrackJoiner.Container.MP4)
            if (TrackJoiner.canJoin(TrackJoiner.Container.WEBM)) {
                verify("webm", listOf("video-vp9.webm", "audio-opus.webm"), TrackJoiner.Container.WEBM)
            } else {
                check(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { "WebM refused on API ${Build.VERSION.SDK_INT}" }
                val refused = runCatching { TrackJoiner.join(listOf(asset("video-vp9.webm")), File(work, "refused.webm"), TrackJoiner.Container.WEBM) }
                check(refused.isFailure && !File(work, "refused.webm").exists()) { "WebM was not refused before API 29" }
                lines += "webm: refused before API 29, as intended"
            }
            work.deleteRecursively()
            true
        } catch (failure: Throwable) {
            lines += "failure: $failure"
            false
        }
        results.putString("report", lines.joinToString("; "))
        results.putString("result", if (ok) "ok" else "failed")
        finish(if (ok) Activity.RESULT_OK else Activity.RESULT_CANCELED, results)
    }
}
