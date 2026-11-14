# Owner-run playback evidence matrix

**Purpose:** close eXcor's G4 (player compatibility), G5 (direct streaming) and G8 (reliability) evidence gaps through authorized, reproducible testing. This is an *execution plan*, not a claim that missing playback paths are implemented. Track results in issue #78 and keep raw credentials, cookies, tokens and signed URLs out of the repository, issues and CI artifacts.

## Preconditions and safety

- Run only on a computer and YouTube account you control, using content and network access you are authorized to use.
- **Do not bypass bot checks, CAPTCHAs, consent screens, DRM or any access-control decision.** If YouTube refuses a request, classify the outcome and stop that case.
- Prefer disposable public test IDs for any report. Never paste a full request/response, media URL, query string, Cookie or Authorization header, visitor data, PO token, browser profile, HAR file or session-bearing screenshot.
- Use the current `main` SHA and note the environment and `ffmpeg` version, but not IP addresses or account identifiers.
- A `SKIP` means *unverified*, not passed. A `LOGIN_REQUIRED` or bot check is *access blocked*, not proof that a decoder works or fails.
- Signed-in testing is **owner-operated only**, using the preview app's supported sign-in flow. The existing `LivePlaybackTest` uses a signed-out `LiveHost`; it does **not** verify signed-in playback.

## Prerequisites

JDK / Android SDK as described in `maintainers/HANDOFF.md`. For full decode and seek checks, install `ffmpeg` and `ffprobe` and confirm they are on PATH.

On Linux/macOS, run from repository root:

```bash
ffmpeg -version
ffprobe -version
EXCOR_LIVE=1 ./gradlew :jvmTest --tests com.excor.LivePlaybackTest.extractedAudioDecodesFullyAndSeeks --rerun-tasks
EXCOR_LIVE=1 EXCOR_VIDEO_HEIGHT=480 ./gradlew :jvmTest --tests com.excor.LivePlaybackTest.extractedVideoAndAudioDecodeAndMux --rerun-tasks
EXCOR_LIVE=1 ./gradlew :jvmTest --tests com.excor.LivePlaybackTest.formatListReportsRangeSupport --rerun-tasks
EXCOR_LIVE=1 EXCOR_HLS_ID=VIDEO_ID ./gradlew :jvmTest --tests com.excor.LivePlaybackTest.hlsManifestDecodesForHostPlayer --rerun-tasks
```

PowerShell on Windows:

```powershell
ffmpeg -version
ffprobe -version
$env:EXCOR_LIVE="1"
$env:EXCOR_VIDEO_HEIGHT="480"
.\gradlew.bat :jvmTest --tests com.excor.LivePlaybackTest.extractedAudioDecodesFullyAndSeeks --rerun-tasks
.\gradlew.bat :jvmTest --tests com.excor.LivePlaybackTest.extractedVideoAndAudioDecodeAndMux --rerun-tasks
.\gradlew.bat :jvmTest --tests com.excor.LivePlaybackTest.formatListReportsRangeSupport --rerun-tasks
$env:EXCOR_HLS_ID="VIDEO_ID"
.\gradlew.bat :jvmTest --tests com.excor.LivePlaybackTest.hlsManifestDecodesForHostPlayer --rerun-tasks
Remove-Item Env:\EXCOR_LIVE
Remove-Item Env:\EXCOR_VIDEO_HEIGHT
Remove-Item Env:\EXCOR_HLS_ID
```

Use `EXCOR_DECODE_ID` only for a public video you are allowed to access. With no override, the test uses its existing fixture ID `dQw4w9WgXcQ`. The audio test downloads the complete stream, validates its duration and reads a mid-file HTTP byte range. The video test downloads and muxes selected video/audio, decodes it, checks dimensions/duration, and reads a mid-file HTTP byte range; its default height cap is 480p. These byte-range checks do not drive a player or establish playback seeking. The format-list test probes advertised ranges. These tests may consume significant bandwidth and disk space. They delete temporary media on completion.

