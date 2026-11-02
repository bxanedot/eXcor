# Cross-provider matching

`excorMatchTrack(source, candidate, options)` compares two tracks deterministically. `excorRankMatches` scores a list. `music.matchAcrossProviders(track)` finds candidates on the other providers: by ISRC where the provider supports ISRC lookup, otherwise by title and primary artist. It returns the scored candidates per provider, dropping DIFFERENT ones, and reports per-provider errors separately.

Matching is for discovery and metadata reconciliation. It is not a way to play one provider's track from another provider.

## Rules

**Any of these makes the verdict DIFFERENT:**
- version markers differ: live, remix, acoustic, remaster, instrumental, karaoke, cover, demo, edit, extended, a cappella, sped up, slowed, orchestral, mono, re-recorded;
- explicit and clean differ;
- the base titles aren't similar;
- no primary artist is shared, which usually means a cover;
- durations differ by more than `maxDurationDifferenceMs` (default 10 s).

A shared ISRC overrides the title and artist checks, but not a version or explicit/clean conflict.

**Agreements add confidence:**

| Evidence | Points |
| --- | --- |
| Title equal | 0.35 |
| Title similar | 0.20 |
| Primary artist shared | 0.25 |
| Duration within 2 s | 0.25 |
| Duration within 5 s | 0.10 |
| Same UPC | 0.10 |
| Same album title | 0.05 |
| Same release year | 0.05 |
| Same disc and track number on the same UPC | 0.05 |

Differing ISRCs subtract 0.15: reissues can carry new ISRCs for the same audio, so this lowers confidence rather than rejecting.

**Verdicts:**

| Verdict | When |
| --- | --- |
| SAME_RECORDING | Confidence 1.0, only from an ISRC agreement |
| LIKELY | Confidence ≥ 0.8 (`likelyThreshold`) |
| POSSIBLE | Confidence ≥ 0.6 |
| DIFFERENT | Below 0.6, or a rejection above |

Every result lists its evidence.

**Substitutions:** `allowCleanExplicitSubstitution` and `allowVersionSubstitution` turn those rejections into a match that carries a `substitutions` set. The caller must then show the difference to the user.

## Title parsing

Version markers and featured artists are read only from bracketed segments, " - " suffixes and a provider's version field. "Live Forever" is a title, not a live version. Text is lowercased; Latin diacritics, apostrophes and punctuation are dropped, and "&" becomes "and".

`shortcut:` diacritic folding covers Latin letters only, because common Kotlin has no Unicode normalizer. Extend the table if other scripts need it.

## Enrichment

`music.enrich(track, matches)` fills only **empty** fields: ISRC, UPC, label, genres, composers, language, release date and artwork. It takes them from non-substituted matches at or above the confidence threshold, and records each filled field's provider, ID and confidence in `sources`. Fields the track already has are never replaced.

## Example

```kotlin
val deezer = excor.music.track("https://www.deezer.com/track/3135556")
val result = excor.music.matchAcrossProviders(deezer, setOf(ExcorCatalogProvider.APPLE_MUSIC))
val best = result.matches[ExcorCatalogProvider.APPLE_MUSIC]?.firstOrNull()   // verdict, confidence, evidence
```

The live test matches this Deezer track to Apple Music with a LIKELY or SAME_RECORDING verdict.

## From a YouTube video

`excor.videoInfo(videoId)?.asMusicTrack()` turns a YouTube video into a matchable track:
- an auto-generated "Artist - Topic" channel gives the artist directly; otherwise an "Artist - Title" video title is split;
- tags that only describe the video, such as "(Official Video)" or "[4K Remaster]", are dropped;
- version tags such as "(Live …)" are kept.

There is no ISRC, so matching scores it as a text candidate. The live test matches a music video to its Deezer track this way.

When YouTube credits the song in the description's Music section, `excor.songs(videoId)` gives a cleaner source: `song.asMusicTrack(videoId, durationMs)` uses YouTube's own title, artist and album instead of a title split. Pass the video's duration only when the video is the song itself. The live test matches a credited song to Deezer this way.

## To YouTube

`excor.findOnYouTube(track)` goes the other way: it searches YouTube once for the track's primary artist and title, reads each video as a track (as above), and returns the non-DIFFERENT matches, best first. Live streams are skipped. A music video whose intro or outro runs past the duration tolerance is rejected like any other duration mismatch, which usually leaves the auto-generated "Topic" upload of the recording. The live test finds a Deezer track on YouTube this way.

## Whole playlists

`music.convertPlaylist(ref, target)` runs `matchAcrossProviders` for each track of a playlist, in order, up to `maxTracks` (default 500), and reports each track through `onTrack` as it finishes. Tracks are matched one after another, so the request budgets set the pace: Deezer allows 50 requests per 5 s, and the keyless iTunes API about 20 per minute. A track the target can't be searched for keeps its error, and the conversion continues. `truncated` says whether tracks were left unread. The live test converts the first five tracks of a Deezer playlist to Apple Music.
