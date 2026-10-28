package com.vyonex.app

import com.vyonex.VyonExStream
import com.vyonex.VyonExUnplayableException
import com.vyonex.VyonExUnplayableReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What playback needs from VyonEx; the app passes VyonEx in, tests a fake. */
interface StreamSource {
    /**
     * The audio stream for [track] (`VyonEx.extract` for YouTube); throws `VyonExUnplayableException` when YouTube
     * refuses, [Refused] when another source does.
     */
    suspend fun stream(track: Track): VyonExStream?

    /** The player was refused [url] (HTTP 403): `VyonEx.onRefused`, so the next lookup avoids that client. */
    fun refused(url: String)
}

/** Why a track does not play, as the app tells the listener. No workaround is offered for any of them. */
enum class Failure {
    SIGN_IN_REQUIRED, AGE_RESTRICTED, UNAVAILABLE, NOT_YET_LIVE, NOT_IN_COUNTRY, BLOCKED,

    /** YouTube asked to confirm this is not a bot: nothing is retried or fetched ahead until the listener acts. */
    BOT_CHECK,

    /** No stream that plays, or the one given was refused again after a retry. */
    NO_STREAM,

    /** The lookup itself failed: no connection, an unexpected answer. */
    NETWORK,

    /** The source only offers this one encrypted (DRM), which VyonEx does not play. */
    DRM,
}

/** A source other than YouTube refused a track, for [failure]. */
class Refused(val failure: Failure) : Exception("refused: $failure")

sealed interface PlayState {
    data object Idle : PlayState
    data class Loading(val track: Track) : PlayState

    /**
     * Start [stream] (its URL with its headers) from the beginning. Not a data class: each start, a repeat of the same
     * stream too, is a new value of [Playback.state], so a collector sees it.
     */
    class Ready(val track: Track, val stream: VyonExStream) : PlayState

    /** [youtubeReason] is YouTube's own message when it gave one. */
    data class Failed(val track: Track, val failure: Failure, val youtubeReason: String?) : PlayState
}

internal fun failureOf(error: Throwable): Pair<Failure, String?> = when (error) {
    is VyonExUnplayableException -> when (error.reason) {
        // YouTube words its bot check as a sign-in request ("Sign in to confirm you're not a bot").
        VyonExUnplayableReason.SIGN_IN_REQUIRED ->
            if ("not a bot" in error.youtubeReason.orEmpty()) Failure.BOT_CHECK else Failure.SIGN_IN_REQUIRED
        VyonExUnplayableReason.AGE_RESTRICTED -> Failure.AGE_RESTRICTED
        VyonExUnplayableReason.UNAVAILABLE -> Failure.UNAVAILABLE
        VyonExUnplayableReason.UPCOMING -> Failure.NOT_YET_LIVE
        VyonExUnplayableReason.GEO_RESTRICTED -> Failure.NOT_IN_COUNTRY
        VyonExUnplayableReason.OTHER -> Failure.BLOCKED
    } to error.youtubeReason
    is Refused -> error.failure to null
    else -> Failure.NETWORK to null
}

// Stream URLs carry their end of life as `expire` (Unix seconds); one this close to it is looked up again.
private const val EXPIRY_MARGIN_SECONDS = 60
// A media URL has `expire=` in its query, a manifest URL an `/expire/` segment in its path.
private val EXPIRE = Regex("""(?:[?&]expire=|/expire/)(\d+)""")

internal fun expiresAt(stream: VyonExStream): Long? = EXPIRE.find(stream.url)?.groupValues?.get(1)?.toLongOrNull()

/**
 * The queue and what plays now. Each track's stream is looked up when it becomes current (stream URLs expire, so not
 * earlier), and the next one's while the current one plays. The platform player reports back with [ended] and
 * [refused].
 */
