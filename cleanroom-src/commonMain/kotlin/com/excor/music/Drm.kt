package com.excor.music

import com.excor.catalog.ExcorCatalogProvider

/**
 * Content-protection systems, identified by their DASH-IF registered system IDs or HLS key formats.
 * [PROVIDER_MANAGED] means a service states the audio is protected and plays only in its own client or SDK, without
 * publishing which system it uses; eXcor does not guess one.
 */
enum class ExcorDrmSystem(val systemId: String?) {
    WIDEVINE("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"),
    PLAYREADY("9a04f079-9840-4286-ab92-e65be0885f95"),
    FAIRPLAY("94ce86fb-07ff-4f43-adb8-93d2fa968ca2"),
    CLEARKEY("e2719d58-a985-b3c9-781a-b030af78d30e"),
    PROVIDER_MANAGED(null),
    UNKNOWN(null),
}

/** ISO/IEC 23001-7 schemes, and the HLS methods that are not part of it. */
enum class ExcorEncryptionScheme { CENC, CBCS, CENS, CBC1, HLS_AES_128, HLS_SAMPLE_AES, HLS_SAMPLE_AES_CTR, UNKNOWN }

/**
 * What a manifest or provider says about protection. Key and license URIs are never kept: only whether the manifest
 * names one ([keyUriPresent]), since they are often signed or tied to an account.
 */
data class ExcorProtectionInfo(
    val protected: Boolean,
    val systems: Set<ExcorDrmSystem> = emptySet(),
    val schemes: Set<ExcorEncryptionScheme> = emptySet(),
    val keyUriPresent: Boolean = false,
    val source: ExcorProtectionSource,
) {
    companion object {
        val UNPROTECTED_MANIFEST = ExcorProtectionInfo(protected = false, source = ExcorProtectionSource.MANIFEST)
    }
}

enum class ExcorProtectionSource { MANIFEST, PROVIDER_STATEMENT }

/**
 * Reads `EXT-X-KEY` and `EXT-X-SESSION-KEY` tags (RFC 8216bis). AES-128 is whole-segment encryption with a key the
 * player fetches, not DRM; it is still reported as protected, so nothing calls such a stream plain.
 */
fun excorHlsProtectionOf(playlist: String): ExcorProtectionInfo {
    val systems = mutableSetOf<ExcorDrmSystem>()
    val schemes = mutableSetOf<ExcorEncryptionScheme>()
    var keyUri = false
    for (line in playlist.lineSequence()) {
        val tag = line.trim()
        val attributes = when {
            tag.startsWith("#EXT-X-KEY:") -> tag.removePrefix("#EXT-X-KEY:")
            tag.startsWith("#EXT-X-SESSION-KEY:") -> tag.removePrefix("#EXT-X-SESSION-KEY:")
            else -> continue
        }.let(::hlsAttributes)
        val method = attributes["METHOD"]?.uppercase() ?: continue
        if (method == "NONE") continue
        schemes += when (method) {
            "AES-128" -> ExcorEncryptionScheme.HLS_AES_128
            "SAMPLE-AES" -> ExcorEncryptionScheme.HLS_SAMPLE_AES
            "SAMPLE-AES-CTR" -> ExcorEncryptionScheme.HLS_SAMPLE_AES_CTR
            "SAMPLE-AES-CENC" -> ExcorEncryptionScheme.CENC
            else -> ExcorEncryptionScheme.UNKNOWN
        }
        if (attributes["URI"] != null) keyUri = true
        val format = attributes["KEYFORMAT"]?.lowercase()
        when {
            format == null || format == "identity" -> if (method != "AES-128") systems += ExcorDrmSystem.UNKNOWN
            format == "com.apple.streamingkeydelivery" -> systems += ExcorDrmSystem.FAIRPLAY
            format == "com.microsoft.playready" -> systems += ExcorDrmSystem.PLAYREADY
            format == "org.w3.clearkey" -> systems += ExcorDrmSystem.CLEARKEY
            format.startsWith("urn:uuid:") -> systems += systemOf(format.removePrefix("urn:uuid:"))
            else -> systems += ExcorDrmSystem.UNKNOWN
        }
    }
    if (schemes.isEmpty()) return ExcorProtectionInfo.UNPROTECTED_MANIFEST
    return ExcorProtectionInfo(true, systems, schemes, keyUri, ExcorProtectionSource.MANIFEST)
}

/** Reads `ContentProtection` descriptors of a DASH MPD (ISO/IEC 23009-1, DASH-IF IOP). */
fun excorDashProtectionOf(mpd: String): ExcorProtectionInfo {
    val systems = mutableSetOf<ExcorDrmSystem>()
    val schemes = mutableSetOf<ExcorEncryptionScheme>()
    var licenseUri = false
    for (match in CONTENT_PROTECTION.findAll(mpd)) {
        val element = match.value
        val attributes = XML_ATTRIBUTE.findAll(element).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        val scheme = attributes["schemeiduri"]?.lowercase() ?: continue
        when {
            scheme == "urn:mpeg:dash:mp4protection:2011" -> schemes += when (attributes["value"]?.lowercase()) {
                "cenc" -> ExcorEncryptionScheme.CENC
                "cbcs" -> ExcorEncryptionScheme.CBCS
                "cens" -> ExcorEncryptionScheme.CENS
                "cbc1" -> ExcorEncryptionScheme.CBC1
                else -> ExcorEncryptionScheme.UNKNOWN
            }
            scheme.startsWith("urn:uuid:") -> systems += systemOf(scheme.removePrefix("urn:uuid:"))
        }
        if ("laurl" in element.lowercase() || "licenseacquisitionurl" in element.lowercase()) licenseUri = true
    }
    if (systems.isEmpty() && schemes.isEmpty()) return ExcorProtectionInfo.UNPROTECTED_MANIFEST
    return ExcorProtectionInfo(true, systems, schemes, licenseUri, ExcorProtectionSource.MANIFEST)
}

