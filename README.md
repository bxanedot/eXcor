# eXcor

[![eXcor evidence readiness](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fbxanedot%2FeXcor%2Fautomation%2Fexcor-competitiveness%2Fscore%2Fbadge.json)](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md)

**Live readiness report:** [current score, missing evidence, release gates and CI state](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md) · [scoring method](docs/COMPETITIVENESS_CHECKER.md) · [automated report runs](https://github.com/bxanedot/eXcor/actions/workflows/competitiveness-score.yml). The score is evidence-weighted engineering readiness. It is not a playback success rate or a measured head-to-head score against InnerTubeX.

**eXcor** is an Apache-2.0 Kotlin Multiplatform library for resolving YouTube audio streams and, experimentally, HLS manifests from video IDs. This clean-room implementation is a draft SDK, not a production-ready release.

## eXcor compared with InnerTubeX

**Evidence snapshot: 2026-11-12.** The latest published InnerTubeX release found in GitHub is [v0.11.6](https://github.com/MetrolistGroup/innertubex/releases/tag/v0.11.6) (2026-11-07). Its [README](https://github.com/MetrolistGroup/innertubex) documents Kotlin Multiplatform targets, standard InnerTube operations and an extraction stack with direct, HLS and SABR/UMP transports. The README's JitPack example still pins v0.11.5; this comparison does not assume that v0.11.6 resolves through JitPack. eXcor's evidence is linked to its [feature matrix](docs/FEATURE_MATRIX.md), [competitor matrix](docs/COMPETITOR_MATRIX.md) and [release gates](docs/release-gates.json).

| Area | eXcor | InnerTubeX | What the evidence says |
|---|---|---|---|
| Metadata and music catalogs | YouTube and YouTube Music metadata, search, channels, playlists, comments, chapters and radio; Spotify, Apple Music and Deezer metadata and matching. The listed YouTube flows have dated live checks. | README documents search, browse, account, playlist and player operations, plus channel discovery and music uploads. | eXcor has broader documented catalog coverage; this does not establish that InnerTubeX lacks features absent from its README. |
| Direct audio and video | One eligible video has full audio/video decode, mux and range-read evidence. Other videos and networks are inconsistent. | README documents direct extraction and bounded media probing. | Both document direct extraction; the available evidence does not support a representative reliability comparison. |
| HLS, DASH and SABR/UMP | HLS is an experimental manifest handoff with no live playback result. DASH-manifest and SABR/UMP playback are not implemented. | README documents HLS and seekable SABR/UMP audio and video, including video representation selection. | **Largest parity gap:** eXcor has no SABR/UMP transport and has not verified live HLS playback. InnerTubeX's capabilities here are upstream documentation claims, not an independent benchmark. |
| Player cipher | Signature and `n` solving are implemented; successful ciphered media decode and seek remain unverified. | README describes Kotlin cipher handling via QuackJS. The latest v0.11.6 release fixes iOS cipher work on large-stack threads. | eXcor does not yet have verified ciphered-playback parity. |
| Reliability and security | URL allowlisting, bounded responses, session-generation invalidation and diagnostic redaction are implemented; live reliability and the full release-security audit remain partial. | README documents bounded bodies and protocol state, strict media URL validation and isolated sessions; the [changelog](https://github.com/MetrolistGroup/innertubex/blob/main/CHANGELOG.md) records additional timeout and redirect hardening. | The designs overlap, but there is no common independent stress-test result. |
| Installation and license | Apache-2.0; `com.excor:eXcor:0.2.0` currently installs from `mavenLocal`; no remote release is published. | GPL-3.0; the README provides a JitPack dependency example. | InnerTubeX has a documented remote installation path; eXcor still needs an authorized public release. |

**Current assessment:** eXcor already offers useful metadata breadth and has direct-playback evidence, but it is not yet a playback-parity alternative to InnerTubeX. SABR/UMP, live HLS, ciphered-stream playback and a public release remain open gates. The live readiness report tracks eXcor's evidence against the [100-point rubric](docs/competitiveness-evidence.json); its score is not a percentage of InnerTubeX features or an apples-to-apples performance result.

The current library exposes package `com.excor`, Gradle coordinates `com.excor:eXcor:0.2.0`, and an iOS XCFramework named `eXcor`. The active implementation is built from `cleanroom/`; the older, uncompiled source is preserved under `legacy/`.

## Current scope

The KMP library provides audio and video format selection, player-response and player-script handling, URL validation, host-supplied session and PO-token interfaces, remembered stream headers, refusal tracking, and player-data caching. Direct media URLs must pass the HTTPS YouTube/GoogleVideo `/videoplayback` allowlist. Experimental HLS extraction validates a GoogleVideo master-playlist URL, its bounded response and redirects, then returns it to a host media player; that player fetches child playlists and segments. HLS live playback has not been verified. eXcor does not mint PO tokens.

`Excor.music` puts Spotify, Apple Music and Deezer metadata behind one API, along with cross-provider matching, incremental playlist and library sync, audio-variant and DRM reporting, and authorized-playback decisions. Deezer and Apple's keyless endpoints are verified live; Spotify and the Apple Music API are tested offline only. See [music providers](docs/MUSIC_PROVIDERS.md) and [limitations](docs/MUSIC_LIMITATIONS.md). YouTube Music search, artists, playlists and radio are available through `Excor` (see below); `Excor.music` itself does not cover YouTube Music. The separate `eXcorKit` Swift package does not yet expose catalog APIs. The historical implementation in `legacy/src/` remains in the repository pending a separate provenance audit; nothing compiles it any more. The earlier music-player apps were removed on 2026-11-03 (owner's decision) in favor of the preview apps below, which use this library. See [the audit](docs/REPOSITORY_AUDIT.md) and [implementation status](docs/IMPLEMENTATION_STATUS.md).

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

[Feature matrix](docs/FEATURE_MATRIX.md) lists what is verified live and what is blocked; [error handling](docs/ERROR_HANDLING.md) explains failure results.

## Playback diagnostics (JVM sample)

The standalone quickstart includes an opt-in, signed-out direct-audio playback runner. Publish the library locally, then run it with `ffplay` installed:

```sh
./gradlew publishToMavenLocal
./gradlew -p samples/jvm-quickstart run --args="--playback VIDEO_ID --seconds 90"
```

Use Left/Right Arrow in ffplay to try backward/forward seeking, then press `q`. The runner prints request/status counts, bytes relayed, and observed Range offsets. Offset observations alone do not prove that a seek key was pressed. Add `--full-decode` to probe duration and decode the whole audio stream with `ffprobe` and `ffmpeg` (bounded to 30 minutes). Each invocation shares a 256 MiB media-body budget across playback, probe, and decode; reaching the cap terminates the child process and reports `byte_limit`.

The player receives only a random-path loopback URL. The signed media URL and allowed request headers stay in the quickstart process and are not printed. The runner covers direct whole-file audio only; it does not add HLS, DASH-manifest, or SABR/UMP playback. YouTube access refusals are reported as such.

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

### HLS manifest handoff (experimental)

`extractHls` returns an HLS manifest URL only when YouTube offers one and the bounded manifest probe succeeds:

```kotlin
val hls = excor.extractHls(videoId)
if (hls != null) {
    // Give hls.url and hls.headers to a host media player with HLS support.
    // Keep the signed URL and any host-provided PO token private.
}
```

The result includes `isLive`, client/profile identifiers and the media headers. The host player handles child playlists and media segments. eXcor validates the initial URL and manifest redirect chain; it does not proxy or validate each child request. HLS has synthetic offline coverage only so far; no live playback result is claimed. DASH and SABR/UMP playback are not implemented.

## Preview apps

Two small apps for trying the library by hand, for the platforms the owner uses (Android and Windows); the library itself also targets the JVM and iOS. They are built on the clean-room library and are not published. The Android preview can send an experimental `extractHls` result to Media3's HLS player when YouTube offers a manifest; the host player handles child playlists and segments. No live HLS playback has been verified. Where YouTube refuses every signed-out client on a network, the apps report that result; they don't get around it.

- **Android** (`preview-android`): a debug APK, Android 8 and later. Search YouTube Music (the default) or YouTube, or paste or share a link. YouTube Music searches show its own filter chips for the query (Songs, Videos, Albums, Artists, Community playlists… as it offers them) and typed results; albums and playlists open as track lists, artists as their shelves, and Back returns. A song or video shows its details, plays audio or video (picture and sound from separate tracks, merged by ExoPlayer), and lists its formats. **Try HLS (experimental)** sends a manifest from `extractHls` to Media3 HLS when a client offers one; child playlists and segments are fetched by the player. Live HLS playback has not been verified. Download audio, Download video and tapping a listed format save to Downloads/eXcor; video is joined with its sound on the phone without re-encoding (H.264 with AAC as MP4; VP9 with Opus as WebM, from Android 10). The screen also shows the library's log.
- **Windows** (`preview-desktop`): an app folder with `eXcor Preview.exe` and its own Java runtime, so nothing needs to be installed. It has the same YouTube Music and YouTube search, filters, album, playlist and artist pages, and details. Play audio and Play video download the media and open it in the system's player; Save format saves any listed format. Video above the qualities YouTube serves as one file needs [ffmpeg](https://ffmpeg.org) on the PATH to join the separate tracks. It also runs on macOS and Linux.

CI builds both on every push: download `excor-preview-apk` and `excor-preview-windows` from a run's artifacts. To build them locally:

```bash
./gradlew :preview-android:assembleDebug   # platforms/preview/android/build/outputs/apk/debug/preview-android-debug.apk
./gradlew :preview-desktop:packageApp      # platforms/preview/desktop/build/app/eXcor Preview/ (the .exe on Windows)
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

## Repository layout

- `cleanroom/` — active clean-room Kotlin Multiplatform library.
- `legacy/` — preserved historical implementation, excluded from the build.
- [`platforms/`](platforms/README.md) — preview apps and the native Swift package.
- `docs/maintainers/` — original brief, open questions, benchmark notes, and dated handoffs.

## Repository guidance

- [Clean-room policy](docs/CLEAN_ROOM.md) and [source provenance](PROVENANCE.md)
- [Architecture](docs/ARCHITECTURE.md) and [feature matrix](docs/FEATURE_MATRIX.md)
- [Repository audit](docs/REPOSITORY_AUDIT.md) and [implementation plan](docs/IMPLEMENTATION_PLAN.md)
- [Dependency licenses](docs/DEPENDENCY_LICENSES.md), [third-party notices](docs/THIRD_PARTY_NOTICES.md), [security policy](SECURITY.md), and [open questions](docs/maintainers/QUESTIONS.md)

The approved names are eXcor for Kotlin and eXcorKit for the separate Swift package; [BRIEF.md](docs/maintainers/BRIEF.md) uses those names throughout its active API contract. Historical app and implementation files now carry the eXcor naming, but their older API shapes remain unintegrated with the clean-room library.

## Native Swift package: eXcorKit

The separate Swift Package Manager product in [`platforms/swift/`](platforms/swift/) provides the section 8 API for iOS 15 and macOS 12. It uses Foundation and JavaScriptCore, with no third-party package dependencies. On macOS, run `swift test --package-path platforms/swift`; GitHub Actions also builds the package for the iOS Simulator.
