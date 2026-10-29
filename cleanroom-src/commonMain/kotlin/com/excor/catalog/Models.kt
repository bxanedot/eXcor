package com.excor.catalog

enum class ExcorCatalogProvider { APPLE_MUSIC, DEEZER, SPOTIFY, YOUTUBE_MUSIC }

fun interface ExcorCatalogTokenProvider {
    suspend fun accessToken(provider: ExcorCatalogProvider): String?
}

enum class ExcorCatalogKind { TRACK, ALBUM, ARTIST }

enum class ExcorCatalogOperation { SEARCH, LOOKUP, ALBUM_TRACKS, ARTIST_ALBUMS }

enum class ExcorCatalogCapabilityStatus {
    SUPPORTED,
    AUTHENTICATION_REQUIRED,
    RESTRICTED,
    TEMPORARILY_UNAVAILABLE,
    UNSUPPORTED,
}

enum class ExcorCatalogError {
    INVALID_QUERY,
    INVALID_URL,
    INVALID_PAGE_TOKEN,
    INVALID_CREDENTIAL,
    CREDENTIAL_PROVIDER_FAILED,
    UNSUPPORTED,
    RESTRICTED,
    NOT_FOUND,
    INVALID_REQUEST,
    AUTHENTICATION_REQUIRED,
    ACCESS_DENIED,
    RATE_LIMITED,
    PROVIDER_UNAVAILABLE,
    NETWORK_FAILURE,
    MALFORMED_RESPONSE,
    RESPONSE_TOO_LARGE,
}

data class ExcorCatalogCapability(
    val provider: ExcorCatalogProvider,
    val operation: ExcorCatalogOperation,
    val status: ExcorCatalogCapabilityStatus,
)

data class ExcorCatalogArtist(
    val provider: ExcorCatalogProvider,
    val id: String,
    val name: String,
    val url: String? = null,
) {
    override fun toString(): String =
        "ExcorCatalogArtist(provider=$provider, id=$id, name=$name, url=${if (url == null) "missing" else "present"})"
}

data class ExcorArtwork(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
) {
    override fun toString(): String = "ExcorArtwork(width=$width, height=$height, url=present)"
}

data class ExcorCatalogItem(
    val provider: ExcorCatalogProvider,
    val kind: ExcorCatalogKind,
    val id: String,
    val title: String,
    val url: String? = null,
    val artists: List<ExcorCatalogArtist> = emptyList(),
    val albumId: String? = null,
    val albumTitle: String? = null,
    val durationMs: Long? = null,
    val releaseDate: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val trackCount: Int? = null,
    val isExplicit: Boolean? = null,
    val genres: List<String> = emptyList(),
    val artwork: List<ExcorArtwork> = emptyList(),
) {
    override fun toString(): String =
        "ExcorCatalogItem(provider=$provider, kind=$kind, id=$id, title=$title, artists=${artists.size}, artwork=${artwork.size}, url=${if (url == null) "missing" else "present"})"
}

data class ExcorCatalogPage(
    val items: List<ExcorCatalogItem>,
    val nextPageToken: String? = null,
    val truncated: Boolean = false,
) {
    override fun toString(): String =
        "ExcorCatalogPage(items=${items.size}, nextPageToken=${if (nextPageToken == null) "missing" else "present"}, truncated=$truncated)"
}

class ExcorCatalogException(
    val provider: ExcorCatalogProvider?,
    val error: ExcorCatalogError,
    val statusCode: Int? = null,
) : Exception("eXcor catalog request failed: $provider $error") {
    override fun toString(): String =
        "ExcorCatalogException(provider=${provider ?: "unknown"}, error=$error, statusCode=${statusCode ?: "missing"})"
}
