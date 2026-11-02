package com.excor

import com.excor.catalog.ExcorArtwork
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * A YouTube channel from InnerTube browse: its details and a page of its uploads (the Videos tab). Continuation pages
 * carry only [videos] and the next [continuation]. Counts are YouTube's display texts, which it rounds.
 */
data class ExcorYouTubeChannel(
    val channelId: String,
    val title: String?,
    val description: String?,
    val handle: String?,
    val subscribersText: String?,
    val videoCountText: String?,
    val keywords: List<String>,
    val isFamilySafe: Boolean?,
    val availableCountries: Set<String>?,
    val avatars: List<ExcorArtwork>,
    /** The channel's tab titles, such as Videos, Shorts, Releases, Playlists. */
    val tabs: List<String>,
    val videos: List<ExcorYouTubePlaylistVideo>,
    val continuation: String?,
    /** The channel's playlists, filled when the Playlists tab was asked for. */
    val playlists: List<ExcorYouTubePlaylistResult> = emptyList(),
)

/** Channel tabs eXcor reads, matched by the tab's address so YouTube's localized titles do not matter. */
enum class ExcorYouTubeChannelTab(internal val path: String) { VIDEOS("videos"), SHORTS("shorts"), LIVE("streams"), PLAYLISTS("playlists") }

/**
 * A channel's About panel. Texts are YouTube's display texts in the session's language; [viewCount] is read from its
 * full, unrounded view count. [country] is present only when the channel shows one.
 */
data class ExcorYouTubeChannelAbout(
    val channelId: String,
    val description: String?,
    val subscribersText: String?,
    val videoCountText: String?,
    val viewCount: Long?,
    /** Such as "Joined Sep 10, 2007". */
    val joinedText: String?,
    val country: String?,
    val links: List<ExcorYouTubeChannelLink>,
)

/** A link the channel lists, with its destination taken out of YouTube's redirect. */
data class ExcorYouTubeChannelLink(val title: String, val url: String)

internal class ChannelHome(val channel: ExcorYouTubeChannel, val tabParams: Map<ExcorYouTubeChannelTab, String>, val aboutToken: String? = null)

