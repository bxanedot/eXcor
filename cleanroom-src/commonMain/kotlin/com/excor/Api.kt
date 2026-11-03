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

/** A video codec, from the format's MIME type. Ordered from the most to the least widely decodable. */
enum class ExcorVideoCodec { H264, VP9, AV1, OTHER }

/**
 * One video track. [containsAudio] is true for a progressive format (picture and sound in one file); otherwise the
 * track is picture only and plays alongside [ExcorVideoStreams.audio]. [initRange] and [indexRange] are the byte
 * ranges of the container's initialization and segment-index data, for building a DASH representation.
 */
data class ExcorVideoTrack(
    val url: String,
    val mimeType: String,
    val codec: ExcorVideoCodec,
    val width: Int?,
    val height: Int,
    val fps: Int?,
    val qualityLabel: String?,
    val kbps: Int,
    val contentLength: Long?,
    val initRange: LongRange?,
    val indexRange: LongRange?,
    val containsAudio: Boolean,
    val headers: Map<String, String>,
) {
    override fun toString(): String =
        "ExcorVideoTrack(mimeType=$mimeType, ${width}x$height, fps=$fps, kbps=$kbps, containsAudio=$containsAudio, " +
            "headers=${headers.keys})"
}

/**
 * A video and the audio to play with it, both from the same client response and both probed as playable. [audio] is
 * null only when [video] is progressive and carries its own sound. The audio container matches the video's (M4A with
 * MP4 video, Opus/WebM with WebM video) when the response offers one, so the pair can be muxed without re-encoding.
 */
/**
 * One downloadable format from a video's format list. [hasAudio] and [hasVideo] are both true for a progressive format.
 * [videoCodec] is null for audio-only formats.
 */
data class ExcorMediaFormat(
    val itag: Int?,
    val url: String,
    val mimeType: String,
    val kbps: Int,
    val contentLength: Long?,
    val hasAudio: Boolean,
    val hasVideo: Boolean,
    val width: Int?,
    val height: Int?,
    val fps: Int?,
    val qualityLabel: String?,
    val videoCodec: ExcorVideoCodec?,
    val audioChannels: Int?,
    val sampleRate: Int?,
    val initRange: LongRange?,
    val indexRange: LongRange?,
) {
    override fun toString(): String =
        "ExcorMediaFormat(itag=$itag, mimeType=$mimeType, kbps=$kbps, height=$height, hasAudio=$hasAudio)"
}

/**
 * Every whole-file format one client offers for a video, for download choices. All URLs are fetched with [headers]. The
 * URLs aren't remembered individually, so when one is refused, call `exclude(videoId, profileId)` and ask again.
 */
data class ExcorFormatList(
    val videoId: String,
    val clientName: String,
    val profileId: String,
    val headers: Map<String, String>,
    val formats: List<ExcorMediaFormat>,
) {
    override fun toString(): String =
        "ExcorFormatList(videoId=$videoId, clientName=$clientName, formats=${formats.size}, headers=${headers.keys})"
}

data class ExcorVideoStreams(
    val videoId: String,
    val video: ExcorVideoTrack,
    val audio: ExcorStream?,
    val clientName: String,
    val profileId: String,
)

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
