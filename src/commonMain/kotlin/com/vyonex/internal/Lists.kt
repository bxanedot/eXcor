package com.vyonex.internal

import com.vyonex.VyonExAlbum
import com.vyonex.VyonExArtist
import com.vyonex.VyonExListItem
import com.vyonex.VyonExListPage
import com.vyonex.VyonExMix
import com.vyonex.VyonExPlaylist
import com.vyonex.VyonExRelease
import com.vyonex.VyonExResultKind
import com.vyonex.VyonExSearchFilter
import com.vyonex.VyonExSearchPage
import com.vyonex.VyonExSearchResult
import com.vyonex.VyonExSession
import com.vyonex.VyonExThumbnail
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private val JsonElement?.list get() = (this as? JsonArray).orEmpty()

internal const val MUSIC_ORIGIN = "https://music.youtube.com"

// yt-dlp 2026.10.21 INNERTUBE_CLIENTS web_music; for YouTube Music pages only, never for playback.
internal val MUSIC_WEB = Identity("WEB_REMIX", "WEB_REMIX", 67, "1.20260707.12.00", DESKTOP_UA, needsPlayerJs = false, supportsCookies = true, needsPoToken = false)

/** A logged-out InnerTube request for [id]'s client; [fields] are the endpoint's own (browseId, continuation, …). */
internal fun innertubeBody(id: Identity, session: VyonExSession, fields: JsonObjectBuilder.() -> Unit): String = buildJsonObject {
    putJsonObject("context") {
        putJsonObject("client") {
            put("clientName", id.clientName)
            put("clientVersion", id.clientVersion)
            put("hl", session.language)
            put("gl", session.region)
            session.visitorData?.let { put("visitorData", it) }
        }
    }
    fields()
}.toString()

/** "4:20" or "1:02:03" in seconds; null for badges that are not a duration ("LIVE", "SHORTS"). */
internal fun durationOf(text: String?): Long? {
    val parts = text?.split(':')?.takeIf { it.size in 2..3 } ?: return null
    return parts.fold(0L) { total, part -> total * 60 + (part.toLongOrNull() ?: return null) }
}

internal fun thumbnailsOf(sources: JsonElement?) = sources.list.mapNotNull { t ->
    t.at("url").str?.let { VyonExThumbnail(it, t.at("width").str?.toIntOrNull() ?: 0, t.at("height").str?.toIntOrNull() ?: 0) }
}

/** A video `lockupViewModel` (yt-dlp _extract_lockup_view_model); null for other content (playlists, podcasts). */
internal fun lockupItemOf(lockup: JsonElement?): VyonExListItem? {
    if (lockup.at("contentType").str != "LOCKUP_CONTENT_TYPE_VIDEO") return null
    val videoId = lockup.at("contentId").str ?: return null
    val meta = lockup.at("metadata", "lockupMetadataViewModel")
    val channel = lockupChannelOf(meta)
    val image = lockup.at("contentImage", "thumbnailViewModel")
    val badges = image.at("overlays").list.flatMap {
        it.at("thumbnailBottomOverlayViewModel", "badges").list + it.at("thumbnailOverlayBadgeViewModel", "thumbnailBadges").list
    }
    return VyonExListItem(
        videoId = videoId,
        title = meta.at("title", "content").str,
        channelName = channel.at("content").str,
        channelId = channel.lockupChannelId(),
        durationSeconds = badges.firstNotNullOfOrNull { durationOf(it.at("thumbnailBadgeViewModel", "text").str) },
        thumbnails = thumbnailsOf(image.at("image", "sources")),
    )
}

/**
 * The channel of a lockup: the metadata part whose first command opens a channel page. A video credited to several
 * channels has no such part; they are listed in the avatar stack's dialog instead, and yt-dlp takes the first.
 */
