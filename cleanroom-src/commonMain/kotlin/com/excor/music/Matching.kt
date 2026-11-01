package com.excor.music

/** Version markers that make two recordings different even when title and artist agree. */
enum class ExcorVersionMarker { LIVE, REMIX, ACOUSTIC, REMASTER, INSTRUMENTAL, KARAOKE, COVER, DEMO, EDIT, EXTENDED, ACAPELLA, SPED_UP, SLOWED, ORCHESTRAL, MONO, RE_RECORDED }

enum class ExcorMatchVerdict { SAME_RECORDING, LIKELY, POSSIBLE, DIFFERENT }

enum class ExcorMatchEvidence {
    ISRC_EQUAL, ISRC_DIFFERENT, TITLE_EQUAL, TITLE_SIMILAR, TITLE_DIFFERENT, ARTIST_OVERLAP, ARTIST_DIFFERENT,
    DURATION_CLOSE, DURATION_NEAR, DURATION_DIFFERENT, VERSION_EQUAL, VERSION_DIFFERENT, EXPLICIT_EQUAL,
    EXPLICIT_DIFFERENT, ALBUM_UPC_EQUAL, ALBUM_TITLE_EQUAL, RELEASE_YEAR_EQUAL, TRACK_POSITION_EQUAL,
}

/** A candidate accepted only because an option allowed it; the caller must show it as a substitution. */
enum class ExcorSubstitution { CLEAN_FOR_EXPLICIT, EXPLICIT_FOR_CLEAN, DIFFERENT_VERSION }

data class ExcorMatchOptions(
    val allowCleanExplicitSubstitution: Boolean = false,
    val allowVersionSubstitution: Boolean = false,
    /** Above this difference the durations are taken as different recordings. */
    val maxDurationDifferenceMs: Long = 10_000,
    val likelyThreshold: Double = 0.8,
    val possibleThreshold: Double = 0.6,
)

data class ExcorTrackMatch(
    val candidate: ExcorMusicTrack,
    val verdict: ExcorMatchVerdict,
    /** 0 to 1. Only an ISRC agreement reaches 1. */
    val confidence: Double,
    val evidence: List<ExcorMatchEvidence>,
    val substitutions: Set<ExcorSubstitution> = emptySet(),
)

/** A title split into its comparable base, its version markers and any featured artists it names. */
data class ExcorParsedTitle(val base: String, val markers: Set<ExcorVersionMarker>, val featured: List<String>)

fun excorParseTitle(title: String, version: String? = null): ExcorParsedTitle {
    val markers = mutableSetOf<ExcorVersionMarker>()
    val featured = mutableListOf<String>()
    var rest = title
    // Bracketed segments and " - " suffixes carry versions; words in the main title ("Live Forever") do not.
    val segments = mutableListOf<String>()
    rest = BRACKETED.replace(rest) { segments += it.groupValues[1].ifEmpty { it.groupValues[2] }; " " }
    val dash = DASH_SUFFIX.find(rest)
    if (dash != null) {
        segments += dash.groupValues[1]
        rest = rest.substring(0, dash.range.first)
    }
    version?.let { segments += it.trim('(', ')', '[', ']', ' ') }
    INLINE_FEAT.find(rest)?.let { found ->
        featured += splitArtists(found.groupValues[1])
        rest = rest.substring(0, found.range.first)
    }
    for (segment in segments) {
        val lower = fold(segment)
        val feat = FEAT_PREFIX.find(lower)
        if (feat != null) {
            featured += splitArtists(lower.substring(feat.range.last + 1))
            continue
        }
        markers += markersOf(lower)
    }
    return ExcorParsedTitle(normalize(rest), markers, featured.map(::normalize).filter(String::isNotEmpty))
}

/** Lowercased, diacritics folded, punctuation dropped, "&" as "and", single spaces. */
fun excorNormalize(text: String): String = normalize(text)

/**
 * Compares [source] with [candidate]. The rules are deterministic: a disagreement on version, explicitness, primary
 * artist, title or duration makes the verdict DIFFERENT (unless an option allows it, and then the substitution is
 * reported); agreements add to the confidence.
 */
