package com.excor

import com.excor.internal.SoundCloudEngine
import com.excor.internal.bareTrackIdsOf
import com.excor.internal.isAllowedSoundCloudStream
import com.excor.internal.isSoundCloudPage
import com.excor.internal.soundCloudPlaylistOf
import com.excor.internal.soundCloudTrackOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SoundCloudTest {
    private val clientId = "abcdefghijABCDEFGHIJ0123456789ab"
    private val streamUrl = "https://playback.media-streaming.soundcloud.cloud/x1/aac_96k/y2/playlist.m3u8?expires=1"

    @Test
    fun trackFromCapture() {
        val track = assertNotNull(soundCloudTrackOf(fixtureJson("soundcloud-track.json")))
        assertEquals(62986583L, track.id)
        assertEquals("Lostin Powers - She so Heavy (SneakPreview) Adrian Ackers Blueprint 1", track.title)
        assertEquals(listOf<Any?>("E.T. ExTerrestrial Music", "https://soundcloud.com/ethmusic", null, null), listOf(track.uploader, track.uploaderUrl, track.artist, track.genre))
        assertEquals(143206L, track.durationMs)
        assertEquals("https://i1.sndcdn.com/artworks-000031955188-rwb18x-t500x500.jpg", track.artworkUrl)
        assertEquals("https://soundcloud.com/ethmusic/lostin-powers-she-so-heavy", track.permalinkUrl)
        assertFalse(track.isBlocked || track.hasDrm)
        // abr_sq is left out, as yt-dlp does.
        val aac = track.transcodings.single()
        assertEquals(listOf<Any?>("aac_96k", SoundCloudProtocol.HLS, "audio/mp4; codecs=\"mp4a.40.2\"", false, null), listOf(aac.preset, aac.protocol, aac.mimeType, aac.isPreview, aac.secretToken))
        assertTrue(aac.url.startsWith("https://api-v2.soundcloud.com/media/soundcloud:tracks:62986583/"))
        // Neither the transcoding's URL nor a token is in its text.
        assertEquals("SoundCloudTranscoding(preset=aac_96k, protocol=HLS, mimeType=audio/mp4; codecs=\"mp4a.40.2\", isPreview=false)", aac.toString())
    }

    @Test
    fun setFromCaptureWithDrmTracksLeftEmpty() {
        val set = fixtureJson("soundcloud-playlist.json")
        // The last of the six came without details; the capture asked for it with tracks?ids=.
        assertEquals(listOf("47127631"), bareTrackIdsOf(set))
        val details = (Json.parseToJsonElement(fixture("soundcloud-playlist-tracks.json")) as JsonArray).associateBy { it.jsonObjectId() }
        val playlist = assertNotNull(soundCloudPlaylistOf(set) { details[it] })
        assertEquals(listOf<Any?>(2284613L, "The Royal Concept EP", "The Royal Concept", "ep"), listOf(playlist.id, playlist.title, playlist.uploader, playlist.setType))
        assertEquals("https://i1.sndcdn.com/artworks-000030896212-o16m9v-t500x500.jpg", playlist.artworkUrl)
        assertEquals(listOf(75206121L, 47127625L, 47127627L, 30510138L, 47127629L, 47127631L), playlist.tracks.map { it.id })
        // Three are only offered encrypted: DRM, so nothing to play, and said so.
        val drm = playlist.tracks.filter { it.hasDrm }
        assertEquals(listOf("World On Fire (Re-Mastered)", "Goldrushed (Re-Mastered)", "In The End"), drm.map { it.title })
        assertTrue(drm.all { it.transcodings.isEmpty() && it.artist == "The Royal Concept" })
        val plain = playlist.tracks.filterNot { it.hasDrm }
        assertTrue(plain.all { t -> t.transcodings.map { it.preset } == listOf("aac_160k", "aac_96k") && t.transcodings.all { it.protocol == SoundCloudProtocol.HLS } })
        assertEquals("Knocked Up", playlist.tracks.last().title)
        // Without the details, a bare track is left out rather than made up.
        assertEquals(5, assertNotNull(soundCloudPlaylistOf(set) { null }).tracks.size)
    }

    @Test
    fun transcodingsAsYtDlpReadsThem() {
        val track = soundCloudTrackOf(
            Json.parseToJsonElement(
                """{"id":7,"policy":"BLOCK","secret_token":"s-Tok","media":{"transcodings":[
                {"url":"https://api-v2.soundcloud.com/media/soundcloud:tracks:7/a/stream/progressive","preset":"mp3_0_0","format":{"protocol":"progressive","mime_type":"audio/mpeg"}},
                {"url":"https://api-v2.soundcloud.com/media/soundcloud:tracks:7/b/stream/hls","preset":"opus_0_0","format":{"protocol":"progressive","mime_type":"audio/ogg"}},
                {"url":"https://api-v2.soundcloud.com/media/soundcloud:tracks:7/c/preview/progressive","preset":"mp3_1_0","snipped":true,"format":{"protocol":"progressive"}},
                {"url":"https://api-v2.soundcloud.com/media/soundcloud:tracks:7/d/stream/encrypted-hls","preset":"aac_160k","format":{"protocol":"hls"}},
                {"url":"https://elsewhere.example/media/x","preset":"mp3_0_0","format":{"protocol":"progressive"}},
                {"url":"https://api-v2.soundcloud.com/media/soundcloud:tracks:7/e/stream/hls","format":{"protocol":"hls"}}]}}""",
            ),
        )!!
        assertEquals(listOf("mp3_0_0", "opus_0_0", "mp3_1_0"), track.transcodings.map { it.preset })
        assertEquals(listOf(SoundCloudProtocol.PROGRESSIVE, SoundCloudProtocol.HLS, SoundCloudProtocol.PROGRESSIVE), track.transcodings.map { it.protocol })
        assertEquals(listOf(false, false, true), track.transcodings.map { it.isPreview })
        assertTrue(track.hasDrm && track.isBlocked)
        // A private track's token goes with each transcoding; the uploader's picture stands in for missing artwork.
        assertTrue(track.transcodings.all { it.secretToken == "s-Tok" })
        assertNull(track.artworkUrl)
        assertNull(soundCloudTrackOf(Json.parseToJsonElement("""{"kind":"track"}""")))
    }

    @Test
    fun linksAndStreamHosts() {
        listOf(
            "https://soundcloud.com/ethmusic/lostin-powers-she-so-heavy",
            "https://m.soundcloud.com/the-concept-band/sets/the-royal-concept-ep",
            "https://www.soundcloud.com/jaimemf/youtube-dl-test-video-a-y-baw/s-8Pjrp?in=x",
        ).forEach { assertTrue(isSoundCloudPage(it), it) }
        listOf(
            "http://soundcloud.com/a/b", "https://soundcloud.com.evil.example/a/b", "https://user@soundcloud.com/a/b",
            "https://soundcloud.com:8443/a/b", "https://api-v2.soundcloud.com/tracks/1", "https://soundcloud.com/",
        ).forEach { assertFalse(isSoundCloudPage(it), it) }
        assertTrue(isAllowedSoundCloudStream(streamUrl))
        assertTrue(isAllowedSoundCloudStream("https://cf-media.sndcdn.com/abc.128.mp3?Policy=x"))
        listOf("http://cf-media.sndcdn.com/a.mp3", "https://sndcdn.com.evil.example/a", "https://evil.example/soundcloud.cloud/a")
            .forEach { assertFalse(isAllowedSoundCloudStream(it), it) }
    }

    private fun engine(
        requests: MutableList<HttpRequestData>,
        refuseFirstCall: Boolean = false,
        stream: String = streamUrl,
        media: Map<String, String> = emptyMap(),
    ): SoundCloudEngine {
        var refused = !refuseFirstCall
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        return SoundCloudEngine(FakeHost().apply { cookie = "SID=secret" }, HttpClient(MockEngine { request ->
            requests += request
            val url = request.url
            when {
                url.host == "soundcloud.com" -> respond(
                    """<script crossorigin src="https://a-v2.sndcdn.com/assets/1-aa.js"></script>""" +
                        """<script crossorigin src="https://a-v2.sndcdn.com/assets/2-bb.js"></script><script src="https://evil.example/3.js"></script>""",
                )
                url.encodedPath.endsWith("2-bb.js") -> respond("""var a={b:1}""")
                url.encodedPath.endsWith("1-aa.js") -> respond("""x={client_id:"$clientId",env:"production"}""")
                url.host != "api-v2.soundcloud.com" && url.encodedPath in media -> media.getValue(url.encodedPath).let {
                    respond(it, headers = headersOf(HttpHeaders.ContentLength, it.length.toString()))
                }
                url.host != "api-v2.soundcloud.com" -> error("unexpected host ${url.host}")
                !refused -> respond("", HttpStatusCode.Unauthorized).also { refused = true }
                url.encodedPath == "/resolve" -> when {
                    "lostin" in url.parameters["url"]!! -> respond(fixture("soundcloud-track.json"), headers = json)
                    "/sets/" in url.parameters["url"]!! -> respond(fixture("soundcloud-playlist.json"), headers = json)
                    else -> respond("""{"code":404}""", HttpStatusCode.NotFound, json)
                }
                url.encodedPath == "/tracks" -> respond(fixture("soundcloud-playlist-tracks.json"), headers = json)
                url.encodedPath.startsWith("/media/") -> respond("""{"url":"$stream"}""", headers = json)
                else -> error("unexpected path")
            }
        }))
    }

    @Test
    fun tracksSetsAndStreamsAreLoggedOutCalls() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = engine(requests)
        val track = assertNotNull(engine.track("https://soundcloud.com/ethmusic/lostin-powers-she-so-heavy"))
        // The client ID is looked up once: SoundCloud's own scripts, the last first; other hosts are not asked.
        assertEquals(
            listOf("soundcloud.com/", "a-v2.sndcdn.com/assets/2-bb.js", "a-v2.sndcdn.com/assets/1-aa.js", "api-v2.soundcloud.com/resolve"),
            requests.map { it.url.host + it.url.encodedPath },
        )
        assertEquals(clientId, requests.last().url.parameters["client_id"])
        assertEquals("https://soundcloud.com/ethmusic/lostin-powers-she-so-heavy", requests.last().url.parameters["url"])
        val stream = engine.stream(track.transcodings.single())
        assertEquals(SoundCloudStream(streamUrl, SoundCloudProtocol.HLS, "audio/mp4; codecs=\"mp4a.40.2\"", false), stream)
        assertFalse(streamUrl in stream.toString())
        val playlist = assertNotNull(engine.playlist("https://soundcloud.com/the-concept-band/sets/the-royal-concept-ep"))
        assertEquals(6, playlist.tracks.size)
        assertEquals("47127631", requests.single { it.url.encodedPath == "/tracks" }.url.parameters["ids"])
        // A link to nothing, or a set asked as a track, is null; anything but a SoundCloud page is refused unasked.
        assertNull(engine.track("https://soundcloud.com/nobody/nothing"))
        assertNull(engine.track("https://soundcloud.com/the-concept-band/sets/the-royal-concept-ep"))
        assertNull(engine.playlist("https://soundcloud.com/ethmusic/lostin-powers-she-so-heavy"))
        val asked = requests.size
        assertFailsWith<IllegalArgumentException> { engine.track("https://evil.example/ethmusic/x") }
        assertEquals(asked, requests.size)
        assertTrue(requests.all { it.headers[HttpHeaders.Cookie] == null })
        assertEquals(1, requests.count { it.url.host == "soundcloud.com" })
    }

    @Test
    fun aRefusedClientIdIsLookedUpAgainOnce() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        assertNotNull(engine(requests, refuseFirstCall = true).track("https://soundcloud.com/ethmusic/lostin-powers-she-so-heavy"))
        assertEquals(2, requests.count { it.url.host == "soundcloud.com" })
        assertEquals(2, requests.count { it.url.encodedPath == "/resolve" })
    }

    @Test
    fun anHlsStreamIsDownloadedAsItsSegmentsInOrder() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val playlist = "#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-PLAYLIST-TYPE:VOD\n#EXT-X-MAP:URI=\"init.mp4\"\n" +
            "#EXTINF:10.0,\nhttps://playback.media-streaming.soundcloud.cloud/x1/aac_96k/y2/s0.m4s?a=1\n#EXTINF:3.1,\ns1.m4s\n#EXT-X-ENDLIST\n"
        val engine = engine(
            requests,
            media = mapOf(
                "/x1/aac_96k/y2/playlist.m3u8" to playlist,
                "/x1/aac_96k/y2/init.mp4" to "INIT",
                "/x1/aac_96k/y2/s0.m4s" to "-S0",
                "/x1/aac_96k/y2/s1.m4s" to "-S1",
            ),
        )
        val track = assertNotNull(engine.track("https://soundcloud.com/ethmusic/lostin-powers-she-so-heavy"))
        val out = StringBuilder()
        val progress = mutableListOf<Float>()
        assertEquals(10L, engine.download(track.transcodings.single(), { out.append(it.decodeToString()) }) { progress += it })
        assertEquals("INIT-S0-S1", out.toString())
        assertEquals(listOf(1f / 3, 2f / 3, 1f), progress)
        assertTrue(requests.all { it.headers[HttpHeaders.Cookie] == null })
    }

    @Test
    fun aProgressiveStreamIsCopiedAndAPlaylistPointingElsewhereIsRefused() = runTest {
        val file = "https://cf-media.sndcdn.com/abc.128.mp3?Policy=x"
        val engine = engine(mutableListOf(), stream = file, media = mapOf("/abc.128.mp3" to "ID3-and-the-rest"))
        val mp3 = soundCloudTrackOf(fixtureJson("soundcloud-track.json"))!!.transcodings.single()
            .copy(protocol = SoundCloudProtocol.PROGRESSIVE)
        val out = StringBuilder()
        val progress = mutableListOf<Float>()
        assertEquals(16L, engine.download(mp3, { out.append(it.decodeToString()) }) { progress += it })
        assertEquals("ID3-and-the-rest", out.toString())
        assertEquals(1f, progress.last())
        val elsewhere = engine(
            mutableListOf(),
            media = mapOf("/x1/aac_96k/y2/playlist.m3u8" to "#EXTM3U\n#EXTINF:1,\nhttps://evil.example/s0.m4s\n#EXT-X-ENDLIST\n"),
        )
        val track = assertNotNull(elsewhere.track("https://soundcloud.com/ethmusic/lostin-powers-she-so-heavy"))
        val e = assertFailsWith<IllegalStateException> { elsewhere.download(track.transcodings.single(), {}, null) }
        assertEquals("Excor: SoundCloud's playlist points elsewhere", e.message)
    }

    @Test
    fun aStreamFromAnotherHostIsRefused() = runTest {
        val engine = engine(mutableListOf(), stream = "https://evil.example/a.m3u8")
        val track = assertNotNull(engine.track("https://soundcloud.com/ethmusic/lostin-powers-she-so-heavy"))
        val e = assertFailsWith<IllegalStateException> { engine.stream(track.transcodings.single()) }
        assertEquals("Excor: SoundCloud gave no stream", e.message)
        assertFailsWith<IllegalArgumentException> {
            engine.stream(track.transcodings.single().copy(url = "https://evil.example/media/x"))
        }
    }
}

private fun kotlinx.serialization.json.JsonElement.jsonObjectId() =
    (this as kotlinx.serialization.json.JsonObject)["id"].toString()
