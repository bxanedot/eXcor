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

val ref = vyonEx.parse("https://music.youtube.com/playlist?list=OLAK5uy_…")   // no network
// VyonExRef.Video(videoId, playlistId, startSeconds), Playlist, Album (MPREb_…) or Mix (RD…); null otherwise

val info = vyonEx.metadata("dQw4w9WgXcQ")
// title, author, channel, duration, views, likes, full description, keywords, category,
// publish/upload dates, thumbnails, live status, private/unlisted/family-safe flags, countries, captions
// and, for YouTube Music uploads, info.music: track, artists, album, release year
```

Android apps use the same API as the JVM (`VyonExHost` with your `OkHttpClient` and, for the player
cache, `context.filesDir`). The library needs `minSdk` 26 and an app compiled against API 37 or
newer, which OkHttp 5.5 requires.

Downloads (every quality YouTube offers, DRM-free formats only):

```kotlin
val formats = vyonEx.formats("dQw4w9WgXcQ")              // all audio and video formats of one client
val best = formats!!.formats.filter { it.hasVideo }.maxBy { it.height ?: 0 }
file.outputStream().use { out -> vyonEx.download(best, formats.headers, { out.write(it) }) }
// 10 MiB ranged requests; pass from = bytes already written to resume.

// Above 360p, video and audio are separate formats. One file, on Android, without re-encoding:
val pair = vyonEx.mergePair(formats, maxHeight = 2160, container = VyonExContainer.WEBM)!!  // VP9 + Opus
// (VyonExContainer.MP4: H.264 + AAC, which YouTube offers up to 1080p.) Download pair.video and
// pair.audio into two files as above, then:
VyonExMerge.merge(videoFile, audioFile, outputFile, pair.container)  // needs room for both inputs again
```

iOS apps use the same library through `VyonEx.xcframework`:

```swift
import VyonEx

let vyonEx = VyonEx(host: myHost)      // myHost: VyonExHost
vyonEx.initialize()
let video = try await vyonEx.extractVideo(videoId: "dQw4w9WgXcQ", maxHeight: 1080, skipClients: [], requireAvc: true)
let info = try await vyonEx.metadata(videoId: "dQw4w9WgXcQ")   // info?.description_ is the description
```

Swift sees no Kotlin default arguments, so it passes every parameter. A `suspend` function that returns
`Long` (`download`) returns a boxed `KotlinLong` in Swift. Kotlin/Native renames
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
8. When YouTube refuses the video to every client, `extract`, `extractVideo` and `formats` throw
   `VyonExUnplayableException`: `reason` (age-restricted, sign-in required, unavailable, upcoming,
   country-blocked or other, sorted as yt-dlp does) and `youtubeReason`, YouTube's own message.
   Swift gets it as the `NSError`'s `kotlinException`.

## Build and test

Gradle 9.8.2 (wrapper), Kotlin 2.4.21, JDK 27 toolchain, JVM target 26.

```sh
./gradlew jvmTest jvmJar                      # any OS
./gradlew assembleAndroidMain                 # Android AAR; needs ANDROID_HOME (an Android SDK)
./gradlew iosSimulatorArm64Test assembleVyonExReleaseXCFramework   # macOS
VYONEX_LIVE=1 ./gradlew jvmTest --tests com.vyonex.LiveTest --rerun   # live, opt-in
VYONEX_LIVE=1 VYONEX_LIVE_IDS=id1,id2 ./gradlew jvmTest --tests com.vyonex.LiveTest --rerun   # also surveys these videos
```

The survey prints one line per video (profile, itag, HTTP code, or YouTube's reason; never URLs)
and fails only if a returned URL does not answer.

On the JVM the prepared player is cached under `filesDir` only while `configStore.enabled` is true.
`close()` stops background work and frees the loaded player when the host is done with VyonEx.

Offline tests read the captured responses in `fixtures/` (see `fixtures/README.md`).
`apicheck/ApiCheck.swift` is type-checked against the XCFramework in CI.

## Files

- `src/commonMain`: public API and the engine; `src/jvmMain`, `src/iosMain`: `VyonEx`, `VyonExHost`, transport, disk cache.
- `ejs/`: the vendored yt-dlp/ejs solver, compiled into the library.
- `PROVENANCE.md`: every source used. `QUESTIONS.md`: open questions. `NOTICE`: third-party notices.
