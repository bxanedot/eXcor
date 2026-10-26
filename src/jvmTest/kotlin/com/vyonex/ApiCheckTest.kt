package com.vyonex

import com.vyonex.internal.primary.extraction.PoTokenResult
import com.vyonex.internal.primary.extraction.TokenProvider
import com.vyonex.internal.primary.extraction.TokenProviderCapabilities
import com.vyonex.internal.primary.extraction.strategy.PoTokenProviderKind
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Compiles against every member of the public API the host app uses (BRIEF section 3). */
class ApiCheckTest {
    private val store = object : VyonExConfigStore {
        override val enabled: Boolean = false
        override fun getString(key: String): String? = null
        override fun getLong(key: String): Long? = null
        override fun putString(key: String, value: String) {}
        override fun putLong(key: String, value: Long) {}
    }

    private val tokens = object : TokenProvider {
        override suspend fun getPoToken(videoId: String, visitorData: String, cookie: String?): PoTokenResult? = null
    }

    private val host = object : VyonExHost {
        override suspend fun session() = VyonExSession(cookie = null, visitorData = null, region = "US", language = "en")
        override val configStore: VyonExConfigStore = store
        override fun nowMs(): Long = 0
        override fun log(level: VyonExLogLevel, message: String, trackId: String?) {}
        override val tokenProvider: TokenProvider = tokens
        override val filesDir: File? = null
        override val httpClient: OkHttpClient = OkHttpClient()
    }

    @Test
    fun publicApiCompiles() = runBlocking {
        val stream = VyonExStream(
            videoId = "v", url = "u", kbps = 1, mimeType = "m", loudnessDb = null,
            clientName = "c", profileId = "p", headers = emptyMap(),
        )
        val (videoId, url, kbps, mimeType, loudnessDb, clientName, profileId, headers) = stream
        listOf<Any?>(videoId, url, kbps, mimeType, loudnessDb, clientName, profileId, headers)

        val candidate = VyonExAudioCandidate(url = "u", kbps = 1, mimeType = "m", format = VyonExAudioFormat.M4A)
        listOf<Any>(candidate.url, candidate.kbps, candidate.mimeType, candidate.format)
        assertEquals(listOf("M4A", "WEBM_OPUS", "OTHER"), VyonExAudioFormat.entries.map { it.name })
        assertEquals(listOf("DEBUG", "INFO", "WARN", "ERROR"), VyonExLogLevel.entries.map { it.name })

        val session: VyonExSession = host.session()
        listOf<Any?>(session.cookie, session.visitorData, session.region, session.language)
        val services: VyonExHostServices = host
        services.log(VyonExLogLevel.INFO, "default trackId")
        listOf<Any?>(services.configStore.enabled, services.nowMs(), store.getString("k"), store.getLong("k"))
        store.putString("k", "v")
        store.putLong("k", 1)
        listOf<Any?>(host.tokenProvider, host.filesDir, host.httpClient)

        assertEquals(TokenProviderCapabilities(), tokens.capabilities)
        val capabilities = TokenProviderCapabilities(providers = PoTokenProviderKind.entries.toSet(), usesWebView = false)
        listOf<Any>(capabilities.providers, capabilities.usesWebView)
        assertEquals(
            listOf("WEB_BOTGUARD", "WEBPAGE_ATTESTATION", "ANDROID_DROIDGUARD", "IOS_ATTESTATION", "EXTERNAL"),
            PoTokenProviderKind.entries.map { it.name },
        )
        assertNull(tokens.getPoToken("v", "visitor"))
        tokens.prewarm()
        tokens.invalidateAttestation()
        tokens.close()
        val token = PoTokenResult(playerRequestToken = "a", streamingDataToken = "b", visitorData = "c")
        listOf(token.playerRequestToken, token.streamingDataToken, token.visitorData)

        val vyonEx = VyonEx(host)
        val init: () -> Unit = vyonEx::init
        val network: suspend () -> Unit = {
            val a: List<VyonExAudioCandidate> = vyonEx.audioCandidates("v")
            val b: VyonExStream? = vyonEx.extract("v", 128)
            val c: VyonExStream? = vyonEx.extract(videoId = "v", maxKbps = 128, skipClients = setOf("WEB"), requireM4a = true)
            listOf(a, b, c, init)
        }
        listOf(network)
        vyonEx.initializeFallback()
        assertNull(vyonEx.headersFor("u"))
        assertNull(vyonEx.onRefused("u"))
        vyonEx.exclude("v", "p")
        vyonEx.onSessionChanged()
    }
}
