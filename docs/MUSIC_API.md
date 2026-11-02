# Music API

Everything is reached through `excor.music`, an instance of `ExcorMusic`. Every network call is a `suspend` function that throws `ExcorCatalogException(provider, error, statusCode)`. In Swift these calls are `async throws`.

| The brief's operation | eXcor |
| --- | --- |
| `music.search(query, providers)` | `search(query, providers, kinds, limit)` returns `ExcorMultiSearchResult` (results and per-provider errors); `search(query, provider, kinds, limit, page)` searches one provider and pages |
| `getTrack` / `getAlbum` / `getArtist` / `getPlaylist(urlOrId)` | `track(url)` or `track(ExcorMusicRef)`; likewise `album`, `artist`, `playlist` |
| Paging | `albumTracks(ref, page)`, `artistAlbums(ref, page)`, `playlistTracks(ref, page)`; pass `page.next` back |
| `getMetadata(track)` | `metadata(track)` completes from the same provider; `enrich(track, matches)` fills from other providers with attribution |
| `getLyrics(track)` | `lyrics(track)` uses the host's licensed `ExcorLyricsProvider`; `excorParseLrc` and `excorParseTtml` parse LRC and TTML with line and word timing; `track.hasLyrics` is Apple's availability flag |
| `getAudioCapabilities(track)` | `audioCapabilities(track)` returns `ExcorAudioCapabilities` |
| `getDrmInfo(track)` | `drmInfo(track)` returns `ExcorProtectionInfo` |
| `matchAcrossProviders(track)` | `matchAcrossProviders(track, providers, options)` |
| `syncLibrary(provider)` | `syncLibrary(provider, previousState, onPage)` |
| `syncPlaylist(playlist)` | `syncPlaylist(ref, previousState, onPage)` |
| `getPlaybackOptions(track)` | `playbackOptions(track)` |
| `createAuthorizedPlayback(track)` | `play(track)` uses a host adapter, or throws `ExcorPlaybackException` |

Other members:

- `parse(url)` parses links offline, including `spotify:` URIs and 32-hex Spotify GIDs.
- `capabilities(provider)` reports what this configuration can do.
- `tracksByIsrc(provider, isrc)` looks tracks up by ISRC.
- `convertPlaylist(ref, target, maxTracks, options, onTrack)` matches each track of a playlist onto another provider, in order, and returns `ExcorPlaylistConversion`: per track, the candidates best first (check the verdict of `best`) or the error that stopped the search. It finds candidates and writes nothing.
- `libraryTracks` and `libraryPlaylists` read the user's library.
- `spotifyAuth(clientId)` runs Spotify sign-in.
- `requestCount` counts the requests sent so far.

## Configuration

```kotlin
class Host : ExcorHost {
    override val musicConfig = ExcorMusicConfig(
        tokens = { provider -> tokenManager.accessToken(provider) },
        appleMusicUserToken = { musicKit.userToken() },
        appleStorefront = "gb",
        spotifyMarket = "GB",
        adapters = listOf(MusicKitAdapter(), SpotifyAppRemoteAdapter()),
        platform = ExcorPlatformCapabilities(drmSystems = setOf(ExcorDrmSystem.WIDEVINE), secureDecoder = true),
        lyrics = myLicensedLyrics,
    )
    // …
}
```

With no `musicConfig`, `catalogTokenProvider` supplies the tokens and everything else uses the defaults.

## Sync

```kotlin
val stored = prefs.getString("sync:$playlistId")?.let(ExcorSyncState::decode)
val result = excor.music.syncPlaylist(ref, stored) { page -> db.upsert(page.entries) }
prefs.putString("sync:$playlistId", result.state.encode())
// result.added / removed / moved / duplicates; result.complete == false means resume next time
```

Each run:

- returns at once, after one request, when the playlist's snapshot hasn't changed (Spotify `snapshot_id`, Deezer `checksum`, Apple `lastModifiedDate`);
- retries rate limits and transient errors with exponential backoff;
- resumes from the failed page after an interruption;
- restarts once if the playlist changes while it is being read;
- keeps only keys in memory and streams tracks to `onPage`.

## Lower-level functions

- `excorMatchTrack` and `excorRankMatches` score matches.
- `excorParseTitle` parses titles.
- `excorSelectAudio` selects a format.
- `excorHlsProtectionOf` and `excorDashProtectionOf` detect protection.
- `excorPlaybackOptionFor` decides playback.
- `ExcorSyncEngine` syncs any `ExcorSyncSource`.
- `ExcorTokenManager` and `ExcorPkce` handle tokens.

Each is usable on its own.
