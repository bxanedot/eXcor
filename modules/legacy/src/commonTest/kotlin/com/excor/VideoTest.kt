package com.excor

import com.excor.internal.VideoFormat
import com.excor.internal.selectVideo
import com.excor.internal.str
import com.excor.internal.videoFormatsOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoTest {
    private val host = FakeHost()

    private fun formats(client: String) =
        videoFormatsOf(fixtureJson("player-response-$client.json")).map { VideoFormat(it, it["url"].str.orEmpty()) }

    @Test
    fun picksTallestFittingThenH264() {
        val formats = formats("visionos")
        assertEquals(137, selectVideo(formats, 1080, false)?.itag)
        assertEquals(136, selectVideo(formats, 720, false)?.itag)
        // No H.264 at 2160p, so VP9 beats AV1.
        assertEquals(313, selectVideo(formats, 2160, false)?.itag)
        assertEquals(137, selectVideo(formats, 2160, true)?.itag)
        // Nothing fits: the smallest height.
        assertEquals(160, selectVideo(formats, 100, false)?.itag)
    }

    @Test
    fun combinedFormatsAreRecognised() =
        assertEquals(listOf(18), formats("tv_samsung").filter { it.muxed }.map { it.itag })

    @Test
    fun videoComesWithAudioAndBothAreRemembered() = runTest {
        val engine = fixtureEngine(host)
        val video = assertNotNull(engine.extractVideo("dQw4w9WgXcQ", 1080, setOf("TVHTML5"), false))
        assertEquals("VISIONOS", video.profileId)
        assertEquals(listOf(1920, 1080, 25), listOf(video.width, video.height, video.fps))
        assertTrue(video.mimeType.startsWith("video/mp4"), video.mimeType)
        val audio = assertNotNull(video.audio)
        assertTrue(audio.mimeType.startsWith("audio/webm"), audio.mimeType)
        assertEquals(video.headers, engine.headersFor(video.url))
        assertEquals(video.headers, engine.headersFor(audio.url))
        assertEquals("dQw4w9WgXcQ", engine.onRefused(video.url))
        assertTrue(engine.isExcluded("dQw4w9WgXcQ", "VISIONOS"))
    }

    @Test
    fun requireAvcPairsAacAudio() = runTest {
        val video = assertNotNull(fixtureEngine(host).extractVideo("dQw4w9WgXcQ", 2160, setOf("TVHTML5"), true))
        assertTrue(video.mimeType.contains("avc1"), video.mimeType)
        assertTrue(assertNotNull(video.audio).mimeType.startsWith("audio/mp4"))
    }

    @Test
    fun refusedVideoUrlIsNotReturned() = runTest {
        val engine = fixtureEngine(host, refusedClients = setOf("VISIONOS"))
        assertNull(engine.extractVideo("dQw4w9WgXcQ", 1080, setOf("TVHTML5"), false))
        assertTrue(engine.isExcluded("dQw4w9WgXcQ", "VISIONOS"))
    }
}
