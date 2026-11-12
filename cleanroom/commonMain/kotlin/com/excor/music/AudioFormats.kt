package com.excor.music

/** Audio codecs eXcor can name. [OTHER] keeps a format we do not recognize from being reported as one we do. */
enum class ExcorAudioCodec(val lossless: Boolean) {
    MP3(false),
    AAC_LC(false),
    HE_AAC(false),
    HE_AAC_V2(false),
    XHE_AAC(false),
    AAC(false), // AAC whose profile the source did not state
    OPUS(false),
    VORBIS(false),
    AC3(false),
    EAC3(false),
    AC4(false),
    FLAC(true),
    ALAC(true),
    PCM(true),
    OTHER(false),
}

enum class ExcorAudioContainer { MP3, MP4, WEBM, OGG, FLAC, WAV, MPEG_TS, OTHER }

/**
 * One audio rendition, described only by what its source states. Null means not stated, never "none".
 * [spatial] is true for Dolby Atmos (E-AC-3 JOC, AC-4) or a provider's own spatial flag.
 */
data class ExcorAudioFormat(
    val codec: ExcorAudioCodec,
    val container: ExcorAudioContainer? = null,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val channels: Int? = null,
    val spatial: Boolean = false,
    val codecString: String? = null,
) {
    val lossless: Boolean get() = codec.lossless

    override fun toString(): String =
        "ExcorAudioFormat(codec=$codec, container=${container ?: "unknown"}, bitrateKbps=$bitrateKbps, " +
            "sampleRateHz=$sampleRateHz, bitDepth=$bitDepth, channels=$channels, spatial=$spatial, " +
            "codecString=${if (codecString.isNullOrEmpty()) "missing" else "present"})"
}

/**
 * Reads a MIME type with an optional RFC 6381 `codecs` parameter, as DASH, HLS and HTML media use them.
 * Null for a non-audio type or an unrecognized one with no codec.
 */
fun excorAudioFormatOf(mimeType: String, bitrateKbps: Int? = null): ExcorAudioFormat? {
    val parts = mimeType.split(';').map { it.trim() }
    val type = parts.first().lowercase()
    if (!type.startsWith("audio/")) return null
    val codecs = parts.drop(1).firstOrNull { it.lowercase().startsWith("codecs=") }
        ?.substringAfter('=')?.trim('"', '\'', ' ')
        ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    val container = when (type) {
        "audio/mpeg", "audio/mp3" -> ExcorAudioContainer.MP3
        "audio/mp4", "audio/m4a", "audio/x-m4a", "audio/aac" -> ExcorAudioContainer.MP4
        "audio/webm" -> ExcorAudioContainer.WEBM
        "audio/ogg", "audio/opus" -> ExcorAudioContainer.OGG
        "audio/flac", "audio/x-flac" -> ExcorAudioContainer.FLAC
        "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave" -> ExcorAudioContainer.WAV
        "audio/mp2t" -> ExcorAudioContainer.MPEG_TS
        else -> ExcorAudioContainer.OTHER
    }
    val codec = codecs.firstOrNull()?.let(::excorAudioCodecOf) ?: when (container) {
        ExcorAudioContainer.MP3 -> ExcorAudioCodec.MP3
        ExcorAudioContainer.FLAC -> ExcorAudioCodec.FLAC
        ExcorAudioContainer.WAV -> ExcorAudioCodec.PCM
        // audio/aac is an ADTS stream whose profile is not in the type.
        ExcorAudioContainer.MP4 -> if (type == "audio/aac") ExcorAudioCodec.AAC else return null
        ExcorAudioContainer.OGG -> if (type == "audio/opus") ExcorAudioCodec.OPUS else return null
        else -> return null
    }
    return ExcorAudioFormat(
        codec = codec,
        container = container,
        bitrateKbps = bitrateKbps,
        spatial = codec == ExcorAudioCodec.AC4,
        codecString = codecs.firstOrNull(),
    )
}

/** An RFC 6381 codec string such as `mp4a.40.2`, `opus`, `fLaC` or `ec-3`. */
fun excorAudioCodecOf(codec: String): ExcorAudioCodec {
    val value = codec.trim().lowercase()
    if (value.startsWith("mp4a.")) {
        val parts = value.split('.')
        // mp4a.40.<audio object type> (ISO/IEC 14496-3); mp4a.<object type indication> otherwise.
        return when (parts.getOrNull(1)) {
            "40" -> when (parts.getOrNull(2)) {
                "2" -> ExcorAudioCodec.AAC_LC
                "5" -> ExcorAudioCodec.HE_AAC
                "29" -> ExcorAudioCodec.HE_AAC_V2
                "42" -> ExcorAudioCodec.XHE_AAC
                "34" -> ExcorAudioCodec.MP3
                null -> ExcorAudioCodec.AAC
                else -> ExcorAudioCodec.OTHER
            }
            "67", "66", "68" -> ExcorAudioCodec.AAC // MPEG-2 AAC profiles
            "69", "6b" -> ExcorAudioCodec.MP3
            "a5" -> ExcorAudioCodec.AC3
            "a6" -> ExcorAudioCodec.EAC3
            else -> ExcorAudioCodec.OTHER
        }
    }
    return when (value) {
        "mp3", ".mp3" -> ExcorAudioCodec.MP3
        "opus" -> ExcorAudioCodec.OPUS
        "vorbis" -> ExcorAudioCodec.VORBIS
        "flac" -> ExcorAudioCodec.FLAC
        "alac" -> ExcorAudioCodec.ALAC
        "ac-3" -> ExcorAudioCodec.AC3
        "ec-3" -> ExcorAudioCodec.EAC3
        "ac-4" -> ExcorAudioCodec.AC4
        "lpcm", "ipcm", "fpcm", "pcm" -> ExcorAudioCodec.PCM
        else -> if (value.startsWith("ac-4.")) ExcorAudioCodec.AC4 else ExcorAudioCodec.OTHER
    }
}

