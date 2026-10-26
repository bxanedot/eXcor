package com.vyonex

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Opt-in: VYONEX_LIVE=1 ./gradlew jvmTest */
class LiveTest {
    private val client = OkHttpClient()

    private val host = object : VyonExHost {
        override suspend fun session() = VyonExSession(cookie = null, visitorData = null, region = "US", language = "en")
        override val configStore = object : VyonExConfigStore {
            override val enabled = true
            override fun getString(key: String): String? = null
            override fun getLong(key: String): Long? = null
            override fun putString(key: String, value: String) {}
            override fun putLong(key: String, value: Long) {}
        }
        override fun nowMs() = System.currentTimeMillis()
        override fun log(level: VyonExLogLevel, message: String, trackId: String?) = println("$level [$trackId] $message")
        override val filesDir: File = Files.createTempDirectory("vyonex-live").toFile()
        override val httpClient = client
    }

    @Test
    fun extractsRangeFetchableStreams() = runBlocking {
        if (System.getenv("VYONEX_LIVE") != "1") return@runBlocking
        val vyonEx = VyonEx(host)
        // Rick Astley "Never Gonna Give You Up", Luis Fonsi "Despacito", Ed Sheeran "Shape of You".
        for (videoId in listOf("dQw4w9WgXcQ", "kJQP7kiw5Fk", "JGwWNGJdvx8")) {
            val stream = assertNotNull(vyonEx.extract(videoId, 160), videoId)
            println("$videoId -> $stream")
            val request = Request.Builder().url(stream.url).header("Range", "bytes=0-1023")
                .apply { stream.headers.forEach { (name, value) -> header(name, value) } }.build()
            client.newCall(request).execute().use { assertTrue(it.code == 200 || it.code == 206, "$videoId: HTTP ${it.code}") }
        }
    }
}
