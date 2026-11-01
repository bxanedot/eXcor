package com.excor

import com.excor.catalog.ExcorArtwork
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
)

internal class ChannelHome(val channel: ExcorYouTubeChannel, val videosParams: String?)

/** The channel home page: details and the Videos tab's browse params. Null when it is not a channel page. */
internal fun decodeChannelHome(source: String, channelId: String): ChannelHome? {
    val root = parseObject(source) ?: return null
    val meta = root.path("metadata", "channelMetadataRenderer") as? JsonObject ?: return null
    if (meta.textAt("externalId") != channelId) return null
    val tabs = (root.path("contents", "twoColumnBrowseResultsRenderer", "tabs") as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonObject)?.get("tabRenderer") as? JsonObject }
    val videosParams = tabs.firstOrNull { it.textAt("title") == "Videos" }
        ?.path("endpoint", "browseEndpoint", "params")?.let { (it as? JsonPrimitive)?.contentOrNull }
        ?.takeIf { it.length <= 256 && it.all { c -> c.isLetterOrDigit() || c in "_-=%" } }
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
    return ChannelHome(channel, videosParams)
}

/** Uploads from the Videos tab page, or a continuation of it: videos and the next continuation token. */
internal fun decodeChannelVideos(source: String): Pair<List<ExcorYouTubePlaylistVideo>, String?>? {
    val root = parseObject(source) ?: return null
    val selected = (root.path("contents", "twoColumnBrowseResultsRenderer", "tabs") as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonObject)?.get("tabRenderer") as? JsonObject }
        .firstOrNull { (it["selected"] as? JsonPrimitive)?.booleanOrNull == true }
    val items = selected?.path("content", "richGridRenderer", "contents") as? JsonArray
        ?: ((root["onResponseReceivedActions"] as? JsonArray)?.firstOrNull() as? JsonObject)
            ?.path("appendContinuationItemsAction", "continuationItems") as? JsonArray
        ?: return null
    val objects = items.mapNotNull { it as? JsonObject }
    val videos = objects.mapNotNull { (it.path("richItemRenderer", "content", "lockupViewModel") as? JsonObject)?.let(::playlistVideoOf) }
    return videos to objects.firstNotNullOfOrNull(::continuationOf)
}

/** YouTube's keyword string: quoted phrases and single words, separated by spaces. */
internal fun keywordsOf(value: String?): List<String> =
    Regex("\"([^\"]+)\"|(\\S+)").findAll(value.orEmpty()).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList()

private fun parseObject(source: String): JsonObject? =
    if (source.length > 4_000_000) null else runCatching { Json.parseToJsonElement(source) }.getOrNull() as? JsonObject
