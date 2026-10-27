package com.vyonex

import com.vyonex.internal.playerIdOf
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.Inet6Address
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Opt-in: VYONEX_LIVE=1 ./gradlew jvmTest */
class LiveTest {
    // A media URL is tied to the IP that requested it, so record which address family reached each host.
    private val families = ConcurrentHashMap<String, String>()
    private val client = OkHttpClient.Builder().eventListener(object : EventListener() {
        override fun connectionAcquired(call: Call, connection: Connection) {
            families[call.request().url.host] = if (connection.route().socketAddress.address is Inet6Address) "IPv6" else "IPv4"
        }
    }).build()

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

    /** Why a stream may be refused, without printing the URL, query values, cookies or tokens. */
    private fun diagnose(stream: VyonExStream, player: String?): String {
        val url = Url(stream.url)
        val ip = url.parameters["ip"]
        return "profile=${stream.profileId} c=${url.parameters["c"]} itag=${url.parameters["itag"]} player=$player " +
            "urlIp=${if (ip == null) "absent" else if (':' in ip) "IPv6" else "IPv4"} " +
            "playerApiOver=${families["www.youtube.com"]} mediaOver=${families[url.host]} " +
            "params=${listOf("n", "sig", "signature", "pot").filter { it in url.parameters.names() }} " +
            "expiresInS=${url.parameters["expire"]?.toLongOrNull()?.minus(System.currentTimeMillis() / 1000)}"
    }

    @Test
    fun extractsRangeFetchableStreams() = runBlocking {
        if (System.getenv("VYONEX_LIVE") != "1") return@runBlocking
        val player = client.newCall(Request.Builder().url("https://www.youtube.com/iframe_api").build()).execute()
            .use { playerIdOf(it.body.string()) }
        val vyonEx = VyonEx(host)
        // Rick Astley "Never Gonna Give You Up", Luis Fonsi "Despacito", Ed Sheeran "Shape of You".
        for (videoId in listOf("dQw4w9WgXcQ", "kJQP7kiw5Fk", "JGwWNGJdvx8")) {
            val stream = assertNotNull(vyonEx.extract(videoId, 160), videoId)
            println("$videoId -> $stream")
            val request = Request.Builder().url(stream.url).header("Range", "bytes=0-1023")
                .apply { stream.headers.forEach { (name, value) -> header(name, value) } }.build()
            client.newCall(request).execute().use {
                assertTrue(it.code == 200 || it.code == 206, "$videoId: HTTP ${it.code} (${diagnose(stream, player)})")
            }
        }
    }

    @Test
    fun extractsVideoAndMetadata() = runBlocking {
        if (System.getenv("VYONEX_LIVE") != "1") return@runBlocking
        val vyonEx = VyonEx(host)
        val video = assertNotNull(vyonEx.extractVideo("dQw4w9WgXcQ", 720))
        println("dQw4w9WgXcQ -> $video")
        for (url in listOfNotNull(video.url, video.audio?.url)) {
            val request = Request.Builder().url(url).header("Range", "bytes=0-1023")
                .apply { video.headers.forEach { (name, value) -> header(name, value) } }.build()
            client.newCall(request).execute().use {
                assertTrue(it.code == 200 || it.code == 206, "HTTP ${it.code} (profile=${video.profileId}, itag=${Url(url).parameters["itag"]})")
            }
        }
        val info = assertNotNull(vyonEx.metadata("dQw4w9WgXcQ"))
        println(
            "dQw4w9WgXcQ -> \"${info.title}\" by ${info.author}, ${info.durationSeconds} s, ${info.viewCount} views, " +
                "${info.likeCount} likes, category ${info.category}, ${info.description?.length} description chars, " +
                "${info.keywords.size} keywords, ${info.thumbnails.size} thumbnails",
        )
        assertTrue("Never Gonna Give You Up" in info.title.orEmpty(), "${info.title}")
        assertTrue(!info.description.isNullOrEmpty())
    }

    /**
     * Opt-in survey of any videos: VYONEX_LIVE=1 VYONEX_LIVE_IDS=id1,id2 (comma-separated). Prints one line per
     * video with profile, itag and HTTP code, or YouTube's reason, never a URL. Unplayable videos are reported,
     * not failed; it fails only when a returned URL does not answer or an error is not the documented kind.
     */
    @Test
    fun surveysListedVideos() = runBlocking {
        val ids = System.getenv("VYONEX_LIVE_IDS").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (System.getenv("VYONEX_LIVE") != "1" || ids.isEmpty()) return@runBlocking
        val vyonEx = VyonEx(host)
        val broken = mutableListOf<String>()
        // Every returned URL must answer a ranged GET with its headers (BRIEF 4.2: "whose URL works").
        fun fetch(videoId: String, url: String, headers: Map<String, String>): String {
            val request = Request.Builder().url(url).header("Range", "bytes=0-1023")
                .apply { headers.forEach { (name, value) -> header(name, value) } }.build()
            val code = client.newCall(request).execute().use { it.code }
            val result = "itag ${Url(url).parameters["itag"]} HTTP $code"
            if (code != 200 && code != 206) broken += "$videoId: $result"
            return result
        }
        // The JVM reports an unplayable video only as IllegalStateException (BRIEF 4.2 step 10).
        suspend fun <T : Any> outcome(videoId: String, call: suspend () -> T?, show: (T) -> String): String = try {
            call()?.let(show) ?: "none"
        } catch (e: IllegalStateException) {
            e.message.orEmpty()
        } catch (e: Exception) {
            broken += "$videoId: ${e::class.simpleName}"
            "error ${e::class.simpleName}"
        }
        for (videoId in ids) {
            val audio = outcome(videoId, { vyonEx.extract(videoId, 160) }) { "${it.profileId} ${fetch(videoId, it.url, it.headers)}" }
            val video = outcome(videoId, { vyonEx.extractVideo(videoId, 1080) }) { v ->
                "${v.profileId} ${v.width}x${v.height}@${v.fps} ${v.mimeType} ${fetch(videoId, v.url, v.headers)}" +
                    (v.audio?.let { " + audio ${fetch(videoId, it.url, it.headers)}" } ?: " (combined)")
            }
            val info = vyonEx.metadata(videoId)
                ?.let { "\"${it.title}\" ${it.durationSeconds} s live=${it.isLive} microformat=${it.category != null}" } ?: "none"
            println("$videoId | audio: $audio | video: $video | metadata: $info")
        }
        assertTrue(broken.isEmpty(), broken.joinToString("\n"))
    }
}
