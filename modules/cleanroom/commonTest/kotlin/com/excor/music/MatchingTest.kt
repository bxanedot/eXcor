package com.excor.music

import com.excor.catalog.ExcorCatalogProvider
import com.excor.catalog.ExcorCatalogProvider.DEEZER
import com.excor.catalog.ExcorCatalogProvider.SPOTIFY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatchingTest {
    private fun track(
        title: String,
        artist: String = "The Example Band",
        duration: Long? = 200_000,
        provider: ExcorCatalogProvider = DEEZER,
        isrc: String? = null,
        explicit: Boolean? = null,
        version: String? = null,
        album: String? = "Example Album",
    ) = ExcorMusicTrack(
        provider, title.hashCode().toString(), title, artists = listOf(ExcorMusicArtist(provider, null, artist)),
        durationMs = duration, isrc = isrc, explicit = explicit, version = version, albumTitle = album,
    )

    private val source = track("Northern Lights", provider = SPOTIFY, explicit = false)

    @Test
    fun parsesVersionsAndFeaturesOnlyFromVersionSegments() {
        val parsed = excorParseTitle("Café Song (feat. Ana & Bo) - Remastered 2011")
        assertEquals("cafe song", parsed.base)
        assertEquals(setOf(ExcorVersionMarker.REMASTER), parsed.markers)
        assertEquals(listOf("ana", "bo"), parsed.featured)
        // "Live" in the title itself is part of the name.
        assertEquals(emptySet(), excorParseTitle("Live Forever").markers)
        assertEquals(setOf(ExcorVersionMarker.LIVE), excorParseTitle("Song", version = "(Live at Somewhere)").markers)
        assertEquals(emptySet(), excorParseTitle("Song (Original Mix)").markers)
        assertEquals(setOf(ExcorVersionMarker.REMIX), excorParseTitle("Song [Someone Remix]").markers)
        assertEquals("dont stop", excorNormalize("Don’t  Stop!"))
    }

    @Test
    fun isrcAgreementIsTheOnlyCertainMatch() {
        val match = excorMatchTrack(source.copy(isrc = "USAAA2400001"), track("Northern Lights", isrc = "usaaa2400001", explicit = false))
        assertEquals(ExcorMatchVerdict.SAME_RECORDING, match.verdict)
        assertEquals(1.0, match.confidence)
        assertTrue(ExcorMatchEvidence.ISRC_EQUAL in match.evidence)

        val textual = excorMatchTrack(source, track("Northern Lights", duration = 201_000, explicit = false))
        assertEquals(ExcorMatchVerdict.LIKELY, textual.verdict)
        assertTrue(textual.confidence < 1.0)
    }

    @Test
    fun rejectsTheUsualFalseFriends() {
        fun verdictOf(candidate: ExcorMusicTrack) = excorMatchTrack(source, candidate).verdict
        assertEquals(ExcorMatchVerdict.DIFFERENT, verdictOf(track("Northern Lights - Live", explicit = false)))
        assertEquals(ExcorMatchVerdict.DIFFERENT, verdictOf(track("Northern Lights (2011 Remaster)", explicit = false)))
        assertEquals(ExcorMatchVerdict.DIFFERENT, verdictOf(track("Northern Lights", explicit = true)))
        assertEquals(ExcorMatchVerdict.DIFFERENT, verdictOf(track("Northern Lights", artist = "Tribute Singers", explicit = false)))
        assertEquals(ExcorMatchVerdict.DIFFERENT, verdictOf(track("Northern Lights (DJ Remix)", explicit = false)))
        assertEquals(ExcorMatchVerdict.DIFFERENT, verdictOf(track("Northern Lights", duration = 260_000, explicit = false)))
        assertEquals(ExcorMatchVerdict.DIFFERENT, verdictOf(track("Southern Nights", explicit = false)))
    }

    @Test
    fun allowedSubstitutionsAreReported() {
        val options = ExcorMatchOptions(allowCleanExplicitSubstitution = true)
        val match = excorMatchTrack(source.copy(explicit = true), track("Northern Lights", explicit = false), options)
        assertEquals(ExcorMatchVerdict.LIKELY, match.verdict)
        assertEquals(setOf(ExcorSubstitution.CLEAN_FOR_EXPLICIT), match.substitutions)
    }

    @Test
    fun featuredCreditsAndPunctuationDoNotBreakAMatch() {
        val spotify = track("Northern Lights (feat. Guest)", provider = SPOTIFY, explicit = false)
        val deezer = track("Northern Lights", artist = "The Example Band, Guest", explicit = false)
        assertEquals(ExcorMatchVerdict.LIKELY, excorMatchTrack(spotify, deezer).verdict)
    }

    @Test
    fun rankingPutsTheBestFirst() {
        val ranked = excorRankMatches(
            source,
            listOf(
                track("Northern Lights - Live", explicit = false),
                track("Northern Lights", duration = 204_000, explicit = false),
                track("Northern Lights", duration = 200_500, explicit = false),
            ),
        )
        assertEquals(listOf(200_500L, 204_000L, 200_000L), ranked.map { it.candidate.durationMs })
        assertEquals(ExcorMatchVerdict.DIFFERENT, ranked.last().verdict)
    }
}
