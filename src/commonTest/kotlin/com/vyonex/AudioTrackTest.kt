package com.vyonex

import com.vyonex.internal.Engine
import com.vyonex.internal.audioFormatsOf
import com.vyonex.internal.audioTracksOf
import com.vyonex.internal.metadataOf
import com.vyonex.internal.str
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Videos with dubbed audio list each audio format once per track. No such response was captured (the build machine
 * gets a bot check), so these add the `audioTrack` fields yt-dlp reads to the captured VISIONOS formats.
 */
class AudioTrackTest {
    private val response = fixtureJson("player-response-visionos.json")
    private val captured = audioFormatsOf(response)

    /** The captured response with every audio format repeated once per (id, displayName, audioIsDefault) track. */
    private fun dubbed(vararg tracks: Triple<String, String, Boolean>): JsonObject {
        val data = response["streamingData"]!!.jsonObject
        val adaptive = data["adaptiveFormats"]!!.jsonArray.flatMap { format ->
            if (format.jsonObject["mimeType"].str!!.startsWith("audio/")) {
                tracks.map { (id, name, default) ->
                    JsonObject(format.jsonObject + ("audioTrack" to JsonObject(
                        mapOf("id" to JsonPrimitive(id), "displayName" to JsonPrimitive(name), "audioIsDefault" to JsonPrimitive(default)),
                    )))
                }
            } else {
                listOf(format)
            }
        }
        return JsonObject(response + ("streamingData" to JsonObject(data + ("adaptiveFormats" to JsonArray(adaptive)))))
    }

    private fun tracksOf(formats: List<JsonObject>) = formats.map { it["audioTrack"]!!.jsonObject["id"].str }.toSet()

    @Test
    fun theOriginalTrackWinsOverYouTubesDefault() {
        val formats = audioFormatsOf(dubbed(
            Triple("es-US.3", "Spanish (United States)", true),
            Triple("en.4", "English original", false),
            Triple("en.8", "English descriptive", false),
        ))
        assertEquals(setOf("en.4"), tracksOf(formats))
        assertEquals(captured.size, formats.size)
    }

    @Test
    fun withoutAnOriginalLabelTheDefaultTrackIsKept() {
        // Display names come in the session's language, where "original" may be another word.
        val formats = audioFormatsOf(dubbed(Triple("de.3", "Deutsch", false), Triple("es.3", "Español", true)))
        assertEquals(setOf("es.3"), tracksOf(formats))
    }

    private val threeTracks = dubbed(
        Triple("es-US.3", "Spanish (United States)", true),
        Triple("en.4", "English original", false),
        Triple("en.8", "English descriptive", false),
    )

    @Test
    fun aLanguageAskedForPicksItsTrackButNeverTheDescriptiveOne() {
        assertEquals(setOf("es-US.3"), tracksOf(audioFormatsOf(threeTracks, "es-US")))
        assertEquals(setOf("es-US.3"), tracksOf(audioFormatsOf(threeTracks, "ES-us")))
        // "en" has an original and a descriptive track: the original.
        assertEquals(setOf("en.4"), tracksOf(audioFormatsOf(threeTracks, "en")))
        // A language the video lacks keeps the original track.
        assertEquals(setOf("en.4"), tracksOf(audioFormatsOf(threeTracks, "fr")))
        assertEquals(captured, audioFormatsOf(response, "fr"))
    }

    @Test
    fun theTracksAsMetadataListsThem() {
        assertEquals(
            listOf(
                VyonExAudioTrack("es-US", "Spanish (United States)", isOriginal = false, isDefault = true, isDescriptive = false),
                VyonExAudioTrack("en", "English original", isOriginal = true, isDefault = false, isDescriptive = false),
                VyonExAudioTrack("en", "English descriptive", isOriginal = false, isDefault = false, isDescriptive = true),
            ),
            audioTracksOf(threeTracks),
        )
        assertEquals(audioTracksOf(threeTracks), metadataOf("v9QtM6qnG50", threeTracks).audioTracks)
        assertEquals(emptyList(), audioTracksOf(response))
    }

    @Test
    fun aPreferredLanguageMustLookLikeOne() = runTest {
        val engine = Engine(FakeHost(), tokenProvider = null, store = null, http = HttpClient(MockEngine { error("no request") }))
        listOf("en", "es-US", "zh-Hans", "fil").forEach(engine::preferAudioLanguage)
        engine.preferAudioLanguage(null)
        listOf("", "e", "en_US", "en-", "../x", "en US").forEach { assertFailsWith<IllegalArgumentException>(it) { engine.preferAudioLanguage(it) } }
    }

    @Test
    fun aSingleLanguageVideoKeepsEveryAudioFormat() {
        assertEquals(5, captured.size)
        assertEquals(captured, audioFormatsOf(dubbed(Triple("en.4", "English", false))).map { JsonObject(it - "audioTrack") })
    }
}
