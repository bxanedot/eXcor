package com.excor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HlsManifestTest {
    private val original =
        "https://manifest.googlevideo.com/api/manifest/hls_variant/expire_123/n/old/file/index.m3u8?signature=synthetic"

    @Test
    fun findsOnlyPathNChallengesFromAllowedManifests() {
        assertEquals(listOf("old"), hlsManifestNChallenges(original))
        assertTrue(hlsManifestNChallenges("https://evil.example/api/manifest/hls_variant/a/n/old/file/index.m3u8").isEmpty())
    }

    @Test
    fun solvesPathChallengeAndAddsOnlyTheProvidedToken() {
        val rewritten = rewriteHlsManifestUrl(original, mapOf("old" to "solved"), "host-token")

        assertEquals(
            "https://manifest.googlevideo.com/api/manifest/hls_variant/expire_123/n/solved/pot/host-token/file/index.m3u8?signature=synthetic",
            rewritten,
        )
        assertFalse("old" in rewritten.orEmpty())
        assertFalse("secret" in rewritten.orEmpty())
    }

    @Test
    fun missingChallengeAnswersAndUnsafeTokensFailClosed() {
        assertNull(rewriteHlsManifestUrl(original, emptyMap(), null))
        assertNull(rewriteHlsManifestUrl(original, mapOf("old" to "solved"), "token/with/slash"))
        assertNull(rewriteHlsManifestUrl(original, mapOf("old" to "../escape"), null))
    }

    @Test
    fun aManifestCannotReuseAnotherProvidedToken() {
        val withToken = original.replace("/file/index.m3u8", "/pot/old-token/file/index.m3u8")
        assertNull(rewriteHlsManifestUrl(withToken, mapOf("old" to "solved"), "new-token"))
    }
}
