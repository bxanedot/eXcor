# Music limitations

## Service restrictions

- **Spotify:**
  - **Development Mode (since February and March 2026).** Apps require the owner to have Premium, allow 5 users, cap search at 10 results, and omit popularity, markets, label and follower counts. Playlist items are returned only for playlists the user owns or collaborates on. Artist top tracks, new releases, categories and batch endpoints were removed.
  - **Extended quota.** It needs Spotify's approval.
  - **Quotas.** Quotas are counted per developer account (July 2026), and 429 responses carry `QUOTA_EXCEEDED`.
  - **Audio.** The Web API gives no audio and no audio-quality data; a session never authorizes exporting audio.
- **Apple Music:**
  - **Developer token.** The full API needs an Apple Developer Program membership and a developer token.
  - **Personal data.** The library and recently played need the user's MusicKit consent.
  - **Previews.** Preview audio is never returned, by design.
  - **Audio variants.** Lossless or Atmos availability is a catalog declaration. It doesn't mean a file is accessible.
- **Deezer:**
  - **Registration.** A Deezer community report from mid-2026 says Deezer is not accepting new app registrations. There is no official Deezer statement, and the developer error documentation sits behind a login. eXcor maps the error types it has observed (`DataException`, `OAuthException`). Its quota rule (code 4, or "quota" in the type) is unverified.
  - **Public endpoints.** These work without credentials. eXcor keeps to 50 requests per 5 s, the commonly cited limit, which it could not confirm in Deezer's documentation.
  - **Full tracks.** They play only in Deezer's apps.

## Not implemented

- **Spotify:** following and saving to the library (`PUT /me/library`), queue management and volume. Playback control and podcasts are implemented but tested offline only.
- **Apple Music:** editorial playlist browsing and storefront discovery. Charts cover songs only. Music videos, stations and recommendations are tested offline only.
- **Deezer:** writes to favorites and playlists; podcasts; editorial endpoints.
- **Built-in protected-playback adapters.** The host must supply them, and none has been tested on a device.
- **A built-in lyrics source.** Lyrics are licensed per use, so the host supplies a licensed `ExcorLyricsProvider`. eXcor parses LRC; it doesn't parse TTML.
- **YouTube Music in `Excor.music`.** It reports RESTRICTED. YouTube extraction stays in the existing `Excor` API.

## Known approximations

- **Spotify GID conversion.** It uses base 62 with the alphabet digits, then lower case, then upper case. It hasn't been checked against a Spotify-issued GID.
- **Diacritic folding in matching.** It covers Latin letters only.
- **Spotify playlist fallback.** For extended-quota apps, a 404 on a playlist's `/items` falls back to `/tracks`.
- **Region restrictions.** These are reported only where the provider states them: Deezer `readable` and `available_countries`, Spotify `restrictions`, Apple `playParams`.
