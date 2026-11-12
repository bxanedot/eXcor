package com.excor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Synthetic YouTube Music responses in the shapes music.youtube.com returns.
class YouTubeMusicTest {
    private fun artistRun(name: String, id: String, page: String = "MUSIC_PAGE_TYPE_ARTIST") =
        """{"text":"$name","navigationEndpoint":{"browseEndpoint":{"browseId":"$id","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"$page"}}}}}"""

    private fun watchItem(id: String, title: String, type: String, extraRuns: String = "") = """{"musicResponsiveListItemRenderer":{
        "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"$title"}]}}},
          {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Vidéo"},{"text":" • "},${artistRun("Mira Vale", "UCaaaaaaaaaaaaaaaaaaaaaa", "MUSIC_PAGE_TYPE_USER_CHANNEL")}$extraRuns]}}}],
        "overlay":{"musicItemThumbnailOverlayRenderer":{"content":{"musicPlayButtonRenderer":{"playNavigationEndpoint":{"watchEndpoint":{"videoId":"$id",
          "watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":{"musicVideoType":"$type"}}}}}}}},
        "playlistItemData":{"videoId":"$id"},
        "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://i.ytimg.com/vi/$id/hq.jpg","width":400,"height":225},{"url":"https://evil.example/x.jpg"}]}}}}}"""

    private val search = """{"contents":{"tabbedSearchResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{
        "header":{"chipCloudRenderer":{"chips":[
          {"chipCloudChipRenderer":{"isSelected":false,"navigationEndpoint":{"searchEndpoint":{"query":"q"}}}},
          {"chipCloudChipRenderer":{"text":{"runs":[{"text":"Artistes"}]},"isSelected":false,"navigationEndpoint":{"searchEndpoint":{"params":"EgWKAQIgAWoM%3D"}}}},
          {"chipCloudChipRenderer":{"text":{"runs":[{"text":"Vidéos"}]},"isSelected":true,"navigationEndpoint":{"searchEndpoint":{"params":"EgWKAQIQAWoM"}}}}]}},
        "contents":[
          {"musicCardShelfRenderer":{"title":{"runs":[{"text":"Mira Vale"}]},"subtitle":{"runs":[{"text":"Artiste"},{"text":" • "},{"text":"68M"}]},
            "onTap":{"browseEndpoint":{"browseId":"UCaaaaaaaaaaaaaaaaaaaaaa","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ARTIST"}}}}}},
          {"itemSectionRenderer":{"contents":[${watchItem("AAAAAAAAAAA", "Glass River", "MUSIC_VIDEO_TYPE_ATV", ",{\"text\":\" • \"}," + artistRun("Tides", "MPREb_synthetic", "MUSIC_PAGE_TYPE_ALBUM") + ",{\"text\":\" • \"},{\"text\":\"3:21\"}")}]}},
          {"itemSectionRenderer":{"contents":[${watchItem("BBBBBBBBBBB", "Glass River (Live)", "MUSIC_VIDEO_TYPE_SHOULDER")}]}},
          {"itemSectionRenderer":{"contents":[${watchItem("CCCCCCCCCCC", "Interview", "MUSIC_VIDEO_TYPE_PODCAST_EPISODE")}]}},
          {"itemSectionRenderer":{"contents":[{"musicResponsiveListItemRenderer":{"flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Night drive"}]}}}],
            "navigationEndpoint":{"browseEndpoint":{"browseId":"VLPLsynthetic","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_PLAYLIST"}}}}}}]}},
          {"musicShelfRenderer":{"contents":[],"continuations":[{"nextContinuationData":{"continuation":"musicmore"}}]}}
        ]}}}}]}}}"""

