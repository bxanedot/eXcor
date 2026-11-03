# Handoff: eXcor (read this first)

Written 2026-11-03 by the cloud session that built PRs #55–#62, for the next session, which was meant to run on the owner's own machine. Updated the same day by a second cloud session (work branch `ccr-f49a6d1e-6h9xd0`, from PR #64), which reran the live checks from another cloud container; the owner's machine is still untried. It covers the project, the owner's rules, the current state, how to build and test, and what to do next. `EXCOR_PROGRESS.md` is the running status file. Update it, and this file, as you work.

## Start here (local session, written 2026-11-03 at the end of the second cloud session)

**State:** `main` is at the merge of #67. Everything from the cloud sessions is merged (#64–#67); no PR of theirs is open except, possibly, the one that adds this section. If it's still open, merge it once CI is green. Both cloud work branches (`claude/clever-curie-tyaxqs`, `ccr-f49a6d1e-6h9xd0`) are done; start a new branch.

**What the owner saw on their phone (screenshots, 2026-11-03, phone on mobile data, APK from #67):**
- These work there:
  - YouTube Music search;
  - artist pages: Tate McRae, 74.8M monthly audience, with a **Top songs** shelf typed `Song`. That network offers songs, which the cloud networks never did, so G7 can now be checked there;
  - YouTube search and video details.
- These fail with the bot-check message, for every video tried (`Rz0rurf9IKM`, `FXk4oLgkKL0`): Play audio, Formats, Download audio and Download video. The web client's metadata reports "Video unavailable".
- So on that network, YouTube bot-checks every signed-out client. Not a preview bug. Don't work around it.

**Next, in order:**
1. ~~**Run the live checks on the owner's PC.**~~ Done 2026-11-03 (results in `EXCOR_PROGRESS.md`). Home network: the same bot check as the phone and the cloud; YouTube Music offers songs and albums (now live-tested); the watch page loads, and the player-script path now solves the live player, but YouTube serves nothing to TVHTML5. Still open there: install ffmpeg and rerun `LivePlaybackTest` for the decode and mux checks.
2. **Bot check: the owner chose both routes (2026-11-03).** Signed-in sessions are built (cookies plus `SAPISIDHASH`; the preview apps take a pasted Cookie header) and need a live check with the owner's account. The PO-token provider is **on hold**: the local session started it on 2026-11-03 by running BotGuard's challenge (`Create`, `GenerateIT`, then minting) in a browser page, and its safety check stopped that as circumventing YouTube's bot check, which the rule above forbids too. Nothing was built and no token was minted. Don't resume it without the owner's explicit decision on that point; signed-in sessions are the route that stands. Original note: The legitimate options are:
   - a signed-in session: the host supplies cookies through `ExcorSession`;
   - a host-supplied PO token: `TokenProvider`.

   Either is the owner's decision. Build nothing until they choose, and never bypass the check.
3. ~~**Preview UI nits from the screenshots:**~~ Done 2026-11-03: the Android action rows wrap, the metadata-client playability line is gone from both apps, and the Android status lines stop at two lines (failures stay in full in the log). Not yet seen on a phone.
   - the action rows scroll sideways, and "Play audio" was half off-screen;
   - the "web client reports Video unavailable" line misleads; it's the metadata client, not playback;
   - the long status lines stack.
4. **The Windows EXE by hand on the owner's PC.** CI packages and self-tests it; this session drove it under Xvfb on Linux only.
5. **G6 SABR/UMP** stays blocked; **G10 release** only on the owner's say-so.

**On a normal machine you don't need the cloud workarounds:** no Maven mirror init script, and the Android SDK is available for `:preview-android:assembleDebug` and `connectedDebugAndroidTest`-style runs. `JoinCheck` runs with `adb shell am instrument -w com.excor.preview.test/com.excor.preview.JoinCheck`.

## What eXcor is

**eXcor** is a clean-room, Apache-2.0 Kotlin Multiplatform library (`com.excor:eXcor:0.2.0`, package `com.excor`) for YouTube and YouTube Music. It works on JVM, Android and iOS. It provides:
- metadata: video info, search, playlists, mixes, channels, comments and chapters;
- YouTube Music: search, artists, playlists and radio;
- audio streams (`extract`), video plus matching audio (`extractVideo`) and a format list (`formats`);
- cross-provider music catalogs (Spotify, Apple Music, Deezer) through `Excor.music`.

The primary benchmark is InnerTubeX; YouTube.js and NewPipeExtractor are secondary.

**Repository:**
- GitHub: `bxanedot/eXcor`. The cloud session's remote was named `vyonex-clean`; same repository.
- Default branch: `main`, head `4f864f0` at the time of writing.
- Work branches: the first cloud session used `claude/clever-curie-tyaxqs`, the second `ccr-f49a6d1e-6h9xd0`. While the second ran, another session was still opening PRs from the first branch (#65, album playlist IDs), so list the open PRs before you start, and start a new branch for new work.

## The owner's rules (standing)

**Autonomy:**
- Work autonomously and keep going ("never stop until we have achieved the goal").
- Committing, pushing, opening PRs and **merging PRs once CI is green** are authorized.
- Ask only when a decision truly belongs to the owner, such as dropping a feature.

**Clean-room and content:**
- Clean room only. Never read, copy, translate or import GPL/LGPL/AGPL extractor code. InnerTubeX is GPL-3.0: its README and black-box behaviour may guide requirements; its code may not.
- yt-dlp (Unlicense) is fine as a reference. Its EJS solver is vendored in `cleanroom-src/ejs`.
- Don't access `bxanedot/vyonex` or `bxanedot/vyon`. Don't copy legacy code from `src/`.

**Security:**
- Never bypass bot checks, captchas, consent screens, DRM or access restrictions.
- Never print or commit stream URLs, cookies, tokens or auth headers, in logs, tests or docs.
- Don't redistribute IP-bearing captures. Don't claim legal clearance.

**Honesty and releases:**
- Never claim unverified features. Say what was verified, how, and where.
- Never remove or weaken a failing test to get green.
- No remote releases or publishing (Maven Central, GitHub Releases) without the owner's explicit authorization. CI artifacts are fine.

**Commits:** end commit messages with the attribution lines the tool asks for. Put no model names in commits or PRs.

## Layout

| Path | What |
|---|---|
| `cleanroom-src/` | The library: `commonMain`, `jvmCommonMain` (JVM and Android), `iosMain`, tests, `ejs/` |
| `preview-android/` | Preview app, debug APK (Compose, Media3). YouTube Music and YouTube search with filters, album, playlist and artist pages, details, play audio and video, downloads to Downloads/eXcor (`TrackJoiner`, `Saver`), formats, log. `src/androidTest`: `JoinCheck`, a plain Instrumentation run by CI |
| `preview-desktop/` | Preview app, Swing. The same browsing; `packageApp` uses jpackage to build `eXcor Preview.exe` with a bundled runtime, plus an installer when WiX is present. `--self-test <file>` checks the package |
| `preview-common/` | Kotlin sources compiled into both preview apps: the browsing model (`Browse.kt`) and range downloads (`Download.kt`). No Android or Swing types |
| `samples/jvm-quickstart/` | A standalone consumer that installs the library the way the README says |
| `swift/` | The `eXcorKit` Swift package |
| `docs/` | `EXCOR_ENGINEERING_AUDIT.md`, `release-gates.json` (G1–G12), `FEATURE_MATRIX.md`, `COMPETITOR_MATRIX.md`, `PERFORMANCE.md`, `ERROR_HANDLING.md`, `CLEAN_ROOM.md`, `PROVENANCE.md` and others |
| `src/`, `ejs/` (root) | The quarantined historical engine. **Nothing compiles it.** Leave it alone until the provenance audit |
| `fixtures/` | Test fixtures, all hand-written or redacted |

The music-player apps (`app-core`, `android-app`, `desktop-app`, `ios-app`) and `historical-engine` were removed on 2026-11-03 at the owner's request. Don't bring them back.

## Build and test

You need JDK 27 (Gradle toolchains) and, for the APK, the Android SDK (`ANDROID_HOME`).

```bash
# Library: all targets plus the offline suite (165 tests, 17 opt-in skipped, 0 failed at handoff)
./gradlew :jvmTest :assembleAndroidMain :compileKotlinIosArm64 :compileKotlinIosSimulatorArm64 :compileKotlinIosX64 :compileCommonMainKotlinMetadata
# iOS tests and frameworks: macOS only (see the macos job in .github/workflows/ci.yml)

# Live checks (real network)
EXCOR_LIVE=1 ./gradlew :jvmTest --tests com.excor.LiveVideoInfoTest      # metadata, YouTube Music
EXCOR_LIVE=1 ./gradlew :jvmTest --tests com.excor.LivePlaybackTest       # audio/video download, ffmpeg decode, seek
EXCOR_LIVE=1 ./gradlew :jvmTest --tests com.excor.music.LiveMusicTest    # Deezer, keyless Apple
EXCOR_BENCH=1 EXCOR_LIVE=1 ./gradlew :jvmTest --tests com.excor.LiveVideoInfoTest   # benchmark
# Playback tests need ffmpeg and ffprobe on PATH. Options: EXCOR_DECODE_ID=<video>, EXCOR_VIDEO_HEIGHT=1080

# Preview apps
./gradlew :preview-android:assembleDebug :preview-android:lintDebug
./gradlew :preview-desktop:packageApp            # build/app/eXcor Preview/ (the .exe on Windows)
./gradlew :preview-desktop:packageApp -PpreviewPackageType=exe   # Windows installer; needs WiX

# Installability
./gradlew publishToMavenLocal && ./gradlew -p samples/jvm-quickstart run
```

On Windows, use `gradlew.bat`, and `set EXCOR_LIVE=1` (cmd) or `$env:EXCOR_LIVE=1` (PowerShell).

**CI** (`.github/workflows/ci.yml`) runs on every push and PR:

| Job | Runs |
|---|---|
| linux | tests, publish, sample, OSV scan |
| windows | tests; packages and self-tests the EXE; uploads `excor-preview-windows` |
| preview | APK and lint; desktop self-test and Xvfb start; uploads `excor-preview-apk` |
| android-device (26, 36) | installs the APK in an emulator and checks it starts |
| macos | iOS tests, XCFramework, Swift checks |

Each push makes two runs (push and PR). One fully green run on the head commit is enough to merge. A macOS job occasionally gets cancelled without a runner; re-run it once.

## Where things stand (release gates; details in `docs/release-gates.json`)

| Gate | Status | Note |
|---|---|---|
| G1 preserved, G2 build, G3 InnerTube, G7 YouTube Music, G9 usability | Passing | G7: songs and albums were withheld from the cloud network, so they were never observed live |
| G4 player compatibility | Partial | The VISIONOS path is verified. The player-script path (signature and n) is untested live, because the watch page was captcha'd |
| G5 streaming | Partial | Audio, video (480p and 1080p) and the format list are verified for `dQw4w9WgXcQ`; most other videos were bot-checked from the cloud |
| G6 SABR/UMP | Blocked | Not implemented. There is no independent spec, and the direct URLs play |
| G8 reliability | Partial | Typed failures, bounded retries, WARN diagnostics; no live fault injection |
| G10 release packaging | Partial | Local publish works; no remote release is authorized |
| G11 competitive analysis | Partial | `docs/COMPETITOR_MATRIX.md` |
| G12 security | Partial | The OSV scan gates on shipped dependencies; tooling findings are report-only |

**What the cloud network showed (yours may differ, and that's the point of running there):**
- YouTube answered most player requests with `LOGIN_REQUIRED` ("confirm you're not a bot") on every client.
- `dQw4w9WgXcQ` played on VISIONOS (clientId 101, version 1.02, no player script, no PO token).
- The HTML watch page answered HTTP 429 (captcha).
- VISIONOS player requests must not carry youtube.com's Referer, or YouTube answers 403. The code handles this.
- googlevideo answers media requests with a 302 to another node. The probe follows up to 3 redirects, using an absolute Location verbatim, because rebuilding it breaks the signature.
- Keyless iTunes gave sporadic 403s.
- Maven Central rate-limited the container, which needed local init scripts. A normal machine doesn't.

**A second cloud container (2026-11-03) showed the same:**
- Only `dQw4w9WgXcQ` played, on VISIONOS: the audio decode, the 480p mux and the 27-format list passed again.
- `kJQP7kiw5Fk` was `UNPLAYABLE`; `JGwWNGJdvx8`, `FGBhQbmPwH8`, `9bZkp7q19f0`, `jNQXAC9IVRw` and `PkZNo7MFNFg` were `LOGIN_REQUIRED`.
- The watch page redirected to Google's captcha page (`google.com/sorry`), so TVHTML5 had no player script and G4 stays untested.
- YouTube Music still offered no Songs or Albums filter.
- Metadata (7 of 7) and the music providers passed.

## Next tasks, in order

1. **Run the live checks on the owner's normal network:** `LiveVideoInfoTest`, `LivePlaybackTest`, `LiveMusicTest`. Two cloud containers have now given the same answers, so only a non-cloud network can move G4, G5 and G7.
   - If many more videos play there, `publicMusicVideosReturnRangeReadableAudio` (it fails on `kJQP7kiw5Fk` in the cloud) should pass. Update G5 and the docs with exactly what you measured.
   - If the watch page loads, the player-script path (TVHTML5, signature and `n`) can finally be checked live. That is G4.
   - If YouTube Music offers Songs and Albums there, verify them. That is G7.
2. **Preview apps: get the owner's verdict on the new build.** On 2026-11-03 the owner tried the APK on their phone. Play audio failed with "Sign in to confirm you're not a bot", search had no artist, playlist, album or song filters, and there were no downloads. The filters, album/playlist/artist pages and Android downloads are now built. Still open:
   - whether the downloads work on the owner's phone: their network bot-checks playback, so only a video YouTube serves there can be downloaded;
   - the bot check itself: a signed-in session or a host-supplied PO token (`TokenProvider`) are the legitimate routes. Ask the owner before building either; never bypass the check.
   - the Windows EXE by hand: CI packages and self-tests it, and the flow was exercised under Xvfb on Linux.
3. **SABR/UMP (G6)** stays blocked unless a normal network serves responses with no direct URLs, or an independent spec appears. Don't derive it from GPL code.
4. **G10:** a remote release only on the owner's say-so.

Done by the second cloud session: CI actions on Node 24 (checkout v7, setup-java v6, upload-artifact v7; #64), the `docs/FEATURE_MATRIX.md` header, notes in `QUESTIONS.md` on the removed apps (`CODEX_HANDOFF.md` already carried one), and a `musicSearch` fix: a top result card that plays a video was typed `UNKNOWN` with no video ID.

## Lessons learned (save yourself the time)

**Mock engine:**
- Ktor `MockEngine` clients must set `followRedirects = false`, as `Excor` does, or redirect-allowlist tests pass for the wrong reason.
- Use a fresh `ExcorEngine` per extraction scenario; it remembers streams.

**Common code (`commonMain`, `commonTest`):**
- `commonTest` has no `java.io`; use `IllegalStateException`.
- Common Kotlin has no `RegexOption.DOT_MATCHES_ALL`; use `[\s\S]`. This once broke publishing.

**Live tests and CI:**
- Live tests print only engine log messages, which never contain URLs or tokens. Keep it that way.
- The jvmTest result XMLs keep earlier targeted runs. Delete `build/test-results/jvmTest` before counting tests.
- On the Windows runner, `Start-Process -Wait` is needed to wait for the GUI-subsystem exe.

**Swing:** inside `JPanel.apply { }`, a field named `height` resolves to the panel's own `height`. A `FlowLayout` doesn't wrap in a `BorderLayout`: the desktop chips use the small `WrapLayout` in `Main.kt`.

**Android preview:** with no Android SDK in the container, CI's preview and android-device jobs are the compiler, lint and device. `JoinCheck` prints `result=ok` from `am instrument`; its clips are made with ffmpeg (commands in its KDoc).

**Cloud containers:**
- `repo.maven.apache.org` answers 429. A local `~/.gradle/init.d/*.gradle.kts` that rewrites that host to `https://maven-central.storage-download.googleapis.com/maven2/` (Google's mirror of Maven Central) in `pluginManagement`, `dependencyResolutionManagement`, `buildscript` and project repositories gets the build through. Keep it out of the repository.
- The second container had no Android SDK, so `:assembleAndroidMain` and the preview APK were left to CI.
- To look at a live response's shape, dump it with URLs, tracking params and long tokens removed, read it, and delete the dump. Don't commit captures.
