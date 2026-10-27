package com.vyonex.internal

import com.vyonex.VyonExAudioFormat
import com.vyonex.VyonExCaptionTrack
import com.vyonex.VyonExChapter
import com.vyonex.VyonExContainer
import com.vyonex.VyonExFormat
import com.vyonex.VyonExLiveStatus
import com.vyonex.VyonExManifestKind
import com.vyonex.VyonExMetadata
import com.vyonex.VyonExMergePair
import com.vyonex.VyonExMusicInfo
import com.vyonex.VyonExSession
import com.vyonex.VyonExThumbnail
import com.vyonex.VyonExUnplayableException
import com.vyonex.VyonExUnplayableReason
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
        id.embedUrl?.let { url -> putJsonObject("thirdParty") { put("embedUrl", url) } }
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
internal val JsonElement?.bool get() = (this as? JsonPrimitive)?.booleanOrNull

/** YouTube's text fields: a plain string, `simpleText`, or `runs` of pieces. */
internal fun textOf(e: JsonElement?) =
    e.str ?: e.at("simpleText").str ?: (e.at("runs") as? JsonArray)?.joinToString("") { it.at("text").str.orEmpty() }

// yt-dlp _is_agegated: one of these in the status or reason, or a desktopLegacyAgeGateReason.
private val AGE_GATE = listOf("confirm your age", "age-restricted", "inappropriate", "age_verification_required", "age_check_required")
private const val NOT_IN_COUNTRY = "The uploader has not made this video available in your country"

/** Why [playerResponse] refuses playback, sorted the way yt-dlp reads it; null when it plays. */
internal fun unplayableOf(videoId: String, playerResponse: JsonObject): VyonExUnplayableException? {
    val playability = playerResponse["playabilityStatus"]
    val status = playability.at("status").str
    if (status == "OK") return null
    val error = playability.at("errorScreen", "playerErrorMessageRenderer")
    val reason = playability.at("reason").str ?: textOf(error.at("reason"))
    val subreason = textOf(error.at("subreason"))
    val kind = when {
        (playability.at("desktopLegacyAgeGateReason").int ?: 0) != 0 ||
            listOfNotNull(status, reason).any { s -> AGE_GATE.any { it in s.lowercase() } } -> VyonExUnplayableReason.AGE_RESTRICTED
        subreason?.startsWith(NOT_IN_COUNTRY) == true -> VyonExUnplayableReason.GEO_RESTRICTED
        status == "LIVE_STREAM_OFFLINE" -> VyonExUnplayableReason.UPCOMING
        status == "ERROR" -> VyonExUnplayableReason.UNAVAILABLE
        reason?.contains("sign in", ignoreCase = true) == true -> VyonExUnplayableReason.SIGN_IN_REQUIRED
        else -> VyonExUnplayableReason.OTHER
    }
    // yt-dlp adds the subreason when it says more, as it does for country blocks.
    val message = if (subreason != null && subreason != reason) listOfNotNull(reason, subreason).joinToString(". ") else reason
    return VyonExUnplayableException(videoId, kind, status, message)
}

internal class AudioFormat(json: JsonObject, val url: String) {
    val itag = json["itag"].int
    val mimeType = json["mimeType"].str.orEmpty()
    val bitrate = json["bitrate"].int ?: 0
    val averageBitrate = json["averageBitrate"].int
    val channels = json["audioChannels"].int
    val sampleRate = json["audioSampleRate"].int
    val loudnessDb = (json["loudnessDb"] as? JsonPrimitive)?.doubleOrNull
}

/**
 * The audio formats, of one language: a video with dubbed tracks lists each format once per track, and only the
 * best-ranked track's are kept, so every choice made from them plays the original.
 */
internal fun audioFormatsOf(playerResponse: JsonObject): List<JsonObject> {
    val formats = formatsOf(playerResponse, "audio/")
    val best = formats.maxOfOrNull(::audioTrackRank) ?: return formats
    return formats.filter { audioTrackRank(it) == best }
}

// yt-dlp get_language_code_and_preference: descriptive tracks last, then the original, then YouTube's default.
// The display name is in the session's language, so "original" may not match; the default then decides.
private fun audioTrackRank(format: JsonObject): Int {
    val track = format["audioTrack"] ?: return -1
    val name = track.at("displayName").str.orEmpty().lowercase()
    return when {
        "descriptive" in name -> -10
        "original" in name -> 10
        track.at("audioIsDefault").bool == true -> 5
        else -> -1
    }
}

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

