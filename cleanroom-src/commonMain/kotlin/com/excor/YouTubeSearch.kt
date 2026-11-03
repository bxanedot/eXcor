package com.excor

import com.excor.catalog.ExcorArtwork
import com.excor.music.ExcorMusicTrack
import io.ktor.http.Url
import io.ktor.http.decodeURLQueryComponent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** One page of YouTube search results; pass [continuation] back for the next page. */
data class ExcorYouTubeSearchPage(
    val videos: List<ExcorYouTubeVideoResult>,
    val channels: List<ExcorYouTubeChannelResult>,
    val playlists: List<ExcorYouTubePlaylistResult>,
    val continuation: String?,
    val estimatedResults: Long? = null,
    /** Shorts from the page's Shorts shelves. Shorts show no duration or channel there. */
    val shorts: List<ExcorYouTubePlaylistVideo> = emptyList(),
    /** YouTube's filter dialog for this query, as YouTube offers it after the filters already applied. */
    val filters: List<ExcorYouTubeSearchFilter> = emptyList(),
)

/**
 * One option from YouTube's search filter dialog, such as group "Type", label "Channels". Pass [params] to
 * `search(query, filterParams = ...)`; it is null for an option that is already [selected] or is [disabled] by the
 * current filters. YouTube builds each [params] to include the filters already applied, so options combine by
 * picking them from successive pages. [group] and [label] are YouTube's display text in the session's language.
 */
data class ExcorYouTubeSearchFilter(val group: String, val label: String, val params: String?, val selected: Boolean, val disabled: Boolean)

/** [viewCount] and [durationSeconds] are null when YouTube shows none (live streams, premieres). */
data class ExcorYouTubeVideoResult(
    val videoId: String,
    val title: String,
    val channelName: String?,
    val channelId: String?,
    val durationSeconds: Long?,
    val viewCount: Long?,
    /** YouTube's relative text, such as "12 years ago"; not a date. */
    val publishedText: String?,
    val isLive: Boolean,
    val officialArtistChannel: Boolean,
    val thumbnails: List<ExcorArtwork>,
) {
    /** The result as a music track, read the same way as [ExcorVideoInfo.asMusicTrack]. */
    fun asMusicTrack(): ExcorMusicTrack = youtubeMusicTrack(videoId, title, channelName, channelId, durationSeconds, thumbnails)
}

data class ExcorYouTubeChannelResult(
    val channelId: String,
    val title: String,
    val handle: String?,
    /** YouTube's display text, such as "7.21M subscribers". */
    val subscribersText: String?,
    val thumbnails: List<ExcorArtwork>,
)

/** [isMix] marks YouTube's generated mixes (IDs starting with "RD"), which differ per listener. */
data class ExcorYouTubePlaylistResult(val playlistId: String, val title: String, val isMix: Boolean)

// A continuation token already carries the query's filters, so params go only with a first page.
internal fun buildSearchRequest(query: String?, continuation: String?, session: ExcorSession, filterParams: String? = null): PlayerRequest = innertubeRequest("search", session) {
    if (continuation != null) {
        put("continuation", continuation)
    } else {
        put("query", query!!)
        if (filterParams != null) put("params", filterParams)
    }
}

/** A first page (`contents`) or a continuation (`onResponseReceivedCommands`); null when it is neither. */
internal fun decodeSearchPage(source: String): ExcorYouTubeSearchPage? {
    if (source.length > 4_000_000) return null
    val root = runCatching { Json.parseToJsonElement(source) }.getOrNull() as? JsonObject ?: return null
    val sections = root.path("contents", "twoColumnSearchResultsRenderer", "primaryContents", "sectionListRenderer", "contents") as? JsonArray
        ?: ((root["onResponseReceivedCommands"] as? JsonArray)?.firstOrNull() as? JsonObject)
            ?.path("appendContinuationItemsAction", "continuationItems") as? JsonArray
        ?: return null
    val videos = mutableListOf<ExcorYouTubeVideoResult>()
    val channels = mutableListOf<ExcorYouTubeChannelResult>()
    val playlists = mutableListOf<ExcorYouTubePlaylistResult>()
    val shorts = mutableListOf<ExcorYouTubePlaylistVideo>()
    var continuation: String? = null
    for (section in sections.mapNotNull { it as? JsonObject }) {
        (section.path("continuationItemRenderer", "continuationEndpoint", "continuationCommand", "token") as? JsonPrimitive)?.contentOrNull
            ?.takeIf { it.length <= MAX_CONTINUATION && TOKEN.matches(it) }?.let { continuation = it }
        val items = section.path("itemSectionRenderer", "contents") as? JsonArray ?: continue
        for (item in items.mapNotNull { it as? JsonObject }) {
            (item["videoRenderer"] as? JsonObject)?.let(::videoOf)?.let(videos::add)
            (item["channelRenderer"] as? JsonObject)?.let(::channelOf)?.let(channels::add)
            (item.path("officialCardViewModel", "header", "pageHeaderViewModel") as? JsonObject)?.let(::officialChannelOf)?.let(channels::add)
            (item["lockupViewModel"] as? JsonObject)?.let(::playlistOf)?.let(playlists::add)
            (item.path("gridShelfViewModel", "contents") as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("shortsLockupViewModel") as? JsonObject }
                .mapNotNull(::shortOf).let(shorts::addAll)
        }
    }
    return ExcorYouTubeSearchPage(videos, channels, playlists, continuation, root.textAt("estimatedResults")?.toLongOrNull(), shorts, filtersOf(root))
}

