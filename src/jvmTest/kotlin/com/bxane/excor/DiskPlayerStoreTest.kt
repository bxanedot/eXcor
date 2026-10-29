package com.bxane.excor

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiskPlayerStoreTest {
    @Test
    fun keepsTheThreeNewestEntries() = runBlocking {
        val dir = Files.createTempDirectory("excor").toFile()
        val store = DiskPlayerStore(dir) { true }
        for (i in 1..5) {
            store.write("p$i", "v$i")
            File(dir, "p$i.js").setLastModified(i * 10_000L)
        }
        assertEquals(listOf("p3.js", "p4.js", "p5.js"), dir.list()!!.sorted())
        assertEquals("v4", store.read("p4"))
        assertNull(store.read("p1"))
        store.delete("p4")
        assertNull(store.read("p4"))
        dir.deleteRecursively()
        Unit
    }

    @Test
    fun readsAndWritesNothingWhileDisabled() = runBlocking {
        val dir = Files.createTempDirectory("excor").toFile()
        var enabled = true
        val store = DiskPlayerStore(dir) { enabled }
        store.write("p1", "v1")
        enabled = false
        assertNull(store.read("p1"))
        store.write("p2", "v2")
        assertEquals(listOf("p1.js"), dir.list()!!.toList())
        store.delete("p1")
        assertEquals(emptyList(), dir.list()!!.toList())
        dir.deleteRecursively()
        Unit
    }

    @Test
    fun excorCachesThePlayerOnlyWhileTheConfigStoreIsEnabled() = runBlocking {
        for (enabled in listOf(true, false)) {
            val dir = Files.createTempDirectory("excor").toFile()
            // WEB needs the prepared player, so a WEB answer means the player went through the store.
            assertEquals("Music", Excor(fixtureHost(dir, enabled)).metadata("dQw4w9WgXcQ")?.category)
            val cached = dir.walk().filter { it.isFile }.map { it.name }.toList()
            assertEquals(if (enabled) listOf("dce23b30-ejs-0.8.0.js") else emptyList(), cached, "enabled=$enabled")
            dir.deleteRecursively()
        }
    }

    /** A host whose OkHttp client answers from the fixtures: the player, and WEB player requests. */
    private fun fixtureHost(dir: File, enabled: Boolean) = object : ExcorHost {
        override suspend fun session() = ExcorSession(cookie = null, visitorData = null, region = "US", language = "en")
        override val configStore = object : ExcorConfigStore {
            override val enabled = enabled
            override fun getString(key: String): String? = null
            override fun getLong(key: String): Long? = null
            override fun putString(key: String, value: String) {}
            override fun putLong(key: String, value: Long) {}
        }
        override fun nowMs() = 0L
        override fun log(level: ExcorLogLevel, message: String, trackId: String?) {}
        override val filesDir = dir
        override val httpClient = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            val body = when {
                path == "/iframe_api" -> fixture("iframe_api.js")
                path.startsWith("/s/player/dce23b30/") -> fixture("player-dce23b30.js")
                path == "/youtubei/v1/player" && request.header("X-YouTube-Client-Name") == "1" -> fixture("player-response-web.json")
                else -> null
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).message("fixture")
                .code(if (body == null) 500 else 200).body(body.orEmpty().toResponseBody()).build()
        }.build()
    }
}