private fun lockupChannelOf(meta: JsonElement?) = meta.at("metadata", "contentMetadataViewModel", "metadataRows").list
    .flatMap { it.at("metadataParts").list }
    .map { it.at("text") }
    .firstOrNull { it.lockupChannelId() != null }
    ?: meta.at(
        "image", "avatarStackViewModel", "rendererContext", "commandContext", "onTap", "innertubeCommand", "showDialogCommand",
        "panelLoadingStrategy", "inlineContent", "dialogViewModel", "customContent", "listViewModel", "listItems",
    ).list.firstOrNull().at("listItemViewModel", "title")

private fun JsonElement?.lockupChannelId() =
    at("commandRuns").list.firstOrNull().at("onTap", "innertubeCommand", "browseEndpoint", "browseId").str

/** The videos in [entries] and the token for the next page, if any. */
internal fun listPageOf(entries: List<JsonElement>) = VyonExListPage(
    items = entries.mapNotNull { lockupItemOf(it.at("lockupViewModel")) },
    // The newer and the older continuation entry (yt-dlp _extract_continuation): playlists use the first, related videos the second.
    continuation = entries.firstNotNullOfOrNull {
        it.at("continuationItemViewModel", "continuationCommand", "innertubeCommand", "continuationCommand", "token").str
            ?: it.at("continuationItemRenderer", "continuationEndpoint", "continuationCommand", "token").str
    },
)

/** A playlist's browse response (`VL` + ID); null when YouTube sent no playlist, as for a missing or private one. */
internal fun playlistOf(playlistId: String, response: JsonObject): VyonExPlaylist? {
    val meta = response.at("metadata", "playlistMetadataRenderer") ?: return null
    // The next page's token is the item section's last entry. A different token follows as a section of its own;
    // the capture's second page came from the item section's.
    val entries = response.at("contents", "twoColumnBrowseResultsRenderer", "tabs").list
        .flatMap { it.at("tabRenderer", "content", "sectionListRenderer", "contents").list }
        .flatMap { it.at("itemSectionRenderer", "contents").list }
    // The sidebar names the owner without the "by" of the localized header; generated album playlists have none.
    val owner = response.at("sidebar", "playlistSidebarRenderer", "items").list
        .firstNotNullOfOrNull { it.at("playlistSidebarSecondaryInfoRenderer", "videoOwner", "videoOwnerRenderer") }
    return VyonExPlaylist(
        playlistId = playlistId,
        title = meta.at("title").str,
        description = meta.at("description").str?.ifEmpty { null },
        ownerName = textOf(owner.at("title")),
        ownerChannelId = owner.at("navigationEndpoint", "browseEndpoint", "browseId").str,
        thumbnails = thumbnailsOf(response.at("microformat", "microformatDataRenderer", "thumbnail", "thumbnails")),
        firstPage = listPageOf(entries),
    )
}

/** The next page of a list from a continuation response, wherever yt-dlp looks for its items. */
internal fun continuationPageOf(response: JsonObject) = listPageOf(
    listOf("onResponseReceivedActions", "onResponseReceivedEndpoints", "onResponseReceivedCommands").flatMap { response.at(it).list }.flatMap {
        it.at("appendContinuationItemsAction", "continuationItems").list + it.at("reloadContinuationItemsCommand", "continuationItems").list
    },
)

/** The related videos beside a video, from a watch-next response. */
internal fun relatedOf(response: JsonObject) =
    listPageOf(response.at("contents", "twoColumnWatchNextResults", "secondaryResults", "secondaryResults", "results").list)

/** A `videoRenderer` (search) or a mix's `playlistPanelVideoRenderer`, read as yt-dlp's _extract_video reads both. */
private fun videoRendererItemOf(item: JsonElement?): VyonExListItem? {
    val videoId = item.at("videoId").str ?: return null
    val byline = item.at("ownerText") ?: item.at("shortBylineText")
    return VyonExListItem(
        videoId = videoId,
        title = textOf(item.at("title")),
        channelName = textOf(byline),
        channelId = byline.at("runs").list.firstNotNullOfOrNull { it.at("navigationEndpoint", "browseEndpoint", "browseId").str },
        durationSeconds = durationOf(textOf(item.at("lengthText"))),
        thumbnails = thumbnailsOf(item.at("thumbnail", "thumbnails")),
    )
}

