package com.excor

import io.ktor.http.Url

internal fun isAllowedPlaybackUrl(value: String): Boolean {
    val schemeEnd = value.indexOf("://")
    if (schemeEnd < 0) return false
    val authorityStart = schemeEnd + 3
    val authorityEnd = value.indexOfAny(charArrayOf('/', '?', '#'), authorityStart)
        .takeIf { it >= 0 } ?: value.length
    if ('@' in value.substring(authorityStart, authorityEnd)) return false

    val url = runCatching { Url(value) }.getOrNull() ?: return false
    if (url.protocol.name != "https" || url.port != 443 || url.encodedPath != "/videoplayback") return false

    val host = url.host.lowercase()
    return host == "googlevideo.com" || host.endsWith(".googlevideo.com") ||
        host == "youtube.com" || host.endsWith(".youtube.com")
}
