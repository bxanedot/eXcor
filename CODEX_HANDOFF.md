# Codex handoff — eXcor shared engine, Android + desktop + iOS

Date: 2026-10-28
Work branch: `feature/music-metadata-providers`
Base: `main`, originally `69600bf486b28c6fb256e423bec43c7f73f1efe9`

## Mission

Deliver **one multiplatform music application** across Android, Windows,
macOS, Linux, iPhone and iPad, backed by the same clean-room Kotlin
Multiplatform VyonEx library, **not** three rewritten extractor implementations.
Ship platform-native UI, playback, background tasks, storage and packaging
around a single shared `app-core` domain/application layer.

Users explicitly requested metadata **and images** from Spotify, Deezer,
Apple Music, YouTube Music, SoundCloud and compatible future catalogs.
Spotify/Deezer support is metadata-only; it must NEVER become a stream ripping
or DRM circumvention component.

## What this branch adds

- `src/commonMain/kotlin/com/vyonex/MusicCatalog.kt`: common metadata, image
  and credit model, strict links, Spotify OAuth token input and provider API.
- `src/commonMain/kotlin/com/vyonex/internal/MusicCatalog.kt`: Spotify
  oEmbed previews, authenticated Spotify track/album/artist lookup/search,
  Deezer track/album/artist/playlist public lookup and catalog search.
- `src/commonTest/kotlin/com/vyonex/MusicCatalogTest.kt`: synthetic JSON
  fixture tests including links, images, invalid tokens, missing data and
  provider error redaction.
- `src/jvmCommonMain/kotlin/com/vyonex/VyonEx.kt` and
  `src/iosMain/kotlin/com/vyonex/VyonEx.kt` expose `vyonEx.musicCatalog`
  on all targets; `apicheck/ApiCheck.swift` references it.
- `android-app/MainActivity.kt`: Spotify/Deezer link inspection, images
  and metadata inside the existing Android app.
- `desktop-app/`: initial JVM/Swing catalog browser with image rendering,
  Deezer/Spotify/YouTube Music searches and external source links.
- `ios-app/`: initial SwiftUI catalog browser; XcodeGen project uses the
  existing `VyonEx.xcframework`.
- `settings.gradle.kts`, CI workflow, README and PROVENANCE updates.

**Important: these shells are initial implementations, not finished
cross-platform feature parity.** Android already has playback/downloads.
Desktop and iOS can discover metadata; they do not yet have complete
native playback/download engines. This limitation must remain explicit
until implementation and real-device tests pass.

## First actions (P0)

1. Run CI and fix *actual* Kotlin, Gradle, Swift/Xcode, or API-compatibility
   failures without weakening checks, and push fixes to this branch.
2. Check the Spotify and Deezer contracts against current documented API
   behavior. The Spotify Search API documented limit is now **10 per type**;
   keyless oEmbed has only basic title/thumbnail data. Unsupported access
   must report honestly, never manufacture metadata.
3. Add unit tests for pagination, malformed catalog JSON, duplicate IDs,
   invalid artwork URLs, request cancellation, 429/retry hints, 401 expired
   token, and Deezer HTTP-200 error bodies.
4. Verify Android catalog image parsing, accessibility and lifecycle behavior
   on API 26 and API 36 emulators, with no main-thread networking.
5. Verify unsigned iOS simulator app compilation and launch with XcodeGen
   and native SwiftUI. The API may need Kotlin/Native name/boxing adjustments.
6. Verify desktop distribution compilation/launch on Windows, macOS, Linux.
   The desktop shell currently relies only on the JDK Swing standard library.

## Cross-platform parity roadmap (P1)

- **Shared logic:** migrate all navigation, domain search, catalog selection,
  queue, playback events, saved library and download states into common
  `app-core` APIs. Use immutable models, cancellation-aware tasks, per-request
  generation IDs, and platform abstractions. Avoid cloning business logic.
- **Android:** retain Jetpack Compose + Media3; refactor `MainActivity`
  into Compose screens/ViewModels; add durable download and queue state;
  fix state-restoration/autoplay and stale 403 handling.
