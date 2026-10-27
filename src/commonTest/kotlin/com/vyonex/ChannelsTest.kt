package com.vyonex

import com.vyonex.internal.channelOf
import com.vyonex.internal.channelPageMoreOf
import com.vyonex.internal.channelPageOf
import com.vyonex.internal.channelTabParamsOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** On captures of Rick Astley's channel (UCuAXFkgsw1L7xaCfnd5JJOw) and its tabs, logged out. */
class ChannelsTest {
    private val id = "UCuAXFkgsw1L7xaCfnd5JJOw"

    @Test
    fun theChannelPageGivesItsDetailsAndTabs() {
        val channel = assertNotNull(channelOf(fixtureJson("browse-channel.json")))
        assertEquals(id to "Rick Astley", channel.channelId to channel.title)
        assertEquals("@RickAstleyYT", channel.handle)
        assertTrue(channel.avatars.isNotEmpty() && channel.banners.isNotEmpty(), "$channel")
        assertEquals(VyonExChannelTab.entries.toList(), channel.tabs)
        // Tabs are found by their URL, so the params match what the captures of each tab were asked with.
        assertEquals("EgZ2aWRlb3PyBgQKAjoA", channelTabParamsOf(fixtureJson("browse-channel.json"))[VyonExChannelTab.VIDEOS])
    }

    @Test
    fun tabsListVideosShortsAndPlaylists() {
        val videos = channelPageOf(fixtureJson("browse-channel-videos.json"))
        assertEquals(30, videos.items.size)
        assertTrue(videos.items.all { it.kind == VyonExResultKind.VIDEO && it.id.length == 11 && it.durationSeconds != null }, "${videos.items}")
        assertNotNull(videos.continuation)

        val shorts = channelPageOf(fixtureJson("browse-channel-shorts.json"))
        assertEquals(48, shorts.items.size)
        assertTrue(shorts.items.all { it.kind == VyonExResultKind.VIDEO && !it.title.isNullOrEmpty() && it.thumbnails.isNotEmpty() }, "${shorts.items.take(3)}")

        val playlists = channelPageOf(fixtureJson("browse-channel-playlists.json"))
        assertEquals(13, playlists.items.size)
        assertTrue(playlists.items.all { it.kind == VyonExResultKind.PLAYLIST && (it.id.startsWith("PL") || it.id.startsWith("OL")) }, "${playlists.items}")
        assertEquals("Rick Astley Live at The O2", playlists.items.first().title)
    }

    @Test
    fun theNextPageOfVideos() {
        val more = channelPageMoreOf(fixtureJson("browse-channel-videos-continuation.json"))
        assertEquals(30, more.items.size)
        val first = channelPageOf(fixtureJson("browse-channel-videos.json")).items.map { it.id }.toSet()
        assertTrue(more.items.none { it.id in first })
    }

    @Test
    fun aTabNeedsTheChannelPageOnlyOnce() = runTest {
        val asked = mutableListOf<String>()
        val engine = fixtureEngine(FakeHost(), browse = { body ->
            asked += body
            fixture(if ("\"params\"" in body) "browse-channel-videos.json" else "browse-channel.json")
        })
        assertEquals(30, engine.channelTab(id, VyonExChannelTab.VIDEOS).items.size)
        assertEquals(2, asked.size)
        engine.channelTab(id, VyonExChannelTab.VIDEOS)
        assertEquals(3, asked.size)
        assertTrue("EgZ2aWRlb3PyBgQKAjoA" in asked.last())
    }
}
