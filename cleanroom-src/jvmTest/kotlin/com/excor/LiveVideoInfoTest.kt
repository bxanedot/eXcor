package com.excor

import com.excor.catalog.ExcorCatalogProvider
import com.excor.music.ExcorMatchVerdict
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.testng.SkipException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** EXCOR_LIVE=1: video metadata from YouTube, and matching that video on Deezer. Nothing is printed. */
class LiveVideoInfoTest {
    @Test
    fun videoMetadataAndCrossProviderMatch() = runBlocking {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable live checks")
        val excor = Excor(Host(OkHttpClient()))
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
        val info = assertNotNull(Excor(Host(OkHttpClient())).videoInfo("jNQXAC9IVRw"))
        assertTrue(info.title == "Me at the zoo" && info.author == "jawed" && info.channelId != null && (info.viewCount ?: 0) > 1_000_000, "info: ${info.title} ${info.author} ${info.viewCount}")
    }

    @Test
    fun searchWithContinuation() = runBlocking {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable live checks")
        val excor = Excor(Host(OkHttpClient()))
        val first = assertNotNull(excor.search("daft punk"))
        assertTrue(first.videos.size >= 5 && first.videos.all { it.title.isNotBlank() })
        assertTrue(first.channels.any { it.title.contains("Daft Punk", ignoreCase = true) })
        val second = assertNotNull(excor.search("daft punk", assertNotNull(first.continuation)))
        assertTrue(second.videos.isNotEmpty() && second.videos.none { v -> first.videos.any { it.videoId == v.videoId } })

        val channelsOnly = first.filters.first { it.group == "Type" && it.label == "Channels" }
        val filtered = assertNotNull(excor.search("daft punk", filterParams = assertNotNull(channelsOnly.params)))
        assertTrue(filtered.channels.size >= 5 && filtered.videos.isEmpty())
        assertTrue(filtered.filters.first { it.label == "Channels" }.selected)
        val more = assertNotNull(excor.search("daft punk", assertNotNull(filtered.continuation)))
        assertTrue(more.channels.isNotEmpty() && more.videos.isEmpty(), "the continuation keeps the filter")
        val shorts = first.filters.first { it.group == "Type" && it.label == "Shorts" }
        assertTrue(assertNotNull(excor.search("daft punk", filterParams = shorts.params)).shorts.isNotEmpty())
    }

    @Test
    fun playlistDetailsAndVideos() = runBlocking {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable live checks")
        val playlist = assertNotNull(Excor(Host(OkHttpClient())).playlist("PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"))
        assertTrue(!playlist.title.isNullOrBlank() && (playlist.videoCount ?: 0) > 0)
        assertTrue(playlist.videos.size >= 10 && playlist.videos.count { it.durationSeconds != null } >= 10)
    }

    @Test
    fun channelDetailsAndUploads() = runBlocking {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable live checks")
        val excor = Excor(Host(OkHttpClient()))
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
        val excor = Excor(Host(OkHttpClient()))
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
        val excor = Excor(Host(OkHttpClient()))
        assertTrue(excor.musicSuggestions("daft pu").any { it.startsWith("daft punk") })

        val search = assertNotNull(excor.musicSearch("daft punk"))
        val artist = assertNotNull(search.items.firstOrNull { it.type == ExcorYouTubeMusicItemType.ARTIST && it.title == "Daft Punk" }, "artist result: ${search.items.map { it.type }}")
        assertTrue(search.items.any { it.videoId != null } && search.filters.isNotEmpty())
        val videos = assertNotNull(search.filters.firstOrNull { it.params != null && it.label == "Videos" })
        val filtered = assertNotNull(excor.musicSearch("daft punk", filterParams = videos.params))
        assertTrue(filtered.items.size >= 10 && filtered.items.all { it.videoId != null } && filtered.items.any { it.artists.isNotEmpty() })
        val more = assertNotNull(excor.musicSearch("daft punk", assertNotNull(filtered.continuation)))
        assertTrue(more.items.isNotEmpty() && more.items.count { v -> filtered.items.none { it.videoId == v.videoId } } >= 5, "more: ${more.items.size}")

        val page = assertNotNull(excor.musicArtist(assertNotNull(artist.browseId)))
        assertTrue(page.name == "Daft Punk" && page.description != null && page.monthlyAudienceText != null && page.radioPlaylistId != null)
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
        val excor = Excor(Host(client))
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
        override fun log(level: ExcorLogLevel, message: String, trackId: String?) = Unit
    }
}
