# eXcor

[![eXcor readiness score](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fbxanedot%2FeXcor%2Fautomation%2Fexcor-competitiveness%2Fscore%2Fbadge.json)](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md)

**Live competitiveness tracker:** [0–100 score and missing capabilities](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md) · [methodology](docs/COMPETITIVENESS_CHECKER.md). The score is evidence-weighted readiness, **not** a live-video playback success rate.

**eXcor** is an Apache-2.0 Kotlin Multiplatform library for resolving directly fetchable YouTube audio streams from video IDs. This clean-room implementation is a draft SDK, not a production-ready release.

The current library exposes package `com.excor`, Gradle coordinates `com.excor:eXcor:0.2.0`, and an iOS XCFramework named `eXcor`. The implementation is built from `cleanroom-src/`; it does not use the older implementation source retained in this repository.

## Current scope

The KMP library provides audio format selection, player-response and player-script handling, URL validation, host-supplied session and PO-token interfaces, remembered stream headers, refusal tracking, and player-data caching. It returns a direct audio stream only when the URL passes the HTTPS YouTube/GoogleVideo `/videoplayback` allowlist. It does not mint PO tokens.

`Excor.music` puts Spotify, Apple Music and Deezer metadata behind one API, along with cross-provider matching, incremental playlist and library sync, audio-variant and DRM reporting, and authorized-playback decisions. Deezer and Apple's keyless endpoints are verified live; Spotify and the Apple Music API are tested offline only. See [music providers](docs/MUSIC_PROVIDERS.md) and [limitations](docs/MUSIC_LIMITATIONS.md). YouTube Music search, artists, playlists and radio are available through `Excor` (see below); `Excor.music` itself does not cover YouTube Music. The separate `eXcorKit` Swift package does not yet expose catalog APIs. The historical implementation in `src/` remains in the repository pending a separate provenance audit; nothing compiles it any more. The earlier music-player apps were removed on 2026-11-03 (owner's decision) in favor of the preview apps below, which use this library. See [the audit](docs/REPOSITORY_AUDIT.md) and [implementation status](docs/IMPLEMENTATION_STATUS.md).

The experimental API and its authorization, paging, and provider limitations are documented in [Catalog metadata](docs/CATALOG.md).

## Installation

No release is published to a remote repository yet. Build and install the library into your local Maven repository from this checkout (JDK 27):

```sh
./gradlew publishToMavenLocal
```

Then, in your app's Gradle build:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
    google()
}

dependencies {
    implementation("com.excor:eXcor:0.2.0")
}
```

Gradle picks the JVM, Android or iOS variant from the published Kotlin Multiplatform metadata. The JVM artifact is JDK 26 bytecode, so JVM apps need JDK 26 or newer (and `jvmTarget` 26). The Android artifact is Java 17 bytecode with minSdk 26. [`samples/jvm-quickstart`](samples/jvm-quickstart) is a standalone app built exactly this way; CI publishes the library locally and builds it on every push.

## Quick start (JVM and Android)

`excorDefaultHost()` gives a ready host with a signed-out session, an in-memory cache and the system clock:

```kotlin
val excor = Excor(excorDefaultHost(region = "US", language = "en"))

val video = excor.videoInfo("dQw4w9WgXcQ")                 // title, channel, duration, views, playability
val results = excor.search("daft punk")                       // videos, Shorts, channels, playlists, filters
val next = excor.search("daft punk", results?.continuation)  // the next page
val channel = excor.channel("UC_kRDKYrUlrbtrSiyu5Tflg", tab = ExcorYouTubeChannelTab.PLAYLISTS)
val comments = excor.comments("dQw4w9WgXcQ", newestFirst = true)

