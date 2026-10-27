package com.vyonex.internal

import com.vyonex.VyonExListItem
import com.vyonex.VyonExListPage
import com.vyonex.VyonExPlaylist
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
