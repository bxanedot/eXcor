package com.excor

import com.excor.catalog.ExcorCatalogProvider
import com.excor.music.ExcorMatchVerdict
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.testng.annotations.AfterMethod
import org.testng.SkipException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** EXCOR_LIVE=1: video metadata from YouTube, and matching that video on Deezer. Nothing is printed. */
class LiveVideoInfoTest {
    private val clients = mutableListOf<OkHttpClient>()
    private val libraries = mutableListOf<Excor>()

    private fun newExcor(client: OkHttpClient = OkHttpClient()): Excor {
        clients += client
        return Excor(Host(client)).also { libraries += it }
    }

    @AfterMethod(alwaysRun = true)
    fun closeClients() {
        libraries.forEach { it.close() }
        clients.forEach {
            it.connectionPool.evictAll()
            it.dispatcher.executorService.shutdown()
        }
        libraries.clear()
        clients.clear()
    }

    @Test
    fun videoMetadataAndCrossProviderMatch() = runBlocking {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable live checks")
        val excor = newExcor()
        val info = assertNotNull(excor.videoInfo("dQw4w9WgXcQ"))
        assertTrue(info.title.isNotBlank() && info.author != null && (info.durationSeconds ?: 0) > 100 && (info.viewCount ?: 0) > 0)
        val matches = excor.music.matchAcrossProviders(info.asMusicTrack(), setOf(ExcorCatalogProvider.DEEZER))
        val best = matches.matches[ExcorCatalogProvider.DEEZER].orEmpty().firstOrNull()
        assertNotNull(best, "a Deezer candidate for the video: ${matches.errors}")
        assertTrue(best.verdict == ExcorMatchVerdict.LIKELY || best.verdict == ExcorMatchVerdict.POSSIBLE)

        val song = assertNotNull(excor.songs("FGBhQbmPwH8")).single()
        assertTrue(song.title == "One More Time" && song.artist == "Daft Punk" && song.credits.isNotEmpty(), "song: ${song.title} / ${song.artist}")
        val songMatches = excor.music.matchAcrossProviders(song.asMusicTrack("FGBhQbmPwH8"), setOf(ExcorCatalogProvider.DEEZER))
        assertNotNull(songMatches.matches[ExcorCatalogProvider.DEEZER].orEmpty().firstOrNull(), "a Deezer candidate for the credited song: ${songMatches.errors}")
        assertEquals(emptyList(), excor.songs("PkZNo7MFNFg"))

        val mix = assertNotNull(excor.mix("FGBhQbmPwH8"))
        assertTrue(mix.videos.size >= 10 && mix.nextVideoId != null, "mix: ${mix.videos.size}")
        val moreMix = assertNotNull(excor.mix(mix.nextVideoId!!, mix.playlistId, mix.nextIndex))
        assertTrue(moreMix.videos.any { v -> mix.videos.none { it.videoId == v.videoId } }, "the next mix window brings new videos")

        val deezerTrack = excor.music.track("https://www.deezer.com/track/3135556")
        val onYouTube = excor.findOnYouTube(deezerTrack)
        assertTrue(onYouTube.isNotEmpty() && onYouTube.first().verdict.let { it == ExcorMatchVerdict.LIKELY || it == ExcorMatchVerdict.POSSIBLE }, "YouTube matches: ${onYouTube.map { it.verdict }}")
    }

    @Test
    fun videoInfoForABotCheckedVideo() = runBlocking {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable live checks")
        // From environments YouTube bot-checks, the player response for this video carries no details; the watch page does.
        val info = assertNotNull(newExcor().videoInfo("jNQXAC9IVRw"))
        assertTrue(info.title == "Me at the zoo" && info.author == "jawed" && info.channelId != null && (info.viewCount ?: 0) > 1_000_000, "info: ${info.title} ${info.author} ${info.viewCount}")
    }

