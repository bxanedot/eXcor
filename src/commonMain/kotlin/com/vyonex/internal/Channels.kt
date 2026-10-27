package com.vyonex.internal

import com.vyonex.VyonExChannel
import com.vyonex.VyonExChannelPage
import com.vyonex.VyonExChannelTab
import com.vyonex.VyonExResultKind
import com.vyonex.VyonExSearchResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Channel pages as yt-dlp 2026.10.21 reads them (_tab.py: _extract_metadata_from_tabs, _extract_selected_tab,
// _rich_entries, _grid_entries, _extract_lockup_view_model).

private val JsonElement?.items get() = (this as? JsonArray).orEmpty()

private fun tabsOf(response: JsonObject) =
    response.at("contents", "twoColumnBrowseResultsRenderer", "tabs").items.mapNotNull { it.at("tabRenderer") ?: it.at("expandableTabRenderer") }

/** Each tab VyonEx can list, with the `params` that ask for it; tabs are told apart by their URL, not their localized title. */
internal fun channelTabParamsOf(response: JsonObject): Map<VyonExChannelTab, String> =
    tabsOf(response).mapNotNull { tab ->
        val endpoint = tab.at("endpoint")
        val path = endpoint.at("commandMetadata", "webCommandMetadata", "url").str?.substringAfterLast('/') ?: return@mapNotNull null
        val kind = VyonExChannelTab.entries.firstOrNull { it.path == path } ?: return@mapNotNull null
        endpoint.at("browseEndpoint", "params").str?.let { kind to it }
    }.toMap()

/** A channel's browse response; null when YouTube sent no channel (a wrong ID). */
internal fun channelOf(response: JsonObject): VyonExChannel? {
    val meta = response.at("metadata", "channelMetadataRenderer") ?: return null
    val header = response.at("header", "pageHeaderRenderer", "content", "pageHeaderViewModel")
    return VyonExChannel(
        channelId = meta.at("externalId").str ?: return null,
        title = meta.at("title").str,
        handle = meta.at("vanityChannelUrl").str?.substringAfterLast('/')?.takeIf { it.startsWith("@") },
        description = meta.at("description").str,
        avatars = thumbnailsOf(meta.at("avatar", "thumbnails")),
        banners = thumbnailsOf(header.at("banner", "imageBannerViewModel", "image", "sources")),
        tabs = channelTabParamsOf(response).keys.sortedBy { it.ordinal },
    )
}

/** A Short in a channel's Shorts grid (yt-dlp _rich_entries' shortsLockupViewModel case). */
private fun shortOf(lockup: JsonElement?): VyonExSearchResult? {
    val videoId = lockup.at("onTap", "innertubeCommand", "reelWatchEndpoint", "videoId").str ?: return null
    val title = lockup.at("overlayMetadata", "primaryText", "content").str
        ?: lockup.at("accessibilityText").str?.let { SHORT_LABEL.matchEntire(it)?.groupValues?.get(1) }
    return VyonExSearchResult(
        VyonExResultKind.VIDEO, videoId, title, null, null, null,
        thumbnailsOf(lockup.at("thumbnailViewModel", "thumbnailViewModel", "image", "sources")),
    )
}

// The English label a Short carries when it has no overlay title: "<title>, 3.4 thousand views - play Short".
private val SHORT_LABEL = Regex("""(.+), (?:[\d,.]+(?:[KM]| thousand| million)?|No) views? - play Short""")

/** The items of a tab or continuation: rich-grid items (videos, Shorts, live) and grid items (playlists), and the next page. */
private fun pageOf(entries: List<JsonElement>) = VyonExChannelPage(
    items = entries.mapNotNull { entry ->
        val content = entry.at("richItemRenderer", "content") ?: entry
        webResultOf(content) ?: shortOf(content.at("shortsLockupViewModel"))
    },
    continuation = entries.firstNotNullOfOrNull { it.at("continuationItemRenderer", "continuationEndpoint", "continuationCommand", "token").str },
)

/** The selected tab of a channel's tab response. */
internal fun channelPageOf(response: JsonObject): VyonExChannelPage {
    val content = tabsOf(response).firstOrNull { it.at("selected").bool == true }.at("content")
    val grid = content.at("richGridRenderer", "contents").items
    val sections = content.at("sectionListRenderer", "contents").items
        .flatMap { it.at("itemSectionRenderer", "contents").items }
        .flatMap { it.at("gridRenderer", "items").items.ifEmpty { listOf(it) } }
    return pageOf(grid + sections)
}

/** The next page of a channel tab. */
internal fun channelPageMoreOf(response: JsonObject) = pageOf(
    response.at("onResponseReceivedActions").items.flatMap { it.at("appendContinuationItemsAction", "continuationItems").items },
)
