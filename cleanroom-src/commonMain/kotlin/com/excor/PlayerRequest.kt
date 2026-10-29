package com.excor

import com.excor.internal.primary.extraction.PoTokenResult
import io.ktor.http.URLBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal data class PlayerRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String,
) {
    override fun toString(): String = "PlayerRequest(headers=${headers.keys}, body=<redacted>)"
}

internal fun buildPlayerRequest(
    client: ClientIdentity,
    videoId: String,
    session: ExcorSession,
    apiKey: String,
    signatureTimestamp: Int?,
    poToken: PoTokenResult?,
): PlayerRequest? {
    if (apiKey.isBlank() || videoId.isBlank()) return null
    if (client.requiresPlayerScript && signatureTimestamp == null) return null

    val clientContext = buildJsonObject {
        put("clientName", client.name)
        put("clientVersion", client.clientVersion)
        put("clientId", client.clientId)
        put("hl", session.language)
        put("gl", session.region)
        put("userAgent", client.userAgent)
        session.visitorData?.let { put("visitorData", it) }
    }
    val body = buildJsonObject {
        put("context", buildJsonObject { put("client", clientContext) })
        put("videoId", videoId)
        put("contentCheckOk", true)
        put("racyCheckOk", true)
        if (client.requiresPlayerScript) {
            put("playbackContext", buildJsonObject {
                put("contentPlaybackContext", buildJsonObject {
                    put("signatureTimestamp", signatureTimestamp!!)
                })
            })
        }
        poToken?.let {
            put("serviceIntegrityDimensions", buildJsonObject { put("poToken", it.playerRequestToken) })
        }
    }
    val url = URLBuilder("https://www.youtube.com/youtubei/v1/player")
        .apply { parameters.append("key", apiKey) }
        .buildString()
    val headers = buildMap {
        put("Accept", "*/*")
        put("Content-Type", "application/json")
        put("Origin", "https://www.youtube.com")
        put("Referer", "https://www.youtube.com/")
        put("User-Agent", client.userAgent)
        put("X-YouTube-Client-Name", client.clientId.toString())
        put("X-YouTube-Client-Version", client.clientVersion)
        session.visitorData?.takeIf(String::isNotBlank)?.let { put("X-Goog-Visitor-Id", it) }
        session.cookie?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
    }
    return PlayerRequest(url, headers, body.toString())
}
