package com.excor.music

import com.excor.catalog.ExcorArtwork
import com.excor.catalog.ExcorCatalogError
import com.excor.catalog.ExcorCatalogException
import com.excor.catalog.ExcorCatalogProvider
import io.ktor.http.Url
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** One provider behind [ExcorMusic]. Operations a provider lacks throw UNSUPPORTED; [capabilities] says which. */
internal abstract class MusicSource(val provider: ExcorCatalogProvider) {
    abstract suspend fun capabilities(): Map<ExcorMusicCapability, com.excor.catalog.ExcorCatalogCapabilityStatus>
    abstract fun parse(url: String): ExcorMusicRef?
    /** What the provider states about full-track protection; previews are not covered. */
    abstract fun protection(track: ExcorMusicTrack): ExcorProtectionInfo?

    open suspend fun search(query: String, kinds: Set<ExcorMusicKind>, limit: Int, page: String?): ExcorMusicSearchResult = unsupported()
    open suspend fun track(id: String): ExcorMusicTrack = unsupported()
    open suspend fun album(id: String): ExcorMusicAlbum = unsupported()
    open suspend fun artist(id: String): ExcorMusicArtistProfile = unsupported()
    open suspend fun playlist(id: String): ExcorMusicPlaylist = unsupported()
    open suspend fun albumTracks(id: String, page: String?): ExcorMusicPage<ExcorMusicTrack> = unsupported()
    open suspend fun artistAlbums(id: String, page: String?): ExcorMusicPage<ExcorMusicAlbum> = unsupported()
    open suspend fun playlistTracks(id: String, page: String?): ExcorMusicPage<ExcorMusicTrack> = unsupported()
    open suspend fun tracksByIsrc(isrc: String): List<ExcorMusicTrack> = unsupported()
    open suspend fun libraryTracks(page: String?): ExcorMusicPage<ExcorMusicTrack> = unsupported()
    open suspend fun libraryPlaylists(page: String?): ExcorMusicPage<ExcorMusicPlaylist> = unsupported()

    protected fun unsupported(): Nothing = throw fail(ExcorCatalogError.UNSUPPORTED)
    fun fail(error: ExcorCatalogError, status: Int? = null) = ExcorCatalogException(provider, error, status)

    /** Page tokens carry the scope they belong to, so a token from one listing cannot steer another request. */
    protected fun pageToken(scope: String, offset: Int) = "${provider.name}|$scope|$offset"

    protected fun offsetOf(scope: String, token: String?): Int {
        if (token == null) return 0
        val prefix = "${provider.name}|$scope|"
        if (token.length > 1_024 || !token.startsWith(prefix)) throw fail(ExcorCatalogError.INVALID_PAGE_TOKEN)
        return token.removePrefix(prefix).toIntOrNull()?.takeIf { it in 0..MAX_OFFSET } ?: throw fail(ExcorCatalogError.INVALID_PAGE_TOKEN)
    }

    protected fun query(text: String): String {
        val q = text.trim()
        if (q.isEmpty() || q.length > 200) throw fail(ExcorCatalogError.INVALID_QUERY)
        return q
    }

    companion object {
        const val MAX_OFFSET = 10_000
    }
}

/** An HTTPS URL on one of [hosts] without credentials or an odd port; null otherwise. */
internal fun httpsUrlOn(value: String, hosts: Set<String>): Url? {
    val url = runCatching { Url(value.trim()) }.getOrNull() ?: return null
    if (url.protocol.name != "https" || url.port != 443 || url.user != null || url.password != null) return null
    return url.takeIf { it.host.lowercase() in hosts }
}

/** An HTTPS image URL on a host ending with one of [suffixes]; null otherwise. */
internal fun artworkOf(value: String?, suffixes: List<String>, width: Int? = null, height: Int? = null): ExcorArtwork? {
    val url = value?.let { runCatching { Url(it) }.getOrNull() } ?: return null
    if (url.protocol.name != "https" || url.port != 443 || url.user != null) return null
    val host = url.host.lowercase()
    if (suffixes.none { host == it.removePrefix(".") || host.endsWith(it) }) return null
    return ExcorArtwork(value, width, height)
}

internal fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject
internal fun JsonObject.arr(name: String): JsonArray? = this[name] as? JsonArray
internal fun JsonObject.str(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString || it.contentOrNull != null }?.contentOrNull?.takeIf { it.isNotBlank() }
internal fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull
internal fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.longOrNull
internal fun JsonObject.double(name: String): Double? = (this[name] as? JsonPrimitive)?.doubleOrNull
internal fun JsonObject.bool(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull
internal fun JsonObject.strings(name: String): List<String> = arr(name).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
internal fun JsonArray.objects(): List<JsonObject> = mapNotNull { it as? JsonObject }
internal fun JsonElement.asObject(provider: ExcorCatalogProvider): JsonObject =
    this as? JsonObject ?: throw ExcorCatalogException(provider, ExcorCatalogError.MALFORMED_RESPONSE)
