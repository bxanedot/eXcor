# Music metadata

The shared models are `ExcorMusicTrack`, `ExcorMusicAlbum`, `ExcorMusicArtistProfile` and `ExcorMusicPlaylist` (in `MusicModels.kt`). A value is never invented: when a provider doesn't give a field, it stays null or empty, and the model's `missing` map says why.

| `ExcorFieldStatus` | Meaning |
| --- | --- |
| `UNSUPPORTED` | The provider's interface never offers this field (for example, Deezer composers). |
| `UNAVAILABLE` | The provider offers the field, but this item has none. |
| `RESTRICTED` | The provider offers the field only with an authorization this request lacked. Examples: Spotify popularity and markets for Development Mode apps; ISRC on Apple without a developer token. |
| `UNKNOWN` | This response didn't say, and another request would. Examples: a search summary, or a track's label, which lives on its album. |

`music.metadata(track)` completes a track from its own provider. It fetches the full track when the given one is a summary, then the album, filling only empty fields: label, genres, album artists, UPC, copyright, release date and artwork. Fields it then still can't fill change from UNKNOWN to UNAVAILABLE.

## Track fields by provider

| Field | Spotify | Apple Music API | iTunes | Deezer |
| --- | --- | --- | --- | --- |
| Title, version | name | name | trackName | `title_short`, `title_version`; `displayTitle` is the full title |
| Artists and roles | artists | relationship or `artistName` | artistName | contributors (Main, Featured) |
| Album, album artists | album | `albumName`, relationship | collectionName | album; artists via `metadata()` |
| ISRC | `external_ids` | `isrc` | restricted | `isrc` |
| UPC | album `external_ids` | via album | restricted | via album |
| Track and disc number, duration, release date | ✓ | ✓ | ✓ | ✓ |
| Genres | via album (restricted in Development Mode) | `genreNames` | primary genre | via album |
| Label, copyright | via album | via album | copyright on album | label via album |
| Composers | unsupported | `composerName` | restricted | unsupported |
| Language | unsupported | `audioLocale` | restricted | unsupported |
| Explicit | ✓ | `contentRating` | `trackExplicitness` | `explicit_lyrics` |
| Popularity (provider's own scale) | `popularity` (restricted in Development Mode) | unsupported | unsupported | `rank` |
| Markets | `available_markets` (restricted in Development Mode) | unsupported | unsupported | `available_countries` |
| Availability | `is_playable`, `restrictions` | `playParams` present | `isStreamable` | `readable` |
| Audio traits | unsupported | `audioVariants` | restricted | unsupported |
| Preview available (boolean) | `preview_url` present | `previews` non-empty | not read | `preview` present |
| Lyrics available | unsupported | `hasLyrics` | restricted | unsupported |

## Safety rules for parsed data

- **Preview URLs are never returned or stored.** Only whether a preview exists. Deezer's `track_token` is never read.
- **Artwork and links are allowlisted.** Artwork is kept only over HTTPS on each provider's image hosts: `*.scdn.co` and `*.spotifycdn.com`; `*.mzstatic.com`; `*.dzcdn.net`. Links are kept only on the provider's own web host.
- **Page tokens are opaque and scoped.** Each token is bound to the listing it came from, so a token can't steer a request to another resource.

## Examples

```kotlin
val track = excor.music.track("https://www.deezer.com/track/3135556")
val complete = excor.music.metadata(track)          // adds label, genres, UPC from the album
val page = excor.music.albumTracks(album.let { ExcorMusicRef(it.provider, ExcorMusicKind.ALBUM, it.id) })
val more = excor.music.albumTracks(ref, page.next)  // null `next` means the last page
```
