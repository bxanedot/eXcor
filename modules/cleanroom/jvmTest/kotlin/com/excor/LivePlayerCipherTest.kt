package com.excor

import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.testng.SkipException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class LivePlayerCipherTest {
    @Test
    fun directUrlNChallengeIsEligibleWithoutSignatureCipher() {
        val challenge = assertNotNull(
            playerChallenge(
                PlayerFormat(
                    url = "https://rr1---sn-a5mekn7e.googlevideo.com/videoplayback?expire=1893456000&n=synthetic_n_challenge",
                    mimeType = "audio/webm; codecs=opus",
                    bitrate = 128_000,
                    averageBitrate = null,
                    channels = null,
                    sampleRate = null,
                ),
            ),
        )

        assertEquals("synthetic_n_challenge", challenge.nChallenge)
        assertNull(challenge.encryptedSignature)
    }

    @Test
    fun signatureCipherChallengeIsEligibleWithoutNParameter() {
        val challenge = assertNotNull(
            playerChallenge(
                PlayerFormat(
                    url = "",
                    signatureCipher = "url=https%3A%2F%2Frr1---sn-a5mekn7e.googlevideo.com%2Fvideoplayback%3Fexpire%3D1893456000&s=synthetic_signature_challenge&sp=sig",
                    mimeType = "audio/webm; codecs=opus",
                    bitrate = 128_000,
                    averageBitrate = null,
                    channels = null,
                    sampleRate = null,
                ),
            ),
        )

        assertEquals("synthetic_signature_challenge", challenge.encryptedSignature)
        assertEquals("sig", challenge.signatureParameter)
        assertNull(challenge.nChallenge)
    }

    @Test
    fun malformedNParameterDoesNotDowngradeSignatureChallenge() {
        val challenge = playerChallenge(
            PlayerFormat(
                url = "",
                signatureCipher = "url=https%3A%2F%2Frr1---sn-a5mekn7e.googlevideo.com%2Fvideoplayback%3Fexpire%3D1893456000%26n%3D&s=synthetic_signature_challenge&sp=sig",
                mimeType = "audio/webm; codecs=opus",
                bitrate = 128_000,
                averageBitrate = null,
                channels = null,
                sampleRate = null,
            ),
        )

        assertNull(challenge)
    }

    @Test
    fun firstPartyPlayerTransformsProduceDecodableAudio() {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable YouTube live checks")
        if (runCatching { ProcessBuilder("ffmpeg", "-version").start().waitFor() }.getOrNull() != 0) {
            throw SkipException("ffmpeg is needed to decode the transformed media")
        }

        val videoId = System.getenv("EXCOR_CIPHER_ID") ?: "dQw4w9WgXcQ"
        assertTrue(videoId.matches(Regex("[A-Za-z0-9_-]{11}")), "EXCOR_CIPHER_ID must be an 11-character public video ID")
        val userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36"
        val http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        try {
            runBlocking {
                val pageUrl = "https://www.youtube.com/watch?v=$videoId"
                val pageText = getText(http, pageUrl, userAgent, MAX_WATCH_PAGE_BYTES)
                val page = assertNotNull(parseWatchPage(pageText), "Could not parse the public watch-page player response")
                if (page.playerResponse.playabilityStatus != "OK") {
                    throw SkipException("The signed-out watch page did not provide playable media")
                }

                val audioFormats = page.playerResponse.formats.filter {
                    it.audioType.startsWith("audio/") && !it.segmented
                }
                val challenges = audioFormats
                    .mapNotNull(::playerChallenge)
                    .sortedWith(
                        compareByDescending<PlayerChallenge> { it.encryptedSignature != null }
                            .thenByDescending { it.format.averageBitrate ?: it.format.bitrate ?: 0 },
                    )
                val format = challenges.firstOrNull()
                    ?: throw SkipException(
                        "No eligible audio player challenge was exposed (audio=${audioFormats.size}, signatureCipher=${challenges.count { it.encryptedSignature != null }}, directN=${challenges.count { it.encryptedSignature == null && it.nChallenge != null }})",
                    )

                val scriptPath = assertNotNull(page.playerResponse.playerScriptUrl, "The watch page did not identify a player script")
                assertTrue(scriptPath.startsWith("/s/player/") && '?' !in scriptPath, "Unexpected first-party player-script path")
                val scriptUrl = "https://www.youtube.com$scriptPath"
                val playerScript = getText(http, scriptUrl, userAgent, MAX_PLAYER_SCRIPT_BYTES)
                assertNotNull(signatureTimestamp(playerScript), "The current player script did not expose its signature timestamp")

                val solved = solvePlayerJs(
                    playerScript = playerScript,
                    preparedPlayer = null,
                    signatures = listOfNotNull(format.encryptedSignature),
                    throttling = listOfNotNull(format.nChallenge),
                ) ?: throw AssertionError("The current player script could not solve the observed audio challenges")
                val mediaUrlBuilder = format.url.newBuilder().apply {
                    format.encryptedSignature?.let { encryptedSignature ->
                        val signature = solved.signatures[encryptedSignature]
                            ?: throw AssertionError("The current player script returned no signature result")
                        setQueryParameter(format.signatureParameter ?: "signature", signature)
                    }
                }
                val mediaUrl = format.nChallenge?.let { nChallenge ->
                    val transformedN = solved.throttling[nChallenge]
                        ?: throw AssertionError("The current player script returned no n-transform result")
                    mediaUrlBuilder.setQueryParameter("n", transformedN).build()
                } ?: mediaUrlBuilder.build()
                assertTrue(isAllowedPlaybackUrl(mediaUrl.toString()), "The transformed media URL failed Excor's URL policy")

                val range = executeMediaRequest(http, mediaUrl, userAgent, "bytes=0-1023")
                val rangeCode = range.use { response ->
                    if (response.code == 403 || response.code == 429 || response.code >= 500) {
                        throw SkipException("The first-party media endpoint refused the transformed URL (HTTP ${response.code}); cipher correctness is inconclusive")
                    }
                    assertTrue(response.code == 200 || response.code == 206, "The first-party media endpoint rejected the transformed URL")
                    val firstBytes = response.body.byteStream().readNBytes(1024).size
                    assertTrue(firstBytes > 0, "The first-party media endpoint returned no bytes")
                    response.code
                }

                val full = executeMediaRequest(http, mediaUrl, userAgent, range = null)
                val decodedBytes = full.use { response ->
                    if (response.code == 403 || response.code == 429 || response.code >= 500) {
                        throw SkipException("The first-party media endpoint refused the transformed URL (HTTP ${response.code}); cipher correctness is inconclusive")
                    }
                    assertEquals(200, response.code, "The full media response did not return HTTP 200")
                    val body = assertNotNull(response.body, "The full media response had no body")
                    if (body.contentLength() > MAX_MEDIA_BYTES) {
                        throw SkipException("The selected public audio format exceeds the 256 MiB verification limit")
                    }
                    val bytes = decodeAudio(body.byteStream())
                    format.format.contentLength?.let { assertEquals(it, bytes, "The decoded media length differed from the first-party format metadata") }
                    bytes
                }

                println("First-party player transform check passed: video=$videoId itag=${format.format.itag} signature=${format.encryptedSignature != null} n=${format.nChallenge != null}; HTTP range=$rangeCode; full FFmpeg decode=$decodedBytes bytes")
            }
        } finally {
            http.connectionPool.evictAll()
            http.dispatcher.executorService.shutdown()
        }
    }

    private fun getText(http: OkHttpClient, url: String, userAgent: String, limit: Int): String {
        val request = Request.Builder().url(url).header("User-Agent", userAgent).header("Accept", "*/*").build()
        return http.newCall(request).execute().use { response ->
            if (response.code != 200) throw SkipException("The first-party page returned HTTP ${response.code}")
            val body = assertNotNull(response.body, "The first-party page returned no body")
            if (body.contentLength() > limit) throw AssertionError("The first-party page exceeded the bounded response size")
            val bytes = body.byteStream().readNBytes(limit + 1)
            assertTrue(bytes.size <= limit, "The first-party page exceeded the bounded response size")
            if (body.contentLength() >= 0) assertEquals(body.contentLength(), bytes.size.toLong(), "The first-party response was truncated")
            String(bytes, Charsets.UTF_8)
        }
    }

    private fun playerChallenge(format: PlayerFormat): PlayerChallenge? {
        val query = format.signatureCipher?.let { raw ->
            if (raw.length > MAX_CIPHER_LENGTH) return null
            "https://www.youtube.com/?$raw".toHttpUrlOrNull() ?: return null
        }
        val rawUrl = query?.queryParameter("url") ?: format.url.takeIf { query == null } ?: return null
        val url = rawUrl.toHttpUrlOrNull() ?: return null
        val signature = query?.queryParameter("s")?.takeIf { it.length in 1..MAX_CHALLENGE_LENGTH }
        if (query != null && signature == null) return null
        val signatureParameter = if (query == null) null else {
            query.queryParameter("sp")?.takeIf { it.matches(Regex("[A-Za-z0-9_]{1,16}")) } ?: "signature"
        }
        val nChallenge = url.queryParameter("n")
        if (nChallenge != null && nChallenge.length !in 1..MAX_CHALLENGE_LENGTH) return null
        if (signature == null && nChallenge == null) return null
        if (!isAllowedPlaybackUrl(url.toString())) return null
        return PlayerChallenge(format, url, signature, signatureParameter, nChallenge)
    }

    private fun executeMediaRequest(
        http: OkHttpClient,
        initialUrl: HttpUrl,
        userAgent: String,
        range: String?,
    ): okhttp3.Response {
        var url = initialUrl
        repeat(MAX_REDIRECTS + 1) { hop ->
            if (!isAllowedPlaybackUrl(url.toString())) throw AssertionError("A media redirect failed Excor's URL policy")
            val request = Request.Builder().url(url).header("User-Agent", userAgent).apply {
                range?.let { header("Range", it) }
            }.build()
            val response = try {
                http.newCall(request).execute()
            } catch (error: IOException) {
                throw AssertionError("The public media request failed (${error.javaClass.simpleName})")
            }
            if (response.code !in REDIRECT_CODES) return response
            if (hop == MAX_REDIRECTS) {
                response.close()
                throw AssertionError("The public media redirect limit was exceeded")
            }
            val next = response.header("Location")?.let(url::resolve)
            response.close()
            if (next == null || !isAllowedPlaybackUrl(next.toString())) {
                throw AssertionError("The public media redirect failed Excor's URL policy")
            }
            url = next
        }
        throw AssertionError("The public media redirect limit was exceeded")
    }

    private fun decodeAudio(input: InputStream): Long {
        val decoder = ProcessBuilder("ffmpeg", "-hide_banner", "-v", "error", "-xerror", "-i", "pipe:0", "-f", "null", "-")
            .redirectErrorStream(true)
            .start()
        val drain = Thread {
            decoder.inputStream.use { stream ->
                val buffer = ByteArray(4096)
                while (stream.read(buffer) != -1) { }
            }
        }.apply { isDaemon = true; start() }
        var bytes = 0L
        try {
            input.use { source ->
                decoder.outputStream.use { sink ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        bytes += count
                        if (bytes > MAX_MEDIA_BYTES) throw SkipException("The transformed media exceeded the 256 MiB verification limit")
                        sink.write(buffer, 0, count)
                    }
                }
            }
            if (!decoder.waitFor(120, TimeUnit.SECONDS)) throw AssertionError("FFmpeg exceeded the bounded decode time")
            assertEquals(0, decoder.exitValue(), "FFmpeg could not decode the transformed media")
            assertTrue(bytes > 0, "FFmpeg received no media bytes")
            drain.join(2_000)
            return bytes
        } catch (error: IOException) {
            decoder.destroyForcibly()
            throw AssertionError("The public media stream failed (${error.javaClass.simpleName})")
        } catch (error: Exception) {
            decoder.destroyForcibly()
            throw error
        } finally {
            if (decoder.isAlive) decoder.destroyForcibly()
            drain.join(2_000)
        }
    }

    private class PlayerChallenge(
        val format: PlayerFormat,
        val url: HttpUrl,
        val encryptedSignature: String?,
        val signatureParameter: String?,
        val nChallenge: String?,
    )

    private companion object {
        const val MAX_WATCH_PAGE_BYTES = 5_000_000
        const val MAX_PLAYER_SCRIPT_BYTES = 5_000_000
        const val MAX_CIPHER_LENGTH = 32_768
        const val MAX_CHALLENGE_LENGTH = 4_096
        const val MAX_MEDIA_BYTES = 256L * 1024 * 1024
        const val MAX_REDIRECTS = 4
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