internal fun formatOf(json: JsonObject, url: String): VyonExFormat {
    val mimeType = json["mimeType"].str.orEmpty()
    val video = mimeType.startsWith("video/")
    return VyonExFormat(
        itag = json["itag"].int ?: 0,
        url = url,
        mimeType = mimeType,
        kbps = (json["bitrate"].int ?: 0) / 1000,
        contentLength = json["contentLength"].str?.toLongOrNull(),
        // A video format lists two codecs when it carries audio too.
        hasAudio = !video || ',' in mimeType,
        hasVideo = video,
        width = json["width"].int,
        height = json["height"].int,
        fps = json["fps"].int,
    )
}

/**
 * The tallest video-only format up to [maxHeight] (then fps, then bitrate) with the best audio of the same family,
 * so the two merge into one [container] file without re-encoding; null if the video has no such pair.
 */
internal fun mergePairOf(formats: List<VyonExFormat>, maxHeight: Int, container: VyonExContainer): VyonExMergePair? {
    val (videoCodec, audioMime) = when (container) {
        VyonExContainer.WEBM -> "vp9" to "audio/webm"
        VyonExContainer.MP4 -> "avc1" to "audio/mp4"
    }
    val video = formats.filter { it.hasVideo && !it.hasAudio && videoCodec in it.mimeType && (it.height ?: 0) <= maxHeight }
        .maxWithOrNull(compareBy({ it.height ?: 0 }, { it.fps ?: 0 }, { it.kbps })) ?: return null
    val audio = formats.filter { !it.hasVideo && it.mimeType.startsWith(audioMime) }.maxByOrNull { it.kbps } ?: return null
    return VyonExMergePair(video, audio, container)
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
        title = details.at("title").str ?: textOf(micro.at("title")),
        author = details.at("author").str ?: micro.at("ownerChannelName").str,
        channelId = details.at("channelId").str ?: micro.at("externalChannelId").str,
        channelUrl = micro.at("ownerProfileUrl").str,
        // Zero means unknown, as in yt-dlp; live and upcoming streams report time on air or zero.
        durationSeconds = (details.at("lengthSeconds") ?: micro.at("lengthSeconds")).str?.toLongOrNull()
            ?.takeIf { it > 0 && liveStatus != VyonExLiveStatus.LIVE && liveStatus != VyonExLiveStatus.UPCOMING },
        viewCount = (details.at("viewCount") ?: micro.at("viewCount")).str?.toLongOrNull(),
        likeCount = micro.at("likeCount").str?.toLongOrNull(),
        description = details.at("shortDescription").str ?: textOf(micro.at("description")),
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
                else VyonExCaptionTrack(url, language, textOf(track.at("name")), track.at("kind").str == "asr", track.at("isTranslatable").bool == true)
            },
        liveStatus = liveStatus,
        music = (details.at("shortDescription").str ?: textOf(micro.at("description")))?.let(::musicInfoOf),
        translationLanguages = (playerResponse.at("captions", "playerCaptionsTracklistRenderer", "translationLanguages") as? JsonArray).orEmpty()
            .mapNotNull { language -> language.at("languageCode").str?.let { code -> textOf(language.at("languageName"))?.let { code to it } } }.toMap(),
    )
}

// yt-dlp _video.py _real_extract: the layout of YouTube Music's auto-generated descriptions.
private val AUTO_GENERATED = Regex(
    """(?:\n|^)([^\n·]+) · ([^\n]+)\n+([^\n]+)\n+(?:℗\s*(\d{4}))?(?:[\s\S]+?\nReleased on\s*:\s*(\d{4}-\d{2}-\d{2}))?""" +
        """(?:[\s\S]+?\nArtist\s*:\s*([^\n]+)\n)?[\s\S]+\nAuto-generated by YouTube\.\s*$""",
)

