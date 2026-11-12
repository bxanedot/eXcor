package com.excor.catalog

import io.ktor.client.HttpClient
import io.ktor.http.Url

class ExcorMusicCatalog internal constructor(
    http: HttpClient,
    tokenProvider: ExcorCatalogTokenProvider? = null,
) {
    private val appleMusic = AppleMusicProvider(http)
    private val spotify = SpotifyProvider(http, tokenProvider)

    fun capabilities(provider: ExcorCatalogProvider): List<ExcorCatalogCapability> =
        ExcorCatalogOperation.values().map { operation ->
            ExcorCatalogCapability(provider, operation, status(provider))
        }

    suspend fun search(
        provider: ExcorCatalogProvider,
        query: String,
        kind: ExcorCatalogKind,
        limit: Int = 25,
        pageToken: String? = null,
    ): ExcorCatalogPage = when (provider) {
        ExcorCatalogProvider.APPLE_MUSIC -> appleMusic.search(query, kind, limit, pageToken)
        ExcorCatalogProvider.SPOTIFY -> spotify.search(query, kind, limit, pageToken)
        else -> throw unavailable(provider)
    }

    suspend fun lookup(url: String): ExcorCatalogItem? {
        val parsed = runCatching { Url(url) }.getOrNull() ?: throw catalogError(null, ExcorCatalogError.INVALID_URL)
        if (parsed.protocol.name != "https" || parsed.port != 443 || parsed.user != null || parsed.password != null) {
            throw catalogError(providerForHost(parsed.host), ExcorCatalogError.INVALID_URL)
        }
        return when (parsed.host) {
            "music.apple.com" -> appleMusic.lookup(url)
            "open.spotify.com" -> spotify.lookup(url)
            "spotify.com", "www.spotify.com" -> throw catalogError(ExcorCatalogProvider.SPOTIFY, ExcorCatalogError.INVALID_URL)
            "deezer.com", "www.deezer.com" -> throw unsupported(ExcorCatalogProvider.DEEZER)
            "music.youtube.com", "youtube.com", "www.youtube.com" ->
                throw catalogError(ExcorCatalogProvider.YOUTUBE_MUSIC, ExcorCatalogError.RESTRICTED)
            else -> throw catalogError(null, ExcorCatalogError.INVALID_URL)
        }
    }

    suspend fun albumTracks(
        provider: ExcorCatalogProvider,
        albumId: String,
        pageToken: String? = null,
    ): ExcorCatalogPage = when (provider) {
        ExcorCatalogProvider.APPLE_MUSIC -> appleMusic.albumTracks(albumId, pageToken)
        ExcorCatalogProvider.SPOTIFY -> spotify.albumTracks(albumId, pageToken)
        else -> throw unavailable(provider)
    }

    suspend fun artistAlbums(
        provider: ExcorCatalogProvider,
        artistId: String,
        pageToken: String? = null,
    ): ExcorCatalogPage = when (provider) {
        ExcorCatalogProvider.APPLE_MUSIC -> appleMusic.artistAlbums(artistId, pageToken)
        ExcorCatalogProvider.SPOTIFY -> spotify.artistAlbums(artistId, pageToken)
        else -> throw unavailable(provider)
    }

    private fun status(provider: ExcorCatalogProvider): ExcorCatalogCapabilityStatus = when (provider) {
        ExcorCatalogProvider.APPLE_MUSIC -> ExcorCatalogCapabilityStatus.SUPPORTED
        ExcorCatalogProvider.SPOTIFY -> if (spotify.hasTokenProvider) {
            ExcorCatalogCapabilityStatus.SUPPORTED
        } else {
            ExcorCatalogCapabilityStatus.AUTHENTICATION_REQUIRED
        }
        ExcorCatalogProvider.YOUTUBE_MUSIC -> ExcorCatalogCapabilityStatus.RESTRICTED
        ExcorCatalogProvider.DEEZER -> ExcorCatalogCapabilityStatus.UNSUPPORTED
    }

    private fun unsupported(provider: ExcorCatalogProvider) =
        catalogError(provider, ExcorCatalogError.UNSUPPORTED)

    private fun unavailable(provider: ExcorCatalogProvider) =
        catalogError(
            provider,
            if (provider == ExcorCatalogProvider.YOUTUBE_MUSIC) ExcorCatalogError.RESTRICTED else ExcorCatalogError.UNSUPPORTED,
        )

    private fun providerForHost(host: String): ExcorCatalogProvider? = when (host) {
        "music.apple.com", "itunes.apple.com" -> ExcorCatalogProvider.APPLE_MUSIC
        "open.spotify.com", "spotify.com", "www.spotify.com" -> ExcorCatalogProvider.SPOTIFY
        "deezer.com", "www.deezer.com" -> ExcorCatalogProvider.DEEZER
        "music.youtube.com", "youtube.com", "www.youtube.com" -> ExcorCatalogProvider.YOUTUBE_MUSIC
        else -> null
    }

    private fun catalogError(provider: ExcorCatalogProvider?, kind: ExcorCatalogError) =
        ExcorCatalogException(provider, kind)
}
