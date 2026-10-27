package com.vyonex.android

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.vyonex.VyonEx
import com.vyonex.VyonExListItem
import com.vyonex.VyonExRef
import com.vyonex.VyonExUnplayableException
import com.vyonex.app.Failure
import com.vyonex.app.PlayState
import com.vyonex.app.Playback
import com.vyonex.app.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** What a link opened: a title, who it is by, and its tracks. */
private data class Page(val title: String?, val byline: String?, val tracks: List<Track>)

private sealed interface Opened {
    data object Nothing : Opened
    data object Loading : Opened
    data class Shown(val page: Page) : Opened
    data class Problem(val message: String) : Opened
}

private fun trackOf(item: VyonExListItem, artwork: String? = null) =
    Track(item.videoId, item.title, item.channelName, item.durationSeconds, item.thumbnails.maxByOrNull { it.width }?.url ?: artwork)

/** The page for [ref]: a video, a playlist, an album, a mix or an artist's top songs; null when YouTube has none. */
private suspend fun VyonEx.pageOf(ref: VyonExRef): Page? = when (ref) {
    is VyonExRef.Video -> metadata(ref.videoId)?.let { m ->
        Page(m.title, m.author, listOf(Track(ref.videoId, m.title, m.author, m.durationSeconds, m.thumbnails.maxByOrNull { it.width }?.url)))
    }
    is VyonExRef.Playlist -> playlist(ref.playlistId)?.let { Page(it.title, it.ownerName, it.firstPage.items.map(::trackOf)) }
    is VyonExRef.Album -> album(ref.browseId)?.let { album ->
        val cover = album.thumbnails.maxByOrNull { it.width }?.url
        Page(album.title, listOfNotNull(album.artist, album.year?.toString()).joinToString(" · "), album.tracks.map { trackOf(it, cover) })
    }
    is VyonExRef.Mix -> mix(ref.playlistId)?.let { Page(it.title, null, it.firstPage.items.map(::trackOf)) }
    is VyonExRef.Channel -> artist(ref.channelId)?.let { Page(it.name, "Top songs", it.topSongs.map(::trackOf)) }
}

private fun messageOf(failure: Failure) = when (failure) {
    Failure.SIGN_IN_REQUIRED -> "YouTube wants you signed in for this one, which VyonEx does not do."
    Failure.AGE_RESTRICTED -> "This is age-restricted, which needs a signed-in account."
    Failure.UNAVAILABLE -> "This video is unavailable."
    Failure.NOT_YET_LIVE -> "This has not started yet."
    Failure.NOT_IN_COUNTRY -> "This is not available in your country."
    Failure.BLOCKED -> "YouTube does not play this one."
    Failure.BOT_CHECK -> "YouTube asked to confirm you are not a bot. Try again later or on another network."
    Failure.NO_STREAM -> "No stream that plays was found."
    Failure.NETWORK -> "Could not reach YouTube. Check the connection."
}

class MainActivity : ComponentActivity() {
    private var link by mutableStateOf("")
    private var opened by mutableStateOf<Opened>(Opened.Nothing)
    private var connecting: ListenableFuture<MediaController>? = null
    private var controller by mutableStateOf<MediaController?>(null)
    private var opening: Job? = null
    private var playing by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as VyonExApp
        sharedLink(intent)?.let { link = it }
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Screen(app.vyonEx, app.playback)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedLink(intent)?.let { link = it }
    }

    /** A link shared from another app ("Share" in YouTube or a browser); some apps put words around it. */
    private fun sharedLink(intent: Intent?) =
        intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
            Regex("https?://\\S+").find(text)?.value ?: text.trim()
        }

    // Connecting starts the media service and keeps it while the app is in front; it then runs on while playing.
    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        connecting = future
        future.addListener({
            // Cancelled when the screen stopped before the controller connected.
            if (future.isCancelled) return@addListener
            controller = future.get().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        playing = isPlaying
                    }
                })
                playing = isPlaying
            }
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        // Releases the controller, or cancels the connection if it is still being made.
        connecting?.let(MediaController::releaseFuture)
        connecting = null
        controller = null
        super.onStop()
    }

    @Composable
    private fun Screen(vyonEx: VyonEx, playback: Playback) {
        val scope = rememberCoroutineScope()
        val state by playback.state.collectAsStateWithLifecycle()
        fun open() {
            val ref = vyonEx.parse(link) ?: run {
                opened = Opened.Problem("That is not a YouTube or YouTube Music link.")
                return
            }
            opened = Opened.Loading
            // A link opened before this one and still loading would otherwise replace it when it arrives.
            opening?.cancel()
            opening = scope.launch {
                opened = try {
                    vyonEx.pageOf(ref)?.let { Opened.Shown(it) } ?: Opened.Problem("YouTube has nothing at that link.")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: VyonExUnplayableException) {
                    Opened.Problem(e.youtubeReason ?: "YouTube does not show this.")
                } catch (e: Exception) {
                    Opened.Problem("Could not load that link. Check the connection.")
                }
            }
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                label = { Text("YouTube or YouTube Music link") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = ::open, enabled = link.isNotBlank()) { Text("Open") }
            when (val now = opened) {
                Opened.Nothing -> Unit
                Opened.Loading -> CircularProgressIndicator()
                is Opened.Problem -> Text(now.message, color = MaterialTheme.colorScheme.error)
                is Opened.Shown -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(now.page.title ?: "", style = MaterialTheme.typography.titleLarge)
                    now.page.byline?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Button(onClick = { playback.play(now.page.tracks) }, enabled = now.page.tracks.isNotEmpty()) { Text("Play all") }
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(now.page.tracks) { index, track ->
                            Column(Modifier.fillMaxWidth().clickable { playback.play(now.page.tracks, index) }.padding(vertical = 8.dp)) {
                                Text(track.title ?: track.videoId, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(track.artist, track.durationSeconds?.let { "%d:%02d".format(it / 60, it % 60) }).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
            if (opened !is Opened.Shown) Spacer(Modifier.weight(1f))
            NowPlaying(state, playback)
        }
    }

    @Composable
    private fun NowPlaying(state: PlayState, playback: Playback) {
        val line = when (state) {
            PlayState.Idle -> return
            is PlayState.Loading -> "Loading ${state.track.title ?: state.track.videoId}…"
            is PlayState.Ready -> listOfNotNull(state.track.title, state.track.artist).joinToString(" · ")
            is PlayState.Failed -> "${state.track.title ?: state.track.videoId}: ${messageOf(state.failure)}"
        }
        Column {
            Text(line, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = playback::previous) { Text("Previous") }
                TextButton(onClick = { controller?.let { if (playing) it.pause() else it.play() } }, enabled = state is PlayState.Ready) {
                    Text(if (playing) "Pause" else "Play")
                }
                TextButton(onClick = playback::next) { Text("Next") }
            }
        }
    }
}
