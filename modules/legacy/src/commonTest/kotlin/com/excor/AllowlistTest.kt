package com.excor

import com.excor.internal.isAllowedUrl
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AllowlistTest {
    @Test
    fun allowsYoutubeMediaUrls() {
        listOf(
            "https://rr5---sn-ajaig5-5h.googlevideo.com/videoplayback?expire=1&c=TVHTML5&sparams=a%2Cb&sig=X%3D",
            "https://googlevideo.com/videoplayback",
            "https://www.youtube.com/videoplayback?c=WEB",
            "https://youtube.com:443/videoplayback?x=1",
        ).forEach { assertTrue(isAllowedUrl(it), it) }
    }

    @Test
    fun rejectsEverythingElse() {
        listOf(
            "http://r1.googlevideo.com/videoplayback",
            "https://r1.googlevideo.com:8443/videoplayback",
            "https://user:pass@r1.googlevideo.com/videoplayback",
            "https://r1.googlevideo.com@evil.com/videoplayback",
            "https://evil.com/videoplayback?r1.googlevideo.com",
            "https://googlevideo.com.evil.com/videoplayback",
            "https://evilgooglevideo.com/videoplayback",
            "https://r1.googlevideo.com/videoplayback/x",
            "https://r1.googlevideo.com/api/manifest/hls_playlist/videoplayback",
            "https://r1.googlevideo.com/videoplaybackx",
            "https://r1.googlevideo.com\\@evil.com/videoplayback",
            "https://r1.googlevideo.com/videoplayback#frag",
            "https://r1.googlevideo.com/videoplayback?a=b c",
            "https://youtube.com.",
            "",
        ).forEach { assertFalse(isAllowedUrl(it), it) }
    }
}
