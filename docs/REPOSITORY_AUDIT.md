# Repository audit

Audit base: `main` at `e12ba98e6fd57c7d53ea8557197e7365d04da664` (2026-10-29). Branch `feature/catalog-metadata` adds an experimental metadata catalog prototype. This is a product and build inventory, not a legal opinion or release approval.

## Build boundary and layout

- The root Gradle project builds the draft KMP artifact `com.excor:eXcor:0.2.0` from `cleanroom-src/` only. It configures JVM, Android, and iOS targets.
- The Kotlin API at the audit base exposes YouTube audio extraction, audio candidates, session/token host interfaces, and stream state. Branch `feature/catalog-metadata` adds experimental catalog models and Apple/iTunes and Spotify adapters for search, URL lookup, album tracks, and artist albums. Deezer remains unsupported and YouTube Music remains restricted.
- `swift/` contains the separate `eXcorKit` Swift package. Its public actor currently provides YouTube extraction and audio-candidate operations; it has no music-catalog provider API.
- `src/`, `app-core/`, `android-app/`, and other preserved implementation/app areas are outside the clean-room root source-set mapping. `app-core` is not integrated with the root library API.
- `fixtures/cleanroom/` supplies synthetic inputs to the clean-room tests. Older fixtures are not inputs to the current root library build.

## Clean-room boundary

The prior user instruction prohibits opening or searching the preserved historical implementation and named legacy repositories. This audit therefore uses only project documentation, build configuration, the active `cleanroom-src/` implementation, and the `swift/` package. It does **not** establish the provenance or licensing of the excluded historical source, app modules, or older fixtures. Those remain quarantined and are not included in any clean-room claim until a separate authorized provenance review is possible.

The active Kotlin implementation's source record is in the clean-room section of the top-level `PROVENANCE.md`. The current build does not compile the preserved implementation into the root artifact.

## Verification snapshot

- Branch workflow run [40968192280](https://github.com/bxanedot/eXcor/actions/runs/40968192280) completed successfully on Linux, Windows, and macOS. It covers JVM tests/JAR, Android library compilation, iOS simulator tests and framework/XCFramework builds, Kotlin API type-checking from Swift, and native Swift package tests/iOS Simulator build.
- The branch also passes `:jvmTest :jvmJar`, `:assembleAndroidMain`, and iOS arm64/simulator-arm64/x64 Kotlin compilation locally with Temurin JDK 27. Linux cannot execute iOS simulator tests or link Apple frameworks; the hosted macOS job verified those checks.
- Live provider access has not been verified from this cloud environment. YouTube can return a bot-check response; live checks remain opt-in and no bypass is used.

## Current product gaps

1. The catalog models and capability API are experimental Kotlin work on `feature/catalog-metadata`; offline tests and cross-target CI pass.
2. Apple support is an experimental adapter to the documented iTunes Search/Lookup API; live behavior is unverified, and the adapter is not available from eXcorKit.
3. Spotify has a host-token-gated experimental adapter, but current API terms/access eligibility and live behavior are not verified; it is not available from eXcorKit.
4. Current official Deezer API access and terms have not been verified for this application; no adapter is present.
5. An authorized YouTube Music catalog path covering music-specific search, songs, albums, and artists has not been verified; private undocumented endpoints remain out of scope.
6. The app modules are not integrated against the new root API, and the excluded source's legal/provenance audit remains open.

The ordered work is tracked in [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md) and [`superpowers/plans/2026-10-29-metadata-catalog.md`](superpowers/plans/2026-10-29-metadata-catalog.md).
