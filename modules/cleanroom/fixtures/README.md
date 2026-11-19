# Clean-room fixture manifest

These files are declared synthetic offline test inputs. They are not live
service captures. Their metadata describes fictional entities, and provider
domains or generic URL paths only model response shape. No media bytes,
credentials, signed playback URLs, or account data are included.

The manifest was assembled from the current fixture tree and its test call
sites. Individual author, creation date, and generator metadata were not
preserved, so the synthetic-origin statement remains a maintainer declaration,
not independent proof of authorship.

| File | Declared origin and sample identifiers | Consuming test |
|---|---|---|
| player-response.json | Synthetic player response; generic googlevideo.com URL shape, no video ID or media data | EngineStateTest, FaultInjectionTest, SessionGenerationTest |
| player-response-video.json | Synthetic format catalogue; generic googlevideo.com URL shape, no video ID or media data | VideoSelectionTest, EngineStateTest, SessionGenerationTest |
| watch-page.html | Synthetic page configuration; fixture-key and fixture player path are placeholders | WatchPageTest, EngineStateTest |
| apple-music/album-tracks.json | Synthetic June Harbor catalogue values and numeric placeholder IDs | AppleMusicProviderTest |
| apple-music/artist-albums.json | Synthetic June Harbor catalogue values and numeric placeholder IDs | AppleMusicProviderTest |
| apple-music/search-album.json | Synthetic June Harbor catalogue values, numeric placeholder IDs, and fake artwork URL | AppleMusicProviderTest |
| apple-music/search-artist.json | Synthetic June Harbor artist and numeric placeholder ID | AppleMusicProviderTest |
| apple-music/search-track.json | Synthetic June Harbor track and numeric placeholder IDs | AppleMusicProviderTest |
| spotify/album-tracks.json | Synthetic June Harbor catalogue values and repeated-digit IDs | SpotifyProviderTest |
| spotify/artist-albums.json | Synthetic June Harbor catalogue values and repeated-digit IDs | SpotifyProviderTest |
| spotify/lookup-track.json | Synthetic June Harbor catalogue values and repeated-digit IDs | SpotifyProviderTest |
| spotify/search-album.json | Synthetic June Harbor catalogue values and repeated-digit IDs | SpotifyProviderTest |
| spotify/search-artist.json | Synthetic June Harbor artist and repeated-digit ID | SpotifyProviderTest |
| spotify/search-track-page-2.json | Synthetic June Harbor catalogue values and repeated-digit IDs | SpotifyProviderTest |
| spotify/search-track.json | Synthetic June Harbor catalogue values and repeated-digit IDs | SpotifyProviderTest |

On 2026-11-12, the Spotify track identifier used in API documentation examples
was replaced with a repeated-digit placeholder in the three Spotify fixture
files and their test inputs. The official example remains documented at
[Spotify's Get Several Tracks reference](https://developer.spotify.com/documentation/web-api/reference/get-several-tracks);
the fixtures do not use that identifier.

The scope and limits of the provenance review are recorded in
[docs/CLEANROOM_PROVENANCE_REVIEW_2026-11-12.md](../../../docs/CLEANROOM_PROVENANCE_REVIEW_2026-11-12.md).
