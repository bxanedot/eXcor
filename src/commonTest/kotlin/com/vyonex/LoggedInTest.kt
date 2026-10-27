package com.vyonex

import com.vyonex.internal.DESKTOP_UA
import com.vyonex.internal.isLoggedIn
import com.vyonex.internal.primary.extraction.PoTokenResult
import com.vyonex.internal.primary.extraction.TokenProvider
import com.vyonex.internal.sidAuthorization
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** Logged-in and PO-token paths, offline with made-up cookies and tokens (no real account). */
class LoggedInTest {
    private val host = FakeHost()
    private val cookie = "LOGIN_INFO=login1; SAPISID=sid1; __Secure-1PAPISID=sid2; __Secure-3PAPISID=sid3"

    private val tokens = object : TokenProvider {
        val calls = mutableListOf<Triple<String, String, String?>>()
        override suspend fun getPoToken(videoId: String, visitorData: String, cookie: String?): PoTokenResult {
            calls += Triple(videoId, visitorData, cookie)
            return PoTokenResult("PLAYER-TOKEN", "STREAM-TOKEN", "TOKEN-VISITOR")
        }
    }

    private fun List<HttpRequestData>.playerRequests() = filter { it.url.encodedPath == "/youtubei/v1/player" }

    @Test
    fun loginNeedsLoginInfoAndASapisidCookie() {
        assertTrue(isLoggedIn("LOGIN_INFO=a; SAPISID=b"))
        assertTrue(isLoggedIn("LOGIN_INFO=a; __Secure-3PAPISID=b"))
        assertFalse(isLoggedIn("SAPISID=b"))
        assertFalse(isLoggedIn("LOGIN_INFO=a; CONSENT=YES+1"))
        assertFalse(isLoggedIn(null))
    }

    // Expected hashes computed independently: sha1("1000 <sid> https://www.youtube.com").
    @Test
    fun sapisidHashesFollowYtDlp() = assertEquals(
        "SAPISIDHASH 1000_afe2e337fbee0041edac04a035f06dc63b64aaca " +
            "SAPISID1PHASH 1000_0e9f071c26450a7dc82ad67aabec7b029d0c1440 " +
            "SAPISID3PHASH 1000_311993f66875a5dfaa77002381a8cd53727fd867",
        sidAuthorization(cookie, 1_000_000L),
    )

    @Test
    fun aConsentCookieIsSentButIsNoLogin() = runTest {
        host.cookie = "CONSENT=YES+1"
        val requests = mutableListOf<HttpRequestData>()
        val stream = assertNotNull(fixtureEngine(host, requests = requests).extract("dQw4w9WgXcQ", 160, setOf("TV_SAMSUNG"), false))
        assertEquals("VISIONOS", stream.profileId)
        // Media requests carry a cookie only for a login.
        assertEquals(setOf("User-Agent"), stream.headers.keys)
        val player = requests.playerRequests().single()
        assertEquals("CONSENT=YES+1", player.headers[HttpHeaders.Cookie])
        assertNull(player.headers[HttpHeaders.Authorization])
        assertNull(player.headers["X-Origin"])
    }

    @Test
    fun loggedInWebUsesCredentialsAndThePoToken() = runTest(timeout = 5.minutes) {
        host.cookie = cookie
        host.visitorData = "SESSION-VISITOR"
        val requests = mutableListOf<HttpRequestData>()
        // WEB is answered with the VISIONOS capture, whose formats have direct URLs (no SABR); WEB still needs the
        // player's signature timestamp, so the captured player is prepared first.
        val engine = fixtureEngine(host, tokenProvider = tokens, webFixture = "visionos", requests = requests)
        val stream = assertNotNull(engine.extract("dQw4w9WgXcQ", 160, setOf("TV_SAMSUNG"), false), "${host.logs}")

        // VISIONOS takes no cookies, so a logged-in session skips it.
        val player = requests.playerRequests().single()
        assertEquals("1", player.headers["X-YouTube-Client-Name"])
        assertEquals<List<Triple<String, String, String?>>>(listOf(Triple("dQw4w9WgXcQ", "SESSION-VISITOR", cookie)), tokens.calls)
        assertEquals(cookie, player.headers[HttpHeaders.Cookie])
        assertEquals(sidAuthorization(cookie, host.now), player.headers[HttpHeaders.Authorization])
        assertEquals("https://www.youtube.com", player.headers["X-Origin"])
        assertEquals("TOKEN-VISITOR", player.headers["X-Goog-Visitor-Id"])
        val body = (player.body as TextContent).text
        listOf("\"poToken\":\"PLAYER-TOKEN\"", "\"visitorData\":\"TOKEN-VISITOR\"", "\"signatureTimestamp\":20746")
            .forEach { assertTrue(it in body, it) }

        assertEquals("WEB", stream.profileId)
        assertTrue(stream.url.endsWith("&pot=STREAM-TOKEN"))
        assertEquals(mapOf("User-Agent" to DESKTOP_UA, "Cookie" to cookie), stream.headers)
        val probe = requests.single { it.url.host.endsWith(".googlevideo.com") }
        assertEquals("STREAM-TOKEN", probe.url.parameters["pot"])
        assertEquals(cookie, probe.headers[HttpHeaders.Cookie])

        // BRIEF 4.6: none of it reaches the logs.
        val secrets = listOf("login1", "sid1", "sid2", "sid3", "PLAYER-TOKEN", "STREAM-TOKEN", "TOKEN-VISITOR", "SESSION-VISITOR", "videoplayback")
        for ((_, message) in host.logs) secrets.forEach { assertFalse(it in message, message) }
    }
}
