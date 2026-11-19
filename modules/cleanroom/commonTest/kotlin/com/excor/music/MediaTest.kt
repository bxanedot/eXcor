package com.excor.music

import com.excor.catalog.ExcorCatalogProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MediaTest {
    @Test
    fun codecStringsFollowRfc6381() {
        assertEquals(ExcorAudioCodec.AAC_LC, excorAudioCodecOf("mp4a.40.2"))
        assertEquals(ExcorAudioCodec.HE_AAC, excorAudioCodecOf("mp4a.40.5"))
        assertEquals(ExcorAudioCodec.HE_AAC_V2, excorAudioCodecOf("mp4a.40.29"))
        assertEquals(ExcorAudioCodec.XHE_AAC, excorAudioCodecOf("mp4a.40.42"))
        assertEquals(ExcorAudioCodec.MP3, excorAudioCodecOf("mp4a.6B"))
        assertEquals(ExcorAudioCodec.FLAC, excorAudioCodecOf("fLaC"))
        assertEquals(ExcorAudioCodec.ALAC, excorAudioCodecOf("alac"))
        assertEquals(ExcorAudioCodec.EAC3, excorAudioCodecOf("ec-3"))
        assertEquals(ExcorAudioCodec.AC4, excorAudioCodecOf("ac-4.02.01.01"))
        assertEquals(ExcorAudioCodec.OTHER, excorAudioCodecOf("mp4a.40.99"))
        assertEquals(ExcorAudioCodec.OTHER, excorAudioCodecOf("avc1.64001f"))
    }

    @Test
    fun mimeTypesGiveContainerAndCodecWithoutGuessing() {
        val opus = excorAudioFormatOf("audio/webm; codecs=\"opus\"", 160)!!
        assertEquals(ExcorAudioCodec.OPUS, opus.codec)
        assertEquals(ExcorAudioContainer.WEBM, opus.container)
        assertEquals(160, opus.bitrateKbps)
        assertFalse(opus.lossless)
        assertEquals(ExcorAudioCodec.MP3, excorAudioFormatOf("audio/mpeg")!!.codec)
        assertTrue(excorAudioFormatOf("audio/flac")!!.lossless)
        assertEquals(ExcorAudioCodec.AAC, excorAudioFormatOf("audio/aac")!!.codec)
        // An MP4 with no codec could hold anything.
        assertNull(excorAudioFormatOf("audio/mp4"))
        assertNull(excorAudioFormatOf("video/mp4; codecs=\"avc1.64001f\""))
        assertEquals(16 to true, excorHlsChannelsOf("\"16/JOC\""))
        assertEquals(2 to false, excorHlsChannelsOf("2"))
    }

    @Test
    fun selectionRejectsWithReasonsAndRanksDeterministically() {
        val aac = ExcorAudioOption("aac", ExcorAudioFormat(ExcorAudioCodec.AAC_LC, bitrateKbps = 256))
        val alac = ExcorAudioOption("alac", ExcorAudioFormat(ExcorAudioCodec.ALAC, sampleRateHz = 48_000, bitDepth = 24), protected = true)
        val hires = ExcorAudioOption("hires", ExcorAudioFormat(ExcorAudioCodec.FLAC, sampleRateHz = 192_000, bitDepth = 24))
        val atmos = ExcorAudioOption("atmos", ExcorAudioFormat(ExcorAudioCodec.EAC3, bitrateKbps = 768, spatial = true))
        val locked = ExcorAudioOption("locked", ExcorAudioFormat(ExcorAudioCodec.FLAC, bitDepth = 16), entitled = false)
        val all = listOf(aac, alac, hires, atmos, locked)

        val best = excorSelectAudio(all)
        assertSame(hires, best.chosen)
        assertEquals(ExcorAudioRejection.PROTECTED, best.rejected["alac"])
        assertEquals(ExcorAudioRejection.NOT_ENTITLED, best.rejected["locked"])

        val capped = excorSelectAudio(all, ExcorAudioPreferences(maxSampleRateHz = 48_000, allowProtected = true))
        assertSame(alac, capped.chosen)
        assertEquals(ExcorAudioRejection.OVER_SAMPLE_RATE, capped.rejected["hires"])

        assertSame(atmos, excorSelectAudio(all, ExcorAudioPreferences(preferSpatial = true)).chosen)
        val lossy = excorSelectAudio(all, ExcorAudioPreferences(supportedCodecs = setOf(ExcorAudioCodec.AAC_LC, ExcorAudioCodec.EAC3), allowSpatial = false))
        assertSame(aac, lossy.chosen)
        assertEquals(ExcorAudioRejection.SPATIAL_NOT_ALLOWED, lossy.rejected["atmos"])
        assertNull(excorSelectAudio(listOf(locked)).chosen)
    }

    @Test
    fun hlsKeysNameTheSystemWithoutKeepingUris() {
        val fairplay = excorHlsProtectionOf(
            """
            #EXTM3U
            #EXT-X-SESSION-KEY:METHOD=SAMPLE-AES,URI="skd://synthetic-key",KEYFORMAT="com.apple.streamingkeydelivery",KEYFORMATVERSIONS="1"
            #EXT-X-KEY:METHOD=SAMPLE-AES-CTR,KEYFORMAT="urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed",URI="data:text/plain;base64,AAAA"
            """.trimIndent(),
        )
        assertTrue(fairplay.protected)
        assertEquals(setOf(ExcorDrmSystem.FAIRPLAY, ExcorDrmSystem.WIDEVINE), fairplay.systems)
        assertEquals(setOf(ExcorEncryptionScheme.HLS_SAMPLE_AES, ExcorEncryptionScheme.HLS_SAMPLE_AES_CTR), fairplay.schemes)
        assertTrue(fairplay.keyUriPresent)
        assertFalse("skd://" in fairplay.toString())

        val aes = excorHlsProtectionOf("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"https://example.invalid/k\"\n#EXTINF:4,\nseg.ts")
        assertTrue(aes.protected)
        assertEquals(emptySet(), aes.systems)
        assertEquals(setOf(ExcorEncryptionScheme.HLS_AES_128), aes.schemes)

        assertFalse(excorHlsProtectionOf("#EXTM3U\n#EXT-X-KEY:METHOD=NONE\n#EXTINF:4,\nseg.ts").protected)
    }

    @Test
    fun dashContentProtectionDescriptors() {
        val mpd = """
            <MPD><Period><AdaptationSet mimeType="audio/mp4">
              <ContentProtection schemeIdUri="urn:mpeg:dash:mp4protection:2011" value="cbcs" cenc:default_KID="00000000-0000-0000-0000-000000000000"/>
              <ContentProtection schemeIdUri="urn:uuid:9A04F079-9840-4286-AB92-E65BE0885F95"><mspr:pro>AAAA</mspr:pro></ContentProtection>
              <ContentProtection schemeIdUri="urn:uuid:EDEF8BA9-79D6-4ACE-A3C8-27DCD51D21ED"><cenc:pssh>AAAA</cenc:pssh><dashif:laurl>https://example.invalid/l</dashif:laurl></ContentProtection>
              <ContentProtection schemeIdUri="urn:uuid:00000000-1111-2222-3333-444444444444"/>
            </AdaptationSet></Period></MPD>
        """.trimIndent()
        val info = excorDashProtectionOf(mpd)
        assertTrue(info.protected)
        assertEquals(setOf(ExcorDrmSystem.PLAYREADY, ExcorDrmSystem.WIDEVINE, ExcorDrmSystem.UNKNOWN), info.systems)
        assertEquals(setOf(ExcorEncryptionScheme.CBCS), info.schemes)
        assertTrue(info.keyUriPresent)
        assertFalse(excorDashProtectionOf("<MPD><AdaptationSet mimeType=\"audio/mp4\"/></MPD>").protected)
    }

    @Test
    fun playbackStatusNeverClaimsProtectedAudioAsPlayable() = runTest {
        val track = ExcorMusicTrack(ExcorCatalogProvider.APPLE_MUSIC, "1", "Song", availability = ExcorAvailability.AVAILABLE)
        val providerProtected = ExcorProtectionInfo(true, setOf(ExcorDrmSystem.PROVIDER_MANAGED), source = ExcorProtectionSource.PROVIDER_STATEMENT)
        assertEquals(ExcorPlaybackStatus.PROTECTED_REQUIRES_CLIENT, excorPlaybackOptionFor(track, providerProtected, emptyList()).status)

        val notReady = FakeAdapter(ExcorCatalogProvider.APPLE_MUSIC, ready = false, entitled = true)
        assertEquals(ExcorPlaybackStatus.PROTECTED_REQUIRES_CLIENT, excorPlaybackOptionFor(track, providerProtected, listOf(notReady)).status)
        val otherProvider = FakeAdapter(ExcorCatalogProvider.SPOTIFY, ready = true, entitled = true)
        assertEquals(ExcorPlaybackStatus.PROTECTED_REQUIRES_CLIENT, excorPlaybackOptionFor(track, providerProtected, listOf(otherProvider)).status)

        val ready = FakeAdapter(ExcorCatalogProvider.APPLE_MUSIC, ready = true, entitled = true)
        val option = excorPlaybackOptionFor(track, providerProtected, listOf(otherProvider, ready))
        assertEquals(ExcorPlaybackStatus.PROTECTED_AUTHORIZED_ADAPTER, option.status)
        assertSame(ready, option.adapter)

        val noSubscription = FakeAdapter(ExcorCatalogProvider.APPLE_MUSIC, ready = true, entitled = false)
        assertEquals(ExcorPlaybackStatus.SUBSCRIPTION_REQUIRED, excorPlaybackOptionFor(track, providerProtected, listOf(noSubscription)).status)

        val widevine = ExcorProtectionInfo(true, setOf(ExcorDrmSystem.WIDEVINE), source = ExcorProtectionSource.MANIFEST)
        assertEquals(ExcorPlaybackStatus.DRM_UNSUPPORTED, excorPlaybackOptionFor(track, widevine, emptyList(), ExcorPlatformCapabilities(setOf(ExcorDrmSystem.FAIRPLAY))).status)
        assertEquals(ExcorPlaybackStatus.UNPROTECTED_PLAYABLE, excorPlaybackOptionFor(track, ExcorProtectionInfo.UNPROTECTED_MANIFEST, emptyList()).status)
        assertEquals(ExcorPlaybackStatus.REGION_RESTRICTED, excorPlaybackOptionFor(track.copy(availability = ExcorAvailability.REGION_RESTRICTED), null, listOf(ready)).status)
        assertEquals(ExcorPlaybackStatus.UNAVAILABLE, excorPlaybackOptionFor(track.copy(availability = ExcorAvailability.UNAVAILABLE), null, listOf(ready)).status)
    }

    private class FakeAdapter(override val provider: ExcorCatalogProvider, val ready: Boolean, val entitled: Boolean?) : ExcorProtectedPlaybackAdapter {
        override suspend fun isReady() = ready
        override suspend fun isEntitled(track: ExcorMusicTrack) = entitled
        override suspend fun play(track: ExcorMusicTrack) = Unit
    }

    @Test
    fun lrcLineAndWordTimes() {
        val lyrics = excorParseLrc(
            """
            [ar:Synthetic]
            [offset:+100]
            [00:01.50][00:20.00]First line
            [00:05.25]<00:05.25>Second <00:05.80>line <00:06.10>here
            [00:10.123]
            """.trimIndent(),
            source = "test",
        )
        assertEquals(ExcorLyricsSync.WORD, lyrics.sync)
        assertEquals(listOf(1_400L, 5_150L, 10_023L, 19_900L), lyrics.lines.map { it.startMs })
        assertEquals("First line", lyrics.lines[0].text)
        assertEquals("Second line here", lyrics.lines[1].text)
        assertEquals(listOf("Second", "line", "here"), lyrics.lines[1].words.map { it.text })
        assertEquals(5_700L, lyrics.lines[1].words[1].startMs)

        val plain = excorParseLrc("Just words\n\nMore words", source = "test")
        assertEquals(ExcorLyricsSync.NONE, plain.sync)
        assertEquals(listOf("Just words", "More words"), plain.lines.map { it.text })
    }

    @Test
    fun missingFieldsSayWhy() {
        val track = ExcorMusicTrack(ExcorCatalogProvider.DEEZER, "1", "Song", isrc = "GBAAA0000001", durationMs = 1000)
            .withMissing(offered = setOf(ExcorMusicField.ISRC, ExcorMusicField.DURATION, ExcorMusicField.LABEL), restricted = setOf(ExcorMusicField.MARKETS))
        assertEquals(ExcorFieldStatus.UNAVAILABLE, track.missing[ExcorMusicField.LABEL])
        assertEquals(ExcorFieldStatus.RESTRICTED, track.missing[ExcorMusicField.MARKETS])
        assertEquals(ExcorFieldStatus.UNSUPPORTED, track.missing[ExcorMusicField.COMPOSERS])
        assertNull(track.missing[ExcorMusicField.ISRC])
    }
}
