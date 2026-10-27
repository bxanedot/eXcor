package com.vyonex

import com.vyonex.internal.Engine
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DownloadTest {
    private val host = FakeHost()
    private val file = ByteArray(25) { it.toByte() }
    private val url = "https://r1.googlevideo.com/videoplayback?itag=251&c=VISIONOS"
    private fun format(contentLength: Long? = 25) =
        VyonExFormat(251, url, "audio/webm; codecs=\"opus\"", 136, contentLength, true, false, null, null, null)

    /** A media server that answers ranges of [file], or [status] when set. */
    private fun engine(ranges: MutableList<String>, status: HttpStatusCode? = null) = Engine(
        host,
        tokenProvider = null,
        http = HttpClient(MockEngine { request ->
            val range = request.headers[HttpHeaders.Range]!!
            ranges += range
            if (status != null) return@MockEngine respond(ByteArray(0), status)
            val (first, last) = range.removePrefix("bytes=").split('-').map { it.toInt() }
            val end = minOf(last, file.size - 1)
            respond(
                file.copyOfRange(first, end + 1),
                HttpStatusCode.PartialContent,
                headersOf(HttpHeaders.ContentRange, "bytes $first-$end/${file.size}"),
            )
        }),
        store = null,
    )

    private fun collect() = mutableListOf<Byte>().let { bytes -> bytes to VyonExSink { bytes += it.toList() } }

    @Test
    fun downloadsInRangedChunks() = runTest {
        val ranges = mutableListOf<String>()
        val (bytes, sink) = collect()
        val progress = mutableListOf<Pair<Long, Long?>>()
        val end = engine(ranges).download(format(), mapOf("User-Agent" to "ua"), sink, 0, { n, total -> progress += n to total }, chunkBytes = 10)
        assertEquals(25L, end)
        assertEquals(listOf("bytes=0-9", "bytes=10-19", "bytes=20-24"), ranges)
        assertContentEquals(file, bytes.toByteArray())
        assertEquals<List<Pair<Long, Long?>>>(listOf(10L to 25L, 20L to 25L, 25L to 25L), progress)
    }

    @Test
    fun resumesFromAnOffsetAndLearnsTheLengthWhenUnknown() = runTest {
        val ranges = mutableListOf<String>()
        val (bytes, sink) = collect()
        assertEquals(25L, engine(ranges).download(format(contentLength = null), emptyMap(), sink, 13, null, chunkBytes = 10))
        assertEquals(listOf("bytes=13-22", "bytes=23-24"), ranges)
        assertContentEquals(file.copyOfRange(13, 25), bytes.toByteArray())
    }

    @Test
    fun aRefusedChunkThrowsWithoutTheUrl() = runTest {
        val error = assertFailsWith<IllegalStateException> {
            engine(mutableListOf(), HttpStatusCode.Forbidden).download(format(), emptyMap(), collect().second, 0, null)
        }
        assertEquals("VyonEx: download refused (HTTP 403)", error.message)
    }

    @Test
    fun onlyYouTubeMediaUrlsAreFetched() = runTest {
        val ranges = mutableListOf<String>()
        assertFailsWith<IllegalArgumentException> {
            engine(ranges).download(format().copy(url = "https://example.com/videoplayback?itag=251"), emptyMap(), collect().second, 0, null)
        }
        assertEquals(emptyList(), ranges)
    }

    @Test
    fun formatsListsEveryPlayableQualityAfterOneProbe() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val formats = assertNotNull(fixtureEngine(host, requests = requests).formats("dQw4w9WgXcQ", setOf("TV_SAMSUNG")))
        assertEquals("VISIONOS", formats.profileId)
        // The capture's 5 audio and 22 video formats, all with direct URLs; video tops out at 2160p.
        assertEquals(5, formats.formats.count { !it.hasVideo })
        assertEquals(22, formats.formats.count { it.hasVideo && !it.hasAudio })
        assertEquals(2160, formats.formats.mapNotNull { it.height }.max())
        assertTrue(formats.formats.all { it.contentLength != null && it.url.startsWith("https://") })
        assertEquals(1, requests.count { it.url.host.endsWith(".googlevideo.com") })
        assertEquals(setOf("User-Agent"), formats.headers.keys)
        assertFalse("videoplayback" in formats.toString() || formats.formats.any { "videoplayback" in it.toString() })
    }
}
