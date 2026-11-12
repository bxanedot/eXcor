package com.excor

import com.excor.catalog.ExcorArtwork
import io.ktor.http.Url
import io.ktor.http.decodeURLQueryComponent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * What a YouTube Music item is, read from YouTube Music's own structured fields (the music video type of a playable
 * item, the page type of a browsable one), never from its localized "Song" or "Video" label.
 */
enum class ExcorYouTubeMusicItemType { SONG, VIDEO, EPISODE, ARTIST, ALBUM, PLAYLIST, PROFILE, PODCAST, UNKNOWN }

/** An artist or channel credited on an item; [channelId] is null when YouTube Music gives the name without a link. */
data class ExcorYouTubeMusicArtistRef(val name: String, val channelId: String?) {
    override fun toString(): String =
        "ExcorYouTubeMusicArtistRef(name=${name.isNotEmpty()}, channelId=${channelId != null})"
}

/**
 * One YouTube Music item: a playable song, video or episode (with [videoId]) or a browsable artist, album, playlist,
 * profile or podcast (with [browseId]). [subtitle] is YouTube Music's display line, such as "Video • Daft Punk • 65M
 * views". [musicVideoType] is YouTube Music's raw type, such as `MUSIC_VIDEO_TYPE_ATV` for a song's audio track.
 */
data class ExcorYouTubeMusicItem(
    val type: ExcorYouTubeMusicItemType,
    val title: String,
    val videoId: String? = null,
    val browseId: String? = null,
    val playlistId: String? = null,
    val artists: List<ExcorYouTubeMusicArtistRef> = emptyList(),
    val album: String? = null,
    val albumBrowseId: String? = null,
    val durationSeconds: Long? = null,
    val subtitle: String? = null,
    val musicVideoType: String? = null,
    val thumbnails: List<ExcorArtwork> = emptyList(),
) {
    override fun toString(): String =
            "ExcorYouTubeMusicItem(type=$type, videoId=${videoId != null}, browseId=${browseId != null}, " +
                "playlistId=${playlistId != null}, artists=${artists.size})"
}

/**
 * A YouTube Music search page. [filters] are YouTube Music's own filter chips for this query, with the params to pass
 * back; [continuation] continues a filtered search (an unfiltered one is a single page of mixed sections).
 */
data class ExcorYouTubeMusicSearchPage(
    val items: List<ExcorYouTubeMusicItem>,
    val filters: List<ExcorYouTubeSearchFilter>,
    val continuation: String?,
) {
    override fun toString(): String =
            "ExcorYouTubeMusicSearchPage(items=${items.size}, filters=${filters.size}, continuation=${continuation != null})"
}

/** A titled shelf of an artist page, such as Videos or Playlists; [moreBrowseId] opens its full list. */
data class ExcorYouTubeMusicShelf(val title: String, val items: List<ExcorYouTubeMusicItem>, val moreBrowseId: String?) {
    override fun toString(): String =
            "ExcorYouTubeMusicShelf(items=${items.size}, more=${moreBrowseId != null})"
}

/** A YouTube Music artist page. [radioPlaylistId] and [shufflePlaylistId] feed [Excor.musicRadio]-style queues. */
data class ExcorYouTubeMusicArtist(
    val channelId: String,
    val name: String,
    val description: String?,
    /** Such as "68.4M monthly audience". */
    val monthlyAudienceText: String?,
    val subscribersText: String?,
    val radioPlaylistId: String?,
    val shufflePlaylistId: String?,
    val shelves: List<ExcorYouTubeMusicShelf>,
    val thumbnails: List<ExcorArtwork>,
) {
    override fun toString(): String =
            "ExcorYouTubeMusicArtist(shelves=${shelves.size}, radioAvailable=${radioPlaylistId != null}, " +
                "shuffleAvailable=${shufflePlaylistId != null}, thumbnails=${thumbnails.size})"
}

/** A YouTube Music playlist page; [continuation] gives the next tracks when the playlist has more. */
data class ExcorYouTubeMusicPlaylist(
    val playlistId: String,
    val title: String?,
    /** YouTube Music's header lines, such as "Playlist • 2026" and "116 songs • 19+ hours". */
    val subtitle: String?,
    val secondSubtitle: String?,
    val tracks: List<ExcorYouTubeMusicItem>,
    val continuation: String?,
) {
    override fun toString(): String =
            "ExcorYouTubeMusicPlaylist(tracks=${tracks.size}, continuation=${continuation != null})"
}

