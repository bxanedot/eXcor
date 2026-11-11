package com.excor

private val playbackReasonUrl = Regex("""(?i)(?:https?://|www\.)[^\s"'<>]+""")
private val playbackReasonCredentials = Regex(
    """(?i)\b(?:authorization|cookie|sapisid|po[_-]?token|visitor[_-]?(?:data|id)|signature|sig|token|api[_-]?key)\s*[:=]\s*(?:bearer\s+)?[^\s,;]+""",
)
private val playbackReasonBearer = Regex("""(?i)\bbearer\s+[^\s,;]+""")

/** Bounds and removes common secrets from untrusted diagnostic text before logging or surfacing it in errors. */
internal fun safeDiagnosticText(raw: String): String = raw
    .replace(playbackReasonUrl, "[link removed]")
    .replace(playbackReasonCredentials, "[credential removed]")
    .replace(playbackReasonBearer, "[credential removed]")
    .filter { it >= ' ' && it != '\u007f' }
    .trim()
    .take(200)

/** Applies the diagnostic sanitizer to untrusted player status and reason text. */
internal fun safePlaybackReason(raw: String): String = safeDiagnosticText(raw)
