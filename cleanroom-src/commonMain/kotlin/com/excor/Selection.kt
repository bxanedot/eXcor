package com.excor

internal data class PlayerFormat(
    val url: String,
    val mimeType: String,
    val bitrate: Int?,
    val averageBitrate: Int?,
    val channels: Int?,
    val sampleRate: Int?,
    val signatureCipher: String? = null,
    val itag: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Int? = null,
    val qualityLabel: String? = null,
    val contentLength: Long? = null,
    val initRange: LongRange? = null,
    val indexRange: LongRange? = null,
    /** True for segmented ("OTF") formats, which can't be fetched as one file with byte ranges. */
    val segmented: Boolean = false,
) {
    override fun toString(): String = "PlayerFormat(mimeType=$mimeType, bitrate=$bitrate, itag=$itag)"

    val kbps: Int get() = (bitrate ?: averageBitrate ?: 0).coerceAtLeast(0) / 1000
    val candidateKbps: Int get() = (averageBitrate ?: bitrate ?: 0).coerceAtLeast(0) / 1000
    val audioType: String get() = mimeType.substringBefore(';').trim().lowercase()
}

internal fun selectAudioFormat(
    formats: List<PlayerFormat>,
    maxKbps: Int,
    requireM4a: Boolean,
): PlayerFormat? = rankAudioFormats(formats, maxKbps, requireM4a).firstOrNull()

internal fun rankAudioFormats(
    formats: List<PlayerFormat>,
    maxKbps: Int,
    requireM4a: Boolean,
): List<PlayerFormat> {
    val usable = formats.filter {
        it.audioType.startsWith("audio/") && isAllowedPlaybackUrl(it.url)
    }
    if (usable.isEmpty()) return emptyList()

    if (requireM4a) {
        return usable.filter { it.audioType == "audio/mp4" }.sortedWith(autoOrder.reversed())
    }

    if (maxKbps <= 64) {
        val m4a = usable.filter { it.audioType == "audio/mp4" }
        return (m4a.ifEmpty { usable }).sortedBy(PlayerFormat::effectiveBitrate)
    }

    val webm = usable.filter { it.audioType == "audio/webm" }
    return (webm.ifEmpty { usable }).sortedWith(autoOrder.reversed())
}

private val autoOrder = compareBy<PlayerFormat> { codecRank(it.audioType) }
    .thenBy(PlayerFormat::effectiveScore)
    .thenBy { it.sampleRate.orZero().coerceAtMost(48_000) }

private fun codecRank(type: String): Int = when (type) {
    "audio/webm" -> 2
    "audio/mp4" -> 1
    else -> 0
}

private fun PlayerFormat.effectiveBitrate(): Long =
    (averageBitrate ?: bitrate ?: 0).coerceAtLeast(0).toLong()

private fun PlayerFormat.effectiveScore(): Long = effectiveBitrate() + when (channels) {
    2 -> 50_000L
    null -> 25_000L
    else -> 0L
}

private fun Int?.orZero(): Int = this ?: 0

internal enum class VideoContainer { MP4, WEBM, OTHER }

internal val PlayerFormat.container: VideoContainer
    get() = when (audioType.substringAfter('/')) {
        "mp4" -> VideoContainer.MP4
        "webm" -> VideoContainer.WEBM
        else -> VideoContainer.OTHER
    }

internal val PlayerFormat.videoCodec: ExcorVideoCodec
    get() {
        val codecs = mimeType.substringAfter("codecs=", "").trim('"', ' ').lowercase()
        return when {
            codecs.startsWith("avc1") -> ExcorVideoCodec.H264
            codecs.startsWith("vp9") || codecs.startsWith("vp09") -> ExcorVideoCodec.VP9
            codecs.startsWith("av01") -> ExcorVideoCodec.AV1
            else -> ExcorVideoCodec.OTHER
        }
    }

/** A progressive format carries its own audio: a second codec in its MIME type, or audio fields. */
internal val PlayerFormat.containsAudio: Boolean
    get() = channels != null || sampleRate != null || mimeType.substringAfter("codecs=", "").contains(',')

/**
 * Video formats that fit [maxHeight] and [codecs], best first: the tallest picture, then the higher frame rate, then
 * the most widely decodable codec (H.264, then VP9, then AV1), then the higher bitrate. Separate video tracks rank
 * before progressive ones at the same height, since progressive formats top out at low bitrates. Formats of unknown
 * height and segmented formats are left out.
 */
internal fun rankVideoFormats(
    formats: List<PlayerFormat>,
    maxHeight: Int,
    codecs: Set<ExcorVideoCodec>,
): List<PlayerFormat> = formats
    .filter {
        it.audioType.startsWith("video/") && !it.segmented && isAllowedPlaybackUrl(it.url) &&
            it.height != null && it.height in 1..maxHeight && it.videoCodec in codecs
    }
    .sortedWith(
        compareByDescending<PlayerFormat> { it.height }
            .thenByDescending { it.fps ?: 30 }
            .thenBy { it.containsAudio }
            .thenBy { it.videoCodec.ordinal }
            .thenByDescending { it.effectiveBitrate() },
    )
