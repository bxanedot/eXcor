package com.vyonex

import com.vyonex.internal.SoundCloudEngine

/** How SoundCloud delivers a stream: one file, or an HLS playlist of segments. */
enum class SoundCloudProtocol { PROGRESSIVE, HLS }

/**
 * One way SoundCloud streams a track. [preset] is SoundCloud's name for it ("aac_160k", "mp3_0_0", "opus_0_0");
 * [isPreview] means a 30-second snippet (a Go+ track for a listener without it). `VyonExSoundCloud.stream` turns it
 * into a URL.
 */
data class SoundCloudTranscoding(
    val preset: String,
    val protocol: SoundCloudProtocol,
    val mimeType: String?,
    val isPreview: Boolean,
    internal val url: String,
    internal val secretToken: String?,
) {
    override fun toString() = "SoundCloudTranscoding(preset=$preset, protocol=$protocol, mimeType=$mimeType, isPreview=$isPreview)"
}

/**
 * A SoundCloud track. [uploader] is the account that posted it, [artist] the artist its publisher named, if any.
 * [transcodings] leaves out the encrypted (DRM) ones, which VyonEx does not play: [hasDrm] says some were left out, and
 * a track with only those has none. [isBlocked] means SoundCloud does not stream it in the listener's country.
 */
data class SoundCloudTrack(
    val id: Long,
    val title: String?,
    val uploader: String?,
    val uploaderUrl: String?,
    val artist: String?,
    val durationMs: Long?,
    val artworkUrl: String?,
    val permalinkUrl: String?,
    val genre: String?,
    val isBlocked: Boolean,
    val hasDrm: Boolean,
    val transcodings: List<SoundCloudTranscoding>,
)

/** A SoundCloud set: a playlist, album or EP ([setType] as SoundCloud names it), with all its tracks. */
data class SoundCloudPlaylist(
    val id: Long,
    val title: String?,
    val uploader: String?,
    val setType: String?,
    val artworkUrl: String?,
    val permalinkUrl: String?,
    val tracks: List<SoundCloudTrack>,
)

/** A stream to play or download; the URL is signed and short-lived, so it is asked for when needed. */
data class SoundCloudStream(val url: String, val protocol: SoundCloudProtocol, val mimeType: String?, val isPreview: Boolean) {
    // The URL is signed.
    override fun toString() = "SoundCloudStream(protocol=$protocol, mimeType=$mimeType, isPreview=$isPreview)"
}

/**
 * SoundCloud, as yt-dlp's extractor reads it: public tracks and sets, logged out, through SoundCloud's own web client
 * API. Links are `https://soundcloud.com/<user>/<track>` and `…/sets/<set>`, also with `www.` or `m.`, and private
 * links with their token.
 */
class VyonExSoundCloud internal constructor(private val engine: SoundCloudEngine) {
    /** The track a link points at; null when SoundCloud has none there or the link is a set or a profile. */
    @Throws(Throwable::class)
    suspend fun track(url: String): SoundCloudTrack? = engine.track(url)

    /** The set a link points at, with every track's details; null when SoundCloud has none there. */
    @Throws(Throwable::class)
    suspend fun playlist(url: String): SoundCloudPlaylist? = engine.playlist(url)

    /** A fresh URL for [transcoding]. */
    @Throws(Throwable::class)
    suspend fun stream(transcoding: SoundCloudTranscoding): SoundCloudStream = engine.stream(transcoding)
}
