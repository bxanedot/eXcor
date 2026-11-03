package com.excor.preview

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.excor.ExcorFormatList
import com.excor.ExcorLogLevel
import com.excor.ExcorMediaFormat
import com.excor.ExcorVideoCodec
import com.excor.ExcorVideoInfo
import com.excor.ExcorYouTubeSearchFilter
import com.excor.excorDefaultHost
import com.excor.excorVideoIdOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The eXcor preview: search YouTube Music (with its own filters: songs, videos, albums, artists, playlists…) or
 * YouTube, or paste a link; open albums, playlists and artists; play a video's audio or picture; download audio,
 * video or any listed format to Downloads/eXcor. The log shows the library's own messages, which never contain URLs
 * or tokens.
 */
@UnstableApi
class MainActivity : ComponentActivity() {
    private val log = mutableStateListOf<String>()
    private var source by mutableStateOf(Source.MUSIC)
    private var query by mutableStateOf("")
    private var status by mutableStateOf("Search YouTube Music or YouTube, or paste a link or a video ID.")
    private var downloadStatus by mutableStateOf("")
    /** The pages opened so far; the last is shown, and Back goes to the one before. */
    private val pages = mutableStateListOf<Listing>()
    private var info by mutableStateOf<ExcorVideoInfo?>(null)
    private var formatList by mutableStateOf<ExcorFormatList?>(null)
    private var maxHeight by mutableIntStateOf(720)
    private var showVideo by mutableStateOf(false)
    private var job: Job? = null
    private var downloadJob: Job? = null
    private var pendingDownload: (() -> Unit)? = null
    private var signedIn by mutableStateOf(false)
    private var signingIn by mutableStateOf(false)
    private var cookieDraft by mutableStateOf("")

