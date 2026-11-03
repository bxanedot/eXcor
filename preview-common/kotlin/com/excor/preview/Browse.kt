package com.excor.preview

import com.excor.Excor
import com.excor.ExcorYouTubeMusicItem
import com.excor.ExcorYouTubeMusicItemType
import com.excor.ExcorYouTubeSearchFilter
import com.excor.ExcorYouTubeVideoResult

// Shared by the Android and the desktop preview (compiled into each; no Android or Swing types here): what a search,
// a YouTube Music playlist or album, and an artist page look like as a list of rows, and what tapping a row opens.

/** Where a search goes: YouTube Music (typed items, its own filters) or YouTube (videos). */
enum class Source(val label: String) { MUSIC("YouTube Music"), YOUTUBE("YouTube") }

/** What tapping a row does. */
sealed interface Target {
    data class Video(val videoId: String) : Target
    data class Playlist(val playlistId: String) : Target
    data class Artist(val channelId: String) : Target
    data class Unsupported(val reason: String) : Target
}

/** One row: its title, a detail line, a type badge (Song, Album, …) and what it opens. */
data class Row(val title: String, val detail: String, val badge: String, val target: Target)

/** A titled group of rows, such as an artist page's Albums shelf; [title] is null for a plain list. */
data class Section(val title: String?, val rows: List<Row>, val more: Target? = null)

/** How to load the next page of a listing. */
sealed interface Next {
    data class MusicSearch(val token: String) : Next
    data class VideoSearch(val query: String, val token: String) : Next
    data class Playlist(val playlistId: String, val token: String) : Next
}

/**
 * One screen of results. [filters] are YouTube Music's own filter chips (Songs, Videos, Albums, Artists, …, as it
 * offers them for this query); [query] is kept so a chip can run the search again.
 */
data class Listing(
    val title: String,
    val subtitle: String? = null,
    val sections: List<Section>,
    val query: String? = null,
    val filters: List<ExcorYouTubeSearchFilter> = emptyList(),
    val next: Next? = null,
) {
    val rowCount: Int get() = sections.sumOf { it.rows.size }
}

/** Loads listings through [excor]. Every call is one or two InnerTube requests; null means YouTube had no such page. */
class Browser(private val excor: Excor) {
    suspend fun search(source: Source, query: String, filterParams: String? = null): Listing = when (source) {
        Source.MUSIC -> {
            val page = excor.musicSearch(query, filterParams = filterParams) ?: error("YouTube Music gave no search page")
            Listing(
                title = "“$query” on YouTube Music",
                sections = listOf(Section(null, page.items.map(::musicRow))),
                query = query,
                filters = page.filters,
                next = page.continuation?.let(Next::MusicSearch),
            )
        }
        Source.YOUTUBE -> {
            val page = excor.search(query) ?: error("YouTube gave no search page")
            Listing(
                title = "“$query” on YouTube",
                sections = listOf(Section(null, page.videos.map(::videoRow))),
                query = query,
                next = page.continuation?.let { Next.VideoSearch(query, it) },
            )
        }
    }

    /** The page a playlist, album or artist row opens. */
    suspend fun open(target: Target): Listing = when (target) {
        is Target.Playlist -> {
            val playlist = excor.musicPlaylist(target.playlistId) ?: error("YouTube Music has no playlist ${target.playlistId}")
            Listing(
                title = playlist.title ?: "Playlist",
                subtitle = listOfNotNull(playlist.subtitle, playlist.secondSubtitle).joinToString(" · ").ifEmpty { null },
                sections = listOf(Section(null, playlist.tracks.map(::musicRow))),
                next = playlist.continuation?.let { Next.Playlist(target.playlistId, it) },
            )
        }
        is Target.Artist -> {
            val artist = excor.musicArtist(target.channelId) ?: error("YouTube Music has no artist page for ${target.channelId}")
            Listing(
                title = artist.name,
                subtitle = listOfNotNull(artist.monthlyAudienceText, artist.subscribersText).joinToString(" · ").ifEmpty { null },
                sections = artist.shelves.map { shelf ->
                    // "Show all" only where it opens a playlist; other shelves link to pages the preview doesn't read.
                    Section(shelf.title, shelf.items.map(::musicRow), shelf.moreBrowseId?.let(::playlistTarget)?.takeIf { it is Target.Playlist })
                },
            )
        }
        is Target.Video, is Target.Unsupported -> error("not a page")
    }

