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
) {
    override fun toString(): String =
        "PlayerResponse(status=${playabilityStatus?.let(::safePlaybackReason)}, " +
            "reason=${playabilityReason?.let(::safePlaybackReason)}, formats=${formats.size}, " +
            "playerScriptUrl=${if (playerScriptUrl == null) "missing" else "present"}, loudnessDb=$loudnessDb)"
}

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
        playabilityStatus = root.objectValue("playabilityStatus")?.stringValue("status")
            ?.let(::safePlaybackReason)?.take(64),
        playabilityReason = root.objectValue("playabilityStatus")?.stringValue("reason")
            ?.let(::safePlaybackReason),
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
        width = obj.intValue("width"),
        height = obj.intValue("height"),
        fps = obj.intValue("fps"),
        qualityLabel = obj.stringValue("qualityLabel")?.filter { it >= ' ' && it != '\u007f' }?.take(32),
        contentLength = obj.stringValue("contentLength")?.toLongOrNull()?.takeIf { it > 0 },
        initRange = obj.rangeValue("initRange"),
        indexRange = obj.rangeValue("indexRange"),
        segmented = obj.stringValue("type") == "FORMAT_STREAM_TYPE_OTF",
    )
}

private fun JsonObject.rangeValue(key: String): LongRange? {
    val range = objectValue(key) ?: return null
    val start = range.stringValue("start")?.toLongOrNull() ?: return null
    val end = range.stringValue("end")?.toLongOrNull() ?: return null
    return if (start in 0..end) start..end else null
}

private fun JsonObject.objectValue(key: String): JsonObject? = get(key) as? JsonObject
private fun JsonObject.arrayValue(key: String): JsonArray? = get(key) as? JsonArray
private fun JsonObject.stringValue(key: String): String? = get(key)?.primitiveContent()
private fun JsonObject.intValue(key: String): Int? = get(key)?.primitiveContent()?.toIntOrNull()
private fun JsonElement.primitiveContent(): String? = (this as? JsonPrimitive)?.contentOrNull
