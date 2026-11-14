package com.excor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SelectionTest {
    @Test
    fun requireM4aUsesAutoRankingAmongM4aOnly() {
        val result = selectAudioFormat(
            listOf(
                format("webm", "audio/webm; codecs=\"opus\"", 256_000, 2, 48_000),
                format("stereo-m4a", "audio/mp4", 128_000, 2, 48_000),
                format("mono-m4a", "audio/mp4", 150_000, 1, 48_000),
            ),
            maxKbps = 192,
            requireM4a = true,
        )

        assertEquals("https://rr1.googlevideo.com/videoplayback?key=stereo-m4a", result?.url)
    }

    @Test
    fun lowQualityModePrefersLowestM4aBeforeOtherCodecs() {
        val result = selectAudioFormat(
            listOf(
                format("webm-low", "audio/webm; codecs=\"opus\"", 32_000, 2, 48_000),
                format("m4a-high", "audio/mp4", 192_000, 2, 48_000),
                format("m4a-low", "audio/mp4", 96_000, 2, 44_100),
            ),
            maxKbps = 64,
            requireM4a = false,
        )

        assertEquals("https://rr1.googlevideo.com/videoplayback?key=m4a-low", result?.url)
    }

    @Test
    fun autoPrefersWebmThenRanksEffectiveBitrateAndSampleRate() {
        val result = selectAudioFormat(
            listOf(
                format("mp4-high", "audio/mp4", 320_000, 2, 48_000),
                format("webm-unknown", "audio/webm; codecs=\"opus\"", 100_000, null, 44_100),
                format("webm-stereo", "audio/webm; codecs=\"opus\"", 75_000, 2, 48_000),
                format("webm-mono", "audio/webm; codecs=\"opus\"", 90_000, 1, 44_100),
            ),
            maxKbps = 128,
            requireM4a = false,
        )

        assertEquals("https://rr1.googlevideo.com/videoplayback?key=webm-stereo", result?.url)
    }

    @Test
    fun maxKbpsAbove64DoesNotCapTheSelectedBitrate() {
        val result = selectAudioFormat(
            listOf(format("high", "audio/mp4", 192_000, 2, 48_000)),
            maxKbps = 128,
            requireM4a = false,
        )

        assertEquals(192, result?.kbps)
    }

    @Test
    fun requireM4aReturnsNullWhenNoM4aExists() {
        assertNull(
            selectAudioFormat(
                listOf(format("webm", "audio/webm; codecs=\"opus\"", 96_000, 2, 48_000)),
                maxKbps = 128,
                requireM4a = true,
            ),
        )
    }

    private fun format(
        url: String,
        mimeType: String,
        bitrate: Int,
        channels: Int?,
        sampleRate: Int,
    ) = PlayerFormat(
        "https://rr1.googlevideo.com/videoplayback?key=$url",
        mimeType,
        bitrate,
        null,
        channels,
        sampleRate,
    )
}
