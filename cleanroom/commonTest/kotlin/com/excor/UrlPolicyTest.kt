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

    @Test
    fun acceptsOnlyBoundedGoogleVideoHlsManifestPaths() {
        listOf(
            "https://manifest.googlevideo.com/api/manifest/hls_variant/expire_123/file/index.m3u8?signature=test",
            "https://rr1---sn.googlevideo.com/api/manifest/hls_playlist/expire_123/playlist/index.m3u8",
        ).forEach { assertTrue(isAllowedHlsManifestUrl(it), it) }

        listOf(
            "http://manifest.googlevideo.com/api/manifest/hls_variant/a/file/index.m3u8",
            "https://manifest.googlevideo.com:444/api/manifest/hls_variant/a/file/index.m3u8",
            "https://user@manifest.googlevideo.com/api/manifest/hls_variant/a/file/index.m3u8",
            "https://manifest.googlevideo.com.evil.example/api/manifest/hls_variant/a/file/index.m3u8",
            "https://youtube.com/api/manifest/hls_variant/a/file/index.m3u8",
            "https://manifest.googlevideo.com/api/manifest/hls_variant/../file/index.m3u8",
            "https://manifest.googlevideo.com/api/manifest/hls_variant/a/videoplayback",
            "https://manifest.googlevideo.com/api/manifest/hls_variant/a/file/index.m3u8#fragment",
        ).forEach { assertFalse(isAllowedHlsManifestUrl(it), it) }
    }
}
