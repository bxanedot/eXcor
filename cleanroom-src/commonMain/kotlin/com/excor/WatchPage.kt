package com.excor

internal data class WatchPage(
    val apiKey: String?,
    val playerResponse: PlayerResponse,
)

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
    return WatchPage(apiKey, player)
}

private val API_KEY = Regex("\\\"INNERTUBE_API_KEY\\\"\\s*:\\s*\\\"([^\\\"]{1,256})\\\"")

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