The HLS test uses `EXCOR_HLS_ID` (default `dQw4w9WgXcQ`), validates a manifest through the public API, and asks FFmpeg to decode up to eight seconds. It needs a live, eligible video and `ffmpeg`; known bot/age challenges and the exact known page-reload refusal are classified as access-blocked, while unrelated errors remain visible. Use `--rerun-tasks` for a fresh network attempt. The 2026-11-14 signed-out attempt was refused before an HLS manifest was returned; see the [sanitized report](../modules/samples/black-box-comparison/results/owner-home-2026-11-14-hls-blocked.md) and earlier [JVM HLS attempt](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-jvm-hls-attempt.md). For the per-ID initial-range matrix, `EXCOR_LIVE_IDS` optionally overrides its default three public IDs as a comma-separated list. It records recognized access blocks per ID and continues to later IDs; unclassified technical failures still fail visibly. A successful initial range is only a 1 KiB transport check, not decode or player-seek evidence.

```powershell
$env:EXCOR_LIVE="1"
$env:EXCOR_LIVE_IDS="dQw4w9WgXcQ,kJQP7kiw5Fk,JGwWNGJdvx8"
.\gradlew.bat :jvmTest --tests 'com.excor.LivePlaybackTest.publicMusicVideosRecordRangeOutcomeForEachId' --rerun-tasks
Remove-Item Env:\EXCOR_LIVE
Remove-Item Env:\EXCOR_LIVE_IDS
```

## Matrix to execute

### Owner-run ffplay result — 2026-11-13

On `dQw4w9WgXcQ`, two signed-out quickstart runs selected VISIONOS WebM audio and started ffplay at 30 seconds. Right then Left produced ordered HTTP 206 range starts `0`, `329300`, `980764`, and `824084` in both runs; the waveform refreshed after both keys on the repeat run. The first and repeat runs relayed 966,656 and 983,040 bytes and ended at their 45-second limits. This repeats eXcor audio seeking on one ID and network; it does not establish video seeking, broader ID/network repeatability, or InnerTubeX player parity. See the [first run](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-player-seek.md) and [repeat run](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-player-seek-repeat.md).

### Current direct-transport run record — 2026-11-13

| Field | Value |
|---|---|
| Source commit | PR #154 head `120892be137b6182c512a26a92d74bc64b14e199`; clean-room sources match base `0409b1fd15130a2ce2723bd420cb02e99da2709f` |
| Date | 2026-11-13 UTC |
| Command | `$env:EXCOR_LIVE='1'; $env:EXCOR_DECODE_ID='dQw4w9WgXcQ'; $env:EXCOR_VIDEO_HEIGHT='480'; .\gradlew.bat :jvmTest --tests 'com.excor.LivePlaybackTest.extractedAudioDecodesFullyAndSeeks' --tests 'com.excor.LivePlaybackTest.extractedVideoAndAudioDecodeAndMux' --tests 'com.excor.LivePlaybackTest.formatListIsRangeReadable' --rerun-tasks --no-daemon` |
| Platform / network | Windows x64 workstation; owner's home network; signed-out test harness |
| Content | One public music video, `dQw4w9WgXcQ` |
| Outcome | Full audio decode, 480p video/audio mux and decode, all 27 format range probes passed. This is one ID/network; HTTP ranges are not player seeks, and no InnerTubeX comparison ran in this command. |

### Per-ID initial-range matrix — 2026-11-13

The updated opt-in test at source `9cc903163d58ef97f11f4352fef02a3c51baee90` ran twice on signed-out Windows x64 over the owner's home network. Both runs recorded `dQw4w9WgXcQ` as a valid 1 KiB HTTP 206 range and `kJQP7kiw5Fk` plus `JGwWNGJdvx8` as `ACCESS_BLOCKED` before media. The 2026-11-14 repeat reported one test, zero skips and zero failures. A baseline run had skipped after the second ID and omitted the third. This adds test-matrix coverage only, not additional successful video, decode, seek or competitor-comparison evidence. See the [sanitized run report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-per-id-range-matrix.md).

