package com.vyonex.internal

import com.vyonex.VyonExAlbum
import com.vyonex.VyonExArtist
import com.vyonex.VyonExListItem
import com.vyonex.VyonExListPage
import com.vyonex.VyonExPlaylist
import com.vyonex.VyonExRelease
import com.vyonex.VyonExSession
import com.vyonex.VyonExThumbnail
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

private fun thumbnailsOf(sources: JsonElement?) = sources.list.mapNotNull { t ->
    t.at("url").str?.let { VyonExThumbnail(it, t.at("width").str?.toIntOrNull() ?: 0, t.at("height").str?.toIntOrNull() ?: 0) }
}

/** A video `lockupViewModel` (yt-dlp _extract_lockup_view_model); null for other content (playlists, podcasts). */
internal fun lockupItemOf(lockup: JsonElement?): VyonExListItem? {
    if (lockup.at("contentType").str != "LOCKUP_CONTENT_TYPE_VIDEO") return null
    val videoId = lockup.at("contentId").str ?: return null
    val meta = lockup.at("metadata", "lockupMetadataViewModel")
    // The channel is the metadata part whose first command opens a channel page. A video credited to several channels
    // has no such part; they are listed in the avatar stack's dialog instead, and yt-dlp takes the first.
    val channel = meta.at("metadata", "contentMetadataViewModel", "metadataRows").list
        .flatMap { it.at("metadataParts").list }
        .map { it.at("text") }
        .firstOrNull { it.at("commandRuns").list.firstOrNull().at("onTap", "innertubeCommand", "browseEndpoint", "browseId") != null }
        ?: meta.at(
            "image", "avatarStackViewModel", "rendererContext", "commandContext", "onTap", "innertubeCommand", "showDialogCommand",
            "panelLoadingStrategy", "inlineContent", "dialogViewModel", "customContent", "listViewModel", "listItems",
        ).list.firstOrNull().at("listItemViewModel", "title")
    val image = lockup.at("contentImage", "thumbnailViewModel")
    val badges = image.at("overlays").list.flatMap {
        it.at("thumbnailBottomOverlayViewModel", "badges").list + it.at("thumbnailOverlayBadgeViewModel", "thumbnailBadges").list
    }
    return VyonExListItem(
        videoId = videoId,
        title = meta.at("title", "content").str,
        channelName = channel.at("content").str,
        channelId = channel.at("commandRuns").list.firstOrNull().at("onTap", "innertubeCommand", "browseEndpoint", "browseId").str,
        durationSeconds = badges.firstNotNullOfOrNull { durationOf(it.at("thumbnailBadgeViewModel", "text").str) },
        thumbnails = thumbnailsOf(image.at("image", "sources")),
    )
}

/** The videos in [entries] and the token for the next page, if any. */
internal fun listPageOf(entries: List<JsonElement>) = VyonExListPage(
    items = entries.mapNotNull { lockupItemOf(it.at("lockupViewModel")) },
    continuation = entries.firstNotNullOfOrNull {
        it.at("continuationItemViewModel", "continuationCommand", "innertubeCommand", "continuationCommand", "token").str
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

/** The next page of a list, from a browse continuation response. */
internal fun continuationPageOf(response: JsonObject) = listPageOf(
    response.at("onResponseReceivedActions").list.flatMap { it.at("appendContinuationItemsAction", "continuationItems").list },
)

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
