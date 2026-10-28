package com.vyonex.app

import com.vyonex.VyonExResultKind
import com.vyonex.VyonExSearchPage
import com.vyonex.VyonExSearchResult
import com.vyonex.VyonExSearchType
import com.vyonex.VyonExThumbnail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun result(kind: VyonExResultKind, id: String, duration: Long? = null, channel: String? = "Rick Astley") = VyonExSearchResult(
    kind, id, "Title $id", channel, "UCuAXFkgsw1L7xaCfnd5JJOw", duration,
    listOf(VyonExThumbnail("https://i.ytimg.com/$id/small.jpg", 120, 90), VyonExThumbnail("https://i.ytimg.com/$id/big.jpg", 480, 360)),
)

class SearchTest {
    private val results = listOf(
        result(VyonExResultKind.VIDEO, "dQw4w9WgXcQ", 213),
        result(VyonExResultKind.PLAYLIST, "PL1"),
        result(VyonExResultKind.VIDEO, "yPYZpwSpKmA", 3725),
        result(VyonExResultKind.CHANNEL, "UCuAXFkgsw1L7xaCfnd5JJOw", channel = null),
        result(VyonExResultKind.VIDEO, "6oHkCfhlDnc"),
    )

    @Test
    fun aVideoResultIsATrackWithItsLargestThumbnail() {
        assertEquals(Track("dQw4w9WgXcQ", "Title dQw4w9WgXcQ", "Rick Astley", 213, "https://i.ytimg.com/dQw4w9WgXcQ/big.jpg"), results[0].toTrack())
        assertNull(results[1].toTrack())
        assertNull(results[3].toTrack())
    }

    @Test
    fun aTapPlaysThatVideoThenTheVideosAfterIt() {
        assertEquals(listOf("yPYZpwSpKmA", "6oHkCfhlDnc"), queueFrom(results, 2).map { it.id })
        assertEquals(listOf("dQw4w9WgXcQ", "yPYZpwSpKmA", "6oHkCfhlDnc"), queueFrom(results, 0).map { it.id })
        assertEquals(emptyList(), queueFrom(results, 1))
        assertEquals(emptyList(), queueFrom(results, 9))
    }

    @Test
    fun bylines() {
        assertEquals(listOf("Rick Astley · 3:33", "Playlist · Rick Astley", "Rick Astley · 1:02:05", "Channel", "Rick Astley"), results.map(::bylineOf))
        assertEquals(listOf("0:05", "59:59", "1:00:00"), listOf(5L, 3599L, 3600L).map(::clockOf))
    }

    @Test
    fun suggestionsAreAskedForWordsNotLinks() {
        assertTrue(suggestsFor("ri"))
        assertTrue(suggestsFor("東京"))
        listOf("", "r", " r ", "https://youtu.be/x", "www.youtube.com").forEach { assertFalse(suggestsFor(it), it) }
    }

    @Test
    fun aShortsSearchListsItsShortsFirst() {
        val short = result(VyonExResultKind.VIDEO, "6oHkCfhlDnc")
        val page = VyonExSearchPage(results = results.take(2), continuation = null, shorts = listOf(short))
        assertEquals(listOf("6oHkCfhlDnc", "dQw4w9WgXcQ", "PL1"), resultsOf(page, VyonExSearchType.SHORTS).map { it.id })
        assertEquals(listOf("dQw4w9WgXcQ", "PL1"), resultsOf(page, null).map { it.id })
        assertEquals(listOf("dQw4w9WgXcQ", "PL1"), resultsOf(page, VyonExSearchType.VIDEOS).map { it.id })
        assertEquals(listOf("Videos", "Shorts", "Channels", "Playlists"), SEARCH_TYPES.map { it.second })
    }

    @Test
    fun aNextPageAddsOnlyResultsNotShownYet() {
        // The same ID as another kind (a channel and an artist) is another result.
        val artist = results[3].copy(kind = VyonExResultKind.ARTIST)
        assertEquals(results + artist, appendResults(results, listOf(results[1], artist)))
    }
}
