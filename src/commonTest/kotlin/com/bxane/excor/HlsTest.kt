package com.bxane.excor

import com.bxane.excor.internal.mediaPlaylistOf
import com.bxane.excor.internal.resolveUri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class HlsTest {
    private val base = "https://media.example/a/b/playlist.m3u8?sig=1"

    @Test
    fun aFinishedMediaPlaylist() {
        // The shape SoundCloud serves (2026-10-27): fragmented MP4 with an init segment, absolute segment URLs.
        val playlist = mediaPlaylistOf(
            "#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-TARGETDURATION:10\n#EXT-X-MEDIA-SEQUENCE:0\n#EXT-X-PLAYLIST-TYPE:VOD\n" +
                "#EXT-X-MAP:URI=\"init.mp4?x=1\"\n#EXTINF:10.0,\nhttps://media.example/a/b/0.m4s?s=2\r\n#EXTINF:9.9,\n1.m4s\n\n" +
                "#EXT-X-KEY:METHOD=NONE\n#EXTINF:3.1,\n/root/2.m4s\n#EXT-X-ENDLIST\n",
            base,
        )
        assertEquals("https://media.example/a/b/init.mp4?x=1", playlist.init)
        assertEquals(
            listOf("https://media.example/a/b/0.m4s?s=2", "https://media.example/a/b/1.m4s", "https://media.example/root/2.m4s"),
            playlist.segments,
        )
        assertNull(mediaPlaylistOf("#EXTM3U\n#EXTINF:1,\nx.ts\n#EXT-X-ENDLIST", base).init)
    }

    @Test
    fun whatCannotBeOneFileIsRefusedByName() {
        mapOf(
            "<html>" to "Excor: not an HLS playlist",
            "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nv.m3u8\n#EXT-X-ENDLIST" to "Excor: an HLS master playlist, not one stream",
            "#EXTM3U\n#EXTINF:1,\nx.ts" to "Excor: a live HLS playlist cannot be downloaded whole",
            "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"k\"\n#EXTINF:1,\nx.ts\n#EXT-X-ENDLIST" to "Excor: encrypted HLS (DRM) is not downloaded",
            "#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"k\"\n#EXTINF:1,\nx.ts\n#EXT-X-ENDLIST" to "Excor: encrypted HLS (DRM) is not downloaded",
            "#EXTM3U\n#EXTINF:1,\n#EXT-X-BYTERANGE:10@0\nx.ts\n#EXT-X-ENDLIST" to "Excor: HLS byte ranges are not supported",
            "#EXTM3U\n#EXT-X-MAP:URI=\"i\",BYTERANGE=\"5@0\"\n#EXTINF:1,\nx.ts\n#EXT-X-ENDLIST" to "Excor: HLS byte ranges are not supported",
            "#EXTM3U\n#EXT-X-MAP:URI=\"i\"\n#EXT-X-MAP:URI=\"j\"\n#EXTINF:1,\nx.ts\n#EXT-X-ENDLIST" to "Excor: more than one HLS init segment",
            "#EXTM3U\n#EXT-X-ENDLIST" to "Excor: an HLS playlist without segments",
        ).forEach { (text, message) -> assertEquals(message, assertFailsWith<IllegalStateException>(text) { mediaPlaylistOf(text, base) }.message, text) }
    }

    @Test
    fun uris() {
        assertEquals("https://other.example/x", resolveUri(base, "https://other.example/x"))
        assertEquals("https://cdn.example/x", resolveUri(base, "//cdn.example/x"))
        assertEquals("https://media.example/x?y=1", resolveUri(base, "/x?y=1"))
        assertEquals("https://media.example/a/b/x", resolveUri(base, "x"))
        assertEquals("https://media.example/x", resolveUri("https://media.example", "x"))
        assertEquals("https://media.example/x", resolveUri("https://media.example?q=a/b", "x"))
    }
}