    @Test
    fun searchWithContinuation() = runBlocking {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable live checks")
        val excor = newExcor()
        val first = assertNotNull(excor.search("daft punk"))
        assertTrue(first.videos.size >= 5 && first.videos.all { it.title.isNotBlank() }, "first page videos: ${first.videos.size}")
        assertTrue(first.channels.any { it.title.contains("Daft Punk", ignoreCase = true) }, "channels: ${first.channels.size}")
        val second = assertNotNull(excor.search("daft punk", assertNotNull(first.continuation)))
        assertTrue(second.videos.isNotEmpty() && second.videos.none { v -> first.videos.any { it.videoId == v.videoId } }, "second page: ${second.videos.size} videos, ${second.videos.count { v -> first.videos.any { it.videoId == v.videoId } }} repeated")

        val channelsOnly = first.filters.first { it.group == "Type" && it.label == "Channels" }
        val filtered = assertNotNull(excor.search("daft punk", filterParams = assertNotNull(channelsOnly.params)))
        assertTrue(filtered.channels.size >= 5 && filtered.videos.isEmpty(), "channels filter: ${filtered.channels.size} channels, ${filtered.videos.size} videos")
        assertTrue(filtered.filters.first { it.label == "Channels" }.selected)
        val more = assertNotNull(excor.search("daft punk", assertNotNull(filtered.continuation)))
        assertTrue(more.channels.isNotEmpty() && more.videos.isEmpty(), "the continuation keeps the filter")
        val shorts = first.filters.first { it.group == "Type" && it.label == "Shorts" }
        assertTrue(assertNotNull(excor.search("daft punk", filterParams = shorts.params)).shorts.isNotEmpty(), "shorts filter")
    }

    @Test
    fun playlistDetailsAndVideos() = runBlocking {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable live checks")
        val playlist = assertNotNull(newExcor().playlist("PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"))
        assertTrue(!playlist.title.isNullOrBlank() && (playlist.videoCount ?: 0) > 0)
        assertTrue(playlist.videos.size >= 10 && playlist.videos.count { it.durationSeconds != null } >= 10)
    }

    @Test
    fun channelDetailsAndUploads() = runBlocking {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable live checks")
        val excor = newExcor()
        val channel = assertNotNull(excor.channel("UC_kRDKYrUlrbtrSiyu5Tflg"))
        assertTrue(channel.title == "Daft Punk" && channel.handle != null && "Videos" in channel.tabs)
        assertTrue(channel.videos.size >= 10 && channel.videos.count { it.durationSeconds != null } >= 10)
        val shorts = assertNotNull(excor.channel("UC_kRDKYrUlrbtrSiyu5Tflg", tab = ExcorYouTubeChannelTab.SHORTS))
        assertTrue(shorts.videos.size >= 5, "shorts: ${shorts.videos.size}")
        val lists = assertNotNull(excor.channel("UC_kRDKYrUlrbtrSiyu5Tflg", tab = ExcorYouTubeChannelTab.PLAYLISTS))
        assertTrue(lists.playlists.size >= 5, "playlists: ${lists.playlists.size}")
        val more = assertNotNull(excor.channel("UC_kRDKYrUlrbtrSiyu5Tflg", assertNotNull(channel.continuation)))
        assertTrue(more.videos.isNotEmpty() && more.videos.none { v -> channel.videos.any { it.videoId == v.videoId } })
        val posts = assertNotNull(excor.channel("UC_kRDKYrUlrbtrSiyu5Tflg", tab = ExcorYouTubeChannelTab.POSTS))
        assertTrue(posts.posts.size >= 5 && posts.posts.any { it.images.isNotEmpty() } && posts.posts.all { it.publishedText != null }, "posts: ${posts.posts.size}")
        val morePosts = assertNotNull(excor.channel("UC_kRDKYrUlrbtrSiyu5Tflg", assertNotNull(posts.continuation)))
        assertTrue(morePosts.posts.isNotEmpty() && morePosts.posts.none { p -> posts.posts.any { it.postId == p.postId } }, "more posts: ${morePosts.posts.size}")
        val live = assertNotNull(excor.channel("UCSJ4gkVC6NrvII8umztf0Ow", tab = ExcorYouTubeChannelTab.LIVE))
        assertTrue(live.videos.any { it.isLive }, "a live stream on Lofi Girl's Live tab")
        val about = assertNotNull(excor.channelAbout("UCBR8-60-B28hp2BmDPdntcQ"))
        assertTrue(about.description != null && (about.viewCount ?: 0) > 1_000_000 && about.country != null && about.subscribersText != null)
        assertTrue(about.links.isNotEmpty() && about.links.all { it.url.startsWith("http") && "redir_token" !in it.url })
    }

