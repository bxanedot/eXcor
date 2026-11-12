package com.excor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Synthetic channel tests: do not need an external server or real player data. */
class BoundedResponseTest {
    @Test
    fun readsAtByteLimitAndRejectsOneExtraByte() = runTest {
        val client = HttpClient(MockEngine { request ->
            val content = if (request.url.encodedPath == "/at-limit") "abcde" else "abcdef"
            respond(content, HttpStatusCode.OK)
        })
        try {
            assertEquals(
                "abcde",
                readBoundedResponseText(client.get("https://example.test/at-limit"), 5),
            )
            assertNull(
                readBoundedResponseText(client.get("https://example.test/too-large"), 5),
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun rejectsChunkedBodyWithoutContentLength() = runTest {
        val client = HttpClient(MockEngine {
            respond("x".repeat(50_000), HttpStatusCode.OK)
        })
        try {
            assertNull(readBoundedResponseText(client.get("https://example.test/data"), 1024))
        } finally {
            client.close()
        }
    }

    @Test
    fun unicodeCharactersCountByUtf8Bytes() = runTest {
        val client = HttpClient(MockEngine {
            respond("éé", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/plain; charset=utf-8"))
        })
        try {
            assertEquals("éé", readBoundedResponseText(client.get("https://example.test/text"), 4))
        } finally {
            client.close()
        }
    }
}
