package com.bxane.excor.app

import com.bxane.excor.ExcorResultKind
import com.bxane.excor.ExcorSearchPage
import com.bxane.excor.ExcorSearchResult
import com.bxane.excor.ExcorSearchType

/** A video search result as the queue plays it; null for anything else (playlists, albums, artists, channels). */
fun ExcorSearchResult.toTrack(): Track? =
    if (kind != ExcorResultKind.VIDEO) null
    else Track(id, title, channelName, durationSeconds, thumbnails.maxByOrNull { it.width }?.url)

/** The line under a result's title: what it is when it is not a video, who made it, how long it is. */
fun bylineOf(result: ExcorSearchResult): String = listOfNotNull(
    when (result.kind) {
        ExcorResultKind.VIDEO -> null
        ExcorResultKind.PLAYLIST -> "Playlist"
        ExcorResultKind.ALBUM -> "Album"
        ExcorResultKind.ARTIST -> "Artist"
        ExcorResultKind.CHANNEL -> "Channel"
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
fun queueFrom(results: List<ExcorSearchResult>, index: Int): List<Track> =
    if (results.getOrNull(index)?.kind != ExcorResultKind.VIDEO) emptyList() else results.drop(index).mapNotNull { it.toTrack() }

/** Whether [text] is worth asking suggestions for: two characters or more, and not a link. */
fun suggestsFor(text: String): Boolean = text.trim().length >= 2 && "://" !in text && !text.trim().startsWith("www.")

/** The search types the app offers beside "All", with their labels. Movies are left out: they are mostly for rent. */
val SEARCH_TYPES = listOf(
    ExcorSearchType.VIDEOS to "Videos",
    ExcorSearchType.SHORTS to "Shorts",
    ExcorSearchType.CHANNELS to "Channels",
    ExcorSearchType.PLAYLISTS to "Playlists",
)

/**
 * What a page of a search of [type] lists: YouTube puts Shorts in grids beside the results, so a Shorts search lists
 * them first; any other search lists its results, as YouTube's page does.
 */
fun resultsOf(page: ExcorSearchPage, type: ExcorSearchType?): List<ExcorSearchResult> =
    if (type == ExcorSearchType.SHORTS) page.shorts + page.results else page.results

/** [loaded] with the next page's results after it, each result once (pages can repeat one). */
fun appendResults(loaded: List<ExcorSearchResult>, next: List<ExcorSearchResult>): List<ExcorSearchResult> =
    (loaded + next).distinctBy { it.kind to it.id }
