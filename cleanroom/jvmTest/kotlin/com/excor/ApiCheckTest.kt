package com.excor

import com.excor.internal.primary.extraction.PoTokenResult
import com.excor.internal.primary.extraction.TokenProvider
import com.excor.internal.primary.extraction.TokenProviderCapabilities
import com.excor.internal.primary.extraction.strategy.PoTokenProviderKind
import com.excor.catalog.ExcorCatalogKind
import com.excor.catalog.ExcorCatalogProvider
import com.excor.catalog.ExcorCatalogTokenProvider
import java.io.File
import okhttp3.OkHttpClient
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiCheckTest {
    @Test
    fun apiCompilesAgainstBriefApi() {
        assertTrue(true)
    }

    @Test
    fun poTokenStringOmitsEveryTokenValue() {
        val values = listOf("secret-player", "secret-stream", "secret-visitor")
        val output = PoTokenResult(values[0], values[1], values[2]).toString()

        values.forEach { assertFalse(it in output) }
        assertTrue("present" in output)
    }
}

private fun apiHost(): ExcorHost = object : ExcorHost {
    override val tokenProvider: TokenProvider? = null
    override val filesDir: File? = null
    override val httpClient: OkHttpClient = OkHttpClient()
    override val configStore: ExcorConfigStore = object : ExcorConfigStore {
        override val enabled: Boolean = true
        override fun getString(key: String): String? = null
        override fun getLong(key: String): Long? = null
        override fun putString(key: String, value: String) = Unit
        override fun putLong(key: String, value: Long) = Unit
    }

    override suspend fun session() = ExcorSession(null, null, "US", "en")
    override fun nowMs(): Long = 0
    override fun log(level: ExcorLogLevel, message: String, trackId: String?) = Unit
}

private fun apiTokenProvider(): TokenProvider = object : TokenProvider {
    override val capabilities = TokenProviderCapabilities(
        providers = setOf(PoTokenProviderKind.EXTERNAL),
        usesWebView = false,
    )

    override suspend fun getPoToken(videoId: String, visitorData: String, cookie: String?): PoTokenResult? = null
    override suspend fun prewarm(cookie: String?) = Unit
    override suspend fun invalidateAttestation() = Unit
    override suspend fun close() = Unit
}

@Suppress("unused")
private suspend fun compileEveryBriefMember() {
    val stream = ExcorStream("id", "url", 1, "audio/mp4", null, "WEB", "WEB", emptyMap())
    val candidate = ExcorAudioCandidate("url", 1, "audio/mp4", ExcorAudioFormat.M4A)
    val session = ExcorSession(null, null, "US", "en")
    val host = apiHost()
    val provider = apiTokenProvider()
    val tokens = PoTokenResult("player", "stream", "visitor")
    val capabilities = TokenProviderCapabilities()
    val providerKind = PoTokenProviderKind.WEB_BOTGUARD
    val levels = listOf(ExcorLogLevel.DEBUG, ExcorLogLevel.INFO, ExcorLogLevel.WARN, ExcorLogLevel.ERROR)
    host.session()
    host.configStore.enabled
    host.configStore.getString("key")
    host.configStore.getLong("key")
    host.configStore.putString("key", "value")
    host.configStore.putLong("key", 1)
    host.nowMs()
    host.log(ExcorLogLevel.INFO, "message")
    provider.capabilities
    provider.getPoToken("id", "visitor")
    provider.prewarm()
    provider.invalidateAttestation()
    provider.close()
    tokens.toString()
    capabilities.providers
    capabilities.usesWebView
    providerKind.name
    levels.map { it.name }
    stream.copy(videoId = "id").headers
    candidate.copy(format = ExcorAudioFormat.WEBM_OPUS).kbps
    session.region
    val library = Excor(host)
    val catalog = library.musicCatalog
    catalog.capabilities(ExcorCatalogProvider.APPLE_MUSIC)
    catalog.search(ExcorCatalogProvider.APPLE_MUSIC, "query", ExcorCatalogKind.TRACK)
    catalog.lookup("https://music.apple.com/us/album/night-signals/740003")
    catalog.albumTracks(ExcorCatalogProvider.APPLE_MUSIC, "740003")
    catalog.artistAlbums(ExcorCatalogProvider.APPLE_MUSIC, "740002")
    catalog.capabilities(ExcorCatalogProvider.SPOTIFY)
    host.catalogTokenProvider
    val catalogTokenProvider = ExcorCatalogTokenProvider { provider -> provider.name }
    catalogTokenProvider.accessToken(ExcorCatalogProvider.SPOTIFY)
    library.init()
    library.initializeFallback()
    library.audioCandidates("abcdefghijk")
    library.extract("abcdefghijk", 128, setOf("WEB"), requireM4a = true)
    library.headersFor("https://rr1---sn.googlevideo.com/videoplayback")
    library.onRefused("https://rr1---sn.googlevideo.com/videoplayback")
    library.exclude("abcdefghijk", "WEB")
    library.onSessionChanged()
}
