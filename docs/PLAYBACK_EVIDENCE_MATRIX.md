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

JDK / Android SDK as described in `HANDOFF.md`. For full decode and seek checks, install `ffmpeg` and `ffprobe` and confirm they are on PATH.

On Linux/macOS, run from repository root:

```bash
ffmpeg -version
ffprobe -version
EXCOR_LIVE=1 ./gradlew :jvmTest --tests com.excor.LivePlaybackTest.extractedAudioDecodesFullyAndSeeks
EXCOR_LIVE=1 EXCOR_VIDEO_HEIGHT=480 ./gradlew :jvmTest --tests com.excor.LivePlaybackTest.extractedVideoAndAudioDecodeAndMux
EXCOR_LIVE=1 ./gradlew :jvmTest --tests com.excor.LivePlaybackTest.formatListIsRangeReadable
```

PowerShell on Windows:

```powershell
ffmpeg -version
ffprobe -version
$env:EXCOR_LIVE="1"
$env:EXCOR_VIDEO_HEIGHT="480"
.\gradlew.bat :jvmTest --tests com.excor.LivePlaybackTest.extractedAudioDecodesFullyAndSeeks
.\gradlew.bat :jvmTest --tests com.excor.LivePlaybackTest.extractedVideoAndAudioDecodeAndMux
.\gradlew.bat :jvmTest --tests com.excor.LivePlaybackTest.formatListIsRangeReadable
Remove-Item Env:\EXCOR_LIVE
Remove-Item Env:\EXCOR_VIDEO_HEIGHT
```

Use `EXCOR_DECODE_ID` only for a public video you are allowed to access. With no override, the test uses its existing fixture ID `dQw4w9WgXcQ`. The audio test downloads the complete stream, validates its duration and a mid-file range seek. The video test downloads and muxes selected video/audio, decodes it, checks dimensions/duration, and tests a mid-file seek; its default height cap is 480p. The format-list test probes advertised ranges. These tests may consume significant bandwidth and disk space. They delete temporary media on completion.

The separate `publicMusicVideosReturnRangeReadableAudio` test requires several publicly available IDs to be allowed by YouTube and is known to be access-blocked on some networks. Run it only when that access is available, and report its precise access outcome rather than weakening its assertion.

## Matrix to execute

| Case | Session/network | Evidence required | Current baseline |
|---|---|---|---|
| A | Signed out, owner's computer | Whole audio decode + middle seek; video with audio mux + whole decode + middle seek; list ranges | One public video previously verified; other IDs mostly blocked |
| B | Signed out, phone/mobile connection | Preview search, metadata, playback and format selection | Search/metadata available; playback access blocked in prior owner reports |
| C | Signed in, owner's computer | Preview playback and format selection with supported sign-in; no credential disclosure | Not verified end to end |
| D | Signed in, owner's phone | Preview audio/video playback and refusal handling after account switch | Not verified end to end |
| E | Eligible response requiring player JavaScript | Confirm `s` and/or `n` transform then **actual full decode and seek** | Cipher transform tested; no live eligible stream verified |
| F | Fault handling | 429, 5xx, cancellation, expiry, refusal and session switch remain bounded and correctly classified | Synthetic cases pass on supported CI; owner-run live fault injection incomplete |
| G | Stream denied by service | Capture only generalized reason/status and which public client path failed | Known `LOGIN_REQUIRED`, `UNPLAYABLE` cases |

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
Middle-byte-range seek: yes / no / not tested
Sanitized short note (no response body, URL, headers or tokens):
```

Post only this sanitized template to issue #78; private raw diagnostics must stay on the owner's machine. If a failure can only be reproduced with sensitive logs, describe the generalized status and ask for a safe synthetic fixture instead.

## Release-gate interpretation

- **G4** cannot be considered complete without at least one authorized *real playable* stream that exercises each claimed cipher path.
- **G5** cannot be called broadly reliable until audio and video full-decode + seek checks work for multiple eligible videos and environments. Service-side access refusals must be tracked separately.
- **G8** synthetic fault-injection coverage does not substitute for eligible, authorized live evidence.
- **G6 SABR/UMP remains BLOCKED** until independently verified implementation and seek/decoding tests exist; this matrix cannot close it.
- **G10 remote publishing remains owner-authorized only**, regardless of these results.

See `docs/release-gates.json`, `docs/COMPETITOR_MATRIX.md`, `docs/ERROR_HANDLING.md`, `HANDOFF.md`, and issue #78.
