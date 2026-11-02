package com.excor

import com.excor.catalog.ExcorArtwork
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * A YouTube playlist page from InnerTube browse. The first page carries the playlist's details; continuation pages
 * carry only [videos] and the next [continuation]. YouTube leaves unavailable videos out of the list, so [videos]
 * across all pages can be fewer than [videoCount].
 */
data class ExcorYouTubePlaylist(
    val playlistId: String,
    val title: String?,
    val description: String?,
    val ownerName: String?,
    val ownerChannelId: String?,
    val videoCount: Int?,
    val viewCount: Long?,
    /** YouTube's text, such as "Last updated on Jul 17, 2026". */
    val lastUpdatedText: String?,
    val videos: List<ExcorYouTubePlaylistVideo>,
    val continuation: String?,
)

data class ExcorYouTubePlaylistVideo(
    val videoId: String,
    val title: String,
    val channelName: String?,
    val channelId: String?,
    val durationSeconds: Long?,
    /** YouTube's display texts, such as "773M views" and "2 years ago". */
    val viewsText: String?,
    val publishedText: String?,
    val thumbnails: List<ExcorArtwork>,
    /** Streaming now: YouTube's live badge, read by its style so the badge's localized text does not matter. */
    val isLive: Boolean = false,
)

internal fun buildBrowseRequest(browseId: String?, continuation: String?, session: ExcorSession, params: String? = null): PlayerRequest =
    innertubeRequest("browse", session) {
        if (continuation != null) {
            put("continuation", continuation)
        } else {
            put("browseId", browseId!!)
            params?.let { put("params", it) }
        }
    }

/** A playlist's first page (`contents`) or a continuation (`onResponseReceivedActions`); null when it is neither. */
internal fun decodePlaylistPage(source: String, playlistId: String): ExcorYouTubePlaylist? {
    if (source.length > 4_000_000) return null
    val root = runCatching { Json.parseToJsonElement(source) }.getOrNull() as? JsonObject ?: return null
    val firstPage = ((root.path("contents", "twoColumnBrowseResultsRenderer", "tabs") as? JsonArray)?.firstOrNull() as? JsonObject)
        ?.path("tabRenderer", "content", "sectionListRenderer", "contents") as? JsonArray
    val sections = firstPage
        ?: ((root["onResponseReceivedActions"] as? JsonArray)?.firstOrNull() as? JsonObject)
            ?.path("appendContinuationItemsAction", "continuationItems") as? JsonArray
        ?: return null
    val videos = mutableListOf<ExcorYouTubePlaylistVideo>()
    var continuation: String? = null
    fun collect(items: JsonArray) {
        for (item in items.mapNotNull { it as? JsonObject }) {
            ((item["lockupViewModel"] ?: item.path("richItemRenderer", "content", "lockupViewModel")) as? JsonObject)
                ?.let(::playlistVideoOf)?.let(videos::add)
            continuationOf(item)?.let { continuation = it }
        }
    }
    // Items come wrapped in itemSectionRenderer or, on some continuation pages, directly.
    collect(sections)
    for (section in sections.mapNotNull { it as? JsonObject }) {
        (section.path("itemSectionRenderer", "contents") as? JsonArray)?.let(::collect)
    }
    if (firstPage == null) return ExcorYouTubePlaylist(playlistId, null, null, null, null, null, null, null, videos, continuation)

    val micro = root.path("microformat", "microformatDataRenderer") as? JsonObject
    val stats = ((root.path("sidebar", "playlistSidebarRenderer", "items") as? JsonArray)?.firstOrNull() as? JsonObject)
        ?.path("playlistSidebarPrimaryInfoRenderer", "stats") as? JsonArray
    val statTexts = stats.orEmpty().map { textOf(it) }
    val owner = ownerOf(root.path("header", "pageHeaderRenderer", "content", "pageHeaderViewModel", "metadata"))
    return ExcorYouTubePlaylist(
        playlistId = playlistId,
        title = root.path("header", "pageHeaderRenderer", "pageTitle")?.let { (it as? JsonPrimitive)?.contentOrNull } ?: micro?.textAt("title"),
        description = micro?.textAt("description")?.takeIf(String::isNotBlank),
        ownerName = owner?.first,
        ownerChannelId = owner?.second,
        videoCount = statTexts.firstOrNull { it?.contains("video") == true }?.filter(Char::isDigit)?.toIntOrNull(),
        viewCount = statTexts.firstOrNull { it?.contains("view") == true }?.filter(Char::isDigit)?.toLongOrNull(),
        lastUpdatedText = statTexts.firstOrNull { it?.startsWith("Last updated") == true || it?.startsWith("Updated") == true },
        videos = videos,
        continuation = continuation,
    )
}