| Case | Session/network | Evidence required | Current baseline |
|---|---|---|---|
| A | Signed out, owner's computer | Whole audio decode + mid-file HTTP range read + actual player seek; video with audio mux + whole decode + mid-file HTTP range read + actual player seek; list ranges | One public video has separate eXcor full audio decode/range and 480p video+audio mux/decode/range checks. The post-order 2026-11-13 paired run passed full 3,507,483-byte Opus decode and forward/back HTTP byte-range probes in 3/3 cold and 3/3 warm samples per adapter. Cold first-media p50/p95 was 1,146/1,265 ms for eXcor and 1,169/1,211 ms for InnerTubeX; cold decode p50/p95 was 307/312 ms and 365/370 ms. Warm first-media p50/p95 was 869/979 ms and 135/143 ms; warm decode p50/p95 was 315/360 ms and 365/374 ms. This remains one shared video/network from a dirty worktree. The earlier pre-update 5,130 ms eXcor first-media result and one-sample Cobalt result (4,724 ms vs 1,100 ms) are historical. On four additional IDs, InnerTubeX passed full decode and both probes in 3/3 while eXcor was access-blocked before media in 3/3: 4,064,225 bytes on `9bZkp7q19f0`, 4,247,110 bytes on `CevxZvSJLk8`, 10,332,653 bytes on `aqz-KE-bpKQ`, and 4,678,808 bytes on `kJQP7kiw5Fk`. On the fifth ID, InnerTubeX first-media p50/p95 was 1,377/1,437 ms and decode p50/p95 was 466/495 ms; eXcor made zero media requests. These are access differences, not additional paired media passes. HTTP byte ranges from the adapters are not player seeks; two eXcor ffplay runs on the first ID show forward/backward audio seeking. Video seeks, broader ID/network repeatability, additional paired playable IDs and InnerTubeX player results remain unverified. Older InnerTubeX FAIL/OTHER results were from adapter 0.1.2 before its JSON serialization fix. See the [cold report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-visionos-first.md), [cold samples](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-visionos-first.jsonl), [warm report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-warm-full.md), [warm samples](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-warm-full.jsonl), [player seek first run](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-player-seek.md), [player seek repeat](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-player-seek-repeat.md), [second report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-second-video.md), [third report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-third-video.md), [fourth report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-bbb-audio.md), [fourth samples](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-bbb-audio.jsonl), [fifth report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-fifth-video.md), [fifth samples](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-fifth-video.jsonl), and [pre-fix recheck](../modules/samples/black-box-comparison/results/owner-home-2026-11-12-classifier-recheck.md). The companion eXcor live test logged Cobalt as UNPLAYABLE and then decoded through VISIONOS. Other signed-out IDs hit access challenges or failed before metadata. The companion LivePlaybackTest did not run a player seek.
| B | Signed out, phone/mobile connection | Preview search, metadata, playback and format selection | Search/metadata available; playback access blocked in prior owner reports |
| C | Signed in, owner's computer | Preview playback and format selection with supported sign-in; no credential disclosure | Not verified end to end |
| D | Signed in, owner's phone | Preview audio/video playback and refusal handling after account switch | Not verified end to end |
| E | Eligible response requiring player JavaScript | Confirm `s` and/or `n` transform then **actual full decode and seek** | Cipher transform tested; no live eligible stream verified |
| F | Fault handling | 429, 5xx, cancellation, expiry, refusal and session switch remain bounded and correctly classified | Synthetic cases pass on supported CI; owner-run live fault injection incomplete |
| G | Stream denied by service | Capture only generalized reason/status and which public client path failed | Known `LOGIN_REQUIRED`, `UNPLAYABLE` cases |
| H | Experimental HLS on owner's Android device and JVM host | Tap **Try HLS (experimental)** in the preview; record Media3 readiness/playback and, for a seekable VOD, backward/forward seek. The JVM check validates the manifest and decodes up to eight seconds with FFmpeg. Classify manifest, child-request, decoder and access refusals separately | Android and JVM returned `UNPLAYABLE` before a manifest on signed-out `dQw4w9WgXcQ`. Two other JVM attempts, including a public NASA ISS live stream, hit bot checks before a manifest. FFmpeg and Media3 never started; no InnerTubeX HLS comparison was possible. Successful HLS playback remains unverified. See the [Android report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-android-hls-attempt.md) and [JVM report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-jvm-hls-attempt.md). |
| I | Signed out, Android 8.0/API 26 and Android 16/API 36 emulators on workstation Wi-Fi | Preview metadata and visible video playback | `dQw4w9WgXcQ` rendered at 720p H.264 with `audio/mp4` at 130 kbps from VISIONOS on both API levels; frames changed during both runs. API 36 logged two HTTP 206 playback-probe responses. One ID/network; no InnerTubeX Android comparison, full-duration decode, player seek or HLS test. See the [run report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-android-preview-playback.md). |

