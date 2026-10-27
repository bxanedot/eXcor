# VyonEx

Kotlin Multiplatform library (JVM, iOS) that turns a YouTube video ID into directly playable
audio or video URLs plus the headers needed to fetch them, and reads the video's metadata.
Apache-2.0.

```kotlin
val vyonEx = VyonEx(host)          // host: your VyonExHost
vyonEx.init()                      // iOS: initialize(); warms up ~2 s later in the background
val stream = vyonEx.extract("dQw4w9WgXcQ", maxKbps = 160)
// stream.url + stream.headers -> ranged GET; on HTTP 403 call vyonEx.onRefused(stream.url)

val video = vyonEx.extractVideo("dQw4w9WgXcQ", maxHeight = 1080)
// video.url is video only; play video.audio alongside it. video.audio == null: a combined format.

val info = vyonEx.metadata("dQw4w9WgXcQ")
// title, author, channel, duration, views, likes, full description, keywords, category,
// publish/upload dates, thumbnails, live/private/unlisted/family-safe flags, countries, captions
```

iOS apps use the same library through `VyonEx.xcframework`:

```swift
import VyonEx

let vyonEx = VyonEx(host: myHost)      // myHost: VyonExHost
vyonEx.initialize()
let video = try await vyonEx.extractVideo(videoId: "dQw4w9WgXcQ", maxHeight: 1080, skipClients: [], requireAvc: true)
let info = try await vyonEx.metadata(videoId: "dQw4w9WgXcQ")   // info?.description_ is the description
```

Swift sees no Kotlin default arguments, so it passes every parameter. Kotlin/Native renames
`description` to `description_` because `NSObject` already has a `description`.

## How it works

1. Reads `host.session()` (cookie, visitor data, `gl`, `hl`) on every call.
2. Asks the player API with up to three client identities, in yt-dlp's current default order:
   `TV_SAMSUNG` (TVHTML5), `VISIONOS`, then `WEB` (only with a PO token from `host.tokenProvider`).
   Logged-in sessions skip `VISIONOS`, which does not take cookies.
3. Decodes signature and `n` by running the player script's own functions in QuickJS, through
   yt-dlp's EJS solver (vendored in `ejs/`). The prepared player is cached per player version
   (JVM: `filesDir/vyonex-players`, three newest; iOS: memory).
4. Audio: picks a format (`requireM4a`, `maxKbps <= 64`, or AUTO). Video: the tallest format up to
   `maxHeight` (the smallest if none fits), then frame rate, then H.264 over VP9 over AV1, then
   bitrate; `requireAvc` keeps only H.264 video and AAC audio (what AVPlayer plays). Video-only
   formats come with the best audio up to 160 kbps.
5. Allows only `https://*.googlevideo.com|youtube.com/videoplayback` URLs, never SABR or HLS, and
   checks every returned URL answers a one-byte ranged GET with the returned headers; a refused
   identity is excluded for 10 minutes and the next one is tried.
6. iOS `extract` falls back to the watch page; `audioCandidates` lists the watch page's audio.
7. `metadata` asks `WEB` (which adds the microformat: likes, category, dates, countries), then
   `VISIONOS`; it returns null when neither reports video details.

## Build and test

Gradle 9.8.2 (wrapper), Kotlin 2.4.21, JDK 27 toolchain, JVM target 26.

```sh
./gradlew jvmTest jvmJar                      # any OS
./gradlew iosSimulatorArm64Test assembleVyonExReleaseXCFramework   # macOS
VYONEX_LIVE=1 ./gradlew jvmTest --tests com.vyonex.LiveTest --rerun   # live, opt-in
```

Offline tests read the captured responses in `fixtures/` (see `fixtures/README.md`).
`apicheck/ApiCheck.swift` is type-checked against the XCFramework in CI.

## Files

- `src/commonMain`: public API and the engine; `src/jvmMain`, `src/iosMain`: `VyonEx`, `VyonExHost`, transport, disk cache.
- `ejs/`: the vendored yt-dlp/ejs solver, compiled into the library.
- `PROVENANCE.md`: every source used. `QUESTIONS.md`: open questions. `NOTICE`: third-party notices.
