package com.excor.preview

import com.excor.Excor
import com.excor.ExcorLogLevel
import com.excor.ExcorMediaFormat
import com.excor.ExcorVideoInfo
import com.excor.ExcorYouTubeVideoResult
import com.excor.excorDefaultHost
import com.excor.excorVideoIdOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.BorderLayout
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.WindowConstants

/**
 * The eXcor preview for the desktop: type a search or paste a YouTube link, then play the audio or the video in the
 * system's player, list the formats or save one. Media is downloaded to a temporary folder first, since the system
 * player can't send the stream's headers. The log shows the library's own messages, which never contain URLs or tokens.
 *
 * Arguments other than `--self-test` are a search or link to open at start.
 *
 * `--self-test <file>` starts the library without a window and writes "OK" to the file: a check that the packaged app
 * and its bundled runtime work.
 */
fun main(args: Array<String>) {
    if (args.firstOrNull() == "--self-test") return selfTest(args.getOrNull(1))
    SwingUtilities.invokeLater {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        PreviewWindow().show(initial = args.joinToString(" ").trim().takeIf { it.isNotEmpty() })
    }
}

private fun selfTest(path: String?) {
    val excor = Excor(excorDefaultHost())
    check(excorVideoIdOf("https://youtu.be/dQw4w9WgXcQ") == "dQw4w9WgXcQ")
    val report = "eXcor Preview OK (Java ${System.getProperty("java.version")}, ${excor::class.simpleName})"
    if (path != null) File(path).writeText(report + "\n") else println(report)
}

private class PreviewWindow {
    private val frame = JFrame("eXcor Preview")
    private val query = JTextField()
    private val status = JLabel("Search YouTube, or paste a link or a video ID.")
    private val results = DefaultListModel<ExcorYouTubeVideoResult>()
    private val resultList = JList(results)
    private val details = JTextArea()
    private val log = JTextArea()
    private val maxHeightPicker = JComboBox(arrayOf(360, 480, 720, 1080, 1440, 2160)).apply { selectedItem = 720 }
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var current: ExcorVideoInfo? = null
    private var formats: List<ExcorMediaFormat> = emptyList()

    // Downloads follow googlevideo's redirects; the final host is checked before anything is written.
    private val downloads = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
    private val excor = Excor(excorDefaultHost(region = region(), language = language()) { level, message -> addLog(level, message) })
    private val tempDir = File(System.getProperty("java.io.tmpdir"), "excor-preview").apply { mkdirs() }

