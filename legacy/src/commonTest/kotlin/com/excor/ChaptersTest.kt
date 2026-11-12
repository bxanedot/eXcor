package com.excor

import com.excor.internal.chaptersFrom
import com.excor.internal.chaptersOfDescription
import com.excor.internal.chaptersOfNext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * No watch-next response with chapters was captured, so these use the shapes yt-dlp reads
 * (`_extract_chapters_from_json`, `_extract_chapters_from_engagement_panel`, `_extract_chapters_from_description`).
 */
private val BAR = """{"playerOverlays":{"playerOverlayRenderer":{"decoratedPlayerBarRenderer":{"decoratedPlayerBarRenderer":{"playerBar":
    {"chapteredPlayerBarRenderer":{"chapters":[
      {"chapterRenderer":{"title":{"simpleText":"Intro"},"timeRangeStartMillis":0}},
      {"chapterRenderer":{"title":{"simpleText":"Verse"},"timeRangeStartMillis":43000}},
      {"chapterRenderer":{"title":{"simpleText":"Chorus"},"timeRangeStartMillis":85500}}]}}}}}}}"""

private val PANEL = """{"engagementPanels":[
    {"engagementPanelSectionListRenderer":{"content":{"structuredDescriptionContentRenderer":{}}}},
    {"engagementPanelSectionListRenderer":{"content":{"macroMarkersListRenderer":{"contents":[
      {"macroMarkersListItemRenderer":{"title":{"simpleText":"Start"},"timeDescription":{"simpleText":"0:00"}}},
      {"macroMarkersListItemRenderer":{"title":{"simpleText":"Middle"},"timeDescription":{"simpleText":"1:30"}}}]}}}}]}"""

class ChaptersTest {
    @Test
    fun chaptersAreKeptInOrderAndWithinTheVideo() {
        val chapters = chaptersFrom(listOf(0.0 to "a", 30.0 to "b", 20.0 to "too early", null to "no time", 50.0 to "c", 500.0 to "after the end"), 212.0, sort = false)
        assertEquals(listOf(ExcorChapter("a", 0.0, 30.0), ExcorChapter("b", 30.0, 50.0), ExcorChapter("c", 50.0, 212.0)), chapters)
        // Without a duration yt-dlp gives none.
        assertTrue(chaptersFrom(listOf(0.0 to "a"), null, sort = false).isEmpty())
    }

    @Test
    fun theWatchNextResponseGivesThePlayerBarElseThePanel() {
        assertEquals(listOf("Intro" to 0.0, "Verse" to 43.0, "Chorus" to 85.5), chaptersOfNext(Json.parseToJsonElement(BAR), 212.0).map { it.title to it.startSeconds })
        assertEquals(listOf("Start" to 0.0, "Middle" to 90.0), chaptersOfNext(Json.parseToJsonElement(PANEL), 212.0).map { it.title to it.startSeconds })
        assertTrue(chaptersOfNext(Json.parseToJsonElement("{}"), 212.0).isEmpty())
    }

    @Test
    fun descriptionTimestampsEitherSideOfTheTitle() {
        val first = "Tracklist:\n0:00 Intro\n1:05 - Second song\n10:30 Last one\nThanks for watching"
        assertEquals(listOf("Intro" to 0.0, "Second song" to 65.0, "Last one" to 630.0), chaptersOfDescription(first, 900.0).map { it.title to it.startSeconds })
        val last = "Intro 0:00\nOutro 1:02:03"
        assertEquals(listOf("Intro" to 0.0, "Outro" to 3723.0), chaptersOfDescription(last, 4000.0).map { it.title to it.startSeconds })
        assertTrue(chaptersOfDescription("No times here", 100.0).isEmpty())
    }

    @Test
    fun chaptersComeFromTheWatchNextResponseThroughTheEngine() = runTest {
        // dQw4w9WgXcQ is 213 s long in the WEB capture.
        val chapters = fixtureEngine(FakeHost(), next = { BAR }).chapters("dQw4w9WgXcQ")
        assertEquals(listOf("Intro", "Verse", "Chorus"), chapters.map { it.title })
        assertEquals(213.0, chapters.last().endSeconds)
    }
}
