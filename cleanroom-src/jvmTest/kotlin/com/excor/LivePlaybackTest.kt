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
    @Test
    fun publicMusicVideosReturnRangeReadableAudio() {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable YouTube live checks")
        val http = OkHttpClient()
        val host = LiveHost(http)
        val excor = Excor(host)

        runBlocking {
            listOf("dQw4w9WgXcQ", "kJQP7kiw5Fk", "JGwWNGJdvx8").forEach { videoId ->
                val stream = assertNotNull(excor.extract(videoId, 128))
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
        override fun log(level: ExcorLogLevel, message: String, trackId: String?) = Unit
    }
}
