package com.excor

import kotlinx.coroutines.runBlocking
import org.testng.SkipException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LivePlaybackAccessTest {
    @Test
    fun knownAccessRefusalsAreClassifiedAsBlocked() {
        assertTrue(isKnownYouTubeAccessRefusal("Excor Sign in to confirm you're not a bot for video YE7VzlLtp-4"))
        assertTrue(isKnownYouTubeAccessRefusal("Excor Sign in to confirm your age for video id"))
        assertTrue(isKnownYouTubeAccessRefusal("Excor The page needs to be reloaded. for video id"))
    }

    @Test
    fun transportAndParsingErrorsAreNotClassifiedAsAccessBlocked() {
        assertFalse(isKnownYouTubeAccessRefusal("YouTube request failed: NETWORK"))
        assertFalse(isKnownYouTubeAccessRefusal("Could not parse player response"))
        assertFalse(isKnownYouTubeAccessRefusal("Excor Sign in to confirm this page is not a bot for video id"))
        assertFalse(isKnownYouTubeAccessRefusal("Excor The page may need to be reloaded for video id"))
        assertFalse(isKnownYouTubeAccessRefusal(null))
    }

    @Test
    fun knownChallengeSkipsLiveRequest() = runBlocking {
        val error = assertFailsWith<SkipException> {
            liveRequest("id") { throw IllegalStateException("Excor Sign in to confirm you're not a bot for video id") }
        }
        assertTrue("access refusal" in error.message.orEmpty())
    }

    @Test
    fun knownReloadRefusalSkipsLiveRequest() = runBlocking {
        val error = assertFailsWith<SkipException> {
            liveRequest("id") { throw IllegalStateException("Excor The page needs to be reloaded. for video id") }
        }
        assertTrue("blocked" in error.message.orEmpty())
    }

    @Test
    fun unrelatedFailurePropagatesFromLiveRequest() = runBlocking {
        val original = IllegalStateException("Could not parse player response")
        val actual = assertFailsWith<IllegalStateException> {
            liveRequest("id") { throw original }
        }
        assertTrue(actual === original)
    }
}