private fun JsonObject.mixPanel() = at("contents", "twoColumnWatchNextResults", "playlist", "playlist")

/** A mix from a watch-next response; null when the response has no mix panel. */
internal fun mixOf(response: JsonObject): VyonExMix? {
    val panel = response.mixPanel() ?: return null
    return VyonExMix(panel.at("playlistId").str ?: return null, panel.at("title").str, mixPageOf(panel, after = null))
}

/** The videos after [after] in a later watch-next response of the same mix. */
internal fun mixMoreOf(response: JsonObject, after: String) =
    response.mixPanel()?.let { mixPageOf(it, after) } ?: VyonExListPage(emptyList(), null)

/**
 * yt-dlp _extract_inline_playlist: the panel shows a window of the mix, so a later call repeats videos up to [after]
 * and only the ones following it are new. The continuation is the last video's place (playlist, video, index,
 * params); with nothing new, the mix has run dry and there is none.
 */
private fun mixPageOf(panel: JsonElement, after: String?): VyonExListPage {
    val entries = panel.at("contents").list.map { it.at("playlistPanelVideoRenderer") }
    val items = entries.mapNotNull(::videoRendererItemOf)
    val fresh = items.drop(items.indexOfFirst { it.videoId == after } + 1)
    val last = entries.lastOrNull().at("navigationEndpoint", "watchEndpoint")
    val playlistId = panel.at("playlistId").str
    val continuation = if (fresh.isEmpty() || playlistId == null) null else listOf(
        playlistId,
        last.at("videoId").str ?: items.last().videoId,
        last.at("index").str ?: "${items.size}",
        last.at("params").str ?: "OAE%3D",
    ).joinToString(" ")
    return VyonExListPage(fresh, continuation)
}

/** A YouTube Music album page (WEB_REMIX browse of `MPREb_…`); null when it has no album header. */
internal fun albumOf(browseId: String, response: JsonObject): VyonExAlbum? {
    val columns = response.at("contents", "twoColumnBrowseResultsRenderer")
    val header = columns.at("tabs").list
        .flatMap { it.at("tabRenderer", "content", "sectionListRenderer", "contents").list }
        .firstNotNullOfOrNull { it.at("musicResponsiveHeaderRenderer") } ?: return null
    val artist = textOf(header.at("straplineTextOne"))
    val artistChannelId = header.at("straplineTextOne", "runs").list
        .firstNotNullOfOrNull { it.at("navigationEndpoint", "browseEndpoint", "browseId").str }
    return VyonExAlbum(
        browseId = browseId,
        // yt-dlp resolves an album to its playlist through the page's canonical URL.
        playlistId = response.at("microformat", "microformatDataRenderer", "urlCanonical").str
            ?.substringAfter("list=", "")?.substringBefore('&')?.ifEmpty { null },
        title = textOf(header.at("title")),
        artist = artist,
        artistChannelId = artistChannelId,
        year = yearOf(header.at("subtitle")),
        thumbnails = thumbnailsOf(header.at("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")),
        tracks = columns.at("secondaryContents", "sectionListRenderer", "contents").list
            .flatMap { it.at("musicShelfRenderer", "contents").list }
            .mapNotNull { musicTrackOf(it.at("musicResponsiveListItemRenderer"), artist, artistChannelId) },
    )
}

