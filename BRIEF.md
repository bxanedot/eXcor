# VyonEx clean-room rewrite — brief for the implementing agent

You are writing **VyonEx**, a Kotlin Multiplatform library that turns a YouTube video ID into a directly playable audio URL, and then **VyonExKit**, a native Swift package that does the same on Apple platforms (section 8). An earlier VyonEx exists, but it contains GPL-licensed code. Your job is to write new ones from scratch, from this brief and from permissively licensed public sources only, so they can be released under **Apache-2.0**.

This brief tells you **what** the library must do. It deliberately does not tell you how the old one did it. Do not try to find out.

---

## 1. Clean-room rules (read first, follow throughout)

1. **Never open the old code.** Off limits, in every form (web, clone, local copy, search index, cache):
   - GitHub `bxanedot/vyonex` and `bxanedot/vyon`, all branches and history, including `player_configs.json`.
   - Local folders `Desktop\vyon-main`, `Desktop\vyon`, `Documents\Codex\vyonex`, any `Documents\Codex\2026-*` folder, and `~/.gradle/caches/vyon-extractor`.
   - The XERJ index `vyonex-current`. Disable the `xerj` MCP server for this work.
2. **Never read GPL, LGPL or AGPL extractor code**, not even "for the idea". That includes NewPipe / NewPipe Extractor, InnerTubeX, Metrolist, InnerTune, OuterTune, ViMusic, SimpMusic, LibreTube, Piped and Invidious.
3. **Allowed sources:**
   - **yt-dlp** (`github.com/yt-dlp/yt-dlp`) is released under the Unlicense (public domain). You may read it and port its logic. Check the `LICENSE` file at the commit you use.
   - yt-dlp's wiki (PO Token guide, extractor arguments).
   - **YouTubeKit** (`github.com/alexeichhorn/YouTubeKit`) is MIT-licensed. You may read it and port from it, mainly for the Swift engine. If you copy or closely port any part, add its copyright line (`Copyright (c) 2022 Alexander Eichhorn`) and the MIT license text to a `NOTICE` file, and log what you took.
   - Any companion repository (for example the JS challenge solver), **only after** you have checked its license file and it is Unlicense, MIT, BSD, ISC or Apache-2.0.
   - YouTube's own public responses (watch page, player JS, player API), observed live by you.
   - Documentation for Kotlin, Ktor, kotlinx.serialization, kotlinx.coroutines and QuickJS-KT, and RFCs.
4. **Keep a provenance log** in `PROVENANCE.md`: every external source you relied on, with URL, commit or date, license, and what you used it for. Every non-trivial algorithm gets an entry.
5. **No pasted code** from anywhere except your own earlier output. Ports from yt-dlp are fine, but log them.
6. **Ask, don't guess.** If this brief is ambiguous, write the question in `QUESTIONS.md` and pick the simplest reading that meets the contract. Do not look for the answer in the old code.
7. After you finish, a reviewer compares your code with the old code. If a passage is too similar, you will get back only "rewrite file X, lines Y–Z from the brief". Rewrite it without asking why.

---

## 2. Deliverable

- Gradle project, group `com.vyonex`, artifact `vyonex`, version `0.2.0`, license Apache-2.0.
- Kotlin Multiplatform targets: `jvm`, `iosArm64`, `iosSimulatorArm64`, `iosX64`. Static iOS framework and an XCFramework, both named `VyonEx`.
- Toolchain: Gradle 9.8.2, Kotlin 2.4.21, JDK 27 toolchain, JVM bytecode target 26.
- Dependencies, matched to the host app (all permissively licensed):
  - Ktor 3.6.0: OkHttp engine on JVM, Darwin engine on iOS.
  - kotlinx.coroutines 1.11.0 and kotlinx.serialization-json 1.12.0.
  - QuickJS-KT 1.0.15 (JavaScript runtime on every target).
  - Anything else only if its license is Apache, MIT or BSD, and logged.
