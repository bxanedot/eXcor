# VyonEx

Kotlin Multiplatform library (JVM, iOS) that turns a YouTube video ID into a directly playable
audio URL plus the headers needed to fetch it. Apache-2.0.

```kotlin
val vyonEx = VyonEx(host)          // host: your VyonExHost
vyonEx.init()                      // iOS: initialize(); warms up ~2 s later in the background
val stream = vyonEx.extract("dQw4w9WgXcQ", maxKbps = 160)
// stream.url + stream.headers -> ranged GET; on HTTP 403 call vyonEx.onRefused(stream.url)
```

## How it works

1. Reads `host.session()` (cookie, visitor data, `gl`, `hl`) on every call.
2. Asks the player API with up to three client identities, in yt-dlp's current default order:
   `TV_SAMSUNG` (TVHTML5), `VISIONOS`, then `WEB` (only with a PO token from `host.tokenProvider`).
   Logged-in sessions skip `VISIONOS`, which does not take cookies.
3. Decodes signature and `n` by running the player script's own functions in QuickJS, through
   yt-dlp's EJS solver (`swift/Sources/VyonExKit/ejs`, shared with VyonExKit). The prepared player is cached per player version
   (JVM: `filesDir/vyonex-players`, three newest; iOS: memory).
4. Picks a format (`requireM4a`, `maxKbps <= 64`, or AUTO), allows only
   `https://*.googlevideo.com|youtube.com/videoplayback` URLs, never SABR or HLS.
5. iOS `extract` falls back to the watch page; `audioCandidates` lists the watch page's audio.

## Build and test

Gradle 9.8.2 (wrapper), Kotlin 2.4.21, JDK 27 toolchain, JVM target 26.

```sh
./gradlew jvmTest jvmJar                      # any OS
./gradlew iosSimulatorArm64Test assembleVyonExReleaseXCFramework   # macOS
VYONEX_LIVE=1 ./gradlew jvmTest --tests com.vyonex.LiveTest --rerun   # live, opt-in
```

Offline tests read the captured responses in `fixtures/` (see `fixtures/README.md`).
`apicheck/ApiCheck.swift` is type-checked against the XCFramework in CI.

## VyonExKit (native Swift)

`swift/` is a dependency-free Swift package with the same behavior for Apple apps that do not
want the Kotlin framework: `URLSession` for HTTP, JavaScriptCore for the player functions,
iOS 15 / macOS 12. `extract` works like the iOS Kotlin version (player API, then the watch page).

```swift
let kit = VyonExKit(host: myHost)       // myHost: VyonExKitHost; optional URLSession: VyonExKit(host:session:)
kit.initialize()
if let stream = try await kit.extract(videoId: "dQw4w9WgXcQ", maxKbps: 160) { /* stream.url, stream.headers */ }
```

```sh
cd swift && swift test                     # offline tests over the shared fixtures/
VYONEX_LIVE=1 swift test --filter LiveTests
```

## Files

- `src/commonMain`: public API and the engine; `src/jvmMain`, `src/iosMain`: `VyonEx`, `VyonExHost`, transport, disk cache.
- `swift/`: VyonExKit; `swift/Sources/VyonExKit/ejs`: the vendored solver both engines use.
- `PROVENANCE.md`: every source used. `QUESTIONS.md`: open questions. `NOTICE`: third-party notices.
