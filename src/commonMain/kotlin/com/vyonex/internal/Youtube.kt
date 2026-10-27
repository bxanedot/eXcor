package com.vyonex.internal

import com.vyonex.VyonExAudioFormat
import com.vyonex.VyonExCaptionTrack
import com.vyonex.VyonExLiveStatus
import com.vyonex.VyonExMetadata
import com.vyonex.VyonExSession
import com.vyonex.VyonExThumbnail
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter
import io.ktor.http.parseQueryString
import io.ktor.util.sha1
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

internal const val YT_ORIGIN = "https://www.youtube.com"
internal const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

internal class Identity(
    val profileId: String,
    val clientName: String,
    val clientId: Int,
    val clientVersion: String,
    val userAgent: String,
    val device: Map<String, String> = emptyMap(),
    val needsPlayerJs: Boolean,
    val supportsCookies: Boolean,
    val needsPoToken: Boolean,
)

// yt-dlp 2026.10.21 defaults to tv_samsung, visionos, web (values from its INNERTUBE_CLIENTS).
// Live on 2026-10-26: TVHTML5 gave ciphered URLs that play once decoded, VISIONOS gave direct URLs,
// WEB gave SABR-only formats without a PO token. VISIONOS does not take cookies, so like yt-dlp
// it is skipped for logged-in sessions.
internal val IDENTITIES = listOf(
    Identity(
        "TV_SAMSUNG", "TVHTML5", 7, "5.20260707",
        "Mozilla/5.0 (SMART-TV; Linux; Tizen 2.4.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/2.4.0 TV Safari/538.1",
        mapOf("deviceMake" to "Samsung", "deviceModel" to "UKS9800", "osName" to "Tizen", "osVersion" to "2.4.0"),
        needsPlayerJs = true, supportsCookies = true, needsPoToken = false,
    ),
    Identity(
        "VISIONOS", "VISIONOS", 101, "1.02",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
        mapOf("deviceMake" to "Apple", "deviceModel" to "RealityDevice17,1", "osName" to "visionOS", "osVersion" to "26.5.23O471"),
        needsPlayerJs = false, supportsCookies = false, needsPoToken = false,
    ),
    Identity("WEB", "WEB", 1, "2.20260708.00.00", DESKTOP_UA, needsPlayerJs = true, supportsCookies = true, needsPoToken = true),
)

internal fun playerRequestBody(
    id: Identity,
    videoId: String,
    session: VyonExSession,
    visitorData: String?,
    sts: Int?,
    poToken: String?,
): String = buildJsonObject {
    putJsonObject("context") {
        putJsonObject("client") {
            put("clientName", id.clientName)
            put("clientVersion", id.clientVersion)
            put("userAgent", id.userAgent)
            id.device.forEach { (key, value) -> put(key, value) }
            put("hl", session.language)
            put("gl", session.region)
            if (visitorData != null) put("visitorData", visitorData)
            put("timeZone", "UTC")
            put("utcOffsetMinutes", 0)
        }
    }
    put("videoId", videoId)
    putJsonObject("playbackContext") {
        putJsonObject("contentPlaybackContext") {
            put("html5Preference", "HTML5_PREF_WANTS")
            if (sts != null) put("signatureTimestamp", sts)
        }
    }
    if (poToken != null) putJsonObject("serviceIntegrityDimensions") { put("poToken", poToken) }
    put("contentCheckOk", true)
    put("racyCheckOk", true)
}.toString()

internal fun cookieValue(cookie: String?, name: String): String? =
    cookie?.split(';')?.firstNotNullOfOrNull { part ->
        part.trim().split('=', limit = 2).takeIf { it.size == 2 && it[0] == name }?.get(1)
    }

// Same test as yt-dlp: LOGIN_INFO plus one of the SAPISID cookies.
internal fun isLoggedIn(cookie: String?) =
    cookieValue(cookie, "LOGIN_INFO") != null &&
        listOf("SAPISID", "__Secure-1PAPISID", "__Secure-3PAPISID").any { cookieValue(cookie, it) != null }

