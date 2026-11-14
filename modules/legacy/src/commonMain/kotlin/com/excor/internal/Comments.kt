package com.excor.internal

import com.excor.ExcorComment
import com.excor.ExcorCommentPage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Comments as yt-dlp 2026.10.21 reads them (_comment_entries, _extract_comment): threads in the continuation items,
// each pointing by key at entity payloads under frameworkUpdates, which hold the text, author and counts.

private val JsonElement?.items get() = (this as? JsonArray).orEmpty()

/** The comment section's first continuation in a watch-next response; null when comments are off. */
internal fun commentsTokenOf(next: JsonObject): String? =
    next.at("contents", "twoColumnWatchNextResults", "results", "results", "contents").items
        .map { it.at("itemSectionRenderer") }
        .firstOrNull { it.at("sectionIdentifier").str == "comment-item-section" }
        .at("contents").items.firstNotNullOfOrNull { tokenOf(it) }

/** A continuation entry's token: an item shown at the end, or a "show more" button (replies). */
private fun tokenOf(item: JsonElement?): String? {
    val renderer = item.at("continuationItemRenderer")
    return renderer.at("continuationEndpoint", "continuationCommand", "token").str
        ?: renderer.at("button", "buttonRenderer", "command", "continuationCommand", "token").str
}

private fun itemsOf(response: JsonObject) =
    listOf("onResponseReceivedEndpoints", "onResponseReceivedActions").flatMap { response.at(it).items }.flatMap {
        it.at("reloadContinuationItemsCommand", "continuationItems").items + it.at("appendContinuationItemsAction", "continuationItems").items
    }

/** The token that asks for the same comments newest first: the header's second sort entry (by place, not by its label). */
internal fun newestFirstTokenOf(response: JsonObject): String? =
    itemsOf(response).firstNotNullOfOrNull { it.at("commentsHeaderRenderer", "sortMenu", "sortFilterSubMenuRenderer", "subMenuItems") }
        .items.getOrNull(1).at("serviceEndpoint", "continuationCommand", "token").str

/** A page of comments or replies. */
internal fun commentPageOf(response: JsonObject): ExcorCommentPage {
    val entities = response.at("frameworkUpdates", "entityBatchUpdate", "mutations").items
        .mapNotNull { m -> m.at("entityKey").str?.let { it to m.at("payload") } }.toMap()
    val items = itemsOf(response)
    val comments = items.mapNotNull { item ->
        val thread = item.at("commentThreadRenderer")
        // A thread on a page of comments, a bare view model on a page of replies.
        val view = thread.at("commentViewModel", "commentViewModel") ?: item.at("commentViewModel")
        val comment = entities[view.at("commentKey").str].at("commentEntityPayload") ?: return@mapNotNull null
        val toolbar = entities[view.at("toolbarStateKey").str].at("engagementToolbarStateEntityPayload")
        val author = comment.at("author")
        ExcorComment(
            commentId = comment.at("properties", "commentId").str ?: return@mapNotNull null,
            text = comment.at("properties", "content", "content").str.orEmpty(),
            authorName = author.at("displayName").str,
            authorChannelId = author.at("channelId").str?.takeIf { it.startsWith("UC") },
            authorAvatarUrl = author.at("avatarThumbnailUrl").str,
            authorIsUploader = author.at("isCreator").bool == true,
            authorIsVerified = author.at("isVerified").bool == true,
            publishedTime = comment.at("properties", "publishedTime").str,
            likeCount = countOf(comment.at("toolbar", "likeCountA11y").str),
            replyCount = countOf(comment.at("toolbar", "replyCount").str),
            isPinned = view.at("pinnedText").str != null,
            isHearted = toolbar.at("heartState").str == "TOOLBAR_HEART_STATE_HEARTED",
            repliesContinuation = thread.at("replies", "commentRepliesRenderer", "contents").items.firstNotNullOfOrNull { tokenOf(it) },
        )
    }
    val count = items.firstNotNullOfOrNull { textOf(it.at("commentsHeaderRenderer", "countText")) }
    return ExcorCommentPage(
        comments = comments,
        continuation = items.lastOrNull()?.let(::tokenOf),
        totalCount = count?.filter(Char::isDigit)?.toLongOrNull(),
    )
}

/** yt-dlp parse_count for YouTube's English counts: "604", "2,460,829", "220K likes", "1.2M". */
internal fun countOf(text: String?): Long? {
    val m = Regex("""([\d,]+(?:\.\d+)?)\s*([KMB])?""", RegexOption.IGNORE_CASE).find(text ?: return null) ?: return null
    val number = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
    val scale = when (m.groupValues[2].uppercase()) { "K" -> 1e3; "M" -> 1e6; "B" -> 1e9; else -> 1.0 }
    return kotlin.math.round(number * scale).toLong()
}
