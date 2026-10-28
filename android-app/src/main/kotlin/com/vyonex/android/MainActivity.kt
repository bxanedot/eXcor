package com.vyonex.android

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.vyonex.VyonExListPage
import com.vyonex.VyonExChannelPage
import com.vyonex.VyonExChannelTab
import com.vyonex.VyonExRef
import com.vyonex.VyonExResultKind
import com.vyonex.VyonExSearchResult
import com.vyonex.VyonExUnplayableException
import com.vyonex.app.Failure
import com.vyonex.app.PlayState
import com.vyonex.app.Playback
import com.vyonex.app.Track
import com.vyonex.app.appendPage
import com.vyonex.app.bylineOf
import com.vyonex.app.clockOf
import com.vyonex.app.queueFrom
import com.vyonex.app.suggestsFor
import com.vyonex.app.toTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What a link opened: a title, who it is by, and its tracks. */
private data class Page(val title: String?, val byline: String?, val tracks: List<Track>, val more: (suspend () -> Page)? = null)

/** A page of a playlist or mix, and the pages after it while YouTube has more. */
private fun VyonEx.listPage(title: String?, byline: String?, page: VyonExListPage, next: suspend VyonEx.(String) -> VyonExListPage): Page =
    Page(title, byline, page.items.map(::trackOf), page.continuation?.let { token -> { listPage(title, byline, next(token), next) } })

/** A page of a channel's videos, and the pages after it. */
private fun VyonEx.channelVideosPage(title: String?, byline: String?, page: VyonExChannelPage): Page =
    Page(title, byline, page.items.mapNotNull { it.toTrack() }, page.continuation?.let { token -> { channelVideosPage(title, byline, channelTabMore(token)) } })

private sealed interface Opened {
    data object Nothing : Opened
    data object Loading : Opened

    /** A page; [back] is the search it was opened from, to go back to. */
    data class Shown(val page: Page, val back: Results? = null) : Opened

    /** YouTube's results for [query]; [continuation] asks for more. */
    data class Results(val query: String, val results: List<VyonExSearchResult>, val continuation: String?) : Opened
    data class Problem(val message: String) : Opened
}

private fun trackOf(item: VyonExListItem, artwork: String? = null) =
    Track(item.videoId, item.title, item.channelName, item.durationSeconds, item.thumbnails.maxByOrNull { it.width }?.url ?: artwork)

/** The page for [ref]: a video, a playlist, an album, a mix or an artist's top songs; null when YouTube has none. */
private suspend fun VyonEx.pageOf(ref: VyonExRef): Page? = when (ref) {
    is VyonExRef.Video -> metadata(ref.videoId)?.let { m ->
        Page(m.title, m.author, listOf(Track(ref.videoId, m.title, m.author, m.durationSeconds, m.thumbnails.maxByOrNull { it.width }?.url)))
    }
    is VyonExRef.Playlist -> playlist(ref.playlistId)?.let { listPage(it.title, it.ownerName, it.firstPage, VyonEx::playlistMore) }
    is VyonExRef.Album -> album(ref.browseId)?.let { album ->
        val cover = album.thumbnails.maxByOrNull { it.width }?.url
        Page(album.title, listOfNotNull(album.artist, album.year?.toString()).joinToString(" · "), album.tracks.map { trackOf(it, cover) })
    }
    is VyonExRef.Mix -> mix(ref.playlistId)?.let { listPage(it.title, null, it.firstPage, VyonEx::mixMore) }
    is VyonExRef.Channel -> artist(ref.channelId)?.let { Page(it.name, "Top songs", it.topSongs.map(::trackOf)) }
}

/** A channel's page: its videos tab, first page. */
private suspend fun VyonEx.channelPageOf(channelId: String): Page? = channel(channelId)?.let { channel ->
    channelVideosPage(channel.title, channel.handle, channelTab(channelId, VyonExChannelTab.VIDEOS))
}

/** The page a search result opens: a playlist, album, artist or channel; videos play instead. */
private suspend fun VyonEx.pageOf(result: VyonExSearchResult): Page? = when (result.kind) {
    VyonExResultKind.VIDEO -> null
    VyonExResultKind.PLAYLIST -> pageOf(VyonExRef.Playlist(result.id))
    VyonExResultKind.ALBUM -> pageOf(VyonExRef.Album(result.id))
    VyonExResultKind.ARTIST -> pageOf(VyonExRef.Channel(result.id))
    VyonExResultKind.CHANNEL -> channelPageOf(result.id)
}