// Port of yt-dlp's _get_sid_authorization_header: "<scheme> <ts>_<sha1("<ts> <sid> <origin>")>".
internal fun sidAuthorization(cookie: String, nowMs: Long): String? {
    val ts = nowMs / 1000
    return listOf(
        "SAPISIDHASH" to (cookieValue(cookie, "SAPISID") ?: cookieValue(cookie, "__Secure-3PAPISID")),
        "SAPISID1PHASH" to cookieValue(cookie, "__Secure-1PAPISID"),
        "SAPISID3PHASH" to cookieValue(cookie, "__Secure-3PAPISID"),
    ).mapNotNull { (scheme, sid) ->
        sid?.let { "$scheme ${ts}_${sha1("$ts $it $YT_ORIGIN".encodeToByteArray()).toHexString()}" }
    }.joinToString(" ").ifEmpty { null }
}

internal fun JsonElement?.at(vararg keys: String): JsonElement? = keys.fold(this) { e, key -> (e as? JsonObject)?.get(key) }
internal val JsonElement?.str get() = (this as? JsonPrimitive)?.contentOrNull
private val JsonElement?.int get() = str?.toIntOrNull()
private val JsonElement?.bool get() = (this as? JsonPrimitive)?.booleanOrNull

internal class AudioFormat(json: JsonObject, val url: String) {
    val itag = json["itag"].int
    val mimeType = json["mimeType"].str.orEmpty()
    val bitrate = json["bitrate"].int ?: 0
    val averageBitrate = json["averageBitrate"].int
    val channels = json["audioChannels"].int
    val sampleRate = json["audioSampleRate"].int
    val loudnessDb = (json["loudnessDb"] as? JsonPrimitive)?.doubleOrNull
}

internal fun audioFormatsOf(playerResponse: JsonObject) = formatsOf(playerResponse, "audio/")

internal fun videoFormatsOf(playerResponse: JsonObject) = formatsOf(playerResponse, "video/")

private fun formatsOf(playerResponse: JsonObject, mimePrefix: String): List<JsonObject> {
    val data = playerResponse["streamingData"] as? JsonObject ?: return emptyList()
    return listOf("formats", "adaptiveFormats").flatMap { (data[it] as? JsonArray).orEmpty() }
        .filterIsInstance<JsonObject>()
        .filter {
            // Live, OTF and DRM formats cannot be fetched directly with byte ranges.
            it["mimeType"].str.orEmpty().startsWith(mimePrefix) && it["targetDurationSec"] == null &&
                it["type"].str != "FORMAT_STREAM_TYPE_OTF" && it["drmFamilies"] == null
        }
}

internal class VideoFormat(json: JsonObject, val url: String) {
    val itag = json["itag"].int
    val mimeType = json["mimeType"].str.orEmpty()
    val width = json["width"].int ?: 0
    val height = json["height"].int ?: 0
    val fps = json["fps"].int ?: 0
    val bitrate = json["bitrate"].int ?: 0
    // Combined audio+video formats list both codecs, e.g. codecs="avc1.42001E, mp4a.40.2".
    val muxed = ',' in mimeType
}

// H.264 first because every player decodes it (AVPlayer included), then VP9, then AV1.
private val VideoFormat.codecRank get() = when {
    "avc1" in mimeType -> 3
    "vp9" in mimeType || "vp09" in mimeType -> 2
    "av01" in mimeType -> 1
    else -> 0
}

/** The tallest format up to [maxHeight] (the smallest one if none fits), then fps, codec, bitrate. */
internal fun selectVideo(formats: List<VideoFormat>, maxHeight: Int, requireAvc: Boolean): VideoFormat? {
    val pool = if (requireAvc) formats.filter { "avc1" in it.mimeType } else formats
    val smallest = pool.minOfOrNull { it.height }
    val fitting = pool.filter { it.height <= maxHeight }.ifEmpty { pool.filter { it.height == smallest } }
    return fitting.maxWithOrNull(compareBy({ it.height }, { it.fps }, { it.codecRank }, { it.bitrate }))
}

