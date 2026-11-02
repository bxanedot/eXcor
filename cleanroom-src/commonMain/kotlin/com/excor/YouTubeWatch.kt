package com.excor

import com.excor.catalog.ExcorCatalogProvider
import com.excor.music.ExcorArtistRole
import com.excor.music.ExcorMusicArtist
import com.excor.music.ExcorMusicTrack
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Videos YouTube suggests next to a video, from the watch page's InnerTube `next` response. */
data class ExcorYouTubeRelated(val videos: List<ExcorYouTubePlaylistVideo>, val continuation: String?)

/** A chapter of a video; it runs until the next chapter's [startMs] or the end of the video. */
data class ExcorYouTubeChapter(val title: String, val startMs: Long)

/**
 * A song YouTube credits in a video's description ("Music" section). [credits] are YouTube's labelled credit lines as
 * shown, in the session's language, such as "Writers" to the writers' names.
 */
data class ExcorYouTubeSong(val title: String, val artist: String?, val album: String?, val credits: Map<String, String>) {
    /**
     * The song as a music track for [com.excor.music.ExcorMusic.matchAcrossProviders], keyed by the video it was
     * credited in. YouTube's credit is cleaner than a title split, but carries no ISRC, so matching still scores it as
     * text; pass the video's duration when the video is the song itself.
     */
    fun asMusicTrack(videoId: String, durationMs: Long? = null): ExcorMusicTrack = ExcorMusicTrack(
        provider = ExcorCatalogProvider.YOUTUBE_MUSIC,
        id = videoId,
        title = title,
        url = "https://www.youtube.com/watch?v=$videoId",
        artists = listOfNotNull(artist?.let { ExcorMusicArtist(ExcorCatalogProvider.YOUTUBE_MUSIC, null, it, ExcorArtistRole.PRIMARY) }),
        albumTitle = album,
        durationMs = durationMs,
    )
}

/** One comment. [text] can be empty (image-only comments). Counts are YouTube's display texts ("220K"), which it rounds. */
data class ExcorYouTubeComment(
    val commentId: String,
    val text: String,
    val authorName: String?,
    val authorChannelId: String?,
    val authorVerified: Boolean,
    val authorIsCreator: Boolean,
    val authorIsArtist: Boolean,
    val publishedText: String?,
    val likesText: String?,
    val replyCount: Int?,
    /** Pass to `Excor.comments(videoId, it)` for this comment's replies; null when it has none. */
    val repliesContinuation: String? = null,
)

data class ExcorYouTubeCommentsPage(
    val comments: List<ExcorYouTubeComment>,
    /** YouTube's total, such as "2,461,182 Comments"; only on the first page. */
    val totalText: String?,
    val continuation: String?,
    /** On the first page: pass to `Excor.comments(videoId, it)` for the same comments newest first. */
    val newestContinuation: String? = null,
)

internal fun buildNextRequest(videoId: String?, continuation: String?, session: ExcorSession): PlayerRequest = innertubeRequest("next", session) {
    if (continuation != null) put("continuation", continuation) else put("videoId", videoId!!)
}

/** Related videos from a first `next` response or a related continuation; null when it is neither. */
internal fun decodeRelated(source: String): ExcorYouTubeRelated? {
    val root = parse(source) ?: return null
    val items = root.path("contents", "twoColumnWatchNextResults", "secondaryResults", "secondaryResults", "results") as? JsonArray
        ?: continuationItems(root)
        ?: return null
    val objects = items.mapNotNull { it as? JsonObject }
    return ExcorYouTubeRelated(
        objects.mapNotNull { (it["lockupViewModel"] as? JsonObject)?.let(::playlistVideoOf) },
        objects.firstNotNullOfOrNull(::continuationOf),
    )
}

/**
 * Chapters from a first `next` response: the player bar's chapter markers, in start order. Empty for a video without
 * chapters; null when the response is not a watch page.
 */
