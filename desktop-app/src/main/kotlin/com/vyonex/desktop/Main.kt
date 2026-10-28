package com.vyonex.desktop

import com.vyonex.MusicCatalogItem
import com.vyonex.MusicCatalogKind
import com.vyonex.VyonEx
import com.vyonex.VyonExConfigStore
import com.vyonex.VyonExHost
import com.vyonex.VyonExLogLevel
import com.vyonex.VyonExSession
import com.vyonex.VyonExSearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.awt.BorderLayout
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Image
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.net.URI
import java.net.URL
import java.util.Locale
import javax.imageio.ImageIO
import javax.swing.DefaultListModel
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.UIManager

/** Desktop uses the existing VyonEx JVM implementation, not a second extractor. */
private class DesktopHost : VyonExHost {
    override val httpClient = OkHttpClient()
    override val filesDir = File(System.getProperty("user.home"), ".vyonex").apply { mkdirs() }
    private val values = mutableMapOf<String, String>()
    override val configStore: VyonExConfigStore = object : VyonExConfigStore {
        override val enabled: Boolean = false
        override fun getString(key: String) = values[key]
        override fun getLong(key: String) = values[key]?.toLongOrNull()
        override fun putString(key: String, value: String) { values[key] = value }
        override fun putLong(key: String, value: Long) { values[key] = value.toString() }
    }
    override suspend fun session(): VyonExSession {
        val locale = Locale.getDefault()
        return VyonExSession(null, null, locale.country.ifEmpty { "US" }, locale.language.ifEmpty { "en" })
    }
    override fun nowMs(): Long = System.currentTimeMillis()
    override fun log(level: VyonExLogLevel, message: String, trackId: String?) {
        if (level == VyonExLogLevel.ERROR) System.err.println("VyonEx: $message")
    }
}

private data class Entry(val title: String, val subtitle: String, val url: String, val image: String?, val detail: String) {
    override fun toString(): String = "$title  —  $subtitle"
}

private fun MusicCatalogItem.asEntry(): Entry {
    val credit = artists.mapNotNull { it.name }.joinToString(", ")
    val lines = listOfNotNull(
        "Catalog: $provider", "Kind: $kind", "Title: ${title ?: "Unknown"}",
        "Artist: $credit".takeIf { credit.isNotEmpty() },
        album?.let { "Album: $it" }, releaseDate?.let { "Released: $it" },
        durationMs?.let { "Duration: ${it / 1000}s" }, isrc?.let { "ISRC: $it" },
        upc?.let { "UPC: $it" }, genres.takeIf { it.isNotEmpty() }?.let { "Genres: ${it.joinToString()}" },
        label?.let { "Label: $it" }, copyright?.let { "Copyright: $it" },
        "Images: ${images.size}", "Linked tracks: ${tracks.size}",
        if (!fullyEnriched) "Basic preview only: Spotify API token required for detailed fields." else null,
        "Source: $url",
    )
    return Entry(title ?: id, listOf(provider.toString(), credit).filter { it.isNotBlank() }.joinToString(" · "),
        url, images.firstOrNull()?.url, lines.joinToString("\n"))
}

private fun VyonExSearchResult.asEntry(): Entry {
    val url = when (kind.name) {
        "VIDEO" -> "https://www.youtube.com/watch?v=$id"
        "CHANNEL", "ARTIST" -> "https://www.youtube.com/channel/$id"
        "PLAYLIST" -> "https://www.youtube.com/playlist?list=$id"
        else -> "https://music.youtube.com/browse/$id"
    }
    return Entry(title ?: id, channelName ?: kind.name, url, thumbnails.maxByOrNull { it.width }?.url,
        listOfNotNull("YouTube Music: $kind", "Title: ${title ?: id}", channelName?.let { "Artist: $it" },
            durationSeconds?.let { "Duration: ${it}s" }, "Source: $url").joinToString("\n"))
}