    @Test
    fun searchItemsAreTypedFromStructuredFields() {
        val page = decodeMusicSearch(search)!!
        assertEquals(
            listOf(ExcorYouTubeMusicItemType.ARTIST, ExcorYouTubeMusicItemType.SONG, ExcorYouTubeMusicItemType.VIDEO, ExcorYouTubeMusicItemType.EPISODE, ExcorYouTubeMusicItemType.PLAYLIST),
            page.items.map { it.type },
        )
        val song = page.items[1]
        assertEquals("AAAAAAAAAAA", song.videoId)
        assertEquals(listOf(ExcorYouTubeMusicArtistRef("Mira Vale", "UCaaaaaaaaaaaaaaaaaaaaaa")), song.artists)
        assertEquals("Tides", song.album)
        assertEquals("MPREb_synthetic", song.albumBrowseId)
        assertEquals(201L, song.durationSeconds)
        assertEquals(1, song.thumbnails.size)
        assertEquals("UCaaaaaaaaaaaaaaaaaaaaaa", page.items[0].browseId)
        assertEquals("VLPLsynthetic", page.items[4].browseId)
        // Labeled chips only; the selected one carries no params, and params arrive URL-decoded.
        assertEquals(listOf("Artistes", "Vidéos"), page.filters.map { it.label })
        assertEquals("EgWKAQIgAWoM=", page.filters[0].params)
        assertTrue(page.filters[1].selected && page.filters[1].params == null)
        assertEquals("musicmore", page.continuation)

        val more = decodeMusicSearch("""{"continuationContents":{"musicShelfContinuation":{"contents":[${watchItem("DDDDDDDDDDD", "Next", "MUSIC_VIDEO_TYPE_OMV")}],
            "continuations":[{"nextContinuationData":{"continuation":"more2"}}]}}}""")!!
        assertEquals("DDDDDDDDDDD", more.items.single().videoId)
        assertEquals("more2", more.continuation)
        assertNull(decodeMusicSearch("""{"responseContext":{}}"""))
    }

    @Test
    fun albumsCarryThePlaylistTheirPlayButtonStarts() {
        fun overlay(key: String) = """"$key":{"musicItemThumbnailOverlayRenderer":{"content":{"musicPlayButtonRenderer":{"playNavigationEndpoint":
            {"watchPlaylistEndpoint":{"playlistId":"OLAK5uy_synthetic0123456789"}}}}}}"""
        val albumBrowse = """"navigationEndpoint":{"browseEndpoint":{"browseId":"MPREb_synthetic","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ALBUM"}}}}"""
        val page = decodeMusicSearch("""{"contents":{"tabbedSearchResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
            {"musicShelfRenderer":{"contents":[{"musicResponsiveListItemRenderer":{"flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Tides"}]}}}],
              $albumBrowse,${overlay("overlay")}}}]}}]}}}}]}}}""")!!
        val album = page.items.single()
        assertEquals(ExcorYouTubeMusicItemType.ALBUM, album.type)
        assertEquals("MPREb_synthetic", album.browseId)
        assertEquals("OLAK5uy_synthetic0123456789", album.playlistId)

        // Album cards on artist pages carry it the same way, on the thumbnail overlay.
        val artist = decodeMusicArtist("""{"header":{"musicImmersiveHeaderRenderer":{"title":{"runs":[{"text":"Mira Vale"}]}}},
            "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
            {"musicCarouselShelfRenderer":{"header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Albums"}]}}},"contents":[
              {"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Tides"}]},$albumBrowse,${overlay("thumbnailOverlay")}}}]}}]}}}}]}}}""", "UCaaaaaaaaaaaaaaaaaaaaaa")!!
        val card = artist.shelves.single().items.single()
        assertEquals(ExcorYouTubeMusicItemType.ALBUM, card.type)
        assertEquals("OLAK5uy_synthetic0123456789", card.playlistId)
    }