internal fun metadataOf(videoId: String, playerResponse: JsonObject): VyonExMetadata {
    val details = playerResponse["videoDetails"]
    val micro = playerResponse.at("microformat", "playerMicroformatRenderer")
    fun text(e: JsonElement?) =
        e.str ?: e.at("simpleText").str ?: (e.at("runs") as? JsonArray)?.joinToString("") { it.at("text").str.orEmpty() }
    fun strings(e: JsonElement?) = (e as? JsonArray).orEmpty().mapNotNull { it.str }
    fun thumbnails(e: JsonElement?) = (e.at("thumbnail", "thumbnails") as? JsonArray).orEmpty().mapNotNull { t ->
        t.at("url").str?.let { VyonExThumbnail(it, t.at("width").int ?: 0, t.at("height").int ?: 0) }
    }
    // yt-dlp _list_formats: isLiveContent alone is also true for past and upcoming streams.
    val liveNow = details.at("isLive").bool ?: micro.at("liveBroadcastDetails", "isLiveNow").bool
    val liveContent = details.at("isLiveContent").bool
    val liveStatus = when {
        details.at("isPostLiveDvr").bool == true -> VyonExLiveStatus.POST_LIVE
        liveNow == true -> VyonExLiveStatus.LIVE
        details.at("isUpcoming").bool == true -> VyonExLiveStatus.UPCOMING
        liveContent == true -> VyonExLiveStatus.WAS_LIVE
        liveNow == false || liveContent == false -> VyonExLiveStatus.NOT_LIVE
        else -> null
    }
    return VyonExMetadata(
        videoId = videoId,
        title = details.at("title").str ?: text(micro.at("title")),
        author = details.at("author").str ?: micro.at("ownerChannelName").str,
        channelId = details.at("channelId").str ?: micro.at("externalChannelId").str,
        channelUrl = micro.at("ownerProfileUrl").str,
        // Zero means unknown, as in yt-dlp; live and upcoming streams report time on air or zero.
        durationSeconds = (details.at("lengthSeconds") ?: micro.at("lengthSeconds")).str?.toLongOrNull()
            ?.takeIf { it > 0 && liveStatus != VyonExLiveStatus.LIVE && liveStatus != VyonExLiveStatus.UPCOMING },
        viewCount = (details.at("viewCount") ?: micro.at("viewCount")).str?.toLongOrNull(),
        likeCount = micro.at("likeCount").str?.toLongOrNull(),
        description = details.at("shortDescription").str ?: text(micro.at("description")),
        keywords = strings(details.at("keywords")),
        category = micro.at("category").str,
        publishDate = micro.at("publishDate").str,
        uploadDate = micro.at("uploadDate").str,
        thumbnails = (thumbnails(details) + thumbnails(micro)).distinctBy { it.url },
        isLive = liveStatus?.let { it == VyonExLiveStatus.LIVE },
        isPrivate = details.at("isPrivate").bool,
        isUnlisted = micro.at("isUnlisted").bool,
        isFamilySafe = micro.at("isFamilySafe").bool,
        isShortsEligible = micro.at("isShortsEligible").bool,
        allowRatings = details.at("allowRatings").bool,
        availableCountries = strings(micro.at("availableCountries")),
        canonicalUrl = micro.at("canonicalUrl").str,
        captions = (playerResponse.at("captions", "playerCaptionsTracklistRenderer", "captionTracks") as? JsonArray).orEmpty()
            .mapNotNull { track ->
                val url = track.at("baseUrl").str?.takeIf { it.startsWith("$YT_ORIGIN/api/timedtext?") }
                val language = track.at("languageCode").str
                // yt-dlp: an `exp` of xpe or xpv means the track needs a PO token, which is not attached here.
                val needsToken = url != null && parseQueryString(url.substringAfter('?')).getAll("exp").orEmpty()
                    .any { it == "xpe" || it == "xpv" }
                if (url == null || language == null || needsToken) null
                else VyonExCaptionTrack(url, language, text(track.at("name")), track.at("kind").str == "asr")
            },
        liveStatus = liveStatus,
    )
}

