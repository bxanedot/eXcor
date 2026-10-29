package com.excor.android

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.excor.ExcorManifestKind
import com.excor.app.PlayState
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Plays what the queue says is current, in the background, with the system's media notification and controls.
 * ExoPlayer holds only the current track; the queue decides what comes next.
 * Media3 marks the pieces used here (forwarding player, data specs, progressive sources) as APIs that may still change.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private val scope = MainScope()
    private lateinit var exo: ExoPlayer
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val playback = (application as ExcorApp).playback
        exo = ExoPlayer.Builder(this)
            // Takes audio focus (and gives way to calls), and pauses when headphones are unplugged.
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) playback.ended()
            }

            override fun onPlayerError(error: PlaybackException) {
                // A refused stream URL gets one more lookup; other errors leave the track where it stopped.
                val refused = error.cause as? HttpDataSource.InvalidResponseCodeException ?: return
                if (refused.responseCode == 403) playback.refused(refused.dataSpec.uri.toString())
            }
        })
        // The notification's and headset's next and previous move through the queue. ExoPlayer holds one track, so it
        // offers neither; this player adds both, and Media3 passes its state on to the session's listeners.
        val player = object : ForwardingSimpleBasePlayer(exo) {
            override fun getState(): State {
                val state = super.getState()
                val commands = state.availableCommands.buildUpon().add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_PREVIOUS)
                return state.buildUpon().setAvailableCommands(commands.build()).build()
            }

            override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> = when (seekCommand) {
                Player.COMMAND_SEEK_TO_NEXT -> Futures.immediateVoidFuture().also { playback.next() }
                Player.COMMAND_SEEK_TO_PREVIOUS -> Futures.immediateVoidFuture().also { playback.previous() }
                else -> super.handleSeek(mediaItemIndex, positionMs, seekCommand)
            }
        }
        session = MediaSession.Builder(this, player).build()
        scope.launch {
            // The service can be stopped while paused and created again later. What was current then is set but not
            // fetched (its URL may have expired); the play button prepares it.
            var first = true
            playback.state.collect { state ->
                when (state) {
                    is PlayState.Ready -> play(state, start = !first)
                    is PlayState.Idle -> exo.stop()
                    is PlayState.Loading, is PlayState.Failed -> exo.pause()
                }
                first = false
            }
        }
    }

    private fun play(ready: PlayState.Ready, start: Boolean) {
        if (exo.currentMediaItem?.localConfiguration?.uri?.toString() == ready.stream.url) {
            // Repeating a track with a stream that is still fresh: start it over.
            exo.seekTo(0)
        } else {
            // Each stream comes with the headers it must be fetched with (its client's user agent).
            val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(ready.stream.headers)
            val item = MediaItem.Builder()
                .setUri(ready.stream.url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(ready.track.title)
                        .setArtist(ready.track.artist)
                        .setArtworkUri(ready.track.artworkUrl?.let(Uri::parse))
                        .build(),
                )
                .build()
            val source = when (ready.stream.mimeType) {
                MANIFEST_TYPES.getValue(ExcorManifestKind.HLS) -> HlsMediaSource.Factory(http)
                MANIFEST_TYPES.getValue(ExcorManifestKind.DASH) -> DashMediaSource.Factory(http)
                else -> ProgressiveMediaSource.Factory(http)
            }
            exo.setMediaSource(source.createMediaSource(item))
        }
        if (start) {
            // Only acts when the player is stopped or failed, which it can be when the same track is started again.
            exo.prepare()
            exo.play()
        } else {
            exo.pause()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        scope.cancel()
        session?.release()
        exo.release()
        super.onDestroy()
    }
}