- Shared logic lives in `commonMain`. Platform code only for transport, storage and the host interface.
- `README.md`, `LICENSE` (Apache-2.0), `PROVENANCE.md`, and a GitHub Actions workflow:
  - Windows job: `jvmTest jvmJar`.
  - macOS job: JVM tests, iOS simulator tests, framework links, XCFramework, and a Swift type-check of the public API.

---

## 3. Public API (must stay source-compatible)

The host app compiles against these exact names, packages and members. Keep them. You may add members, but don't rename or remove any.

### Package `com.vyonex`, shared

| Type | Kind | Members |
|---|---|---|
| `VyonExStream` | data class | `videoId: String`, `url: String`, `kbps: Int`, `mimeType: String`, `loudnessDb: Double?`, `clientName: String`, `profileId: String`, `headers: Map<String, String>` |
| `VyonExAudioFormat` | enum | `M4A`, `WEBM_OPUS`, `OTHER` |
| `VyonExAudioCandidate` | data class | `url: String`, `kbps: Int`, `mimeType: String`, `format: VyonExAudioFormat` |
| `VyonExSession` | data class | `cookie: String?`, `visitorData: String?`, `region: String`, `language: String` |
| `VyonExLogLevel` | enum | `DEBUG`, `INFO`, `WARN`, `ERROR` |
| `VyonExConfigStore` | interface | `enabled: Boolean`, `getString(key): String?`, `getLong(key): Long?`, `putString(key, value)`, `putLong(key, value)` |
| `VyonExHostServices` | interface | `suspend session(): VyonExSession`, `configStore: VyonExConfigStore`, `nowMs(): Long`, `log(level: VyonExLogLevel, message: String, trackId: String? = null)` |

### Package `com.vyonex`, per platform

| Type | JVM | iOS |
|---|---|---|
| `VyonExHost : VyonExHostServices` | adds `tokenProvider: TokenProvider?`, `filesDir: java.io.File?`, `httpClient: okhttp3.OkHttpClient` | adds `tokenProvider: TokenProvider?` |
| `VyonEx(host: VyonExHost)` | `init()` | `initialize()` (Swift reserves `init`) |

On **both** platforms `VyonEx` also has: `initializeFallback()`, `suspend audioCandidates(videoId: String): List<VyonExAudioCandidate>`, `suspend extract(videoId: String, maxKbps: Int, skipClients: Set<String> = emptySet(), requireM4a: Boolean = false): VyonExStream?`, `headersFor(url: String): Map<String, String>?`, `onRefused(url: String): String?`, `exclude(videoId: String, profileId: String)`, `onSessionChanged()`.

### PO token types (the host app imports these exact paths)

| Fully qualified name | Shape |
|---|---|
| `com.vyonex.internal.primary.extraction.TokenProvider` | interface: `capabilities: TokenProviderCapabilities` (default: empty), `suspend getPoToken(videoId: String, visitorData: String, cookie: String? = null): PoTokenResult?`, `suspend prewarm(cookie: String? = null)`, `suspend invalidateAttestation()`, `suspend close()`; the last three default to doing nothing |
| `com.vyonex.internal.primary.extraction.TokenProviderCapabilities` | data class: `providers: Set<PoTokenProviderKind> = emptySet()`, `usesWebView: Boolean = false` |
| `com.vyonex.internal.primary.extraction.PoTokenResult` | data class: `playerRequestToken: String`, `streamingDataToken: String`, `visitorData: String`; `toString()` must print only present/missing, never the values |
| `com.vyonex.internal.primary.extraction.strategy.PoTokenProviderKind` | enum: `WEB_BOTGUARD`, `WEBPAGE_ATTESTATION`, `ANDROID_DROIDGUARD`, `IOS_ATTESTATION`, `EXTERNAL` |

---

## 4. Required behavior

### 4.1 Lifecycle