val music = excor.musicSearch("daft punk")                    // typed: songs, videos, artists, albums, playlists…
val artist = excor.musicArtist("UC_kRDKYrUlrbtrSiyu5Tflg")
val radio = excor.musicRadio(videoId = "FGBhQbmPwH8")         // pass radio.continuation back for more
```

Every call is `suspend`:
- It returns null when YouTube answered, but not with the expected page (an unavailable or removed item).
- It throws `ExcorRequestException` when the request itself kept failing after up to three attempts. `failure` says whether YouTube was rate limiting (`RATE_LIMITED`), had server errors (`SERVER_ERROR`) or couldn't be reached (`NETWORK`).
- Invalid arguments throw `IllegalArgumentException`.

 [Feature matrix](docs/FEATURE_MATRIX.md) lists what is verified live and what is blocked.

## Streams

The host implements `ExcorHost` (or uses `excorDefaultHost`) to provide the HTTP client, session, clock, logging, cache settings, and optional PO-token provider.

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

`maxKbps` selects low-bitrate audio at 64 kbps or below; above that threshold it does not cap the selected bitrate. `requireM4a` restricts selection to AAC/M4A.

### Video

```kotlin
val streams = excor.extractVideo(
    videoId = "dQw4w9WgXcQ",
    maxHeight = 1080,                                   // the tallest picture allowed
    codecs = setOf(ExcorVideoCodec.H264, ExcorVideoCodec.VP9),
)
if (streams != null) {
    val video = streams.video   // url, headers, width x height, fps, codec, initRange/indexRange
    val audio = streams.audio   // null only when the video is progressive (carries its own sound)
}
```

The best picture under `maxHeight` wins, then the higher frame rate, then the most widely decodable codec (H.264, VP9, AV1). The audio comes from the same response, in the matching container where one exists (M4A with MP4 video, Opus/WebM with WebM video), so the two can be played together or muxed without re-encoding. Both tracks pass the playback probe before they are returned. If the response has no playable separate audio, a progressive format is used instead. Segmented ("OTF") formats are not selected.

`excor.formats(videoId)` lists every whole-file audio and video format one client offers (itag, codec, size, frame rate, bitrate, length, byte ranges), for download choices. All of them are fetched with `list.headers`. One representative format is probed, since a response's URLs are signed together. If a listed URL is refused, call `exclude(videoId, list.profileId)` and ask again. A `TokenProvider` is an integration boundary: applications may supply tokens they are authorized to obtain, while eXcor never creates them.

## Preview apps

Two small apps for trying the library by hand, for the platforms the owner uses (Android and Windows); the library itself also targets the JVM and iOS. They are built on the clean-room library and are not published. Where YouTube's bot check refuses every signed-out client on a network, they say so in the status line; they don't get around it.

- **Android** (`preview-android`): a debug APK, Android 8 and later. Search YouTube Music (the default) or YouTube, or paste or share a link. YouTube Music searches show its own filter chips for the query (Songs, Videos, Albums, Artists, Community playlists… as it offers them) and typed results; albums and playlists open as track lists, artists as their shelves, and Back returns. A song or video shows its details, plays its audio or its video (picture and sound from separate tracks, merged by ExoPlayer), and lists its formats. Download audio, Download video and tapping a listed format save to Downloads/eXcor; video is joined with its sound on the phone without re-encoding (H.264 with AAC as MP4; VP9 with Opus as WebM, from Android 10). The screen also shows the library's log.
- **Windows** (`preview-desktop`): an app folder with `eXcor Preview.exe` and its own Java runtime, so nothing needs to be installed. It has the same YouTube Music and YouTube search, filters, album, playlist and artist pages, and details. Play audio and Play video download the media and open it in the system's player; Save format saves any listed format. Video above the qualities YouTube serves as one file needs [ffmpeg](https://ffmpeg.org) on the PATH to join the separate tracks. It also runs on macOS and Linux.

CI builds both on every push: download `excor-preview-apk` and `excor-preview-windows` from a run's artifacts. To build them locally:

```bash
./gradlew :preview-android:assembleDebug   # preview-android/build/outputs/apk/debug/preview-android-debug.apk
./gradlew :preview-desktop:packageApp      # preview-desktop/build/app/eXcor Preview/ (the .exe on Windows)
```

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
