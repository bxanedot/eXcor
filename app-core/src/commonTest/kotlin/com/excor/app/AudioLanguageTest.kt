package com.excor.app

import com.excor.ExcorAudioTrack
import kotlin.test.Test
import kotlin.test.assertEquals

class AudioLanguageTest {
    private fun track(code: String, name: String?, original: Boolean = false, descriptive: Boolean = false) =
        ExcorAudioTrack(code, name, original, isDefault = false, isDescriptive = descriptive)

    @Test
    fun theOriginalFirstThenEachOtherLanguageOnce() {
        val tracks = listOf(
            track("es-US", "Spanish (United States)"),
            track("en", "English original", original = true),
            track("en", "English descriptive", descriptive = true),
            track("de", "German"),
            track("ES-us", "Spanish again"),
        )
        assertEquals(
            listOf(AudioChoice(null, "Original (English original)"), AudioChoice("es-US", "Spanish (United States)"), AudioChoice("de", "German")),
            audioChoicesOf(tracks),
        )
    }

    @Test
    fun withoutAnOriginalLabelOrNamesTheCodesStand() {
        assertEquals(listOf(AudioChoice(null, "Original"), AudioChoice("fr", "fr")), audioChoicesOf(listOf(track("fr", null))))
        assertEquals(emptyList(), audioChoicesOf(emptyList()))
    }

    @Test
    fun theSelectedChoiceIsWhatPlays() {
        val choices = listOf(AudioChoice(null, "Original"), AudioChoice("es-US", "Spanish"), AudioChoice("de", "German"))
        assertEquals(choices[1], selectedChoice(choices, "es-us"))
        // A language this video lacks plays the original.
        assertEquals(choices[0], selectedChoice(choices, "fr"))
        assertEquals(choices[0], selectedChoice(choices, null))
        assertEquals(null, selectedChoice(emptyList(), "de"))
    }
}
