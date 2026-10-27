package com.vyonex

import com.vyonex.VyonExRef.Album
import com.vyonex.VyonExRef.Channel
import com.vyonex.VyonExRef.Mix
import com.vyonex.VyonExRef.Playlist
import com.vyonex.VyonExRef.Video
import com.vyonex.internal.parseRef
import kotlin.test.Test
import kotlin.test.assertEquals

class LinksTest {
    // Public IDs from yt-dlp's own test data (Unlicense).
    private val v = "dQw4w9WgXcQ"
    private val pl = "PLRBp0Fe2GpgmgoscNFLxNyBVSFVdYmFkq"
    private val albumTracks = "OLAK5uy_l1m0thk3g31NmIIz_vMIbWtyv7eZixlH0"
    private val album = "MPREb_gTAcphH99wE"

    @Test
    fun videos() = check(
        v to Video(v),
        "  $v \n" to Video(v),
        "https://www.youtube.com/watch?v=$v" to Video(v),
        "http://youtube.com/watch?v=$v&feature=share" to Video(v),
        "https://m.youtube.com/watch?app=desktop&v=$v" to Video(v),
        "www.youtube.com/watch?v=$v" to Video(v),
        "//www.youtube.com/watch?v=$v" to Video(v),
        "HTTPS://WWW.YOUTUBE.COM/watch?v=$v" to Video(v),
        "https://www.youtube.com/?v=$v" to Video(v),
        "https://youtu.be/$v?si=abc" to Video(v),
        "https://www.youtube.com/shorts/$v?feature=share" to Video(v),
        "https://www.youtube.com/live/$v?si=x" to Video(v),
        "https://www.youtube.com/v/$v" to Video(v),
        "https://www.youtube-nocookie.com/embed/$v" to Video(v),
    )

    @Test
    fun startTimes() = check(
        "https://youtu.be/$v?t=42" to Video(v, startSeconds = 42),
        "https://www.youtube.com/watch?v=$v&t=1m30s" to Video(v, startSeconds = 90),
        "https://www.youtube.com/watch?v=$v&t=2h" to Video(v, startSeconds = 7200),
        "https://www.youtube.com/watch?v=$v#t=1:02:03" to Video(v, startSeconds = 3723),
        "https://www.youtube.com/embed/$v?start=10" to Video(v, startSeconds = 10),
        "https://www.youtube.com/watch?v=$v&t=soon" to Video(v),
    )

    @Test
    fun videosOpenedFromAList() = check(
        "https://music.youtube.com/watch?v=$v&list=$albumTracks" to Video(v, albumTracks),
        "https://www.youtube.com/watch?v=$v&list=RD$v&start_radio=1" to Video(v, "RD$v"),
        "https://www.youtube.com/watch?v=$v&list=nonsense" to Video(v),
    )

    @Test
    fun lists() = check(
        pl to Playlist(pl),
        "https://www.youtube.com/playlist?list=$pl" to Playlist(pl),
        "https://www.youtube.com/watch?list=$pl" to Playlist(pl),
        "https://www.youtube.com/watch?v=tooShort&list=$pl" to Playlist(pl),
        "https://www.youtube.com/embed/videoseries?list=$pl" to Playlist(pl),
        "https://music.youtube.com/playlist?list=$albumTracks" to Playlist(albumTracks),
        "https://music.youtube.com/browse/VL$pl" to Playlist(pl),
        "https://www.youtube.com/playlist?list=RD$v" to Mix("RD$v"),
        "RD$v" to Mix("RD$v"),
        album to Album(album),
        "https://music.youtube.com/browse/$album" to Album(album),
        "UCuAXFkgsw1L7xaCfnd5JJOw" to Channel("UCuAXFkgsw1L7xaCfnd5JJOw"),
        "https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw" to Channel("UCuAXFkgsw1L7xaCfnd5JJOw"),
        "https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw/videos" to Channel("UCuAXFkgsw1L7xaCfnd5JJOw"),
        "https://music.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw" to Channel("UCuAXFkgsw1L7xaCfnd5JJOw"),
        "https://music.youtube.com/browse/UCuAXFkgsw1L7xaCfnd5JJOw" to Channel("UCuAXFkgsw1L7xaCfnd5JJOw"),
    )

    @Test
    fun rejectsEverythingElse() = listOf(
        "",
        "   ",
        "dQw4w9WgXc",
        "dQw4w9WgXcQQ",
        "javascript:alert(1)",
        "https://example.com/watch?v=$v",
        "https://www.youtube.com.evil.example/watch?v=$v",
        "https://evilyoutube.com/watch?v=$v",
        "https://user@www.youtube.com/watch?v=$v",
        "https://www.youtube.com:8443/watch?v=$v",
        "https://www.youtube.com/@RickAstleyYT",
        "https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJO",
        "https://www.youtube.com/embed/live_stream?channel=UCuAXFkgsw1L7xaCfnd5JJOw",
        "https://www.youtube.com/watch?v=dQw4w9WgXc",
        "https://www.youtube.com/results?search_query=rick",
    ).forEach { assertEquals(null, parseRef(it), it) }

    private fun check(vararg cases: Pair<String, VyonExRef>) = cases.forEach { (input, ref) -> assertEquals(ref, parseRef(input), input) }
}
