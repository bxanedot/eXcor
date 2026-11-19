# Authentication

eXcor embeds no credentials and stores no tokens itself. The host supplies tokens through `ExcorMusicConfig.tokens`, an `ExcorCatalogTokenProvider`, per provider:

| Provider | Token |
| --- | --- |
| Spotify | The user's OAuth access token |
| Deezer | The user's OAuth access token |
| Apple Music | The developer token |

The Apple Music-User-Token comes separately, from `appleMusicUserToken`.

## Token rules

- **Kept per provider.** A provider's token is sent only to that provider's API host. Every token store and refresh is keyed by provider.
- **Validated.** A token with whitespace or control characters, or longer than 8 KiB, is refused as `INVALID_CREDENTIAL` and never sent. A failing token provider becomes `CREDENTIAL_PROVIDER_FAILED`.
- **Redacted.** `ExcorOAuthToken`, `ExcorPkce` and `ExcorAuthorizationRequest` print no secrets. Responses fetched with a token are never cached.
- **Deezer's query-parameter token.** Deezer takes its token as an `access_token` query parameter, so eXcor never caches or logs such URLs.

## Spotify (Authorization Code with PKCE)

```kotlin
val auth = excor.music.spotifyAuth(clientId = "your-client-id")
val request = auth.authorizationUrl("com.example.app://callback", setOf("user-library-read", "playlist-read-private"))
// open request.url; on the redirect:
val token = auth.exchange(request, code, returnedState)   // rejects a state that differs
val manager = ExcorTokenManager(store = yourKeychainStore, refresher = auth.refresher, nowMs = { now() })
manager.connect(ExcorCatalogProvider.SPOTIFY, token)
val config = ExcorMusicConfig(tokens = { provider -> manager.accessToken(provider) })
```

PKCE needs no client secret. eXcor generates the verifier from 48 secure random bytes and computes the S256 challenge with its own SHA-256, which is tested against the FIPS 180-4 vectors and RFC 7636 appendix B.

`ExcorTokenManager` handles the token lifecycle:

- it refreshes 60 s before expiry, one refresh per provider at a time;
- it keeps the old refresh token when the provider doesn't rotate it;
- when the provider rejects the refresh token (`invalid_grant`), it deletes the stored token;
- `disconnect(provider)` deletes the token.

Spotify introduced refresh-token expiry in July 2026. The re-authorization path above handles it.

Since February 2026, Spotify Development Mode apps:

- require the app owner to have Premium;
- allow at most 5 users per app;
- get a reduced set of endpoints and fields.

Set `spotifyExtendedQuota = true` only for an app Spotify has approved for extended quota.

## Deezer

`excorDeezerAuthorizationUrl(appId, redirectUri, permissions)` builds the consent URL. Exchanging the code needs the app secret, so that step must run on the host's server, never in a shipped app. Deezer reportedly no longer accepts new app registrations; existing apps keep working.

## Apple Music

- **Developer token.** A developer token is an ES256 JWT signed with the host's MusicKit private key. Sign it on a server and hand the app only the token; the private key must never ship.
- **Music-User-Token.** The Music-User-Token comes from MusicKit after the listener consents (`MusicAuthorization` on Apple platforms, MusicKit for Android).
- **Without a developer token.** eXcor uses the keyless iTunes Search API.

## Your token store

Implement `ExcorTokenStore` over Keychain on Apple platforms, EncryptedSharedPreferences or Keystore on Android, or the OS credential vault on desktop.
