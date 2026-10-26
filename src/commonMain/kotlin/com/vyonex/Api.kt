package com.vyonex

data class VyonExStream(
    val videoId: String,
    val url: String,
    val kbps: Int,
    val mimeType: String,
    val loudnessDb: Double?,
    val clientName: String,
    val profileId: String,
    val headers: Map<String, String>,
) {
    // The URL may carry a PO token and the headers a cookie.
    override fun toString() =
        "VyonExStream(videoId=$videoId, kbps=$kbps, mimeType=$mimeType, loudnessDb=$loudnessDb, " +
            "clientName=$clientName, profileId=$profileId, headers=${headers.keys})"
}

enum class VyonExAudioFormat { M4A, WEBM_OPUS, OTHER }

data class VyonExAudioCandidate(
    val url: String,
    val kbps: Int,
    val mimeType: String,
    val format: VyonExAudioFormat,
)

data class VyonExSession(
    val cookie: String?,
    val visitorData: String?,
    val region: String,
    val language: String,
) {
    override fun toString() =
        "VyonExSession(cookie=${presence(cookie)}, visitorData=${presence(visitorData)}, region=$region, language=$language)"
}

enum class VyonExLogLevel { DEBUG, INFO, WARN, ERROR }

interface VyonExConfigStore {
    val enabled: Boolean
    fun getString(key: String): String?
    fun getLong(key: String): Long?
    fun putString(key: String, value: String)
    fun putLong(key: String, value: Long)
}

interface VyonExHostServices {
    suspend fun session(): VyonExSession
    val configStore: VyonExConfigStore
    fun nowMs(): Long
    fun log(level: VyonExLogLevel, message: String, trackId: String? = null)
}

internal fun presence(value: String?) = if (value.isNullOrEmpty()) "missing" else "present"
