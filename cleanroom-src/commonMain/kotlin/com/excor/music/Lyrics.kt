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

/**
 * Parses timed lyrics in TTML (W3C TTML2), the format Apple Music uses: each `<p>` is a line with its `begin`
 * time, and `<span begin=…>` children inside it carry word times. Clock (`hh:mm:ss.fff`, `mm:ss.fff`) and offset
 * (`12.3s`, `450ms`) times are read; `xml:lang` on the root gives the language. Lines without times are untimed.
 */
fun excorParseTtml(ttml: String, source: String): ExcorLyrics {
    val language = TT_LANG.find(ttml)?.groupValues?.get(1)
    val lines = TTML_P.findAll(ttml).mapNotNull { p ->
        val start = attribute(p.groupValues[1], "begin")?.let(::ttmlTime)
        val body = p.groupValues[2]
        val words = TTML_SPAN.findAll(body).mapNotNull { span ->
            val at = attribute(span.groupValues[1], "begin")?.let(::ttmlTime) ?: return@mapNotNull null
            ExcorLyricsWord(at, xmlText(span.groupValues[2]).trim()).takeIf { it.text.isNotEmpty() }
        }.toList()
        val text = xmlText(body.replace(TTML_BR, " ")).replace(SPACES, " ").trim()
        if (text.isEmpty()) null else ExcorLyricsLine(start, text, words)
    }.toList()
    val sync = when {
        lines.any { it.words.isNotEmpty() } -> ExcorLyricsSync.WORD
        lines.isNotEmpty() && lines.all { it.startMs != null } -> ExcorLyricsSync.LINE
        else -> ExcorLyricsSync.NONE
    }
    return ExcorLyrics(if (sync == ExcorLyricsSync.NONE) lines.map { it.copy(startMs = null, words = emptyList()) } else lines, sync, language, source = source)
}

/** A TTML time expression in milliseconds; null when it is not one. */
internal fun ttmlTime(value: String): Long? {
    val v = value.trim()
    TTML_OFFSET.matchEntire(v)?.let { m ->
        val n = m.groupValues[1].toDoubleOrNull() ?: return null
        return when (m.groupValues[2]) {
            "h" -> (n * 3_600_000).toLong(); "m" -> (n * 60_000).toLong(); "s" -> (n * 1_000).toLong(); "ms" -> n.toLong()
            else -> null
        }
    }
    val parts = v.split(':')
    if (parts.size !in 1..3) return null
    val seconds = parts.last().toDoubleOrNull() ?: return null
    val whole = parts.dropLast(1).map { it.toLongOrNull() ?: return null }
    val minutes = whole.fold(0L) { acc, x -> acc * 60 + x }
    return minutes * 60_000 + (seconds * 1_000 + 0.5).toLong()
}

private fun attribute(attributes: String, name: String): String? =
    Regex("(?:^|\\s)(?:\\w+:)?$name\\s*=\\s*\"([^\"]*)\"").find(attributes)?.groupValues?.get(1)

private fun xmlText(fragment: String): String = fragment.replace(XML_TAG, "")
    .replace(Regex("&#(x[0-9a-fA-F]+|[0-9]+);")) { m ->
        val code = m.groupValues[1].let { if (it.startsWith("x")) it.drop(1).toIntOrNull(16) else it.toIntOrNull() }
        code?.takeIf { it in 1..0x10FFFF }?.let { c -> if (c > 0xFFFF) { val u = c - 0x10000; "${(0xD800 + (u shr 10)).toChar()}${(0xDC00 + (u and 0x3FF)).toChar()}" } else c.toChar().toString() } ?: ""
    }
    .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

private val TT_LANG = Regex("<(?:\\w+:)?tt\\b[^>]*\\bxml:lang\\s*=\\s*\"([^\"]+)\"")
private val TTML_P = Regex("<(?:\\w+:)?p\\b([^>]*)>(.*?)</(?:\\w+:)?p>", RegexOption.DOT_MATCHES_ALL)
private val TTML_SPAN = Regex("<(?:\\w+:)?span\\b([^>]*)>(.*?)</(?:\\w+:)?span>", RegexOption.DOT_MATCHES_ALL)
private val TTML_BR = Regex("<(?:\\w+:)?br\\s*/?>")
private val TTML_OFFSET = Regex("([0-9]+(?:\\.[0-9]+)?)(h|ms|m|s)")
private val XML_TAG = Regex("<[^>]+>")
