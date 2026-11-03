package com.excor.preview

import com.excor.Excor
import com.excor.ExcorLogLevel
import com.excor.ExcorMediaFormat
import com.excor.ExcorVideoInfo
import com.excor.ExcorYouTubeMusicItem
import com.excor.ExcorYouTubeMusicItemType
import com.excor.ExcorYouTubeSearchFilter
import com.excor.excorDefaultHost
import com.excor.excorVideoIdOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.prefs.Preferences
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
import javax.swing.JPasswordField
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JToggleButton
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.WindowConstants

/**
 * The eXcor preview for the desktop: search YouTube Music (with its own filters: songs, videos, albums, artists,
 * playlists…) or YouTube, or paste a YouTube link; open albums, playlists and artists; then play a video's audio or
 * picture in the system's player, list its formats or save one. Media is downloaded to a temporary folder first, since the system
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
    // The shared browsing code: an album opens as its playlist, an artist as its page.
    val album = ExcorYouTubeMusicItem(ExcorYouTubeMusicItemType.ALBUM, "Album", browseId = "MPREb_x", playlistId = "OLAK5uy_x")
    check(musicRow(album).target == Target.Playlist("OLAK5uy_x"))
    check(musicRow(ExcorYouTubeMusicItem(ExcorYouTubeMusicItemType.ARTIST, "Artist", browseId = "UCx")).target == Target.Artist("UCx"))
    val report = "eXcor Preview OK (Java ${System.getProperty("java.version")}, ${excor::class.simpleName})"
    if (path != null) File(path).writeText(report + "\n") else println(report)
}

private class PreviewWindow {
    private val frame = JFrame("eXcor Preview")
    private val query = JTextField()
    private val sourcePicker = JComboBox(Source.entries.map { it.label }.toTypedArray())
    private val status = JLabel("Search YouTube Music or YouTube, or paste a link or a video ID.")
    // Section headers (Header) and rows (Row) of the page shown.
    private val results = DefaultListModel<Any>()
    private val resultList = JList(results)
    private val pageTitle = JLabel(" ")
    private val filterPanel = JPanel(WrapLayout())
    private val backButton = JButton("‹ Back").apply { isEnabled = false }
    private val moreButton = JButton("More").apply { isEnabled = false }
    /** The pages opened so far; the last is shown, and Back goes to the one before. */
    private val pages = mutableListOf<Listing>()
    private val details = JTextArea()
    private val log = JTextArea()
    private val maxHeightPicker = JComboBox(arrayOf(360, 480, 720, 1080, 1440, 2160)).apply { selectedItem = 720 }
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var current: ExcorVideoInfo? = null
    private var formats: List<ExcorMediaFormat> = emptyList()

    // Downloads follow googlevideo's redirects; the final host is checked before anything is written.
    private val downloads = mediaClient()
    // The account's YouTube cookies, if the user signed in; kept in the user's own preferences, never logged.
    private val prefs = Preferences.userRoot().node("com/excor/preview")
    private var excor = connect(prefs.get(COOKIE_KEY, null))
    private var browser = Browser(excor)
    private val signIn = JButton().apply { addActionListener { signInOrOut() } }
    private val tempDir = File(System.getProperty("java.io.tmpdir"), "excor-preview").apply { mkdirs() }

    private fun connect(cookie: String?): Excor =
        Excor(excorDefaultHost(region = region(), language = language(), cookie = cookie) { level, message -> addLog(level, message) }).also { it.init() }

    private fun updateSignIn() {
        signIn.text = if (prefs.get(COOKIE_KEY, null) != null) "Sign out" else "Sign in…"
    }

    /** Signing in takes the Cookie header of a signed-in youtube.com tab, pasted by the user; signing out forgets it. */
    private fun signInOrOut() {
        if (prefs.get(COOKIE_KEY, null) != null) {
            prefs.remove(COOKIE_KEY)
            setStatus("Signed out.")
        } else {
            val field = JPasswordField(40)
            val message = arrayOf<Any>(
                "Paste the Cookie header of a youtube.com request from a browser where you are signed in\n" +
                    "(developer tools, Network tab, any youtube.com request, Request headers, Cookie).\n" +
                    "It is your account's sign-in: it is stored in your user preferences on this computer only.",
                field,
            )
            if (JOptionPane.showConfirmDialog(frame, message, "Sign in to YouTube", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return
            val cookie = String(field.password).trim().removePrefix("Cookie:").trim()
            if ("SAPISID=" !in cookie && "__Secure-3PAPISID=" !in cookie) {
                JOptionPane.showMessageDialog(frame, "That has no SAPISID cookie, so YouTube would not treat it as signed in.")
                return
            }
            if (cookie.any { it < ' ' }) return JOptionPane.showMessageDialog(frame, "Paste the header value on one line.")
            if (cookie.length > Preferences.MAX_VALUE_LENGTH) return JOptionPane.showMessageDialog(frame, "That is longer than any YouTube Cookie header.")
            prefs.put(COOKIE_KEY, cookie)
            setStatus("Signed in. Requests now use your account.")
        }
        excor = connect(prefs.get(COOKIE_KEY, null))
        browser = Browser(excor)
        updateSignIn()
    }

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
            add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                add(sourcePicker)
                add(JLabel("Search, link or video ID:"))
            }, BorderLayout.WEST)
            add(query, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { add(go); add(signIn) }, BorderLayout.EAST)
        }
        updateSignIn()

        resultList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        resultList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean) =
                super.getListCellRendererComponent(list, label(value), index, selected, focus).also { cell ->
                    if (value is Header) cell.font = cell.font.deriveFont(Font.BOLD)
                }
        }
        resultList.addListSelectionListener { event ->
            if (event.valueIsAdjusting) return@addListSelectionListener
            when (val picked = resultList.selectedValue) {
                is Row -> openRow(picked.target)
                is Header -> picked.more?.let(::openRow)
            }
        }
        backButton.addActionListener { back() }
        moreButton.addActionListener { more() }
        val listPanel = JPanel(BorderLayout(0, 4)).apply {
            add(JPanel(BorderLayout(0, 4)).apply {
                add(JPanel(BorderLayout(6, 0)).apply {
                    add(backButton, BorderLayout.WEST)
                    add(pageTitle, BorderLayout.CENTER)
                }, BorderLayout.NORTH)
                add(filterPanel, BorderLayout.CENTER)
            }, BorderLayout.NORTH)
            add(JScrollPane(resultList), BorderLayout.CENTER)
            add(moreButton, BorderLayout.SOUTH)
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
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listPanel, right).apply { resizeWeight = 0.45 }
        // The chips wrap to the list's width, so they are laid out again when it changes.
        listPanel.addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(event: java.awt.event.ComponentEvent) = filterPanel.revalidate()
        })
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
        split.setDividerLocation(0.45)
        if (initial != null) {
            query.text = initial
            go()
        }
    }

    private fun ui(block: () -> Unit) = SwingUtilities.invokeLater(block)

    private fun setStatus(text: String) = ui { status.text = text; status.toolTipText = text }

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
                val message = explain(label, failure)
                setStatus(message)
                addLog(ExcorLogLevel.WARN, message)
            }
        }
    }

    private fun go() {
        val text = query.text.trim()
        if (text.isEmpty()) return
        excorVideoIdOf(text)?.let { return open(it) }
        val source = Source.entries[sourcePicker.selectedIndex]
        task("Searching ${source.label}") {
            val listing = browser.search(source, text)
            ui { pages.clear(); pages += listing; showPage() }
        }
    }

    private fun openRow(target: Target) {
        when (target) {
            is Target.Video -> open(target.videoId)
            is Target.Playlist, is Target.Artist -> task("Opening") {
                val listing = browser.open(target)
                ui { pages += listing; showPage() }
            }
            is Target.Unsupported -> setStatus(target.reason)
        }
    }

    /** Runs the search again with a YouTube Music filter chip; the selected chip carries no params and clears it. */
    private fun filter(listing: Listing, chip: ExcorYouTubeSearchFilter) {
        val text = listing.query ?: return
        task("Filtering") {
            val filtered = browser.search(Source.MUSIC, text, filterParams = if (chip.selected) null else chip.params)
            ui { pages[pages.lastIndex] = filtered; showPage() }
        }
    }

    private fun more() {
        val listing = pages.lastOrNull() ?: return
        task("Loading more") {
            val longer = browser.more(listing)
            ui { pages[pages.lastIndex] = longer; showPage() }
        }
    }

    private fun back() {
        if (pages.size > 1) pages.removeAt(pages.lastIndex)
        showPage()
    }

    /** Shows the last page: its title, filter chips, sections and rows. On the Swing thread. */
    private fun showPage() {
        val listing = pages.lastOrNull()
        results.clear()
        filterPanel.removeAll()
        pageTitle.text = listing?.let { listOfNotNull(it.title, it.subtitle).joinToString("  ·  ") } ?: " "
        listing?.filters?.forEach { chip ->
            filterPanel.add(JToggleButton(chip.label, chip.selected).apply {
                isEnabled = !chip.disabled
                addActionListener { filter(listing, chip) }
            })
        }
        listing?.sections?.forEach { section ->
            section.title?.let { results.addElement(Header(it, section.more)) }
            section.rows.forEach(results::addElement)
        }
        filterPanel.revalidate()
        filterPanel.repaint()
        backButton.isEnabled = pages.size > 1
        moreButton.isEnabled = listing?.next != null
        status.text = when {
            listing == null -> " "
            listing.rowCount == 0 -> "Nothing found."
            else -> "${listing.rowCount} results. Pick one."
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
    private suspend fun download(url: String, headers: Map<String, String>, file: File, label: String) =
        com.excor.preview.download(downloads, url, headers, file) { done, total -> setStatus("$label… ${megabytes(done, total)}") }

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

/** A left-aligned FlowLayout that wraps onto more lines and asks its container for the height they take. */
private class WrapLayout : FlowLayout(LEFT, 4, 2) {
    override fun preferredLayoutSize(target: java.awt.Container): Dimension = wrappedSize(target)
    override fun minimumLayoutSize(target: java.awt.Container): Dimension = wrappedSize(target).apply { width = 0 }

    private fun wrappedSize(target: java.awt.Container): Dimension = synchronized(target.treeLock) {
        val insets = target.insets
        val available = (target.parent?.width ?: 0).takeIf { it > 0 } ?: Int.MAX_VALUE
        val maxWidth = available - insets.left - insets.right - 2 * hgap
        var widest = 0
        var lineWidth = 0
        var lineHeight = 0
        var height = 0
        target.components.filter { it.isVisible }.forEach { component ->
            val size = component.preferredSize
            if (lineWidth > 0 && lineWidth + hgap + size.width > maxWidth) {
                widest = maxOf(widest, lineWidth)
                height += lineHeight + vgap
                lineWidth = 0
                lineHeight = 0
            }
            lineWidth += (if (lineWidth > 0) hgap else 0) + size.width
            lineHeight = maxOf(lineHeight, size.height)
        }
        widest = maxOf(widest, lineWidth)
        height += lineHeight
        Dimension(widest + insets.left + insets.right + 2 * hgap, height + insets.top + insets.bottom + 2 * vgap)
    }
}

private const val COOKIE_KEY = "youtubeCookie"

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

/** A section's title in the result list; picking it opens the whole list when there is one. */
private data class Header(val title: String, val more: Target?)

private fun label(value: Any?): Any? = when (value) {
    is Row -> "[${value.badge}]  ${value.title}${if (value.detail.isEmpty()) "" else "  ·  ${value.detail}"}"
    is Header -> if (value.more != null) "${value.title}  (show all ›)" else value.title
    else -> value
}

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