    @Test
    fun suggestionsArtistPlaylistAndQueue() {
        assertEquals(listOf("mira vale", "mira vale live"), decodeMusicSuggestions("""{"contents":[{"searchSuggestionsSectionRenderer":{"contents":[
            {"searchSuggestionRenderer":{"suggestion":{"runs":[{"text":"mira","bold":true},{"text":" vale"}]}}},
            {"searchSuggestionRenderer":{"suggestion":{"runs":[{"text":"mira vale live"}]}}}]}}]}"""))

        val artist = decodeMusicArtist("""{"header":{"musicImmersiveHeaderRenderer":{"title":{"runs":[{"text":"Mira Vale"}]},
            "description":{"runs":[{"text":"A duo."}]},"monthlyListenerCount":{"runs":[{"text":"68.4M monthly audience"}]},
            "subscriptionButton":{"subscribeButtonRenderer":{"subscriberCountText":{"runs":[{"text":"7.21M"}]}}},
            "startRadioButton":{"buttonRenderer":{"navigationEndpoint":{"watchPlaylistEndpoint":{"playlistId":"RDEMsynthetic"}}}},
            "playButton":{"buttonRenderer":{"navigationEndpoint":{"watchPlaylistEndpoint":{"playlistId":"RDAOsynthetic"}}}},
            "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://lh3.googleusercontent.com/a=w540","width":540,"height":225}]}}}}},
          "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
            {"musicCarouselShelfRenderer":{"header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Videos"}]},
              "moreContentButton":{"buttonRenderer":{"navigationEndpoint":{"browseEndpoint":{"browseId":"VLOLAK5uy_synthetic"}}}}}},
              "contents":[{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Glass River"}]},"subtitle":{"runs":[${artistRun("Mira Vale", "UCaaaaaaaaaaaaaaaaaaaaaa", "MUSIC_PAGE_TYPE_USER_CHANNEL")},{"text":" • 623M views"}]},
                "navigationEndpoint":{"watchEndpoint":{"videoId":"AAAAAAAAAAA","playlistId":"OLAK5uy_synthetic",
                  "watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":{"musicVideoType":"MUSIC_VIDEO_TYPE_OMV"}}}}}}]}},
            {"musicDescriptionShelfRenderer":{}}]}}}}]}}}""", "UCaaaaaaaaaaaaaaaaaaaaaa")!!
        assertEquals("Mira Vale", artist.name)
        assertEquals("68.4M monthly audience", artist.monthlyAudienceText)
        assertEquals("7.21M", artist.subscribersText)
        assertEquals("RDEMsynthetic" to "RDAOsynthetic", artist.radioPlaylistId to artist.shufflePlaylistId)
        val shelf = artist.shelves.single()
        assertEquals("VLOLAK5uy_synthetic", shelf.moreBrowseId)
        assertEquals(ExcorYouTubeMusicItemType.VIDEO, shelf.items.single().type)
        assertEquals("OLAK5uy_synthetic", shelf.items.single().playlistId)
        assertEquals(1, artist.thumbnails.size)
        // The newer header variant: both buttons use a watchEndpoint that also names a first video.
        val mix = decodeMusicArtist("""{"header":{"musicImmersiveHeaderRenderer":{"title":{"runs":[{"text":"Mira Vale"}]},
            "startRadioButton":{"buttonRenderer":{"navigationEndpoint":{"watchEndpoint":{"videoId":"AAAAAAAAAAA","playlistId":"RDEMsynthetic"}}}},
            "playButton":{"buttonRenderer":{"navigationEndpoint":{"watchEndpoint":{"videoId":"AAAAAAAAAAA","playlistId":"RDAOsynthetic"}}}}}}}""", "UCaaaaaaaaaaaaaaaaaaaaaa")!!
        assertEquals("RDEMsynthetic" to "RDAOsynthetic", mix.radioPlaylistId to mix.shufflePlaylistId)
        assertNull(decodeMusicArtist("""{"header":{}}""", "UCaaaaaaaaaaaaaaaaaaaaaa"))

        fun row(id: String, title: String) = """{"musicResponsiveListItemRenderer":{"flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"$title"}]}}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[${artistRun("Mira Vale", "UCaaaaaaaaaaaaaaaaaaaaaa")}]}}}],
            "fixedColumns":[{"musicResponsiveListItemFixedColumnRenderer":{"text":{"runs":[{"text":"5:22"}]}}}],"playlistItemData":{"videoId":"$id"}}}"""
        val playlist = decodeMusicPlaylist("""{"contents":{"twoColumnBrowseResultsRenderer":{
            "tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[{"musicResponsiveHeaderRenderer":{"title":{"runs":[{"text":"Videos"}]},
              "subtitle":{"runs":[{"text":"Playlist • 2026"}]},"secondSubtitle":{"runs":[{"text":"116 songs"}]}}}]}}}}],
            "secondaryContents":{"sectionListRenderer":{"contents":[{"musicPlaylistShelfRenderer":{"contents":[${row("AAAAAAAAAAA", "Glass River")},
              {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"plmore"}}}}]}}]}}}}}""", "OLAK5uy_synthetic")!!
        assertEquals("Videos", playlist.title)
        assertEquals("116 songs", playlist.secondSubtitle)
        assertEquals(322L, playlist.tracks.single().durationSeconds)
        assertEquals("plmore", playlist.continuation)
        val nextTracks = decodeMusicPlaylist("""{"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[${row("BBBBBBBBBBB", "Tides")}]}}]}""", "OLAK5uy_synthetic")!!
        assertEquals("BBBBBBBBBBB", nextTracks.tracks.single().videoId)
        assertNull(nextTracks.continuation)

        fun queued(id: String) = """{"playlistPanelVideoRenderer":{"videoId":"$id","title":{"runs":[{"text":"Song $id"}]},"lengthText":{"runs":[{"text":"4:02"}]},
            "longBylineText":{"runs":[${artistRun("Mira Vale", "UCaaaaaaaaaaaaaaaaaaaaaa")},{"text":" • "},${artistRun("Tides", "MPREb_synthetic", "MUSIC_PAGE_TYPE_ALBUM")}]},
            "navigationEndpoint":{"watchEndpoint":{"videoId":"$id","watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":{"musicVideoType":"MUSIC_VIDEO_TYPE_ATV"}}}}}}"""
        val queue = decodeMusicQueue("""{"contents":{"singleColumnMusicWatchNextResultsRenderer":{"tabbedRenderer":{"watchNextTabbedResultsRenderer":{"tabs":[{"tabRenderer":{"content":
            {"musicQueueRenderer":{"content":{"playlistPanelRenderer":{"playlistId":"RDAMVMAAAAAAAAAAA","contents":[${queued("AAAAAAAAAAA")},${queued("BBBBBBBBBBB")}],
              "continuations":[{"nextRadioContinuationData":{"continuation":"radiomore"}}]}}}}}}]}}}}}""")!!
        assertEquals("RDAMVMAAAAAAAAAAA", queue.playlistId)
        assertEquals(listOf(ExcorYouTubeMusicItemType.SONG, ExcorYouTubeMusicItemType.SONG), queue.tracks.map { it.type })
        assertEquals("Tides", queue.tracks[0].album)
        assertEquals(242L, queue.tracks[0].durationSeconds)
        assertEquals("radiomore", queue.continuation)
        val nextWindow = decodeMusicQueue("""{"continuationContents":{"playlistPanelContinuation":{"contents":[${queued("CCCCCCCCCCC")}]}}}""")!!
        assertEquals("CCCCCCCCCCC", nextWindow.tracks.single().videoId)
        assertNull(nextWindow.continuation)
    }

