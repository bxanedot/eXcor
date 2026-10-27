package com.vyonex

import com.vyonex.internal.audioFormatsOf
import com.vyonex.internal.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test
    fun aSingleLanguageVideoKeepsEveryAudioFormat() {
        assertEquals(5, captured.size)
        assertEquals(captured, audioFormatsOf(dubbed(Triple("en.4", "English", false))).map { JsonObject(it - "audioTrack") })
    }
}