/** The channel home page: details and the Videos tab's browse params. Null when it is not a channel page. */
internal fun decodeChannelHome(source: String, channelId: String): ChannelHome? {
    val root = parseObject(source) ?: return null
    val meta = root.path("metadata", "channelMetadataRenderer") as? JsonObject ?: return null
    if (meta.textAt("externalId") != channelId) return null
    val tabs = (root.path("contents", "twoColumnBrowseResultsRenderer", "tabs") as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonObject)?.get("tabRenderer") as? JsonObject }
    val tabParams = tabs.mapNotNull { tab ->
        val url = tab.path("endpoint", "commandMetadata", "webCommandMetadata", "url")?.let { (it as? JsonPrimitive)?.contentOrNull } ?: return@mapNotNull null
        val kind = ExcorYouTubeChannelTab.entries.firstOrNull { url.substringBefore('?').endsWith("/${it.path}") } ?: return@mapNotNull null
        val params = tab.path("endpoint", "browseEndpoint", "params")?.let { (it as? JsonPrimitive)?.contentOrNull }
            ?.takeIf { it.length <= 256 && it.all { c -> c.isLetterOrDigit() || c in "_-=%" } } ?: return@mapNotNull null
        kind to params
    }.toMap()
    val headerTexts = ((root.path("header", "pageHeaderRenderer", "content", "pageHeaderViewModel", "metadata", "contentMetadataViewModel", "metadataRows") as? JsonArray).orEmpty())
        .flatMap { ((it as? JsonObject)?.get("metadataParts") as? JsonArray).orEmpty() }
        .mapNotNull { (it as? JsonObject)?.path("text", "content")?.let { c -> (c as? JsonPrimitive)?.contentOrNull } }
    val vanity = meta.textAt("vanityChannelUrl")?.substringAfterLast('/')?.takeIf { it.startsWith("@") }
    val channel = ExcorYouTubeChannel(
        channelId = channelId,
        title = meta.textAt("title"),
        description = meta.textAt("description")?.trim()?.takeIf(String::isNotEmpty),
        handle = vanity ?: headerTexts.firstOrNull { it.startsWith("@") },
        subscribersText = headerTexts.firstOrNull { "subscriber" in it },
        videoCountText = headerTexts.firstOrNull { "video" in it },
        keywords = keywordsOf(meta.textAt("keywords")),
        isFamilySafe = (meta["isFamilySafe"] as? JsonPrimitive)?.booleanOrNull,
        availableCountries = (meta["availableCountryCodes"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.toSet(),
        avatars = ((meta.path("avatar", "thumbnails") as? JsonArray).orEmpty()).mapNotNull { it as? JsonObject }.mapNotNull { t ->
            val url = t.textAt("url") ?: return@mapNotNull null
            val parsed = runCatching { Url(url) }.getOrNull() ?: return@mapNotNull null
            if (parsed.protocol.name != "https" || parsed.host.lowercase() !in setOf("yt3.ggpht.com", "yt3.googleusercontent.com")) return@mapNotNull null
            ExcorArtwork(url, t.textAt("width")?.toIntOrNull(), t.textAt("height")?.toIntOrNull())
        },
        tabs = tabs.mapNotNull { it.textAt("title") },
        videos = emptyList(),
        continuation = null,
    )
    // The header's "...more" opens an engagement panel whose only item is a continuation that loads the About panel.
    val header = root.path("header", "pageHeaderRenderer", "content", "pageHeaderViewModel")
    val aboutToken = firstValue(firstValue(header, "showEngagementPanelEndpoint"), "continuationCommand")
        ?.let { ((it as? JsonObject)?.get("token") as? JsonPrimitive)?.contentOrNull }
        ?.takeIf { it.length <= MAX_CONTINUATION && it.all { c -> c.isLetterOrDigit() || c in "_-=%" } }
    return ChannelHome(channel, tabParams, aboutToken)
}

/** The About panel continuation; null when it is not this channel's About panel. */
internal fun decodeChannelAbout(source: String, channelId: String): ExcorYouTubeChannelAbout? {
    val root = parseObject(source) ?: return null
    val about = firstValue(root["onResponseReceivedEndpoints"], "aboutChannelViewModel") as? JsonObject ?: return null
    if (about.textAt("channelId") != channelId) return null
    val links = (about["links"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("channelExternalLinkViewModel") as? JsonObject }.mapNotNull { l ->
        val title = l.path("title", "content")?.let { (it as? JsonPrimitive)?.contentOrNull } ?: return@mapNotNull null
        val target = ((l.path("link", "commandRuns") as? JsonArray)?.firstOrNull() as? JsonObject)
            ?.path("onTap", "innertubeCommand", "urlEndpoint", "url")?.let { (it as? JsonPrimitive)?.contentOrNull } ?: return@mapNotNull null
        ExcorYouTubeChannelLink(title, linkDestination(target) ?: return@mapNotNull null)
    }
    return ExcorYouTubeChannelAbout(
        channelId = channelId,
        description = about.textAt("description")?.trim()?.takeIf(String::isNotEmpty),
        subscribersText = about.textAt("subscriberCountText"),
        videoCountText = about.textAt("videoCountText"),
        viewCount = about.textAt("viewCountText")?.filter(Char::isDigit)?.takeIf(String::isNotEmpty)?.toLongOrNull(),
        joinedText = about.path("joinedDateText", "content")?.let { (it as? JsonPrimitive)?.contentOrNull },
        country = about.textAt("country"),
        links = links,
    )
}

/**
 * Off-site links go through youtube.com/redirect with the destination in `q`; the redirect's one-time token is
 * dropped. YouTube's own links come direct. Only http(s) destinations are kept.
 */
private fun linkDestination(target: String): String? {
    val url = runCatching { Url(target) }.getOrNull() ?: return null
    val host = url.host.lowercase()
    val destination = if ((host == "www.youtube.com" || host == "youtube.com") && url.encodedPath == "/redirect") url.parameters["q"] ?: return null else target
    val parsed = runCatching { Url(destination) }.getOrNull() ?: return null
    return destination.takeIf { parsed.protocol.name in setOf("http", "https") && parsed.host.isNotEmpty() && it.length <= 2_048 }
}

/** The first value stored under [key] anywhere below [node], depth first. */
private fun firstValue(node: JsonElement?, key: String, depth: Int = 0): JsonElement? {
    if (depth > 40) return null
    return when (node) {
        is JsonObject -> node[key] ?: node.values.firstNotNullOfOrNull { firstValue(it, key, depth + 1) }
        is JsonArray -> node.firstNotNullOfOrNull { firstValue(it, key, depth + 1) }
        else -> null
    }
}

internal class ChannelTabPage(val videos: List<ExcorYouTubePlaylistVideo>, val playlists: List<ExcorYouTubePlaylistResult>, val continuation: String?)

/**
 * A channel tab page (a rich grid for Videos, Shorts and Live; a grid inside a section list for Playlists) or a
 * continuation of one.
 */
internal fun decodeChannelTab(source: String): ChannelTabPage? {
    val root = parseObject(source) ?: return null
    val selected = (root.path("contents", "twoColumnBrowseResultsRenderer", "tabs") as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonObject)?.get("tabRenderer") as? JsonObject }
        .firstOrNull { (it["selected"] as? JsonPrimitive)?.booleanOrNull == true }
    val grid = selected?.path("content", "sectionListRenderer", "contents")?.let { sections ->
        (sections as? JsonArray).orEmpty().firstNotNullOfOrNull { s ->
            ((s as? JsonObject)?.path("itemSectionRenderer", "contents") as? JsonArray)?.firstNotNullOfOrNull { (it as? JsonObject)?.path("gridRenderer", "items") as? JsonArray }
        }
    }
    val items = selected?.path("content", "richGridRenderer", "contents") as? JsonArray
        ?: grid
        ?: ((root["onResponseReceivedActions"] as? JsonArray)?.firstOrNull() as? JsonObject)
            ?.path("appendContinuationItemsAction", "continuationItems") as? JsonArray
        ?: return null
    val objects = items.mapNotNull { it as? JsonObject }
    val contents = objects.map { (it.path("richItemRenderer", "content") as? JsonObject) ?: it }
    val videos = contents.mapNotNull { c ->
        (c["lockupViewModel"] as? JsonObject)?.let(::playlistVideoOf) ?: (c["shortsLockupViewModel"] as? JsonObject)?.let(::shortOf)
    }
    val playlists = contents.mapNotNull { c ->
        val l = c["lockupViewModel"] as? JsonObject ?: return@mapNotNull null
        if (l.textAt("contentType") != "LOCKUP_CONTENT_TYPE_PLAYLIST") return@mapNotNull null
        val id = l.textAt("contentId")?.takeIf(PLAYLIST_ID::matches) ?: return@mapNotNull null
        val title = l.path("metadata", "lockupMetadataViewModel", "title", "content")?.let { (it as? JsonPrimitive)?.contentOrNull } ?: return@mapNotNull null
        ExcorYouTubePlaylistResult(id, title, id.startsWith("RD"))
    }
    return ChannelTabPage(videos, playlists, objects.firstNotNullOfOrNull(::continuationOf))
}

/** A Short: its ID from the tap command, title and views from the overlay. Shorts show no duration. */
internal fun shortOf(s: JsonObject): ExcorYouTubePlaylistVideo? {
    val id = s.path("onTap", "innertubeCommand", "reelWatchEndpoint", "videoId")?.let { (it as? JsonPrimitive)?.contentOrNull }?.takeIf(VIDEO_ID::matches) ?: return null
    val title = s.path("overlayMetadata", "primaryText", "content")?.let { (it as? JsonPrimitive)?.contentOrNull } ?: return null
    val thumbs = ((s.path("thumbnailViewModel", "image", "sources") ?: s.path("onTap", "innertubeCommand", "reelWatchEndpoint", "thumbnail", "thumbnails")) as? JsonArray)
        .orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { t ->
            val url = t.textAt("url") ?: return@mapNotNull null
            val parsed = runCatching { Url(url) }.getOrNull() ?: return@mapNotNull null
            if (parsed.protocol.name != "https" || parsed.host.lowercase() != "i.ytimg.com") return@mapNotNull null
            ExcorArtwork(url, t.textAt("width")?.toIntOrNull(), t.textAt("height")?.toIntOrNull())
        }
    return ExcorYouTubePlaylistVideo(
        videoId = id,
        title = title,
        channelName = null,
        channelId = null,
        durationSeconds = null,
        viewsText = s.path("overlayMetadata", "secondaryText", "content")?.let { (it as? JsonPrimitive)?.contentOrNull },
        publishedText = null,
        thumbnails = thumbs,
    )
}

/** YouTube's keyword string: quoted phrases and single words, separated by spaces. */
internal fun keywordsOf(value: String?): List<String> =
    Regex("\"([^\"]+)\"|(\\S+)").findAll(value.orEmpty()).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList()

private fun parseObject(source: String): JsonObject? =
    if (source.length > 4_000_000) null else runCatching { Json.parseToJsonElement(source) }.getOrNull() as? JsonObject