- **Desktop:** add native playback through a verified permissive-license
  implementation supporting AAC/M4A, Opus/WebM, HLS/DASH plus per-stream
  HTTP headers; use native media keys, taskbar controls, file downloads,
  high-DPI/multimonitor behavior and standard installers. Do not bundle
  GPL or unverified codecs/extractor code. If no safe implementation is
  available, document the constraint rather than fake playback.
- **iOS:** link the Kotlin shared application-core framework if needed,
  implement AVPlayer playback with supported HTTP headers/session policy,
  lock-screen media commands, audio interruptions, background audio,
  native downloads and application state restoration. Do not use
  undocumented/private AVFoundation keys. Ship and test on real devices
  once code-signing is available.
- **Parity screens:** Home, search, provider metadata, artists, albums,
  playlists, media player, queue, downloads, settings and artwork views,
  with responsive layouts for phones, tablets, desktop and iPad.
- **Metadata/image policy:** canonical identity IDs (ISRC/UPC when present),
  original images, attribution, cross-provider artwork credits, unknown
  values preserved as unknown. Do not equate tracks across services
  solely by matching titles. No modified Spotify artwork and no Spotify
  content downloads, DRM bypass or Spotify-content model training.
- **Provider expansion:** Apple Music and SoundCloud already have adapters.
  Add providers only using independently documented authorized endpoints,
  license-safe implementations and tests, with explicit feature availability.

## Build and verification commands

Run using JDK 27 toolchain and the environment settings from
`.github/workflows/ci.yml`:

```sh
./gradlew jvmTest jvmJar assembleAndroidMain :app-core:jvmTest
./gradlew :android-app:assembleDebug :android-app:lintDebug
./gradlew :desktop-app:distZip
# On macOS with Xcode:
./gradlew iosSimulatorArm64Test assembleVyonExReleaseXCFramework
xcodegen generate --spec ios-app/project.yml --project ios-app/VyonExIOS.xcodeproj
xcodebuild -project ios-app/VyonExIOS.xcodeproj -scheme VyonExIOS \
  -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

Use emulator tests for real playback/service interactions, not only process
launch. For final desktop releases, verify JDK 27 and packaging per OS.
iOS signing for device/App Store requires owner-supplied Apple credentials;
never invent certificates or put secrets in the repository.

## Strict clean-room and security constraints

1. **Read `BRIEF.md` and `PROVENANCE.md` first.** The old GPL-based
   VyonEx/Vyon repositories and listed local sources remain strictly
   off-limits. Do not search, clone, diff, browse, or read their history.
2. Do not inspect GPL/LGPL/AGPL extractor implementations for algorithms.
   Allowed documentation/source and license requirements are exactly those
   in `BRIEF.md`. Verify and record any new dependency in provenance.
3. New substantial algorithms require source URL, commit or access date,
   license and implementation mapping in `PROVENANCE.md`.
4. Never log or store OAuth tokens, signatures, cookies, signed stream
   URLs, or credentials in fixtures/issue reports. Spotify tokens are
   caller-supplied and transient; implement proper PKCE and secure keychain
   storage before production authorization.
5. Enforce provider terms separately from open-source licenses. Artwork
   requires proper source attribution and may not be freely reusable.
6. Work in reviewable PRs, keep CI green, and document tests and remaining
   platform-specific limitations. Do not merge incomplete platform parity
   as if it were a production release.

## Definition of done

- Android, desktop (Windows/macOS/Linux) and iOS (iPhone/iPad) launch and
  provide the same core search/catalog metadata/artwork UI from **one**
  shared engine, verified by CI and device/simulator smoke tests.
- Real user actions are exercised with deterministic playback/queue tests
  across platform adapters, not just compilation.
- Provider errors, outages, permissions and no-data states are handled
  without crashes, leaked tokens or misleading fallback data.
- Every dependency and algorithm has a provenance record; no forbidden
  legacy code was accessed. Release artifacts and unsigned/signed outputs
  are clearly distinguished.
