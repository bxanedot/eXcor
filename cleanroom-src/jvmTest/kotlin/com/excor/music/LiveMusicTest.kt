package com.excor.music

import com.excor.catalog.ExcorCatalogProvider
import com.excor.catalog.ExcorCatalogTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File
import kotlinx.coroutines.runBlocking
import org.testng.SkipException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Live checks against the real provider APIs. EXCOR_LIVE=1 runs the keyless ones (Deezer, iTunes); Spotify and the
 * Apple Music API also need EXCOR_SPOTIFY_TOKEN / EXCOR_APPLE_DEVELOPER_TOKEN, which are read from the environment
 * and never printed. EXCOR_BENCH=1 writes latency and request counts to build/reports/music-bench.txt.
 */
class LiveMusicTest {
    private fun music(): ExcorMusic {
        if (System.getenv("EXCOR_LIVE") != "1") throw SkipException("Set EXCOR_LIVE=1 to enable live music checks")
        val tokens = mapOf(
            ExcorCatalogProvider.SPOTIFY to System.getenv("EXCOR_SPOTIFY_TOKEN"),
            ExcorCatalogProvider.APPLE_MUSIC to System.getenv("EXCOR_APPLE_DEVELOPER_TOKEN"),
            ExcorCatalogProvider.DEEZER to System.getenv("EXCOR_DEEZER_TOKEN"),
        ).filterValues { !it.isNullOrBlank() }
        return ExcorMusic(HttpClient(OkHttp) { expectSuccess = false }, ExcorMusicConfig(tokens = ExcorCatalogTokenProvider { tokens[it] }), System::currentTimeMillis)
    }

    @Test
    fun deezerCatalogEndToEnd() = runBlocking {
        val m = music()
        val track = m.track("https://www.deezer.com/track/3135556")
        assertTrue(track.title.isNotBlank())
        assertTrue(DeezerSource.ISRC.matches(assertNotNull(track.isrc)))
        assertTrue(track.artwork.isNotEmpty())
        assertTrue(track.markets.orEmpty().isNotEmpty())
        assertEquals(track.id, m.tracksByIsrc(ExcorCatalogProvider.DEEZER, track.isrc!!).single().id)

        val full = m.metadata(track)
        assertTrue(full.label != null && full.genres.isNotEmpty() && full.upc != null, "album enrichment")
        assertEquals(full.albumId, m.deezerCatalog.albumByUpc(full.upc!!)?.id)

        val search = m.search("daft punk", ExcorCatalogProvider.DEEZER, setOf(ExcorMusicKind.TRACK, ExcorMusicKind.ALBUM, ExcorMusicKind.ARTIST), limit = 5)
        assertEquals(5, search.tracks!!.items.size)
        val second = m.search("daft punk", ExcorCatalogProvider.DEEZER, setOf(ExcorMusicKind.TRACK, ExcorMusicKind.ALBUM, ExcorMusicKind.ARTIST), limit = 5, page = search.tracks!!.next)
        assertTrue(second.tracks!!.items.none { t -> search.tracks!!.items.any { it.id == t.id } })

        val playlistRef = m.parse("https://www.deezer.com/playlist/908622995")!!
        val playlist = m.playlist(playlistRef)
        val sync = m.syncPlaylist(playlistRef, null)
        assertTrue(sync.complete)
        assertTrue(sync.state.keys.size >= (playlist.trackCount ?: 0) - 5, "sync read the playlist")
        assertTrue(m.syncPlaylist(playlistRef, sync.state).unchanged)

        assertTrue(m.deezerCatalog.genres().isNotEmpty())
        assertTrue(m.deezerCatalog.chartTracks().items.isNotEmpty())
        assertTrue(m.deezerCatalog.relatedArtists("27").isNotEmpty())
    }

    @Test
    fun appleKeylessAndCrossProviderMatch() = runBlocking {
        val m = music()
        val apple = m.track("https://music.apple.com/us/album/one-more-time/697194953?i=697195462")
        assertTrue(apple.title.isNotBlank() && apple.durationMs != null)
        val album = m.album(ExcorMusicRef(ExcorCatalogProvider.APPLE_MUSIC, ExcorMusicKind.ALBUM, "697194953"))
        assertTrue((album.tracks?.items?.size ?: 0) >= 10)

        val deezer = m.track("https://www.deezer.com/track/3135556")
        val matches = m.matchAcrossProviders(deezer, setOf(ExcorCatalogProvider.APPLE_MUSIC))
        val best = matches.matches[ExcorCatalogProvider.APPLE_MUSIC].orEmpty().firstOrNull()
        assertNotNull(best, "an Apple Music candidate for the Deezer track: ${matches.errors}")
        assertTrue(best.verdict == ExcorMatchVerdict.LIKELY || best.verdict == ExcorMatchVerdict.SAME_RECORDING)
    }