**Earlier isolated-user attempt (2026-11-13; no playback result):** A fresh Android 16/API 36 emulator user stayed at Android’s system “Unlock for all features and data” screen, so no playback operation could be invoked there. This was an environment blocker, not an app failure or YouTube access refusal; the temporary user was removed.

**Android preview playback (2026-11-13; signed-out pass):** On the existing unlocked emulator user, without clearing app data, the signed-out preview resolved `dQw4w9WgXcQ`, reported playback at 720p H.264 with `audio/mp4` at 130 kbps from VISIONOS, logged two HTTP 206 playback-probe responses, and rendered different video frames across captures more than six seconds apart. This was an emulator on the workstation Wi-Fi, not a mobile-data test or an InnerTubeX app comparison. Full-duration decode, player seeking, other IDs and live HLS remain unverified. See the [sanitized run report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-android-preview-playback.md).

**Post-order matched check (2026-11-13):** after moving signed-out VISIONOS ahead of the scripted fallback profiles, eXcor and InnerTubeX both passed full decode and forward/backward HTTP byte-range probes in 3/3 cold samples for `dQw4w9WgXcQ` on the owner's Windows home network. A separate same-day warm run passed 3/3 per adapter on the same ID/network. Cold first-media p50/p95: 1,146/1,265 ms for eXcor and 1,169/1,211 ms for InnerTubeX; warm: 869/979 ms and 135/143 ms. Cold decode p50/p95: 307/312 ms and 365/370 ms; warm: 315/360 ms and 365/374 ms. The current worktree was dirty and only one shared video/network was tested. HTTP byte-range probes are not player seeks. See the [cold report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-visionos-first.md), [cold samples](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-visionos-first.jsonl), [warm report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-warm-full.md), and [warm samples](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-warm-full.jsonl).

**Latest rerun after lazy watch-page fetching (2026-11-13):** all 12 cold/warm attempts passed full decode of 3,507,483 bytes and both HTTP byte ranges. First-media p50/p95 was 357/438 ms vs 1,344/1,662 ms cold and 98/106 ms vs 168/187 ms warm (eXcor vs InnerTubeX). eXcor now defers the watch-page request until a selected client needs a player script; scripted fallbacks still fetch it. InnerTubeX request-count instrumentation misses extractor traffic and is not compared. One video/network only; dirty-worktree diagnostic, not player-seek evidence. See the [report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-lazy-watch.md), [JSON summary](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-lazy-watch-report.json) and [sanitized samples](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-lazy-watch.jsonl).

**Second-video rerun (2026-11-13):** `aqz-KE-bpKQ` produced one eXcor full-decode plus forward/backward range PASS and two access blocks; InnerTubeX passed all three cold attempts. Both decoded 10,332,653 bytes on the successful samples. This is a shared success on a second video, with only 1/3 eXcor repeatability on that video and no new network coverage. It came from the dirty worktree and is not player-seek evidence. See the [report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-lazy-watch-aqz.md), [JSON summary](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-lazy-watch-aqz-report.json) and [sanitized samples](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-lazy-watch-aqz.jsonl).

**Exact-main matched two-video comparison (2026-11-13; source `4e5e67d`):** the signed-out cold/warm run used three samples per phase and adapter on one Windows home network. For `dQw4w9WgXcQ`, eXcor and InnerTubeX passed full audio decode and both HTTP ranges in all six attempts; first-media p50/p95 was 379/406 ms cold and 118/139 ms warm for eXcor, versus 1,524/1,645 ms and 185/189 ms for InnerTubeX. For `kJQP7kiw5Fk`, eXcor was `ACCESS_DENIED` before media in all six attempts while InnerTubeX passed decode and both ranges in all six. This is one-network diagnostic evidence, with different extraction paths; ranges are not player seeks. See the [per-video report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-main-4e5e67d.md) and [sanitized samples](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-main-4e5e67d.jsonl).

For each row, mark **PASS**, **FAIL**, **BLOCKED** or **NOT RUN**. Do not mark cipher support PASS just because a token or transformed URL was constructed; actual playback/seek verification is required. Do not infer a transport (SABR/UMP, HLS or direct file) from a playlist name or marketing description.

