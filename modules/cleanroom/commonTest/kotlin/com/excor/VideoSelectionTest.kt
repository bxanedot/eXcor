package com.excor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoSelectionTest {
    private val formats = requireNotNull(decodePlayerResponse(fixture("player-response-video.json"))).formats
    private val allCodecs = setOf(ExcorVideoCodec.H264, ExcorVideoCodec.VP9, ExcorVideoCodec.AV1)

    @Test
    fun decodesVideoFieldsRangesAndSegmentedFormats() {
        val avc = formats.single { it.itag == 137 }
        assertEquals(1920, avc.width)
        assertEquals(1080, avc.height)
        assertEquals(30, avc.fps)
        assertEquals("1080p", avc.qualityLabel)
        assertEquals(5_000_000L, avc.contentLength)
        assertEquals(0L..740L, avc.initRange)
        assertEquals(741L..1240L, avc.indexRange)
        assertFalse(avc.segmented)
        assertFalse(avc.containsAudio)
        assertEquals(ExcorVideoCodec.H264, avc.videoCodec)
        assertEquals(VideoContainer.MP4, avc.container)

        assertTrue(formats.single { it.itag == 299 }.segmented)
        assertTrue(formats.single { it.itag == 18 }.containsAudio)
        assertEquals(ExcorVideoCodec.VP9, formats.single { it.itag == 248 }.videoCodec)
        assertEquals(VideoContainer.WEBM, formats.single { it.itag == 248 }.container)
        assertEquals(ExcorVideoCodec.AV1, formats.single { it.itag == 399 }.videoCodec)
    }

    @Test
    fun ranksByHeightThenFrameRateThenCodecCompatibility() {
        // 1440p is over the cap, the 1080p60 format is segmented, audio formats are not video.
        assertEquals(listOf(137, 248, 399, 298, 18), rankVideoFormats(formats, 1080, allCodecs).map { it.itag })
        assertEquals(listOf(400, 137, 248, 399, 298, 18), rankVideoFormats(formats, 2160, allCodecs).map { it.itag })
        assertEquals(listOf(298, 18), rankVideoFormats(formats, 720, allCodecs).map { it.itag })
    }

    @Test
    fun codecFilterAndProgressiveOrdering() {
        assertEquals(listOf(248), rankVideoFormats(formats, 1080, setOf(ExcorVideoCodec.VP9)).map { it.itag })
        assertEquals(listOf(18), rankVideoFormats(formats, 480, allCodecs).map { it.itag })

        // At the same height, a separate video track ranks before a progressive one.
        val progressive720 = formats.single { it.itag == 18 }.copy(width = 1280, height = 720, fps = 60, itag = 22)
        val ranked = rankVideoFormats(formats + progressive720, 720, allCodecs).map { it.itag }
        assertEquals(listOf(298, 22, 18), ranked)
    }

    @Test
    fun formatsWithoutAllowedUrlsOrHeightsAreLeftOut() {
        val offList = formats.single { it.itag == 137 }.copy(url = "https://evil.example/videoplayback", itag = 1)
        val noHeight = formats.single { it.itag == 137 }.copy(height = null, itag = 2)
        val ranked = rankVideoFormats(listOf(offList, noHeight), 1080, allCodecs)
        assertTrue(ranked.isEmpty())
    }
}
