package com.excor

/**
 * The YouTube video ID in [input]: a bare 11-character ID, or a youtube.com, m.youtube.com, music.youtube.com,
 * youtube-nocookie.com or youtu.be link to a video (watch, shorts, live, embed and v/ paths). Null for anything else,
 * including links to other hosts that merely contain an ID-shaped string.
 */
fun excorVideoIdOf(input: String): String? {
    val text = input.trim()
    if (VIDEO_ID.matches(text)) return text
    val withScheme = if ("://" in text) text else "https://$text"
    val scheme = withScheme.substringBefore("://").lowercase()
    if (scheme != "https" && scheme != "http") return null
    val afterScheme = withScheme.substringAfter("://")
    val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    if ('@' in authority) return null
    val host = authority.substringBefore(':').lowercase().removePrefix("www.")
    val rest = afterScheme.removePrefix(authority)
    val path = rest.substringBefore('?').substringBefore('#')
    val query = rest.substringAfter('?', "").substringBefore('#')
    val segments = path.split('/').filter { it.isNotEmpty() }
    val candidate = when (host) {
        "youtu.be" -> segments.firstOrNull()
        "youtube.com", "m.youtube.com", "music.youtube.com", "youtube-nocookie.com" -> when {
            segments.firstOrNull() == "watch" -> query.split('&').firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
            segments.size >= 2 && segments[0] in setOf("shorts", "live", "embed", "v") -> segments[1]
            else -> null
        }
        else -> null
    }
    return candidate?.takeIf(VIDEO_ID::matches)
}
