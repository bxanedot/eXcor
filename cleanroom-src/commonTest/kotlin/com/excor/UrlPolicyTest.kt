package com.excor

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrlPolicyTest {
    @Test
    fun acceptsOnlyHttpsAllowedHostsAndExactPath() {
        listOf(
            "https://rr1---sn.googlevideo.com/videoplayback?c=WEB",
            "https://youtube.com/videoplayback?c=WEB",
            "https://music.youtube.com/videoplayback?c=WEB",
        ).forEach { assertTrue(isAllowedPlaybackUrl(it), it) }

        listOf(
            "http://rr1.googlevideo.com/videoplayback",
            "https://rr1.googlevideo.com:444/videoplayback",
            "https://user@rr1.googlevideo.com/videoplayback",
            "https://notgooglevideo.com/videoplayback",
            "https://youtube.com.evil.example/videoplayback",
            "https://rr1.googlevideo.com/watch?v=id",
            "https://rr1.googlevideo.com/videoplayback/",
            "https://rr1.googlevideo.com/videoplayback.m3u8",
            "not a URL",
        ).forEach { assertFalse(isAllowedPlaybackUrl(it), it) }
    }
}
