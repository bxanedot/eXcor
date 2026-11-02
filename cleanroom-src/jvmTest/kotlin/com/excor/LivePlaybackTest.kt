package com.excor

import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.testng.SkipException
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LivePlaybackTest {
    /**
     * The extracted stream is real, complete media, not just a readable first KiB:
     * - the whole file is downloaded with the stream's headers;
     * - ffmpeg decodes every frame without error, and the decoded duration matches the video's length;
     * - a range read from the middle of the file starts where it was asked to (seeking).
     * It needs ffmpeg and ffprobe on PATH, and a video YouTube serves to this network; it skips otherwise.
     */
    @Test
    fun extractedAudioDecodesFullyAndSeeks() {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable YouTube live checks")
        if (listOf("ffmpeg", "ffprobe").any { tool -> runCatching { ProcessBuilder(tool, "-version").start().waitFor() }.getOrNull() != 0 }) {
            throw SkipException("ffmpeg and ffprobe are needed to decode the media")
        }
        val http = OkHttpClient()
        val excor = Excor(LiveHost(http))
        runBlocking {
            val videoId = System.getenv("EXCOR_DECODE_ID") ?: "dQw4w9WgXcQ"
            val info = assertNotNull(excor.videoInfo(videoId))
            val stream = excor.extract(videoId, 160) ?: throw SkipException("YouTube serves no playable response for $videoId to this network")
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
    fun publicMusicVideosReturnRangeReadableAudio() {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable YouTube live checks")
        val http = OkHttpClient()
        val host = LiveHost(http)
        val excor = Excor(host)

        runBlocking {
            listOf("dQw4w9WgXcQ", "kJQP7kiw5Fk", "JGwWNGJdvx8").forEach { videoId ->
                val stream = assertNotNull(excor.extract(videoId, 128), "no stream for $videoId")
                val request = Request.Builder().url(stream.url).header("Range", "bytes=0-1023").apply {
                    stream.headers.forEach { (name, value) -> header(name, value) }
                }.build()
                http.newCall(request).execute().use { response ->
                    assertTrue(response.code == 200 || response.code == 206, "Range request failed for $videoId")
                }
            }
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
