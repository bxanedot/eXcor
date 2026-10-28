package com.vyonex.internal

import com.vyonex.VyonExPost
import com.vyonex.VyonExPostPage
import io.ktor.http.parseQueryString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Community posts as yt-dlp 2026.10.26 reads them (_tab.py _post_thread_entries, _post_thread_continuation_entries),
// and the image, like-count and link fields of YouTube's own Posts tab response (2026-10-28).

private val JsonElement?.items get() = (this as? JsonArray).orEmpty()

/** Where a run's link goes: a link out has YouTube's redirect taken off (its `q`), a video link is its watch URL. */
private fun linkOf(run: JsonElement?): String? {
    val endpoint = run.at("navigationEndpoint")
    endpoint.at("watchEndpoint", "videoId").str?.let { return "$YT_ORIGIN/watch?v=$it" }
    val url = endpoint.at("urlEndpoint", "url").str ?: return null
    if (!url.startsWith("$YT_ORIGIN/redirect?")) return url
    // The link text can carry a no-break space into the target.
    return parseQueryString(url.substringAfter('?'))["q"]?.trim()?.ifEmpty { null }
}

/** One post (`backstagePostRenderer`); null for anything else. */
internal fun postOf(renderer: JsonElement?): VyonExPost? {
    val postId = renderer.at("postId").str ?: return null
    val runs = renderer.at("contentText", "runs").items
    val attachment = renderer.at("backstageAttachment")
    val images = (listOfNotNull(attachment.at("backstageImageRenderer")) +
        attachment.at("postMultiImageRenderer", "images").items.mapNotNull { it.at("backstageImageRenderer") })
        .mapNotNull { image -> thumbnailsOf(image.at("image", "thumbnails")).maxByOrNull { it.width } }
        // Some come scheme-relative ("//yt3.ggpht.com/…").
        .map { if (it.url.startsWith("//")) it.copy(url = "https:${it.url}") else it }
    return VyonExPost(
        postId = postId,
        text = runs.mapNotNull { it.at("text").str }.joinToString("").ifEmpty { null },
        links = runs.mapNotNull(::linkOf).distinct(),
        publishedText = textOf(renderer.at("publishedTimeText")),
        // The label reads "4.5K likes"; the short text alone may be blank for few likes.
        likeCount = countOf(renderer.at("voteCount", "accessibility", "accessibilityData", "label").str ?: textOf(renderer.at("voteCount"))),
        images = images,
        video = webResultOf(attachment)?.takeIf { attachment.at("videoRenderer") != null },
        playlistId = attachment.at("playlistRenderer", "playlistId").str,
    )
}

private fun postsPageOf(entries: List<JsonElement>) = VyonExPostPage(
    posts = entries.mapNotNull { postOf(it.at("backstagePostThreadRenderer", "post", "backstagePostRenderer")) },
    continuation = entries.firstNotNullOfOrNull { it.at("continuationItemRenderer", "continuationEndpoint", "continuationCommand", "token").str },
)

/** The Posts tab of a channel's browse response. */
internal fun postPageOf(response: JsonObject): VyonExPostPage {
    val tab = response.at("contents", "twoColumnBrowseResultsRenderer", "tabs").items
        .mapNotNull { it.at("tabRenderer") }.firstOrNull { it.at("selected").bool == true }
    return postsPageOf(tab.at("content", "sectionListRenderer", "contents").items.flatMap { it.at("itemSectionRenderer", "contents").items })
}

/** The next page of posts. */
internal fun postPageMoreOf(response: JsonObject) =
    postsPageOf(response.at("onResponseReceivedEndpoints").items.plus(response.at("onResponseReceivedActions").items)
        .flatMap { it.at("appendContinuationItemsAction", "continuationItems").items })
