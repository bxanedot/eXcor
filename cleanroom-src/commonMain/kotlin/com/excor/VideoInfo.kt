package com.excor

import com.excor.catalog.ExcorArtwork
import com.excor.catalog.ExcorCatalogProvider
import com.excor.music.ExcorArtistRole
import com.excor.music.ExcorAvailability
import com.excor.music.ExcorMusicArtist
import com.excor.music.ExcorMusicTrack
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * A YouTube video's metadata from the InnerTube player response (`videoDetails`, `microformat`, `playabilityStatus`).
 * YouTube returns it even when it withholds the streams, so [playable] is reported separately; [playabilityReason]
 * is YouTube's own text. Null fields were not in the response.
 */
data class ExcorVideoInfo(
    val videoId: String,
    val title: String,
    val author: String?,
    val channelId: String?,
    val channelHandle: String?,
    val durationSeconds: Long?,
    val viewCount: Long?,
    val likeCount: Long?,
    val description: String?,
    val keywords: List<String>,
    val category: String?,
    val publishDate: String?,
    val uploadDate: String?,
    val thumbnails: List<ExcorArtwork>,
    val isLive: Boolean,
    val isLiveContent: Boolean,
    val isPrivate: Boolean?,
    val isUnlisted: Boolean?,
    val isFamilySafe: Boolean?,
    val isShortsEligible: Boolean?,
    val availableCountries: Set<String>?,
    val playabilityStatus: String?,
    val playabilityReason: String?,
) {
    val playable: Boolean get() = playabilityStatus == "OK"
    val url: String get() = "https://www.youtube.com/watch?v=$videoId"

    /**
     * The video as a music track for [com.excor.music.ExcorMusic.matchAcrossProviders]. Auto-generated "Artist - Topic"
     * channels give the artist directly; otherwise an "Artist - Title" video title is split. The result is a best
     * guess from the title, so matching scores it like any other text candidate (no ISRC).
     */
    fun asMusicTrack(): ExcorMusicTrack {
        val topicArtist = author?.takeIf { it.endsWith(TOPIC_SUFFIX) }?.removeSuffix(TOPIC_SUFFIX)?.trim()
        // "(Official Video)", "[4K Remaster]" and the like describe the video, not the recording.
        val cleaned = VIDEO_TAG.replace(title) { m -> if (VIDEO_WORDS.containsMatchIn(m.value) && !VERSION_WORDS.containsMatchIn(m.value)) " " else m.value }.replace(Regex("\\s+"), " ").trim()
        val dash = Regex("^(.+?)\\s[-–—]\\s(.+)$").find(cleaned)
        val (artist, song) = when {
            topicArtist != null -> topicArtist to cleaned
            dash != null -> dash.groupValues[1].trim() to dash.groupValues[2].trim()
            else -> author to cleaned
        }
        return ExcorMusicTrack(
            provider = ExcorCatalogProvider.YOUTUBE_MUSIC,
            id = videoId,
            title = song,
            url = url,
            artists = listOfNotNull(artist?.let { ExcorMusicArtist(ExcorCatalogProvider.YOUTUBE_MUSIC, channelId.takeIf { topicArtist != null }, it, ExcorArtistRole.PRIMARY) }),
            durationMs = durationSeconds?.times(1_000),
            releaseDate = publishDate?.take(10),
            artwork = thumbnails,
            markets = availableCountries,
            availability = when {
                playable -> ExcorAvailability.AVAILABLE
                playabilityStatus == null -> ExcorAvailability.UNKNOWN
                else -> ExcorAvailability.UNAVAILABLE
            },
        )
    }

    private companion object {
        const val TOPIC_SUFFIX = " - Topic"
        val VIDEO_TAG = Regex("[(\\[][^)\\]]*[)\\]]")
        val VERSION_WORDS = Regex("\\b(live|remix|acoustic|cover|instrumental|karaoke|demo|edit|version)\\b", RegexOption.IGNORE_CASE)
        val VIDEO_WORDS = Regex("\\b(official|video|audio|lyrics?|visuali[sz]er|hd|hq|4k|8k|mv|m/v)\\b", RegexOption.IGNORE_CASE)
    }
}

internal val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")

/** A metadata-only player request: no signature timestamp or streaming token, since no stream is asked for. */
internal fun buildMetadataRequest(videoId: String, session: ExcorSession): PlayerRequest = innertubeRequest("player", session) {
    put("videoId", videoId)
    put("contentCheckOk", true)
    put("racyCheckOk", true)
}

/** Null when the response has no `videoDetails` for [videoId] (removed, invalid, or not a player response). */
internal fun decodeVideoInfo(source: String, videoId: String): ExcorVideoInfo? {
    if (source.length > 2_000_000) return null
    val root = runCatching { Json.parseToJsonElement(source) }.getOrNull() as? JsonObject ?: return null
    val details = root["videoDetails"] as? JsonObject ?: return null
    if (details.text("videoId") != videoId) return null
    val micro = (root["microformat"] as? JsonObject)?.get("playerMicroformatRenderer") as? JsonObject
    val playability = root["playabilityStatus"] as? JsonObject
    val thumbnails = ((details["thumbnail"] as? JsonObject)?.get("thumbnails") as? JsonArray).orEmpty()
        .mapNotNull { it as? JsonObject }
        .mapNotNull { t ->
            val url = t.text("url") ?: return@mapNotNull null
            val parsed = runCatching { Url(url) }.getOrNull() ?: return@mapNotNull null
            val host = parsed.host.lowercase()
            if (parsed.protocol.name != "https" || (host != "i.ytimg.com" && !host.matches(Regex("i[0-9]\\.ytimg\\.com")))) return@mapNotNull null
            ExcorArtwork(url, t.text("width")?.toIntOrNull(), t.text("height")?.toIntOrNull())
        }
    val handle = micro?.text("ownerProfileUrl")?.substringAfterLast('/', "")?.takeIf { it.startsWith("@") }
    return ExcorVideoInfo(
        videoId = videoId,
        title = details.text("title") ?: micro?.text("title") ?: return null,
        author = details.text("author") ?: micro?.text("ownerChannelName"),
        channelId = details.text("channelId") ?: micro?.text("externalChannelId"),
        channelHandle = handle,
        durationSeconds = details.text("lengthSeconds")?.toLongOrNull() ?: micro?.text("lengthSeconds")?.toLongOrNull(),
        viewCount = details.text("viewCount")?.toLongOrNull() ?: micro?.text("viewCount")?.toLongOrNull(),
        likeCount = micro?.text("likeCount")?.toLongOrNull(),
        description = details.text("shortDescription"),
        keywords = (details["keywords"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
        category = micro?.text("category"),
        publishDate = micro?.text("publishDate"),
        uploadDate = micro?.text("uploadDate"),
        thumbnails = thumbnails,
        isLive = details.flag("isLive") == true,
        isLiveContent = details.flag("isLiveContent") == true,
        isPrivate = details.flag("isPrivate"),
        isUnlisted = micro?.flag("isUnlisted"),
        isFamilySafe = micro?.flag("isFamilySafe"),
        isShortsEligible = micro?.flag("isShortsEligible"),
        availableCountries = (micro?.get("availableCountries") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.toSet(),
        playabilityStatus = playability?.text("status"),
        playabilityReason = playability?.text("reason")?.filter { it >= ' ' && it != '\u007f' }?.take(200),
    )
}

private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonObject.flag(key: String): Boolean? = (get(key) as? JsonPrimitive)?.booleanOrNull