- **`init()` / `initialize()`** returns immediately. About 2 seconds later, in the background, it warms up: reads the host session and fetches and prepares whatever the first extraction would otherwise wait on (player script, signature timestamp, token-provider prewarm). Failures are logged at WARN and never thrown.
- **`initializeFallback()`** does nothing. It exists for source compatibility.
- **`onSessionChanged()`** forgets all exclusions and remembered streams. If `configStore.enabled`, it starts a warm-up immediately.

### 4.2 `extract(videoId, maxKbps, skipClients, requireM4a)`

1. Read `host.session()` on every call and use its cookie, visitor data, region (YouTube `gl`) and language (YouTube `hl`) for every YouTube request.
2. Ask YouTube's player API for the video, trying one or more **client identities** (web, Android, iOS, TV and so on) until one yields a playable audio format whose URL works. Which identities work changes over time, so take the current list and order from yt-dlp and from your own live testing, and document your choice.
   - Skip any identity whose name or profile ID is in `skipClients`, or currently excluded for this video (see 4.5).
   - Make at most a small, fixed number of player requests per call.
3. **Make the URL playable:**
   - If a format has a signature cipher, decode the signature with the player script's function and add it under the parameter name YouTube specifies.
   - If the URL has an `n` parameter, transform it with the player script's function. Otherwise YouTube throttles or refuses it.
   - Fetch the player script whose URL the page or API names, and run its functions in QuickJS. Cache the prepared results per player version (see 4.7).
   - Web-type clients need the player script's signature timestamp in the player request.
4. **PO tokens:** when the chosen identity needs one and `host.tokenProvider` is non-null, call `getPoToken(videoId, visitorData, cookie)`. Send `playerRequestToken` with the player request and attach `streamingDataToken` to the media URL as YouTube expects. If there is no provider or it returns null, try identities that don't need a token.
5. **Pick the format.** Only audio formats with a usable URL count.
   - `requireM4a`: only `audio/mp4`; take the best by the AUTO ranking below. If there is none, this identity has no result.
   - Else if `maxKbps <= 64`: the lowest-bitrate `audio/mp4`. If there is none, the lowest-bitrate format overall.
   - Else (AUTO): if any `audio/webm` exists, consider only those; otherwise all. Rank by codec first (WebM over MP4 over anything else). Within a codec, rank by bitrate, where stereo counts as +50 kbps and an unknown channel count as +25 kbps. Break ties with sample rate (capped at 48 kHz).
   - Above 64, `maxKbps` is **not** a bitrate cap. Keep that.
6. **Never return** a stream that needs SABR or HLS. Only URLs that can be fetched directly, with byte ranges, are allowed.
7. **URL allowlist (security):** return a URL only if it is `https`, port 443, has no user info, its host is `googlevideo.com`, `*.googlevideo.com`, `youtube.com` or `*.youtube.com`, and its path is exactly `/videoplayback`. Do not strip YouTube's own query parameters: the host reads the `c` parameter to size its range requests.
8. **Build the result:**
   - `kbps` = the format's bitrate in bits/s ÷ 1000 (integer division; 0 if unknown).
   - `mimeType` includes codecs in the form `audio/webm; codecs="opus"`.
   - `loudnessDb` comes from the player response's audio loudness value, or null.
   - `clientName` = the YouTube client name used, e.g. `WEB`, `ANDROID_VR`.
   - `profileId` = a stable ID for the identity variant that produced it.
   - `headers` = exactly the headers the host must send to fetch `url` (User-Agent and the like, plus `Cookie` when the session is logged in).
9. **Remember** every returned stream by URL, for `headersFor` and `onRefused`. Keep at most 64; when full, clear them all before adding.
10. **Failure:**
    - **JVM** `extract` returns null, or throws an exception whose message says why the video is unplayable (unavailable, login or age required, and so on).
    - **iOS** `extract` runs the steps above. If that throws (anything except cancellation) or returns null, it runs the watch-page fallback (4.3).
    - Never swallow coroutine cancellation.

### 4.3 Watch-page fallback (iOS `extract`; also the source for `audioCandidates`)

