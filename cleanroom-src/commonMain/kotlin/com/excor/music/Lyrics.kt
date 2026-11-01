package com.excor.music

enum class ExcorLyricsSync { NONE, LINE, WORD }

data class ExcorLyricsWord(val startMs: Long, val text: String)

data class ExcorLyricsLine(val startMs: Long?, val text: String, val words: List<ExcorLyricsWord> = emptyList())

/**
 * Lyrics from a licensed source the host chose. [source] names it for attribution; [instrumental] is true only when
 * the source says so. eXcor ships no lyrics source of its own, since lyrics are licensed per use.
 */
data class ExcorLyrics(
    val lines: List<ExcorLyricsLine>,
    val sync: ExcorLyricsSync,
    val language: String? = null,
    val instrumental: Boolean = false,
    val source: String,
)

/** A host-supplied lyrics source, such as a licensed lyrics API. Null when it has none for the track. */
fun interface ExcorLyricsProvider {
    @Throws(Throwable::class)
    suspend fun lyrics(track: ExcorMusicTrack): ExcorLyrics?
}

/**
 * Parses LRC text: `[mm:ss.xx]` line times (several per line repeat it), and the enhanced `<mm:ss.xx>` word times.
 * Metadata tags such as `[ar:…]` are skipped; `[offset:±ms]` is applied. Untimed text gives unsynchronized lyrics.
 */
fun excorParseLrc(text: String, source: String, language: String? = null): ExcorLyrics {
    var offset = 0L
    val timed = mutableListOf<ExcorLyricsLine>()
    val plain = mutableListOf<ExcorLyricsLine>()
    for (raw in text.lineSequence()) {
        val line = raw.trimEnd('\r')
        OFFSET_TAG.matchEntire(line.trim())?.let { offset = it.groupValues[1].toLong(); continue }
        if (META_TAG.matches(line.trim())) continue
        var rest = line
        val times = mutableListOf<Long>()
        while (true) {
            val m = LINE_TIME.find(rest) ?: break
            if (m.range.first != 0) break
            times += timeOf(m) ?: break
            rest = rest.substring(m.range.last + 1)
        }
        if (times.isEmpty()) {
            if (line.isNotBlank()) plain += ExcorLyricsLine(null, line.trim())
            continue
        }
        val marks = WORD_TIME.findAll(rest).toList()
        val words = marks.mapIndexedNotNull { i, m ->
            val end = marks.getOrNull(i + 1)?.range?.first ?: rest.length
            val start = timeOf(m) ?: return@mapIndexedNotNull null
            ExcorLyricsWord(start - offset, rest.substring(m.range.last + 1, end).trim()).takeIf { it.text.isNotEmpty() }
        }
        val clean = WORD_TIME.replace(rest, "").trim().replace(SPACES, " ")
        for (t in times) timed += ExcorLyricsLine(t - offset, clean, words)
    }
    if (timed.isEmpty()) return ExcorLyrics(plain, ExcorLyricsSync.NONE, language, source = source)
    val sorted = timed.sortedBy { it.startMs }
    val sync = if (sorted.any { it.words.isNotEmpty() }) ExcorLyricsSync.WORD else ExcorLyricsSync.LINE
    return ExcorLyrics(sorted, sync, language, source = source)
}

private fun timeOf(m: MatchResult): Long? {
    val minutes = m.groupValues[1].toLongOrNull() ?: return null
    val seconds = m.groupValues[2].toLongOrNull()?.takeIf { it < 60 } ?: return null
    val fraction = m.groupValues[3]
    val ms = if (fraction.isEmpty()) 0 else fraction.padEnd(3, '0').take(3).toLong()
    return minutes * 60_000 + seconds * 1_000 + ms
}

private val LINE_TIME = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
private val WORD_TIME = Regex("<(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?>")
private val OFFSET_TAG = Regex("\\[offset:\\s*([+-]?\\d+)]", RegexOption.IGNORE_CASE)
private val META_TAG = Regex("\\[[a-zA-Z]+:[^]]*]")
private val SPACES = Regex("\\s+")