/** A window of a YouTube Music radio or watch queue; it does not end, so [continuation] gives the next window. */
data class ExcorYouTubeMusicQueue(val playlistId: String?, val tracks: List<ExcorYouTubeMusicItem>, val continuation: String?) {
    override fun toString(): String =
            "ExcorYouTubeMusicQueue(tracks=${tracks.size}, continuation=${continuation != null})"
}

private const val MUSIC_HOST = "music.youtube.com"

internal fun buildMusicSearchRequest(query: String?, continuation: String?, filterParams: String?, session: ExcorSession): PlayerRequest =
    innertubeRequest("search", session, musicClient, MUSIC_HOST) {
        if (continuation != null) {
            put("continuation", continuation)
        } else {
            put("query", query!!)
            if (filterParams != null) put("params", filterParams)
        }
    }

internal fun buildMusicSuggestionsRequest(input: String, session: ExcorSession): PlayerRequest =
    innertubeRequest("music/get_search_suggestions", session, musicClient, MUSIC_HOST) { put("input", input) }

internal fun buildMusicBrowseRequest(browseId: String?, continuation: String?, session: ExcorSession): PlayerRequest =
    innertubeRequest("browse", session, musicClient, MUSIC_HOST) {
        if (continuation != null) put("continuation", continuation) else put("browseId", browseId!!)
    }

internal fun buildMusicQueueRequest(videoId: String?, playlistId: String?, continuation: String?, session: ExcorSession): PlayerRequest =
    innertubeRequest("next", session, musicClient, MUSIC_HOST) {
        if (continuation != null) {
            put("continuation", continuation)
        } else {
            videoId?.let { put("videoId", it) }
            playlistId?.let { put("playlistId", it) }
            put("isAudioOnly", true)
        }
    }