- Skip it (return null) if `WEB` or `WEB_WATCH_PAGE` is in `skipClients` or excluded for the video.
- Fetch `https://www.youtube.com/watch?v=<id>`, sending login cookies when the session has a logged-in cookie. Read the embedded player response and the player script URL from the page.
- Continue only if the playability status is `OK`. Take all formats plus adaptive formats, keep the audio ones, decode them as in 4.2 step 3, and apply the allowlist.
- Select with the same rules as 4.2 step 5.
- Result: `clientName = "WEB"`, `profileId = "WEB_WATCH_PAGE"`, `loudnessDb` from the player response.
- Headers: a desktop-browser User-Agent; `Accept: */*`; `Accept-Language` built from the session language and region; `Referer: https://www.youtube.com/`; `Origin: https://www.youtube.com`; and `Cookie` when logged in.
- Remember the result (4.2 step 9) and log at INFO which format was chosen.

### 4.4 `audioCandidates(videoId)`

- Load the watch page as in 4.3 and return **every** allowed audio format, with no selection.
- `kbps` = (average bitrate, or bitrate if absent) ÷ 1000.
- `format`: `audio/mp4` → `M4A`; `audio/webm` with Opus → `WEBM_OPUS`; anything else → `OTHER`.
- Return an empty list on any failure.

### 4.5 `headersFor`, `onRefused`, `exclude`

- **`headersFor(url)`** returns the headers of a remembered stream, or null.
- **`onRefused(url)`** handles a host that got a 403 or similar. If the URL is remembered:
  1. Forget it.
  2. Call `exclude(videoId, profileId)`.
  3. In the background, invalidate cached player/cipher data so the next extraction re-derives it.
  4. Return the video ID.

  Otherwise return null.
- **`exclude(videoId, profileId)`** logs at WARN and skips that profile for that video for **10 minutes**.

### 4.6 Logging and secrets

- Log only through `host.log`. Prefix messages with `VyonEx`. Use DEBUG, INFO and WARN; never ERROR.
- **Never** log or `toString()` cookies, visitor data, PO tokens or authorization headers.

### 4.7 Platform specifics

- **JVM:**
  - Build the Ktor client on the host's `OkHttpClient`, so cookies, proxies and interceptors are shared. Timeouts: request 30 s, connect 15 s, socket 20 s.
  - Cache prepared player data under a folder in `filesDir` (skip the cache if `filesDir` is null). Write to a temp file and then rename. Keep only the 3 newest entries.
- **iOS:** VyonEx creates its own Darwin client and keeps caches in memory only.
- **`configStore`:** may hold small cached values. When `enabled` is false, everything still works, just without persistence.

---

## 5. Out of scope

Bandcamp, SoundCloud, PeerTube, MediaCCC, comments, channels, playlists, search, captions, video formats, SABR streaming, HLS, account sign-in flows, and minting PO tokens inside the library (the host provides them).

> **Amended by the owner on 2026-10-27.** Now in scope: video formats and captions (added earlier the same day), YouTube and YouTube Music links, playlists, albums, mixes, music metadata, and, after collections, search. Still out of scope: Bandcamp, SoundCloud, PeerTube, MediaCCC, comments, channels, SABR streaming, HLS (so live streams), account sign-in flows, minting PO tokens inside the library, and downloads or offline copies. The owner also chose Android + iOS as the app's platforms (an Android library target follows). See `QUESTIONS.md` item 31.

> **Amended again by the owner on 2026-10-27.** Also in scope: downloads of video and audio in every quality YouTube offers (DRM-free formats only), with video and audio merged into one file on the device; YouTube Music artist pages; recommendations available logged out (related videos and mixes). Account features (library, likes, Premium, members-only) come later as a separately reviewed step. See `QUESTIONS.md` item 36.

---

## 6. Tests and definition of done