    @Test
    fun relatedVideosAndComments() = runBlocking {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable live checks")
        val excor = newExcor()
        val related = assertNotNull(excor.related("dQw4w9WgXcQ"))
        assertTrue(related.videos.size >= 5 && related.videos.none { it.videoId == "dQw4w9WgXcQ" }, "related: ${related.videos.size}")
        val first = assertNotNull(excor.comments("dQw4w9WgXcQ"))
        assertTrue(first.comments.count { it.text.isNotBlank() } >= 10 && first.totalText != null, "comments: ${first.comments.size} total=${first.totalText != null}")
        val second = assertNotNull(excor.comments("dQw4w9WgXcQ", assertNotNull(first.continuation)))
        assertTrue(second.comments.isNotEmpty() && second.comments.none { c -> first.comments.any { it.commentId == c.commentId } }, "second: ${second.comments.size}")
        val withReplies = assertNotNull(first.comments.firstOrNull { it.repliesContinuation != null }, "a comment with replies")
        val replies = assertNotNull(excor.comments("dQw4w9WgXcQ", withReplies.repliesContinuation))
        assertTrue(replies.comments.isNotEmpty() && replies.comments.none { it.commentId == withReplies.commentId }, "replies: ${replies.comments.size}")
        assertTrue(excor.suggestions("daft pu").any { it.startsWith("daft punk") })
        val chapters = assertNotNull(excor.chapters("PkZNo7MFNFg"))
        assertTrue(chapters.size >= 10 && chapters.first().startMs == 0L && chapters.zipWithNext().all { (a, b) -> a.startMs < b.startMs }, "chapters: ${chapters.size}")
        assertEquals(emptyList(), excor.chapters("dQw4w9WgXcQ"))
        val newest = assertNotNull(excor.comments("dQw4w9WgXcQ", newestFirst = true))
        assertTrue(newest.comments.isNotEmpty() && newest.comments.map { it.commentId } != first.comments.map { it.commentId }, "newest differs from top")
    }

    @Test
    fun youTubeMusicSearchArtistPlaylistAndRadio() = runBlocking {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable live checks")
        val excor = newExcor()
        assertTrue(excor.musicSuggestions("daft pu").any { it.startsWith("daft punk") })

        val search = assertNotNull(excor.musicSearch("daft punk"))
        val artist = assertNotNull(search.items.firstOrNull { it.type == ExcorYouTubeMusicItemType.ARTIST && it.title == "Daft Punk" }, "artist result: ${search.items.map { it.type }}")
        assertTrue(search.items.any { it.videoId != null } && search.filters.isNotEmpty())
        // A top result card that plays (here, the music video) is typed and carries its video ID, like a list item.
        val top = assertNotNull(excor.musicSearch("one more time")).items.first()
        assertTrue(top.videoId != null && top.type in setOf(ExcorYouTubeMusicItemType.VIDEO, ExcorYouTubeMusicItemType.SONG) && top.durationSeconds != null, "top result: ${top.type} ${top.title}")
        val videos = assertNotNull(search.filters.firstOrNull { it.params != null && it.label == "Videos" })
        val filtered = assertNotNull(excor.musicSearch("daft punk", filterParams = videos.params))
        assertTrue(filtered.items.size >= 10 && filtered.items.all { it.videoId != null } && filtered.items.any { it.artists.isNotEmpty() }, "videos filter: ${filtered.items.map { "${it.type} ${it.videoId != null} ${it.artists.size}" }}")
        val more = assertNotNull(excor.musicSearch("daft punk", assertNotNull(filtered.continuation)))
        assertTrue(more.items.isNotEmpty() && more.items.count { v -> filtered.items.none { it.videoId == v.videoId } } >= 5, "more: ${more.items.size}")

        val page = assertNotNull(excor.musicArtist(assertNotNull(artist.browseId)))
        assertTrue(page.name == "Daft Punk" && page.description != null && page.monthlyAudienceText != null && page.radioPlaylistId != null, "artist page: name=${page.name} description=${page.description != null} audience=${page.monthlyAudienceText != null} radio=${page.radioPlaylistId != null}")
        assertTrue(page.shelves.isNotEmpty() && page.shelves.all { it.items.isNotEmpty() }, "shelves: ${page.shelves.map { it.title }}")
        val videoShelf = page.shelves.first { it.moreBrowseId != null }
        val playlist = assertNotNull(excor.musicPlaylist(videoShelf.moreBrowseId!!))
        assertTrue(playlist.title != null && playlist.tracks.size >= 20 && playlist.tracks.all { it.videoId != null && it.durationSeconds != null }, "tracks: ${playlist.tracks.size}")

        val radio = assertNotNull(excor.musicRadio("FGBhQbmPwH8"))
        assertTrue(radio.tracks.size >= 20 && radio.tracks.all { it.videoId != null } && radio.continuation != null, "radio: ${radio.tracks.size}")
        val next = assertNotNull(excor.musicRadio(continuation = radio.continuation))
        assertTrue(next.tracks.any { t -> radio.tracks.none { it.videoId == t.videoId } }, "the next radio window brings new tracks")
        val artistRadio = assertNotNull(excor.musicRadio(playlistId = page.radioPlaylistId))
        assertTrue(artistRadio.tracks.isNotEmpty())
    }