    private lateinit var excor: Excor
    private lateinit var browser: Browser
    // The account's YouTube cookies when signed in: app-private, outside backups, never logged.
    private val cookieFile by lazy { File(noBackupFilesDir, "youtube-cookie") }
    private lateinit var player: ExoPlayer
    private val http = mediaClient()
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    // Android 8 and 9 only: saving into the shared Downloads folder needs this permission.
    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val download = pendingDownload
        pendingDownload = null
        if (granted) download?.invoke() else downloadStatus = "Saving to Downloads needs the storage permission on Android 8 and 9."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connect(cookieFile.takeIf(File::isFile)?.readText())
        player = ExoPlayer.Builder(this).build()
        sharedText(intent)?.let { query = it }
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Screen() }
            }
        }
    }

    private fun connect(cookie: String?) {
        val region = Locale.getDefault().country.takeIf { it.length == 2 && it.all(Char::isLetter) } ?: "US"
        val language = Locale.getDefault().language.takeIf { it.length in 2..12 && it.all(Char::isLetter) } ?: "en"
        excor = Excor(excorDefaultHost(region = region, language = language, filesDir = cacheDir, cookie = cookie) { level, message -> addLog(level, message) })
        excor.init()
        browser = Browser(excor)
        signedIn = cookie != null
    }

    /** Takes the Cookie header of a signed-in youtube.com browser tab, pasted by the user. */
    private fun signIn() {
        val cookie = cookieDraft.trim().removePrefix("Cookie:").trim()
        status = when {
            "SAPISID=" !in cookie && "__Secure-3PAPISID=" !in cookie -> "That has no SAPISID cookie, so YouTube would not treat it as signed in."
            cookie.any { it < ' ' } -> "Paste the header value on one line."
            else -> {
                cookieFile.writeText(cookie)
                connect(cookie)
                signingIn = false
                "Signed in. Requests now use your account."
            }
        }
        cookieDraft = ""
    }

    private fun signOut() {
        cookieFile.delete()
        connect(null)
        status = "Signed out."
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
                status = explain(label, failure)
                addLog(ExcorLogLevel.WARN, status)
            }
        }
    }

    private fun go() {
        val text = query.trim()
        if (text.isEmpty()) return
        val videoId = excorVideoIdOf(text)
        if (videoId != null) return open(videoId)
        task("Searching ${source.label}") { show(browser.search(source, text), replace = true) }
    }

    /** Shows [listing], as a new page or in place of every page so far. */
    private fun show(listing: Listing, replace: Boolean) {
        if (replace) pages.clear()
        pages.add(listing)
        status = if (listing.rowCount == 0) "Nothing found." else "${listing.rowCount} results. Tap one."
    }

    private fun filter(listing: Listing, chip: ExcorYouTubeSearchFilter) {
        val text = listing.query ?: return
        // The selected chip carries no params; tapping it again clears the filter.
        task("Filtering") {
            val filtered = browser.search(Source.MUSIC, text, filterParams = if (chip.selected) null else chip.params)
            pages.removeAt(pages.lastIndex)
            show(filtered, replace = false)
        }
    }

    private fun openRow(target: Target) {
        when (target) {
            is Target.Video -> open(target.videoId)
            is Target.Playlist, is Target.Artist -> task("Opening") { show(browser.open(target), replace = false) }
            is Target.Unsupported -> status = target.reason
        }
    }

    private fun more() {
        val listing = pages.lastOrNull() ?: return
        task("Loading more") {
            val longer = browser.more(listing)
            pages[pages.lastIndex] = longer
            status = "${longer.rowCount} results."
        }
    }

    private fun open(videoId: String) = task("Loading $videoId") {
        formatList = null
        info = excor.videoInfo(videoId) ?: error("YouTube has no video $videoId")
        // Not the metadata client's playability: playback asks other clients, and reports its own failure.
        status = "Ready."
    }

    private fun mediaSource(url: String, headers: Map<String, String>): MediaSource {
        val factory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
        return ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(url))
    }

    private fun playAudio(videoId: String) = task("Finding audio") {
        val stream = excor.extract(videoId, 160) ?: error("no playable audio")
        showVideo = false
        player.setMediaSource(mediaSource(stream.url, stream.headers))
        player.prepare()
        player.play()
        status = "Playing ${stream.mimeType.substringBefore(';')} · ${stream.kbps} kbps · ${stream.clientName}"
    }

    private fun playVideo(videoId: String) = task("Finding video") {
        val streams = excor.extractVideo(videoId, maxHeight = maxHeight) ?: error("no playable video")
        val video = streams.video
        val picture = mediaSource(video.url, video.headers)
        val audio = streams.audio
        showVideo = true
        player.setMediaSource(if (audio == null) picture else MergingMediaSource(picture, mediaSource(audio.url, audio.headers)))
        player.prepare()
        player.play()
        status = "Playing ${video.qualityLabel ?: "${video.height}p"} ${video.codec} · " +
            (audio?.let { "${it.mimeType.substringBefore(';')} ${it.kbps} kbps" } ?: "progressive") + " · ${streams.clientName}"
    }

    private fun listFormats(videoId: String) = task("Listing formats") {
        val list = excor.formats(videoId) ?: error("no playable formats")
        formatList = list
        status = "${list.formats.size} formats from ${list.clientName}. Tap one to download it as it is."
    }

    /**
     * Runs one download at a time, apart from browsing, so searching meanwhile doesn't stop it. [block] returns where
     * the file was saved. Keep the app open until it finishes.
     */
    private fun startDownload(label: String, block: suspend (File) -> String) {
        if (Saver.needsStoragePermission && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = { startDownload(label, block) }
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        downloadJob?.cancel()
        downloadStatus = "$label…"
        downloadJob = lifecycleScope.launch {
            val work = File(cacheDir, "downloads/${System.nanoTime()}")
            try {
                check(work.mkdirs()) { "no room for a working folder" }
                downloadStatus = "Saved to ${block(work)}"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                downloadStatus = explain(label, failure)
                addLog(ExcorLogLevel.WARN, downloadStatus)
            } finally {
                work.deleteRecursively()
            }
        }
    }

    private suspend fun fetch(url: String, headers: Map<String, String>, file: File, label: String) =
        download(http, url, headers, file) { done, total -> runOnUiThread { downloadStatus = "$label… ${megabytes(done, total)}" } }

    private suspend fun saveFile(file: File, name: String, mimeType: String): String =
        withContext(Dispatchers.IO) { Saver.save(this@MainActivity, file, name, mimeType) }

    /** M4A where YouTube offers it, rewritten as a plain .m4a that every music player reads; Opus in WebM otherwise. */
    private fun downloadAudio(video: ExcorVideoInfo) = startDownload("Downloading audio") { work ->
        val stream = excor.extract(video.videoId, 160, requireM4a = true) ?: excor.extract(video.videoId, 160) ?: error("no playable audio")
        val raw = File(work, "audio")
        fetch(stream.url, stream.headers, raw, "Downloading audio")
        if ("mp4" in stream.mimeType) {
            downloadStatus = "Writing a plain .m4a…"
            val plain = File(work, "audio.m4a")
            withContext(Dispatchers.Default) { TrackJoiner.join(listOf(raw), plain, TrackJoiner.Container.MP4) }
            saveFile(plain, "${safeName(video.title)}.m4a", "audio/mp4")
        } else {
            saveFile(raw, "${safeName(video.title)}.webm", "audio/webm")
        }
    }

    /**
     * The picture at most [maxHeight] and its sound, joined into one file on the phone. H.264 with AAC (MP4) plays
     * everywhere; above 1080p YouTube has only VP9, joined with Opus into WebM, which Android writes from version 10.
     */
    private fun downloadVideo(video: ExcorVideoInfo) = startDownload("Downloading video") { work ->
        val webm = TrackJoiner.canJoin(TrackJoiner.Container.WEBM)
        val codecs = if (webm) setOf(ExcorVideoCodec.H264, ExcorVideoCodec.VP9) else setOf(ExcorVideoCodec.H264)
        val streams = excor.extractVideo(video.videoId, maxHeight = maxHeight, codecs = codecs)
            ?: error("no playable ${if (webm) "H.264 or VP9" else "H.264"} video up to ${maxHeight}p")
        val track = streams.video
        val container = if ("webm" in track.mimeType) TrackJoiner.Container.WEBM else TrackJoiner.Container.MP4
        val name = "${safeName(video.title)} ${track.height}p.${container.extension}"
        val picture = File(work, "video")
        fetch(track.url, track.headers, picture, "Downloading ${track.height}p video")
        val audio = streams.audio ?: return@startDownload saveFile(picture, name, container.mimeType) // progressive: sound included
        check(("webm" in audio.mimeType) == (container == TrackJoiner.Container.WEBM)) {
            "YouTube offered this picture and sound in different containers; try another height"
        }
        val sound = File(work, "audio")
        fetch(audio.url, audio.headers, sound, "Downloading audio")
        downloadStatus = "Joining picture and sound…"
        val joined = File(work, "joined.${container.extension}")
        withContext(Dispatchers.Default) { TrackJoiner.join(listOf(picture, sound), joined, container) }
        saveFile(joined, name, container.mimeType)
    }

    /** One listed format, saved as YouTube serves it. */
    private fun downloadFormat(video: ExcorVideoInfo, list: ExcorFormatList, format: ExcorMediaFormat) = startDownload("Downloading itag ${format.itag}") { work ->
        val file = File(work, "format")
        fetch(format.url, list.headers, file, "Downloading itag ${format.itag}")
        saveFile(file, "${safeName(video.title)} ${format.itag}.${extension(format)}", format.mimeType.substringBefore(';'))
    }

    @Composable
    private fun Screen() {
        BackHandler(enabled = pages.size > 1) { pages.removeAt(pages.lastIndex) }
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("eXcor Preview", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
                if (signedIn) TextButton(onClick = ::signOut) { Text("Signed in to YouTube · Sign out") }
                else TextButton(onClick = { signingIn = !signingIn }) { Text("Sign in to YouTube") }
                if (signingIn && !signedIn) {
                    Text(
                        "Paste the Cookie header of a youtube.com request from a browser where you are signed in (developer tools, " +
                            "Network, any youtube.com request, Request headers, Cookie). It stays on this phone, in the app's private storage.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = cookieDraft,
                            onValueChange = { cookieDraft = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("Cookie header") },
                            visualTransformation = PasswordVisualTransformation(),
                        )
                        Button(onClick = ::signIn, modifier = Modifier.padding(top = 8.dp)) { Text("Save") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Source.entries.forEach { option ->
                        FilterChip(selected = source == option, onClick = { source = option }, label = { Text(option.label) })
                    }
                }
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
                // Two lines each; failures are logged in full below.
                Text(status, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (downloadStatus.isNotEmpty()) Text(downloadStatus, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            item {
                // One player view: a 16:9 picture for video, just the controls for audio.
                AndroidView(
                    factory = { context -> PlayerView(context).also { it.player = player } },
                    modifier = if (showVideo) Modifier.fillMaxWidth().aspectRatio(16f / 9f) else Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
            info?.let { video -> item { Details(video) } }
            formatList?.let { list ->
                val video = info
                if (video != null && list.videoId == video.videoId) {
                    item { Text("Formats (tap to download)", style = MaterialTheme.typography.titleMedium) }
                    items(list.formats) { format ->
                        Text(
                            describe(format),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().clickable { downloadFormat(video, list, format) }.padding(vertical = 4.dp),
                        )
                    }
                }
            }
            pages.lastOrNull()?.let { listing -> listingItems(listing) }
            item { HorizontalDivider(); Text("Log", style = MaterialTheme.typography.titleMedium) }
            items(log) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
        }
    }

    private fun LazyListScope.listingItems(listing: Listing) {
        item {
            HorizontalDivider()
            if (pages.size > 1) TextButton(onClick = { pages.removeAt(pages.lastIndex) }) { Text("‹ Back") }
            Text(listing.title, style = MaterialTheme.typography.titleMedium)
            listing.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (listing.filters.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listing.filters.forEach { chip ->
                        FilterChip(selected = chip.selected, enabled = !chip.disabled, onClick = { filter(listing, chip) }, label = { Text(chip.label) })
                    }
                }
            }
        }
        listing.sections.forEach { section ->
            section.title?.let { title -> item { Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) } }
            items(section.rows) { row ->
                Column(Modifier.fillMaxWidth().clickable { openRow(row.target) }.padding(vertical = 6.dp)) {
                    Text(row.title, style = MaterialTheme.typography.bodyLarge)
                    Text("${row.badge} · ${row.detail}".removeSuffix(" · "), style = MaterialTheme.typography.bodySmall)
                }
            }
            section.more?.let { target -> item { TextButton(onClick = { openRow(target) }) { Text("Show all ${section.title ?: ""}".trim()) } } }
        }
        if (listing.next != null) item { OutlinedButton(onClick = ::more) { Text("More") } }
    }

    @OptIn(ExperimentalLayoutApi::class)
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { playAudio(video.videoId) }) { Text("Play audio") }
                Button(onClick = { playVideo(video.videoId) }) { Text("Play video") }
                OutlinedButton(onClick = { listFormats(video.videoId) }) { Text("Formats") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { downloadAudio(video) }) { Text("Download audio") }
                OutlinedButton(onClick = { downloadVideo(video) }) { Text("Download video") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(360, 480, 720, 1080, 1440, 2160).forEach { height ->
                    FilterChip(selected = maxHeight == height, onClick = { maxHeight = height }, label = { Text("${height}p") })
                }
            }
        }
    }
}

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

private fun extension(format: ExcorMediaFormat): String = when {
    format.mimeType.startsWith("audio/mp4") -> "m4a"
    format.mimeType.startsWith("audio/webm") -> "webm"
    format.mimeType.startsWith("video/webm") -> "webm"
    else -> "mp4"
}