## Safe evidence template

```text
Date (UTC):
main commit:
OS and app version:
Network category: home / mobile / CI / other (no IP)
Session category: signed out / signed in by owner (no account details)
Public video ID or label:
Operation: metadata / audio / video / formats / cipher / refusal
Result: PASS / FAIL / BLOCKED / NOT RUN
Observed status category: OK / LOGIN_REQUIRED / UNPLAYABLE / HTTP 429 / 5xx / transport / other
Selected client profile (non-secret):
Container and codec (if known):
Full decode and duration match: yes / no / not tested
Mid-file HTTP byte-range read: yes / no / not tested
Actual backward/forward player seek: yes / no / not tested
Sanitized short note (no response body, URL, headers or tokens):
```

Post only this sanitized template to issue #78; private raw diagnostics must stay on the owner's machine. If a failure can only be reproduced with sensitive logs, describe the generalized status and ask for a safe synthetic fixture instead.

**Java 25 runtime recheck (2026-11-13):** After lowering the JVM bytecode target to Java 17 and using a JDK 21 build toolchain, the eXcor and InnerTubeX adapters ran on the workstation's Java 25 runtime. On `dQw4w9WgXcQ`, both passed full 3,507,483-byte Opus decode and forward/backward HTTP byte-range probes in 3/3 cold attempts. First-media p50/p95 was 1,131/1,348 ms for eXcor and 1,392/1,757 ms for InnerTubeX; decode p50/p95 was 319/363 ms and 369/369 ms. This is one ID/network. A separate run under Temurin JDK 17.0.20.1 passed the same full decode and forward/backward range checks in all 3 cold and 3 warm samples per adapter; see the [JDK 17 report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-jdk17-direct-audio.md). See the [normalized report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-java17-runtime-recheck.md) and [sanitized samples](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-java17-runtime-recheck.jsonl).

**Second-ID JDK 17 repeat (2026-11-13):** On signed-out `aqz-KE-bpKQ`, eXcor was classified `ACCESS_DENIED` before media in 3/3 cold attempts; InnerTubeX fully decoded 10,332,653 bytes and passed both HTTP byte-range probes in 3/3. A prior JDK 25 run on the same ID yielded eXcor success in 1/3, so the second ID remains inconsistent and provides no repeatable shared decode result. See the [normalized report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-jdk17-aqz-repeat.md) and [sanitized samples](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-jdk17-aqz-repeat.jsonl).

**Sixth-ID matched attempt (2026-11-13):** On `M7lc1UVf-VE`, eXcor was blocked before media in 3/3. InnerTubeX passed both HTTP byte-range probes but timed out during full decode after reading 73,728 bytes in 3/3. This adds no shared full-decode pass. See the [normalized report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-sixth-video.md) and [sanitized samples](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-sixth-video.jsonl).

**Fresh matched cold recheck (2026-11-13 09:18 UTC):** On signed-out `dQw4w9WgXcQ`, eXcor 0.1.4 and InnerTubeX 0.1.3 each passed full 3,507,483-byte Opus decode and forward/backward HTTP byte-range probes in 3/3 cold samples. First-media p50/p95 was 1,438/1,530 ms for eXcor and 1,358/1,535 ms for InnerTubeX; decode p50/p95 was 332/381 ms and 378/388 ms. This is one video/network from a dirty worktree, not player-seek evidence, and does not change G5/G11 status. See the [normalized report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-autonomy-followup.md) and [sanitized samples](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-autonomy-followup.jsonl).

**TVHTML5-only cipher-path attempt (2026-11-13):** A signed-out run against `dQw4w9WgXcQ` skipped VISIONOS and WEB; the test observed one player-script fetch, then YouTube returned `The page needs to be reloaded.` before a playable stream. No media download, full decode, byte-range probe, or seek was performed; this records an access/playability refusal and does not establish cipher failure or successful ciphered playback. See the [sanitized attempt report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-tvhtml5-only.md). G4 remains partial.

**Live extraction with one injected 503 (2026-11-13 12:44 UTC):** A signed-out test locally returned one synthetic HTTP 503 with `Retry-After: 0` before the first `/youtubei/v1/player` request was sent. The bounded retry made one live player POST; VISIONOS returned usable audio. eXcor fully decoded 3,433,755 bytes and passed a midpoint HTTP 206 range read. This verifies recovery in one real extraction flow, not an upstream outage or player seek. See the [sanitized report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-live-503-recovery.md). G8 remains partial.