fun excorMatchTrack(source: ExcorMusicTrack, candidate: ExcorMusicTrack, options: ExcorMatchOptions = ExcorMatchOptions()): ExcorTrackMatch {
    val evidence = mutableListOf<ExcorMatchEvidence>()
    val substitutions = mutableSetOf<ExcorSubstitution>()
    var different = false
    var score = 0.0

    val a = excorParseTitle(source.title, source.version)
    val b = excorParseTitle(candidate.title, candidate.version)

    val isrcEqual = source.isrc != null && candidate.isrc != null && source.isrc.equals(candidate.isrc, ignoreCase = true)
    if (isrcEqual) evidence += ExcorMatchEvidence.ISRC_EQUAL
    else if (source.isrc != null && candidate.isrc != null) {
        // Reissues can carry new ISRCs for the same audio, so this lowers confidence rather than rejecting.
        evidence += ExcorMatchEvidence.ISRC_DIFFERENT
        score -= 0.15
    }

    if (a.markers == b.markers) evidence += ExcorMatchEvidence.VERSION_EQUAL
    else {
        evidence += ExcorMatchEvidence.VERSION_DIFFERENT
        if (options.allowVersionSubstitution) substitutions += ExcorSubstitution.DIFFERENT_VERSION else different = true
    }

    if (source.explicit != null && candidate.explicit != null) {
        if (source.explicit == candidate.explicit) evidence += ExcorMatchEvidence.EXPLICIT_EQUAL
        else {
            evidence += ExcorMatchEvidence.EXPLICIT_DIFFERENT
            if (options.allowCleanExplicitSubstitution) {
                substitutions += if (source.explicit) ExcorSubstitution.CLEAN_FOR_EXPLICIT else ExcorSubstitution.EXPLICIT_FOR_CLEAN
            } else {
                different = true
            }
        }
    }

    when {
        a.base == b.base -> { evidence += ExcorMatchEvidence.TITLE_EQUAL; score += 0.35 }
        jaccard(a.base, b.base) >= 0.75 -> { evidence += ExcorMatchEvidence.TITLE_SIMILAR; score += 0.2 }
        else -> { evidence += ExcorMatchEvidence.TITLE_DIFFERENT; if (!isrcEqual) different = true }
    }

    val artistsA = primaryArtists(source)
    val artistsB = primaryArtists(candidate)
    if (artistsA.isNotEmpty() && artistsB.isNotEmpty()) {
        if (artistsA.any { it in artistsB }) { evidence += ExcorMatchEvidence.ARTIST_OVERLAP; score += 0.25 }
        else {
            // Same title by another artist is usually a cover.
            evidence += ExcorMatchEvidence.ARTIST_DIFFERENT
            if (!isrcEqual) different = true
        }
    }

    if (source.durationMs != null && candidate.durationMs != null) {
        val delta = kotlin.math.abs(source.durationMs - candidate.durationMs)
        when {
            delta <= 2_000 -> { evidence += ExcorMatchEvidence.DURATION_CLOSE; score += 0.25 }
            delta <= 5_000 -> { evidence += ExcorMatchEvidence.DURATION_NEAR; score += 0.1 }
            delta > options.maxDurationDifferenceMs -> { evidence += ExcorMatchEvidence.DURATION_DIFFERENT; different = true }
        }
    }

    if (source.upc != null && source.upc == candidate.upc) { evidence += ExcorMatchEvidence.ALBUM_UPC_EQUAL; score += 0.1 }
    else if (source.albumTitle != null && candidate.albumTitle != null &&
        excorParseTitle(source.albumTitle).base == excorParseTitle(candidate.albumTitle).base
    ) { evidence += ExcorMatchEvidence.ALBUM_TITLE_EQUAL; score += 0.05 }
    if (source.releaseDate?.take(4)?.takeIf { it.length == 4 } == candidate.releaseDate?.take(4) && source.releaseDate != null) {
        evidence += ExcorMatchEvidence.RELEASE_YEAR_EQUAL; score += 0.05
    }
    if (source.trackNumber != null && source.trackNumber == candidate.trackNumber &&
        (source.discNumber ?: 1) == (candidate.discNumber ?: 1) && ExcorMatchEvidence.ALBUM_UPC_EQUAL in evidence
    ) { evidence += ExcorMatchEvidence.TRACK_POSITION_EQUAL; score += 0.05 }

    val confidence: Double
    val verdict: ExcorMatchVerdict
    when {
        different -> { confidence = 0.0; verdict = ExcorMatchVerdict.DIFFERENT }
        isrcEqual -> { confidence = 1.0; verdict = ExcorMatchVerdict.SAME_RECORDING }
        else -> {
            confidence = score.coerceIn(0.0, 0.99)
            verdict = when {
                confidence >= options.likelyThreshold -> ExcorMatchVerdict.LIKELY
                confidence >= options.possibleThreshold -> ExcorMatchVerdict.POSSIBLE
                else -> ExcorMatchVerdict.DIFFERENT
            }
        }
    }
    return ExcorTrackMatch(candidate, verdict, confidence, evidence, if (verdict == ExcorMatchVerdict.DIFFERENT) emptySet() else substitutions)
}

/** All candidates scored, best first; ties keep the candidates' order. */
fun excorRankMatches(source: ExcorMusicTrack, candidates: List<ExcorMusicTrack>, options: ExcorMatchOptions = ExcorMatchOptions()): List<ExcorTrackMatch> =
    candidates.map { excorMatchTrack(source, it, options) }
        .sortedWith(compareBy<ExcorTrackMatch> { it.verdict.ordinal }.thenByDescending { it.confidence })

