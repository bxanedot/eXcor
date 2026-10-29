package com.bxane.excor.app

import com.bxane.excor.ExcorContainer
import com.bxane.excor.ExcorFormat
import com.bxane.excor.ExcorFormats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private fun video(itag: Int, codec: String, height: Int, fps: Int = 30, bytes: Long? = null) = ExcorFormat(
    itag, "u$itag", if (codec == "avc1") "video/mp4; codecs=\"avc1.640028\"" else "video/webm; codecs=\"$codec\"", 1000, bytes,
    hasAudio = false, hasVideo = true, width = height * 16 / 9, height = height, fps = fps,
)

private fun audio(itag: Int, aac: Boolean, kbps: Int, bytes: Long?) = ExcorFormat(
    itag, "u$itag", if (aac) "audio/mp4; codecs=\"mp4a.40.2\"" else "audio/webm; codecs=\"opus\"", kbps, bytes,
    hasAudio = true, hasVideo = false, width = null, height = null, fps = null,
)

/** Formats laid out as YouTube offers them: H.264 up to 1080p, VP9 above, an AV1-only 8K, and a 360p with sound. */
private val FORMATS = ExcorFormats(
    "v",
    listOf(
        video(571, "av01.0.16M.08", 4320),
        video(313, "vp9", 2160, bytes = 1_200_000_000),
        video(271, "vp9", 1440, bytes = 400_000_000),
        video(248, "vp9", 1080, bytes = 60_000_000),
        video(137, "avc1", 1080, bytes = 50_000_000),
        video(298, "avc1", 720, fps = 60, bytes = 30_000_000),
        audio(140, aac = true, kbps = 128, bytes = 3_000_000),
        audio(251, aac = false, kbps = 140, bytes = 3_200_000),
        audio(250, aac = false, kbps = 70, bytes = null),
        ExcorFormat(18, "u18", "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"", 500, null, hasAudio = true, hasVideo = true, width = 640, height = 360, fps = 30),
    ),
    "VISIONOS", "VISIONOS", emptyMap(),
)

class DownloadTest {
    @Test
    fun videoQualitiesPreferMp4AndLeaveOutWhatCannotBeMerged() {
        assertEquals(
            listOf("2160p · WebM · 1.2 GB", "1440p · WebM · 403 MB", "1080p · MP4 · 53 MB", "720p60 · MP4 · 33 MB"),
            videoOptions(FORMATS, writesOpus = true).map { it.label },
        )
        // Without Opus (Android 8 and 9) only MP4 is left, so nothing above 1080p.
        assertEquals(listOf(Choice.Video(1080), Choice.Video(720)), videoOptions(FORMATS, writesOpus = false).map { it.choice })
    }

    @Test
    fun audioQualitiesAreTheSavableFormatsBestFirst() {
        assertEquals(
            listOf("Opus · 140 kbps · 3 MB", "AAC · 128 kbps · 3 MB", "Opus · 70 kbps"),
            audioOptions(FORMATS, writesOpus = true).map { it.label },
        )
        assertEquals(listOf(Choice.Audio(140)), audioOptions(FORMATS, writesOpus = false).map { it.choice })
    }

    @Test
    fun aChoiceComesToFormatsAndAnExtension() {
        val best = assertIs<Plan.Audio>(planOf(FORMATS, Choice.Audio(null), writesOpus = true))
        assertEquals(251 to "opus", best.format.itag to best.extension)
        // Opus chosen where it cannot be written: the best AAC instead.
        val aac = assertIs<Plan.Audio>(planOf(FORMATS, Choice.Audio(251), writesOpus = false))
        assertEquals(140 to "m4a", aac.format.itag to aac.extension)

        val uhd = assertIs<Plan.Video>(planOf(FORMATS, Choice.Video(2160), writesOpus = true))
        assertEquals(Triple(313, 251, "webm"), Triple(uhd.pair.video.itag, uhd.pair.audio.itag, uhd.extension))
        // The same choice for a device without Opus: the tallest MP4.
        val hd = assertIs<Plan.Video>(planOf(FORMATS, Choice.Video(2160), writesOpus = false))
        assertEquals(Triple(137, 140, ExcorContainer.MP4), Triple(hd.pair.video.itag, hd.pair.audio.itag, hd.pair.container))
        assertNull(planOf(FORMATS, Choice.Video(144), writesOpus = true))
    }

    @Test
    fun fileNamesKeepTheTitleWithoutWhatFileSystemsRefuse() {
        assertEquals("AC_DC_ Back in Black_.m4a", fileNameOf("AC/DC: Back in Black?", "m4a"))
        assertEquals("download.mp4", fileNameOf(" ... ", "mp4"))
        assertEquals("a".repeat(120) + ".opus", fileNameOf("a".repeat(300), "opus"))
    }
}
