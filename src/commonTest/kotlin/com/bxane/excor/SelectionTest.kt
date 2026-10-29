package com.bxane.excor

import com.bxane.excor.internal.AudioFormat
import com.bxane.excor.internal.audioFormatsOf
import com.bxane.excor.internal.selectFormat
import com.bxane.excor.internal.str
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SelectionTest {
    // VISIONOS response: 139 (mp4 50k), 140 (mp4 130k), 249/250/251 (opus 49k/65k/136k).
    private val captured = audioFormatsOf(fixtureJson("player-response-visionos.json")).map { AudioFormat(it, it["url"].str!!) }
    private val webmOnly = captured.filter { it.mimeType.startsWith("audio/webm") }

    private fun fmt(itag: Int, mime: String, bitrate: Int, channels: Int? = 2, rate: Int? = 44_100) = AudioFormat(
        buildJsonObject {
            put("itag", itag)
            put("mimeType", mime)
            put("bitrate", bitrate)
            channels?.let { put("audioChannels", it) }
            rate?.let { put("audioSampleRate", it.toString()) }
        },
        "https://r1.googlevideo.com/videoplayback?itag=$itag",
    )

    @Test
    fun autoTakesBestWebm() = assertEquals(251, selectFormat(captured, 320, false)?.itag)

    @Test
    fun maxKbpsAbove64IsNotACap() {
        assertEquals(251, selectFormat(captured, 65, false)?.itag)
        assertEquals(251, selectFormat(captured, 128, false)?.itag)
    }

    @Test
    fun lowModeTakesLowestMp4() = assertEquals(139, selectFormat(captured, 64, false)?.itag)

    @Test
    fun lowModeFallsBackToLowestOverall() = assertEquals(249, selectFormat(webmOnly, 48, false)?.itag)

    @Test
    fun requireM4aTakesBestMp4OrNothing() {
        assertEquals(140, selectFormat(captured, 32, true)?.itag)
        assertEquals(140, selectFormat(captured, 320, true)?.itag)
        assertNull(selectFormat(webmOnly, 320, true))
    }

    @Test
    fun autoRanksMp4OverOtherCodecsWithoutWebm() =
        assertEquals(1, selectFormat(listOf(fmt(1, "audio/mp4", 100_000), fmt(2, "audio/ogg", 300_000)), 160, false)?.itag)

    @Test
    fun stereoAndUnknownChannelsGetABonus() {
        val webm = "audio/webm; codecs=\"opus\""
        assertEquals(2, selectFormat(listOf(fmt(1, webm, 140_000, channels = 1), fmt(2, webm, 100_000)), 160, false)?.itag)
        assertEquals(2, selectFormat(listOf(fmt(1, webm, 120_000, channels = 1), fmt(2, webm, 100_000, channels = null)), 160, false)?.itag)
        assertEquals(1, selectFormat(listOf(fmt(1, webm, 100_000), fmt(2, webm, 100_000, channels = null)), 160, false)?.itag)
    }

    @Test
    fun sampleRateBreaksTiesUpTo48k() {
        val webm = "audio/webm; codecs=\"opus\""
        assertEquals(2, selectFormat(listOf(fmt(1, webm, 100_000, rate = 44_100), fmt(2, webm, 100_000, rate = 48_000)), 160, false)?.itag)
        // 96 kHz counts as 48 kHz, so the first of the tied formats stays.
        assertEquals(1, selectFormat(listOf(fmt(1, webm, 100_000, rate = 48_000), fmt(2, webm, 100_000, rate = 96_000)), 160, false)?.itag)
    }
}