private fun primaryArtists(track: ExcorMusicTrack): Set<String> {
    val listed = track.artists.filter { it.role == ExcorArtistRole.PRIMARY }.ifEmpty { track.artists }
    return listed.flatMap { splitArtists(it.name) }.map(::normalize).filter(String::isNotEmpty).toSet()
}

private fun splitArtists(text: String): List<String> = ARTIST_SEPARATORS.split(text).map { it.trim() }.filter { it.isNotEmpty() }

private fun markersOf(segment: String): Set<ExcorVersionMarker> = buildSet {
    val words = segment.split(NON_WORD).filter(String::isNotEmpty)
    fun has(vararg w: String) = w.any { it in words }
    if (has("live", "concert", "unplugged")) add(ExcorVersionMarker.LIVE)
    if (has("remix", "mix", "rmx", "dub", "bootleg", "rework", "vip") && !has("original")) add(ExcorVersionMarker.REMIX)
    if (has("acoustic", "unplugged")) add(ExcorVersionMarker.ACOUSTIC)
    if (words.any { it.startsWith("remaster") }) add(ExcorVersionMarker.REMASTER)
    if (has("instrumental")) add(ExcorVersionMarker.INSTRUMENTAL)
    if (has("karaoke")) add(ExcorVersionMarker.KARAOKE)
    if (has("cover", "tribute")) add(ExcorVersionMarker.COVER)
    if (has("demo")) add(ExcorVersionMarker.DEMO)
    if (has("edit") || "radio edit" in segment || "single version" in segment) add(ExcorVersionMarker.EDIT)
    if (has("extended")) add(ExcorVersionMarker.EXTENDED)
    if (has("acapella", "cappella")) add(ExcorVersionMarker.ACAPELLA)
    if ("sped up" in segment || has("nightcore")) add(ExcorVersionMarker.SPED_UP)
    if (has("slowed")) add(ExcorVersionMarker.SLOWED)
    if (has("orchestral", "symphonic")) add(ExcorVersionMarker.ORCHESTRAL)
    if (has("mono")) add(ExcorVersionMarker.MONO)
    if ("taylor s version" in segment || has("rerecorded") || "re recorded" in segment) add(ExcorVersionMarker.RE_RECORDED)
}

private fun jaccard(a: String, b: String): Double {
    val x = a.split(' ').filter(String::isNotEmpty).toSet()
    val y = b.split(' ').filter(String::isNotEmpty).toSet()
    if (x.isEmpty() && y.isEmpty()) return 1.0
    return (x intersect y).size.toDouble() / (x union y).size
}

private fun normalize(text: String): String =
    fold(text).replace("&", " and ").replace(NON_WORD, " ").trim().replace(SPACES, " ")

private fun fold(text: String): String = buildString(text.length) {
    for (c in text.lowercase()) {
        val i = FOLD_FROM.indexOf(c)
        when {
            i >= 0 -> append(FOLD_TO[i])
            c == 'ß' -> append("ss")
            c == 'æ' -> append("ae")
            c == 'œ' -> append("oe")
            c == '’' || c == '\'' || c == '`' -> Unit // "don't" and "dont" compare equal
            else -> append(c)
        }
    }
}

// shortcut: Latin letters only, since common Kotlin has no Unicode normalizer; extend the table if other scripts' marks matter.
private const val FOLD_FROM = "àáâãäåāăąçćĉċčďđèéêëēĕėęěĝğġģĥħìíîïĩīĭįıĵķĺļľŀłñńņňòóôõöøōŏőŕŗřśŝşšţťŧùúûüũūŭůűųŵýÿŷźżž"
private const val FOLD_TO = "aaaaaaaaacccccddeeeeeeeeegggghhiiiiiiiiijklllllnnnnooooooooorrrssssttttuuuuuuuuuuwyyyzzz"

private val BRACKETED = Regex("\\(([^)]*)\\)|\\[([^]]*)]")
private val DASH_SUFFIX = Regex("\\s[-–—]\\s(.+)$")
private val INLINE_FEAT = Regex("\\s(?:feat\\.?|ft\\.?|featuring)\\s(.+)$", RegexOption.IGNORE_CASE)
private val FEAT_PREFIX = Regex("^\\s*(?:feat\\.?|ft\\.?|featuring|with)\\s")
private val ARTIST_SEPARATORS = Regex("\\s*(?:,|&|\\band\\b|\\bx\\b|\\bfeat\\.?|\\bft\\.?|\\bfeaturing\\b|\\bwith\\b|;|/)\\s*", RegexOption.IGNORE_CASE)
private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
private val SPACES = Regex("\\s+")