    /** [listing] with its next page appended to its last section. */
    suspend fun more(listing: Listing): Listing {
        val (rows, next) = when (val next = listing.next ?: return listing) {
            is Next.MusicSearch -> excor.musicSearch("", continuation = next.token)
                ?.let { page -> page.items.map(::musicRow) to page.continuation?.let(Next::MusicSearch) }
            is Next.VideoSearch -> excor.search(next.query, next.token)
                ?.let { page -> page.videos.map(::videoRow) to page.continuation?.let { Next.VideoSearch(next.query, it) } }
            is Next.Playlist -> excor.musicPlaylist(next.playlistId, next.token)
                ?.let { page -> page.tracks.map(::musicRow) to page.continuation?.let { Next.Playlist(next.playlistId, it) } }
        } ?: error("YouTube gave no next page")
        val known = listing.sections.flatMap { it.rows }.toSet()
        val last = listing.sections.lastOrNull() ?: Section(null, emptyList())
        return listing.copy(sections = listing.sections.dropLast(1) + last.copy(rows = last.rows + rows.filter { it !in known }), next = next)
    }
}

fun videoRow(video: ExcorYouTubeVideoResult): Row = Row(
    title = video.title,
    detail = listOfNotNull(video.channelName, video.durationSeconds?.let(::duration), if (video.isLive) "live" else null).joinToString(" · "),
    badge = if (video.isLive) "Live" else "Video",
    target = Target.Video(video.videoId),
)

/** A YouTube Music item as a row, typed from its structured fields rather than its localized subtitle. */
fun musicRow(item: ExcorYouTubeMusicItem): Row {
    val videoId = item.videoId
    val browseId = item.browseId
    val target = when (item.type) {
        ExcorYouTubeMusicItemType.SONG, ExcorYouTubeMusicItemType.VIDEO, ExcorYouTubeMusicItemType.EPISODE ->
            videoId?.let(Target::Video) ?: Target.Unsupported("YouTube Music gave this item no video ID")
        // Albums open as their playlist (OLAK5uy_…); their page ID (MPREb_…) is a different kind of page.
        ExcorYouTubeMusicItemType.ALBUM ->
            item.playlistId?.let(Target::Playlist) ?: Target.Unsupported("YouTube Music gave this album no playlist ID")
        ExcorYouTubeMusicItemType.PLAYLIST ->
            (item.playlistId ?: browseId)?.let(::playlistTarget) ?: Target.Unsupported("YouTube Music gave this playlist no ID")
        ExcorYouTubeMusicItemType.ARTIST, ExcorYouTubeMusicItemType.PROFILE ->
            browseId?.takeIf { it.startsWith("UC") }?.let(Target::Artist) ?: Target.Unsupported("YouTube Music gave this artist no channel ID")
        ExcorYouTubeMusicItemType.PODCAST -> Target.Unsupported("Podcast pages aren't in the preview yet")
        ExcorYouTubeMusicItemType.UNKNOWN -> videoId?.let(Target::Video) ?: Target.Unsupported("YouTube Music didn't say what this item is")
    }
    val detail = item.subtitle ?: listOfNotNull(
        item.artists.joinToString(", ") { it.name }.ifEmpty { null },
        item.album,
        item.durationSeconds?.let(::duration),
    ).joinToString(" · ")
    return Row(item.title, detail, badge(item.type), target)
}

/** A playlist ID or a "VL…" browse ID as a playlist to open; other browse IDs are other kinds of pages. */
fun playlistTarget(id: String): Target =
    if (id.startsWith("VL") || id.startsWith("PL") || id.startsWith("OLAK5uy_") || id.startsWith("RD")) Target.Playlist(id)
    else Target.Unsupported("This list isn't a playlist the preview can open")

fun badge(type: ExcorYouTubeMusicItemType): String = when (type) {
    ExcorYouTubeMusicItemType.SONG -> "Song"
    ExcorYouTubeMusicItemType.VIDEO -> "Video"
    ExcorYouTubeMusicItemType.EPISODE -> "Episode"
    ExcorYouTubeMusicItemType.ARTIST -> "Artist"
    ExcorYouTubeMusicItemType.ALBUM -> "Album"
    ExcorYouTubeMusicItemType.PLAYLIST -> "Playlist"
    ExcorYouTubeMusicItemType.PROFILE -> "Profile"
    ExcorYouTubeMusicItemType.PODCAST -> "Podcast"
    ExcorYouTubeMusicItemType.UNKNOWN -> "Other"
}

/**
 * A failure as the status line shows it. YouTube's bot check ("Sign in to confirm you're not a bot") is explained,
 * since the preview, like the library, never gets around it.
 */
fun explain(label: String, failure: Throwable): String {
    val message = failure.message ?: failure::class.simpleName ?: "error"
    return if ("not a bot" in message || "LOGIN_REQUIRED" in message) {
        "$label: YouTube's bot check blocks every signed-out client on this network; eXcor doesn't get around it."
    } else {
        "$label failed: ${message.removePrefix("Excor ")}"
    }
}

fun duration(seconds: Long): String =
    if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)

/** A file name from a title: no path or reserved characters, at most 120 characters. */
fun safeName(title: String): String =
    title.map { if (it in "\\/:*?\"<>|" || it.code < 32) '_' else it }.joinToString("").trim().trim('.').take(120).ifEmpty { "excor" }