private class Browser {
    private val host = DesktopHost()
    private val engine = VyonEx(host).also { it.init() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val frame = JFrame("eXcor — shared music engine")
    private val mode = JComboBox(arrayOf(
        "Inspect link", "Deezer tracks", "Deezer albums", "Deezer artists",
        "Spotify tracks", "Spotify albums", "Spotify artists", "YouTube Music",
    ))
    private val query = JTextField(36)
    private val token = JPasswordField(16)
    private val model = DefaultListModel<Entry>()
    private val list = JList(model)
    private val info = JTextArea()
    private val cover = JLabel("Artwork")
    private val status = JLabel("Ready")
    private val open = JButton("Open provider page")
    private var selected: Entry? = null

    fun show() {
        frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        frame.minimumSize = Dimension(760, 520)
        frame.setSize(1080, 700)
        frame.setLocationRelativeTo(null)
        val top = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(mode)
            add(query)
            add(JButton("Search / Inspect").apply { addActionListener { search() } })
            add(JLabel("Spotify token:"))
            add(token)
        }
        token.toolTipText = "Optional Spotify OAuth token; kept only in this input field."
        query.addActionListener { search() }
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                selected = list.selectedValue
                info.text = selected?.detail ?: ""
                loadArtwork(selected?.image)
                open.isEnabled = selected != null
            }
        }
        info.isEditable = false
        info.lineWrap = true
        info.wrapStyleWord = true
        val right = JPanel(BorderLayout(8, 8)).apply {
            cover.horizontalAlignment = JLabel.CENTER
            cover.preferredSize = Dimension(300, 230)
            add(cover, BorderLayout.NORTH)
            add(JScrollPane(info), BorderLayout.CENTER)
        }
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, JScrollPane(list), right).apply {
            resizeWeight = 0.5
        }
        val bottom = JPanel(BorderLayout()).apply {
            add(status, BorderLayout.CENTER)
            open.isEnabled = false
            open.addActionListener { selected?.let { openSource(it.url) } }
            add(open, BorderLayout.EAST)
        }
        frame.contentPane.add(top, BorderLayout.NORTH)
        frame.contentPane.add(split, BorderLayout.CENTER)
        frame.contentPane.add(bottom, BorderLayout.SOUTH)
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent?) {
                scope.cancel()
                engine.close()
            }
        })
        frame.isVisible = true
    }

    private fun search() {
        val q = query.text.trim()
        if (q.isEmpty()) return
        val choice = mode.selectedIndex
        // Clear the password after copying to a local value so it never persists beyond this action.
        val secret = String(token.password).takeIf { it.isNotBlank() }
        token.text = ""
        status.text = "Loading…"
        scope.launch {
            val result = runCatching {
                when (choice) {
                    0 -> listOfNotNull(engine.musicCatalog.lookup(q, secret)?.asEntry())
                    in 1..3 -> engine.musicCatalog.searchDeezer(q, kind(choice - 1)).map { it.asEntry() }
                    in 4..6 -> engine.musicCatalog.searchSpotify(q, kind(choice - 4), secret ?: error("Spotify OAuth access token required."), 10).map { it.asEntry() }
                    else -> engine.searchMusic(q).results.map { it.asEntry() }
                }
            }
            SwingUtilities.invokeLater {
                model.clear()
                result.onSuccess { entries ->
                    entries.forEach(model::addElement)
                    status.text = "${entries.size} result(s)"
                    if (entries.isNotEmpty()) list.selectedIndex = 0
                }.onFailure { error ->
                    status.text = "Request failed"
                    info.text = error.message ?: "Catalog request failed."
                }
            }
        }
    }

    private fun kind(index: Int) = when (index) {
        0 -> MusicCatalogKind.TRACK
        1 -> MusicCatalogKind.ALBUM
        else -> MusicCatalogKind.ARTIST
    }

    private fun loadArtwork(url: String?) {
        cover.icon = null
        cover.text = "No image"
        if (url == null) return
        scope.launch {
            val icon = runCatching {
                val parsed = URI(url)
                val host = parsed.host ?: return@runCatching null
                if (parsed.scheme != "https" || !(
                        host == "i.scdn.co" || host.endsWith(".scdn.co") ||
                        host == "cdn-images.dzcdn.net" || host.endsWith(".ytimg.com") ||
                        host == "yt3.ggpht.com"
                    )) return@runCatching null
                val connection = URL(url).openConnection().apply {
                    connectTimeout = 5000
                    readTimeout = 7000
                }
                val image = connection.getInputStream().use(ImageIO::read) ?: return@runCatching null
                ImageIcon(image.getScaledInstance(210, 210, Image.SCALE_SMOOTH))
            }.getOrNull()
            SwingUtilities.invokeLater {
                if (selected?.image == url) {
                    cover.icon = icon
                    cover.text = if (icon == null) "Artwork unavailable" else ""
                }
            }
        }
    }

    private fun openSource(url: String) {
        try {
            val uri = URI(url)
            if (uri.scheme != "https") return
            Desktop.getDesktop().browse(uri)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(frame, "Cannot open the provider page.")
        }
    }
}

fun main() {
    SwingUtilities.invokeLater {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        Browser().show()
    }
}