    /** Shows the window; [initial], a search or link from the command line, is opened straight away. */
    fun show(initial: String?) {
        val mono = Font(Font.MONOSPACED, Font.PLAIN, 12)
        details.isEditable = false
        details.lineWrap = true
        details.wrapStyleWord = true
        details.font = mono
        log.isEditable = false
        log.font = mono

        val go = JButton("Go").apply { addActionListener { go() } }
        query.addActionListener { go() }
        val top = JPanel(BorderLayout(8, 8)).apply {
            border = BorderFactory.createEmptyBorder(8, 8, 0, 8)
            add(JLabel("Search, link or video ID:"), BorderLayout.WEST)
            add(query, BorderLayout.CENTER)
            add(go, BorderLayout.EAST)
        }

        resultList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        resultList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean) =
                super.getListCellRendererComponent(list, (value as? ExcorYouTubeVideoResult)?.let(::label) ?: value, index, selected, focus)
        }
        resultList.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) resultList.selectedValue?.let { open(it.videoId) }
        }

        val actions = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JButton("Play audio").apply { addActionListener { current?.let { playAudio(it) } } })
            add(JButton("Play video").apply { addActionListener { current?.let { playVideo(it) } } })
            add(JLabel("up to"))
            add(maxHeightPicker)
            add(JButton("Formats").apply { addActionListener { current?.let { listFormats(it) } } })
            add(JButton("Save format…").apply { addActionListener { current?.let { saveFormat(it) } } })
        }
        val right = JPanel(BorderLayout()).apply {
            add(JScrollPane(details), BorderLayout.CENTER)
            add(actions, BorderLayout.SOUTH)
        }
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, JScrollPane(resultList), right).apply { resizeWeight = 0.4 }
        val logPane = JScrollPane(log).apply { preferredSize = Dimension(0, 170) }
        val bottom = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(0, 8, 8, 8)
            add(status, BorderLayout.NORTH)
            add(logPane, BorderLayout.CENTER)
        }
        frame.contentPane = JPanel(BorderLayout(0, 8)).apply {
            add(top, BorderLayout.NORTH)
            add(split, BorderLayout.CENTER)
            add(bottom, BorderLayout.SOUTH)
        }
        frame.defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        frame.size = Dimension(1100, 720)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
        excor.init()
        if (initial != null) {
            query.text = initial
            go()
        }
    }

    private fun ui(block: () -> Unit) = SwingUtilities.invokeLater(block)

    private fun setStatus(text: String) = ui { status.text = text }

    private fun addLog(level: ExcorLogLevel, message: String) = ui {
        log.insert("${clock.format(Date())} ${level.name.first()} $message\n", 0)
        if (log.document.length > 60_000) log.text = log.text.take(40_000)
    }

    /** Runs [block] as the one current task, cancelling the previous one, and reports failures in the status line. */
    private fun task(label: String, block: suspend () -> Unit) {
        job?.cancel()
        setStatus("$label…")
        job = scope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val message = "$label failed: ${failure.message ?: failure::class.simpleName}"
                setStatus(message)
                addLog(ExcorLogLevel.WARN, message)
            }
        }
    }

    private fun go() {
        val text = query.text.trim()
        if (text.isEmpty()) return
        excorVideoIdOf(text)?.let { return open(it) }
        task("Searching") {
            val videos = excor.search(text)?.videos.orEmpty()
            ui {
                results.clear()
                videos.forEach(results::addElement)
            }
            setStatus(if (videos.isEmpty()) "No videos found." else "${videos.size} videos. Pick one.")
        }
    }

    private fun open(videoId: String) = task("Loading $videoId") {
        val info = excor.videoInfo(videoId) ?: error("YouTube has no video $videoId")
        current = info
        formats = emptyList()
        ui { details.text = describe(info); details.caretPosition = 0 }
        setStatus(readyText(info))
    }

    private fun playAudio(info: ExcorVideoInfo) = task("Finding audio") {
        // M4A, which every system player opens.
        val stream = excor.extract(info.videoId, 160, requireM4a = true) ?: excor.extract(info.videoId, 160) ?: error("no playable audio")
        val file = File(tempDir, "${info.videoId}-audio.${if ("webm" in stream.mimeType) "webm" else "m4a"}")
        download(stream.url, stream.headers, file, "Downloading audio")
        setStatus("Playing ${stream.mimeType.substringBefore(';')} · ${stream.kbps} kbps · ${stream.clientName}")
        openInPlayer(file)
    }

    private fun playVideo(info: ExcorVideoInfo) = task("Finding video") {
        val maxHeight = maxHeightPicker.selectedItem as Int
        if (ffmpegAvailable()) {
            val streams = excor.extractVideo(info.videoId, maxHeight = maxHeight) ?: error("no playable video")
            val video = streams.video
            val videoFile = File(tempDir, "${info.videoId}-video.${if ("webm" in video.mimeType) "webm" else "mp4"}")
            download(video.url, video.headers, videoFile, "Downloading ${video.height}p video")
            val audio = streams.audio
            val output = if (audio == null) videoFile else {
                val audioFile = File(tempDir, "${info.videoId}-audio.${if ("webm" in audio.mimeType) "webm" else "m4a"}")
                download(audio.url, audio.headers, audioFile, "Downloading audio")
                val mp4 = "mp4" in video.mimeType && "mp4" in audio.mimeType
                val muxed = File(tempDir, "${info.videoId}-${video.height}p.${if (mp4) "mp4" else "mkv"}")
                setStatus("Joining video and audio with ffmpeg…")
                mux(videoFile, audioFile, muxed)
                muxed
            }
            setStatus("Playing ${video.qualityLabel ?: "${video.height}p"} ${video.codec} · ${audio?.mimeType?.substringBefore(';') ?: "progressive"} · ${streams.clientName}")
            openInPlayer(output)
        } else {
            // Without ffmpeg, separate video and audio tracks can't be joined, so a format with both is used.
            val list = excor.formats(info.videoId) ?: error("no playable formats")
            val format = list.formats.filter { it.hasVideo && it.hasAudio && (it.height ?: 0) <= maxHeight }.maxByOrNull { it.height ?: 0 }
                ?: error("no format with both picture and sound; install ffmpeg to join separate tracks")
            val file = File(tempDir, "${info.videoId}-${format.height}p.mp4")
            download(format.url, list.headers, file, "Downloading ${format.height}p video")
            setStatus("Playing ${format.height}p with sound in one file (install ffmpeg for higher qualities) · ${list.clientName}")
            openInPlayer(file)
        }
    }

    private fun listFormats(info: ExcorVideoInfo) = task("Listing formats") {
        val list = excor.formats(info.videoId) ?: error("no playable formats")
        formats = list.formats
        ui {
            details.text = describe(info) + "\n\nFormats from ${list.clientName}:\n" + list.formats.joinToString("\n", transform = ::describe)
            details.caretPosition = 0
        }
        setStatus("${list.formats.size} formats from ${list.clientName}")
    }

    private fun saveFormat(info: ExcorVideoInfo) = task("Saving") {
        val list = excor.formats(info.videoId) ?: error("no playable formats")
        var chosen: ExcorMediaFormat? = null
        var target: File? = null
        withContext(SwingDispatcher) {
            val names = list.formats.map(::describe).toTypedArray()
            val pick = JOptionPane.showInputDialog(frame, "Format to save:", "Save format", JOptionPane.PLAIN_MESSAGE, null, names, names.firstOrNull()) as? String
                ?: return@withContext
            val format = list.formats[names.indexOf(pick)]
            val chooser = JFileChooser().apply { selectedFile = File(safeName(info.title) + "." + extension(format)) }
            if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
                chosen = format
                target = chooser.selectedFile
            }
        }
        val format = chosen ?: return@task setStatus("Not saved.")
        val file = target ?: return@task
        download(format.url, list.headers, file, "Saving itag ${format.itag}")
        setStatus("Saved ${file.name} (${file.length() / 1_000_000} MB)")
    }

    /** Downloads [url] in 4 MiB range reads, as players do, reporting progress in the status line. */
    private suspend fun download(url: String, headers: Map<String, String>, file: File, label: String) = withContext(Dispatchers.IO) {
        val chunk = 4L * 1024 * 1024
        var offset = 0L
        var total: Long? = null
        file.outputStream().use { out ->
            while (total == null || offset < total!!) {
                ensureActive()
                val request = Request.Builder().url(url).header("Range", "bytes=$offset-${offset + chunk - 1}").apply {
                    headers.forEach { (name, value) -> header(name, value) }
                }.build()
                val read = downloads.newCall(request).execute().use { response ->
                    check(response.request.url.host.endsWith(".googlevideo.com")) { "media was redirected off googlevideo.com" }
                    check(response.code == 206 || (response.code == 200 && offset == 0L)) { "YouTube answered HTTP ${response.code}" }
                    total = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                        ?: response.body.contentLength().takeIf { response.code == 200 && it > 0 }
                    response.body.byteStream().copyTo(out)
                }
                if (read == 0L) break
                offset += read
                setStatus("$label… ${offset / 1_000_000} of ${total?.div(1_000_000) ?: "?"} MB")
                if (total == null) break
            }
        }
        check(offset > 0) { "nothing was downloaded" }
    }

    private fun ffmpegAvailable(): Boolean =
        runCatching { ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start().apply { inputStream.readBytes() }.waitFor() == 0 }.getOrDefault(false)

    private suspend fun mux(video: File, audio: File, output: File) = withContext(Dispatchers.IO) {
        val process = ProcessBuilder("ffmpeg", "-v", "error", "-y", "-i", video.path, "-i", audio.path, "-c", "copy", output.path)
            .redirectErrorStream(true).start()
        val errors = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "ffmpeg could not join the tracks: ${errors.take(200)}" }
    }

    private fun openInPlayer(file: File) {
        val opened = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN) &&
            runCatching { Desktop.getDesktop().open(file) }.isSuccess
        if (!opened) setStatus("Saved to ${file.path}, but no default player could open it.")
    }
}