## 2026-11-14 — current-main matched direct-audio check

At exact main `ad7a6f1514c1f320b493c1e62d9bbb6c04c82a5a`, the owner-run Windows x64 signed-out comparison used the home network and the public music video `dQw4w9WgXcQ`. It ran three cold and three warm attempts per adapter with a 30-second timeout and FFmpeg 9.0.2. eXcor 0.2.0 was rebuilt from this checkout; InnerTubeX was pinned to v0.11.6. Both decoded the same 3,507,483-byte Opus stream and passed both HTTP byte-range probes in 6/6 attempts. First-media p50/p95 was 395/555 ms cold and 96/98 ms warm for eXcor, versus 1,388/1,488 ms and 142/145 ms for InnerTubeX. Full-decode p50/p95 was 464/475 ms and 322/331 ms for eXcor, versus 455/540 ms and 364/367 ms for InnerTubeX. See the [report](../modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-ad7-dq-full.md) and [sanitized samples](../modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-ad7-dq-full.jsonl).

A separate current-main LivePlaybackTest on the same ID also passed full 3,433,755-byte Opus decode and a midpoint HTTP 206 range; see the [sanitized run](../modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-ad7-playback.md).

On merged main `36d5bd0a08a39c1c708b9d71405cbacc2ca1fc8b`, the signed-out Windows owner-workstation test on the same video passed both the full Opus decode and 480p H.264/AAC mux-and-decode cases (two tests, zero skips/failures). The video was 854×480 at 25 fps; the mux contained audio and video and decoded for 213.089 seconds. Midpoint HTTP byte-range probes returned 206 for both tracks. This eXcor-only run does not compare video playback with InnerTubeX; ranges are not player-controlled seeks. See the [exact-main run report](../modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-36d5-playback.md).

This is one video and one network, not broad parity or a performance ranking. The range requests are not player-controlled seeks. The per-sample report records forward/backward player seeks as `NOT_TESTED`; ciphered playback, same-harness InnerTubeX video comparison and another network remain open.

On a second public video, aqz-KE-bpKQ, the same adapters on the same network gave eXcor ACCESS_DENIED before media in all six attempts while InnerTubeX passed full decode and both byte-range probes in all six. This does not establish a cause for the access difference. See the [second-ID report](../modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-ad7-aqz-full.md) and [sanitized samples](../modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-ad7-aqz-full.jsonl).

## 2026-11-14 — exact-source strict range validation

At exact source commit `e990cb32a05c096cba9c02eed7962febf275ef0c`, nine selected tests passed with zero skips or failures. The signed-out Windows x64 run fully decoded 3,433,755 bytes of Opus, muxed and fully decoded 480p H.264/AAC, and validated MIME type, content length, exact range boundaries and body sizes. All 27 VISIONOS format-list entries returned valid HTTP 206 ranges. Synthetic regressions rejected misaligned ranges, body-length mismatches, MIME mismatches and excess bytes, and accepted a short final range and valid HTTP 200 full response. This is one public ID and one network; it is not an InnerTubeX comparison or player-seek result. See the [sanitized exact-source report](../modules/samples/black-box-comparison/results/owner-home-2026-11-14-e990-range-validation.md).

## Release-gate interpretation

- **G4** cannot be considered complete without at least one authorized *real playable* stream that exercises each claimed cipher path.
- **G5** cannot be called broadly reliable until audio and video full-decode + seek checks work for multiple eligible videos and environments. Service-side access refusals must be tracked separately.
- **G8** includes one owner-run live extraction that recovered from a locally injected synthetic 503; no upstream YouTube fault or live cancellation was tested, so reliability remains partial.
- **G6 SABR/UMP remains BLOCKED** until independently verified implementation and seek/decoding tests exist; this matrix cannot close it.
- **G10 remote publishing remains owner-authorized only**, regardless of these results.

See `docs/release-gates.json`, `docs/COMPETITOR_MATRIX.md`, `docs/ERROR_HANDLING.md`, `maintainers/HANDOFF.md`, and issue #78.
