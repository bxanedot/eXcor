package com.excor

import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * An InnerTube request for calls that need neither a player script nor a streaming token (metadata, search, browse):
 * the WEB client on www.youtube.com by default, or YouTube Music's client on [host]. [fields] adds the endpoint's own
 * fields.
 */
internal fun innertubeRequest(
    endpoint: String,
    session: ExcorSession,
    client: ClientIdentity = metadataClient,
    host: String = "www.youtube.com",
    fields: JsonObjectBuilder.() -> Unit,
): PlayerRequest {
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
        put("Origin", "https://$host")
        put("Referer", "https://$host/")
        put("User-Agent", client.userAgent)
        put("X-YouTube-Client-Name", client.clientId.toString())
        put("X-YouTube-Client-Version", client.clientVersion)
        session.visitorData?.takeIf(String::isNotBlank)?.let { put("X-Goog-Visitor-Id", it) }
        session.cookie?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
        putAll(signedInHeaders(session.cookie, "https://$host"))
    }
    return PlayerRequest("https://$host/youtubei/v1/$endpoint?prettyPrint=false", headers, body.toString())
}