internal fun decodeChapters(source: String): List<ExcorYouTubeChapter>? {
    val root = parse(source) ?: return null
    if (root.path("contents", "twoColumnWatchNextResults") !is JsonObject) return null
    val markers = (root.path("playerOverlays", "playerOverlayRenderer", "decoratedPlayerBarRenderer", "decoratedPlayerBarRenderer", "playerBar", "multiMarkersPlayerBarRenderer", "markersMap") as? JsonArray)
        .orEmpty().mapNotNull { it as? JsonObject }
    val chapters = markers.firstNotNullOfOrNull { it.path("value", "chapters") as? JsonArray } ?: return emptyList()
    return chapters.mapNotNull { (it as? JsonObject)?.get("chapterRenderer") as? JsonObject }.mapNotNull { c ->
        val title = c.path("title", "simpleText")?.let { (it as? JsonPrimitive)?.contentOrNull }?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
        val start = c.textAt("timeRangeStartMillis")?.toLongOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
        ExcorYouTubeChapter(title, start)
    }.sortedBy { it.startMs }
}

/**
 * Songs from a first `next` response's structured description. The music card list is the one whose footer button
 * has the MUSIC icon, so its localized "Music" title does not matter. Empty when the video credits no songs; null when
 * the response is not a watch page.
 */
