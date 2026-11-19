package com.excor

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal data class WatchPage(
    val apiKey: String?,
    val playerResponse: PlayerResponse,
) {
    override fun toString(): String =
        "WatchPage(apiKey=${if (apiKey == null) "missing" else "present"}, playerResponse=$playerResponse)"
}

internal fun parseWatchPage(source: String): WatchPage? {
    if (source.length > 5_000_000) return null
    val marker = source.indexOf("ytInitialPlayerResponse")
    if (marker < 0) return null
    val start = source.indexOf('{', marker + "ytInitialPlayerResponse".length)
    if (start < 0) return null
    val end = jsonObjectEnd(source, start)
    if (end < 0) return null
    val player = decodePlayerResponse(source.substring(start, end)) ?: return null
    val apiKey = API_KEY.find(source)?.groupValues?.getOrNull(1)
    // Current pages name the player script in their config (jsUrl, PLAYER_JS_URL), not in the player response's assets.
    val scriptUrl = player.playerScriptUrl ?: PLAYER_JS_URL.find(source)?.groupValues?.getOrNull(1)
    return WatchPage(apiKey, player.copy(playerScriptUrl = scriptUrl))
}

internal fun musicWatchEndpointVideoId(source: String): String? {
    if (source.length > 5_000_000) return null
    val marker = INITIAL_ENDPOINT_MARKER.find(source) ?: return null
    val start = source.indexOf('{', marker.range.last + 1)
    if (start < 0) return null
    val end = jsonObjectEnd(source, start)
    if (end < 0) return null
    val endpoint = parseObject(source.substring(start, end)) ?: return null
    return (endpoint.path("watchEndpoint", "videoId") as? JsonPrimitive)?.contentOrNull?.takeIf(VIDEO_ID::matches)
}

private val API_KEY = Regex("\\\"INNERTUBE_API_KEY\\\"\\s*:\\s*\\\"([^\\\"]{1,256})\\\"")
private val INITIAL_ENDPOINT_MARKER = Regex("\\\"INITIAL_ENDPOINT\\\"\\s*:")
private val PLAYER_JS_URL = Regex("\"(?:jsUrl|PLAYER_JS_URL)\"\\s*:\\s*\"(/s/player/[A-Za-z0-9_./-]{1,200}\\.js)\"")

private fun jsonObjectEnd(source: String, start: Int): Int {
    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until source.length) {
        val char = source[index]
        if (inString) {
            if (escaped) escaped = false
            else when (char) {
                '\\' -> escaped = true
                '"' -> inString = false
            }
        } else when (char) {
            '"' -> inString = true
            '{' -> depth++
            '}' -> if (--depth == 0) return index + 1
        }
    }
    return -1
}
