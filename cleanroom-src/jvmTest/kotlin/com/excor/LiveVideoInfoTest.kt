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