class Playback(
    private val source: StreamSource,
    private val scope: CoroutineScope,
    private val nowSeconds: () -> Long,
) {
    private val _queue = MutableStateFlow(Queue())
    val queue: StateFlow<Queue> = _queue.asStateFlow()
    private val _state = MutableStateFlow<PlayState>(PlayState.Idle)
    val state: StateFlow<PlayState> = _state.asStateFlow()

    private var loading: Job? = null
    private var ahead: Pair<String, Deferred<Result<VyonExStream?>>>? = null
    private var retried = false

    fun play(tracks: List<Track>, start: Int = 0) {
        _queue.value = Queue(tracks, start, _queue.value.repeat)
        load()
    }

    fun next() = go(_queue.value.nextIndex(ended = false))

    fun previous() = go(_queue.value.previousIndex())

    fun skipTo(index: Int) = go(index)

    /** The platform player reached the end of the current track. */
    fun ended() = go(_queue.value.nextIndex(ended = true))

    /** The platform player was refused [url] (HTTP 403): the source is told, and the track is looked up once more. */
    fun refused(url: String) {
        val now = _state.value
        if (now !is PlayState.Ready || now.stream.url != url) return
        source.refused(url)
        if (retried) {
            _state.value = PlayState.Failed(now.track, Failure.NO_STREAM, null)
        } else {
            retried = true
            load(retry = true)
        }
    }

    fun setRepeat(mode: RepeatMode) {
        _queue.value = _queue.value.copy(repeat = mode)
    }

    fun shuffle() {
        _queue.value = _queue.value.shuffled()
    }

    fun playNext(track: Track) {
        _queue.value = _queue.value.playNext(track)
    }

    fun add(tracks: List<Track>) {
        _queue.value = _queue.value.add(tracks)
    }

    fun move(from: Int, to: Int) {
        _queue.value = _queue.value.move(from, to)
    }

    fun remove(at: Int) {
        val before = _queue.value
        _queue.value = before.remove(at)
        if (at == before.index) load()
    }

    private fun go(index: Int?) {
        if (index == null) {
            loading?.cancel()
            _state.value = PlayState.Idle
            return
        }
        _queue.value = _queue.value.skipTo(index)
        load()
    }

    private fun load(retry: Boolean = false) {
        loading?.cancel()
        if (!retry) retried = false
        val track = _queue.value.current ?: run {
            _state.value = PlayState.Idle
            return
        }
        // Repeating a track that still plays reuses its stream.
        val playing = (_state.value as? PlayState.Ready)?.takeIf { !retry && it.track.id == track.id && fresh(it.stream) }
        if (playing != null) {
            _state.value = PlayState.Ready(track, playing.stream)
            return
        }
        _state.value = PlayState.Loading(track)
        loading = scope.launch {
            // A stream fetched ahead is used while fresh; anything else (a failure, no stream) is looked up again now.
            val looked = ahead?.takeIf { (id, _) -> !retry && id == track.id }?.second?.await()?.getOrNull()
            ahead = null
            val result = looked?.takeIf(::fresh)?.let { Result.success(it) } ?: lookUp(track)
            val stream = result.getOrElse { error ->
                val (failure, reason) = failureOf(error)
                _state.value = PlayState.Failed(track, failure, reason)
                return@launch
            }
            if (stream == null) {
                _state.value = PlayState.Failed(track, Failure.NO_STREAM, null)
                return@launch
            }
            _state.value = PlayState.Ready(track, stream)
            fetchAhead()
        }
    }

    /** Looks up the track after this one while this one plays, unless it is this one again. */
    private fun fetchAhead() {
        val queue = _queue.value
        val next = queue.nextIndex(ended = true)?.takeIf { it != queue.index }?.let { queue.tracks[it] } ?: return
        if (ahead?.first == next.id) return
        ahead = next.id to scope.async { lookUp(next) }
    }

    private suspend fun lookUp(track: Track): Result<VyonExStream?> = try {
        Result.success(source.stream(track))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

    private fun fresh(stream: VyonExStream) = expiresAt(stream)?.let { it - nowSeconds() > EXPIRY_MARGIN_SECONDS } ?: true
}
