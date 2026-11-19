package com.excor

private val playbackReasonUrl = Regex("""(?i)(?:https?://|www\.)[^\s"'<>]+""")
private val playbackReasonHeaderCredentials = Regex("""(?i)\b(?:authorization|set[_-]?cookie|cookie)\s*[:=][^\r\n]*""")
private val playbackReasonCredentials = Regex(
    """(?i)\b(?:authorization[_-]?code|sapisid|po[_-]?token|visitor[_-]?(?:data|id)|access[_-]?token|refresh[_-]?token|id[_-]?token|oauth[_-]?token(?:[_-]?secret)?|client[_-]?secret|code[_-]?verifier|signature|sig|token|api[_-]?key)\s*[:=]\s*(?:bearer\s+)?[^\s,;]+""",
)
private val playbackReasonBearer = Regex("""(?i)\bbearer\s+[^\s,;]+""")

/** Bounds and removes common secrets from untrusted diagnostic text before logging or surfacing it in errors. */
internal fun safeDiagnosticText(raw: String): String = raw
    .replace(playbackReasonUrl, "[link removed]")
    // Header values can contain multiple space- or semicolon-separated credentials.
    .replace(playbackReasonHeaderCredentials, "[credential removed]")
    .replace(playbackReasonCredentials, "[credential removed]")
    .replace(playbackReasonBearer, "[credential removed]")
    .filter { it >= ' ' && it != '\u007f' }
    .trim()
    .take(200)

/** Applies the diagnostic sanitizer to untrusted player status and reason text. */
internal fun safePlaybackReason(raw: String): String = safeDiagnosticText(raw)
