package com.excor

import com.excor.internal.primary.extraction.TokenProvider
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient

interface ExcorHost : ExcorHostServices {
    val tokenProvider: TokenProvider?
    val filesDir: File?
    val httpClient: OkHttpClient
}

/**
 * A ready [ExcorHost] for apps that need no custom session or persistence:
 * - a session in [region] and [language], signed out unless [cookie] carries the account's YouTube cookies (a Cookie header value);
 * - an in-memory config store, so player data lasts only as long as this host;
 * - the system clock;
 * - [log] for messages, which never contain stream URLs, cookies or tokens.
 *
 * Pass [filesDir] to keep the player-data cache on disk. Implement [ExcorHost] yourself for visitor data or a session
 * that changes while the host lives.
 */
fun excorDefaultHost(
    httpClient: OkHttpClient = OkHttpClient(),
    region: String = "US",
    language: String = "en",
    filesDir: File? = null,
    tokenProvider: TokenProvider? = null,
    cookie: String? = null,
    log: (ExcorLogLevel, String) -> Unit = { _, _ -> },
): ExcorHost {
    require(region.length == 2 && region.all(Char::isLetter)) { "region must be a two-letter code" }
    require(language.length in 2..12 && language.all { it.isLetter() || it == '-' }) { "language must be a language tag" }
    require(cookie == null || cookie.all { it in ' '..'~' }) { "cookie must be a single header value in printable ASCII" }
    val session = ExcorSession(cookie?.trim()?.takeIf(String::isNotEmpty), null, region.uppercase(), language)
    return object : ExcorHost {
        override val tokenProvider = tokenProvider
        override val filesDir = filesDir
        override val httpClient = httpClient
        override val configStore = object : ExcorConfigStore {
            private val strings = ConcurrentHashMap<String, String>()
            private val longs = ConcurrentHashMap<String, Long>()
            override val enabled = true
            override fun getString(key: String): String? = strings[key]
            override fun getLong(key: String): Long? = longs[key]
            override fun putString(key: String, value: String) { strings[key] = value }
            override fun putLong(key: String, value: Long) { longs[key] = value }
        }
        override suspend fun session() = session
        override fun nowMs(): Long = System.currentTimeMillis()
        override fun log(level: ExcorLogLevel, message: String, trackId: String?) = log(level, message)
    }
}
