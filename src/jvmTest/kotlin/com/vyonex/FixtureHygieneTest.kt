package com.vyonex

import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Captures must not carry the requester's address, cookies or visitor IDs (fixtures/README.md). Never prints them. */
class FixtureHygieneTest {
    private val captures = File(FIXTURES).listFiles()!!.filter { it.isFile && it.name != "README.md" }

    @Test
    fun noPublicAddress() {
        // ip= in raw, JSON-escaped and HTML-escaped URLs and percent-encoded inside signatureCipher, plus remoteHost.
        val ipParam = Regex("""(?:[?&]|\\u0026|&amp;|%26)ip(?:=|%3D)([0-9A-Fa-f:.%]+)""")
        val remoteHost = Regex(""""remoteHost"\s*:\s*"([^"]*)"""")
        for (file in captures) {
            val text = file.readText()
            val values = ipParam.findAll(text).map { it.groupValues[1] } + remoteHost.findAll(text).map { it.groupValues[1] }
            for (value in values) {
                val address = URLDecoder.decode(URLDecoder.decode(value, Charsets.UTF_8), Charsets.UTF_8).substringBefore('&')
                assertFalse(isPublic(address), "${file.name}: a public address is left in (not printed)")
            }
        }
    }

    @Test
    fun noCookiesOrVisitorIds() {
        // The player script names these cookies in its code; only an assignment means a captured value.
        val cookie = Regex("""\b(?:LOGIN_INFO|S?APISID|HSID|SSID|__Secure-[\w-]+|VISITOR_INFO1_LIVE)=[^;\s"'&]""")
        val visitor = Regex(""""(?:visitorData|VISITOR_DATA)"\s*:\s*"(?!REDACTED-VISITOR-DATA")""")
        for (file in captures) {
            val text = file.readText()
            assertFalse(cookie.containsMatchIn(text), "${file.name}: a cookie value is left in (not printed)")
            assertFalse(visitor.containsMatchIn(text), "${file.name}: a visitor ID is left in (not printed)")
        }
    }

    @Test
    fun publicMeansRoutableAndNotForDocumentation() {
        assertTrue(isPublic("8.8.8.8"))
        assertTrue(isPublic("2a00:1450::1"))
        listOf("0.0.0.0", "10.1.2.3", "192.0.2.1", "203.0.113.9", "::", "fd12:3456::1", "fe80::1", "2001:db8::1", "::ffff:0.0.0.0")
            .forEach { assertFalse(isPublic(it), it) }
    }

    private fun isPublic(literal: String): Boolean {
        val address = InetAddress.ofLiteral(literal)
        val text = address.hostAddress
        val documentation = listOf("192.0.2.", "198.51.100.", "203.0.113.", "2001:db8:").any { text.startsWith(it) }
        val uniqueLocal = address !is Inet4Address && (address.address[0].toInt() and 0xfe) == 0xfc
        return !(address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress || documentation || uniqueLocal)
    }
}
