# eXcor implementation status

Updated 2026-10-29 for `feature/catalog-metadata`, based on `main` at `e12ba98e6fd57c7d53ea8557197e7365d04da664`. This repository remains a draft SDK, not a finished multi-provider product or verified public release.

## Current deliverables

| Deliverable | State |
|---|---|
| Clean-room Kotlin YouTube audio engine | Implemented in `cleanroom-src/`; additive API and offline tests are present |
| Native Swift `eXcorKit` YouTube engine | Implemented under `swift/`; separate from the Kotlin framework |
| Linux / Windows / macOS CI | Green at current `main`; [workflow run 40917570550](https://github.com/bxanedot/eXcor/actions/runs/40917570550) |
| Android app integration | Not complete; preserved app modules are not integrated with the root clean-room API |
| Shared catalog models and provider capability API | Experimental Kotlin API on `feature/catalog-metadata`; JVM suite, JAR, Android packaging, and iOS Kotlin compilation pass locally |
| Apple/iTunes metadata | Experimental Kotlin search, URL lookup, album tracks, artist albums; uses the public iTunes Search/Lookup endpoints, not the full Apple Music API; live and cross-target behavior not verified |
| Spotify metadata | Experimental Kotlin adapter; host supplies access tokens; current provider terms/API access and cross-target behavior are unverified |
| Deezer metadata | Not implemented; current access eligibility and terms not yet verified |
| YouTube Music catalog metadata | Restricted / not implemented; an authorized music-specific catalog path is unresolved |
| Full source and fixture provenance | Open for quarantined historical source and older fixtures; active clean-room sources are logged in `PROVENANCE.md` |

## Environment and local verification

The project requires a JDK 27 toolchain. This cloud executor originally provided JDK 21 only; a pinned Temurin 27.0.1 build was installed outside the repository after its published SHA-256 was verified. Gradle also needs the configured HTTP proxy and the system Java CA store to resolve dependencies. The full JVM suite and JAR build, Android library packaging, and Kotlin compilation for iOS arm64, simulator arm64, and x64 pass locally. Hosted CI is green only for the base commit, not this working tree.

Linux cannot run the iOS simulator or link Apple frameworks. Those checks must continue on the macOS workflow. The Android SDK is installed outside the repository at `/workspace/.toolchains/android-sdk`; its setup is in the saved environment draft. YouTube live access is not established by CI and must remain opt-in.

## Remaining release gates

- Run updated catalog tests and framework links in hosted CI; JVM, Android, and iOS Kotlin compilation pass locally on this branch.
- Verify current Spotify access terms for the token-gated prototype; verify whether Deezer offers an eligible, permitted API before coding; keep YouTube Music restricted until an authorized music catalog path exists.
- Complete Apple/iTunes provider documentation, provenance, test coverage, and current-platform verification; do not describe it as full Apple Music API support.
- Verify session isolation, cancellation, response bounds, URL validation, and secret redaction for catalog calls.
- Integrate the Android consumer against the supported public API.
- Complete clean-room/provenance and fixture-rights review for excluded repository content before making a whole-repository legal claim.
- Add examples, documentation, public API compatibility checks, release metadata, and reproducible publication workflows for verified targets.

The detailed sequence is [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md) and [`superpowers/plans/2026-10-29-metadata-catalog.md`](superpowers/plans/2026-10-29-metadata-catalog.md).
