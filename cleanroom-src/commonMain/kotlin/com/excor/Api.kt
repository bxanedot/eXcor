package com.excor

import com.excor.catalog.ExcorCatalogTokenProvider

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
}
