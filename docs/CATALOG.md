# Catalog metadata API

The Kotlin catalog API is experimental. It returns metadata only; it does not download audio, fetch artwork, or store provider responses. See the [feature matrix](FEATURE_MATRIX.md) and [implementation status](IMPLEMENTATION_STATUS.md) before shipping it.

## Current providers

| Provider | Current Kotlin behavior | Access |
|---|---|---|
| Apple/iTunes | Search and lookup tracks, albums, and artists; list album tracks and credited artist albums | Public iTunes Search/Lookup API; this is not the full Apple Music API |
| Spotify | Search and lookup tracks, albums, and artists; list album tracks and artist albums | Host supplies an authorized access token; eXcor does not run OAuth, refresh tokens, or store credentials |
| Deezer | Unsupported | Current official API eligibility and terms are unverified |
| YouTube Music | Restricted | No documented, authorized music-catalog API path has been verified |

Apple and Spotify responses preserve provider identity. Missing upstream fields remain null or empty. Artwork URLs are returned as metadata; eXcor does not retrieve the images. Preview audio URLs are not included.

## Host authorization

Implement `ExcorCatalogTokenProvider` on the host only for providers that use bearer-token APIs. The callback is invoked when a provider request is made; the host owns token acquisition and refresh.

```kotlin
override val catalogTokenProvider = ExcorCatalogTokenProvider { provider ->
    when (provider) {
        ExcorCatalogProvider.SPOTIFY -> spotifyTokenStore.currentAccessToken()
        else -> null
    }
}

val page = excor.musicCatalog.search(
    provider = ExcorCatalogProvider.SPOTIFY,
    query = "North Loop",
    kind = ExcorCatalogKind.TRACK,
)
```

Keep returned access tokens private. eXcor places them only in the provider request header and does not include them in errors or model `toString()` output. A provider may still reject an otherwise valid token because of app access, market, or account restrictions.

## Operations

`search(provider, query, kind, limit, pageToken)` searches one resource kind. Apple accepts limits 1–200; Spotify accepts 1–50. `lookup(url)` accepts validated Apple Music or Spotify share links. Spotify album-track and artist-album lists support continuation tokens. Apple returns one capped page for those lists and sets `truncated` when the limit is reached; it does not currently expose a continuation token. Spotify tokens are opaque and scoped to provider, operation, resource, query, kind, and page size.

`capabilities(provider)` reports `AUTHENTICATION_REQUIRED` for Spotify when no token callback is configured, `UNSUPPORTED` for Deezer, and `RESTRICTED` for YouTube Music. A configured Spotify callback means the host has an authentication mechanism; an individual request can still fail with `AUTHENTICATION_REQUIRED` if it returns no token.

Requests report typed `ExcorCatalogException` values for invalid input, authentication/authorization failures, rate limits, provider failures, malformed responses, and oversized responses. Cancellation is propagated to the caller. Provider response bodies and credential values are not copied into exceptions.

## Verification status

Tests use synthetic response fixtures. No live Apple or Spotify credentials were used, and current Spotify Terms/access eligibility have not been verified in the restricted cloud environment. Deezer and YouTube Music must not be described as supported. The eXcorKit Swift package does not yet expose catalog metadata.
