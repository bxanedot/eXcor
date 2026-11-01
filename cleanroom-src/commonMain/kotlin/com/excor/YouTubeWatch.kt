package com.excor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Videos YouTube suggests next to a video, from the watch page's InnerTube `next` response. */
data class ExcorYouTubeRelated(val videos: List<ExcorYouTubePlaylistVideo>, val continuation: String?)

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
    return ExcorYouTubeCommentsPage(comments, total, items.firstNotNullOfOrNull(::continuationOf))
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
