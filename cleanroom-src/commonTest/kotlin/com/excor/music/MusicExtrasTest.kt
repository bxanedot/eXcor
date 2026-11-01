package com.excor.music

import com.excor.catalog.ExcorCatalogError
import com.excor.catalog.ExcorCatalogException
import com.excor.catalog.ExcorCatalogProvider
import com.excor.catalog.ExcorCatalogTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

// Synthetic responses only.
class MusicExtrasTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun music(routes: Map<String, Pair<Int, String>>): ExcorMusic {
        val client = HttpClient(MockEngine { r ->
            requests += r
            val (status, body) = routes["${r.method.value} ${r.url.encodedPath}"] ?: (404 to "{}")
            if (status == 204) respond("", HttpStatusCode.NoContent)
            else respond(body, HttpStatusCode.fromValue(status), headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val tokens = mapOf(ExcorCatalogProvider.SPOTIFY to "s", ExcorCatalogProvider.APPLE_MUSIC to "a")
        return ExcorMusic(client, ExcorMusicConfig(tokens = ExcorCatalogTokenProvider { tokens[it] }, appleMusicUserToken = { "u" }), nowMs = { 0L })
    }

    @Test
    fun spotifyRemoteControlSendsCommandsToTheUsersClient() = runTest {
        val m = music(mapOf(
            "PUT /v1/me/player/play" to (204 to ""),
            "PUT /v1/me/player/pause" to (403 to """{"error":{"status":403,"message":"Player command failed: Premium required","reason":"PREMIUM_REQUIRED"}}"""),
            "PUT /v1/me/player/seek" to (204 to ""),
            "GET /v1/me/player/devices" to (200 to """{"devices":[{"id":"abc123","name":"Phone","type":"Smartphone","is_active":true,"is_restricted":false},{"name":"no id"}]}"""),
        ))
        m.spotifyCatalog.play(listOf("spotify:track:0123456789abcdefABCDEF"), deviceId = "abc123")
        val play = requests.last()
        assertEquals(HttpMethod.Put, play.method)
        assertEquals("abc123", play.url.parameters["device_id"])
        assertEquals("""{"uris":["spotify:track:0123456789abcdefABCDEF"]}""", (play.body as TextContent).text)

        assertEquals(ExcorCatalogError.ACCESS_DENIED, assertFailsWith<ExcorCatalogException> { m.spotifyCatalog.pause() }.error)
        m.spotifyCatalog.seek(30_000)
        assertEquals("30000", requests.last().url.parameters["position_ms"])
        assertEquals(listOf("abc123"), m.spotifyCatalog.devices().map { it.id })

        assertEquals(ExcorCatalogError.INVALID_REQUEST, assertFailsWith<ExcorCatalogException> { m.spotifyCatalog.play(listOf("spotify:track:x\"]}")) }.error)
        assertEquals(ExcorCatalogError.INVALID_REQUEST, assertFailsWith<ExcorCatalogException> { m.spotifyCatalog.play(listOf("spotify:album:2222222222222222222222")) }.error)
        assertEquals(ExcorCatalogError.INVALID_REQUEST, assertFailsWith<ExcorCatalogException> { m.spotifyCatalog.pause("bad id&x=1") }.error)
    }

    @Test
    fun spotifyEpisodesAndShowsStayOutOfMusicModels() = runTest {
        val episode = """{"type":"episode","id":"5555555555555555555555","name":"Episode One","description":"About things","duration_ms":1800000,
            "release_date":"2026-01-02","explicit":false,"language":"en","is_playable":true,
            "show":{"id":"6666666666666666666666","name":"The Show"},"external_urls":{"spotify":"https://open.spotify.com/episode/5555555555555555555555"},
            "images":[{"url":"https://i.scdn.co/image/ep","width":640,"height":640}]}"""
        val m = music(mapOf(
            "GET /v1/episodes/5555555555555555555555" to (200 to episode),
            "GET /v1/shows/6666666666666666666666" to (200 to """{"type":"show","id":"6666666666666666666666","name":"The Show","languages":["en"],"total_episodes":1}"""),
            "GET /v1/shows/6666666666666666666666/episodes" to (200 to """{"items":[$episode],"total":1,"next":null}"""),
        ))
        val e = m.spotifyCatalog.episode("5555555555555555555555")
        assertEquals("The Show", e.showTitle)
        assertEquals(1_800_000L, e.durationMs)
        assertEquals(listOf("en"), e.languages)
        val show = m.spotifyCatalog.show("6666666666666666666666")
        assertEquals(1, show.episodes.items.size)
        assertEquals(null, show.publisher)
    }

    @Test
    fun appleMusicVideosStationsAndRecommendations() = runTest {
        val m = music(mapOf(
            "GET /v1/catalog/us/music-videos/8001" to (200 to """{"data":[{"id":"8001","type":"music-videos","attributes":{"name":"Glass River (Video)","artistName":"Mira Vale",
                "isrc":"QZAAA2400002","durationInMillis":210000,"has4K":true,"hasHDR":false,"contentRating":"clean","genreNames":["Pop","Music"],
                "url":"https://music.apple.com/us/music-video/glass-river/8001","artwork":{"url":"https://is1-ssl.mzstatic.com/v/{w}x{h}.jpg","width":1920,"height":1080}}}]}"""),
            "GET /v1/catalog/us/stations/ra.123" to (200 to """{"data":[{"id":"ra.123","type":"stations","attributes":{"name":"Mira Vale Radio","isLive":false,"url":"https://music.apple.com/us/station/ra.123"}}]}"""),
            "GET /v1/me/recommendations" to (200 to """{"data":[{"id":"r1","type":"personal-recommendation","attributes":{"title":{"stringForDisplay":"Made for You"}},
                "relationships":{"contents":{"data":[{"id":"pl.abc","type":"playlists"},{"id":"7000","type":"albums"},{"id":"x","type":"apps"}]}}}]}"""),
        ))
        val video = m.appleMusicCatalog.musicVideo("8001")
        assertEquals(true, video.has4K)
        assertEquals(false, video.explicit)
        assertEquals(listOf("Pop"), video.genres)
        assertEquals(listOf(300, 600, 1200, 1920), video.artwork.map { it.width })
        assertEquals("Mira Vale Radio", m.appleMusicCatalog.station("ra.123").name)
        val recs = m.appleMusicCatalog.recommendations()
        assertEquals("Made for You", recs.single().title)
        assertEquals(listOf(ExcorMusicKind.PLAYLIST, ExcorMusicKind.ALBUM), recs.single().items.map { it.kind })
        assertTrue(requests.last().headers["Music-User-Token"] == "u")
        assertEquals(ExcorCatalogError.INVALID_URL, assertFailsWith<ExcorCatalogException> { m.appleMusicCatalog.station("../me") }.error)
    }

    @Test
    fun spotifyLibraryWritesQueueAndVolume() = runTest {
        val m = music(mapOf(
            "PUT /v1/me/library" to (200 to ""),
            "DELETE /v1/me/library" to (200 to ""),
            "GET /v1/me/library/contains" to (200 to "[true,false]"),
            "POST /v1/me/player/queue" to (204 to ""),
            "PUT /v1/me/player/volume" to (204 to ""),
        ))
        val uris = (0 until 45).map { "spotify:track:" + it.toString().padStart(22, '0') }
        m.spotifyCatalog.saveToLibrary(uris)
        val puts = requests.filter { it.method == HttpMethod.Put }
        assertEquals(listOf(40, 5), puts.map { it.url.parameters["uris"]!!.split(',').size })
        m.spotifyCatalog.removeFromLibrary(uris.take(2))
        assertEquals(HttpMethod.Delete, requests.last().method)
        assertEquals(listOf(true, false), m.spotifyCatalog.libraryContains(uris.take(2)))
        assertEquals(ExcorCatalogError.MALFORMED_RESPONSE, assertFailsWith<ExcorCatalogException> { m.spotifyCatalog.libraryContains(uris.take(3)) }.error)
        assertEquals(ExcorCatalogError.INVALID_REQUEST, assertFailsWith<ExcorCatalogException> { m.spotifyCatalog.saveToLibrary(listOf("https://open.spotify.com/track/x")) }.error)
        m.spotifyCatalog.queue("spotify:episode:5555555555555555555555")
        assertEquals("spotify:episode:5555555555555555555555", requests.last().url.parameters["uri"])
        m.spotifyCatalog.setVolume(35)
        assertEquals("35", requests.last().url.parameters["volume_percent"])
        assertEquals(ExcorCatalogError.INVALID_REQUEST, assertFailsWith<ExcorCatalogException> { m.spotifyCatalog.setVolume(101) }.error)
    }

    @Test
    fun ttmlLyricsWithLineAndWordTimes() {
        val ttml = """<?xml version="1.0" encoding="UTF-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml" xml:lang="en"><body><div>
              <p begin="00:00:01.500" end="00:00:04.000"><span begin="1.5s">Glass</span> <span begin="00:02.100">river &amp;</span> <span begin="2600ms">light</span></p>
              <p begin="5.25s">Second<br/>line</p>
              <p begin="1:00:00.000">Late</p>
            </div></body></tt>"""
        val lyrics = excorParseTtml(ttml, source = "test")
        assertEquals(ExcorLyricsSync.WORD, lyrics.sync)
        assertEquals("en", lyrics.language)
        assertEquals(listOf(1_500L, 5_250L, 3_600_000L), lyrics.lines.map { it.startMs })
        assertEquals("Glass river & light", lyrics.lines[0].text)
        assertEquals(listOf(1_500L, 2_100L, 2_600L), lyrics.lines[0].words.map { it.startMs })
        assertEquals("Second line", lyrics.lines[1].text)
        assertEquals(ExcorLyricsSync.NONE, excorParseTtml("<tt><body><p>Untimed &#x263A;</p></body></tt>", "test").sync)
        assertEquals("Untimed \u263A", excorParseTtml("<tt><body><p>Untimed &#x263A;</p></body></tt>", "test").lines.single().text)
        assertEquals(null, ttmlTime("soon"))
    }
}
