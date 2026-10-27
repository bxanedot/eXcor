package com.vyonex

import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Captures must not carry the requester's address, cookies or visitor IDs (fixtures/README.md). Never prints them. */
class FixtureHygieneTest {
    private val captures = File(FIXTURES).listFiles()!!.filter { it.isFile && it.name != "README.md" }

    @Test
    fun noPublicAddress() = captures.forEach { assertFalse(hasPublicAddress(it.readText()), "${it.name}: a public address is left in (not printed)") }

    @Test
    fun noCookiesOrVisitorIds() = captures.forEach {
        val text = it.readText()
        assertFalse(hasCookieValue(text), "${it.name}: a cookie value is left in (not printed)")
        assertFalse(hasVisitorId(text), "${it.name}: a visitor ID is left in (not printed)")
        assertFalse(hasHeartbeatData(text), "${it.name}: heartbeat data is left in (not printed)")
    }

    @Test
    fun redactionRemovesPublicAddressesAndVisitorIds() {
        val visitor = "Cgt0ZXN0LXZpc2l0b3I%3D%3D"
        val capture = """{"responseContext":{"visitorData":"$visitor"},"remoteHost":"8.8.4.4","""" +
            """"a":"https://r1.googlevideo.com/videoplayback?ip=8.8.8.8&id=1","b":"x\u0026ip=2a00:1450::1\u0026c=WEB","""" +
            """"c":"url=https%3A%2F%2Fr1.googlevideo.com%2Fvideoplayback%3Fid%3D1%26ip%3D2a00%3A1450%3A%3A1%26ipbits%3D0","""" +
            """"d":"ip=0.0.0.0&x","e":"${URLEncoder.encode(visitor, Charsets.UTF_8)}","heartbeatParams":{"heartbeatServerData":"GAEgALIB"}}"""
        assertTrue(hasPublicAddress(capture) && hasVisitorId(capture) && hasHeartbeatData(capture))
        val redacted = redactCapture(capture)
        assertFalse(hasPublicAddress(redacted) || hasVisitorId(redacted) || hasHeartbeatData(redacted) || "Cgt0ZXN0" in redacted || "GAEgALIB" in redacted, redacted)
        listOf("?ip=192.0.2.1&id=1", "\\u0026ip=192.0.2.1\\u0026", "%26ip%3D192.0.2.1%26ipbits", "ip=0.0.0.0&x", "\"remoteHost\":\"192.0.2.1\"")
            .forEach { assertTrue(it in redacted, it) }
    }

    @Test
    fun publicMeansRoutableAndNotForDocumentation() {
        assertTrue(isPublic("8.8.8.8"))
        assertTrue(isPublic("2a00:1450::1"))
        listOf("0.0.0.0", "10.1.2.3", "192.0.2.1", "203.0.113.9", "::", "fd12:3456::1", "fe80::1", "2001:db8::1", "::ffff:0.0.0.0")
            .forEach { assertFalse(isPublic(it), it) }
    }
}

// ip= in raw, JSON-escaped and HTML-escaped URLs and percent-encoded inside signatureCipher, plus remoteHost.
internal val IP_PARAM = Regex("""(?:[?&]|\\u0026|&amp;|%26)ip(?:=|%3D)([0-9A-Fa-f:.%]+)""")
internal val REMOTE_HOST = Regex(""""remoteHost"\s*:\s*"([^"]*)"""")
// The player script names these cookies in its code; only an assignment means a captured value.
private val COOKIE_VALUE = Regex("""\b(?:LOGIN_INFO|S?APISID|HSID|SSID|__Secure-[\w-]+|VISITOR_INFO1_LIVE)=[^;\s"'&]""")
internal val VISITOR_ID = Regex(""""(?:visitorData|VISITOR_DATA)"\s*:\s*"([^"]+)"""")
internal const val REDACTED_VISITOR = "REDACTED-VISITOR-DATA"
// An encrypted per-request token for watch-time heartbeats (upcoming streams carry one). It may encode the
// requester, and nothing here reads it.
private val HEARTBEAT = Regex(""""heartbeatServerData"\s*:\s*"([^"]+)"""")
private const val REDACTED_HEARTBEAT = "REDACTED-HEARTBEAT-DATA"

internal fun addressOf(value: String) =
    URLDecoder.decode(URLDecoder.decode(value, Charsets.UTF_8), Charsets.UTF_8).substringBefore('&')

internal fun hasPublicAddress(text: String) =
    (IP_PARAM.findAll(text) + REMOTE_HOST.findAll(text)).any { isPublic(addressOf(it.groupValues[1])) }

internal fun hasCookieValue(text: String) = COOKIE_VALUE.containsMatchIn(text)

internal fun hasVisitorId(text: String) = VISITOR_ID.findAll(text).any { it.groupValues[1] != REDACTED_VISITOR }

internal fun hasHeartbeatData(text: String) = HEARTBEAT.findAll(text).any { it.groupValues[1] != REDACTED_HEARTBEAT }

internal fun isPublic(literal: String): Boolean {
    val address = InetAddress.ofLiteral(literal)
    val text = address.hostAddress
    val documentation = listOf("192.0.2.", "198.51.100.", "203.0.113.", "2001:db8:").any { text.startsWith(it) }
    val uniqueLocal = address !is Inet4Address && (address.address[0].toInt() and 0xfe) == 0xfc
    return !(address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
        address.isSiteLocalAddress || address.isMulticastAddress || documentation || uniqueLocal)
}

/** fixtures/README.md: public addresses become 192.0.2.1, every copy of a visitor ID and heartbeat data a placeholder. */
internal fun redactCapture(text: String): String {
    var out = HEARTBEAT.replace(text) { it.value.replace(it.groupValues[1], REDACTED_HEARTBEAT) }
    VISITOR_ID.findAll(out).map { it.groupValues[1] }.filter { it != REDACTED_VISITOR }.toSet().forEach { id ->
        listOf(id, URLEncoder.encode(id, Charsets.UTF_8)).forEach { out = out.replace(it, REDACTED_VISITOR) }
    }
    for (pattern in listOf(IP_PARAM, REMOTE_HOST)) {
        out = pattern.replace(out) { m ->
            val value = m.groupValues[1]
            // A percent-encoded value can run into the next parameter's "%26", which stays.
            val tail = if ("%26" in value) "%26" + value.substringAfter("%26") else ""
            if (isPublic(addressOf(value))) m.value.replace(value, "192.0.2.1$tail") else m.value
        }
    }
    return out
}