private fun systemOf(uuid: String): ExcorDrmSystem =
    ExcorDrmSystem.entries.firstOrNull { it.systemId != null && it.systemId == uuid.lowercase() } ?: ExcorDrmSystem.UNKNOWN

// Self-closing or with children; the descriptor's own children (cenc:pssh, laurl) are inside the match.
// `[\s\S]` rather than DOT_MATCHES_ALL, which common Kotlin does not have (only the platforms do).
private val CONTENT_PROTECTION = Regex("<(?:\\w+:)?ContentProtection\\b[^>]*?(?:/>|>[\\s\\S]*?</(?:\\w+:)?ContentProtection>)")
private val XML_ATTRIBUTE = Regex("([\\w:]+)\\s*=\\s*\"([^\"]*)\"")

/** HLS attribute lists: NAME=value pairs, values optionally quoted and holding commas. */
internal fun hlsAttributes(list: String): Map<String, String> {
    val out = linkedMapOf<String, String>()
    var i = 0
    while (i < list.length) {
        val eq = list.indexOf('=', i).takeIf { it >= 0 } ?: break
        val name = list.substring(i, eq).trim().trimStart(',').trim()
        var j = eq + 1
        val value: String
        if (j < list.length && list[j] == '"') {
            val end = list.indexOf('"', j + 1).takeIf { it >= 0 } ?: list.length
            value = list.substring(j + 1, end)
            j = end + 1
        } else {
            val end = list.indexOf(',', j).takeIf { it >= 0 } ?: list.length
            value = list.substring(j, end)
            j = end
        }
        out[name.uppercase()] = value
        i = j + 1
    }
    return out
}

/** How a track can be played, from best to worst. Never a success when eXcor cannot deliver playable audio. */
enum class ExcorPlaybackStatus {
    UNPROTECTED_PLAYABLE,
    PROTECTED_AUTHORIZED_ADAPTER,
    PROTECTED_REQUIRES_CLIENT,
    SUBSCRIPTION_REQUIRED,
    REGION_RESTRICTED,
    DRM_UNSUPPORTED,
    UNAVAILABLE,
}

/** What the host platform can do; eXcor does not detect a CDM or SDK itself. */
data class ExcorPlatformCapabilities(
    val drmSystems: Set<ExcorDrmSystem> = emptySet(),
    val secureDecoder: Boolean = false,
)

/**
 * A host-supplied player that handles protected content entirely inside a licensed SDK or CDM: the Spotify app
 * remote or Web Playback SDK, MusicKit, a Media3 DRM session. eXcor only decides whether to hand a track to it; keys,
 * licenses and decrypted media never pass through eXcor.
 */
interface ExcorProtectedPlaybackAdapter {
    val provider: ExcorCatalogProvider
    /** True only when the adapter's SDK is present and signed in on this device. */
    suspend fun isReady(): Boolean
    /** Whether this account may play [track] here; null when the SDK cannot tell before trying. */
    suspend fun isEntitled(track: ExcorMusicTrack): Boolean?
    /** Starts playback inside the SDK. Errors are the SDK's own. */
    @Throws(Throwable::class)
    suspend fun play(track: ExcorMusicTrack)
}

data class ExcorPlaybackOption(
    val status: ExcorPlaybackStatus,
    val adapter: ExcorProtectedPlaybackAdapter? = null,
    val protection: ExcorProtectionInfo? = null,
) {
    override fun toString(): String =
        "ExcorPlaybackOption(status=$status, adapter=${adapter != null}, protection=${protection != null})"
}

/**
 * Decides the playback status from what is known; [available] false means the provider says the track cannot be
 * played (removed, or not in [region]). A protected track with no ready adapter is reported, not played.
 */
suspend fun excorPlaybackOptionFor(
    track: ExcorMusicTrack,
    protection: ExcorProtectionInfo?,
    adapters: List<ExcorProtectedPlaybackAdapter>,
    platform: ExcorPlatformCapabilities = ExcorPlatformCapabilities(),
): ExcorPlaybackOption {
    when (track.availability) {
        ExcorAvailability.UNAVAILABLE -> return ExcorPlaybackOption(ExcorPlaybackStatus.UNAVAILABLE, protection = protection)
        ExcorAvailability.REGION_RESTRICTED -> return ExcorPlaybackOption(ExcorPlaybackStatus.REGION_RESTRICTED, protection = protection)
        else -> Unit
    }
    if (protection != null && !protection.protected) return ExcorPlaybackOption(ExcorPlaybackStatus.UNPROTECTED_PLAYABLE, protection = protection)
    val adapter = adapters.firstOrNull { it.provider == track.provider && it.isReady() }
    if (adapter != null) {
        return when (adapter.isEntitled(track)) {
            false -> ExcorPlaybackOption(ExcorPlaybackStatus.SUBSCRIPTION_REQUIRED, protection = protection)
            else -> ExcorPlaybackOption(ExcorPlaybackStatus.PROTECTED_AUTHORIZED_ADAPTER, adapter, protection)
        }
    }
    val systems = protection?.systems.orEmpty() - ExcorDrmSystem.PROVIDER_MANAGED - ExcorDrmSystem.UNKNOWN
    if (systems.isNotEmpty() && systems.none { it in platform.drmSystems }) {
        return ExcorPlaybackOption(ExcorPlaybackStatus.DRM_UNSUPPORTED, protection = protection)
    }
    return ExcorPlaybackOption(ExcorPlaybackStatus.PROTECTED_REQUIRES_CLIENT, protection = protection)
}