internal fun musicInfoOf(description: String): VyonExMusicInfo? {
    if (!description.trim().endsWith("\nAuto-generated by YouTube.")) return null
    val m = AUTO_GENERATED.find(description) ?: return null
    val (track, artist, album, year, date, cleanArtist) = m.destructured
    return VyonExMusicInfo(
        track = track.trim(),
        // A separate "Artist:" line names the artist whole; otherwise the title line lists them.
        artists = if (cleanArtist.isNotEmpty()) listOf(cleanArtist) else artist.split(" · ").map { it.trim() },
        album = album.trim(),
        releaseYear = year.toIntOrNull() ?: date.take(4).toIntOrNull(),
        releaseDate = date.ifEmpty { null },
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

private val ALLOWED_MANIFEST = Regex("""https://(?:[a-z0-9-]+\.)*googlevideo\.com(?::443)?/api/manifest/[^?#\s\\]*(?:\?[^#\s\\]*)?""")

internal fun isAllowedManifestUrl(url: String) = ALLOWED_MANIFEST.matches(url)

// yt-dlp _extract_formats_and_subtitles: the n challenge is a path segment, and HLS keeps its file suffix last.
private val MANIFEST_N = Regex("""/n/([^/]+)/""")
private val HLS_SUFFIX = Regex("""/(?:file|playlist)/index\.m3u8$""")

/** [url] with its n challenge solved and, when given, [poToken] added as a `/pot/` segment (before HLS's file name). */
internal suspend fun manifestUrlOf(url: String, poToken: String?, solveN: suspend (String) -> String): String {
    val query = url.substringAfter('?', "").let { if (it.isEmpty()) "" else "?$it" }
    var path = url.substringBefore('?')
    MANIFEST_N.find(path)?.let { m -> path = path.replaceRange(m.groups[1]!!.range, solveN(m.groupValues[1])) }
    if (poToken != null) {
        val suffix = HLS_SUFFIX.find(path)?.value.orEmpty()
        path = path.removeSuffix(suffix).trimEnd('/') + "/pot/$poToken" + suffix
    }
    return path + query
}

/** Whether the start of an answer is a manifest of [kind]: an M3U8 playlist or an MPD document. */
internal fun isManifest(kind: VyonExManifestKind, start: String): Boolean {
    val text = start.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
    return when (kind) {
        VyonExManifestKind.HLS -> text.startsWith("#EXTM3U")
        VyonExManifestKind.DASH -> "<MPD" in text
    }
}

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

// yt-dlp _extract_chapters_from_description: a time then a title on a line, or a title then a time.
private const val TIME = """(?:\d+:)?\d{1,2}:\d{2}"""
private val TIME_FIRST = Regex("""(?m)^\s*($TIME)\b\W*\s(.+?)\s*$""")
private val TIME_LAST = Regex("""(?m)^\s*(.+?)\b\W*\s($TIME)\s*$""")

/**
 * yt-dlp _extract_chapters_helper: chapters that start in order, from 0 up to [duration], each ending where the next
 * starts. [sort] for chapters read from a description, where yt-dlp sorts them first. Nothing without a duration.
 */
internal fun chaptersFrom(starts: List<Pair<Double?, String?>>, duration: Double?, sort: Boolean): List<VyonExChapter> {
    if (duration == null || duration <= 0) return emptyList()
    val kept = mutableListOf<Pair<Double, String>>()
    for ((start, title) in if (sort) starts.sortedBy { it.first ?: 0.0 } else starts) {
        if (start != null && start >= (kept.lastOrNull()?.first ?: 0.0) && start <= duration) kept += start to title.orEmpty()
    }
    return kept.mapIndexed { i, (start, title) -> VyonExChapter(title, start, kept.getOrNull(i + 1)?.first ?: duration) }
}

/** The chapters a watch-next response shows: the player bar's, else the "chapters" panel's (yt-dlp's two places). */
internal fun chaptersOfNext(next: JsonElement?, duration: Double?): List<VyonExChapter> {
    val bar = next.at("playerOverlays", "playerOverlayRenderer", "decoratedPlayerBarRenderer", "decoratedPlayerBarRenderer", "playerBar",
        "chapteredPlayerBarRenderer", "chapters") as? JsonArray
    chaptersFrom(bar.orEmpty().map { chapter ->
        val renderer = chapter.at("chapterRenderer")
        renderer.at("timeRangeStartMillis").str?.toDoubleOrNull()?.div(1000) to textOf(renderer.at("title"))
    }, duration, sort = false).takeIf { it.isNotEmpty() }?.let { return it }
    val panels = (next.at("engagementPanels") as? JsonArray).orEmpty()
        .mapNotNull { it.at("engagementPanelSectionListRenderer", "content", "macroMarkersListRenderer", "contents") as? JsonArray }
    return panels.firstNotNullOfOrNull { contents ->
        chaptersFrom(contents.mapNotNull { it.at("macroMarkersListItemRenderer").takeIf { r -> r != null } }.map { marker ->
            durationOf(textOf(marker.at("timeDescription")))?.toDouble() to textOf(marker.at("title"))
        }, duration, sort = false).takeIf { it.isNotEmpty() }
    }.orEmpty()
}

/** Chapters written as timestamps in a description. */
internal fun chaptersOfDescription(description: String?, duration: Double?): List<VyonExChapter> {
    val text = description.orEmpty()
    return chaptersFrom(TIME_FIRST.findAll(text).map { durationOf(it.groupValues[1])?.toDouble() to it.groupValues[2] }.toList(), duration, sort = true)
        .ifEmpty { chaptersFrom(TIME_LAST.findAll(text).map { durationOf(it.groupValues[2])?.toDouble() to it.groupValues[1] }.toList(), duration, sort = true) }
}
