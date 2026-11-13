package com.excor

import kotlinx.coroutines.runBlocking
import org.testng.SkipException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LivePlaybackAccessTest {
    @Test
    fun knownBotChallengeIsClassifiedAsAccessBlocked() {
        assertTrue(isKnownYouTubeAccessChallenge("Excor Sign in to confirm you're not a bot for video YE7VzlLtp-4"))
        assertTrue(isKnownYouTubeAccessChallenge("Excor Sign in to confirm your age for video id"))
    }

    @Test
    fun transportAndParsingErrorsAreNotClassifiedAsAccessBlocked() {
        assertFalse(isKnownYouTubeAccessChallenge("YouTube request failed: NETWORK"))
        assertFalse(isKnownYouTubeAccessChallenge("Could not parse player response"))
        assertFalse(isKnownYouTubeAccessChallenge("Excor Sign in to confirm this page is not a bot for video id"))
        assertFalse(isKnownYouTubeAccessChallenge(null))
    }

    @Test
    fun knownChallengeSkipsLiveRequest() = runBlocking {
        val error = assertFailsWith<SkipException> {
            liveRequest("id") { throw IllegalStateException("Excor Sign in to confirm you're not a bot for video id") }
        }
        assertTrue("access challenge" in error.message.orEmpty())
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