    @Test
    fun spotifyWithAHostToken() = runBlocking {
        val m = music()
        if (System.getenv("EXCOR_SPOTIFY_TOKEN").isNullOrBlank()) throw SkipException("Set EXCOR_SPOTIFY_TOKEN for Spotify live checks")
        val result = m.search("daft punk one more time", ExcorCatalogProvider.SPOTIFY, limit = 5)
        assertTrue(result.tracks!!.items.isNotEmpty())
        val track = m.track(result.tracks!!.items.first().ref)
        assertTrue(track.durationMs != null)
    }

    @Test
    fun appleMusicApiWithADeveloperToken() = runBlocking {
        val m = music()
        if (System.getenv("EXCOR_APPLE_DEVELOPER_TOKEN").isNullOrBlank()) throw SkipException("Set EXCOR_APPLE_DEVELOPER_TOKEN for Apple Music API live checks")
        val track = m.track(ExcorMusicRef(ExcorCatalogProvider.APPLE_MUSIC, ExcorMusicKind.TRACK, "697195462"))
        assertNotNull(track.isrc)
        assertTrue(m.tracksByIsrc(ExcorCatalogProvider.APPLE_MUSIC, track.isrc!!).isNotEmpty())
    }

    @Test
    fun benchmark() = runBlocking {
        if (System.getenv("EXCOR_BENCH") != "1") throw SkipException("Set EXCOR_BENCH=1 to run the music benchmark")
        val report = StringBuilder()
        fun summary(name: String, samples: List<Long>, requests: Int) {
            val sorted = samples.sorted()
            val p50 = sorted[sorted.size / 2]
            val p95 = sorted[((sorted.size - 1) * 95) / 100]
            report.append("$name: n=${samples.size} median=${p50}ms p95=${p95}ms requests=$requests\n")
        }
        val runtime = Runtime.getRuntime()
        System.gc()
        val heapBefore = runtime.totalMemory() - runtime.freeMemory()

        val coldStart = System.nanoTime()
        val m = music()
        m.track("https://www.deezer.com/track/3135556")
        report.append("cold start (construct + first Deezer track): ${(System.nanoTime() - coldStart) / 1_000_000}ms\n")

        suspend fun measure(name: String, n: Int, block: suspend (Int) -> Unit) {
            val before = m.requestCount
            val samples = (0 until n).map { i -> val t = System.nanoTime(); block(i); (System.nanoTime() - t) / 1_000_000 }
            summary(name, samples, m.requestCount - before)
        }
        val ids = listOf("3135553", "3135554", "3135555", "3135556", "3135557", "3135558", "3135559", "3135560", "3135561", "3135562")
        measure("deezer track (uncached)", ids.size) { m.track(ExcorMusicRef(ExcorCatalogProvider.DEEZER, ExcorMusicKind.TRACK, ids[it])) }
        measure("deezer track (cached)", ids.size) { m.track(ExcorMusicRef(ExcorCatalogProvider.DEEZER, ExcorMusicKind.TRACK, ids[it])) }
        measure("deezer search", 5) { m.search("query $it daft", ExcorCatalogProvider.DEEZER, limit = 10) }
        measure("itunes track", 5) { m.track(ExcorMusicRef(ExcorCatalogProvider.APPLE_MUSIC, ExcorMusicKind.TRACK, listOf("697195462", "697195570", "697195633", "697195787", "697195813")[it])) }
        val deezerTrack = m.track("https://www.deezer.com/track/3135556")
        measure("cross-provider match deezer->apple", 3) { m.matchAcrossProviders(deezerTrack, setOf(ExcorCatalogProvider.APPLE_MUSIC)) }
        measure("playlist sync (50 tracks, fresh)", 3) { m.syncPlaylist(m.parse("https://www.deezer.com/playlist/908622995")!!, null) }

        // Offline CPU cost of matching, independent of the network.
        val candidates = (0 until 1_000).map { deezerTrack.copy(id = "$it", title = if (it % 2 == 0) deezerTrack.title else "${deezerTrack.title} (Live)") }
        val t = System.nanoTime()
        repeat(20) { excorRankMatches(deezerTrack, candidates) }
        report.append("match 1000 candidates (offline): ${(System.nanoTime() - t) / 20 / 1_000_000}ms per ranking\n")

        System.gc()
        report.append("heap delta after run: ${((runtime.totalMemory() - runtime.freeMemory()) - heapBefore) / 1024}KiB\n")
        File("build/reports").mkdirs()
        File("build/reports/music-bench.txt").writeText(report.toString())
        println(report)
    }
}
