@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vyonex.app

import com.vyonex.VyonExStream
import com.vyonex.VyonExUnplayableException
import com.vyonex.VyonExUnplayableReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

private const val NOW = 1_800_000_000L

/** Streams as VyonEx returns them: a URL that names its expiry, here [life] seconds from [NOW]. */
private fun streamOf(videoId: String, version: Int = 1, life: Long = 6 * 3600) = VyonExStream(
    videoId, "https://r1.googlevideo.com/videoplayback?id=$videoId&v=$version&expire=${NOW + life}",
    128, "audio/webm; codecs=\"opus\"", null, "VISIONOS", "VISIONOS", mapOf("User-Agent" to "ua"),
)

/** Answers lookups from a script, one answer per call; [gates] hold a lookup until the test opens them. */
private class FakeSource : StreamSource {
    val answers = mutableMapOf<String, ArrayDeque<() -> VyonExStream?>>()
    val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
    val lookups = mutableListOf<String>()
    val refusedUrls = mutableListOf<String>()

    fun answer(videoId: String, vararg next: () -> VyonExStream?) = answers.getOrPut(videoId) { ArrayDeque() }.addAll(next)

    override suspend fun stream(videoId: String): VyonExStream? {
        lookups += videoId
        gates[videoId]?.await()
        val scripted = answers[videoId]?.removeFirstOrNull() ?: return streamOf(videoId)
        return scripted()
    }

    override fun refused(url: String) {
        refusedUrls += url
    }
}

private fun assertReady(track: Track, stream: VyonExStream, state: PlayState) {
    assertIs<PlayState.Ready>(state)
    assertEquals(track to stream, state.track to state.stream)
}

// TestNG runs every test on one instance of the class, so each test makes its own fake.
class PlaybackTest {
    private val abc = listOf(track("a"), track("b"), track("c"))
    private fun TestScope.playback(source: FakeSource) = Playback(source, this, nowSeconds = { NOW })

    @Test
    fun theCurrentTrackIsLookedUpAndTheNextOneAhead() = runTest {
        val source = FakeSource()
        val playback = playback(source)
        playback.play(abc)
        assertEquals(PlayState.Loading(abc[0]), playback.state.value)
        advanceUntilIdle()
        assertReady(abc[0], streamOf("a"), playback.state.value)
        assertEquals(listOf("a", "b"), source.lookups)
        // "b" was fetched ahead, so moving on asks only for "c".
        playback.next()
        advanceUntilIdle()
        assertReady(abc[1], streamOf("b"), playback.state.value)
        assertEquals(listOf("a", "b", "c"), source.lookups)
    }

    @Test
    fun endingFollowsRepeat() = runTest {
        val source = FakeSource()
        val playback = playback(source)
        playback.play(abc, start = 2)
        advanceUntilIdle()
        playback.ended()
        assertEquals(PlayState.Idle, playback.state.value)

        playback.setRepeat(RepeatMode.ALL)
        playback.skipTo(2)
        advanceUntilIdle()
        val lookups = source.lookups.size
        playback.ended()
        advanceUntilIdle()
        // Repeating all wraps to the first track, which was fetched ahead.
        assertReady(abc[0], streamOf("a"), playback.state.value)
        assertEquals(lookups + 1, source.lookups.size)

        playback.setRepeat(RepeatMode.ONE)
        val before = source.lookups.size
        val playing = playback.state.value
        playback.ended()
        advanceUntilIdle()
        // Repeating one replays the same stream without asking again, as a new state the player sees.
        assertReady(abc[0], streamOf("a"), playback.state.value)
        assertNotEquals(playing, playback.state.value)
        assertEquals(before, source.lookups.size)
    }

    @Test
    fun streamsAboutToExpireAreLookedUpAgain() = runTest {
        val source = FakeSource()
        source.answer("a", { streamOf("a", life = 30) }, { streamOf("a", version = 2) })
        source.answer("b", { streamOf("b", life = 30) }, { streamOf("b", version = 2) })
        val playback = playback(source)
        playback.play(abc.take(2))
        advanceUntilIdle()
        // "b" fetched ahead expires within the margin, so it is asked for again when it becomes current.
        playback.next()
        advanceUntilIdle()
        assertReady(abc[1], streamOf("b", version = 2), playback.state.value)
        assertEquals(listOf("a", "b", "b"), source.lookups)
        // So is the current one when repeated.
        playback.skipTo(0)
        advanceUntilIdle()
        playback.setRepeat(RepeatMode.ONE)
        playback.ended()
        advanceUntilIdle()
        assertReady(abc[0], streamOf("a", version = 2), playback.state.value)
    }