/** The filter dialog behind the header's Filters button; empty when the response has none (continuations). */
private fun filtersOf(root: JsonObject): List<ExcorYouTubeSearchFilter> {
    val groups = root.path("header", "searchHeaderRenderer", "searchFilterButton", "buttonRenderer", "command", "openPopupAction", "popup", "searchFilterOptionsDialogRenderer", "groups") as? JsonArray
        ?: return emptyList()
    return groups.mapNotNull { (it as? JsonObject)?.get("searchFilterGroupRenderer") as? JsonObject }.flatMap { g ->
        val group = runsText(g["title"]) ?: return@flatMap emptyList()
        (g["filters"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("searchFilterRenderer") as? JsonObject }.mapNotNull { f ->
            val status = f.textAt("status")
            // The dialog gives params URL-encoded, as in its /results link; the API takes them decoded.
            val params = (f.path("navigationEndpoint", "searchEndpoint", "params") as? JsonPrimitive)?.contentOrNull
                ?.let { runCatching { it.decodeURLQueryComponent() }.getOrNull() }?.takeIf(SEARCH_PARAMS::matches)
            ExcorYouTubeSearchFilter(group, runsText(f["label"]) ?: return@mapNotNull null, params, status == "FILTER_STATUS_SELECTED", status == "FILTER_STATUS_DISABLED")
        }
    }
}

private fun videoOf(v: JsonObject): ExcorYouTubeVideoResult? {
    val id = v.textAt("videoId")?.takeIf(VIDEO_ID::matches) ?: return null
    val owner = (v.path("ownerText", "runs") as? JsonArray)?.firstOrNull() as? JsonObject
    val badges = (v["badges"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.path("metadataBadgeRenderer", "label")?.let { l -> (l as? JsonPrimitive)?.contentOrNull } }
    val ownerBadges = (v["ownerBadges"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.path("metadataBadgeRenderer", "tooltip")?.let { l -> (l as? JsonPrimitive)?.contentOrNull } }
    val length = (v.path("lengthText", "simpleText") as? JsonPrimitive)?.contentOrNull
    val views = (v.path("viewCountText", "simpleText") as? JsonPrimitive)?.contentOrNull
    return ExcorYouTubeVideoResult(
        videoId = id,
        title = runsText(v["title"]) ?: return null,
        channelName = (owner?.get("text") as? JsonPrimitive)?.contentOrNull,
        channelId = (owner?.path("navigationEndpoint", "browseEndpoint", "browseId") as? JsonPrimitive)?.contentOrNull?.takeIf(CHANNEL_ID::matches),
        durationSeconds = length?.let(::clockSeconds),
        viewCount = views?.takeIf { it.any(Char::isDigit) && "watching" !in it }?.filter(Char::isDigit)?.toLongOrNull(),
        publishedText = (v.path("publishedTimeText", "simpleText") as? JsonPrimitive)?.contentOrNull,
        isLive = badges.any { it.equals("LIVE", ignoreCase = true) } || (length == null && views?.contains("watching") == true),
        officialArtistChannel = ownerBadges.any { it.contains("Official Artist", ignoreCase = true) },
        thumbnails = thumbnailsOf(v["thumbnail"], setOf("i.ytimg.com")),
    )
}

private fun channelOf(c: JsonObject): ExcorYouTubeChannelResult? {
    val handle = (c.path("subscriberCountText", "simpleText") as? JsonPrimitive)?.contentOrNull?.takeIf { it.startsWith("@") }
    return ExcorYouTubeChannelResult(
        channelId = c.textAt("channelId")?.takeIf(CHANNEL_ID::matches) ?: return null,
        title = (c.path("title", "simpleText") as? JsonPrimitive)?.contentOrNull ?: return null,
        handle = handle,
        // With a handle shown, YouTube moves the subscriber count into videoCountText.
        subscribersText = (c.path(if (handle != null) "videoCountText" else "subscriberCountText", "simpleText") as? JsonPrimitive)?.contentOrNull,
        thumbnails = thumbnailsOf(c["thumbnail"], setOf("yt3.ggpht.com", "yt3.googleusercontent.com")),
    )
}

// An artist's official card, which newer search pages show in place of a channelRenderer.
private fun officialChannelOf(h: JsonObject): ExcorYouTubeChannelResult? {
    val title = h.path("title", "dynamicTextViewModel") as? JsonObject ?: return null
    val parts = (h.path("metadata", "contentMetadataViewModel", "metadataRows") as? JsonArray).orEmpty()
        .flatMap { ((it as? JsonObject)?.get("metadataParts") as? JsonArray).orEmpty() }
        .mapNotNull { ((it as? JsonObject)?.path("text", "content") as? JsonPrimitive)?.contentOrNull }
    val image = h.path("image", "contentPreviewImageViewModel", "image", "sources")
    return ExcorYouTubeChannelResult(
        channelId = (title.path("rendererContext", "commandContext", "onTap", "innertubeCommand", "browseEndpoint", "browseId") as? JsonPrimitive)
            ?.contentOrNull?.takeIf(CHANNEL_ID::matches) ?: return null,
        title = (title.path("text", "content") as? JsonPrimitive)?.contentOrNull ?: return null,
        handle = parts.firstOrNull { it.startsWith("@") },
        subscribersText = parts.firstOrNull { !it.startsWith("@") },
        thumbnails = thumbnailsOf(image?.let { JsonObject(mapOf("thumbnails" to it)) }, setOf("yt3.ggpht.com", "yt3.googleusercontent.com")),
    )
}

private fun playlistOf(l: JsonObject): ExcorYouTubePlaylistResult? {
    if (l.textAt("contentType") != "LOCKUP_CONTENT_TYPE_PLAYLIST") return null
    val id = l.textAt("contentId")?.takeIf(PLAYLIST_ID::matches) ?: return null
    val title = (l.path("metadata", "lockupMetadataViewModel", "title", "content") as? JsonPrimitive)?.contentOrNull ?: return null
    return ExcorYouTubePlaylistResult(id, title, id.startsWith("RD"))
}

private fun thumbnailsOf(value: JsonElement?, hosts: Set<String>): List<ExcorArtwork> =
    ((value as? JsonObject)?.get("thumbnails") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { t ->
        val raw = t.textAt("url") ?: return@mapNotNull null
        val url = if (raw.startsWith("//")) "https:$raw" else raw
        val parsed = runCatching { Url(url) }.getOrNull() ?: return@mapNotNull null
        if (parsed.protocol.name != "https" || parsed.host.lowercase() !in hosts) return@mapNotNull null
        ExcorArtwork(url, t.textAt("width")?.toIntOrNull(), t.textAt("height")?.toIntOrNull())
    }

private fun runsText(value: JsonElement?): String? {
    val o = value as? JsonObject ?: return null
    (o["simpleText"] as? JsonPrimitive)?.contentOrNull?.let { return it }
    return (o["runs"] as? JsonArray)?.mapNotNull { ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull }?.joinToString("")?.takeIf(String::isNotBlank)
}

/** "5:40" or "1:02:03" in seconds; null for anything else. */
internal fun clockSeconds(text: String): Long? {
    val parts = text.trim().split(':')
    if (parts.size !in 2..3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
    return parts.fold(0L) { acc, p -> acc * 60 + p.toLong() }
}

internal fun JsonObject.path(vararg keys: String): JsonElement? =
    keys.fold(this as JsonElement?) { node, key -> (node as? JsonObject)?.get(key) }

internal fun JsonObject.textAt(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

internal val CHANNEL_ID = Regex("UC[A-Za-z0-9_-]{22}")
internal val PLAYLIST_ID = Regex("[A-Za-z0-9_-]{2,64}")
private val TOKEN = Regex("[A-Za-z0-9_=%-]+")
internal val SEARCH_PARAMS = Regex("[A-Za-z0-9_+/=-]{1,256}")
internal const val MAX_CONTINUATION = 8_192
