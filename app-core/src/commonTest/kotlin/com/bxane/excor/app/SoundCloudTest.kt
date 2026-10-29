package com.bxane.excor.app

import com.bxane.excor.SoundCloudProtocol
import com.bxane.excor.SoundCloudStream
import com.bxane.excor.SoundCloudTrack
import com.bxane.excor.SoundCloudTranscoding
import com.bxane.excor.ExcorStream
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private fun transcoding(preset: String, mimeType: String?, protocol: SoundCloudProtocol = SoundCloudProtocol.HLS, preview: Boolean = false) =
    SoundCloudTranscoding(preset, protocol, mimeType, preview, "https://api-v2.soundcloud.com/media/soundcloud:tracks:1/$preset", null)

private val AAC_160 = transcoding("aac_160k", "audio/mp4; codecs=\"mp4a.40.2\"")
private val AAC_96 = transcoding("aac_96k", "audio/mp4; codecs=\"mp4a.40.2\"")
private val MP3 = transcoding("mp3_0_0", "audio/mpeg", SoundCloudProtocol.PROGRESSIVE)
private val OPUS = transcoding("opus_0_0", "audio/ogg; codecs=\"opus\"")

private fun track(transcodings: List<SoundCloudTranscoding>, blocked: Boolean = false, drm: Boolean = false) = SoundCloudTrack(
    7, "Song", "uploader", "https://soundcloud.com/u", null, 61_500, "https://i1.sndcdn.com/a-t500x500.jpg",
    "https://soundcloud.com/u/song", null, blocked, drm, transcodings,
)

class SoundCloudTest {
    @Test
    fun theBestTranscodingIsWholeThenAacThenTheHighestBitrate() {
        assertEquals(AAC_160, bestTranscoding(listOf(MP3, AAC_96, OPUS, AAC_160)))
        assertEquals(MP3, bestTranscoding(listOf(OPUS, MP3)))
        // A whole track in a lesser codec beats a 30-second preview.
        assertEquals(OPUS, bestTranscoding(listOf(AAC_160.copy(isPreview = true), OPUS)))
        assertNull(bestTranscoding(emptyList()))
    }

    @Test
    fun extensions() {
        assertEquals(listOf("m4a", "m4a", "mp3", "opus"), listOf(AAC_160, AAC_96, MP3, OPUS).map(::soundCloudExtensionOf))
    }

    @Test
    fun aTrackForTheQueuePlaysByItsPageUrl() {
        assertEquals(
            Track("https://soundcloud.com/u/song", "Song", "uploader", 61, "https://i1.sndcdn.com/a-t500x500.jpg", Source.SOUNDCLOUD),
            track(listOf(AAC_96)).toTrack(),
        )
        assertEquals("Artist", track(emptyList()).copy(artist = "Artist").toTrack()?.artist)
        assertNull(track(emptyList()).copy(permalinkUrl = null).toTrack())
    }

    @Test
    fun theStreamOfItsBestTranscoding() = runTest {
        val asked = mutableListOf<SoundCloudTranscoding>()
        val hls = soundCloudStreamOf(track(listOf(AAC_96, AAC_160))) {
            asked += it
            SoundCloudStream("https://playback.media-streaming.soundcloud.cloud/x/playlist.m3u8", SoundCloudProtocol.HLS, it.mimeType, false)
        }
        assertEquals(listOf(AAC_160), asked)
        assertEquals(
            ExcorStream(
                "https://soundcloud.com/u/song", "https://playback.media-streaming.soundcloud.cloud/x/playlist.m3u8", 0, HLS_MIME_TYPE, null,
                "SOUNDCLOUD", "SOUNDCLOUD", emptyMap(),
            ),
            hls,
        )
        val mp3 = soundCloudStreamOf(track(listOf(MP3))) { SoundCloudStream("https://cf-media.sndcdn.com/a.mp3", SoundCloudProtocol.PROGRESSIVE, "audio/mpeg", false) }
        assertEquals("audio/mpeg", mp3?.mimeType)
        assertNull(soundCloudStreamOf(null) { error("not asked") })
    }

    @Test
    fun blockedAndDrmOnlyTracksAreRefusedUnasked() = runTest {
        val never: suspend (SoundCloudTranscoding) -> SoundCloudStream = { error("not asked") }
        assertEquals(Failure.NOT_IN_COUNTRY, assertFailsWith<Refused> { soundCloudStreamOf(track(listOf(AAC_96), blocked = true), never) }.failure)
        assertEquals(Failure.DRM, assertFailsWith<Refused> { soundCloudStreamOf(track(emptyList(), drm = true), never) }.failure)
        assertEquals(Failure.NO_STREAM, assertFailsWith<Refused> { soundCloudStreamOf(track(emptyList()), never) }.failure)
        // The queue reads a refusal as its failure.
        assertEquals(Failure.DRM to null, failureOf(Refused(Failure.DRM)))
    }
}