internal fun decodeSongs(source: String): List<ExcorYouTubeSong>? {
    val root = parse(source) ?: return null
    if (root.path("contents", "twoColumnWatchNextResults") !is JsonObject) return null
    val lists = (root["engagementPanels"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.flatMap { panel ->
        (panel.path("engagementPanelSectionListRenderer", "content", "structuredDescriptionContentRenderer", "items") as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.get("horizontalCardListRenderer") as? JsonObject }
    }
    val music = lists.firstOrNull { it.path("footerButton", "buttonViewModel", "iconName")?.let { i -> (i as? JsonPrimitive)?.contentOrNull } == "MUSIC" } ?: return emptyList()
    return (music["cards"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("videoAttributeViewModel") as? JsonObject }.mapNotNull { card ->
        val title = card.textAt("title")?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
        val dialog = ((card.path("overflowMenuOnTap", "innertubeCommand", "confirmDialogEndpoint", "content", "confirmDialogRenderer", "dialogMessages") as? JsonArray)?.firstOrNull() as? JsonObject)
        ExcorYouTubeSong(
            title = title,
            artist = card.textAt("subtitle")?.trim()?.takeIf(String::isNotEmpty),
            album = card.path("secondarySubtitle", "content")?.let { (it as? JsonPrimitive)?.contentOrNull }?.trim()?.takeIf(String::isNotEmpty),
            credits = creditsOf(dialog?.get("runs") as? JsonArray),
        )
    }
}

/** The credits dialog's runs: a bold "Label: " followed by plain value runs, entries separated by blank lines. */
private fun creditsOf(runs: JsonArray?): Map<String, String> {
    val credits = linkedMapOf<String, String>()
    val label = StringBuilder()
    val value = StringBuilder()
    fun flush() {
        val l = label.toString().trim().removeSuffix(":").trim()
        val v = value.toString().trim()
        if (l.isNotEmpty() && v.isNotEmpty() && credits.size < 32) credits[l.take(100)] = v.take(1_000)
        label.clear()
        value.clear()
    }
    for (run in runs.orEmpty().mapNotNull { it as? JsonObject }) {
        val text = run.textAt("text") ?: continue
        if ((run["bold"] as? JsonPrimitive)?.booleanOrNull == true) {
            if (value.isNotEmpty()) flush()
            label.append(text)
        } else {
            value.append(text)
        }
    }
    flush()
    return credits
}

/** The comments section's continuation token in a first `next` response; null when comments are off or absent. */
internal fun commentsTokenOf(source: String): String? {
    val contents = parse(source)?.path("contents", "twoColumnWatchNextResults", "results", "results", "contents") as? JsonArray ?: return null
    val section = contents.mapNotNull { (it as? JsonObject)?.get("itemSectionRenderer") as? JsonObject }
        .firstOrNull { it.textAt("sectionIdentifier") == "comment-item-section" } ?: return null
    return (section["contents"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.firstNotNullOfOrNull(::continuationOf)
}

/**
 * A comments page. Threads list comment keys in order; the comment text and author come from the entity store in
 * `frameworkUpdates`, matched by key.
 */
internal fun decodeComments(source: String): ExcorYouTubeCommentsPage? {
    val root = parse(source) ?: return null
    val endpoints = (root["onResponseReceivedEndpoints"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    if (endpoints.isEmpty()) return null
    val items = endpoints.flatMap { e ->
        ((e.path("reloadContinuationItemsCommand", "continuationItems") ?: e.path("appendContinuationItemsAction", "continuationItems")) as? JsonArray).orEmpty()
    }.mapNotNull { it as? JsonObject }
    val entities = ((root.path("frameworkUpdates", "entityBatchUpdate", "mutations") as? JsonArray).orEmpty())
        .mapNotNull { (it as? JsonObject)?.path("payload", "commentEntityPayload") as? JsonObject }
        .associateBy { it.textAt("key") }
    val comments = items.mapNotNull { item ->
        // Top-level pages wrap each comment in a thread; reply pages list commentViewModel items directly.
        val thread = item["commentThreadRenderer"] as? JsonObject
        val model = (thread?.path("commentViewModel", "commentViewModel") ?: item["commentViewModel"]) as? JsonObject
        val key = model?.textAt("commentKey") ?: return@mapNotNull null
        val repliesToken = ((thread?.path("replies", "commentRepliesRenderer", "contents")) as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }.firstNotNullOfOrNull(::continuationOf)
        val entity = entities[key] ?: return@mapNotNull null
        val properties = entity["properties"] as? JsonObject ?: return@mapNotNull null
        val author = entity["author"] as? JsonObject
        val toolbar = entity["toolbar"] as? JsonObject
        fun flag(name: String) = (author?.get(name) as? JsonPrimitive)?.booleanOrNull == true
        ExcorYouTubeComment(
            commentId = properties.textAt("commentId") ?: return@mapNotNull null,
            text = properties.path("content", "content")?.let { (it as? JsonPrimitive)?.contentOrNull } ?: return@mapNotNull null,
            authorName = author?.textAt("displayName"),
            authorChannelId = author?.textAt("channelId")?.takeIf(CHANNEL_ID::matches),
            authorVerified = flag("isVerified"),
            authorIsCreator = flag("isCreator"),
            authorIsArtist = flag("isArtist"),
            publishedText = properties.textAt("publishedTime"),
            likesText = toolbar?.textAt("likeCountNotliked")?.takeIf(String::isNotBlank),
            replyCount = toolbar?.textAt("replyCount")?.filter(Char::isDigit)?.toIntOrNull(),
            repliesContinuation = repliesToken,
        )
    }
    val header = items.firstNotNullOfOrNull { it["commentsHeaderRenderer"] as? JsonObject }
    val total = (header?.path("countText", "runs") as? JsonArray)?.mapNotNull { (it as? JsonObject)?.textAt("text") }?.joinToString("")
    // The sort menu lists Top, then Newest; titles are localized, so the position is what identifies Newest.
    val sortItems = (header?.path("sortMenu", "sortFilterSubMenuRenderer", "subMenuItems") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    val newest = sortItems.getOrNull(1)?.path("serviceEndpoint", "continuationCommand", "token")?.let { (it as? JsonPrimitive)?.contentOrNull }
        ?.takeIf { it.length <= MAX_CONTINUATION && it.all { c -> c.isLetterOrDigit() || c in "_-=%" } }
    return ExcorYouTubeCommentsPage(comments, total, items.firstNotNullOfOrNull(::continuationOf), newest)
}

private fun continuationItems(root: JsonObject): JsonArray? =
    (root["onResponseReceivedEndpoints"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.firstNotNullOfOrNull { e ->
        (e.path("appendContinuationItemsAction", "continuationItems") ?: e.path("reloadContinuationItemsCommand", "continuationItems")) as? JsonArray
    }

private fun parse(source: String): JsonObject? =
    if (source.length > 4_000_000) null else runCatching { Json.parseToJsonElement(source) }.getOrNull() as? JsonObject

/** Query completions from YouTube's suggestion service (JSONP-wrapped JSON: `[query, [[text, …], …], …]`). */
internal fun decodeSuggestions(source: String): List<String> {
    val start = source.indexOf('(')
    val end = source.lastIndexOf(')')
    val body = if (start >= 0 && end > start) source.substring(start + 1, end) else source
    val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonArray ?: return emptyList()
    return (root.getOrNull(1) as? JsonArray).orEmpty().mapNotNull { ((it as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.contentOrNull }
}