/** Swing's event thread as a dispatcher, for the dialogs. */
private object SwingDispatcher : kotlinx.coroutines.CoroutineDispatcher() {
    override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) = SwingUtilities.invokeLater(block)
}

/** The metadata request's playability is the web client's; playback asks other clients, so it is only a hint. */
private fun readyText(info: ExcorVideoInfo): String =
    if (info.playabilityStatus == null || info.playabilityStatus == "OK") "Ready."
    else "Ready. The web client reports ${info.playabilityReason ?: info.playabilityStatus}; playback tries other clients."

private fun region(): String = Locale.getDefault().country.takeIf { it.length == 2 && it.all(Char::isLetter) } ?: "US"

private fun language(): String = Locale.getDefault().language.takeIf { it.length in 2..12 && it.all(Char::isLetter) } ?: "en"

private fun label(result: ExcorYouTubeVideoResult): String =
    listOfNotNull(result.title, result.channelName, result.durationSeconds?.let(::duration), if (result.isLive) "live" else null).joinToString("  ·  ")

private fun describe(info: ExcorVideoInfo): String = buildString {
    appendLine(info.title)
    appendLine(listOfNotNull(info.author, info.durationSeconds?.let(::duration), info.viewCount?.let { "%,d views".format(it) }).joinToString(" · "))
    appendLine("Video ID: ${info.videoId}")
    // From the web client that answers metadata requests; playback asks other clients, which may still serve it.
    info.playabilityStatus?.let { appendLine("Web client playability: $it${info.playabilityReason?.let { reason -> " ($reason)" } ?: ""}") }
    info.description?.takeIf { it.isNotBlank() }?.let { append("\n").append(it.take(1_500)) }
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

private fun safeName(title: String): String =
    title.map { if (it in "\\/:*?\"<>|" || it.code < 32) '_' else it }.joinToString("").trim().trim('.').take(120).ifEmpty { "excor" }

private fun duration(seconds: Long): String =
    if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
