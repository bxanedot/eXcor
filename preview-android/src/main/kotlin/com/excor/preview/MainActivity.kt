package com.excor.preview

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.excor.Excor
import com.excor.ExcorLogLevel
import com.excor.ExcorMediaFormat
import com.excor.ExcorVideoInfo
import com.excor.ExcorYouTubeVideoResult
import com.excor.excorDefaultHost
import com.excor.excorVideoIdOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The eXcor preview: type a search or paste a YouTube link, then play the audio or the video, or list the formats.
 * The log shows the library's own messages (client choices, probe statuses), which never contain URLs or tokens.
 */
@UnstableApi
class MainActivity : ComponentActivity() {
    private val log = mutableStateListOf<String>()
    private var query by mutableStateOf("")
    private var status by mutableStateOf("Search YouTube, or paste a link or a video ID.")
    private var results by mutableStateOf<List<ExcorYouTubeVideoResult>>(emptyList())
    private var info by mutableStateOf<ExcorVideoInfo?>(null)
    private var formats by mutableStateOf<List<ExcorMediaFormat>>(emptyList())
    private var maxHeight by mutableIntStateOf(720)
    private var showVideo by mutableStateOf(false)
    private var job: Job? = null

    private lateinit var excor: Excor
    private lateinit var player: ExoPlayer
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val region = Locale.getDefault().country.takeIf { it.length == 2 && it.all(Char::isLetter) } ?: "US"
        val language = Locale.getDefault().language.takeIf { it.length in 2..12 && it.all(Char::isLetter) } ?: "en"
        excor = Excor(excorDefaultHost(region = region, language = language, filesDir = cacheDir) { level, message -> addLog(level, message) })
        excor.init()
        player = ExoPlayer.Builder(this).build()
        sharedText(intent)?.let { query = it }
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Screen() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedText(intent)?.let { query = it; go() }
    }

    override fun onStop() {
        super.onStop()
        player.pause()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    private fun sharedText(intent: Intent?): String? =
        intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.take(500)

    private fun addLog(level: ExcorLogLevel, message: String) = runOnUiThread {
        log.add(0, "${clock.format(Date())} ${level.name.first()} $message")
        if (log.size > 300) log.removeRange(300, log.size)
    }

    /** Runs [block] as the one current task, cancelling the previous one, and reports failures in the status line. */
    private fun task(label: String, block: suspend () -> Unit) {
        job?.cancel()
        status = "$label…"
        job = lifecycleScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                status = "$label failed: ${failure.message ?: failure::class.simpleName}"
                addLog(ExcorLogLevel.WARN, status)
            }
        }
    }

    private fun go() {
        val text = query.trim()
        if (text.isEmpty()) return
        val videoId = excorVideoIdOf(text)
        if (videoId != null) return open(videoId)
        task("Searching") {
            val page = excor.search(text)
            results = page?.videos.orEmpty()
            status = if (results.isEmpty()) "No videos found." else "${results.size} videos. Tap one."
        }
    }

    private fun open(videoId: String) = task("Loading $videoId") {
        formats = emptyList()
        info = excor.videoInfo(videoId) ?: error("YouTube has no video $videoId")
        // The metadata request's playability is the web client's; playback asks other clients, so it is only a hint.
        val video = info
        status = if (video == null || video.playabilityStatus == null || video.playabilityStatus == "OK") "Ready."
        else "Ready. The web client reports ${video.playabilityReason ?: video.playabilityStatus}; playback tries other clients."
    }

    private fun source(url: String, headers: Map<String, String>): MediaSource {
        val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
        return ProgressiveMediaSource.Factory(http).createMediaSource(MediaItem.fromUri(url))
    }

    private fun playAudio(videoId: String) = task("Finding audio") {
        val stream = excor.extract(videoId, 160) ?: error("no playable audio")
        showVideo = false
        player.setMediaSource(source(stream.url, stream.headers))
        player.prepare()
        player.play()
        status = "Playing ${stream.mimeType.substringBefore(';')} · ${stream.kbps} kbps · ${stream.clientName}"
    }

    private fun playVideo(videoId: String) = task("Finding video") {
        val streams = excor.extractVideo(videoId, maxHeight = maxHeight) ?: error("no playable video")
        val video = streams.video
        val picture = source(video.url, video.headers)
        val audio = streams.audio
        showVideo = true
        player.setMediaSource(if (audio == null) picture else MergingMediaSource(picture, source(audio.url, audio.headers)))
        player.prepare()
        player.play()
        status = "Playing ${video.qualityLabel ?: "${video.height}p"} ${video.codec} · " +
            (audio?.let { "${it.mimeType.substringBefore(';')} ${it.kbps} kbps" } ?: "progressive") + " · ${streams.clientName}"
    }

    private fun listFormats(videoId: String) = task("Listing formats") {
        val list = excor.formats(videoId) ?: error("no playable formats")
        formats = list.formats
        status = "${list.formats.size} formats from ${list.clientName}"
    }

    @Composable
    private fun Screen() {
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("eXcor Preview", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Search, link or video ID") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { go() }),
                    )
                    Button(onClick = ::go, modifier = Modifier.padding(top = 8.dp)) { Text("Go") }
                }
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }
            item {
                // One player view: a 16:9 picture for video, just the controls for audio.
                AndroidView(
                    factory = { context -> PlayerView(context).also { it.player = player } },
                    modifier = if (showVideo) Modifier.fillMaxWidth().aspectRatio(16f / 9f) else Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
            info?.let { video -> item { Details(video) } }
            if (formats.isNotEmpty()) {
                item { Text("Formats", style = MaterialTheme.typography.titleMedium) }
                items(formats) { Text(describe(it), fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
            }
            if (results.isNotEmpty()) {
                item { HorizontalDivider(); Text("Results", style = MaterialTheme.typography.titleMedium) }
                items(results, key = { it.videoId }) { result ->
                    Column(Modifier.fillMaxWidth().clickable { open(result.videoId) }.padding(vertical = 6.dp)) {
                        Text(result.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOfNotNull(result.channelName, result.durationSeconds?.let(::duration), if (result.isLive) "live" else null).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item { HorizontalDivider(); Text("Log", style = MaterialTheme.typography.titleMedium) }
            items(log) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
        }
    }

    @Composable
    private fun Details(video: ExcorVideoInfo) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HorizontalDivider()
            Text(video.title, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(video.author, video.durationSeconds?.let(::duration), video.viewCount?.let { "%,d views".format(it) }, video.videoId)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { playAudio(video.videoId) }) { Text("Play audio") }
                Button(onClick = { playVideo(video.videoId) }) { Text("Play video") }
                OutlinedButton(onClick = { listFormats(video.videoId) }) { Text("Formats") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(360, 480, 720, 1080, 2160).forEach { height ->
                    FilterChip(selected = maxHeight == height, onClick = { maxHeight = height }, label = { Text("${height}p") })
                }
            }
        }
    }
}

private fun duration(seconds: Long): String =
    if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)

private fun describe(format: ExcorMediaFormat): String {
    val kind = when {
        format.hasVideo && format.hasAudio -> "a+v"
        format.hasVideo -> "video"
        else -> "audio"
    }
    val picture = format.height?.let { "${it}p${format.fps?.takeIf { fps -> fps > 30 } ?: ""} ${format.videoCodec}" }
    val size = format.contentLength?.let { "%.1f MB".format(it / 1_000_000.0) }
    return listOfNotNull("${format.itag}".padEnd(4), kind.padEnd(5), picture, format.mimeType.substringBefore(';'), "${format.kbps} kbps", size)
        .joinToString(" · ")
}