/** A track row (yt-dlp _music_reponsive_list_entry); null when it cannot be played. A row without credits is the album artist's. */
private fun musicTrackOf(row: JsonElement?, artist: String?, artistChannelId: String?): VyonExListItem? {
    val videoId = row.at("playlistItemData", "videoId").str ?: return null
    fun column(index: Int) = row.at("flexColumns").list.getOrNull(index).at("musicResponsiveListItemFlexColumnRenderer", "text")
    val credits = column(1)
    return VyonExListItem(
        videoId = videoId,
        title = column(0).at("runs").list.firstOrNull().at("text").str,
        channelName = textOf(credits)?.ifEmpty { null } ?: artist,
        channelId = credits.at("runs").list.firstNotNullOfOrNull { it.at("navigationEndpoint", "browseEndpoint", "browseId").str } ?: artistChannelId,
        durationSeconds = durationOf(textOf(row.at("fixedColumns").list.firstOrNull().at("musicResponsiveListItemFixedColumnRenderer", "text"))),
        thumbnails = thumbnailsOf(row.at("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")),
    )
}

// "Album • 2015", "Single • 2026", "2023": the four-digit part, whatever the language of the rest.
private fun yearOf(subtitle: JsonElement?) =
    subtitle.at("runs").list.firstNotNullOfOrNull { run -> run.at("text").str?.takeIf { it.length == 4 }?.toIntOrNull() }

private fun JsonElement?.musicPageType() =
    at("browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig", "pageType").str

private fun playlistIdOf(browse: JsonElement?) =
    browse.takeIf { it.musicPageType() == "MUSIC_PAGE_TYPE_PLAYLIST" }.at("browseId").str?.removePrefix("VL")

/**
 * A YouTube Music artist page (WEB_REMIX browse of the channel); null when it has no artist header. Shelves are told
 * apart by what they link to, not by their localized titles.
 */
internal fun artistOf(channelId: String, response: JsonObject): VyonExArtist? {
    val header = response.at("header", "musicImmersiveHeaderRenderer") ?: return null
    val shelves = response.at("contents", "singleColumnBrowseResultsRenderer", "tabs").list
        .flatMap { it.at("tabRenderer", "content", "sectionListRenderer", "contents").list }
    val name = textOf(header.at("title"))
    // The song shelf links to a playlist of all the artist's songs.
    val songs = shelves.firstNotNullOfOrNull { it.at("musicShelfRenderer") }
    val carousels = shelves.mapNotNull { it.at("musicCarouselShelfRenderer") }
    // The video shelf holds videos and links to a playlist of all of them (live performances link to a channel tab).
    val videos = carousels.firstOrNull { shelf ->
        shelf.at("contents").list.firstOrNull().at("musicTwoRowItemRenderer", "navigationEndpoint", "watchEndpoint") != null &&
            playlistIdOf(shelf.moreEndpoint()) != null
    }
    return VyonExArtist(
        channelId = channelId,
        name = name,
        description = textOf(header.at("description")),
        thumbnails = thumbnailsOf(header.at("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")),
        topSongs = songs.at("contents").list.mapNotNull { musicTrackOf(it.at("musicResponsiveListItemRenderer"), name, null) },
        songsPlaylistId = playlistIdOf(songs.at("bottomEndpoint", "browseEndpoint")),
        videosPlaylistId = videos?.let { playlistIdOf(it.moreEndpoint()) },
        releases = carousels.flatMap { it.at("contents").list }.mapNotNull { item ->
            val row = item.at("musicTwoRowItemRenderer")
            val browse = row.at("navigationEndpoint", "browseEndpoint").takeIf { it.musicPageType() == "MUSIC_PAGE_TYPE_ALBUM" }
            browse.at("browseId").str?.let {
                VyonExRelease(it, textOf(row.at("title")), yearOf(row.at("subtitle")), thumbnailsOf(row.at("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail", "thumbnails")))
            }
        }.distinctBy { it.browseId },
    )
}

private fun JsonElement?.moreEndpoint() =
    at("header", "musicCarouselShelfBasicHeaderRenderer", "moreContentButton", "buttonRenderer", "navigationEndpoint", "browseEndpoint")

private fun VyonExListItem.asResult() =
    VyonExSearchResult(VyonExResultKind.VIDEO, videoId, title, channelName, channelId, durationSeconds, thumbnails)

