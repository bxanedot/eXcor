package com.vyonex

import com.vyonex.internal.commentPageOf
import com.vyonex.internal.commentsTokenOf
import com.vyonex.internal.countOf
import com.vyonex.internal.newestFirstTokenOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** On the captured comments of dQw4w9WgXcQ, with every author anonymized by the capture tool. */
class CommentsTest {
    @Test
    fun aPageOfCommentsWithTheirAuthorsCountsAndReplies() {
        val page = commentPageOf(fixtureJson("next-comments.json"))
        assertEquals(20, page.comments.size)
        val first = page.comments.first()
        assertEquals("Let’s put the meme aside, this is actually a banging song.", first.text)
        assertEquals("@author01" to "UCxxxxxxxxxxxxxxAUTHOR01", first.authorName to first.authorChannelId)
        assertEquals("6 years ago", first.publishedTime)
        assertEquals(220_000L to 604L, first.likeCount to first.replyCount)
        assertNotNull(first.repliesContinuation)
        assertNotNull(page.continuation)
        assertEquals(2_460_829L, page.totalCount)
        assertEquals(page.comments.size, page.comments.map { it.commentId }.toSet().size)
    }

    @Test
    fun repliesComeAsBareViewModels() {
        val replies = commentPageOf(fixtureJson("next-comment-replies.json"))
        assertEquals(9, replies.comments.size)
        assertTrue(replies.comments.all { it.text.isNotEmpty() && it.repliesContinuation == null }, "${replies.comments}")
        assertNull(replies.totalCount)
    }

    @Test
    fun theWatchNextResponseNamesTheCommentSectionAndItsSortOrder() {
        assertNotNull(commentsTokenOf(fixtureJson("next-watch.json")))
        // The header's second entry ("Newest" in English) is taken by place.
        assertNotNull(newestFirstTokenOf(fixtureJson("next-comments.json")))
        // A response without a comment section (comments turned off).
        assertNull(commentsTokenOf(JsonObject(emptyMap())))
    }

    @Test
    fun countsInYouTubesWords() {
        assertEquals(604L, countOf("604"))
        assertEquals(2_460_829L, countOf("2,460,829 Comments"))
        assertEquals(220_000L, countOf("220K likes"))
        assertEquals(1_200_000L, countOf("1.2M"))
        assertNull(countOf("no count"))
        assertNull(countOf(null))
    }

    @Test
    fun commentsThroughTheEngine() = runTest {
        val bodies = mutableListOf<String>()
        val engine = fixtureEngine(FakeHost(), next = { body ->
            bodies += body
            fixture(if ("\"continuation\"" in body) "next-comments.json" else "next-watch.json")
        })
        val page = engine.comments("dQw4w9WgXcQ", newestFirst = false)
        assertEquals(20, page.comments.size)
        assertEquals(2, bodies.size)
        // None of these is pinned, hearted by the uploader, or written by them (their toolbar states say UNHEARTED).
        assertFalse(page.comments.any { it.isPinned || it.isHearted || it.authorIsUploader })
        // Newest first takes one more request, with the header's sort token.
        engine.comments("dQw4w9WgXcQ", newestFirst = true)
        assertEquals(5, bodies.size)
        assertTrue(newestFirstTokenOf(fixtureJson("next-comments.json"))!! in bodies.last())
    }
}