/**
 * An HLS `CHANNELS` attribute (RFC 8216bis): a count, optionally followed by `/JOC` for Dolby Atmos in E-AC-3.
 * Returns the channel count and whether the rendition is spatial.
 */
fun excorHlsChannelsOf(value: String): Pair<Int?, Boolean> {
    val parts = value.trim('"').split('/')
    return parts.first().toIntOrNull() to parts.drop(1).any { it.equals("JOC", ignoreCase = true) }
}

/** A choice the host can actually use: entitlement and playback support are the host's to state. */
data class ExcorAudioOption(
    val id: String,
    val format: ExcorAudioFormat,
    val protected: Boolean = false,
    val entitled: Boolean = true,
) {
    override fun toString(): String = "ExcorAudioOption(id=present, format=present, protected=$protected, entitled=$entitled)"
}

/** User and device limits. An empty [supportedCodecs] means every codec. */
data class ExcorAudioPreferences(
    val preferLossless: Boolean = true,
    val allowSpatial: Boolean = true,
    val preferSpatial: Boolean = false,
    val maxBitrateKbps: Int? = null,
    val maxSampleRateHz: Int? = null,
    val maxBitDepth: Int? = null,
    val supportedCodecs: Set<ExcorAudioCodec> = emptySet(),
    val allowProtected: Boolean = false,
)

enum class ExcorAudioRejection { NOT_ENTITLED, PROTECTED, CODEC_UNSUPPORTED, SPATIAL_NOT_ALLOWED, OVER_BITRATE, OVER_SAMPLE_RATE, OVER_BIT_DEPTH }

data class ExcorAudioSelection(
    val chosen: ExcorAudioOption?,
    val rejected: Map<String, ExcorAudioRejection>,
) {
    override fun toString(): String =
        "ExcorAudioSelection(chosen=${chosen != null}, rejected=${rejected.size})"
}

/**
 * Picks one option deterministically: options that break a limit are rejected with the reason, then the rest are ranked
 * by the preferences, then by stated quality; ties keep the input order. A missing bitrate never passes for a high one.
 */
fun excorSelectAudio(options: List<ExcorAudioOption>, preferences: ExcorAudioPreferences = ExcorAudioPreferences()): ExcorAudioSelection {
    val rejected = linkedMapOf<String, ExcorAudioRejection>()
    val usable = options.filter { option ->
        val f = option.format
        val reason = when {
            !option.entitled -> ExcorAudioRejection.NOT_ENTITLED
            option.protected && !preferences.allowProtected -> ExcorAudioRejection.PROTECTED
            preferences.supportedCodecs.isNotEmpty() && f.codec !in preferences.supportedCodecs -> ExcorAudioRejection.CODEC_UNSUPPORTED
            f.spatial && !preferences.allowSpatial -> ExcorAudioRejection.SPATIAL_NOT_ALLOWED
            preferences.maxBitrateKbps != null && (f.bitrateKbps ?: 0) > preferences.maxBitrateKbps -> ExcorAudioRejection.OVER_BITRATE
            preferences.maxSampleRateHz != null && (f.sampleRateHz ?: 0) > preferences.maxSampleRateHz -> ExcorAudioRejection.OVER_SAMPLE_RATE
            preferences.maxBitDepth != null && (f.bitDepth ?: 0) > preferences.maxBitDepth -> ExcorAudioRejection.OVER_BIT_DEPTH
            else -> null
        }
        if (reason != null) rejected[option.id] = reason
        reason == null
    }
    val ranking = compareByDescending<ExcorAudioOption> { preferences.preferSpatial && it.format.spatial }
        .thenByDescending { preferences.preferLossless && it.format.lossless }
        .thenByDescending { it.format.bitDepth ?: 0 }
        .thenByDescending { it.format.sampleRateHz ?: 0 }
        .thenByDescending { it.format.bitrateKbps ?: 0 }
        .thenBy { if (it.protected) 1 else 0 }
    return ExcorAudioSelection(usable.sortedWith(ranking).firstOrNull(), rejected)
}
