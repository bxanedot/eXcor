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
}
