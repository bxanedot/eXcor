package com.excor

private val HLS_N_SEGMENT = Regex("/n/([^/?#]+)")
private val HLS_POT_SUFFIX = Regex("/pot/([A-Za-z0-9._~+=-]{1,4096})/(?:file|playlist)/index\\.m3u8$")
private val HLS_MANIFEST_SUFFIX = Regex("/(?:file|playlist)/index\\.m3u8$")
private val HLS_URL_SEGMENT = Regex("^[A-Za-z0-9._~+=-]{1,4096}$")

internal fun hlsManifestNChallenges(url: String): List<String> {
    if (!isAllowedHlsManifestUrl(url)) return emptyList()
    val path = manifestPath(url) ?: return emptyList()
    return HLS_N_SEGMENT.findAll(path).map { it.groupValues[1] }.distinct().toList()
}

/** Applies only player-solved path challenges and the host-provided PO token; it never creates either value. */
internal fun rewriteHlsManifestUrl(
    original: String,
    solvedN: Map<String, String>,
    streamingToken: String?,
): String? {
    if (!isAllowedHlsManifestUrl(original)) return null
    var result = original
    val pathRange = manifestPathRange(result) ?: return null
    val path = result.substring(pathRange.first, pathRange.last + 1)
    val challenges = HLS_N_SEGMENT.findAll(path).toList()
    val distinctChallenges = challenges.map { it.groupValues[1] }.distinct()
    if (challenges.size > 1) return null
    distinctChallenges.singleOrNull()?.let { challenge ->
        val value = solvedN[challenge]?.takeIf(HLS_URL_SEGMENT::matches) ?: return null
        val match = challenges.single()
        val start = pathRange.first + match.range.first + match.value.length - challenge.length
        val end = start + challenge.length
        result = result.replaceRange(start, end, value)
    }

    if (streamingToken != null) {
        if (!HLS_URL_SEGMENT.matches(streamingToken)) return null
        val updatedRange = manifestPathRange(result) ?: return null
        val updatedPath = result.substring(updatedRange.first, updatedRange.last + 1)
        val existingToken = HLS_POT_SUFFIX.find(updatedPath)?.groupValues?.get(1)
        if (existingToken != null) {
            if (existingToken != streamingToken) return null
        } else {
            val suffix = HLS_MANIFEST_SUFFIX.find(updatedPath) ?: return null
            val insertAt = updatedRange.first + suffix.range.first
            result = result.substring(0, insertAt) + "/pot/$streamingToken" + result.substring(insertAt)
        }
    }
    return result.takeIf(::isAllowedHlsManifestUrl)
}

private fun manifestPath(url: String): String? {
    val range = manifestPathRange(url) ?: return null
    return url.substring(range.first, range.last + 1)
}

private fun manifestPathRange(url: String): IntRange? {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd < 0) return null
    val start = url.indexOf('/', schemeEnd + 3)
    if (start < 0) return null
    val end = url.indexOfAny(charArrayOf('?', '#'), start).takeIf { it >= 0 } ?: url.length
    return if (end > start) start until end else null
}
