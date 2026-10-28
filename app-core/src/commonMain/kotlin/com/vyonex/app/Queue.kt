package com.vyonex.app

import kotlin.random.Random

/** Where a track comes from. */
enum class Source { YOUTUBE, SOUNDCLOUD }

/**
 * A track to play, with what its list showed, so it can be shown before it plays. [id] is a YouTube video ID, or a
 * SoundCloud track's page URL.
 */
data class Track(
    val id: String,
    val title: String?,
    val artist: String?,
    val durationSeconds: Long?,
    val artworkUrl: String?,
    val source: Source = Source.YOUTUBE,
)

/** [ONE] repeats the current track when it ends; [ALL] starts again from the first after the last. */
enum class RepeatMode { OFF, ALL, ONE }

/**
 * The play queue: tracks in play order and the current one ([index], -1 before anything plays). Immutable; every
 * change returns a new queue. Shuffling reorders what comes after the current track, once.
 */
data class Queue(val tracks: List<Track> = emptyList(), val index: Int = -1, val repeat: RepeatMode = RepeatMode.OFF) {
    init {
        require(index in -1 until tracks.size) { "index $index outside ${tracks.size} tracks" }
    }

    val current: Track? get() = tracks.getOrNull(index)

    /** Where play goes when the current track ends ([ended]) or "next" is pressed; null when it stops. */
    fun nextIndex(ended: Boolean): Int? = when {
        tracks.isEmpty() -> null
        ended && repeat == RepeatMode.ONE -> index
        index + 1 < tracks.size -> index + 1
        repeat != RepeatMode.OFF -> 0
        else -> null
    }

    /** "Previous": the track before, the last one from the first when repeating all, else the first again. */
    fun previousIndex(): Int? = when {
        tracks.isEmpty() -> null
        index > 0 -> index - 1
        repeat == RepeatMode.ALL -> tracks.lastIndex
        else -> 0
    }

    fun skipTo(index: Int) = copy(index = index)

    /** [track] right after the current one (first, before anything plays). */
    fun playNext(track: Track) = copy(tracks = tracks.toMutableList().apply { add(index + 1, track) })

    fun add(more: List<Track>) = copy(tracks = tracks + more)

    /** Moves the track at [from] to [to]; the current track stays current wherever it ends up. */
    fun move(from: Int, to: Int): Queue {
        val moved = tracks.toMutableList().apply { add(to, removeAt(from)) }
        val current = when {
            index == from -> to
            from < index && to >= index -> index - 1
            from > index && to <= index -> index + 1
            else -> index
        }
        return copy(tracks = moved, index = current)
    }

    /** Removes the track at [at]. Removing the current one makes the next current (the new last one at the end). */
    fun remove(at: Int): Queue {
        val left = tracks.toMutableList().apply { removeAt(at) }
        val current = when {
            at < index -> index - 1
            at == index -> minOf(index, left.lastIndex)
            else -> index
        }
        return copy(tracks = left, index = current)
    }

    /** The tracks after the current one in a random order; what has played and the current track stay put. */
    fun shuffled(random: Random = Random): Queue =
        copy(tracks = tracks.take(index + 1) + tracks.drop(index + 1).shuffled(random))
}