/** A first search page (`tabbedSearchResultsRenderer`) or a filtered continuation; null when it is neither. */
internal fun decodeMusicSearch(source: String): ExcorYouTubeMusicSearchPage? {
    val root = parseObject(source) ?: return null
    (root.path("continuationContents", "musicShelfContinuation") as? JsonObject)?.let { shelf ->
        return ExcorYouTubeMusicSearchPage(itemsOf(shelf["contents"]), emptyList(), nextContinuationOf(shelf))
    }
    val list = ((root.path("contents", "tabbedSearchResultsRenderer", "tabs") as? JsonArray)?.firstOrNull() as? JsonObject)
        ?.path("tabRenderer", "content", "sectionListRenderer") as? JsonObject ?: return null
    val items = mutableListOf<ExcorYouTubeMusicItem>()
    var continuation: String? = null
    for (section in (list["contents"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }) {
        (section["musicCardShelfRenderer"] as? JsonObject)?.let(::topResultOf)?.let(items::add)
        (section.path("itemSectionRenderer", "contents") as? JsonArray)?.let { items += itemsOf(it) }
        (section["musicShelfRenderer"] as? JsonObject)?.let { shelf ->
            items += itemsOf(shelf["contents"])
            continuation = continuation ?: nextContinuationOf(shelf)
        }
    }
    val filters = (list.path("header", "chipCloudRenderer", "chips") as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("chipCloudChipRenderer") as? JsonObject }.mapNotNull { chip ->
        // The first chip is an unlabeled icon that clears the filter; only labeled chips are filters.
        val label = runsOf(chip["text"]) ?: return@mapNotNull null
        val selected = (chip["isSelected"] as? JsonPrimitive)?.booleanOrNull == true
        val params = (chip.path("navigationEndpoint", "searchEndpoint", "params") as? JsonPrimitive)?.contentOrNull
            ?.let { runCatching { it.decodeURLQueryComponent() }.getOrNull() }?.takeIf(SEARCH_PARAMS::matches)
        ExcorYouTubeSearchFilter("Type", label, params.takeIf { !selected }, selected, false)
    }
    return ExcorYouTubeMusicSearchPage(items, filters, continuation)
}

/** Query completions from `music/get_search_suggestions`; empty when there are none. */
internal fun decodeMusicSuggestions(source: String): List<String> {
    val root = parseObject(source) ?: return emptyList()
    return (root["contents"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        .flatMap { (it.path("searchSuggestionsSectionRenderer", "contents") as? JsonArray).orEmpty() }
        .mapNotNull { (it as? JsonObject)?.path("searchSuggestionRenderer", "suggestion")?.let(::runsOf) }
        .filter { it.length <= 200 }
        .take(20)
}

/** An artist page (`musicImmersiveHeaderRenderer` plus carousels); null when it is not one. */
internal fun decodeMusicArtist(source: String, channelId: String): ExcorYouTubeMusicArtist? {
    val root = parseObject(source) ?: return null
    val header = (root.path("header", "musicImmersiveHeaderRenderer") ?: root.path("header", "musicVisualHeaderRenderer")) as? JsonObject ?: return null
    val name = runsOf(header["title"]) ?: return null
    val sections = ((root.path("contents", "singleColumnBrowseResultsRenderer", "tabs") as? JsonArray)?.firstOrNull() as? JsonObject)
        ?.path("tabRenderer", "content", "sectionListRenderer", "contents") as? JsonArray
    val shelves = sections.orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { section ->
        val carousel = section["musicCarouselShelfRenderer"] as? JsonObject
        val shelf = section["musicShelfRenderer"] as? JsonObject
        when {
            carousel != null -> {
                val head = carousel.path("header", "musicCarouselShelfBasicHeaderRenderer") as? JsonObject
                ExcorYouTubeMusicShelf(
                    title = runsOf(head?.get("title")) ?: return@mapNotNull null,
                    items = itemsOf(carousel["contents"]),
                    moreBrowseId = head?.path("moreContentButton", "buttonRenderer", "navigationEndpoint", "browseEndpoint", "browseId")?.let(::stringOf),
                )
            }
            shelf != null -> ExcorYouTubeMusicShelf(
                title = runsOf(shelf["title"]) ?: return@mapNotNull null,
                items = itemsOf(shelf["contents"]),
                moreBrowseId = shelf.path("bottomEndpoint", "browseEndpoint", "browseId")?.let(::stringOf),
            )
            else -> null
        }
    }
    return ExcorYouTubeMusicArtist(
        channelId = channelId,
        name = name,
        description = runsOf(header["description"]),
        monthlyAudienceText = runsOf(header["monthlyListenerCount"]),
        subscribersText = runsOf(header.path("subscriptionButton", "subscribeButtonRenderer", "subscriberCountText")),
        radioPlaylistId = buttonPlaylistId(header["startRadioButton"]),
        shufflePlaylistId = buttonPlaylistId(header["playButton"]),
        shelves = shelves,
        thumbnails = thumbnailsOf(header.path("thumbnail", "musicThumbnailRenderer", "thumbnail")),
    )
}

// The header's play and radio buttons carry either a watchPlaylistEndpoint or, in a newer variant, a watchEndpoint with a first video.
private fun buttonPlaylistId(button: JsonElement?): String? {
    val endpoint = (button as? JsonObject)?.path("buttonRenderer", "navigationEndpoint") as? JsonObject ?: return null
    return (endpoint.path("watchPlaylistEndpoint", "playlistId") ?: endpoint.path("watchEndpoint", "playlistId"))?.let(::stringOf)?.takeIf(PLAYLIST_ID::matches)
}

/** A playlist page (`musicPlaylistShelfRenderer`) or a continuation of one; null when it is neither. */
internal fun decodeMusicPlaylist(source: String, playlistId: String): ExcorYouTubeMusicPlaylist? {
    val root = parseObject(source) ?: return null
    val continued = (root.path("continuationContents", "musicPlaylistShelfContinuation") as? JsonObject)
        ?: ((root["onResponseReceivedActions"] as? JsonArray)?.firstOrNull() as? JsonObject)?.path("appendContinuationItemsAction")?.let { a ->
            (a as? JsonObject)?.get("continuationItems")?.let { JsonObject(mapOf("contents" to it)) }
        }
    if (continued != null) {
        val items = continued["contents"] as? JsonArray ?: JsonArray(emptyList())
        return ExcorYouTubeMusicPlaylist(playlistId, null, null, null, itemsOf(items), nextContinuationOf(continued) ?: trailingContinuationOf(items))
    }
    val sections = root.path("contents", "twoColumnBrowseResultsRenderer", "secondaryContents", "sectionListRenderer", "contents") as? JsonArray ?: return null
    val shelf = sections.firstNotNullOfOrNull { (it as? JsonObject)?.get("musicPlaylistShelfRenderer") as? JsonObject } ?: return null
    val header = ((root.path("contents", "twoColumnBrowseResultsRenderer", "tabs") as? JsonArray)?.firstOrNull() as? JsonObject)
        ?.path("tabRenderer", "content", "sectionListRenderer", "contents")?.let { c ->
            (c as? JsonArray)?.firstNotNullOfOrNull { (it as? JsonObject)?.get("musicResponsiveHeaderRenderer") as? JsonObject }
        }
    val items = shelf["contents"] as? JsonArray ?: JsonArray(emptyList())
    return ExcorYouTubeMusicPlaylist(
        playlistId = playlistId,
        title = runsOf(header?.get("title")),
        subtitle = runsOf(header?.get("subtitle")),
        secondSubtitle = runsOf(header?.get("secondSubtitle")),
        tracks = itemsOf(items),
        continuation = nextContinuationOf(shelf) ?: trailingContinuationOf(items),
    )
}

/** A radio or watch queue (`playlistPanelRenderer`) or its continuation; null when it is neither. */
internal fun decodeMusicQueue(source: String): ExcorYouTubeMusicQueue? {
    val root = parseObject(source) ?: return null
    val panel = (root.path("continuationContents", "playlistPanelContinuation") as? JsonObject)
        ?: root.path(
            "contents", "singleColumnMusicWatchNextResultsRenderer", "tabbedRenderer", "watchNextTabbedResultsRenderer",
        )?.let { tabs ->
            ((tabs as? JsonObject)?.get("tabs") as? JsonArray)?.firstOrNull()?.let { tab ->
                (tab as? JsonObject)?.path("tabRenderer", "content", "musicQueueRenderer", "content", "playlistPanelRenderer") as? JsonObject
            }
        }
        ?: return null
    val continuation = (panel["continuations"] as? JsonArray).orEmpty().firstNotNullOfOrNull { c ->
        ((c as? JsonObject)?.path("nextRadioContinuationData", "continuation") ?: (c as? JsonObject)?.path("nextContinuationData", "continuation"))?.let(::stringOf)
    }?.takeIf(::validToken)
    return ExcorYouTubeMusicQueue(panel.textAt("playlistId")?.takeIf(PLAYLIST_ID::matches), itemsOf(panel["contents"]), continuation)
}

/** Items of any of YouTube Music's list renderers; entries that don't decode are skipped. */
private fun itemsOf(value: JsonElement?): List<ExcorYouTubeMusicItem> =
    (value as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { entry ->
        (entry["musicResponsiveListItemRenderer"] as? JsonObject)?.let(::listItemOf)
            ?: (entry["musicTwoRowItemRenderer"] as? JsonObject)?.let(::twoRowItemOf)
            ?: (entry["playlistPanelVideoRenderer"] as? JsonObject)?.let(::queueItemOf)
            ?: (entry.path("playlistPanelVideoWrapperRenderer", "primaryRenderer", "playlistPanelVideoRenderer") as? JsonObject)?.let(::queueItemOf)
    }

private fun listItemOf(item: JsonObject): ExcorYouTubeMusicItem? {
    val columns = (item["flexColumns"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.path("musicResponsiveListItemFlexColumnRenderer", "text") as? JsonObject }
    val title = runsOf(columns.firstOrNull()) ?: return null
    val detailRuns = columns.drop(1).flatMap { (it["runs"] as? JsonArray).orEmpty().mapNotNull { r -> r as? JsonObject } }
    val watch = (item.path("overlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint")
        ?: ((columns.firstOrNull()?.get("runs") as? JsonArray)?.firstOrNull() as? JsonObject)?.path("navigationEndpoint", "watchEndpoint")) as? JsonObject
    val videoId = (item.path("playlistItemData", "videoId")?.let(::stringOf) ?: watch?.textAt("videoId"))?.takeIf(VIDEO_ID::matches)
    val browse = item.path("navigationEndpoint", "browseEndpoint") as? JsonObject
    val fixed = (item["fixedColumns"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.path("musicResponsiveListItemFixedColumnRenderer", "text")?.let(::runsOf) }
    return itemOf(
        title = title,
        videoId = videoId,
        musicVideoType = watch?.path("watchEndpointMusicSupportedConfigs", "watchEndpointMusicConfig", "musicVideoType")?.let(::stringOf),
        browse = browse,
        playlistId = watch?.textAt("playlistId") ?: playAllPlaylistId(item),
        runs = detailRuns,
        // Song rows put the length at the end of the subtitle instead of in a fixed column.
        duration = fixed.firstNotNullOfOrNull(::clockSeconds) ?: detailRuns.mapNotNull { it.textAt("text") }.firstNotNullOfOrNull(::clockSeconds),
        subtitle = runsOf(columns.getOrNull(1)),
        thumbnails = thumbnailsOf(item.path("thumbnail", "musicThumbnailRenderer", "thumbnail")),
    )
}

/**
 * The playlist an album's or playlist's play button starts. Album rows and cards link to their page by a browse ID
 * (MPREb_…) and carry the album's playlist (OLAK5uy_…) only on that button, as a watchPlaylistEndpoint.
 */
private fun playAllPlaylistId(item: JsonObject): String? = sequenceOf("overlay", "thumbnailOverlay")
    .mapNotNull { key ->
        item.path(key, "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer", "playNavigationEndpoint", "watchPlaylistEndpoint", "playlistId")
            ?.let(::stringOf)
    }
    .firstOrNull()

private fun twoRowItemOf(item: JsonObject): ExcorYouTubeMusicItem? {
    val title = runsOf(item["title"]) ?: return null
    val watch = item.path("navigationEndpoint", "watchEndpoint") as? JsonObject
    return itemOf(
        title = title,
        videoId = watch?.textAt("videoId")?.takeIf(VIDEO_ID::matches),
        musicVideoType = watch?.path("watchEndpointMusicSupportedConfigs", "watchEndpointMusicConfig", "musicVideoType")?.let(::stringOf),
        browse = item.path("navigationEndpoint", "browseEndpoint") as? JsonObject,
        playlistId = watch?.textAt("playlistId") ?: playAllPlaylistId(item),
        runs = (item.path("subtitle", "runs") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject },
        duration = null,
        subtitle = runsOf(item["subtitle"]),
        thumbnails = thumbnailsOf(item.path("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail")),
    )
}

private fun queueItemOf(item: JsonObject): ExcorYouTubeMusicItem? {
    val watch = item.path("navigationEndpoint", "watchEndpoint") as? JsonObject
    return itemOf(
        title = runsOf(item["title"]) ?: return null,
        videoId = (item.textAt("videoId") ?: watch?.textAt("videoId"))?.takeIf(VIDEO_ID::matches) ?: return null,
        musicVideoType = watch?.path("watchEndpointMusicSupportedConfigs", "watchEndpointMusicConfig", "musicVideoType")?.let(::stringOf),
        browse = null,
        playlistId = watch?.textAt("playlistId"),
        runs = (item.path("longBylineText", "runs") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject },
        duration = runsOf(item["lengthText"])?.let(::clockSeconds),
        subtitle = runsOf(item["longBylineText"]),
        thumbnails = thumbnailsOf(item["thumbnail"]),
    )
}

private fun itemOf(
    title: String,
    videoId: String?,
    musicVideoType: String?,
    browse: JsonObject?,
    playlistId: String?,
    runs: List<JsonObject>,
    duration: Long?,
    subtitle: String?,
    thumbnails: List<ExcorArtwork>,
): ExcorYouTubeMusicItem {
    val pageType = browse?.path("browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig", "pageType")?.let(::stringOf)
    val type = when {
        videoId != null && browse == null -> when (musicVideoType) {
            "MUSIC_VIDEO_TYPE_ATV" -> ExcorYouTubeMusicItemType.SONG
            "MUSIC_VIDEO_TYPE_PODCAST_EPISODE" -> ExcorYouTubeMusicItemType.EPISODE
            else -> ExcorYouTubeMusicItemType.VIDEO
        }
        else -> pageTypeOf(pageType)
    }
    val linked = runs.mapNotNull { run -> run.textAt("text")?.let { text -> text to run.path("navigationEndpoint", "browseEndpoint") as? JsonObject } }
    val artists = linked.mapNotNull { (text, link) ->
        val linkType = link?.path("browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig", "pageType")?.let(::stringOf)
        if (linkType == "MUSIC_PAGE_TYPE_ARTIST" || linkType == "MUSIC_PAGE_TYPE_USER_CHANNEL") ExcorYouTubeMusicArtistRef(text, link?.textAt("browseId")?.takeIf(CHANNEL_ID::matches)) else null
    }
    val album = linked.firstOrNull { (_, link) ->
        link?.path("browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig", "pageType")?.let(::stringOf) == "MUSIC_PAGE_TYPE_ALBUM"
    }
    return ExcorYouTubeMusicItem(
        type = type,
        title = title,
        videoId = videoId,
        browseId = browse?.textAt("browseId")?.takeIf { it.length <= 128 && it.all { c -> c.isLetterOrDigit() || c in "_-" } },
        playlistId = playlistId?.takeIf(PLAYLIST_ID::matches),
        artists = artists,
        album = album?.first,
        albumBrowseId = album?.second?.textAt("browseId"),
        durationSeconds = duration,
        subtitle = subtitle,
        musicVideoType = musicVideoType,
        thumbnails = thumbnails,
    )
}

private fun pageTypeOf(pageType: String?): ExcorYouTubeMusicItemType = when (pageType) {
    "MUSIC_PAGE_TYPE_ARTIST" -> ExcorYouTubeMusicItemType.ARTIST
    "MUSIC_PAGE_TYPE_ALBUM" -> ExcorYouTubeMusicItemType.ALBUM
    "MUSIC_PAGE_TYPE_PLAYLIST" -> ExcorYouTubeMusicItemType.PLAYLIST
    "MUSIC_PAGE_TYPE_USER_CHANNEL" -> ExcorYouTubeMusicItemType.PROFILE
    "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE" -> ExcorYouTubeMusicItemType.PODCAST
    else -> ExcorYouTubeMusicItemType.UNKNOWN
}

/**
 * The search's top result card. It either links to a page (usually the artist), with a browse endpoint, or plays a
 * video or song, with a watch endpoint on the card and its title; a playable card's subtitle ends with the duration.
 */
private fun topResultOf(card: JsonObject): ExcorYouTubeMusicItem? {
    val title = runsOf(card["title"]) ?: return null
    val titleLink = ((card.path("title", "runs") as? JsonArray)?.firstOrNull() as? JsonObject)?.get("navigationEndpoint") as? JsonObject
    val browse = (card.path("onTap", "browseEndpoint") ?: titleLink?.get("browseEndpoint")) as? JsonObject
    val watch = if (browse == null) (card.path("onTap", "watchEndpoint") ?: titleLink?.get("watchEndpoint")) as? JsonObject else null
    val runs = (card.path("subtitle", "runs") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    return itemOf(
        title = title,
        videoId = watch?.textAt("videoId")?.takeIf(VIDEO_ID::matches),
        musicVideoType = watch?.path("watchEndpointMusicSupportedConfigs", "watchEndpointMusicConfig", "musicVideoType")?.let(::stringOf),
        browse = browse,
        playlistId = watch?.textAt("playlistId"),
        runs = runs,
        duration = if (watch != null) runs.mapNotNull { it.textAt("text") }.lastOrNull()?.let(::clockSeconds) else null,
        subtitle = runsOf(card["subtitle"]),
        thumbnails = thumbnailsOf(card.path("thumbnail", "musicThumbnailRenderer", "thumbnail")),
    )
}

private fun nextContinuationOf(shelf: JsonObject): String? =
    (shelf["continuations"] as? JsonArray).orEmpty().firstNotNullOfOrNull { (it as? JsonObject)?.path("nextContinuationData", "continuation")?.let(::stringOf) }
        ?.takeIf(::validToken)

private fun trailingContinuationOf(items: JsonArray): String? =
    items.mapNotNull { it as? JsonObject }.firstNotNullOfOrNull { it.path("continuationItemRenderer", "continuationEndpoint", "continuationCommand", "token")?.let(::stringOf) }
        ?.takeIf(::validToken)

private fun validToken(token: String) = token.length <= MAX_CONTINUATION && token.all { it.isLetterOrDigit() || it in "_-=%" }

private fun runsOf(value: JsonElement?): String? {
    val o = value as? JsonObject ?: return null
    o.textAt("simpleText")?.let { return it.takeIf(String::isNotBlank) }
    return (o["runs"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.textAt("text") }?.joinToString("")?.trim()?.takeIf(String::isNotEmpty)
}

private fun stringOf(value: JsonElement): String? = (value as? JsonPrimitive)?.contentOrNull

private fun thumbnailsOf(value: JsonElement?): List<ExcorArtwork> =
    ((value as? JsonObject)?.get("thumbnails") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { t ->
        val url = t.textAt("url") ?: return@mapNotNull null
        val parsed = runCatching { Url(url) }.getOrNull() ?: return@mapNotNull null
        val host = parsed.host.lowercase()
        if (parsed.protocol.name != "https" || (host != "i.ytimg.com" && !host.endsWith(".googleusercontent.com") && host != "yt3.ggpht.com")) return@mapNotNull null
        ExcorArtwork(url, t.textAt("width")?.toIntOrNull(), t.textAt("height")?.toIntOrNull())
    }
