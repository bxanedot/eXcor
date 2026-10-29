package com.excor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal data class PlayerResponse(
    val playabilityStatus: String?,
    val playabilityReason: String?,
    val formats: List<PlayerFormat>,
    val playerScriptUrl: String?,
    val loudnessDb: Double?,
)

private val playerJson = Json { isLenient = false }

internal fun decodePlayerResponse(source: String): PlayerResponse? {
    if (source.length > 2_000_000) return null
    val root = runCatching { playerJson.parseToJsonElement(source) }.getOrNull() as? JsonObject ?: return null
    val streaming = root.objectValue("streamingData")
    val formats = sequenceOf("formats", "adaptiveFormats")
        .flatMap { key -> streaming?.arrayValue(key)?.asSequence().orEmpty() }
        .take(128)
        .mapNotNull(::decodeFormat)
        .toList()
    val loudness = root.objectValue("playerConfig")
        ?.objectValue("audioConfig")
        ?.get("loudnessDb")
        ?.primitiveContent()
        ?.toDoubleOrNull()
    return PlayerResponse(
        playabilityStatus = root.objectValue("playabilityStatus")?.stringValue("status"),
        playabilityReason = root.objectValue("playabilityStatus")?.stringValue("reason")
            ?.filter { it >= ' ' && it != '\u007f' }?.take(200),
        formats = formats,
        playerScriptUrl = root.objectValue("assets")?.stringValue("js"),
        loudnessDb = loudness,
    )
}

private fun decodeFormat(value: JsonElement): PlayerFormat? {
    val obj = value as? JsonObject ?: return null
    val mime = obj.stringValue("mimeType") ?: return null
    return PlayerFormat(
        url = obj.stringValue("url").orEmpty(),
        mimeType = mime,
        bitrate = obj.intValue("bitrate"),
        averageBitrate = obj.intValue("averageBitrate"),
        channels = obj.intValue("audioChannels"),
        sampleRate = obj.intValue("audioSampleRate"),
        signatureCipher = obj.stringValue("signatureCipher") ?: obj.stringValue("cipher"),
        itag = obj.intValue("itag"),
    )
}

private fun JsonObject.objectValue(key: String): JsonObject? = get(key) as? JsonObject
private fun JsonObject.arrayValue(key: String): JsonArray? = get(key) as? JsonArray
private fun JsonObject.stringValue(key: String): String? = get(key)?.primitiveContent()
private fun JsonObject.intValue(key: String): Int? = get(key)?.primitiveContent()?.toIntOrNull()
private fun JsonElement.primitiveContent(): String? = (this as? JsonPrimitive)?.contentOrNull
