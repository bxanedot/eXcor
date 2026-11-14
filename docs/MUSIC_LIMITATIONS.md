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
  - **Keyless rate and 403s.** Apple's Search API page says it is "limited to approximately 20 calls per minute (subject to change)", so eXcor holds keyless iTunes calls to 20 per minute, waiting rather than failing; the Apple Music API keeps its own budget. Separately, on 2026-11-02 this cloud environment got sporadic HTTP 403s from iTunes search: about 1 in 3 of 12 calls spaced 3 s apart, with the next identical call usually answering 200. eXcor reports them as `ACCESS_DENIED` and does not retry a refusal; `convertPlaylist` keeps the error on the affected track so the caller can try those tracks again later.
- **Deezer:**
  - **Registration.** A Deezer community report from mid-2026 says Deezer is not accepting new app registrations. There is no official Deezer statement, and the developer error documentation sits behind a login. eXcor maps the error types it has observed (`DataException`, `OAuthException`). Its quota rule (code 4, or "quota" in the type) is unverified.
  - **Public endpoints.** These work without credentials. eXcor keeps to 50 requests per 5 s, the commonly cited limit, which it could not confirm in Deezer's documentation.
  - **Full tracks.** They play only in Deezer's apps.

## Not implemented

- **Spotify:** nothing beyond the 2026 Development Mode limits. Playlist creation, details, covers and item editing, library saves, queue, volume, playback control and podcasts are implemented but tested offline only.
- **Apple Music:** editorial playlist browsing and storefront discovery. Charts cover songs only. Music videos, stations and recommendations are tested offline only.
- **Deezer:** writes to favorites and playlists (Deezer's write documentation is behind a login and new apps reportedly can't register); editorial new releases (the endpoint returned none when checked).
- **Built-in protected-playback adapters.** The host must supply them, and none has been tested on a device.
- **A built-in lyrics source.** Lyrics are licensed per use, so the host supplies a licensed `ExcorLyricsProvider`. eXcor parses LRC and TTML with line and word timing.
- **YouTube Music in `Excor.music`.** It reports RESTRICTED there; YouTube Music is read through the `Excor` API instead (`musicSearch`, `musicArtist`, `musicPlaylist`, `musicRadio`, `musicSuggestions`). From this environment YouTube Music withholds songs and albums.

## Known approximations

- **Diacritic folding in matching.** It covers Latin letters only.
- **Spotify playlist fallback.** For extended-quota apps, a 404 on a playlist's `/items` falls back to `/tracks`.
- **Region restrictions.** These are reported only where the provider states them: Deezer `readable` and `available_countries`, Spotify `restrictions`, Apple `playParams`.
