package com.excor

import com.excor.catalog.ExcorCatalogProvider
import com.excor.music.ExcorMusicTrack
import com.excor.music.ExcorSyncEntry
import com.excor.music.ExcorSyncPage
import com.excor.music.ExcorSyncResult
import com.excor.music.ExcorSyncState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticModelSafetyTest {
    @Test
    fun opaqueContinuationAndUserStateDoNotAppearInModelStrings() {
        val marker = "synthetic-diagnostic-private-value"
        val playlistVideo = ExcorYouTubePlaylistVideo(
            videoId = marker,
            title = marker,
            channelName = marker,
            channelId = marker,
            durationSeconds = 30,
            viewsText = marker,
            publishedText = marker,
            thumbnails = emptyList(),
        )
        val channel = ExcorYouTubeChannel(
            channelId = marker,
            title = marker,
            description = marker,
            handle = marker,
            subscribersText = marker,
            videoCountText = marker,
            keywords = listOf(marker),
            isFamilySafe = true,
            availableCountries = setOf("CH"),
            avatars = emptyList(),
            tabs = emptyList(),
            videos = listOf(playlistVideo),
            continuation = marker,
            playlists = listOf(ExcorYouTubePlaylistResult(marker, marker, true)),
        )
        val filter = ExcorYouTubeSearchFilter("Type", "Videos", marker, false, false)
        val search = ExcorYouTubeSearchPage(
            videos = listOf(ExcorYouTubeVideoResult(marker, marker, marker, marker, 30, 1, marker, false, false, emptyList())),
            channels = emptyList(),
            playlists = emptyList(),
            continuation = marker,
            filters = listOf(filter),
        )
        val playlist = ExcorYouTubePlaylist(
            playlistId = marker,
            title = marker,
            description = marker,
            ownerName = marker,
            ownerChannelId = marker,
            videoCount = 1,
            viewCount = 1,
            lastUpdatedText = marker,
            videos = listOf(playlistVideo),
            continuation = marker,
        )

        val musicItem = ExcorYouTubeMusicItem(
            type = ExcorYouTubeMusicItemType.SONG,
            title = marker,
            videoId = marker,
            browseId = marker,
            playlistId = marker,
        )
        val musicSearch = ExcorYouTubeMusicSearchPage(listOf(musicItem), listOf(filter), marker)
        val shelf = ExcorYouTubeMusicShelf(marker, listOf(musicItem), marker)
        val musicArtist = ExcorYouTubeMusicArtist(
            channelId = marker,
            name = marker,
            description = marker,
            monthlyAudienceText = marker,
            subscribersText = marker,
            radioPlaylistId = marker,
            shufflePlaylistId = marker,
            shelves = listOf(shelf),
            thumbnails = emptyList(),
        )
        val musicPlaylist = ExcorYouTubeMusicPlaylist(marker, marker, marker, marker, listOf(musicItem), marker)
        val queue = ExcorYouTubeMusicQueue(marker, listOf(musicItem), marker)

        val related = ExcorYouTubeRelated(listOf(playlistVideo), marker)
        val mix = ExcorYouTubeMix(marker, marker, listOf(playlistVideo), marker, 1)
        val comment = ExcorYouTubeComment(marker, marker, marker, marker, false, false, false, marker, marker, 1, marker)
        val comments = ExcorYouTubeCommentsPage(listOf(comment), marker, marker, marker)

        val entry = ExcorSyncEntry(marker, ExcorMusicTrack(ExcorCatalogProvider.APPLE_MUSIC, marker, marker), marker)
        val syncPage = ExcorSyncPage(listOf(entry), marker, marker)
        val state = ExcorSyncState(marker, listOf(marker), marker, listOf(marker), marker)
        val syncResult = ExcorSyncResult(
            state = state,
            unchanged = false,
            added = listOf(marker),
            removed = listOf(marker),
            moved = listOf(marker),
            duplicates = listOf(marker),
            complete = false,
            pagesFetched = 1,
            error = IllegalStateException(marker),
        )
        val rendered = listOf(
            channel, search, filter, playlist, playlistVideo, musicItem, musicSearch, shelf, musicArtist,
            musicPlaylist, queue, related, mix, comment, comments, entry, syncPage, state, syncResult,
        ).joinToString()

        assertFalse(marker in rendered, "Diagnostic rendering exposed opaque continuation or private content")
        assertTrue("continuation=true" in channel.toString())
        assertTrue("videos=1" in playlist.toString())
        assertTrue("items=1" in musicSearch.toString())
        assertTrue("comments=1" in comments.toString())
        assertTrue("keys=1" in state.toString())
        assertTrue("error=true" in syncResult.toString())
    }
}
