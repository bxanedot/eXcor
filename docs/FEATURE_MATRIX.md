# eXcor feature matrix

This matrix describes the active clean-room Kotlin sources under `cleanroom-src/` and the separate Swift package under `swift/`. Preserved implementation folders are excluded. The catalog prototype is on `feature/catalog-metadata`, based on `main` at `e12ba98`; GitHub Actions run [40968192280](https://github.com/bxanedot/eXcor/actions/runs/40968192280) passes on Linux, Windows, and macOS. `Supported and verified` means the behavior has a public API, relevant tests, and passing CI for the stated target; it does not by itself mean production-ready.

| Capability | Kotlin KMP status | eXcorKit status | Evidence or gap |
|---|---|---|---|
| Resolve audio from a YouTube video ID | Supported and verified on CI targets | Supported and verified on CI targets | `Excor.extract` / `ExcorKit.extract`; offline tests and workflow run 40968192280 |
| Apple/iTunes track, album, and artist metadata | Experimental Kotlin adapter; offline tests and cross-target CI pass | Unsupported | Synthetic search, lookup, album-track, and artist-album fixtures; live API behavior is unverified, and this uses iTunes Search/Lookup rather than the full Apple Music API |
| Deezer catalog metadata (`Excor.music`) | Supported; live-verified 2026-11-01 (keyless public API) | Unsupported | Track, album, artist and playlist reads, search, ISRC and UPC lookup, charts, genres, related artists and playlist sync all pass `LiveMusicTest`. The user library is offline-tested only, and new Deezer app registration is reportedly closed. |
| Spotify catalog metadata | Experimental Kotlin adapter; offline tests and cross-target CI pass | Unsupported | Host supplies OAuth access tokens; synthetic fixture tests cover search, lookup, album tracks, artist albums, pagination, and auth errors; current terms and live access remain unverified |
| YouTube Music catalog metadata | Restricted / unsupported | Restricted / unsupported | No authorized music catalog API path verified; undocumented private endpoints are not used |
| Cross-provider catalog API | Experimental Kotlin models and capability surface; offline tests and cross-target CI pass | Unsupported | Search, URL lookup, album tracks, and artist albums are wired for Apple and Spotify |
| Unified music API (`Excor.music`) for Spotify, Apple Music and Deezer | Implemented; offline tests; Deezer and iTunes live-verified | Unsupported | `docs/MUSIC_API.md`. Spotify Web API and Apple Music API calls are tested with synthetic responses only, because no credentials were available. |
| Cross-provider matching and enrichment | Implemented; offline tests; Deezer→Apple live match verified | Unsupported | `MatchingTest` covers live, remaster, clean/explicit, cover, remix and duration cases. |
| Incremental playlist and library sync | Implemented; offline tests; Deezer playlist live-verified | Unsupported | Snapshot short-circuit, resume, retries, moves and duplicates (`SyncAuthTest`). |
| Audio formats, quality declarations and format selection | Implemented; offline tests | Unsupported | RFC 6381 parsing and Apple `audioVariants` traits. Spotify and Deezer declare no per-track quality. |
| DRM detection and playback decisions | Implemented; offline tests | Unsupported | HLS and DASH protection parsing. eXcor ships no protected-playback adapter, and none has been tested on a device. |
| OAuth (Spotify PKCE), token manager, SHA-256 | Implemented; offline tests with standard vectors | Unsupported | FIPS 180-4 and RFC 7636 known answers. |
| Select M4A, low-bitrate, or automatic audio | Supported and verified | Supported and verified | Kotlin `SelectionTest`; Swift selection tests |
| Decode player signature and `n` challenges | Implemented; live upstream behavior unverified | Implemented; live upstream behavior unverified | Synthetic player-script tests; live YouTube access is not a required CI check |
| Restrict returned playback URLs | Supported and verified | Supported and verified | URL-policy tests in each package |
| Host-owned session and PO-token integration | Interface implemented; provider behavior varies | Interface implemented; provider behavior varies | Host supplies session/token service; eXcor does not mint tokens |
| Catalog search and pagination | Experimental for Apple/iTunes and Spotify | Unsupported | Provider-bound offset tokens; synthetic pagination tests only |
| YouTube video metadata lookup | Unsupported by the active clean-room public API | Unsupported | Extractor returns stream information, not catalog metadata |
| YouTube Music songs, albums, artists, and search | Restricted / not implemented | Restricted / not implemented | No authorized music-specific catalog path has been verified |
| Android app integration | Not integrated | Not applicable | `app-core` still uses an older API shape and is not a root CI target |
| Public release readiness | Not ready | Not ready | Provider authorization/access, remaining providers, cross-platform verification, provenance, examples, and release packaging remain open |

## Platform verification

The root CI workflow has green Linux, Windows, and macOS jobs for this branch at commit `81a072f`. It checks Kotlin tests, Android library compilation, iOS simulator tests/framework artifacts, the Swift API check, Swift package tests, and an iOS Simulator build. Local Linux verification also passes `:jvmTest :jvmJar`, `:assembleAndroidMain`, and Kotlin compilation for iOS arm64, simulator arm64, and x64.

No provider is marked release-ready merely because a documented interface or CI check exists. Apple live behavior and Spotify's current terms/access remain unverified; Deezer and YouTube Music remain gated on verified authorized access paths.
