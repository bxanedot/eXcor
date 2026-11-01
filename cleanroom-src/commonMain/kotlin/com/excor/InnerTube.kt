package com.excor

import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * An InnerTube request with the WEB client context, for calls that need neither a player script nor a streaming
 * token (metadata, search, browse). [fields] adds the endpoint's own fields.
 */
internal fun innertubeRequest(endpoint: String, session: ExcorSession, fields: JsonObjectBuilder.() -> Unit): PlayerRequest {
    val client = metadataClient
    val body = buildJsonObject {
        put("context", buildJsonObject {
            put("client", buildJsonObject {
                put("clientName", client.name)
                put("clientVersion", client.clientVersion)
                put("hl", session.language.ifBlank { "en" })
                put("gl", session.region.ifBlank { "US" })
                session.visitorData?.let { put("visitorData", it) }
            })
        })
        fields()
    }
    val headers = buildMap {
        put("Accept", "*/*")
        put("Content-Type", "application/json")
        put("Origin", "https://www.youtube.com")
        put("Referer", "https://www.youtube.com/")
        put("User-Agent", client.userAgent)
        put("X-YouTube-Client-Name", client.clientId.toString())
        put("X-YouTube-Client-Version", client.clientVersion)
        session.visitorData?.takeIf(String::isNotBlank)?.let { put("X-Goog-Visitor-Id", it) }
        session.cookie?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
    }
    return PlayerRequest("https://www.youtube.com/youtubei/v1/$endpoint?prettyPrint=false", headers, body.toString())
}
