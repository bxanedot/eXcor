package com.excor

import com.excor.catalog.ExcorCatalogProvider
import com.excor.music.ExcorMatchVerdict
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.testng.SkipException
import kotlin.test.Test
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
