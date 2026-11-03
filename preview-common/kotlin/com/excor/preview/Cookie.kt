package com.excor.preview

/**
 * Why a pasted Cookie header can't be used, or null when it can. A developer-tools view that shortens long values
 * leaves a "…" in the copy, which no header may carry.
 */
fun cookieProblem(cookie: String): String? = when {
    '…' in cookie -> "That copy was cut off (it contains …). Copy the whole value: in the developer tools' Network tab, " +
        "right-click a youtube.com request, choose Copy request headers (or Copy as cURL), and paste the text after \"cookie:\"."
    cookie.any { it < ' ' } -> "Paste the header value on one line."
    cookie.any { it > '~' } -> "That has characters a Cookie header can't carry; copy it again from the request headers."
    "SAPISID=" !in cookie && "__Secure-3PAPISID=" !in cookie -> "That has no SAPISID cookie, so YouTube would not treat it as signed in."
    else -> null
}

/** The value of a pasted "Cookie: …" line or a bare value. */
fun cookieValue(pasted: String): String = pasted.trim().removePrefix("Cookie:").removePrefix("cookie:").trim()
