package com.excor

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.testng.annotations.AfterMethod
import org.testng.SkipException
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LivePlaybackTest {
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
     * - a range read from the middle of the file starts where it was asked to (seeking).
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
                val total = http.newCall(request("bytes=0-")).execute().use { response ->
                    assertTrue(response.code == 200 || response.code == 206, "full read answered ${response.code}")
                    response.body!!.byteStream().use { input -> file.outputStream().use { input.copyTo(it) } }
                }
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
                    assertTrue(response.header("Content-Range")?.startsWith("bytes $middle-") == true, "seek landed at ${response.header("Content-Range")}")
                    assertTrue(response.body!!.bytes().size == 65_536)
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
                val videoBytes = download(http, video.url, video.headers, videoFile)
                video.contentLength?.let { assertTrue(it == videoBytes, "downloaded $videoBytes of $it bytes") }

                val (width, height) = ffprobe(videoFile, "v:0", "stream=width,height").lines().filter { it.isNotBlank() }.map { it.trim().toInt() }
                assertTrue(height == video.height && (video.width == null || width == video.width), "file is ${width}x$height, track says ${video.width}x${video.height}")

                val inputs = mutableListOf("-i", videoFile.path)
                streams.audio?.let { audio ->
                    val audioFile = File(dir, if ("webm" in audio.mimeType) "audio.webm" else "audio.m4a")
                    download(http, audio.url, audio.headers, audioFile)
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
                    assertTrue(response.header("Content-Range")?.startsWith("bytes $middle-") == true, "seek landed at ${response.header("Content-Range")}")
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

    /** The format list has audio and video, and every listed format answers a range read with the list's headers. */
    @Test
    fun formatListIsRangeReadable() {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable YouTube live checks")
        val (excor, http) = newExcor()
        runBlocking {
            val videoId = System.getenv("EXCOR_DECODE_ID") ?: "dQw4w9WgXcQ"
            val list = assertNotNull(liveRequest(videoId) { excor.formats(videoId) }, "No formats returned for $videoId")
            assertTrue(list.formats.any { it.hasVideo && !it.hasAudio } && list.formats.any { it.hasAudio && !it.hasVideo }, "list has ${list.formats}")
            val refused = list.formats.filter { format ->
                val request = Request.Builder().url(format.url).header("Range", "bytes=0-1023").apply {
                    list.headers.forEach { (name, value) -> header(name, value) }
                }.build()
                http.newCall(request).execute().use { it.code != 206 && it.code != 200 }
            }
            assertTrue(refused.isEmpty(), "refused: $refused")
            println("listed ${list.formats.size} formats from ${list.clientName}; heights ${list.formats.mapNotNull { it.height }.distinct().sortedDescending()}; all range-readable")
        }
    }

    private fun download(http: OkHttpClient, url: String, headers: Map<String, String>, file: File): Long {
        val chunk = 4L * 1024 * 1024
        var offset = 0L
        file.outputStream().use { out ->
            while (true) {
                val request = Request.Builder().url(url).header("Range", "bytes=$offset-${offset + chunk - 1}").apply {
                    headers.forEach { (name, value) -> header(name, value) }
                }.build()
                val (read, total) = http.newCall(request).execute().use { response ->
                    assertTrue(response.code == 206 || (response.code == 200 && offset == 0L), "range read at $offset answered ${response.code}")
                    val total = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                    response.body!!.byteStream().copyTo(out) to total
                }
                offset += read
                if (read == 0L || total == null || offset >= total) break
            }
        }
        assertTrue(offset > 100_000, "a complete media file, got $offset bytes")
        return offset
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
                        val bytesRead = response.body?.byteStream()?.readNBytes(1024)?.size ?: 0
                        val outcome = when {
                            response.code == 206 && response.header("Content-Range")?.startsWith("bytes 0-1023/") == true && bytesRead == 1024 -> "RANGE_READABLE"
                            response.code == 200 && bytesRead == 1024 -> "READABLE_HTTP_200"
                            response.code == 403 || response.code == 429 -> "MEDIA_REFUSED_HTTP_${response.code}"
                            response.code == 404 -> "MEDIA_UNAVAILABLE_HTTP_404"
                            bytesRead == 0 -> "EMPTY_MEDIA_HTTP_${response.code}"
                            else -> "HTTP_${response.code}"
                        }
                        "$videoId:$outcome"
                    }
                } catch (error: SkipException) {
                    "$videoId:ACCESS_BLOCKED"
                }
            }
            println("public music video range matrix: ${outcomes.joinToString(", ")}")
            val readable = outcomes.any { it.endsWith(":RANGE_READABLE") || it.endsWith(":READABLE_HTTP_200") }
            val accessOnly = outcomes.all {
                it.endsWith(":ACCESS_BLOCKED") || ":MEDIA_REFUSED_HTTP_" in it || it.endsWith(":MEDIA_UNAVAILABLE_HTTP_404")
            }
            if (!readable && accessOnly) throw SkipException("All selected public videos were access-blocked, unavailable or media-refused: ${outcomes.joinToString(", ")}")
            assertTrue(readable, "No selected public video returned readable audio: ${outcomes.joinToString(", ")}")
            assertTrue(
                outcomes.none { it.endsWith(":NO_STREAM") || ":EMPTY_MEDIA_HTTP_" in it || it.substringAfter(':').startsWith("HTTP_") },
                "Unexpected live playback matrix outcome: ${outcomes.joinToString(", ")}",
            )
        }
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
internal fun isKnownYouTubeAccessChallenge(message: String?): Boolean {
    val reason = message?.lowercase()?.replace('’', '\'') ?: return false
    return reason.startsWith("excor sign in to confirm you're not a bot") ||
        reason.startsWith("excor sign in to confirm your age")
}

internal suspend fun <T> liveRequest(videoId: String, request: suspend () -> T): T = try {
    request()
} catch (error: IllegalStateException) {
    if (!isKnownYouTubeAccessChallenge(error.message)) throw error
    throw SkipException("YouTube access challenge blocked live verification for $videoId")
}
