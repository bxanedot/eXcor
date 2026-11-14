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

/** A YouTube HLS master playlist URL. Its child playlists and segments remain the host player's responsibility. */
internal fun isAllowedHlsManifestUrl(value: String): Boolean {
    if (value.length !in 1..16_384 || value.any { it.code !in 0x21..0x7e } || '#' in value) return false
    val schemeEnd = value.indexOf("://")
    if (schemeEnd < 0) return false
    val authorityStart = schemeEnd + 3
    val authorityEnd = value.indexOfAny(charArrayOf('/', '?', '#'), authorityStart)
        .takeIf { it >= 0 } ?: value.length
    if ('@' in value.substring(authorityStart, authorityEnd)) return false

    val url = runCatching { Url(value) }.getOrNull() ?: return false
    if (url.protocol.name != "https" || url.port != 443) return false
    val host = url.host.lowercase()
    if (host != "googlevideo.com" && !host.endsWith(".googlevideo.com")) return false

    val path = url.encodedPath
    if (path.length > 4_096 || !path.startsWith("/api/manifest/hls_") ||
        !(path.endsWith("/file/index.m3u8") || path.endsWith("/playlist/index.m3u8"))
    ) return false
    val segment = Regex("^[A-Za-z0-9._~+=-]+$")
    val segments = path.split('/').drop(1)
    return segments.isNotEmpty() && segments.all { it.isNotEmpty() && it != "." && it != ".." && segment.matches(it) }
}