/** A channel in YouTube's search results (yt-dlp _extract_channel_renderer). */
private fun channelResultOf(renderer: JsonElement?): VyonExSearchResult? = VyonExSearchResult(
    kind = VyonExResultKind.CHANNEL,
    id = renderer.at("channelId").str?.takeIf { it.startsWith("UC") } ?: return null,
    title = textOf(renderer.at("title")),
    channelName = null,
    channelId = null,
    durationSeconds = null,
    thumbnails = thumbnailsOf(renderer.at("thumbnail", "thumbnails")),
)

/** A YouTube search entry: a video, a playlist or a channel; shelves, Shorts grids and the artist card are left out. */
internal fun webResultOf(entry: JsonElement?): VyonExSearchResult? {
    videoRendererItemOf(entry.at("videoRenderer"))?.let { return it.asResult() }
    channelResultOf(entry.at("channelRenderer"))?.let { return it }
    val lockup = entry.at("lockupViewModel")
    lockupItemOf(lockup)?.let { return it.asResult() }
    if (lockup.at("contentType").str != "LOCKUP_CONTENT_TYPE_PLAYLIST") return null
    val meta = lockup.at("metadata", "lockupMetadataViewModel")
    val channel = lockupChannelOf(meta)
    return VyonExSearchResult(
        kind = VyonExResultKind.PLAYLIST,
        id = lockup.at("contentId").str ?: return null,
        title = meta.at("title", "content").str,
        channelName = channel.at("content").str,
        channelId = channel.lockupChannelId(),
        durationSeconds = null,
        thumbnails = thumbnailsOf(lockup.at("contentImage", "collectionThumbnailViewModel", "primaryThumbnail", "thumbnailViewModel", "image", "sources")),
    )
}

private fun webSearchPageOf(sections: List<JsonElement>, filters: List<VyonExSearchFilter> = emptyList()): VyonExSearchPage {
    val entries = sections.flatMap { it.at("itemSectionRenderer", "contents").list }
    return VyonExSearchPage(
        results = entries.mapNotNull(::webResultOf),
        continuation = sections.firstNotNullOfOrNull { it.at("continuationItemRenderer", "continuationEndpoint", "continuationCommand", "token").str },
        // A video can be in more than one grid ("Latest Shorts from …", "Shorts").
        shorts = entries.flatMap { it.at("gridShelfViewModel", "contents").list }.mapNotNull { shortOf(it.at("shortsLockupViewModel")) }.distinctBy { it.id },
        filters = filters,
    )
}

/** The filters of YouTube's search page, group by group, from the popup its "Filters" button opens. */
private fun searchFiltersOf(response: JsonObject) =
    response.at("header", "searchHeaderRenderer", "searchFilterButton", "buttonRenderer", "command", "openPopupAction", "popup", "searchFilterOptionsDialogRenderer", "groups")
        .list.flatMap { item ->
            val group = item.at("searchFilterGroupRenderer")
            group.at("filters").list.mapNotNull { f ->
                val filter = f.at("searchFilterRenderer")
                VyonExSearchFilter(
                    group = textOf(group.at("title")),
                    label = textOf(filter.at("label")) ?: return@mapNotNull null,
                    params = filter.at("navigationEndpoint", "searchEndpoint", "params").str,
                    selected = filter.at("status").str == "FILTER_STATUS_SELECTED",
                )
            }
        }

/** A YouTube search response (WEB `search`). */
internal fun webSearchOf(response: JsonObject) = webSearchPageOf(
    response.at("contents", "twoColumnSearchResultsRenderer", "primaryContents", "sectionListRenderer", "contents").list,
    searchFiltersOf(response),
)

/**
 * YouTube's search suggestions: `["query", [["suggestion", 0, [...]], ...], {...}]`. A JSONP wrapper, which YouTube's
 * own search box strips the same way, is taken off first.
 */