internal fun playlistVideoOf(l: JsonObject): ExcorYouTubePlaylistVideo? {
    if (l.textAt("contentType") != "LOCKUP_CONTENT_TYPE_VIDEO") return null
    val id = l.textAt("contentId")?.takeIf(VIDEO_ID::matches) ?: return null
    val meta = l.path("metadata", "lockupMetadataViewModel") as? JsonObject ?: return null
    val title = meta.path("title", "content")?.let { (it as? JsonPrimitive)?.contentOrNull } ?: return null
    val rows = (meta.path("metadata", "contentMetadataViewModel", "metadataRows") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    val parts = rows.flatMap { row -> (row["metadataParts"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject } }
    // Parts are told apart by content: a channel link, a views count, then the published text. Channel pages omit the channel.
    val channelPart = parts.firstOrNull { channelBrowseId(it) != null }
    val texts = parts.filter { it !== channelPart }.mapNotNull { it.path("text", "content")?.let { c -> (c as? JsonPrimitive)?.contentOrNull } }
    val image = l.path("contentImage", "thumbnailViewModel") as? JsonObject
    val badges = ((image?.get("overlays") as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.path("thumbnailBottomOverlayViewModel", "badges") as? JsonArray }).flatten()
    val duration = badges.firstNotNullOfOrNull { (it as? JsonObject)?.path("thumbnailBadgeViewModel", "text")?.let { t -> (t as? JsonPrimitive)?.contentOrNull }?.let(::clockSeconds) }
    val live = badges.any { ((it as? JsonObject)?.path("thumbnailBadgeViewModel", "badgeStyle") as? JsonPrimitive)?.contentOrNull == "THUMBNAIL_OVERLAY_BADGE_STYLE_LIVE" }
    return ExcorYouTubePlaylistVideo(
        videoId = id,
        title = title,
        channelName = channelPart?.path("text", "content")?.let { (it as? JsonPrimitive)?.contentOrNull },
        channelId = channelBrowseId(channelPart),
        durationSeconds = duration,
        viewsText = texts.firstOrNull { "view" in it || "watching" in it },
        publishedText = texts.firstOrNull { "view" !in it && "watching" !in it },
        thumbnails = (image?.path("image", "sources") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { t ->
            val url = t.textAt("url") ?: return@mapNotNull null
            val parsed = runCatching { Url(url) }.getOrNull() ?: return@mapNotNull null
            if (parsed.protocol.name != "https" || parsed.host.lowercase() != "i.ytimg.com") return@mapNotNull null
            ExcorArtwork(url, t.textAt("width")?.toIntOrNull(), t.textAt("height")?.toIntOrNull())
        },
        isLive = live,
    )
}

/** "by Name" with its channel, from the page header's metadata rows. */
private fun ownerOf(metadata: JsonElement?): Pair<String, String?>? {
    val rows = ((metadata as? JsonObject)?.path("contentMetadataViewModel", "metadataRows") as? JsonArray).orEmpty()
    for (part in rows.flatMap { ((it as? JsonObject)?.get("metadataParts") as? JsonArray).orEmpty() }.mapNotNull { it as? JsonObject }) {
        val text = ((part.path("avatarStack", "avatarStackViewModel", "text") ?: part["text"]) as? JsonObject) ?: continue
        val content = text.textAt("content") ?: continue
        if (!content.startsWith("by ")) continue
        return content.removePrefix("by ").trim() to channelBrowseId(JsonObject(mapOf("text" to text)))
    }
    return null
}

internal fun channelBrowseId(part: JsonObject?): String? {
    val runs = part?.path("text", "commandRuns") as? JsonArray ?: return null
    return runs.firstNotNullOfOrNull { run ->
        ((run as? JsonObject)?.path("onTap", "innertubeCommand", "browseEndpoint", "browseId") as? JsonPrimitive)?.contentOrNull
    }?.takeIf(CHANNEL_ID::matches)
}

internal fun continuationOf(item: JsonObject): String? {
    val token = item.path("continuationItemViewModel", "continuationCommand", "innertubeCommand", "continuationCommand", "token")
        ?: item.path("continuationItemRenderer", "continuationEndpoint", "continuationCommand", "token")
    return (token as? JsonPrimitive)?.contentOrNull?.takeIf { it.length <= MAX_CONTINUATION && it.all { c -> c.isLetterOrDigit() || c in "_-=%" } }
}

private fun textOf(value: JsonElement): String? {
    val o = value as? JsonObject ?: return null
    o.textAt("simpleText")?.let { return it }
    return (o["runs"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.textAt("text") }?.joinToString("")?.takeIf(String::isNotBlank)
}
