package com.excor

private val playbackReasonUrl = Regex("""(?i)(?:https?://|www\.)[^\s"'<>]+""")
private val playbackReasonCredentials = Regex(
    """(?i)\b(?:authorization|cookie|sapisid|po[_-]?token|visitor[_-]?data|signature|sig|token|api[_-]?key)\s*[:=]\s*(?:bearer\s+)?[^\s,;]+""",
)
private val playbackReasonBearer = Regex("""(?i)\bbearer\s+[^\s,;]+""")

/** Bounds and removes common secrets from untrusted player status text before logging or surfacing it in errors. */
internal fun safePlaybackReason(raw: String): String = raw
    .replace(playbackReasonUrl, "[link removed]")
    .replace(playbackReasonCredentials, "[credential removed]")
    .replace(playbackReasonBearer, "[credential removed]")
    .filter { it >= ' ' && it != '\u007f' }
    .trim()
    .take(200)