    @Test
    fun aRefusedStreamIsReportedAndLookedUpOnceMore() = runTest {
        val source = FakeSource()
        source.answer("a", { streamOf("a") }, { streamOf("a", version = 2) })
        val playback = playback(source)
        playback.play(abc.take(1))
        advanceUntilIdle()
        playback.refused("https://elsewhere.example/other")
        assertReady(abc[0], streamOf("a"), playback.state.value)

        playback.refused(streamOf("a").url)
        advanceUntilIdle()
        assertReady(abc[0], streamOf("a", version = 2), playback.state.value)
        // A second refusal ends the attempts for this track.
        playback.refused(streamOf("a", version = 2).url)
        assertEquals(PlayState.Failed(abc[0], Failure.NO_STREAM, null), playback.state.value)
        assertEquals(listOf(streamOf("a").url, streamOf("a", version = 2).url), source.refusedUrls)
    }

    @Test
    fun refusalsYieldAFailureAndNothingIsFetchedAhead() = runTest {
        val source = FakeSource()
        val bot = "Sign in to confirm you’re not a bot"
        source.answer("a", { throw VyonExUnplayableException("a", VyonExUnplayableReason.SIGN_IN_REQUIRED, "LOGIN_REQUIRED", bot) })
        source.answer("b", { null })
        val playback = playback(source)
        playback.play(abc)
        advanceUntilIdle()
        assertEquals(PlayState.Failed(abc[0], Failure.BOT_CHECK, bot), playback.state.value)
        assertEquals(listOf("a"), source.lookups)
        playback.next()
        advanceUntilIdle()
        assertEquals(PlayState.Failed(abc[1], Failure.NO_STREAM, null), playback.state.value)
    }

    @Test
    fun failuresAsTheListenerSeesThem() {
        fun failure(reason: VyonExUnplayableReason, said: String? = "x") = failureOf(VyonExUnplayableException("v", reason, "S", said)).first
        assertEquals(Failure.SIGN_IN_REQUIRED, failure(VyonExUnplayableReason.SIGN_IN_REQUIRED, "Sign in to view"))
        assertEquals(Failure.AGE_RESTRICTED, failure(VyonExUnplayableReason.AGE_RESTRICTED))
        assertEquals(Failure.UNAVAILABLE, failure(VyonExUnplayableReason.UNAVAILABLE))
        assertEquals(Failure.NOT_YET_LIVE, failure(VyonExUnplayableReason.UPCOMING))
        assertEquals(Failure.NOT_IN_COUNTRY, failure(VyonExUnplayableReason.GEO_RESTRICTED))
        assertEquals(Failure.BLOCKED, failure(VyonExUnplayableReason.OTHER))
        assertEquals(Failure.NETWORK to null, failureOf(IllegalStateException("player API HTTP 500")))
    }

    @Test
    fun movingOnDropsAnUnfinishedLookup() = runTest {
        val source = FakeSource()
        source.gates["a"] = CompletableDeferred()
        val playback = playback(source)
        playback.play(abc)
        advanceUntilIdle()
        assertIs<PlayState.Loading>(playback.state.value)
        playback.skipTo(1)
        source.gates.getValue("a").complete(Unit)
        advanceUntilIdle()
        assertReady(abc[1], streamOf("b"), playback.state.value)
    }

    @Test
    fun removingTheCurrentTrackPlaysTheNext() = runTest {
        val source = FakeSource()
        val playback = playback(source)
        playback.play(abc)
        advanceUntilIdle()
        playback.remove(0)
        advanceUntilIdle()
        assertReady(abc[1], streamOf("b"), playback.state.value)
        assertEquals(listOf("b", "c"), playback.queue.value.tracks.map { it.videoId })
    }
}
