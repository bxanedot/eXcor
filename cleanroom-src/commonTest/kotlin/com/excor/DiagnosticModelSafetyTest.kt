package com.excor

import com.excor.catalog.ExcorCatalogArtist
import com.excor.catalog.ExcorCatalogItem
import com.excor.catalog.ExcorCatalogKind
import com.excor.catalog.ExcorCatalogPage
import com.excor.catalog.ExcorCatalogProvider
import com.excor.music.ExcorMusicTrack
import com.excor.music.ExcorAudioCapabilities
import com.excor.music.ExcorAudioCodec
import com.excor.music.ExcorAudioFormat
import com.excor.music.ExcorAudioOption
import com.excor.music.ExcorAudioRejection
import com.excor.music.ExcorAudioSelection
import com.excor.music.ExcorSpotifyPlaybackState
import com.excor.music.ExcorSpotifyDevice
import com.excor.music.ExcorPodcastShow
import com.excor.music.ExcorPodcastEpisode
import com.excor.music.ExcorMusicSearchResult
import com.excor.music.ExcorMusicRef
import com.excor.music.ExcorMusicPlaylist
import com.excor.music.ExcorMusicPage
import com.excor.music.ExcorMusicKind
import com.excor.music.ExcorMusicArtistProfile
import com.excor.music.ExcorMusicArtist
import com.excor.music.ExcorMusicAlbum
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
        val musicArtistRef = ExcorYouTubeMusicArtistRef(marker, marker)
        val post = ExcorYouTubePost(marker, marker, marker, marker, emptyList())
        val channelLink = ExcorYouTubeChannelLink(marker, marker)
        val about = ExcorYouTubeChannelAbout(marker, marker, marker, marker, 1, marker, marker, listOf(channelLink))
        val searchVideo = ExcorYouTubeVideoResult(marker, marker, marker, marker, 30, 1, marker, true, false, emptyList())
        val searchChannel = ExcorYouTubeChannelResult(marker, marker, marker, marker, emptyList())
        val searchPlaylist = ExcorYouTubePlaylistResult(marker, marker, true)
        val chapter = ExcorYouTubeChapter(marker, 1000)
        val song = ExcorYouTubeSong(marker, marker, marker, mapOf(marker to marker))
        val videoInfo = ExcorVideoInfo(
            videoId = marker,
            title = marker,
            author = marker,
            channelId = marker,
            channelHandle = marker,
            durationSeconds = 30,
            viewCount = 1,
            likeCount = 1,
            description = marker,
            keywords = listOf(marker),
            category = marker,
            publishDate = marker,
            uploadDate = marker,
            thumbnails = emptyList(),
            isLive = false,
            isLiveContent = false,
            isPrivate = true,
            isUnlisted = true,
            isFamilySafe = false,
            isShortsEligible = true,
            availableCountries = setOf(marker),
            playabilityStatus = marker,
            playabilityReason = marker,
            publishedText = marker,
            fromWatchPage = false,
        )

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
            musicPlaylist, queue, musicArtistRef, post, about, channelLink, searchVideo, searchChannel, searchPlaylist,
            chapter, song, videoInfo, related, mix, comment, comments, entry, syncPage, state, syncResult,
        ).joinToString()

        assertFalse(marker in rendered, "Diagnostic rendering exposed opaque continuation or private content")
        assertTrue("continuation=true" in channel.toString())
        assertTrue("videos=1" in playlist.toString())
        assertTrue("items=1" in musicSearch.toString())
        assertTrue("comments=1" in comments.toString())
        assertTrue("keys=1" in state.toString())
        assertTrue("error=true" in syncResult.toString())
    }
    @Test
    fun privateCatalogAndPlaybackDetailsAreNotRendered() {
        val marker = "synthetic-private-account-value"
        val track = ExcorMusicTrack(
            provider = ExcorCatalogProvider.APPLE_MUSIC,
            id = marker,
            title = marker,
            url = "https://music.example/track?token=$marker",
        )
        val page = ExcorMusicPage(items = listOf(track), next = marker, total = 1)
        val artist = ExcorMusicArtist(ExcorCatalogProvider.APPLE_MUSIC, marker, marker, url = "https://music.example/artist/$marker")
        val album = ExcorMusicAlbum(
            provider = ExcorCatalogProvider.APPLE_MUSIC,
            id = marker,
            title = marker,
            url = "https://music.example/album/$marker",
            artists = listOf(artist),
            tracks = page,
        )
        val profile = ExcorMusicArtistProfile(
            provider = ExcorCatalogProvider.APPLE_MUSIC,
            id = marker,
            name = marker,
            url = "https://music.example/profile/$marker",
        )
        val playlist = ExcorMusicPlaylist(
            provider = ExcorCatalogProvider.APPLE_MUSIC,
            id = marker,
            title = marker,
            description = marker,
            ownerName = marker,
            ownerId = marker,
            snapshot = marker,
            tracks = page,
        )
        val ref = ExcorMusicRef(ExcorCatalogProvider.APPLE_MUSIC, ExcorMusicKind.TRACK, marker)
        val search = ExcorMusicSearchResult(tracks = page, albums = ExcorMusicPage(listOf(album), marker, 1))
        val episode = ExcorPodcastEpisode(
            id = marker,
            title = marker,
            description = marker,
            showId = marker,
            showTitle = marker,
            durationMs = 1,
            releaseDate = marker,
            explicit = false,
            languages = listOf(marker),
            playable = true,
            url = "https://podcast.example/episode/$marker",
            artwork = emptyList(),
        )
        val show = ExcorPodcastShow(
            id = marker,
            title = marker,
            description = marker,
            publisher = marker,
            explicit = false,
            languages = listOf(marker),
            totalEpisodes = 1,
            url = "https://podcast.example/show/$marker",
            artwork = emptyList(),
            episodes = ExcorMusicPage(listOf(episode), marker, 1),
        )
        val playback = ExcorSpotifyPlaybackState(
            isPlaying = true,
            progressMs = 12_345,
            deviceName = marker,
            deviceType = marker,
            track = track,
        )
        val device = ExcorSpotifyDevice(marker, marker, marker, active = true, restricted = false)

        val rendered = listOf(track, page, artist, album, profile, playlist, ref, search, episode, show, playback, device).joinToString()
        assertFalse(marker in rendered, "Music diagnostics exposed private library or playback state")
        assertTrue("tracks=1" in album.toString())
        assertTrue("episodes=1" in show.toString())
        assertTrue("track=true" in playback.toString())
        assertTrue("active=true" in device.toString())
    }

    @Test
    fun genericCatalogStringsRedactNamesTitlesAndIdentifiers() {
        val marker = "synthetic-private-catalog-value"
        val artist = ExcorCatalogArtist(
            provider = ExcorCatalogProvider.APPLE_MUSIC,
            id = marker,
            name = marker,
            url = "https://music.example/artist/$marker",
        )
        val item = ExcorCatalogItem(
            provider = ExcorCatalogProvider.APPLE_MUSIC,
            kind = ExcorCatalogKind.TRACK,
            id = marker,
            title = marker,
            url = "https://music.example/item/$marker",
            artists = listOf(artist),
            albumId = marker,
            albumTitle = marker,
            genres = listOf(marker),
        )
        val page = ExcorCatalogPage(items = listOf(item), nextPageToken = marker)
        val rendered = listOf(artist, item, page).joinToString()

        assertFalse(marker in rendered, "Catalog diagnostics exposed private labels or identifiers")
        assertTrue("artists=1" in item.toString())
        assertTrue("items=1" in page.toString())
        assertTrue("nextPageToken=present" in page.toString())
    }

    @Test
    fun streamAndAudioDiagnosticStringsRedactSignedUrlsAndOpaqueIds() {
        val marker = "synthetic-private-stream-marker"
        val signedUrl = "https://video.example/videoplayback?signature=$marker"
        val audioFormat = ExcorAudioFormat(codec = ExcorAudioCodec.AAC, codecString = marker)
        val audioOption = ExcorAudioOption(id = marker, format = audioFormat)
        val stream = ExcorStream(
            videoId = marker, url = signedUrl, kbps = 128, mimeType = marker, loudnessDb = null,
            clientName = marker, profileId = marker, headers = mapOf(marker to marker),
        )
        val videoTrack = ExcorVideoTrack(
            url = signedUrl, mimeType = marker, codec = ExcorVideoCodec.H264, width = 1920, height = 1080,
            fps = 30, qualityLabel = marker, kbps = 1000, contentLength = 1024,
            initRange = null, indexRange = null, containsAudio = false, headers = mapOf(marker to marker),
        )
        val mediaFormat = ExcorMediaFormat(
            itag = 18, url = signedUrl, mimeType = marker, kbps = 128, contentLength = 1024,
            hasAudio = true, hasVideo = true, width = 640, height = 360, fps = 30,
            qualityLabel = marker, videoCodec = ExcorVideoCodec.H264, audioChannels = 2, sampleRate = 48_000,
            initRange = null, indexRange = null,
        )
        val formats = ExcorFormatList(
            videoId = marker, clientName = marker, profileId = marker, headers = mapOf(marker to marker),
            formats = listOf(mediaFormat),
        )
        val streams = ExcorVideoStreams(
            videoId = marker, video = videoTrack, audio = stream, clientName = marker, profileId = marker,
        )
        val candidate = ExcorAudioCandidate(url = signedUrl, kbps = 128, mimeType = marker, format = audioFormat)
        val selection = ExcorAudioSelection(
            chosen = audioOption,
            rejected = mapOf(marker to ExcorAudioRejection.PROTECTED),
        )
        val capabilities = ExcorAudioCapabilities(
            provider = ExcorCatalogProvider.APPLE_MUSIC,
            traits = emptySet(),
            formats = listOf(audioFormat),
            lossless = false,
            hiResLossless = false,
            spatial = false,
            previewAvailable = true,
            protection = null,
        )
        val session = ExcorSession(cookie = marker, visitorData = marker, region = marker, language = marker)
        val rendered = listOf(
            stream, videoTrack, mediaFormat, formats, streams, candidate, audioFormat, audioOption,
            selection, capabilities, session,
        ).joinToString()

        assertFalse(marker in rendered, "Diagnostics exposed a signed URL, identifier, header, or session value")
        assertFalse("signature=" in rendered)
        assertTrue("formats=1" in formats.toString())
        assertTrue("rejected=1" in selection.toString())
        assertTrue("cookie=present" in session.toString())
    }

}
