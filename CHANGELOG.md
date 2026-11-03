# Changelog

All notable changes to the eXcor library (`com.excor:eXcor`). No version has been published to a remote repository yet.

## Unreleased (after 0.2.0)

### Added
- **Video extraction:** `extractVideo` returns a video track (width, height, frame rate, codec, quality label, length, DASH byte ranges) and the audio to play with it, from the same client response, both probed as playable. Audio is matched to the video's container; a progressive format is used when the response has no playable separate audio. Verified live at 480p and 1080p with an ffmpeg mux and full decode.
- **YouTube metadata:**
  - `videoInfo`, falling back to the watch page when YouTube's bot check withholds details (`fromWatchPage`);
  - `search`, with YouTube's own filters, Shorts, continuations and suggestions;
  - `playlist`; `mix`;
  - `channel`: Videos, Shorts, Live, Playlists and Posts tabs; `isLive` marking;
  - `channelAbout`, `related`, `comments` (top, newest, replies), `chapters`, `songs` (credited music).
- **YouTube Music:** `musicSearch` (typed items, YouTube Music's filters, continuations), `musicSuggestions`, `musicArtist`, `musicPlaylist`, `musicRadio`.
- **Cross-provider music** (`Excor.music`):
  - Spotify, Apple Music and Deezer catalogs; matching and enrichment;
  - playlist and library sync; `convertPlaylist`;
  - `findOnYouTube`, and `asMusicTrack` for videos, search results and credited songs;
  - audio-format and DRM reporting; authorized-playback decisions; OAuth/PKCE; LRC and TTML lyrics parsing.
- **Errors:** `ExcorRequestException` with `ExcorRequestFailure` (`RATE_LIMITED`, `SERVER_ERROR`, `NETWORK`) for metadata requests that keep failing.
- **Setup:** `excorDefaultHost()` for JVM and Android.
- **Packaging:** `samples/jvm-quickstart`, and README Installation and Quick start sections.
- **Opt-in tooling:** a YouTube benchmark (`EXCOR_BENCH=1`) and live tests (`EXCOR_LIVE=1`).

### Changed
- **Retries:** InnerTube metadata reads retry HTTP 429, 500, 502, 503, 504 and transport errors. Three attempts at most; a Retry-After of up to 5 s is honored, otherwise exponential backoff with jitter. Once retries run out, metadata calls throw `ExcorRequestException` where they used to return null. A null result now means only that YouTube answered without the expected page.
- **Failed channel tabs:** `channel()` reports a tab that fails to load (as the exception above) instead of returning an empty list.
- **Keyless iTunes budget:** keyless iTunes requests are held to Apple's documented ~20 calls per minute.
- **Diagnostics:** non-2xx InnerTube answers are logged at WARN, with the endpoint name and status only. Extraction logs at DEBUG why each client was skipped or which probe status it got.
- **CI:** it publishes the library locally, builds the sample consumer, and scans dependencies (OSV), gating on what ships.
- **Tests:** TestNG for tests is 7.5.1 (GHSA-rc2q-x9mf-w3vf).

### Fixed
- **Stream extraction found nothing even when YouTube served a playable response.** Three causes:
  - it no longer requires the HTML watch page, which can answer with a captcha, for clients that need neither the page's player script nor its API key;
  - VISIONOS player requests no longer send youtube.com's Referer, which YouTube answers with 403;
  - the playback probe follows googlevideo's redirects to other allowlisted nodes.

  Verified live by fully decoding an extracted stream with ffmpeg.
- **Publishing:** the library could not be published. The shared metadata did not compile, because common Kotlin has no `RegexOption.DOT_MATCHES_ALL`.
- **`videoInfo`:** it returned null for most videos from bot-checked networks.
- **Mixes:** a mix's continuation was lost when its last item did not decode.

### Known limitations
- **Bot-checked networks:** streams, captions and SABR/UMP can't be verified where YouTube bot-checks every player request. SABR/UMP is not implemented. See `EXCOR_PROGRESS.md` and `docs/release-gates.json`.
- **YouTube Music catalog:** songs, albums and discographies are decoded when YouTube Music returns them, but are untested live, because it withholds them from the test environment.
