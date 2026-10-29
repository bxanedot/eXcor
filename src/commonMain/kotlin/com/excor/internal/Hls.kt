package com.excor.internal

/** One stream's HLS playlist: an init segment (fragmented MP4) if any, then the media segments in order. */
internal class MediaPlaylist(val init: String?, val segments: List<String>)

private val MAP_URI = Regex("""URI="([^"]+)"""")
private val KEY_METHOD = Regex("""METHOD=([A-Z0-9-]+)""")

/**
 * A finished stream's media playlist (RFC 8216), its URIs resolved against [base]. Whatever this cannot turn into one
 * whole file is refused by name: a master playlist (pick a stream first), a live one (no `#EXT-X-ENDLIST`), encrypted
 * segments (any `#EXT-X-KEY` but `METHOD=NONE`: DRM is not downloaded), and byte ranges.
 */
internal fun mediaPlaylistOf(text: String, base: String): MediaPlaylist {
    val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    check(lines.firstOrNull() == "#EXTM3U") { "Excor: not an HLS playlist" }
    check(lines.none { it.startsWith("#EXT-X-STREAM-INF") }) { "Excor: an HLS master playlist, not one stream" }
    check("#EXT-X-ENDLIST" in lines) { "Excor: a live HLS playlist cannot be downloaded whole" }
    check(lines.filter { it.startsWith("#EXT-X-KEY") }.all { KEY_METHOD.find(it)?.groupValues?.get(1) == "NONE" }) {
        "Excor: encrypted HLS (DRM) is not downloaded"
    }
    val maps = lines.filter { it.startsWith("#EXT-X-MAP") }
    check(lines.none { it.startsWith("#EXT-X-BYTERANGE") } && maps.none { "BYTERANGE" in it }) { "Excor: HLS byte ranges are not supported" }
    check(maps.size <= 1) { "Excor: more than one HLS init segment" }
    val segments = lines.filterNot { it.startsWith("#") }.map { resolveUri(base, it) }
    check(segments.isNotEmpty()) { "Excor: an HLS playlist without segments" }
    return MediaPlaylist(maps.firstOrNull()?.let { m -> resolveUri(base, MAP_URI.find(m)?.groupValues?.get(1) ?: error("Excor: an HLS init segment without a URI")) }, segments)
}

/** [uri] read against [base] (RFC 3986 5.2, the cases playlists use: absolute, scheme-relative, host-relative, relative). */
internal fun resolveUri(base: String, uri: String): String {
    val origin = base.substringBefore("://") + "://" + base.substringAfter("://").substringBefore('/').substringBefore('?')
    return when {
        "://" in uri.substringBefore('?') -> uri
        uri.startsWith("//") -> base.substringBefore("://") + ":" + uri
        uri.startsWith("/") -> origin + uri
        // A base without a path is the origin itself.
        else -> base.substringBefore('?').let { if ('/' in it.substringAfter("://")) it.substringBeforeLast('/') else origin } + "/" + uri
    }
}
