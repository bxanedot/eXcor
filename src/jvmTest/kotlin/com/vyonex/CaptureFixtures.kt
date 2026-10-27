package com.vyonex

import com.vyonex.internal.DESKTOP_UA
import com.vyonex.internal.IDENTITIES
import com.vyonex.internal.playerRequestBody
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import kotlin.test.Test

/**
 * Opt-in capture of the logged-out YouTube responses the error, collection, search and related tests need:
 * VYONEX_CAPTURE=1 ./gradlew jvmTest --tests com.vyonex.CaptureFixtures --rerun -i
 * Redacts before writing into fixtures/ (fixtures/README.md) and refuses to write a file the hygiene checks
 * still flag. Stops at a bot check instead of working around it. Prints file names, HTTP codes and sizes only.
 */
class CaptureFixtures {
    // Client names, numbers and versions from yt-dlp 2026.10.21 _base.py INNERTUBE_CLIENTS (web, web_music).
    private class Client(val name: String, val id: Int, val version: String, val origin: String)
    private val web = Client("WEB", 1, "2.20260708.00.00", "https://www.youtube.com")
    private val music = Client("WEB_REMIX", 67, "1.20260707.12.00", "https://music.youtube.com")
    private val http = OkHttpClient()

    @Test
    fun capture() {
        if (System.getenv("VYONEX_CAPTURE") != "1") return
        // The unplayable cases of the live survey (QUESTIONS item 2), asked as extract asks VISIONOS.
        mapOf(
            "age" to "qpgTC9MDx1o", "not-found" to "AAAAAAAAAAA", "live" to "jfKfPfyJRdk",
            "upcoming" to "uEMx1aH5j8k", "blocked" to "17-7M-m3YRY",
        ).forEach { (case, videoId) -> player("player-unplayable-$case.json", videoId) }
        // A playlist with over 400 videos (yt-dlp's tests), so it has a second page.
        val playlist = call("browse", web, "browse-playlist.json") { put("browseId", "VLPLsyOSbh5bs16vubvKePAQ1x3PhKavfBIl") }
        val token = playlist?.let { Regex(""""continuationCommand":\{"token":"([^"]+)"""").find(it)?.groupValues?.get(1) }
        if (token == null) println("browse-playlist-continuation.json: no continuation token, skipped")
        else call("browse", web, "browse-playlist-continuation.json") { put("continuation", token) }
        // An album, its track playlist and an artist page (IDs from yt-dlp's tests and the existing captures).
        call("browse", music, "browse-album-music.json") { put("browseId", "MPREb_gTAcphH99wE") }
        call("browse", web, "browse-album-playlist.json") { put("browseId", "VLOLAK5uy_l1m0thk3g31NmIIz_vMIbWtyv7eZixlH0") }
        call("browse", music, "browse-artist-music.json") { put("browseId", "UCuAXFkgsw1L7xaCfnd5JJOw") }
        call("next", web, "next-mix.json") { put("videoId", "dQw4w9WgXcQ"); put("playlistId", "RDdQw4w9WgXcQ") }
        call("next", web, "next-related.json") { put("videoId", "dQw4w9WgXcQ") }
        call("search", web, "search-web.json") { put("query", "rick astley") }
        call("search", music, "search-music.json") { put("query", "never gonna give you up") }
    }

    private fun player(file: String, videoId: String) {
        val id = IDENTITIES.first { it.profileId == "VISIONOS" }
        val session = VyonExSession(cookie = null, visitorData = null, region = "US", language = "en")
        post(
            "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            playerRequestBody(id, videoId, session, null, null, null),
            mapOf("User-Agent" to id.userAgent, "X-YouTube-Client-Name" to "${id.clientId}", "X-YouTube-Client-Version" to id.clientVersion),
            file,
        )
    }

    private fun call(endpoint: String, client: Client, file: String, fields: JsonObjectBuilder.() -> Unit): String? {
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", client.name)
                    put("clientVersion", client.version)
                    put("hl", "en")
                    put("gl", "US")
                }
            }
            fields()
        }.toString()
        val headers = mapOf(
            "User-Agent" to DESKTOP_UA, "X-YouTube-Client-Name" to "${client.id}", "X-YouTube-Client-Version" to client.version,
            "Origin" to client.origin, "Referer" to "${client.origin}/",
        )
        return post("${client.origin}/youtubei/v1/$endpoint?prettyPrint=false", body, headers, file)
    }

    private fun post(url: String, body: String, headers: Map<String, String>, file: String): String? {
        val request = Request.Builder().url(url).post(body.toRequestBody("application/json".toMediaType()))
            .apply { headers.forEach { (name, value) -> header(name, value) } }.build()
        val (code, text) = http.newCall(request).execute().use { it.code to it.body.string() }
        check("not a bot" !in text) { "$file: YouTube asked to confirm this is not a bot; stopping (not worked around)" }
        if (code != 200) {
            println("$file: HTTP $code, skipped")
            return null
        }
        val redacted = redactCapture(text)
        check(!hasPublicAddress(redacted) && !hasCookieValue(redacted) && !hasVisitorId(redacted)) { "$file: redaction incomplete, not written" }
        File(FIXTURES, file).writeText(redacted)
        println("$file: ${redacted.length} chars")
        return redacted
    }
}
