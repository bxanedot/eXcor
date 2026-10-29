package com.excor.app

import com.excor.SoundCloudProtocol
import com.excor.SoundCloudStream
import com.excor.SoundCloudTrack
import com.excor.SoundCloudTranscoding
import com.excor.ExcorStream

/** The MIME type the player picks HLS by (`MANIFEST_TYPES` in the Android app). */
const val HLS_MIME_TYPE = "application/x-mpegURL"

private val KBPS = Regex("""(\d+)k""")

private fun codecRank(t: SoundCloudTranscoding) = when {
    t.mimeType.orEmpty().startsWith("audio/mp4") || t.preset.startsWith("aac") -> 3
    t.mimeType.orEmpty().startsWith("audio/mpeg") || t.preset.startsWith("mp3") -> 2
    else -> 1
}

/** The transcoding to play or save: a whole track over a preview, then AAC over MP3 over Opus, then the highest bitrate. */
fun bestTranscoding(transcodings: List<SoundCloudTranscoding>): SoundCloudTranscoding? =
    transcodings.maxWithOrNull(compareBy({ !it.isPreview }, ::codecRank, { KBPS.find(it.preset)?.groupValues?.get(1)?.toIntOrNull() ?: 0 }))

/** A SoundCloud track as the queue shows it; null without a page URL, which is what plays it later. */
fun SoundCloudTrack.toTrack(): Track? = permalinkUrl?.let {
    Track(it, title, artist ?: uploader, durationMs?.div(1000), artworkUrl, Source.SOUNDCLOUD)
}

/**
 * What plays for [track] (`ExcorSoundCloud.track` of the queue's URL; null when SoundCloud has none there): its best
 * transcoding, through [stream]. Throws [Refused] for a track blocked in the listener's country, or one only offered
 * encrypted (DRM).
 */
suspend fun soundCloudStreamOf(track: SoundCloudTrack?, stream: suspend (SoundCloudTranscoding) -> SoundCloudStream): ExcorStream? {
    track ?: return null
    if (track.isBlocked) throw Refused(Failure.NOT_IN_COUNTRY)
    val transcoding = bestTranscoding(track.transcodings) ?: throw Refused(if (track.hasDrm) Failure.DRM else Failure.NO_STREAM)
    val answer = stream(transcoding)
    val mimeType = if (answer.protocol == SoundCloudProtocol.HLS) HLS_MIME_TYPE else answer.mimeType ?: "audio/mpeg"
    return ExcorStream(track.permalinkUrl ?: track.id.toString(), answer.url, 0, mimeType, null, "SOUNDCLOUD", "SOUNDCLOUD", emptyMap())
}

/**
 * The extension of a saved [transcoding]: AAC, single or HLS (fragmented MP4, remuxed after), is `m4a`; MP3 is `mp3`;
 * Opus comes in Ogg, `opus`.
 */
fun soundCloudExtensionOf(transcoding: SoundCloudTranscoding): String = when (codecRank(transcoding)) {
    3 -> "m4a"
    2 -> "mp3"
    else -> "opus"
}