    /** Songs and albums, which YouTube Music offers to some networks only (not to the cloud containers tried so far). */
    @Test
    fun youTubeMusicSongsAndAlbums() = runBlocking {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable live checks")
        val excor = newExcor()
        val search = assertNotNull(excor.musicSearch("daft punk"))
        val songsFilter = search.filters.firstOrNull { it.label == "Songs" && it.params != null }
            ?: throw SkipException("YouTube Music offers no Songs filter to this network: ${search.filters.map { it.label }}")
        val songs = assertNotNull(excor.musicSearch("daft punk", filterParams = songsFilter.params)).items
        assertTrue(songs.size >= 10 && songs.all { it.type == ExcorYouTubeMusicItemType.SONG && it.videoId != null && it.durationSeconds != null }, "songs: ${songs.map { "${it.type} ${it.videoId != null} ${it.durationSeconds}" }}")
        assertTrue(songs.count { it.album != null && it.albumBrowseId != null && it.artists.isNotEmpty() } >= songs.size / 2, "songs with album and artist")

        val albumsFilter = assertNotNull(search.filters.firstOrNull { it.label == "Albums" && it.params != null })
        val albums = assertNotNull(excor.musicSearch("daft punk", filterParams = albumsFilter.params)).items
        assertTrue(albums.size >= 5 && albums.all { it.type == ExcorYouTubeMusicItemType.ALBUM && it.browseId != null }, "albums: ${albums.map { it.type }}")
        val album = assertNotNull(albums.firstOrNull { it.playlistId != null }, "an album with its playlist")
        val tracks = assertNotNull(excor.musicPlaylist(album.playlistId!!)).tracks
        assertTrue(tracks.size >= 5 && tracks.all { it.videoId != null && it.durationSeconds != null }, "album tracks: ${tracks.size}")

        val artist = assertNotNull(excor.musicArtist("UCRr1xG_2WIDs18a6cIiCxeA"))
        val top = assertNotNull(artist.shelves.firstOrNull { it.title == "Top songs" }, "shelves: ${artist.shelves.map { it.title }}")
        assertTrue(top.items.isNotEmpty() && top.items.all { it.type == ExcorYouTubeMusicItemType.SONG && it.videoId != null }, "top songs: ${top.items.map { it.type }}")
        assertTrue(artist.shelves.any { s -> s.items.any { it.type == ExcorYouTubeMusicItemType.ALBUM } }, "an album shelf")
    }

    @Test
    fun youTubeMusicArtistAlbumShelfOpensFullDiscography(): Unit = runBlocking {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable live checks")
        val excor = newExcor()
        val artistResult = assertNotNull(excor.musicSearch("daft punk")?.items?.firstOrNull { it.type == ExcorYouTubeMusicItemType.ARTIST && it.title == "Daft Punk" })
        val artist = assertNotNull(excor.musicArtist(assertNotNull(artistResult.browseId)))
        val albumShelf = artist.shelves.firstOrNull { shelf ->
            shelf.moreBrowseId != null && shelf.items.any { it.type == ExcorYouTubeMusicItemType.ALBUM }
        } ?: error("artist shelves: ${artist.shelves.joinToString { "${it.title} (${it.items.size}, ${it.items.map { item -> item.type }.distinct()}, more=${it.moreBrowseId != null})" }}")

        val browseId = assertNotNull(albumShelf.moreBrowseId)
        val albums = mutableListOf<ExcorYouTubeMusicItem>()
        var continuation: String? = null
        for (pageIndex in 0 until 20) {
            if (pageIndex > 0 && continuation == null) break
            val page = excor.musicPlaylist(browseId, continuation) ?: error("discography page $pageIndex returned null")
            assertTrue(page.tracks.isNotEmpty() && page.tracks.all { it.type == ExcorYouTubeMusicItemType.ALBUM && it.browseId != null }, "discography page $pageIndex has album items")
            albums += page.tracks
            continuation = page.continuation
        }
        assertNull(continuation, "discography finishes within 20 pages")
        assertTrue(albums.size > albumShelf.items.size, "full album list exceeds the artist-page shelf preview")
        assertEquals(albums.size, albums.mapNotNull { it.browseId }.distinct().size, "discography albums are unique")
    }

