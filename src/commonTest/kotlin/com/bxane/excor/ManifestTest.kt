package com.bxane.excor

import com.bxane.excor.internal.isAllowedManifestUrl
import com.bxane.excor.internal.isManifest
import com.bxane.excor.internal.manifestUrlOf
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

private const val HLS = "https://manifest.googlevideo.com/api/manifest/hls_variant/expire/1800000000/id/abc.1/n/0y4uRkW-b4aOQ_bjZXB/source/yt_live_broadcast/file/index.m3u8"
private const val DASH = "https://manifest.googlevideo.com/api/manifest/dash/expire/1800000000/id/abc.1/n/0y4uRkW-b4aOQ_bjZXB/source/yt_live_broadcast"

/**
 * No live stream could be captured (the build machines get a bot check), so the VISIONOS capture is given the
 * manifest fields yt-dlp reads: `streamingData.hlsManifestUrl` / `dashManifestUrl`, and `videoDetails.isLive`.
 */
private fun liveResponse(hls: String?, dash: String?): String {
    val pr = fixtureJson("player-response-visionos.json")
    val data = pr["streamingData"] as JsonObject
    val streaming = JsonObject(buildMap {
        // A live stream's formats carry targetDurationSec and cannot be fetched whole; leave none to choose from.
        hls?.let { put("hlsManifestUrl", JsonPrimitive(it)) }
        dash?.let { put("dashManifestUrl", JsonPrimitive(it)) }
        put("expiresInSeconds", data["expiresInSeconds"]!!)
    })
    val details = JsonObject((pr["videoDetails"] as JsonObject) + ("isLive" to JsonPrimitive(true)))
    return JsonObject(pr + ("streamingData" to streaming) + ("videoDetails" to details)).toString()
}

class ManifestTest {
    private val host = FakeHost()

    @Test
    fun theNChallengeIsSolvedAndTheTokenGoesBeforeTheFileName() = runTest {
        val solved = manifestUrlOf(HLS, "TOKEN") { "solved-$it" }
        assertEquals(
            "https://manifest.googlevideo.com/api/manifest/hls_variant/expire/1800000000/id/abc.1/n/solved-0y4uRkW-b4aOQ_bjZXB/" +
                "source/yt_live_broadcast/pot/TOKEN/file/index.m3u8",
            solved,
        )
        assertEquals("$DASH/pot/TOKEN".replace("/n/0y4uRkW-b4aOQ_bjZXB/", "/n/x/"), manifestUrlOf(DASH, "TOKEN") { "x" })
        // No challenge, no token: as given.
        assertEquals("https://manifest.googlevideo.com/api/manifest/dash/id/abc?x=1", manifestUrlOf("https://manifest.googlevideo.com/api/manifest/dash/id/abc?x=1", null) { error("no challenge") })
    }

    @Test
    fun onlyGooglevideoManifestsAndRealManifestsCount() {
        assertTrue(isAllowedManifestUrl(HLS))
        assertFalse(isAllowedManifestUrl("https://manifest.googlevideo.com.example.org/api/manifest/hls_variant/x"))
        assertFalse(isAllowedManifestUrl("https://r1.googlevideo.com/videoplayback?id=1"))
        assertFalse(isAllowedManifestUrl("http://manifest.googlevideo.com/api/manifest/dash/x"))
        assertTrue(isManifest(ExcorManifestKind.HLS, "﻿#EXTM3U\n#EXT-X-VERSION:3"))
        assertTrue(isManifest(ExcorManifestKind.DASH, "<?xml version=\"1.0\"?>\n<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\">"))
        assertFalse(isManifest(ExcorManifestKind.HLS, "<html><body>Sorry</body></html>"))
        assertFalse(isManifest(ExcorManifestKind.DASH, "#EXTM3U"))
    }

    @Test
    fun aLiveStreamComesAsItsHlsManifest() = runTest(timeout = 5.minutes) {
        val engine = fixtureEngine(host, playerBody = liveResponse(HLS, DASH))
        val manifest = assertNotNull(engine.manifest("live1", setOf("TVHTML5")), "${host.logs}")
        assertEquals(ExcorManifestKind.HLS to "VISIONOS", manifest.kind to manifest.profileId)
        // The challenge solved with the captured player (fixtures/challenges-dce23b30.json).
        assertTrue("/n/Ah1cMh_G-imAsQ/" in manifest.url, manifest.url)
        assertTrue(manifest.isLive)
        assertEquals(setOf("User-Agent"), manifest.headers.keys)
        assertFalse(manifest.url in manifest.toString())
    }

    @Test
    fun dashIsTakenWhenThereIsNoHls() = runTest(timeout = 5.minutes) {
        val mpd = "<?xml version=\"1.0\"?><MPD type=\"dynamic\"></MPD>"
        val engine = fixtureEngine(host, playerBody = liveResponse(null, DASH), manifestAnswer = HttpStatusCode.OK to mpd)
        val manifest = assertNotNull(engine.manifest("live1", setOf("TVHTML5")), "${host.logs}")
        assertEquals(ExcorManifestKind.DASH, manifest.kind)
    }

    @Test
    fun aManifestThatDoesNotAnswerIsRefused() = runTest(timeout = 5.minutes) {
        val engine = fixtureEngine(host, playerBody = liveResponse(HLS, null), manifestAnswer = HttpStatusCode.OK to "<html>blocked</html>")
        assertNull(engine.manifest("live1", setOf("TVHTML5")))
        assertTrue(engine.isExcluded("live1", "VISIONOS"))
        assertTrue(host.logs.any { it.second == "Excor: VISIONOS manifest refused (not a HLS manifest)" }, "${host.logs}")
    }

    @Test
    fun visionosOffersAnHlsManifestForOrdinaryVideosToo() = runTest {
        // From the real capture: an hls_variant manifest with demuxed audio, and no n challenge to solve.
        val manifest = assertNotNull(fixtureEngine(host).manifest("dQw4w9WgXcQ", setOf("TVHTML5")), "${host.logs}")
        assertEquals(ExcorManifestKind.HLS to "VISIONOS", manifest.kind to manifest.profileId)
        assertTrue(manifest.url.startsWith("https://manifest.googlevideo.com/api/manifest/hls_variant/"))
        assertFalse(manifest.isLive)
    }

    @Test
    fun clientsWithoutManifestGiveNone() = runTest {
        // The TV and WEB captures have no manifest; WEB is not asked without a token.
        assertNull(fixtureEngine(host).manifest("dQw4w9WgXcQ", setOf("VISIONOS")))
    }
}