    @Test
    fun requestsGoToYouTubeMusicWithItsClient() {
        val session = ExcorSession(null, null, "FR", "fr")
        val search = buildMusicSearchRequest("mira vale", null, "EgWKAQIQAWoM", session)
        assertEquals("https://music.youtube.com/youtubei/v1/search?prettyPrint=false", search.url)
        assertEquals("https://music.youtube.com", search.headers["Origin"])
        assertEquals("67", search.headers["X-YouTube-Client-Name"])
        assertTrue("\"clientName\":\"WEB_REMIX\"" in search.body && "\"params\":\"EgWKAQIQAWoM\"" in search.body && "\"hl\":\"fr\"" in search.body)
        assertTrue("params" !in buildMusicSearchRequest(null, "tok", "EgWKAQIQAWoM", session).body)
        val queue = buildMusicQueueRequest("AAAAAAAAAAA", "RDAMVMAAAAAAAAAAA", null, session).body
        assertTrue("\"isAudioOnly\":true" in queue && "\"playlistId\":\"RDAMVMAAAAAAAAAAA\"" in queue)
        // The WEB client is unchanged for the rest of YouTube.
        assertEquals("https://www.youtube.com", buildSearchRequest("q", null, session).headers["Origin"])
    }

    @Test
    fun aTopResultThatPlaysIsAVideoOrSong() {
        fun watch(id: String, type: String) =
            """{"watchEndpoint":{"videoId":"$id","watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":{"musicVideoType":"$type"}}}}"""
        fun card(id: String, type: String, label: String, onTap: Boolean) = """{"musicCardShelfRenderer":{
            "title":{"runs":[{"text":"Glass River","navigationEndpoint":${watch(id, type)}}]},
            "subtitle":{"runs":[{"text":"$label"},{"text":" • "},${artistRun("Mira Vale", "UCaaaaaaaaaaaaaaaaaaaaaa")},{"text":" • "},{"text":"68M views"},{"text":" • "},{"text":"5:22"}]}
            ${if (onTap) ""","onTap":${watch(id, type)}""" else ""}}}"""
        fun page(card: String) = """{"contents":{"tabbedSearchResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[$card]}}}}]}}}"""

        val video = decodeMusicSearch(page(card("AAAAAAAAAAA", "MUSIC_VIDEO_TYPE_OMV", "Video", onTap = true)))!!.items.single()
        assertEquals(ExcorYouTubeMusicItemType.VIDEO, video.type)
        assertEquals("AAAAAAAAAAA", video.videoId)
        assertEquals("MUSIC_VIDEO_TYPE_OMV", video.musicVideoType)
        assertEquals(322L, video.durationSeconds)
        assertEquals(listOf(ExcorYouTubeMusicArtistRef("Mira Vale", "UCaaaaaaaaaaaaaaaaaaaaaa")), video.artists)
        assertNull(video.browseId)

        // Without onTap, the title's link still plays it; an audio track is a song.
        val song = decodeMusicSearch(page(card("BBBBBBBBBBB", "MUSIC_VIDEO_TYPE_ATV", "Song", onTap = false)))!!.items.single()
        assertEquals(ExcorYouTubeMusicItemType.SONG, song.type)
        assertEquals("BBBBBBBBBBB", song.videoId)
    }
}
