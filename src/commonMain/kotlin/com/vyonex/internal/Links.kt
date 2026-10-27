package com.vyonex.internal

import com.vyonex.VyonExRef
import io.ktor.http.parseQueryString

// YouTube's own hosts from yt-dlp's YoutubeIE._VALID_URL, without the mirror sites it also accepts.
private val HOSTS = setOf(
    "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com",
    "youtube-nocookie.com", "www.youtube-nocookie.com", "youtu.be",
)
private val VIDEO_ID = Regex("[0-9A-Za-z_-]{11}")
// yt-dlp _base.py _PLAYLIST_ID_RE.
private val PLAYLIST_ID = Regex("(?:PL|LL|EC|UU|FL|RD|UL|TL|PU|OLAK5uy_)[0-9A-Za-z_-]{10,}|RDMM|WL|LL|LM")
private val ALBUM_ID = Regex("MPREb_[0-9A-Za-z_-]+")
// Paths followed by a video ID; "videoseries" and "live_stream" fit the ID pattern but are not videos.
private val VIDEO_PATHS = setOf("v", "embed", "e", "shorts", "live")
private val NOT_VIDEO_IDS = setOf("videoseries", "live_stream")
private val SCHEME = Regex("^(?:https?:)?//", RegexOption.IGNORE_CASE)

/** What a YouTube or YouTube Music link, or a bare ID, points at; null for anything else. Makes no request. */
internal fun parseRef(input: String): VyonExRef? {
    val text = input.trim()
    if (VIDEO_ID.matches(text)) return VyonExRef.Video(text)
    if (ALBUM_ID.matches(text)) return VyonExRef.Album(text)
    if (PLAYLIST_ID.matches(text)) return listRef(text)

    val link = SCHEME.replace(text, "")
    // Anything before the first '/', '?' or '#' is the host; userinfo or a port makes it unknown.
    val hostEnd = link.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) link.length else it }
    val host = link.substring(0, hostEnd).lowercase()
    if (host !in HOSTS) return null
    val rest = link.substring(hostEnd)
    val segments = rest.substringBefore('#').substringBefore('?').split('/').filter { it.isNotEmpty() }
    val query = parseQueryString(rest.substringBefore('#').substringAfter('?', ""))
    val fragment = parseQueryString(rest.substringAfter('#', ""))
    val list = query["list"]?.takeIf { PLAYLIST_ID.matches(it) }

    val videoId = when {
        host == "youtu.be" -> segments.firstOrNull()
        segments.size >= 2 && segments[0] in VIDEO_PATHS -> segments[1].takeIf { it !in NOT_VIDEO_IDS }
        segments.isEmpty() || segments == listOf("watch") -> query["v"]
        else -> null
    }?.takeIf { VIDEO_ID.matches(it) }
    if (videoId != null) {
        val start = (query["t"] ?: query["start"] ?: fragment["t"])?.let(::startSeconds)
        return VyonExRef.Video(videoId, list, start)
    }
    if (segments.size == 2 && segments[0] == "browse") {
        val id = segments[1]
        // yt-dlp _tab.py: a "VL" browse ID is the playlist after the prefix.
        if (id.startsWith("VL") && PLAYLIST_ID.matches(id.substring(2))) return listRef(id.substring(2))
        if (ALBUM_ID.matches(id)) return VyonExRef.Album(id)
        return null
    }
    // A list without a usable video, as yt-dlp treats watch?list=, playlist?list= and embed/videoseries?list=.
    return list?.let(::listRef)
}

private fun listRef(id: String) = if (id.startsWith("RD")) VyonExRef.Mix(id) else VyonExRef.Playlist(id)

/** `t`/`start` as yt-dlp's parse_duration reads them here: seconds, [h:]m:s, or 1h2m3s. */
private fun startSeconds(value: String): Int? {
    value.toIntOrNull()?.let { return it }
    Regex("""(?:(\d+):)?(\d+):(\d{1,2})""").matchEntire(value)?.let { m ->
        val (h, min, s) = m.destructured
        return (h.toIntOrNull() ?: 0) * 3600 + min.toInt() * 60 + s.toInt()
    }
    Regex("""(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?""").matchEntire(value)?.takeIf { value.isNotEmpty() }?.let { m ->
        val (h, min, s) = m.destructured
        return (h.toIntOrNull() ?: 0) * 3600 + (min.toIntOrNull() ?: 0) * 60 + (s.toIntOrNull() ?: 0)
    }
    return null
}
