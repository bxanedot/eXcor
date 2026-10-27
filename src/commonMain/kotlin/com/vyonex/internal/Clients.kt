package com.vyonex.internal

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.update

/**
 * The InnerTube clients VyonEx speaks as. These values change when YouTube changes its clients, so they live here, as
 * data, apart from the engine. All of them, and the orders below, follow yt-dlp 2026.10.21's INNERTUBE_CLIENTS and
 * default client lists (`_base.py`, `_video.py`).
 */
internal class Identity(
    val profileId: String,
    val clientName: String,
    val clientId: Int,
    val clientVersion: String,
    val userAgent: String,
    val device: Map<String, String> = emptyMap(),
    val needsPlayerJs: Boolean,
    val supportsCookies: Boolean,
    /** YouTube refuses this client's media without a PO token, so it is asked only when the host provides one. */
    val needsPoToken: Boolean,
    /** Embedded players say which page embeds them; yt-dlp: "any valid non-YouTube URL". */
    val embedUrl: String? = null,
)

private const val SAFARI_UA =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.5 Safari/605.1.15,gzip(gfe)"

internal val IDENTITIES = listOf(
    Identity(
        "TV_SAMSUNG", "TVHTML5", 7, "5.20260707",
        "Mozilla/5.0 (SMART-TV; Linux; Tizen 2.4.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/2.4.0 TV Safari/538.1",
        mapOf("deviceMake" to "Samsung", "deviceModel" to "UKS9800", "osName" to "Tizen", "osVersion" to "2.4.0"),
        needsPlayerJs = true, supportsCookies = true, needsPoToken = false,
    ),
    Identity(
        "VISIONOS", "VISIONOS", 101, "1.02",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
        mapOf("deviceMake" to "Apple", "deviceModel" to "RealityDevice17,1", "osName" to "visionOS", "osVersion" to "26.5.23O471"),
        needsPlayerJs = false, supportsCookies = false, needsPoToken = false,
    ),
    Identity("WEB", "WEB", 1, "2.20260708.00.00", DESKTOP_UA, needsPlayerJs = true, supportsCookies = true, needsPoToken = true),
    Identity(
        "WEB_EMBEDDED", "WEB_EMBEDDED_PLAYER", 56, "2.20260708.00.00", SAFARI_UA,
        needsPlayerJs = true, supportsCookies = true, needsPoToken = false, embedUrl = "https://www.reddit.com/",
    ),
    Identity(
        "TV", "TVHTML5", 7, "7.20260707.07.00",
        "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)",
        needsPlayerJs = true, supportsCookies = true, needsPoToken = false,
    ),
    Identity(
        "TV_DOWNGRADED", "TVHTML5", 7, "5.20260707", "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version",
        needsPlayerJs = true, supportsCookies = true, needsPoToken = false,
    ),
)

/**
 * yt-dlp's defaults (tv_samsung, visionos, web; logged in: web_embedded, tv_samsung, web), then the TV clients it
 * also defines, which need no PO token, as further fallbacks. VISIONOS takes no cookies, so a logged-in session never
 * uses it. Live on 2026-10-26: TVHTML5 gave ciphered URLs that play once decoded, VISIONOS gave direct URLs, WEB gave
 * SABR-only formats without a PO token.
 */
internal val LOGGED_OUT_ORDER = listOf("TV_SAMSUNG", "VISIONOS", "WEB", "TV", "TV_DOWNGRADED").map(::identity)
internal val LOGGED_IN_ORDER = listOf("WEB_EMBEDDED", "TV_SAMSUNG", "WEB", "TV", "TV_DOWNGRADED").map(::identity)

internal fun identity(profileId: String) = IDENTITIES.first { it.profileId == profileId }

internal const val DEMOTE_AFTER_FAILURES = 3
internal const val DEMOTE_MS = 30 * 60 * 1000L

/**
 * How each client has fared lately, across videos. One that failed [DEMOTE_AFTER_FAILURES] times in a row (refused
 * media, a failed request) is tried after the others for [DEMOTE_MS]: on a network where YouTube refuses a client,
 * every video would otherwise spend a request on it first. A success, or the time running out, clears it.
 * Refusals of one video (age, region) say nothing about the client and are not counted.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class ClientHealth(private val nowMs: () -> Long) {
    private class Record(val failures: Int, val demotedUntil: Long)

    private val records = AtomicReference(emptyMap<String, Record>())

    fun failed(profileId: String) = records.update { all ->
        val now = nowMs()
        val failures = (all[profileId]?.takeIf { it.demotedUntil == 0L || it.demotedUntil > now }?.failures ?: 0) + 1
        all + (profileId to Record(failures, if (failures >= DEMOTE_AFTER_FAILURES) now + DEMOTE_MS else 0))
    }

    fun succeeded(profileId: String) = records.update { it - profileId }

    fun isDemoted(profileId: String) = (records.load()[profileId]?.demotedUntil ?: 0) > nowMs()

    /** [order] with the demoted clients moved to the end, each group in its own order. */
    fun ordered(order: List<Identity>): List<Identity> = order.partition { !isDemoted(it.profileId) }.let { (fine, demoted) -> fine + demoted }

    fun clear() = records.store(emptyMap())
}
