# eXcor

**eXcor** is an Apache-2.0 Kotlin Multiplatform library for resolving directly fetchable YouTube audio streams from video IDs. This clean-room implementation is a draft SDK, not a production-ready release.

The current library exposes package `com.excor`, Gradle coordinates `com.excor:eXcor:0.2.0`, and an iOS XCFramework named `eXcor`. The implementation is built from `cleanroom-src/`; it does not use the older implementation source retained in this repository.

## Current scope

The KMP library provides audio format selection, player-response and player-script handling, URL validation, host-supplied session and PO-token interfaces, remembered stream headers, refusal tracking, and player-data caching. It returns a direct audio stream only when the URL passes the HTTPS YouTube/GoogleVideo `/videoplayback` allowlist. It does not mint PO tokens.

The current clean-room API does not provide catalog search, playlists, downloads, video formats, or other media providers. The historical app and implementation folders remain in the repository pending a separate provenance and integration audit; they are not compiled by the clean-room root build. In particular, `app-core` still references the older API and does not currently compile against the new root library. See [the audit](docs/REPOSITORY_AUDIT.md) and [implementation status](docs/IMPLEMENTATION_STATUS.md).

## Usage

The host implements `ExcorHost` to provide the HTTP client, session, clock, logging, cache settings, and optional PO-token provider.

```kotlin
val excor = Excor(host)
excor.init() // optional asynchronous warm-up

val stream = excor.extract(
    videoId = "dQw4w9WgXcQ",
    maxKbps = 128,
)

if (stream != null) {
    // Fetch stream.url with stream.headers. Keep the returned headers private.
    val headers = excor.headersFor(stream.url)

    // After a host player reports HTTP 403:
    val refusedVideoId = excor.onRefused(stream.url)
}
```

`maxKbps` selects low-bitrate audio at 64 kbps or below; above that threshold it does not cap the selected bitrate. `requireM4a` restricts selection to AAC/M4A. A `TokenProvider` is an integration boundary: applications may supply tokens they are authorized to obtain, while eXcor never creates them.

## Build and test

Use the Gradle wrapper and JDK 27. The Kotlin, Ktor, coroutine, serialization and QuickJS-KT versions are pinned in the build.

```sh
./gradlew :jvmTest :jvmJar :assembleAndroidMain
```

The macOS workflow additionally runs iOS simulator tests, links the iOS frameworks, builds the XCFramework, type-checks the published Kotlin API from Swift, and tests/builds the native `eXcorKit` Swift package. Live tests are opt-in and should run only where YouTube access is permitted:

```sh
EXCOR_LIVE=1 ./gradlew :jvmTest --tests com.excor.LivePlaybackTest --rerun
```

Offline clean-room tests use synthetic fixtures under `fixtures/cleanroom/`. The older captured fixtures at `fixtures/` are not inputs to the clean-room implementation. GitHub Actions and current verification limits are recorded in [implementation status](docs/IMPLEMENTATION_STATUS.md).

## Repository guidance

- [Clean-room policy](docs/CLEAN_ROOM.md) and [source provenance](PROVENANCE.md)
- [Architecture](docs/ARCHITECTURE.md) and [feature matrix](docs/FEATURE_MATRIX.md)
- [Repository audit](docs/REPOSITORY_AUDIT.md) and [implementation plan](docs/IMPLEMENTATION_PLAN.md)
- [Dependency licenses](docs/DEPENDENCY_LICENSES.md), [third-party notices](docs/THIRD_PARTY_NOTICES.md), and [open questions](QUESTIONS.md)

The approved names are eXcor for Kotlin and eXcorKit for the separate Swift package; [BRIEF.md](BRIEF.md) uses those names throughout its active API contract. Historical app and implementation files now carry the eXcor naming, but their older API shapes remain unintegrated with the clean-room library.

## Native Swift package: eXcorKit

The separate Swift Package Manager product in [`swift/`](swift/) provides the section 8 API for iOS 15 and macOS 12. It uses Foundation and JavaScriptCore, with no third-party package dependencies. On macOS, run `swift test --package-path swift`; GitHub Actions also builds the package for the iOS Simulator.
