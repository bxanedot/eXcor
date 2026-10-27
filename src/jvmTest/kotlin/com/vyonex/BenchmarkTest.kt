package com.vyonex

import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test

/**
 * Opt-in reliability benchmark (BENCHMARKS.md): VYONEX_BENCH=1 ./gradlew jvmTest --tests com.vyonex.BenchmarkTest --rerun -i
 * Runs a fixed set of videos by kind (or VYONEX_BENCH_IDS=id:kind,...), and for each records the outcome, the identity
 * that served it, the time taken, the network calls made and whether the returned media answers a 1 KiB ranged GET
 * with exactly 1 KiB. Writes build/benchmark-report.md and prints it; never a URL, cookie or token. Logged out.
 */
class BenchmarkTest {
    /** What a video is expected to give: [playable] media, or a refusal of some [reason]. */
    private data class Case(val videoId: String, val kind: String, val playable: Boolean)

    // Picked from the project's captures and yt-dlp's tests; refusals change over time (an upcoming stream goes live).
    private val cases = listOf(
        Case("dQw4w9WgXcQ", "ordinary", true),
        Case("kJQP7kiw5Fk", "ordinary, most viewed", true),
        Case("JGwWNGJdvx8", "ordinary", true),
        Case("6oHkCfhlDnc", "Short", true),
        Case("MgNrAu2pzNs", "YouTube Music track", true),
        Case("DbCvuSGfR3Y", "YouTube Music track", true),
        Case("LXb3EKWsInQ", "4K 60 fps", true),
        Case("v9QtM6qnG50", "dubbed audio", true),
        Case("jfKfPfyJRdk", "24/7 live stream", true),
        Case("qpgTC9MDx1o", "age-restricted", false),
        Case("17-7M-m3YRY", "copyright-blocked", false),
        Case("AAAAAAAAAAA", "does not exist", false),
    )

    private val calls = AtomicInteger()
    private val client = OkHttpClient.Builder().eventListener(object : EventListener() {
        override fun callStart(call: Call) {
            calls.incrementAndGet()
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
        override fun log(level: VyonExLogLevel, message: String, trackId: String?) {}
        override val filesDir: File = Files.createTempDirectory("vyonex-bench").toFile()
        override val httpClient = client
    }

    /** Whether [url] gives exactly the first KiB with [headers]: the status and the byte count, never the URL. */
    private fun answers(url: String, headers: Map<String, String>): Pair<Boolean, String> {
        val request = Request.Builder().url(url).header("Range", "bytes=0-1023").apply { headers.forEach { (n, v) -> header(n, v) } }.build()
        return client.newCall(request).execute().use { response ->
            // At most 2 KiB is read: a server ignoring the range would otherwise send the whole file.
            val bytes = response.body.byteStream().readNBytes(2048).size
            (response.code == 206 && bytes == 1024) to "HTTP ${response.code}, $bytes bytes"
        }
    }

    @Test
    fun benchmark() = runBlocking {
        if (System.getenv("VYONEX_BENCH") != "1") return@runBlocking
        val chosen = System.getenv("VYONEX_BENCH_IDS")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.map { val (id, kind) = (it.split(':', limit = 2) + "listed").take(2); Case(id, kind, true) } ?: cases
        val vyonEx = VyonEx(host)
        val rows = mutableListOf<String>()
        var asExpected = 0
        var botChecks = 0
        for (case in chosen) {
            calls.set(0)
            val started = System.currentTimeMillis()
            val (outcome, served) = try {
                val stream = vyonEx.extract(case.videoId, 160)
                when {
                    stream != null -> answers(stream.url, stream.headers).let { (ok, how) -> (if (ok) "media" else "media refused ($how)") to stream.profileId }
                    // A live stream has no single file; its manifest is what plays.
                    else -> vyonEx.manifest(case.videoId)?.let { "manifest ${it.kind}" to it.profileId } ?: ("nothing" to "-")
                }
            } catch (e: VyonExUnplayableException) {
                // A bot check says nothing about the video: the network was refused, and the run is not representative.
                (if ("not a bot" in e.youtubeReason.orEmpty()) "bot check (${e.status})" else "refused: ${e.reason} (${e.status})") to "-"
            } catch (e: Exception) {
                "error: ${e::class.simpleName}" to "-"
            }
            val ms = System.currentTimeMillis() - started
            val good = if (case.playable) outcome == "media" || outcome.startsWith("manifest") else outcome.startsWith("refused")
            if (good) asExpected++
            if (outcome.startsWith("bot check")) botChecks++
            rows += "| ${case.videoId} | ${case.kind} | $outcome | $served | $ms | ${calls.get()} | ${if (good) "yes" else "**no**"} |"
        }
        val report = buildString {
            appendLine("# VyonEx benchmark, ${java.time.LocalDate.now()}, logged out")
            appendLine()
            appendLine("As expected: $asExpected of ${chosen.size} (${asExpected * 100 / chosen.size}%).")
            if (botChecks > 0) appendLine("YouTube answered $botChecks with a bot check: this network's results do not count.")
            appendLine()
            appendLine("| Video | Kind | Outcome | Identity | ms | Network calls | As expected |")
            appendLine("|---|---|---|---|---|---|---|")
            rows.forEach(::appendLine)
        }
        File("build/benchmark-report.md").apply { parentFile.mkdirs() }.writeText(report)
        println(report)
    }
}
