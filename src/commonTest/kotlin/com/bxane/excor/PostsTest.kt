package com.bxane.excor

import com.bxane.excor.ExcorResultKind.VIDEO
import com.bxane.excor.internal.Engine
import com.bxane.excor.internal.postOf
import com.bxane.excor.internal.postPageMoreOf
import com.bxane.excor.internal.postPageOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostsTest {
    @Test
    fun postsFromCapture() {
        val page = postPageOf(fixtureJson("browse-channel-posts.json"))
        assertEquals(10, page.posts.size)
        assertNotNull(page.continuation)
        val first = page.posts[0]
        assertEquals("Ugkx6HzKUems4roLJrhsxgkRwC0sz_cHiVHX", first.postId)
        assertTrue(first.text!!.startsWith("We’re excited to announce that Rick is bringing his spectacular Swinging Christmas"))
        // Every run, link texts too, in order.
        assertTrue(first.text!!.endsWith("10am BST.\n\nLet’s swing into Christmas together!\n\nTeam Rick"))
        assertTrue("https://gigst.rs/RASC26SignUp" in first.text!!)
        // The link out, with YouTube's redirect and the trailing no-break space taken off.
        assertEquals(listOf("https://gigst.rs/RASC26SignUp"), first.links)
        assertEquals(listOf<Any?>("1 month ago", 4500L), listOf(first.publishedText, first.likeCount))
        assertEquals(1, first.images.size)
        assertEquals(1080, first.images[0].width)
        assertTrue(first.images[0].url.startsWith("https://yt3.ggpht.com/"))
        assertNull(first.video)
        // A post with a video attached.
        val video = assertNotNull(page.posts[1].video)
        assertEquals(VIDEO to "yBwD4iYcWC4", video.kind to video.id)
        assertEquals(listOf<Any?>(243L, emptyList<ExcorThumbnail>()), listOf(page.posts[1].likeCount, page.posts[1].images))
        // Several images in one post: each at its largest.
        assertEquals(2, page.posts.single { it.postId.startsWith("Ugkxn2vT6vGp") }.images.size)
        assertTrue(page.posts.flatMap { it.images }.all { it.url.startsWith("https://") })
    }

    @Test
    fun theNextPageFromCapture() {
        val more = postPageMoreOf(fixtureJson("browse-channel-posts-continuation.json"))
        assertEquals(10, more.posts.size)
        assertEquals("UgkxhJ9x5rhrIFXiR3gxUAc_mBaUbsGxl5rW", more.posts[0].postId)
        assertNotNull(more.continuation)
    }

    @Test
    fun linksAndCountsAsWritten() {
        val post = assertNotNull(postOf(Json.parseToJsonElement("""{"postId":"P","contentText":{"runs":[
            {"text":"see "},
            {"text":"this","navigationEndpoint":{"watchEndpoint":{"videoId":"dQw4w9WgXcQ"}}},
            {"text":" and ","navigationEndpoint":{"urlEndpoint":{"url":"https://example.org/a"}}},
            {"text":"x","navigationEndpoint":{"urlEndpoint":{"url":"https://www.youtube.com/redirect?event=backstage_event&q=%20%C2%A0"}}}]},
            "voteCount":{"simpleText":"12"},
            "backstageAttachment":{"playlistRenderer":{"playlistId":"PL1"}}}""")))
        assertEquals("see this and x", post.text)
        assertEquals(listOf("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "https://example.org/a"), post.links)
        assertEquals(listOf<Any?>(12L, "PL1", null), listOf(post.likeCount, post.playlistId, post.publishedText))
        assertNull(postOf(Json.parseToJsonElement("""{"contentText":{}}""")))
    }

    @Test
    fun postsAreAskedForWithTheTabsOwnParams() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = Engine(FakeHost(), tokenProvider = null, store = null, http = HttpClient(MockEngine { request ->
            requests += request
            val body = (request.body as TextContent).text
            val file = when {
                "\"params\"" in body -> "browse-channel-posts.json"
                "\"continuation\"" in body -> "browse-channel-posts-continuation.json"
                else -> "browse-channel.json"
            }
            respond(fixture(file), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }))
        val page = engine.channelPosts("UCuAXFkgsw1L7xaCfnd5JJOw")
        assertEquals(10, page.posts.size)
        // The channel page first, for the Posts tab's params, which then ask for the tab.
        val params = Regex(""""url":"/@[^"/]+/posts".*?"params":"([^"]+)"""").find(fixture("browse-channel.json"))!!.groupValues[1]
        assertTrue("\"params\":\"$params\"" in (requests[1].body as TextContent).text)
        assertEquals(10, engine.channelPostsMore(page.continuation!!).posts.size)
        // The params are kept: the second channel's posts would ask again, the same channel's do not.
        engine.channelPosts("UCuAXFkgsw1L7xaCfnd5JJOw")
        assertEquals(4, requests.size)
    }
}
