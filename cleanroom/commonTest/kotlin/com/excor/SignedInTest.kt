package com.excor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignedInTest {
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    @Test
    fun sha1MatchesFipsVectors() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", hex(sha1("abc".encodeToByteArray())))
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", hex(sha1(ByteArray(0))))
        assertEquals("84983e441c3bd26ebaae4aa1f95129e5e54670f1", hex(sha1("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray())))
    }

    @Test
    fun sapisidHashIsSignedForTheOrigin() {
        val headers = signedInHeaders("PREF=f1=1; SAPISID=abc/def; SID=x", "https://www.youtube.com", nowSeconds = 1_700_000_000)
        assertEquals("SAPISIDHASH 1700000000_" + hex(sha1("1700000000 abc/def https://www.youtube.com".encodeToByteArray())), headers["Authorization"])
        assertEquals("https://www.youtube.com", headers["X-Origin"])
        assertEquals("0", headers["X-Goog-AuthUser"])
        assertEquals(headers["Authorization"], signedInHeaders("__Secure-3PAPISID=abc/def", "https://www.youtube.com", 1_700_000_000)["Authorization"])
        assertTrue(signedInHeaders("PREF=f1=1", "https://www.youtube.com").isEmpty())
        assertTrue(signedInHeaders(null, "https://www.youtube.com").isEmpty())
    }

    @Test
    fun innertubeRequestsCarryTheSignedInHeaders() {
        val session = ExcorSession("SAPISID=abc", null, "US", "en")
        assertTrue(innertubeRequest("browse", session) {}.headers["Authorization"]!!.startsWith("SAPISIDHASH "))
        assertTrue(innertubeRequest("browse", ExcorSession(null, null, "US", "en")) {}.headers["Authorization"] == null)
    }
}
