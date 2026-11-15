package com.excor

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.testng.annotations.AfterMethod
import org.testng.SkipException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LivePlaybackTest {
    private data class ByteRange(val first: Long, val last: Long, val total: Long) {
        val length: Long get() = last - first + 1
    }

    private val contentRangePattern = Regex("bytes (\\d+)-(\\d+)/(\\d+)", RegexOption.IGNORE_CASE)
    private val clients = mutableListOf<OkHttpClient>()
    private val libraries = mutableListOf<Excor>()
    private val playerScriptRequests = AtomicInteger()
    private val playerApiRequests = AtomicInteger()
    private val injectedPlayer503 = AtomicInteger()
    private val playerFaultArmed = AtomicBoolean()

    private fun newExcor(): Pair<Excor, OkHttpClient> {
        playerScriptRequests.set(0)
        playerApiRequests.set(0)
        injectedPlayer503.set(0)
        playerFaultArmed.set(false)
        val inject503Once = System.getenv("EXCOR_LIVE_503_ONCE") == "1"
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val playerPost = request.url.encodedPath.endsWith("/youtubei/v1/player")
            if (playerPost && inject503Once && playerFaultArmed.get() && injectedPlayer503.compareAndSet(0, 1)) {
                println("Injected one local HTTP 503 for a live player POST")
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(503)
                    .message("Injected test failure")
                    .header("Retry-After", "0")
                    .body(ByteArray(0).toResponseBody("text/plain".toMediaType()))
                    .build()
            } else {
                if (playerPost) playerApiRequests.incrementAndGet()
                if (request.url.encodedPath.contains("/s/player/")) playerScriptRequests.incrementAndGet()
                chain.proceed(request)
            }
        }.build()
        val library = Excor(LiveHost(http))
        clients += http
        libraries += library
        return library to http
    }

    @AfterMethod(alwaysRun = true)
    fun closeClients() {
        libraries.forEach { it.close() }
        clients.forEach {
            it.connectionPool.evictAll()
            it.dispatcher.executorService.shutdown()
        }
        libraries.clear()
        clients.clear()
    }

    /**
     * The extracted stream is real, complete media, not just a readable first KiB:
     * - the whole file is downloaded with the stream's headers;
     * - ffmpeg decodes every frame without error, and the decoded duration matches the video's length;
     * - a midpoint HTTP byte-range request returns the exact requested bytes; this is not a player seek.
     * It needs ffmpeg and ffprobe on PATH, and a video YouTube serves to this network. Set EXCOR_FORCE_TV=1 to test
     * the TVHTML5 player-script path alone or EXCOR_LIVE_503_ONCE=1 to inject one local 503 after metadata lookup.
     * Only a recognized access challenge is skipped; null metadata, streams or formats fail so unknown outcomes remain visible.
     */
    @Test
    fun extractedAudioDecodesFullyAndSeeks() {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable YouTube live checks")
        if (listOf("ffmpeg", "ffprobe").any { tool -> runCatching { ProcessBuilder(tool, "-version").start().waitFor() }.getOrNull() != 0 }) {
            throw SkipException("ffmpeg and ffprobe are needed to decode the media")
        }
        val (excor, http) = newExcor()
        runBlocking {
            val videoId = System.getenv("EXCOR_DECODE_ID") ?: "dQw4w9WgXcQ"
            val info = assertNotNull(liveRequest(videoId) { excor.videoInfo(videoId) }, "No metadata returned for $videoId")
            val forceTv = System.getenv("EXCOR_FORCE_TV") == "1"
            val inject503Once = System.getenv("EXCOR_LIVE_503_ONCE") == "1"
            val skipClients = if (forceTv) setOf("visionos", "web") else emptySet()
            if (forceTv) playerScriptRequests.set(0)
            val playerPostsBeforeExtract = playerApiRequests.get()
            if (inject503Once) playerFaultArmed.set(true)
            val extracted = try {
                liveRequest(videoId) { excor.extract(videoId, 160, skipClients = skipClients) }
            } finally {
                if (forceTv) println("TVHTML5 player-script requests: ${playerScriptRequests.get()}")
                if (inject503Once) {
                    println("Injected player 503 responses: ${injectedPlayer503.get()}; live player POSTs after injection: ${playerApiRequests.get() - playerPostsBeforeExtract}")
                }
            }
            val stream = assertNotNull(extracted, "No playable audio returned for $videoId")
            if (inject503Once) {
                assertTrue(injectedPlayer503.get() == 1, "Expected exactly one locally injected player 503")
                assertTrue(playerApiRequests.get() > playerPostsBeforeExtract, "No live player POST followed the injected 503")
            }
            if (forceTv) {
                assertTrue(stream.clientName == "TVHTML5", "Expected TVHTML5, got ${stream.clientName}")
                assertTrue(playerScriptRequests.get() > 0, "TVHTML5 did not fetch a player script")
            }
            fun request(range: String) = Request.Builder().url(stream.url).header("Range", range).apply {
                stream.headers.forEach { (name, value) -> header(name, value) }
            }.build()

            val file = File.createTempFile("excor-decode", if ("webm" in stream.mimeType) ".webm" else ".m4a")
            try {
                val total = download(http, stream.url, stream.headers, file, stream.mimeType)
                assertTrue(total > 100_000, "a complete audio file, got $total bytes")

                val decode = ProcessBuilder("ffmpeg", "-v", "error", "-i", file.path, "-f", "null", "-").redirectErrorStream(true).start()
                val decodeErrors = decode.inputStream.bufferedReader().readText()
                assertTrue(decode.waitFor() == 0 && decodeErrors.isBlank(), "ffmpeg decode errors: ${decodeErrors.take(300)}")
                val probe = ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=nw=1:nk=1", file.path).start()
                val decodedSeconds = probe.inputStream.bufferedReader().readText().trim().toDouble()
                val expected = info.durationSeconds ?: decodedSeconds.toLong()
                assertTrue(kotlin.math.abs(decodedSeconds - expected) <= 2.0, "decoded ${decodedSeconds}s, video is ${expected}s")

                val middle = total / 2
                http.newCall(request("bytes=$middle-${middle + 65_535}")).execute().use { response ->
                    assertTrue(response.code == 206, "seek read answered ${response.code}")
                    val body = assertNotNull(response.body)
                    assertTrue(body.contentLength() < 0 || body.contentLength() <= 65_536, "midpoint range exceeded 64 KiB")
                    val bytes = body.byteStream().use { it.readNBytes(65_537) }
                    val range = assertNotNull(parseContentRange(response.header("Content-Range"), middle, middle + 65_535, bytes.size.toLong()))
                    assertEquals(total, range.total)
                    assertEquals(65_536, bytes.size)
                }
                println("decoded ${stream.mimeType} from ${stream.clientName}: $total bytes, ${decodedSeconds}s; seek to $middle ok")
            } finally {
                file.delete()
            }
        }
    }

    @Test
    fun hlsManifestDecodesForHostPlayer() {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable YouTube live checks")
        if (runCatching { ProcessBuilder("ffmpeg", "-version").start().waitFor() }.getOrNull() != 0) {
            throw SkipException("ffmpeg is needed to decode the HLS media")
        }
        val (excor, _) = newExcor()
        runBlocking {
            val videoId = System.getenv("EXCOR_HLS_ID") ?: "dQw4w9WgXcQ"
            val stream = assertNotNull(
                liveRequest(videoId) { excor.extractHls(videoId) },
                "No validated HLS manifest returned for $videoId",
            )
            val command = mutableListOf("ffmpeg", "-v", "error", "-xerror", "-rw_timeout", "15000000")
            if (stream.headers.isNotEmpty()) {
                command += listOf("-headers", stream.headers.entries.joinToString("\r\n", postfix = "\r\n") { "${it.key}: ${it.value}" })
            }
            command += listOf("-i", stream.url, "-t", "8", "-f", "null", "-")
            val decode = ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            try {
                assertTrue(decode.waitFor(45, TimeUnit.SECONDS), "ffmpeg timed out decoding HLS media")
                assertTrue(decode.exitValue() == 0, "ffmpeg could not decode the first 8 seconds of HLS media")
            } finally {
                if (decode.isAlive) {
                    decode.destroyForcibly()
                    decode.waitFor(5, TimeUnit.SECONDS)
                }
            }
            println("decoded an HLS sample from ${stream.clientName} (live=${stream.isLive})")
        }
    }

    /**
     * A video extraction gives a real picture track and the audio to go with it:
     * - both tracks are downloaded in full, in 4 MiB range reads, as a player would;
     * - ffprobe reports the picture size the track claims, and ffmpeg muxes the two without re-encoding and decodes
     *   every video and audio frame without error, with the duration matching the video's length;
     * - a range read from the middle of the video track starts where it was asked to.
     * The height is capped (EXCOR_VIDEO_HEIGHT, default 480) to keep the download small.
     */
    @Test
    fun extractedVideoAndAudioDecodeAndMux() {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable YouTube live checks")
        if (listOf("ffmpeg", "ffprobe").any { tool -> runCatching { ProcessBuilder(tool, "-version").start().waitFor() }.getOrNull() != 0 }) {
            throw SkipException("ffmpeg and ffprobe are needed to decode the media")
        }
        val (excor, http) = newExcor()
        runBlocking {
            val videoId = System.getenv("EXCOR_DECODE_ID") ?: "dQw4w9WgXcQ"
            val maxHeight = System.getenv("EXCOR_VIDEO_HEIGHT")?.toIntOrNull() ?: 480
            val info = assertNotNull(liveRequest(videoId) { excor.videoInfo(videoId) }, "No metadata returned for $videoId")
            val streams = assertNotNull(
                liveRequest(videoId) { excor.extractVideo(videoId, maxHeight = maxHeight) },
                "No playable video returned for $videoId",
            )
            val video = streams.video
            assertTrue(video.height <= maxHeight, "picked ${video.height}p over the $maxHeight cap")

            val dir = kotlin.io.path.createTempDirectory("excor-video").toFile()
            try {
                val videoFile = File(dir, if ("webm" in video.mimeType) "video.webm" else "video.mp4")
                val videoBytes = download(http, video.url, video.headers, videoFile, video.mimeType, video.contentLength)

                val (width, height) = ffprobe(videoFile, "v:0", "stream=width,height").lines().filter { it.isNotBlank() }.map { it.trim().toInt() }
                assertTrue(height == video.height && (video.width == null || width == video.width), "file is ${width}x$height, track says ${video.width}x${video.height}")

                val inputs = mutableListOf("-i", videoFile.path)
                streams.audio?.let { audio ->
                    val audioFile = File(dir, if ("webm" in audio.mimeType) "audio.webm" else "audio.m4a")
                    download(http, audio.url, audio.headers, audioFile, audio.mimeType)
                    inputs += listOf("-i", audioFile.path)
                }
                val muxed = File(dir, "muxed.mkv")
                val mux = ProcessBuilder(listOf("ffmpeg", "-v", "error", "-y") + inputs + listOf("-c", "copy", muxed.path)).redirectErrorStream(true).start()
                val muxErrors = mux.inputStream.bufferedReader().readText()
                assertTrue(mux.waitFor() == 0 && muxErrors.isBlank(), "ffmpeg mux errors: ${muxErrors.take(300)}")

                val decode = ProcessBuilder("ffmpeg", "-v", "error", "-i", muxed.path, "-f", "null", "-").redirectErrorStream(true).start()
                val decodeErrors = decode.inputStream.bufferedReader().readText()
                assertTrue(decode.waitFor() == 0 && decodeErrors.isBlank(), "ffmpeg decode errors: ${decodeErrors.take(300)}")
                val codecs = ffprobe(muxed, null, "stream=codec_type").lines().map { it.trim() }.filter { it.isNotEmpty() }
                assertTrue("video" in codecs && "audio" in codecs, "muxed file has $codecs")
                val seconds = ffprobe(muxed, null, "format=duration").trim().toDouble()
                val expected = info.durationSeconds ?: seconds.toLong()
                assertTrue(kotlin.math.abs(seconds - expected) <= 2.0, "decoded ${seconds}s, video is ${expected}s")

                val middle = videoBytes / 2
                val seek = Request.Builder().url(video.url).header("Range", "bytes=$middle-${middle + 65_535}").apply {
                    video.headers.forEach { (name, value) -> header(name, value) }
                }.build()
                http.newCall(seek).execute().use { response ->
                    assertTrue(response.code == 206, "seek read answered ${response.code}")
                    val body = assertNotNull(response.body)
                    assertTrue(body.contentLength() < 0 || body.contentLength() <= 65_536, "midpoint range exceeded 64 KiB")
                    val bytes = body.byteStream().use { it.readNBytes(65_537) }
                    val range = assertNotNull(parseContentRange(response.header("Content-Range"), middle, middle + 65_535, bytes.size.toLong()))
                    assertEquals(videoBytes, range.total)
                    assertEquals(65_536, bytes.size)
                }
                println(
                    "decoded ${video.qualityLabel} ${video.codec} (${width}x$height, ${video.fps} fps, $videoBytes bytes) " +
                        "with ${streams.audio?.mimeType ?: "its own audio"} from ${streams.clientName}: ${seconds}s, streams $codecs; seek ok",
                )
            } finally {
                dir.deleteRecursively()
            }
        }
    }

    /** Record whether each listed format supports byte ranges or only an initial read. */
    @Test
    fun formatListReportsRangeSupport() {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable YouTube live checks")
        val (excor, http) = newExcor()
        runBlocking {
            val videoId = System.getenv("EXCOR_DECODE_ID") ?: "dQw4w9WgXcQ"
            val list = assertNotNull(liveRequest(videoId) { excor.formats(videoId) }, "No formats returned for $videoId")
            assertTrue(list.formats.any { it.hasVideo && !it.hasAudio } && list.formats.any { it.hasAudio && !it.hasVideo }, "list has ${list.formats}")
            val outcomes = list.formats.map { format ->
                val request = Request.Builder().url(format.url).header("Range", "bytes=0-1023").apply {
                    list.headers.forEach { (name, value) -> header(name, value) }
                }.build()
                http.newCall(request).execute().use { response ->
                    when (response.code) {
                        206 -> {
                            val body = assertNotNull(response.body, "format ${format.itag} had no body")
                            assertMediaType(body, format.mimeType)
                            assertTrue(body.contentLength() < 0 || body.contentLength() <= 1024, "format ${format.itag} range exceeded 1024 bytes")
                            val bytes = body.byteStream().use { it.readNBytes(1025) }
                            val range = assertNotNull(
                                parseContentRange(response.header("Content-Range"), 0, 1023, bytes.size.toLong()),
                                "format ${format.itag} returned an invalid range",
                            )
                            if (body.contentLength() >= 0) assertEquals(range.length, body.contentLength())
                            format.contentLength?.let { assertEquals(it, range.total, "format ${format.itag} total length") }
                            "RANGE_READABLE"
                        }
                        200 -> {
                            val body = assertNotNull(response.body, "format ${format.itag} had no body")
                            assertMediaType(body, format.mimeType)
                            val bytes = body.byteStream().use { it.readNBytes(1025) }
                            val minimum = minOf(1024L, format.contentLength ?: 1024L).toInt()
                            assertTrue(bytes.size >= minimum, "format ${format.itag} returned only ${bytes.size} initial bytes")
                            if (format.contentLength != null && body.contentLength() >= 0) {
                                assertEquals(format.contentLength, body.contentLength(), "format ${format.itag} total length")
                            }
                            "READABLE_HTTP_200"
                        }
                        else -> "HTTP_${response.code}"
                    }
                }
            }
            assertTrue(outcomes.all { it == "RANGE_READABLE" || it == "READABLE_HTTP_200" }, "format outcomes: $outcomes")
            println("listed ${list.formats.size} formats from ${list.clientName}; ${outcomes.count { it == "RANGE_READABLE" }} range-readable, ${outcomes.count { it == "READABLE_HTTP_200" }} initially readable only")
        }
    }

    @Test
    fun rangedDownloadRejectsMisalignedContentRange() {
        val http = rangeResponseClient("bytes 1-150000/150001", 150_000)
        try {
            val error = assertFailsWith<AssertionError> { downloadSyntheticResponse(http) }
            assertTrue("Content-Range" in error.message.orEmpty())
        } finally {
            closeTestClient(http)
        }
    }

    @Test
    fun rangedDownloadRejectsContentRangeBodyLengthMismatch() {
        val http = rangeResponseClient("bytes 0-149999/150000", 150_001)
        try {
            val error = assertFailsWith<AssertionError> { downloadSyntheticResponse(http) }
            assertTrue("Content-Range" in error.message.orEmpty())
        } finally {
            closeTestClient(http)
        }
    }

    @Test
    fun rangedDownloadAcceptsAnAccurateShortFinalRange() {
        val http = rangeResponseClient("bytes 0-149999/150000", 150_000)
        try {
            assertEquals(150_000, downloadSyntheticResponse(http))
        } finally {
            closeTestClient(http)
        }
    }

    @Test
    fun rangedDownloadAcceptsAFullResponseWhenRangeIsIgnored() {
        val http = rangeResponseClient(null, 150_000, status = 200)
        try {
            assertEquals(150_000, downloadSyntheticResponse(http))
        } finally {
            closeTestClient(http)
        }
    }

    @Test
    fun rangedDownloadRejectsAMismatchedMediaType() {
        val http = rangeResponseClient("bytes 0-149999/150000", 150_000, contentType = "audio/mp4")
        try {
            val error = assertFailsWith<AssertionError> { downloadSyntheticResponse(http) }
            assertTrue("MIME type" in error.message.orEmpty())
        } finally {
            closeTestClient(http)
        }
    }

    @Test
    fun boundedRangeCopyNeverWritesPastTheExpectedLength() {
        val output = ByteArrayOutputStream()
        assertFailsWith<AssertionError> {
            copyBody(ByteArrayInputStream(byteArrayOf(1, 2)), output, 1)
        }
        assertTrue(output.size() <= 1)
    }

    private fun rangeResponseClient(
        contentRange: String?,
        bodyBytes: Int,
        contentType: String = "video/mp4",
        status: Int = 206,
    ): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message(if (status == 206) "Partial Content" else "OK")
                .apply { contentRange?.let { header("Content-Range", it) } }
                .body(ByteArray(bodyBytes).toResponseBody(contentType.toMediaType()))
                .build()
        }.build()

    private fun downloadSyntheticResponse(http: OkHttpClient): Long {
        val file = File.createTempFile("excor-range", ".mp4")
        return try {
            download(http, "https://media.example.invalid/video", emptyMap(), file, "video/mp4")
        } finally {
            file.delete()
        }
    }

    private fun closeTestClient(http: OkHttpClient) {
        http.connectionPool.evictAll()
        http.dispatcher.executorService.shutdown()
    }

    private fun download(
        http: OkHttpClient,
        url: String,
        headers: Map<String, String>,
        file: File,
        expectedMimeType: String,
        expectedLength: Long? = null,
    ): Long {
        // shortcut: skip owner-run media larger than 256 MiB, raise after sizing the selected stream.
        val chunk = 4L * 1024 * 1024
        val maximumSize = 256L * 1024 * 1024
        assertTrue(expectedLength == null || expectedLength in 1..maximumSize, "invalid expected media length $expectedLength")
        var offset = 0L
        var total: Long? = null
        file.outputStream().use { out ->
            while (true) {
                val requestedEnd = minOf(offset + chunk - 1, maximumSize - 1)
                val request = Request.Builder().url(url).header("Range", "bytes=$offset-$requestedEnd").apply {
                    headers.forEach { (name, value) -> header(name, value) }
                }.build()
                val (read, responseTotal) = http.newCall(request).execute().use { response ->
                    assertTrue(response.code == 206 || (response.code == 200 && offset == 0L), "range read at $offset answered ${response.code}")
                    val body = assertNotNull(response.body, "media response had no body")
                    assertMediaType(body, expectedMimeType)
                    val declaredLength = body.contentLength()
                    val (expectedBytes, responseTotal) = if (response.code == 206) {
                        val range = assertNotNull(
                            parseContentRange(response.header("Content-Range"), offset, requestedEnd),
                            "invalid Content-Range: ${response.header("Content-Range")}",
                        )
                        range.length to range.total
                    } else {
                        val length = declaredLength.takeIf { it >= 0 } ?: expectedLength
                        val completeLength = assertNotNull(length, "full response had no verifiable content length")
                        completeLength to completeLength
                    }
                    assertTrue(responseTotal <= maximumSize, "media exceeded $maximumSize bytes")
                    if (declaredLength >= 0) assertEquals(expectedBytes, declaredLength, "Content-Length did not match Content-Range")
                    if (expectedLength != null) assertEquals(expectedLength, responseTotal, "stream content length")
                    val read = copyBody(body.byteStream(), out, expectedBytes)
                    assertEquals(expectedBytes, read, "body length did not match response range")
                    read to responseTotal
                }
                assertTrue(responseTotal > offset, "media range made no progress at byte $offset")
                assertTrue(total == null || total == responseTotal, "media total changed from $total to $responseTotal")
                total = responseTotal
                offset += read
                assertTrue(offset <= responseTotal, "media response exceeded its reported total length")
                if (offset == responseTotal) break
            }
        }
        assertTrue(offset > 100_000, "a complete media file, got $offset bytes")
        assertEquals(total, offset, "downloaded media length")
        return offset
    }

    private fun assertMediaType(body: ResponseBody, expectedMimeType: String) {
        assertTrue(matchesMediaType(body, expectedMimeType), "media response MIME type did not match $expectedMimeType")
    }

    private fun matchesMediaType(body: ResponseBody, expectedMimeType: String): Boolean {
        val actual = body.contentType()?.let { "${it.type}/${it.subtype}" }?.lowercase()
        return expectedMimeType.substringBefore(';').trim().lowercase() == actual
    }

    private fun parseContentRange(value: String?, requestedStart: Long, requestedEnd: Long, bodyBytes: Long? = null): ByteRange? {
        val match = contentRangePattern.matchEntire(value.orEmpty()) ?: return null
        val first = match.groupValues[1].toLongOrNull() ?: return null
        val last = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].toLongOrNull() ?: return null
        if (first != requestedStart || last < first || total <= last || last != minOf(requestedEnd, total - 1)) return null
        if (bodyBytes != null && last - first + 1 != bodyBytes) return null
        return ByteRange(first, last, total)
    }

    private fun copyBody(input: InputStream, output: OutputStream, maximumBytes: Long): Long {
        var copied = 0L
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val remaining = maximumBytes - copied
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining + 1).toInt())
            if (count < 0) return copied
            assertTrue(count.toLong() <= remaining, "media response exceeded its validated byte range")
            output.write(buffer, 0, count)
            copied += count
        }
    }

    private fun ffprobe(file: File, select: String?, entries: String): String {
        val command = mutableListOf("ffprobe", "-v", "error")
        if (select != null) command += listOf("-select_streams", select)
        command += listOf("-show_entries", entries, "-of", "default=nw=1:nk=1", file.path)
        return ProcessBuilder(command).start().inputStream.bufferedReader().readText()
    }

    /** A blocked ID must not prevent the remaining public IDs from contributing classified outcomes. */
    @Test
    fun publicMusicVideosRecordRangeOutcomeForEachId() {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable YouTube live checks")
        val (excor, http) = newExcor()
        val videoIds = System.getenv("EXCOR_LIVE_IDS").orEmpty()
            .ifBlank { "dQw4w9WgXcQ,kJQP7kiw5Fk,JGwWNGJdvx8" }
            .split(',').map(String::trim).filter(String::isNotEmpty)
        assertTrue(videoIds.isNotEmpty() && videoIds.all { it.matches(Regex("[A-Za-z0-9_-]{11}")) }, "EXCOR_LIVE_IDS must be a comma-separated list of public YouTube video IDs")

        runBlocking {
            val outcomes = videoIds.map { videoId ->
                try {
                    val stream = liveRequest(videoId) { excor.extract(videoId, 128) }
                        ?: return@map "$videoId:NO_STREAM"
                    val request = Request.Builder().url(stream.url).header("Range", "bytes=0-1023").apply {
                        stream.headers.forEach { (name, value) -> header(name, value) }
                    }.build()
                    http.newCall(request).execute().use { response ->
                        val body = response.body
                        val readLimit = if (response.code == 206) 1025 else 1024
                        val bytesRead = body.byteStream().use { it.readNBytes(readLimit).size }
                        val range = if (response.code == 206) {
                            parseContentRange(response.header("Content-Range"), 0, 1023, bytesRead.toLong())
                        } else {
                            null
                        }
                        val mimeTypeMatches = matchesMediaType(body, stream.mimeType)
                        val outcome = when (response.code) {
                            206 -> when {
                                !mimeTypeMatches -> "MIME_MISMATCH"
                                range == null || (body.contentLength() >= 0 && range.length != body.contentLength()) -> "INVALID_RANGE"
                                else -> "RANGE_READABLE"
                            }
                            200 -> when {
                                !mimeTypeMatches -> "MIME_MISMATCH"
                                bytesRead == 1024 -> "READABLE_HTTP_200"
                                else -> "EMPTY_MEDIA_HTTP_200"
                            }
                            403, 429 -> "MEDIA_REFUSED_HTTP_${response.code}"
                            404 -> "MEDIA_UNAVAILABLE_HTTP_404"
                            else -> if (bytesRead == 0) "EMPTY_MEDIA_HTTP_${response.code}" else "HTTP_${response.code}"
                        }
                        "$videoId:$outcome"
                    }
                } catch (error: SkipException) {
                    "$videoId:ACCESS_BLOCKED"
                } catch (error: IllegalStateException) {
                    if (!isKnownYouTubeUnavailable(error.message)) throw error
                    "$videoId:VIDEO_UNAVAILABLE"
                }
            }
            println("public music video range matrix: ${outcomes.joinToString(", ")}")
            val readable = outcomes.any { it.endsWith(":RANGE_READABLE") || it.endsWith(":READABLE_HTTP_200") }
            val onlyBlockedOrUnavailable = outcomes.all {
                it.endsWith(":ACCESS_BLOCKED") || it.endsWith(":VIDEO_UNAVAILABLE") ||
                    ":MEDIA_REFUSED_HTTP_" in it || it.endsWith(":MEDIA_UNAVAILABLE_HTTP_404")
            }
            if (!readable && onlyBlockedOrUnavailable) throw SkipException("All selected public videos were access-blocked, unavailable or media-refused: ${outcomes.joinToString(", ")}")
            assertTrue(readable, "No selected public video returned readable audio: ${outcomes.joinToString(", ")}")
            assertTrue(
                outcomes.none { it.endsWith(":NO_STREAM") || ":EMPTY_MEDIA_HTTP_" in it || it.endsWith(":INVALID_RANGE") || it.endsWith(":MIME_MISMATCH") || it.substringAfter(':').startsWith("HTTP_") },
                "Unexpected live playback matrix outcome: ${outcomes.joinToString(", ")}",
            )
        }
    }

    @Test
    fun mp4TracksExposeDashInitializationAndIndex() {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable YouTube live checks")
        val (excor, http) = newExcor()
        runBlocking {
            val videoId = System.getenv("EXCOR_DECODE_ID") ?: "dQw4w9WgXcQ"
            val list = assertNotNull(liveRequest(videoId) { excor.formats(videoId) }, "No formats returned for $videoId")
            indexedMp4Tracks(list, http.newBuilder().followRedirects(false).followSslRedirects(false).build())
        }
    }

    // Experimental local-file SegmentList diagnostic, not a supported SDK DASH playback path.
    // FFmpeg 7.1.5 failed the generated-media diagnostic; see the dated maintainer note.
    @Test
    fun dashManifestDecodesLiveIndexedMp4Tracks() {
        if (System.getenv("EXCOR_LIVE") != "1" || System.getenv("EXCOR_DASH_DECODE") != "1") {
            throw SkipException("Set EXCOR_LIVE=1 and EXCOR_DASH_DECODE=1 to enable DASH media checks")
        }
        val (excor, http) = newExcor()
        // Stop starting media calls after 120 seconds; an in-flight call has at most 15 more seconds.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120)
        val media = http.newBuilder().followRedirects(false).followSslRedirects(false)
            .callTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                if (System.nanoTime() >= deadline) throw java.io.IOException("DASH media deadline exceeded")
                chain.proceed(chain.request())
            }.build()
        runBlocking {
            val videoId = System.getenv("EXCOR_DECODE_ID") ?: "dQw4w9WgXcQ"
            val list = assertNotNull(liveRequest(videoId) { excor.formats(videoId) }, "No formats returned for $videoId")
            val tracks = indexedMp4Tracks(list, media)
            assertTrue(tracks.sumOf { assertNotNull(it.format.contentLength) } <= 256L * 1024 * 1024,
                "Combined DASH tracks exceed 256 MiB")
            val dir = kotlin.io.path.createTempDirectory("excor-dash-test-").toFile()
            try {
                tracks.forEachIndexed { i, track ->
                    download(media, track.format.url, list.headers, File(dir, "track-$i.mp4"),
                        track.format.mimeType, track.format.contentLength)
                }
                val manifest = File(dir, "sample.mpd")
                manifest.writeText(localDashManifest(tracks))
                val progress = runDashTool(
                    "ffmpeg", "-nostdin", "-v", "error", "-xerror", "-protocol_whitelist", "file",
                    "-progress", "pipe:1", "-stats_period", "5", "-i", manifest.path,
                    "-map", "0:v:0", "-map", "0:a:0", "-f", "null", "-",
                )
                val decodedUs = assertNotNull(progress.lineSequence().lastOrNull { it.startsWith("out_time_us=") }
                    ?.substringAfter('=')?.toLongOrNull())
                val declaredUs = tracks.maxOf { it.index.endTicks * 1_000_000 / it.index.timescale }
                assertTrue(kotlin.math.abs(decodedUs - declaredUs) <= 1_000_000, "DASH decode did not cover its declared duration")
                assertTrue(progress.lineSequence().last { it.isNotBlank() } == "progress=end")
                println("DASH manifest decoded audio and video: outputUs=$decodedUs declaredUs=$declaredUs")
            } finally {
                dir.listFiles()?.forEach { it.delete() }
                dir.delete()
            }
        }
    }

    private fun indexedMp4Tracks(list: ExcorFormatList, media: OkHttpClient): List<IndexedTrack> {
        val indexed = list.formats.filter { it.initRange != null && it.indexRange != null }
        val audio = assertNotNull(indexed.firstOrNull {
            it.hasAudio && !it.hasVideo && it.mimeType.startsWith("audio/mp4;")
        }, "No indexed audio MP4")
        val video = assertNotNull(indexed.filter {
            it.hasVideo && !it.hasAudio && it.mimeType.startsWith("video/mp4;") &&
                it.videoCodec == ExcorVideoCodec.H264 && (it.height ?: 0) in 1..480
        }.maxByOrNull { it.height ?: 0 }, "No indexed H.264 MP4 at or below 480p")

        return listOf(audio, video).map { format ->
            val init = assertNotNull(format.initRange)
            val index = assertNotNull(format.indexRange)
            assertEquals(0L, init.first)
            assertTrue(init.last < index.first, "MP4 initialization and index ranges overlap")
            val initBoxes = mp4BoxTypes(readMp4Range(media, format, init, list.headers))
            assertTrue(initBoxes.containsAll(listOf("ftyp", "moov")), "MP4 initialization lacks ftyp or moov")
            val bytes = readMp4Range(media, format, index, list.headers)
            val total = assertNotNull(format.contentLength, "MP4 format has no total length")
            val summary = checkMp4Index(bytes, index.last + 1, total)
            val first = summary.firstMediaByte
            val firstHeader = readMp4Range(media, format, first..first + 7, list.headers)
            assertEquals("moof", firstHeader.decodeToString(4, 8), "First SIDX reference did not lead to a movie fragment")
            println("MP4 itag=${format.itag} init=$init sidx=$index timescale=${summary.timescale} " +
                "durationTicks=${summary.durationTicks} references=${summary.segments.size} firstMoof=$first")
            IndexedTrack(format, init, summary)
        }
    }

    @Test
    fun localDashManifestKeepsTimelineAndRangesTogether() {
        val format = ExcorMediaFormat(139, "https://example.invalid/unused", "audio/mp4; codecs=\"mp4a.40.2\"",
            48, 300, true, false, null, null, null, null, null, 2, 44100, 0L..99L, 100L..199L)
        val track = IndexedTrack(format, 0L..99L,
            IndexSummary(44100, 0, 88200, listOf(IndexedSegment(200L..249L, 44100), IndexedSegment(250L..299L, 44100))))
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(localDashManifest(listOf(track)).toByteArray()))
        val lists = document.getElementsByTagNameNS("urn:mpeg:dash:schema:mpd:2011", "SegmentList")
        assertEquals(1, lists.length)
        val list = lists.item(0) as org.w3c.dom.Element
        assertEquals("44100", list.getAttribute("timescale"))
        assertEquals("Representation", list.parentNode.localName)
        assertEquals(1, list.getElementsByTagNameNS("*", "Initialization").length)
        assertEquals(2, list.getElementsByTagNameNS("*", "S").length)
        val ranges = list.getElementsByTagNameNS("*", "SegmentURL")
        assertEquals("200-249", (ranges.item(0) as org.w3c.dom.Element).getAttribute("mediaRange"))
        assertEquals("250-299", (ranges.item(1) as org.w3c.dom.Element).getAttribute("mediaRange"))
        assertTrue(!localDashManifest(listOf(track)).contains(format.url))
    }

    // Deliberately opt-in: exposes local-file byte-range incompatibility instead of treating it as a pass.
    @Test
    fun localDashManifestDecodesSyntheticIndexedTracks() {
        if (System.getenv("EXCOR_DASH_DECODE") != "1") throw SkipException("Set EXCOR_DASH_DECODE=1 for synthetic FFmpeg checks")
        val dir = kotlin.io.path.createTempDirectory("excor-dash-synthetic-").toFile()
        try {
            val tracks = listOf(false, true).mapIndexed { i, video ->
                val file = File(dir, "track-$i.mp4")
                val source = if (video) "testsrc2=size=64x64:rate=10" else "sine=frequency=440:sample_rate=44100"
                runDashTool("ffmpeg", "-nostdin", "-v", "error", "-f", "lavfi", "-i", source,
                    "-t", "2", "-c:${if (video) "v" else "a"}", if (video) "libx264" else "aac",
                    "-movflags", "+dash+global_sidx", file.path)
                val bytes = file.readBytes()
                var position = 0
                var indexStart = -1
                var indexEnd = -1
                while (position < bytes.size) {
                    val size = ByteBuffer.wrap(bytes, position, 4).int
                    assertTrue(size >= 8 && size <= bytes.size - position)
                    if (bytes.decodeToString(position + 4, position + 8) == "sidx") {
                        indexStart = position
                        indexEnd = position + size
                        break
                    }
                    position += size
                }
                assertTrue(indexStart > 0)
                val summary = checkMp4Index(bytes.copyOfRange(indexStart, indexEnd), indexEnd.toLong(), bytes.size.toLong())
                val mime = if (video) "video/mp4; codecs=\"avc1.64000a\"" else "audio/mp4; codecs=\"mp4a.40.2\""
                val format = ExcorMediaFormat(null, "https://example.invalid/unused", mime, 128, bytes.size.toLong(),
                    !video, video, null, null, null, null, null, null, null, null, null)
                IndexedTrack(format, 0L until indexStart.toLong(), summary)
            }
            val manifest = File(dir, "sample.mpd").apply { writeText(localDashManifest(tracks)) }
            val progress = runDashTool("ffmpeg", "-nostdin", "-v", "error", "-xerror", "-protocol_whitelist", "file",
                "-progress", "pipe:1", "-i", manifest.path, "-map", "0:v:0", "-map", "0:a:0", "-f", "null", "-")
            assertTrue(progress.lineSequence().any { it == "progress=end" })
            val decodedUs = progress.lineSequence().last { it.startsWith("out_time_us=") }.substringAfter('=').toLong()
            assertTrue(decodedUs in 1_900_000..2_200_000, "Synthetic tracks did not decode fully")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun sidxRejectsIndirectOutOfBoundsAndOverflowingReferences() {
        fun index(size: Int = 8, start: Long = 0, offset: Long = 0): ByteArray =
            ByteBuffer.allocate(52).apply {
                putInt(52); putInt(0x73696478); putInt(1 shl 24); putInt(1); putInt(1000)
                putLong(start); putLong(offset); putShort(0); putShort(1)
                putInt(size); putInt(1000); putInt(0)
            }.array()
        val valid = checkMp4Index(index(), 52, 60)
        assertEquals(52L..59L, valid.segments.single().bytes)
        assertEquals(1000L, valid.endTicks)
        for (bytes in listOf(index(size = Int.MIN_VALUE), index(size = 9), index(start = Long.MAX_VALUE),
            index(start = -1), index(offset = -1), index().copyOf(30))) {
            assertFailsWith<AssertionError> { checkMp4Index(bytes, 52, 60) }
        }
    }

    private fun localDashManifest(tracks: List<IndexedTrack>): String = buildString {
        val durationMs = tracks.maxOf { (it.index.endTicks * 1_000 / it.index.timescale) + 1 }
        val duration = "PT${durationMs / 1_000}.${(durationMs % 1_000).toString().padStart(3, '0')}S"
        appendLine("""<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" profiles="urn:mpeg:dash:profile:full:2011" type="static" minBufferTime="PT1S" mediaPresentationDuration="$duration">""")
        appendLine("""<Period start="PT0S">""")
        tracks.forEachIndexed { i, track ->
            val type = if (track.format.hasVideo) "video" else "audio"
            assertEquals(0L, track.index.startTicks, "Local DASH experiment requires zero-based tracks")
            assertTrue(track.format.kbps > 0, "DASH representation needs positive bandwidth")
            val codecs = track.format.mimeType.substringAfter("codecs=", "").trim().trim('"')
            assertTrue(codecs.matches(Regex("[A-Za-z0-9.]+")), "Invalid codec string for DASH representation")
            appendLine("""<AdaptationSet contentType="$type" mimeType="$type/mp4">""")
            appendLine("""<Representation id="track-$i" bandwidth="${track.format.kbps * 1000L}" codecs="$codecs">""")
            appendLine("""<SegmentList timescale="${track.index.timescale}">""")
            appendLine("""<Initialization sourceURL="track-$i.mp4" range="${track.init.first}-${track.init.last}"/>""")
            appendLine("<SegmentTimeline>")
            track.index.segments.forEachIndexed { j, segment ->
                val start = if (j == 0) """ t="${track.index.startTicks}"""" else ""
                appendLine("""<S$start d="${segment.durationTicks}"/>""")
            }
            appendLine("</SegmentTimeline>")
            for ((bytes) in track.index.segments) {
                appendLine("""<SegmentURL media="track-$i.mp4" mediaRange="${bytes.first}-${bytes.last}"/>""")
            }
            appendLine("</SegmentList></Representation></AdaptationSet>")
        }
        appendLine("</Period></MPD>")
    }

    private fun runDashTool(vararg command: String): String {
        val process = ProcessBuilder(*command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val output = FutureTask { process.inputStream.use { it.readNBytes(32_769) } }
        Thread(output, "excor-dash-test-output").apply { isDaemon = true; start() }
        try {
            assertTrue(process.waitFor(45, TimeUnit.SECONDS), "${command.first()} timed out verifying DASH media")
            assertEquals(0, process.exitValue(), "${command.first()} failed to process the DASH manifest")
            val bytes = output.get(2, TimeUnit.SECONDS)
            assertTrue(bytes.size <= 32_768, "DASH diagnostic output exceeded its limit")
            return bytes.decodeToString()
        } finally {
            if (process.isAlive) process.destroyForcibly().waitFor(5, TimeUnit.SECONDS)
            process.inputStream.close()
            output.cancel(true)
        }
    }

    private fun readMp4Range(
        http: OkHttpClient, format: ExcorMediaFormat, range: LongRange, headers: Map<String, String>,
    ): ByteArray {
        assertTrue(range.first >= 0 && range.last >= range.first && range.last - range.first < 65_536, "MP4 range exceeds 64 KiB")
        val request = Request.Builder().url(format.url).apply {
            headers.forEach { (name, value) -> header(name, value) }
            header("Range", "bytes=${range.first}-${range.last}")
        }.build()
        return http.newCall(request).execute().use { response ->
            assertEquals(206, response.code, "MP4 range request was not honored")
            val bytes = response.body.byteStream().use { it.readNBytes((range.last - range.first + 2).toInt()) }
            val confirmed = assertNotNull(parseContentRange(response.header("Content-Range"), range.first, range.last, bytes.size.toLong()))
            assertEquals(range.last, confirmed.last)
            format.contentLength?.let { assertEquals(it, confirmed.total) }
            bytes
        }
    }

    private fun mp4BoxTypes(bytes: ByteArray): List<String> {
        val input = ByteBuffer.wrap(bytes)
        return buildList {
            while (input.hasRemaining()) {
                val start = input.position()
                assertTrue(input.remaining() >= 8, "Truncated MP4 box header")
                val size = input.int
                assertTrue(size in 8..(input.limit() - start), "MP4 box is not contained in the requested range")
                add(ByteArray(4).also { input.get(it) }.decodeToString())
                input.position(start + size)
            }
        }
    }

    private data class IndexedTrack(val format: ExcorMediaFormat, val init: LongRange, val index: IndexSummary)
    private data class IndexedSegment(val bytes: LongRange, val durationTicks: Long)
    private data class IndexSummary(
        val timescale: Long, val startTicks: Long, val durationTicks: Long, val segments: List<IndexedSegment>,
    ) {
        val firstMediaByte get() = segments.first().bytes.first
        val endTicks get() = startTicks + durationTicks
    }

    // DASH-IF indexed addressing uses one SIDX with direct references to media ranges.
    private fun checkMp4Index(bytes: ByteArray, endExclusive: Long, contentLength: Long): IndexSummary {
        val input = ByteBuffer.wrap(bytes)
        assertTrue(input.remaining() >= 32, "Truncated SIDX")
        assertEquals(bytes.size, input.int, "The index range must contain exactly one box")
        assertEquals("sidx", bytes.decodeToString(4, 8))
        input.position(8)
        val version = input.int ushr 24
        assertTrue(version == 0 || version == 1, "Unsupported SIDX version")
        input.int // reference ID
        val timescale = Integer.toUnsignedLong(input.int)
        assertTrue(timescale > 0, "Invalid SIDX timescale")
        val (start, firstOffset) = if (version == 0) {
            Integer.toUnsignedLong(input.int) to Integer.toUnsignedLong(input.int)
        } else {
            assertTrue(input.remaining() >= 20, "Truncated version 1 SIDX")
            input.long to input.long
        }
        assertTrue(endExclusive in 1 until contentLength && firstOffset in 0 until contentLength - endExclusive)
        val firstMediaByte = endExclusive + firstOffset
        input.short // reserved
        val references = input.short.toInt() and 0xffff
        assertTrue(references > 0 && input.remaining() == references * 12, "Invalid SIDX reference table")
        var position = firstMediaByte
        var duration = 0L
        val segments = List(references) {
            val size = input.int
            assertTrue(size >= 8 && size.toLong() <= contentLength - position, "SIDX reference is indirect or outside the track")
            val durationTicks = Integer.toUnsignedLong(input.int)
            assertTrue(durationTicks > 0, "SIDX reference has no duration")
            input.int // stream access point flags
            val segment = IndexedSegment(position until position + size, durationTicks)
            position += size
            duration += durationTicks
            segment
        }
        assertTrue(start in 0..Long.MAX_VALUE / 1_000_000 - duration, "Unsupported SIDX presentation time")
        return IndexSummary(timescale, start, duration, segments)
    }

    private class LiveHost(override val httpClient: OkHttpClient) : ExcorHost {
        override val tokenProvider = null
        override val filesDir: File? = null
        override val configStore = object : ExcorConfigStore {
            override val enabled = false
            override fun getString(key: String): String? = null
            override fun getLong(key: String): Long? = null
            override fun putString(key: String, value: String) = Unit
            override fun putLong(key: String, value: Long) = Unit
        }
        override suspend fun session() = ExcorSession(null, null, "US", "en")
        override fun nowMs(): Long = System.currentTimeMillis()
        // Engine messages name clients, formats and statuses, never URLs or tokens, so printing them is safe.
        override fun log(level: ExcorLogLevel, message: String, trackId: String?) = println("[$level] $message")
    }
}
internal fun isKnownYouTubeAccessRefusal(message: String?): Boolean {
    val reason = message?.lowercase()?.replace('’', '\'') ?: return false
    return reason.startsWith("excor sign in to confirm you're not a bot") ||
        reason.startsWith("excor sign in to confirm your age") ||
        reason.startsWith("excor the page needs to be reloaded.")
}

internal fun isKnownYouTubeUnavailable(message: String?): Boolean =
    message?.lowercase()?.startsWith("excor this video is not available for video ") == true

internal suspend fun <T> liveRequest(videoId: String, request: suspend () -> T): T = try {
    request()
} catch (error: IllegalStateException) {
    if (!isKnownYouTubeAccessRefusal(error.message)) throw error
    throw SkipException("YouTube access refusal blocked live verification for $videoId")
}
