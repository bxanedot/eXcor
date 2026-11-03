# eXcor implementation status

Updated 2026-11-02, based on `main` after bxanedot/eXcor#53. This repository remains a draft SDK, not a verified public release. Nothing here is a legal clearance.

## Current deliverables

| Deliverable | State |
|---|---|
| Clean-room Kotlin YouTube audio engine | Implemented in `cleanroom-src/` with offline tests. Stream extraction can't be verified from this cloud environment: YouTube answers `LOGIN_REQUIRED` or `UNPLAYABLE` and a reCAPTCHA here, and eXcor does not work around it |
| Clean-room YouTube metadata (`Excor`) | Live-verified 2026-11-02 with `EXCOR_LIVE=1`: video metadata, search with YouTube's own filters and Shorts, playlists, mixes, channels (Videos, Shorts, Live, Playlists, Posts tabs; About panel), related videos, comments (top and newest, replies), chapters, credited songs, suggestions, and `findOnYouTube` for tracks from other providers. Transcripts, hashtag pages, the caption track list and the Releases tab are blocked from this environment; see `FEATURE_MATRIX.md` |
| Music engine (`Excor.music`) | Implemented in `cleanroom-src/commonMain/kotlin/com/excor/music/`: models, capabilities, matching, enrichment, playlist sync, audio-format and DRM reporting, OAuth/PKCE, LRC/TTML parsing |
| Deezer metadata | Live-verified 2026-11-02 (keyless public API) by `LiveMusicTest.deezerCatalogEndToEnd`; writes are offline-tested only |
| Apple Music metadata | Keyless iTunes Search/Lookup live-verified 2026-11-02, including a Deezer-to-Apple match (one transient 403 noted in `MUSIC_LIMITATIONS.md`). The full Apple Music API needs a developer token and is offline-tested only |
| Spotify metadata and remote control | Offline-tested only; live checks need a host-supplied token (`EXCOR_SPOTIFY_TOKEN`) and run skipped here |
| YouTube Music | Live-verified 2026-11-02: search with filters and continuations, suggestions, artist pages, playlists and radio queues, through `Excor`. Songs, albums and discographies are withheld from this environment |
| Native Swift `eXcorKit` | Under `swift/`; built and tested on the macOS CI job |
| Apps | The music-player apps were removed on 2026-11-03. The preview apps use the clean-room library: an Android APK (`preview` job, linted, started on API 26 and 36 emulators) and a desktop app packaged with jpackage (self-test and start on Linux; `eXcor Preview.exe` built and self-tested on Windows) |
| CI | Green on Linux, Windows, macOS, apps and Android devices (API 26 and 36) for every merged PR through bxanedot/eXcor#53 |
| Provenance | Active clean-room sources are logged in `PROVENANCE.md`; the clean-room disclosure is in `CLEAN_ROOM.md` |

## Environment and local verification

The full JVM suite, Android library packaging and Kotlin compilation for iOS arm64, simulator arm64 and x64 pass locally. Linux cannot run the iOS simulator or link Apple frameworks, so those checks run on the macOS workflow. Live checks are opt-in (`EXCOR_LIVE=1`) and never run in CI.

## Remaining release gates

- Verify Spotify and the full Apple Music API live with real credentials, and confirm current provider terms and eligibility.
- Verify stream extraction from a normal network; it can't be observed from this environment.
- Integrate the apps with the clean-room API.
- Complete clean-room/provenance and fixture-rights review for excluded repository content before making any whole-repository legal claim.
- Add release metadata, public API compatibility checks and reproducible publication workflows for verified targets.
