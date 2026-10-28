package com.vyonex.app

import com.vyonex.VyonExResultKind
import com.vyonex.VyonExSearchResult

/** A video search result as the queue plays it; null for anything else (playlists, albums, artists, channels). */
fun VyonExSearchResult.toTrack(): Track? =
    if (kind != VyonExResultKind.VIDEO) null
    else Track(id, title, channelName, durationSeconds, thumbnails.maxByOrNull { it.width }?.url)

/** The line under a result's title: what it is when it is not a video, who made it, how long it is. */
fun bylineOf(result: VyonExSearchResult): String = listOfNotNull(
    when (result.kind) {
        VyonExResultKind.VIDEO -> null
        VyonExResultKind.PLAYLIST -> "Playlist"
        VyonExResultKind.ALBUM -> "Album"
        VyonExResultKind.ARTIST -> "Artist"
        VyonExResultKind.CHANNEL -> "Channel"
    },
    result.channelName,
    result.durationSeconds?.let(::clockOf),
).joinToString(" · ")

/** "3:32", "1:02:05". */
fun clockOf(seconds: Long): String {
    fun two(n: Long) = n.toString().padStart(2, '0')
    return if (seconds >= 3600) "${seconds / 3600}:${two(seconds / 60 % 60)}:${two(seconds % 60)}" else "${seconds / 60}:${two(seconds % 60)}"
}

/**
 * The tracks a tap on [index] plays: the video results from that one on, so the next one plays after it. Empty when
 * [index] is not a video.
 */
fun queueFrom(results: List<VyonExSearchResult>, index: Int): List<Track> =
    if (results.getOrNull(index)?.kind != VyonExResultKind.VIDEO) emptyList() else results.drop(index).mapNotNull { it.toTrack() }

/** Whether [text] is worth asking suggestions for: two characters or more, and not a link. */
fun suggestsFor(text: String): Boolean = text.trim().length >= 2 && "://" !in text && !text.trim().startsWith("www.")
