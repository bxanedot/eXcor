package com.vyonex.app

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

internal fun track(id: String) = Track(id, "title $id", "artist", 200, null)

class QueueTest {
    private val abc = Queue(listOf(track("a"), track("b"), track("c")), index = 0)
    private fun Queue.ids() = tracks.map { it.id }

    @Test
    fun whereNextGoesWithEachRepeatMode() {
        val last = abc.skipTo(2)
        assertNull(last.nextIndex(ended = true))
        assertNull(last.nextIndex(ended = false))
        assertEquals(0, last.copy(repeat = RepeatMode.ALL).nextIndex(ended = true))
        // Repeat one replays the track when it ends; "next" still moves on, wrapping like repeat all.
        assertEquals(2, last.copy(repeat = RepeatMode.ONE).nextIndex(ended = true))
        assertEquals(0, last.copy(repeat = RepeatMode.ONE).nextIndex(ended = false))
        assertEquals(1, abc.nextIndex(ended = true))
        assertNull(Queue().nextIndex(ended = true))
    }

    @Test
    fun previousGoesBackOrStartsOver() {
        assertEquals(1, abc.skipTo(2).previousIndex())
        assertEquals(0, abc.previousIndex())
        assertEquals(2, abc.copy(repeat = RepeatMode.ALL).previousIndex())
    }

    @Test
    fun editingKeepsTheCurrentTrack() {
        val b = abc.skipTo(1)
        assertEquals(listOf("a", "b", "x", "c"), b.playNext(track("x")).ids())
        assertEquals(listOf("x", "a", "b", "c"), Queue(abc.tracks).playNext(track("x")).ids())
        assertEquals(listOf("a", "b", "c", "x", "y"), b.add(listOf(track("x"), track("y"))).ids())
        // Moving tracks around the current one, or the current one itself.
        b.move(0, 2).let { assertEquals(listOf("b", "c", "a"), it.ids()); assertEquals("b", it.current?.id) }
        b.move(2, 0).let { assertEquals(listOf("c", "a", "b"), it.ids()); assertEquals("b", it.current?.id) }
        b.move(1, 0).let { assertEquals(listOf("b", "a", "c"), it.ids()); assertEquals(0, it.index) }
        // Removing: before the current one, the current one (its next becomes current), the last current one.
        assertEquals("b", b.remove(0).current?.id)
        assertEquals("c", b.remove(1).current?.id)
        assertEquals("b", abc.skipTo(2).remove(2).current?.id)
        assertEquals(-1, Queue(listOf(track("a")), 0).remove(0).index)
    }

    @Test
    fun shuffleReordersOnlyWhatComesNext() {
        val tracks = (1..20).map { track("t$it") }
        val shuffled = Queue(tracks, index = 4).shuffled(Random(7))
        assertEquals(tracks.take(5), shuffled.tracks.take(5))
        assertEquals(tracks.toSet(), shuffled.tracks.toSet())
        assertEquals(4, shuffled.index)
        check(shuffled.tracks.drop(5) != tracks.drop(5))
    }

    @Test
    fun theIndexMustPointIntoTheQueue() {
        assertFailsWith<IllegalArgumentException> { Queue(listOf(track("a")), index = 1) }
    }
}
