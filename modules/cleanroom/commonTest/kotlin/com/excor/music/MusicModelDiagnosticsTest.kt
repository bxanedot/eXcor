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
    @Test
    fun lyricsAndCatalogDiagnosticStringsRedactPrivateTextAndIdentifiers() {
        val lyricText = "Synthetic private lyric"
        val lyricSource = "Synthetic private lyrics provider"
        val parsedTitle = "Synthetic private parsed title"
        val featuredArtist = "Synthetic private featured artist"
        val videoId = "synthetic-private-video-id"
        val videoTitle = "Synthetic private video title"
        val artist = "Synthetic private artist"
        val isrc = "synthetic-private-isrc"
        val videoUrl = "https://music.example/video?session=synthetic-video-secret"
        val stationId = "synthetic-private-station-id"
        val stationName = "Synthetic private station"
        val recommendationTitle = "Synthetic private recommendation"
        val refId = "synthetic-private-reference-id"
        val genreId = "synthetic-private-genre-id"
        val genreName = "Synthetic private genre"
        val radioId = "synthetic-private-radio-id"
        val radioTitle = "Synthetic private radio"
        val radioDescription = "Synthetic private radio description"
        val trackId = "synthetic-private-track-id"
        val trackTitle = "Synthetic private track title"
        val playlistId = "synthetic-private-conversion-playlist-id"
        val playlistTitle = "Synthetic private conversion playlist"
        val fieldId = "synthetic-private-field-source-id"

        val lyrics = ExcorLyrics(
            lines = listOf(ExcorLyricsLine(123L, lyricText, listOf(ExcorLyricsWord(123L, lyricText)))),
            sync = ExcorLyricsSync.WORD,
            language = "en",
            source = lyricSource,
        )
        val parsed = ExcorParsedTitle(parsedTitle, emptySet(), listOf(featuredArtist))
        val video = ExcorAppleMusicVideo(
            id = videoId, title = videoTitle, artistName = artist, isrc = isrc, durationMs = 123L,
            releaseDate = "2026-01-01", genres = listOf("Synthetic private genre text"), explicit = true,
            has4K = true, hasHdr = false, url = videoUrl, artwork = emptyList(),
        )
        val station = ExcorAppleStation(stationId, stationName, true, videoUrl, emptyList())
        val recommendation = ExcorAppleRecommendation(
            recommendationTitle,
            listOf(ExcorMusicRef(ExcorCatalogProvider.APPLE_MUSIC, ExcorMusicKind.TRACK, refId)),
        )
        val genre = ExcorDeezerGenre(genreId, genreName, emptyList())
        val radio = ExcorDeezerRadio(radioId, radioTitle, radioDescription, emptyList())
        val track = ExcorMusicTrack(
            provider = ExcorCatalogProvider.DEEZER, id = trackId, title = trackTitle, url = videoUrl,
        )
        val playlist = ExcorMusicPlaylist(
            provider = ExcorCatalogProvider.DEEZER, id = playlistId, title = playlistTitle,
        )
        val charts = ExcorDeezerCharts(
            tracks = listOf(track), albums = emptyList(), artists = emptyList(), playlists = listOf(playlist), podcasts = emptyList(),
        )
        val conversion = ExcorPlaylistConversion(
            playlist = playlist,
            target = ExcorCatalogProvider.APPLE_MUSIC,
            tracks = listOf(ExcorConvertedTrack(track, emptyList())),
            truncated = false,
        )
        val fieldSource = ExcorFieldSource(ExcorCatalogProvider.APPLE_MUSIC, fieldId, 0.9)
        val enriched = ExcorEnrichedTrack(track, mapOf(ExcorMusicField.ISRC to fieldSource))
        val multiSearch = ExcorMultiSearchResult(
            results = mapOf(
                ExcorCatalogProvider.DEEZER to ExcorMusicSearchResult(
                    tracks = ExcorMusicPage(items = listOf(track), next = "synthetic-private-search-cursor"),
                ),
            ),
            errors = emptyMap(),
        )

        val rendered = listOf(
            lyrics, parsed, video, station, recommendation, genre, radio, charts, conversion,
            fieldSource, enriched, multiSearch,
        ).joinToString("\n")
        listOf(
            lyricText, lyricSource, parsedTitle, featuredArtist, videoId, videoTitle, artist, isrc,
            videoUrl, "synthetic-video-secret", stationId, stationName, recommendationTitle, refId,
            genreId, genreName, radioId, radioTitle, radioDescription, trackId, trackTitle,
            playlistId, playlistTitle, fieldId, "synthetic-private-search-cursor",
        ).forEach { assertFalse(it in rendered, "Music diagnostics exposed a private value") }

        assertTrue("lines=1" in lyrics.toString())
        assertTrue("items=1" in recommendation.toString())
        assertTrue("tracks=1" in charts.toString())
        assertTrue("sources=1" in enriched.toString())
        assertTrue("providers=1" in multiSearch.toString())
    }

}