private val N_PARAM = Regex("([?&]n=)([^&]*)")

/** The format's URL with signature and `n` decoded, or null when it has none (SABR-only). */
internal suspend fun playableUrl(format: JsonObject, solve: suspend (type: String, challenge: String) -> String): String? {
    var url = format["url"].str ?: run {
        val cipher = parseQueryString(format["signatureCipher"].str ?: return null)
        val base = cipher["url"] ?: return null
        val s = cipher["s"] ?: return null
        "$base&${cipher["sp"] ?: "signature"}=${solve("sig", s).encodeURLParameter()}"
    }
    N_PARAM.find(url)?.let { m ->
        val n = solve("n", m.groupValues[2].decodeURLQueryComponent())
        url = url.replaceRange(m.range, m.groupValues[1] + n.encodeURLParameter())
    }
    return url
}

// Checked on the raw string, so the host's URL parser cannot read a different host than we did.
private val ALLOWED_URL = Regex("""https://(?:[a-z0-9-]+\.)*(?:googlevideo|youtube)\.com(?::443)?/videoplayback(?:\?[^#\s\\]*)?""")

internal fun isAllowedUrl(url: String) = ALLOWED_URL.matches(url)

private val AudioFormat.codecRank get() = when {
    mimeType.startsWith("audio/webm") -> 2
    mimeType.startsWith("audio/mp4") -> 1
    else -> 0
}

private val AUTO_ORDER = compareBy<AudioFormat>(
    { it.codecRank },
    { it.bitrate + if (it.channels == 2) 50_000 else if (it.channels == null) 25_000 else 0 },
    { minOf(it.sampleRate ?: 0, 48_000) },
)

internal fun selectFormat(formats: List<AudioFormat>, maxKbps: Int, requireM4a: Boolean): AudioFormat? {
    val mp4 = formats.filter { it.mimeType.startsWith("audio/mp4") }
    return when {
        requireM4a -> mp4.maxWithOrNull(AUTO_ORDER)
        maxKbps <= 64 -> mp4.minByOrNull { it.bitrate } ?: formats.minByOrNull { it.bitrate }
        // Above 64 kbps maxKbps is deliberately not a cap.
        else -> formats.filter { it.mimeType.startsWith("audio/webm") }.ifEmpty { formats }.maxWithOrNull(AUTO_ORDER)
    }
}

internal fun audioFormatOf(mimeType: String) = when {
    mimeType.startsWith("audio/mp4") -> VyonExAudioFormat.M4A
    mimeType.startsWith("audio/webm") && "opus" in mimeType -> VyonExAudioFormat.WEBM_OPUS
    else -> VyonExAudioFormat.OTHER
}

/** Player version from a page or the iframe API, which escapes its slashes. */
internal fun playerIdOf(text: String) = Regex("""player\\?/([0-9a-fA-F]{8,})\\?/""").find(text)?.groupValues?.get(1)

internal class WatchPage(val playerResponse: JsonObject, val playerId: String?)

internal fun parseWatchPage(html: String): WatchPage? {
    val start = Regex("""ytInitialPlayerResponse\s*=\s*\{""").find(html) ?: return null
    val json = balancedObject(html, start.range.last) ?: return null
    return WatchPage(Json.parseToJsonElement(json) as? JsonObject ?: return null, playerIdOf(html))
}

private fun balancedObject(s: String, open: Int): String? {
    var depth = 0
    var inString = false
    var i = open
    while (i < s.length) {
        val c = s[i]
        if (inString) {
            if (c == '\\') i++ else if (c == '"') inString = false
        } else when (c) {
            '"' -> inString = true
            '{' -> depth++
            '}' -> if (--depth == 0) return s.substring(open, i + 1)
        }
        i++
    }
    return null
}