- **Offline unit tests** (`commonTest`, plus `jvmTest` where needed), using fixtures **you capture yourself** from YouTube and store in the top-level `fixtures/` folder (shared with the Swift tests, see section 8). Cover:
  - format selection (all three modes and the "no cap above 64" rule)
  - the URL allowlist
  - remember, forget, cap and clear of streams
  - 10-minute exclusion (with a fake clock)
  - `onRefused` and `onSessionChanged`
  - watch-page parsing
  - signature and `n` decoding against a captured player script
  - secrets redaction in `toString()`
- **Opt-in live test** (runs only when `VYONEX_LIVE=1`): for 3 public music videos, `extract` returns a stream, and a ranged GET (`Range: bytes=0-1023`) with the returned headers gets 200 or 206.
- **API check:**
  - A JVM test that compiles against every member in section 3.
  - A Swift file the macOS CI type-checks against the XCFramework.
- `PROVENANCE.md` is complete, every dependency's license is listed and permissive, and nothing in it comes from section 1's forbidden list.

## 7. Hand back

Deliver the work in two pull requests into `main`: the Kotlin library first (branch `rewrite`), then the Swift package (branch `rewrite-swift`, see section 8).

Report in each:
- which client identities you chose and why
- test results (offline and live)
- any open items in `QUESTIONS.md`
- the commit hash

Do not push anywhere except the repository you were given.

---

## 8. Second deliverable: native Swift engine `VyonExKit`

> **Withdrawn by the owner on 2026-10-27.** iOS apps use the Kotlin XCFramework; VyonExKit was delivered, then removed. Do not rebuild it.

A pure-Swift package with the same job and behavior as the Kotlin library, for Apple apps that don't want the Kotlin framework. Build it after the Kotlin PR is open.

- **Layout:** Swift Package Manager package in `swift/`, library product and module `VyonExKit`, license Apache-2.0. The module is not named `VyonEx`, so it can't clash with the Kotlin XCFramework.
- **Platforms:** iOS 15 and macOS 12 (needed to run tests on the CI machine). Add tvOS, watchOS and visionOS only if they compile without extra code.
- **Dependencies:** none. Use `URLSession` for HTTP and `JavaScriptCore` to run the player script's functions.
- **Concurrency:** Swift `async`/`await`. The main type is an `actor` or otherwise `Sendable`, and everything is safe to call from any thread.
- **API.** Same concepts and member names as section 3, in Swift style:
  - Value types (`struct`/`enum`): `VyonExStream`, `VyonExAudioCandidate`, `VyonExAudioFormat`, `VyonExSession`, `VyonExLogLevel`, `PoTokenResult`, `PoTokenProviderKind`.
  - Protocols: `VyonExConfigStore`; `VyonExKitHost` (`session() async -> VyonExSession`, `configStore`, `now`, `log(_:_:trackId:)`, optional `tokenProvider`); `PoTokenProvider` (`capabilities`, `poToken(videoId:visitorData:cookie:) async throws -> PoTokenResult?`, `prewarm`, `invalidateAttestation`, `close`).
  - Main type `VyonExKit(host:)`: `initialize()`, `audioCandidates(videoId:) async -> [VyonExAudioCandidate]`, `extract(videoId:maxKbps:skipClients:requireM4a:) async throws -> VyonExStream?`, `headers(for:)`, `onRefused(url:) -> String?`, `exclude(videoId:profileId:)`, `onSessionChanged()`.
  - The host may pass its own `URLSession`; otherwise use a dedicated one with the section 4.7 JVM timeouts.
- **Behavior:** exactly section 4, with `extract` working like the **iOS** version (primary clients, then the watch-page fallback). Caches stay in memory; an on-disk cache in the app's Caches folder is optional.
- **Parity:** put the captured YouTube fixtures in a top-level `fixtures/` folder that both the Kotlin and Swift tests read, so both engines are checked against the same inputs.
- **Tests:** offline XCTest or Swift Testing cases covering the same list as section 6, plus the opt-in live test under `VYONEX_LIVE=1`.
- **CI:** add to the macOS job `swift test` in `swift/`, and an `xcodebuild` build of `VyonExKit` for the iOS Simulator.
