package com.excor.music

import com.excor.catalog.ExcorCatalogProvider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MusicModelDiagnosticsTest {
    @Test
    fun pageAndPlaylistStringsRedactCursorsAndPrivateMetadata() {
        val cursor = "synthetic-private-continuation"
        val playlistId = "synthetic-private-playlist-id"
        val playlistTitle = "Synthetic Private Playlist"
        val description = "Synthetic private description"
        val owner = "synthetic-private-owner"
        val snapshot = "synthetic-private-snapshot"
        val trackTitle = "Synthetic private track"

        val page = ExcorMusicPage(
            items = listOf(ExcorMusicTrack(ExcorCatalogProvider.APPLE_MUSIC, "synthetic-track-id", trackTitle)),
            next = cursor,
            total = 1,
        )
        val playlist = ExcorMusicPlaylist(
            provider = ExcorCatalogProvider.APPLE_MUSIC,
            id = playlistId,
            title = playlistTitle,
            url = "https://music.example/playlist/$playlistId?session=synthetic-url-secret",
            description = description,
            ownerName = owner,
            ownerId = "synthetic-private-owner-id",
            snapshot = snapshot,
            tracks = page,
        )

        val pageText = page.toString()
        val playlistText = playlist.toString()
        val rendered = pageText + playlistText
        listOf(
            cursor,
            playlistId,
            playlistTitle,
            description,
            owner,
            "synthetic-private-owner-id",
            snapshot,
            trackTitle,
            "synthetic-url-secret",
        ).forEach { assertFalse(it in rendered, "Music diagnostics exposed a private value") }

        assertTrue("items=1" in pageText)
        assertTrue("next=present" in pageText)
        assertTrue("trackPageItems=1" in playlistText)
        assertTrue("trackPageHasNext=true" in playlistText)
    }
}
