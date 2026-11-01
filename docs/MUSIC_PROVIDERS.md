# Music providers

`Excor.music` (`com.excor.music.ExcorMusic`) puts Spotify, Apple Music and Deezer behind one API. Each provider is created on first use. `capabilities(provider)` reports what this configuration can do, as SUPPORTED, AUTHENTICATION_REQUIRED, RESTRICTED or UNSUPPORTED, so callers check before calling instead of assuming.

None of these providers gives eXcor audio. Full tracks are protected and play only in the provider's own client or SDK; see [DRM_COMPATIBILITY.md](DRM_COMPATIBILITY.md).

## Status

"Live-verified" means the opt-in `LiveMusicTest` passed against the real service from this repository's cloud environment on 2026-11-01. "Offline-tested" means the code is tested only against synthetic responses in the provider's documented shape.

| Provider | Access | Status |
| --- | --- | --- |
| Deezer, public API | No credentials | **Live-verified**: track, album, artist, playlist, paging, search, ISRC and UPC lookup, charts, genres, related artists, playlist sync. |
| Deezer, user library | OAuth access token from the host | Offline-tested. Deezer reportedly stopped accepting new app registrations; see [MUSIC_LIMITATIONS.md](MUSIC_LIMITATIONS.md). |
| Apple Music, iTunes Search API | No credentials | **Live-verified**: track, album with tracks, artist, search. Gives no ISRC, playlists or audio variants. |
| Apple Music API | Developer token (JWT the host signs on its own server) | Offline-tested. A live test runs when `EXCOR_APPLE_DEVELOPER_TOKEN` is set. |
| Apple Music library and recently played | Developer token plus Music-User-Token from MusicKit | Offline-tested. |
| Spotify Web API | OAuth access token from the host; `spotifyAuth(clientId)` runs PKCE | Offline-tested. A live test runs when `EXCOR_SPOTIFY_TOKEN` is set. |

## Operations by provider

| Operation | Spotify | Apple Music API | iTunes fallback | Deezer |
| --- | --- | --- | --- | --- |
| Search tracks, albums, artists, playlists | ✓ (max 10 per page in Development Mode) | ✓ (max 25) | tracks, albums, artists | ✓ (max 100) |
| Track, album, artist | ✓ | ✓ | ✓ | ✓ |
| Playlist and its tracks | ✓ (Development Mode: only playlists the user owns or collaborates on) | catalog playlists | – | ✓ public |
| Album tracks and artist albums, paged | ✓ | ✓ | single page | ✓ |
| ISRC lookup | ✓ (`isrc:` search) | ✓ (`filter[isrc]`) | – | ✓ (`track/isrc:`) |
| UPC lookup | ✓ (`upc:` search) | ✓ (`filter[upc]`) | – | ✓ (`album/upc:`) |
| User library: saved tracks and playlists | ✓ | ✓ (with user token) | – | ✓ (with token) |
| Charts | – | ✓ `appleMusicCatalog.chartSongs` | – | ✓ `deezerCatalog.chartTracks` |
| Genres, radios, related artists, artist top tracks | – | – | – | ✓ `deezerCatalog` |
| Playback state | ✓ `spotifyCatalog.playbackState` | – | – | – |
| Audio variants (lossless, Atmos…) | not offered | ✓ | – | not offered |
| Lyrics availability | not offered | ✓ `hasLyrics` | – | not offered |
| Preview availability (boolean only) | ✓ when returned | ✓ | – | ✓ |

## Provider-specific extras

Provider-only features stay out of the shared models: `music.deezerCatalog`, `music.spotifyCatalog` and `music.appleMusicCatalog`.

## Not implemented

Spotify podcasts and episodes (links parse; no metadata calls), Apple Music stations, music videos and recommendations, and Spotify playback control (play, pause, skip). [MUSIC_LIMITATIONS.md](MUSIC_LIMITATIONS.md) has the full list.
