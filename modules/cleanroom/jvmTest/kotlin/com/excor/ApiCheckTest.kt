package com.excor

import com.excor.internal.primary.extraction.PoTokenResult
import com.excor.internal.primary.extraction.TokenProvider
import com.excor.internal.primary.extraction.TokenProviderCapabilities
import com.excor.internal.primary.extraction.strategy.PoTokenProviderKind
import com.excor.catalog.ExcorCatalogKind
import com.excor.catalog.ExcorCatalogProvider
import com.excor.catalog.ExcorCatalogTokenProvider
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    @Test
    fun closingExcorStopsFurtherRequestsAndIsIdempotent() = runBlocking {
        var dnsLookups = 0
        val httpClient = OkHttpClient.Builder().dns { hostname ->
            dnsLookups++
            throw UnknownHostException(hostname)
        }.build()
        try {
            val library = Excor(apiHost(httpClient))
            library.close()
            library.close()
            var cancelledAfterClose = false
            try {
                library.search("close lifecycle")
            } catch (_: CancellationException) {
                cancelledAfterClose = true
            }

            assertTrue(cancelledAfterClose, "closed eXcor instances must reject new requests")
            assertEquals(0, dnsLookups)
            assertFalse(httpClient.dispatcher.executorService.isShutdown, "eXcor must not shut down the host's shared OkHttp dispatcher")
        } finally {
            httpClient.dispatcher.executorService.shutdownNow()
            httpClient.connectionPool.evictAll()
        }
    }

    @Test
    fun clonedTransportDoesNotSendHostJarCookiesButKeepsExplicitSessionCookie() {
        val receivedCookies = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                receivedCookies += exchange.requestHeaders.getFirst("Cookie") ?: "<none>"
                exchange.sendResponseHeaders(204, -1)
            }
            start()
        }
        val url = "http://127.0.0.1:${server.address.port}/"
        val hostClient = OkHttpClient.Builder().cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
            override fun loadForRequest(url: HttpUrl): List<Cookie> = listOf(
                Cookie.Builder().name("ambient").value("host-secret").hostOnlyDomain(url.host).path("/").build(),
            )
        }).build()
        val library = Excor(apiHost(hostClient))
        try {
            val transport = Excor::class.java.getDeclaredField("engineHttpClient").apply { isAccessible = true }
                .get(library) as OkHttpClient
            transport.newCall(Request.Builder().url(url).build()).execute().close()
            transport.newCall(Request.Builder().url(url).header("Cookie", "SID=explicit-session").build()).execute().close()

            assertEquals(listOf("<none>", "SID=explicit-session"), receivedCookies)
        } finally {
            library.close()
            hostClient.dispatcher.executorService.shutdownNow()
            hostClient.connectionPool.evictAll()
            server.stop(0)
        }
    }

    @Test
    fun extractDoesNotReturnAnEmptyPartialContentResponseAsPlayable() = runBlocking {
        val hostClient = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val (code, body, type) = when (request.url.encodedPath) {
                "/youtubei/v1/player" -> Triple(200, fixture("player-response.json"), "application/json")
                "/videoplayback" -> Triple(206, "", "audio/mp4")
                else -> Triple(404, "", "text/plain")
            }
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 206) "Partial Content" else if (code == 200) "OK" else "Not Found")
                .body(body.toResponseBody(type.toMediaType()))
                .build()
        }.build()
        val library = Excor(apiHost(hostClient))
        try {
            assertNull(library.extract("dQw4w9WgXcQ", 128))
        } finally {
            library.close()
            hostClient.dispatcher.executorService.shutdownNow()
            hostClient.connectionPool.evictAll()
        }
    }
}

private fun apiHost(httpClient: OkHttpClient = OkHttpClient()): ExcorHost = object : ExcorHost {
    override val tokenProvider: TokenProvider? = null
    override val filesDir: File? = null
    override val httpClient: OkHttpClient = httpClient
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
    library.close()
}