internal fun suggestionsOf(text: String): List<String> {
    val json = text.trim().let { if (it.startsWith("[")) it else it.substringAfter('(').substringBeforeLast(')') }
    return (Json.parseToJsonElement(json) as? JsonArray)?.getOrNull(1).list.mapNotNull { it.list.firstOrNull().str }
}

/** The next page of a YouTube search; yt-dlp finds it under onResponseReceivedCommands. */
internal fun webSearchMoreOf(response: JsonObject) =
    webSearchPageOf(response.at("onResponseReceivedCommands").list.flatMap { it.at("appendContinuationItemsAction", "continuationItems").list })

private val MUSIC_CHANNEL_PAGES = setOf("MUSIC_PAGE_TYPE_ARTIST", "MUSIC_PAGE_TYPE_USER_CHANNEL")

/** What a YouTube Music result opens, from its endpoints rather than its localized "Song • …" label. */
private fun musicResultOf(videoId: String?, browse: JsonElement?, title: String?, credits: List<JsonElement>, thumbnails: List<VyonExThumbnail>): VyonExSearchResult? {
    val id = browse.at("browseId").str
    val (kind, target) = when {
        videoId != null -> VyonExResultKind.VIDEO to videoId
        browse.musicPageType() == "MUSIC_PAGE_TYPE_ALBUM" -> VyonExResultKind.ALBUM to id
        browse.musicPageType() == "MUSIC_PAGE_TYPE_ARTIST" -> VyonExResultKind.ARTIST to id
        browse.musicPageType() == "MUSIC_PAGE_TYPE_PLAYLIST" -> VyonExResultKind.PLAYLIST to id?.removePrefix("VL")
        else -> return null // profiles, podcasts
    }
    // The credited artist or channel is the run that opens one; the duration is the run that reads as one.
    val channel = credits.firstOrNull { it.at("navigationEndpoint", "browseEndpoint").musicPageType() in MUSIC_CHANNEL_PAGES }
    return VyonExSearchResult(
        kind = kind,
        id = target ?: return null,
        title = title,
        channelName = channel.at("text").str,
        channelId = channel.at("navigationEndpoint", "browseEndpoint", "browseId").str,
        durationSeconds = credits.firstNotNullOfOrNull { durationOf(it.at("text").str) },
        thumbnails = thumbnails,
    )
}

/** A YouTube Music search response (WEB_REMIX `search`): the top result card, then the result rows. */
internal fun musicSearchOf(response: JsonObject): VyonExSearchPage {
    val sections = response.at("contents", "tabbedSearchResultsRenderer", "tabs").list.firstOrNull()
        .at("tabRenderer", "content", "sectionListRenderer", "contents").list
    val results = sections.flatMap { section ->
        val card = section.at("musicCardShelfRenderer")
        val top = card?.let {
            val endpoint = it.at("title", "runs").list.firstOrNull().at("navigationEndpoint")
            musicResultOf(
                endpoint.at("watchEndpoint", "videoId").str, endpoint.at("browseEndpoint"), textOf(it.at("title")),
                it.at("subtitle", "runs").list, thumbnailsOf(it.at("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")),
            )
        }
        val rows = (card.at("contents").list + section.at("itemSectionRenderer", "contents").list).mapNotNull { entry ->
            val row = entry.at("musicResponsiveListItemRenderer") ?: return@mapNotNull null
            fun column(index: Int) = row.at("flexColumns").list.getOrNull(index).at("musicResponsiveListItemFlexColumnRenderer", "text")
            musicResultOf(
                row.at("playlistItemData", "videoId").str, row.at("navigationEndpoint", "browseEndpoint"), textOf(column(0)),
                column(1).at("runs").list, thumbnailsOf(row.at("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")),
            )
        }
        listOfNotNull(top) + rows
    }
    // The top result can come back among the rows.
    return VyonExSearchPage(results.distinctBy { it.kind to it.id }, continuation = null)
}
