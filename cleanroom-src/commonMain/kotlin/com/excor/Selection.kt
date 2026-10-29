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
