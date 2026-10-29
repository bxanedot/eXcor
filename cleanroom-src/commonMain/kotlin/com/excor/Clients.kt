package com.excor

internal const val CLIENT_ATTEMPT_LIMIT = 3

internal data class ClientIdentity(
    val name: String,
    val profileId: String,
    val clientId: Int,
    val clientVersion: String,
    val userAgent: String,
    val requiresPlayerScript: Boolean,
    val requiresStreamingToken: Boolean,
)

private val webEmbedded = ClientIdentity(
    name = "WEB_EMBEDDED_PLAYER",
    profileId = "web_embedded",
    clientId = 56,
    clientVersion = "2.20260708.00.00",
    userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.5 Safari/605.1.15,gzip(gfe)",
    requiresPlayerScript = true,
    requiresStreamingToken = false,
)

private val tvSamsung = ClientIdentity(
    name = "TVHTML5",
    profileId = "tv_samsung",
    clientId = 7,
    clientVersion = "5.20260707",
    userAgent = "Mozilla/5.0 (SMART-TV; Linux; Tizen 2.4.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/2.4.0 TV Safari/538.1",
    requiresPlayerScript = true,
    requiresStreamingToken = false,
)

private val visionOs = ClientIdentity(
    name = "VISIONOS",
    profileId = "visionos",
    clientId = 101,
    clientVersion = "1.02",
    userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
    requiresPlayerScript = false,
    requiresStreamingToken = false,
)

private val web = ClientIdentity(
    name = "WEB",
    profileId = "web",
    clientId = 1,
    clientVersion = "2.20260708.00.00",
    userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36",
    requiresPlayerScript = true,
    requiresStreamingToken = true,
)

internal fun selectClients(
    session: ExcorSession,
    skipClients: Set<String>,
    excludedProfiles: Set<String>,
): List<ClientIdentity> {
    val ordered = if (session.cookie.isNullOrBlank()) {
        listOf(tvSamsung, visionOs, web)
    } else {
        listOf(webEmbedded, tvSamsung, web)
    }
    val skipped = (skipClients + excludedProfiles).mapTo(mutableSetOf()) { it.lowercase() }
    return ordered.filterNot { it.name.lowercase() in skipped || it.profileId.lowercase() in skipped }
        .take(CLIENT_ATTEMPT_LIMIT)
}