    /**
     * Latency, request count and heap for the public metadata calls against live YouTube. EXCOR_BENCH=1 runs it and
     * writes build/reports/youtube-bench.txt. Results depend on the network and YouTube's servers, so the report names
     * the environment and sample counts; compare runs from the same machine only.
     */
    @Test
    fun benchmark() = runBlocking {
        if (System.getenv("EXCOR_BENCH") != "1") throw SkipException("Set EXCOR_BENCH=1 to run the YouTube benchmark")
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain -> requests++; chain.proceed(chain.request()) }.build()
        val report = StringBuilder()
        val runtime = Runtime.getRuntime()
        report.append("environment: ${System.getProperty("os.name")} ${System.getProperty("os.arch")}, ${runtime.availableProcessors()} CPUs, JDK ${System.getProperty("java.version")}\n")
        System.gc()
        val heapBefore = runtime.totalMemory() - runtime.freeMemory()
        val coldStart = System.nanoTime()
        val excor = newExcor(client)
        excor.videoInfo("dQw4w9WgXcQ")
        report.append("cold start (construct + first videoInfo): ${(System.nanoTime() - coldStart) / 1_000_000}ms, requests=$requests\n")

        suspend fun measure(name: String, inputs: List<String>, block: suspend (String) -> Any?) {
            val before = requests
            var failures = 0
            val samples = inputs.map { input ->
                val t = System.nanoTime()
                if (runCatching { block(input) }.getOrNull() == null) failures++
                (System.nanoTime() - t) / 1_000_000
            }.sorted()
            report.append("$name: n=${samples.size} median=${samples[samples.size / 2]}ms p95=${samples[((samples.size - 1) * 95) / 100]}ms requests=${requests - before} failures=$failures\n")
        }
        val videos = listOf("dQw4w9WgXcQ", "FGBhQbmPwH8", "K0HSD_i2DvA", "jNQXAC9IVRw", "PkZNo7MFNFg", "yydNF8tuVmU", "dwDns8x3Jb4", "PwILkY9gRrc", "HhZaHf8RP6g", "5NV6Rdv1a3I")
        val queries = listOf("daft punk", "lofi", "jazz piano", "news", "chess", "cooking pasta", "guitar lesson", "space documentary", "kotlin", "rain sounds")
        measure("videoInfo", videos) { excor.videoInfo(it) }
        measure("search", queries) { excor.search(it) }
        measure("search continuation", queries.take(5)) { q -> excor.search(q)?.continuation?.let { excor.search(q, it) } }
        measure("playlist", List(5) { "PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI" }) { excor.playlist(it) }
        measure("channel (2 requests)", List(5) { "UC_kRDKYrUlrbtrSiyu5Tflg" }) { excor.channel(it) }
        measure("comments (2 requests)", videos.take(5)) { excor.comments(it) }
        measure("musicSearch", queries) { excor.musicSearch(it) }
        measure("musicRadio", videos.take(5)) { excor.musicRadio(videoId = it) }
        System.gc()
        report.append("heap delta after run: ${((runtime.totalMemory() - runtime.freeMemory()) - heapBefore) / 1024}KiB\n")
        File("build/reports").mkdirs()
        File("build/reports/youtube-bench.txt").writeText(report.toString())
        println(report)
    }

    private class Host(override val httpClient: OkHttpClient) : ExcorHost {
        override val tokenProvider = null
        override val filesDir: File? = null
        override val configStore = object : ExcorConfigStore {
            override val enabled = false
            override fun getString(key: String): String? = null
            override fun getLong(key: String): Long? = null
            override fun putString(key: String, value: String) = Unit
            override fun putLong(key: String, value: Long) = Unit
        }
        override suspend fun session() = ExcorSession(null, null, "US", "en")
        override fun nowMs(): Long = System.currentTimeMillis()
        // Warnings name an endpoint and a status only, so printing them is safe and explains a null result.
        override fun log(level: ExcorLogLevel, message: String, trackId: String?) { if (level >= ExcorLogLevel.WARN) println("[$level] $message") }
    }
}
