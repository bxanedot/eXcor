package com.excor

/**
 * Headers for media URLs. A playback response can point to or redirect to googlevideo.com, so account cookies and
 * visitor IDs stay on YouTube page/API requests and are not forwarded with the media request.
 */
internal fun mediaHeaders(userAgent: String, session: ExcorSession): Map<String, String> = buildMap {
    put("User-Agent", userAgent)
    put("Accept", "*/*")
    put("Accept-Language", acceptLanguage(session))
    put("Referer", "https://www.youtube.com/")
    put("Origin", "https://www.youtube.com")
}

internal fun acceptLanguage(session: ExcorSession): String {
    val language = session.language.ifBlank { "en" }
    val locale = if (session.region.isBlank()) language else "$language-${session.region}"
    return "$locale,$language;q=0.9"
}
