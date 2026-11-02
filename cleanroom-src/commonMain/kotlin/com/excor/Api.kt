package com.excor

import com.excor.catalog.ExcorCatalogTokenProvider
import com.excor.music.ExcorMusicConfig

data class ExcorStream(
    val videoId: String,
    val url: String,
    val kbps: Int,
    val mimeType: String,
    val loudnessDb: Double?,
    val clientName: String,
    val profileId: String,
    val headers: Map<String, String>,
) {
    override fun toString(): String =
        "ExcorStream(videoId=$videoId, kbps=$kbps, mimeType=$mimeType, clientName=$clientName, " +
            "profileId=$profileId, headers=${headers.keys})"
}

enum class ExcorAudioFormat { M4A, WEBM_OPUS, OTHER }

data class ExcorAudioCandidate(
    val url: String,
    val kbps: Int,
    val mimeType: String,
    val format: ExcorAudioFormat,
) {
    override fun toString(): String = "ExcorAudioCandidate(kbps=$kbps, mimeType=$mimeType, format=$format)"
}

data class ExcorSession(
    val cookie: String?,
    val visitorData: String?,
    val region: String,
    val language: String,
) {
    override fun toString(): String =
        "ExcorSession(cookie=${presence(cookie)}, visitorData=${presence(visitorData)}, region=$region, language=$language)"

    private fun presence(value: String?): String = if (value.isNullOrEmpty()) "missing" else "present"
}

enum class ExcorLogLevel { DEBUG, INFO, WARN, ERROR }

interface ExcorConfigStore {
    val enabled: Boolean
    fun getString(key: String): String?
    fun getLong(key: String): Long?
    fun putString(key: String, value: String)
    fun putLong(key: String, value: Long)
}

interface ExcorHostServices {
    suspend fun session(): ExcorSession
    val configStore: ExcorConfigStore
    fun nowMs(): Long
    fun log(level: ExcorLogLevel, message: String, trackId: String? = null)
    val catalogTokenProvider: ExcorCatalogTokenProvider? get() = null
    /** Null uses [catalogTokenProvider] for tokens and defaults for the rest. */
    val musicConfig: ExcorMusicConfig? get() = null
}

/** Why a YouTube request failed after eXcor's retries. */
enum class ExcorRequestFailure {
    /** HTTP 429 on every attempt: back off before asking again. */
    RATE_LIMITED,

    /** HTTP 500, 502, 503 or 504 on every attempt: YouTube is having trouble; try again later. */
    SERVER_ERROR,

    /** The connection failed on every attempt (DNS, TLS, timeout, reset); check connectivity or the HTTP client. */
    NETWORK,
}

/**
 * Thrown by the metadata calls (`videoInfo`, `search`, `channel`, `musicSearch` and the rest) when the request itself
 * kept failing, after up to three attempts. A null result instead means YouTube answered, but not with the page asked
 * for (an unavailable or removed item, or a refusal that retrying would not change). The message names only the
 * failure and status, never URLs, headers or session data.
 */
class ExcorRequestException(
    val failure: ExcorRequestFailure,
    val statusCode: Int?,
    cause: Throwable? = null,
) : Exception("YouTube request failed: $failure" + (statusCode?.let { " (HTTP $it)" } ?: ""), cause)