/** The page for a SoundCloud link: a set and its tracks, or one track; null when SoundCloud has none. */
private suspend fun VyonEx.soundCloudPageOf(url: String): Page? =
    if ("/sets/" in url) {
        soundCloud.playlist(url)?.let { set -> Page(set.title, set.uploader, set.tracks.mapNotNull { it.toTrack() }) }
    } else {
        soundCloud.track(url)?.let { track -> Page(track.title, track.uploader, listOfNotNull(track.toTrack())) }
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
    Failure.NETWORK -> "Could not reach it. Check the connection."
    Failure.DRM -> "SoundCloud only offers this one encrypted (DRM), which VyonEx does not play."
}

class MainActivity : ComponentActivity() {
    private var link by mutableStateOf("")
    private var opened by mutableStateOf<Opened>(Opened.Nothing)
    private var connecting: ListenableFuture<MediaController>? = null
    private var controller by mutableStateOf<MediaController?>(null)
    private var opening: Job? = null
    private var playing by mutableStateOf(false)
    private var afterPermissions: (() -> Unit)? = null

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val then = afterPermissions
        afterPermissions = null
        // The notification permission only shows progress; below Android 10 saving needs the storage permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || granted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            then?.invoke()
        } else {
            Toast.makeText(this, "VyonEx needs storage access to save downloads.", Toast.LENGTH_LONG).show()
        }
    }

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** Asks for what downloading uses and has not been granted, then runs [then]. */
    private fun withPermissions(then: () -> Unit) {
        val needed = buildList {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filterNot(::granted)
        if (needed.isEmpty()) {
            then()
        } else {
            afterPermissions = then
            askPermissions.launch(needed.toTypedArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as VyonExApp
        sharedLink(intent)?.let { link = it }
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Screen(app.vyonEx, app.playback, app.downloads)
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
    private fun Screen(vyonEx: VyonEx, playback: Playback, downloads: Downloads) {
        val scope = rememberCoroutineScope()
        val state by playback.state.collectAsStateWithLifecycle()
        val jobs by downloads.jobs.collectAsStateWithLifecycle()
        var downloading by remember { mutableStateOf<List<Track>?>(null) }
        var suggestions by remember { mutableStateOf(emptyList<String>()) }
        // What was last opened or searched, so its own suggestions do not come back over its results.
        var asked by remember { mutableStateOf("") }
        /** Shows Loading, then what [load] finds; a load started before this one and still running is dropped. */
        fun show(load: suspend () -> Opened) {
            suggestions = emptyList()
            opened = Opened.Loading
            opening?.cancel()
            opening = scope.launch {
                opened = try {
                    load()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: VyonExUnplayableException) {
                    Opened.Problem(e.youtubeReason ?: "YouTube does not show this.")
                } catch (e: Exception) {
                    Opened.Problem("Could not load that. Check the connection.")
                }
            }
        }
        var loadingMore by remember { mutableStateOf(false) }
        /**
         * Appends what [load] fetches to what is shown, in place, so the list keeps its place. Only while [shown] is
         * still what is shown; a failure leaves it as it was and says so.
         */
        fun <T : Opened> more(shown: T, load: suspend () -> T) {
            if (loadingMore) return
            loadingMore = true
            scope.launch {
                try {
                    val next = load()
                    if (opened == shown) opened = next
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Could not load more. Check the connection.", Toast.LENGTH_SHORT).show()
                } finally {
                    loadingMore = false
                }
            }
        }
        // A YouTube or SoundCloud link opens; anything else is searched on YouTube.
        fun open() {
            val text = link.trim()
            asked = text
            val ref = vyonEx.parse(text)
            show {
                val page = when {
                    ref != null -> vyonEx.pageOf(ref)
                    vyonEx.soundCloud.isLink(text) -> vyonEx.soundCloudPageOf(text)
                    else -> null
                }
                when {
                    page != null -> Opened.Shown(page)
                    "://" in text -> Opened.Problem("There is nothing at that link.")
                    // Words, or a bare ID YouTube has nothing for: a search.
                    else -> vyonEx.search(text).let { found ->
                        if (found.results.isEmpty()) Opened.Problem("Nothing found for \"$text\".") else Opened.Results(text, found.results, found.continuation)
                    }
                }
            }
        }
        // What YouTube's search box would suggest, once typing pauses.
        LaunchedEffect(link) {
            suggestions = emptyList()
            val text = link.trim()
            if (!suggestsFor(text) || text == asked) return@LaunchedEffect
            delay(300)
            suggestions = try {
                vyonEx.suggestions(text).take(5)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                label = { Text("Search, or a YouTube, YouTube Music or SoundCloud link") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = ::open, enabled = link.isNotBlank()) { Text("Go") }
            suggestions.forEach { suggestion ->
                Text(
                    suggestion,
                    Modifier.fillMaxWidth().clickable {
                        link = suggestion
                        open()
                    }.padding(vertical = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (val now = opened) {
                Opened.Nothing -> Unit
                Opened.Loading -> CircularProgressIndicator()
                is Opened.Problem -> Text(now.message, color = MaterialTheme.colorScheme.error)
                is Opened.Results -> LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(now.results) { index, result ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                Modifier.weight(1f).clickable {
                                    if (result.kind == VyonExResultKind.VIDEO) {
                                        playback.play(queueFrom(now.results, index))
                                    } else {
                                        show { vyonEx.pageOf(result)?.let { Opened.Shown(it, back = now) } ?: Opened.Problem("YouTube has nothing there.") }
                                    }
                                }.padding(vertical = 8.dp),
                            ) {
                                Text(result.title ?: result.id, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(bylineOf(result), style = MaterialTheme.typography.bodySmall)
                            }
                            result.toTrack()?.let { track -> TextButton(onClick = { downloading = listOf(track) }) { Text("Download") } }
                        }
                    }
                    now.continuation?.let { token ->
                        item {
                            TextButton(enabled = !loadingMore, onClick = {
                                more(now) {
                                    val found = vyonEx.searchMore(token)
                                    // Pages can repeat a result; each shows once.
                                    now.copy(results = (now.results + found.results).distinctBy { it.kind to it.id }, continuation = found.continuation)
                                }
                            }) { Text(if (loadingMore) "Loading…" else "More results") }
                        }
                    }
                }
                is Opened.Shown -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    now.back?.let { results -> TextButton(onClick = { opened = results }) { Text("Back to results for \"${results.query}\"") } }
                    Text(now.page.title ?: "", style = MaterialTheme.typography.titleLarge)
                    now.page.byline?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // With more pages to load, the buttons take what is loaded and say so.
                        val which = if (now.page.more == null) "all" else "${now.page.tracks.size} loaded"
                        Button(onClick = { playback.play(now.page.tracks) }, enabled = now.page.tracks.isNotEmpty()) { Text("Play $which") }
                        OutlinedButton(onClick = { downloading = now.page.tracks }, enabled = now.page.tracks.isNotEmpty()) { Text("Download $which") }
                    }
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(now.page.tracks) { index, track ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).clickable { playback.play(now.page.tracks, index) }.padding(vertical = 8.dp)) {
                                    Text(track.title ?: track.id, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(track.artist, track.durationSeconds?.let(::clockOf)).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                TextButton(onClick = { downloading = listOf(track) }) { Text("Download") }
                            }
                        }
                        now.page.more?.let { load ->
                            item {
                                TextButton(enabled = !loadingMore, onClick = {
                                    more(now) {
                                        val next = load()
                                        now.copy(page = now.page.copy(tracks = appendPage(now.page.tracks, next.tracks), more = next.more))
                                    }
                                }) { Text(if (loadingMore) "Loading…" else "Load more") }
                            }
                        }
                    }
                }
            }
            if (opened !is Opened.Shown) Spacer(Modifier.weight(1f))
            DownloadList(jobs, downloads::clearFinished)
            NowPlaying(state, playback)
        }
        downloading?.let { tracks ->
            DownloadDialog(tracks, vyonEx, downloads.writesOpus, onDismiss = { downloading = null }) { choice, label ->
                downloading = null
                withPermissions { downloads.add(tracks, choice, label) }
            }
        }
    }

    @Composable
    private fun NowPlaying(state: PlayState, playback: Playback) {
        val line = when (state) {
            PlayState.Idle -> return
            is PlayState.Loading -> "Loading ${state.track.title ?: state.track.id}…"
            is PlayState.Ready -> listOfNotNull(state.track.title, state.track.artist).joinToString(" · ")
            is PlayState.Failed -> "${state.track.title ?: state.track.id}: ${messageOf(state.failure)}"
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
