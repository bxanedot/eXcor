# eXcor progress

The continuation file for the production-engineering directive of 2026-11-02. If work is interrupted, resume from **Next tasks**; don't repeat the audit.

**Related files:**
- `HANDOFF.md`: start here in a new session (rules, layout, commands, next tasks).
- `docs/EXCOR_ENGINEERING_AUDIT.md`: the subsystem inventory.
- `docs/release-gates.json`: the G1–G12 status, evidence and commands.
- `docs/COMPETITOR_MATRIX.md`: the comparison with InnerTubeX.

## Current objective

Make eXcor a credible, independently implemented competitor to InnerTubeX by completing the reliability, security, playback verification and transport-compatibility work in the engineering plan. Keep the GPL-3.0-only distribution license, clean-room source boundary and verified release gates.

**License update — 2026-11-13:** the owner changed the current license target from Apache-2.0 to GPL-3.0-only. Earlier Apache-2.0 grants remain available for previously distributed revisions; see `NOTICE` and `docs/licenses/Apache-2.0.txt`. The source-independence rules did not change.

## Latest verified checkpoint — 2026-11-13 20:07 UTC

- A public-API JVM regression reproduced `extract` returning a stream for an empty HTTP 206. The media probe now reads one byte and rejects an empty 200/206 response while preserving cancellation and closing the response body. The synthetic regression failed before the fix and passed after it; the full local `:jvmTest` suite passed. This is HTTP preflight evidence only, not full decode or live upstream-fault verification; G8 remains partial.
- On the previous main head `d82cd4e6f8bc9d67ca864249608a18c27e1339ef`, all exact-head CI jobs except macOS passed in [run 43494678820](https://github.com/bxanedot/eXcor/actions/runs/43494678820); its macOS Kotlin/Native and XCFramework job was still running. The live score remains 53/100, Release NO-GO. The score is evidence-weighted readiness, not playback-success rate or feature parity.
- A current-main four-adapter signed-out comparison covered `dQw4w9WgXcQ` and `aqz-KE-bpKQ`, three cold and three warm samples each, one Windows home network. eXcor, InnerTubeX and NewPipe passed bounded initial-media reads in 6/6 on dQ. On aqz, eXcor was `ACCESS_DENIED` in 6/6 while InnerTubeX and NewPipe passed in 6/6. YouTube.js reported `UNSUPPORTED_TRANSPORT` on both IDs in 6/6. This is initial-read evidence only, not full decode, byte-range or player-seek evidence. See the [dQ report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-four-adapter-dq.md), [aqz report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-four-adapter-aqz.md), and [current-main full-decode report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-main-aqz-recheck.md).
- A one-attempt-per-ID signed-out profile trace saw `LOGIN_REQUIRED` with YouTube's bot-check reason from VISIONOS and TVHTML5 on aqz; WEB was skipped without a host streaming-token provider. VISIONOS selected audio for dQ. This explains the observed block on these requests, not behavior on other networks; see the [sanitized profile report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-profile-diagnostic.md).
- The offline verifier tests accept direct-URL `n` and signature-only challenges and reject malformed empty `n`. Twelve signed-out public IDs (four initial plus eight additional) exposed neither `signatureCipher` nor direct-URL `n`; every live test skipped before JavaScript/media requests. This is access evidence, not solver or transformed-playback evidence.
- A follow-up audit found the test-only verifier still required `n` for every signature cipher. It now accepts a valid signature-only cipher, applies `n` only when present, and rejects malformed empty `n` instead of downgrading it. The direct-URL `n`, signature-only and malformed-`n` focused JVM regressions pass; the live sample had no cipher entries and does not verify these transform paths.
- Issue #78 remains open because no eligible live cipher challenge reached transformed playback. There are no open PRs or obsolete merged branches. The only remote branches are `main` and the active `automation/excor-competitiveness` readiness branch. Current distribution is GPL-3.0-only; preserve the clean-room source boundary.

**Next:** find eligible videos and network conditions that expose a live signature or direct-`n` challenge, then verify transformed playback. Investigate the repeated direct-audio access differential on a second network before changing client fallback behavior. Keep G4/G5 partial and release NO-GO until the required evidence is obtained.

## Previous verified checkpoint — 2026-11-11 13:52:57

**Latest code checkpoint:** PR #108 merged as `9a01d0371c67c1350baee5ab9308e1360473c81e`. Its exact head `b4cd4ca079a25c125ccdda05ef3bdba0fd5e4562` passed all six supported-target jobs in [CI run 43029076515](https://github.com/bxanedot/eXcor/actions/runs/43029076515). Player rejection reasons from YouTube are sanitized before they appear in caller-facing exceptions. A synthetic regression checks cookie-like text, signed media URLs and token redaction.

**Readiness remains 53/100; release NO-GO.** Score refresh run 43032632110 records merge commit `9a01d03` at 53/100. Its CI snapshot listed main run 43032631977 as queued; all six jobs were running when this checkpoint was written. PR #108 is diagnostic hardening and earns no score credit. G6 remains blocked at 0/20.

- **Issue #77:** closed after the deterministic retry, transport, session, diagnostic-redaction and bounded exclusion-cache acceptance checks passed.
- **G4/G5:** partial. Issue #78 remains open: owner-run checks now include four signed-out cipher/direct-`n` probe outcomes, but none exposed an eligible challenge and no transformed playback was verified.
- **G6:** blocked, SABR/UMP playback 0/20. No network transport, playable SABR media, full decode or seek evidence.
- **G8:** partial. One owner-run signed-out extraction recovered after a locally injected 503, then fully decoded and passed a midpoint HTTP range read. Actual upstream fault and live cancellation behavior remain unverified.
- **G12:** partial. PRs #103–#108 harden media credential isolation, authenticated-response caching, logged diagnostic redaction and caller-facing rejection messages. Signed-in playback and the broader release-security audit remain incomplete.
- **G10/G11:** partial. Remote publishing remains unauthorized; reproducible black-box comparison is incomplete.
- No credentials, signed URLs, raw captures, bot-check bypasses or competitor implementation source were used.

**Next:** continue the broader security review and issue #78's owner-authorized playback/cipher matrix. The corrected verifier supports direct-URL `n` challenges and skips before player-script fetch when no challenge is present; its 2026-11-13 four-ID run found no eligible challenge. The repeated two-run audio-seek evidence does not close G5 or G11; keep their partial status until the remaining matrix and paired player evidence are complete. Preserve the clean-room boundary.

## Release gates

| Gate | Status | Note |
|---|---|---|
| G1 Existing eXcor preserved | Passing | Additive changes only |
| G2 Build correctness | Passing | Now includes the shared metadata compile |
| G3 InnerTube operations | Passing | Live-verified |
| G4 Player compatibility | Partial | VISIONOS path verified live; on the owner's network the live player script now loads and solves n and sig, but YouTube serves no video to TVHTML5 there |
| G5 Streaming | Partial | Audio/video extraction and full decode; one signed-out audio player run now shows backward/forward seeking. Video player seeks, repeatable IDs and other networks remain unverified |
| G6 SABR/UMP | Blocked | Offline framing, response assembly and regression tests exist; SABR network/playback transport is absent. Responses here carry direct URLs that play |
| G7 YouTube Music | Passing | Songs and albums verified live on the owner's network (2026-11-03); one artist album-discography pagination flow passed live on 2026-11-13; cloud containers don't get these catalogs |
| G8 Reliability | Partial | Typed failures, bounded retry/session behavior and exclusion-cache cap/recency regression tests; live fault injection remains unverified |
| G9 Developer usability | Passing | `modules/samples/jvm-quickstart` runs live from the local publish |
| G10 Release packaging | Partial | Local publish works; no remote release is authorized |
| G11 Competitive analysis | Partial | Paired direct-audio decode/range evidence plus one eXcor-only real audio player-seek run; matched InnerTubeX player seeks and more shared playable IDs remain unverified |
| G12 Security | Partial | CI OSV scan gates on shipped dependencies; PRs #103–#108 add media credential isolation, authorized-response cache exclusion and bounded logged/caller-facing diagnostic redaction. PR #108 passed all six exact-head CI targets; signed-in playback and the broader release-security audit remain unverified |

**Not a release candidate:** SABR network negotiation and playback (G6) are not implemented. Offline framing and bounded assembly do not establish SABR parity. G4 and G5 are verified only for videos YouTube serves to this environment; most are bot-checked.

## Completed in this pass

- **First local run, owner's PC, home network** (2026-11-03): offline suite 168 tests, 18 skipped, 0 failed. Live: `LiveVideoInfoTest` 8 of 9 pass (the benchmark is opt-in), `LiveMusicTest` 2 of 5 (the rest need credentials or `EXCOR_BENCH`), `LivePlaybackTest` 1 of 4: the format list passes; audio decode and video mux skipped (no ffmpeg on that PC); `publicMusicVideosReturnRangeReadableAudio` fails on `kJQP7kiw5Fk`, bot-checked (`LOGIN_REQUIRED`) on every client, as in the cloud. Fixed from what that network showed:
  - YouTube search: the artist's channel now comes as an `officialCardViewModel`; it's read as a channel again;
  - YouTube Music artist pages: the radio and shuffle buttons now use a `watchEndpoint`, so `radioPlaylistId` was null;
  - YouTube Music songs had no duration (it's at the end of the subtitle); verified with a new live test, `youTubeMusicSongsAndAlbums`, since this network offers Songs, Albums and a Top songs shelf;
  - the player-script path (G4) never started: the watch page loads here, but the script URL is in the page config, not `assets.js`; the 2.66 MB script exceeded a 2 MB cap; and the EJS solver needs 256 MiB of QuickJS memory, not 64 (128 runs out). It now solves n and sig against the live player (about 6 s cold), and TVHTML5 sends its request; YouTube answers it `UNPLAYABLE` or `LOGIN_REQUIRED`;
  - unlabeled live-test assertions now say what they saw.
- **Preview apps, from the owner's phone test** (2026-11-03): the owner reported that the APK says "Sign in to confirm you're not a bot" and that Play audio fails with it, that search had no artist, playlist, album or song filters, and that the Android app had no downloads. Now:
  - both apps search YouTube Music by default, with its own filter chips, typed rows, album, playlist and artist pages, Back and More (shared code in `preview/common/`); live-checked from the desktop app under Xvfb: the Videos chip, clearing it, an artist's shelves, "show all" (92 videos), a community playlist, a song's details, and Play audio for `dQw4w9WgXcQ` through the shared range download (3,449,447 bytes of AAC, 213.09 s);
  - Android downloads to Downloads/eXcor: audio, video joined with its sound on the phone (MediaExtractor and MediaMuxer), and any listed format; `JoinCheck` passes on the CI emulators: API 36 joins MP4 (60 video and 88 audio samples), a plain .m4a (88) and WebM (60 and 101), each read back whole; API 26 joins MP4 and .m4a and refuses WebM, as intended. No real YouTube download has run on a phone yet;
  - the bot check is named as such in the status line. The owner's network bot-checks every signed-out client too; the legitimate routes are a signed-in session or a host `TokenProvider`, which is the owner's call.
- **Second cloud session** (2026-11-03): reran every live check from another cloud container; the results match the first session's (see **Commands**). Also:
  - CI actions moved to their Node 24 majors (checkout v7, setup-java v6, upload-artifact v7; bxanedot/eXcor#64);
  - `musicSearch`'s top result card is typed when it plays a video or song (it was `UNKNOWN`, without a video ID);
  - stale docs: the `docs/FEATURE_MATRIX.md` header, and notes in `QUESTIONS.md` on the removed apps.
- **Preview apps** (2026-11-03, owner's decision): the music-player apps and `historical-engine` are removed. `preview-android` (APK) and `preview-desktop` (Windows EXE through jpackage) use the clean-room library. Checked by hand under Xvfb: search, details, Play video (720p, joined with ffmpeg, clean decode).
- **Format list** (`formats`, 2026-11-03): every whole-file format of one client, for download choices; the first step toward moving the apps onto the clean-room SDK. Live: 27 formats, 144p to 2160p, all range-readable.
- **Video extraction** (`extractVideo`, 2026-11-03): video track plus matching audio, both probed. Live: 480p and 1080p H.264 from VISIONOS, muxed with M4A by ffmpeg and fully decoded, seek correct.
- **YouTube Music** (`YouTubeMusic.kt`): typed search, suggestions, artist pages, playlists and radio. Live-verified.
- **`excorDefaultHost()`:** constructing `Excor` needs no boilerplate.
- **Publishing fix:** the library couldn't be published (shared metadata compile error). It is fixed, and CI now publishes and builds a consumer.
- **`modules/samples/jvm-quickstart`:** a standalone consumer that follows the README.
- **README:** Installation and Quick start sections.
- **`videoInfo` watch-page fallback:** it returned null for 8 of 10 videos from this environment.
- **InnerTube read retries:** 429, 5xx and transport errors; three attempts.
- **YouTube benchmark:** results in `docs/PERFORMANCE.md`.
- **Merged earlier today:** mixes and the failed-tab fix (bxanedot/eXcor#54), playlist conversion and the iTunes budget (bxanedot/eXcor#55).

## Commands

- **Offline suite and targets:**
  `./gradlew :jvmTest :assembleAndroidMain :compileKotlinIosArm64 :compileKotlinIosSimulatorArm64 :compileKotlinIosX64 :compileCommonMainKotlinMetadata`
  Latest (2026-11-03, second cloud session): 165 tests, 17 skipped (the opt-in live tests and benchmarks), 0 failed. This container has no Android SDK, so `:assembleAndroidMain` ran in CI only.
- **Live YouTube and YouTube Music:**
  `EXCOR_LIVE=1 ./gradlew :jvmTest --tests com.excor.LiveVideoInfoTest`
  Latest (2026-11-03, second cloud session): 7 of 7 pass, including the new top-result check; the benchmark is opt-in (`EXCOR_BENCH=1`).
- **Live music:** `EXCOR_LIVE=1 ./gradlew :jvmTest --tests com.excor.music.LiveMusicTest`
  Latest (2026-11-03, second cloud session): pass (Deezer end to end, keyless Apple with a cross-provider match). Spotify and the full Apple Music API are skipped without credentials.
- **Consumer:** `./gradlew publishToMavenLocal && ./gradlew -p modules/samples/jvm-quickstart run`
  Latest: all six steps "ok".
- **Playback:** `EXCOR_LIVE=1 ./gradlew :jvmTest --tests com.excor.LivePlaybackTest`
  - `extractedAudioDecodesFullyAndSeeks` passes (2026-11-02): VISIONOS Opus, 3,433,755 bytes, ffmpeg decodes 213.06 s against a 213 s video, mid-file seek correct.
  - `extractedVideoAndAudioDecodeAndMux` passes (2026-11-03): 480p H.264, 854x480, 14,103,519 bytes; with `EXCOR_VIDEO_HEIGHT=1080`, 1920x1080, 80,911,999 bytes. Both muxed with M4A audio without re-encoding and decoded to 213.09 s; mid-file seek correct.
  - `publicMusicVideosReturnRangeReadableAudio` passes its first video but fails on the second, `kJQP7kiw5Fk`, which every client gets as "unavailable" here.
  - Second cloud session (2026-11-03): the same. Audio (3,433,755 bytes, 213.061 s), 480p video muxed with M4A (213.089 s) and the 27-format list pass for `dQw4w9WgXcQ`, all from VISIONOS. `kJQP7kiw5Fk` is `UNPLAYABLE` ("not available") on VISIONOS; `JGwWNGJdvx8`, `FGBhQbmPwH8`, `9bZkp7q19f0`, `jNQXAC9IVRw` and `PkZNo7MFNFg` are `LOGIN_REQUIRED` (bot check). TVHTML5 is skipped because the watch page redirects to Google's captcha page (`google.com/sorry`), so there is no player script; WEB is skipped without a token provider.

## Known blockers

- **Bot check:** YouTube answers most videos from this environment with `LOGIN_REQUIRED` ("Sign in to confirm you're not a bot") or "unavailable", on every client. Some videos (such as `dQw4w9WgXcQ`) are served to VISIONOS, which is how streaming was verified. The HTML watch page answers cloud containers with a captcha (HTTP 429). The owner's home network and phone bot-check the same way, though the watch page loads there. eXcor does not bypass either.
- **Withheld YouTube Music catalog (cloud only):** songs, albums and discographies aren't offered to cloud containers; the owner's home network gets them (2026-11-03). Rechecked 2026-11-03 from a second cloud container: search filters are Artists, Videos, Episodes, Profiles, Community playlists and Podcasts only, and the Daft Punk artist page has Videos, Live performances and Playlists shelves only.
- **Maven Central 429s:** `repo.maven.apache.org` rate-limits cloud containers. The second cloud session used a local init script (`~/.gradle/init.d`, not in the repository) that points Maven Central at Google's mirror, `maven-central.storage-download.googleapis.com/maven2/`. CI is unaffected.

## Next tasks

1. Done:
   - YouTube benchmark (`docs/PERFORMANCE.md`), InnerTube read retries, `videoInfo` watch-page fallback;
   - YouTube.js and NewPipeExtractor documentation assessment;
   - OSV dependency scan (bxanedot/eXcor#57);
   - typed request failures (`docs/ERROR_HANDLING.md`) and `CHANGELOG.md`.
2. If the intermittent nulls seen once in a full live run recur, read the WARN logs (endpoint and status) and fix the cause.
3. On a normal network: run `LivePlaybackTest`. If playback works there, SABR/UMP work can start against real responses.

## 2026-11-10 — competitor-readiness work branch

The owner's current goal is a production-quality, solo-maintained eXcor extractor competitive with InnerTubeX, with no known external users. Work started on feature/competitive-readiness-20261110 from main commit bbeee33f70e1ced3987e0ff1c70163310c108af2.

- Added a clean-room, common-source diagnostic sanitizer for untrusted player status/reason values and synthetic tests guarding URL/credential-pattern exposure, control characters and bounds. A stand-alone Kotlin smoke check passed locally; the full Gradle and platform CI checks are **pending**.
- Refreshed docs/COMPETITOR_MATRIX.md from public documentation and recorded a prioritized plan at docs/superpowers/plans/2026-11-10-excor-competitor-plan.md.
- This PR does **not** deliver SABR/UMP, new live-player evidence, or a release. All current release-gate statuses remain unchanged.

**Next:** review CI at the PR head, inspect any failure, then pursue deterministic network/cancellation fault-injection tests and legitimate owner-network playback checks. Do not claim parity based on this PR.


## 2026-11-11 — verified competitor-readiness checkpoint

**Evidence-weighted readiness score: 53/100**, unchanged from the checkpoint recorded on issue #77. The score is not a playback success percentage.

- PR #83 merged as `429c179089a8f44b655e2477647c929c50d695a5` after all six supported-target CI jobs passed. It adds a safe owner-run playback evidence protocol; it does not add live playback evidence. Issue #78 remains open.
- PR #84 merged as `11ae5d5414af2311ed457b4408f908a2347ba129` after all six CI jobs passed. This is offline-only UMP frame decoding. It does not negotiate or play SABR streams; G6 remains blocked and SABR/UMP playback remains 0/20.
- PR #86 merged as `3083d5b5ba09ea1f43083720bc7beed360111919` after all six supported-target CI jobs passed. It adds offline-only UMP response assembly across separate HTTP response bodies; it does not implement network negotiation or play media. G6 remains blocked and SABR/UMP playback remains 0/20.
- PR #87 merged as `3f451811af946e6d1253d81567d0399d622a4d5c` from head `157da30337a4b5c146c8e12b353c5a7ca01a7151` after exact-head CI run `42965037745` passed all six supported-target jobs. Its synthetic tests cover redaction of transport/player values, host adapters, OAuth redirects and MIME text; it adds no live playback evidence, SABR transport or score credit.
- Merged PRs #76, #80, #81 and #82 have supported-target CI evidence recorded on issue #77. They add diagnostic redaction, deterministic retry/redirect fault tests, session-generation invalidation, and bounded response reads. G8 and G12 remain partial because owner-run live fault injection, complete diagnostic-surface review, and reproducible playback/cipher evidence are still outstanding.

The InnerTubeX matrix was rechecked against its public README and changelog on 2026-11-10; the latest documented release remains v0.11.6 (2026-11-07). No competitor implementation source was inspected.

**Release decision: NO-GO.** No credentials, signed URLs, raw captures, or bot-check bypasses were used.

## 2026-11-11 — merged UMP boundedness guard and current audit state

- PR #90 merged as `cdc9ad86ca51bdf6ab8d85540c1e3437d1e32bab`; all six supported-target CI jobs passed in run `42944572854`. It rejects non-empty UMP parts that make zero payload-byte progress in a complete response and adds synthetic initial/continuation coverage. This is a boundedness rule, not evidence of SABR request handling or playback. G6 remains blocked; SABR/UMP playback is 0/20.
- PR #91 merged as `0abf37d343772f4c2ac4162e2a04552afcb8ae77` after all six supported-target CI jobs passed in run `42954594244`. It adds synthetic regression tests for continuation boundaries and multibyte UMP lengths only; there is no SABR transport or playback. G6 remains blocked; SABR/UMP playback is 0/20.
- PR #94 merged at `b9553e94c720bc2a0ac488f1df7aa861e624c2e0` after exact-head run `42958552005` passed all six supported-target jobs; it rejects readiness proof symlinks that resolve outside the repository. PR #87 is a draft at head `60622027a17bd25a742a26a2481e35dbd1bdce7f`, based on main `b9553e94c720bc2a0ac488f1df7aa861e624c2e0`; exact-head run `42963712070` is pending (macOS queued, other jobs queued or running) at the 2026-11-11 05:10 UTC checkpoint. These security/diagnostic changes receive no score credit before merge. Main CI run `42963567109` is also pending.
- Issue #78 still has no owner-run playback result. No credentials, signed URLs, raw captures or bot-check bypasses were used.

**Evidence-weighted readiness remains 53/100. Release remains NO-GO.**

## 2026-11-12 — follow-up playback comparison and diagnostic audit

- Main is at `a9995256e3629b583f0e15faa4f73479aa54208a`; merge CI run `43292048392` and score refresh run `43294140127` succeeded. The live readiness score remains 53/100; release remains NO-GO.
- The owner matched three cold samples per adapter on one signed-out Windows home-network video. eXcor selected Opus and passed both HTTP byte-offset probes in all three attempts, but each full decode hit the 30-second sample timeout. InnerTubeX returned `FAIL/OTHER` during extraction in all three attempts, before media probing. Neither adapter had a PASS; see `modules/samples/black-box-comparison/results/owner-home-2026-11-12-direct-audio.md` and its JSONL. This is diagnostic evidence, not a pass-rate, parity, or performance result.
- The first comparison run exposed an adapter bug: it treated `videoInfo()` playability from one client as final before trying `extract()` fallback clients. The adapter now waits for extraction before using metadata playability as the no-stream fallback; its version is `0.1.2-excor0.2.0`, and its targeted tests pass. A separate direct playback probe still decoded 213.061 seconds of audio and read a midpoint range (HTTP 206), but repeatable paired full decode is not established.
- A manual diagnostic-sanitizer review found that field names `access_token`, `refresh_token`, `client_secret`, `authorization_code`, `code_verifier`, and `set-cookie` were not covered; `Authorization: Basic` and `Digest` could leave values after the scheme, and later Cookie pairs could remain after a semicolon. Synthetic regressions failed before the fix and pass after redacting complete Authorization, Cookie and Set-Cookie header lines. Targeted `DiagnosticModelSafetyTest` and player-reason tests pass.
- A source audit found that ExcorSyncEngine could keep fetching indefinitely when a provider repeated or cycled continuation cursors. It now returns an incomplete PROVIDER_UNAVAILABLE result with partial progress; a self-repeat regression failed before the fix; the broader A-to-B-to-A regression and SyncAuthTest pass after. G8/G12 remain partial pending broader live and release-security evidence.
- Strix could not run locally because Docker and local LLM configuration are absent. No source was uploaded to managed scanning. This focused review does not complete G12; the full diagnostic/security audit and live fault evidence remain open.
- This working tree moves synthetic fixtures under `modules/cleanroom/fixtures/` and historical captures under `modules/legacy/fixtures/`, reducing tracked root directories from 11 to 10 while keeping both data sets intact. Only `main` remains as a local branch; the active `origin/automation/excor-competitiveness` branch is retained. No obsolete merged feature branches remain.

## 2026-11-12 — corrected full-range comparison follow-up

- The paired adapter now requests the entire known audio object with `Range: bytes=0-`, validates a `206` response's complete `Content-Range`, and counts the transfer as a range attempt. Both JVM adapter test suites pass.
- A fresh matched run on the same signed-out Windows home-network video passed eXcor full transfer+decode and forward/backward HTTP byte-offset probes in 3/3 cold samples (3,507,483 Opus bytes per sample; 456–526 ms full transfer+decode). InnerTubeX v0.11.6 failed extraction in 3/3 and made no media requests. See `modules/samples/black-box-comparison/results/owner-home-2026-11-12-range-full.md` and JSONL. This is repeatable eXcor evidence for one video/network, not a head-to-head playback result or performance comparison.
- Separate owner-run checks passed full audio and 480p video+audio decode/range validation for that public video. They do not test player seeks. G5/G11 remain partial pending more videos, networks, and actual player-seek evidence.

- The active library, preserved historical source, platform packages, and samples are now grouped under modules/, reducing tracked root directories from 10 to 7. The root Kotlin ABI baseline remains in place; Gradle, CI, ignore rules, documentation, and links point at the new locations.

- A 2026-11-12 signed-out attempt on YE7VzlLtp-4 was stopped by YouTube's bot check before media in both audio and 480p video cases; these are explicit access-blocked skips. BaW_jenozKc returned no metadata, which remains an unclassified failure rather than a skip. Classifier and wrapper regressions passed; the full JVM suite passed (228 passed, 0 failed, 18 skipped). G5 remains partial.

**Next:** continue the G12 source audit and close any remaining concrete findings; extend owner-authorized audio/video checks across eligible videos and networks, add player-seek evidence, and repeat a matched sample when InnerTubeX extraction reaches media probing. Keep G4/G5/G8/G11 partial and G6 blocked until repeatable authorized evidence satisfies their gates.

## 2026-11-12 — G12 security audit continuation

- TruffleHog v3.99.8 was checksum-checked against its official release checksum file and run locally with verification disabled. The working-tree scan reported 9 matches, all under archived legacy captures or synthetic tests; the Git-history scan reported 29 in the same fixture/test areas. The API-key-shaped strings in captured YouTube pages/player source remain unverified; no secret values were copied into this report.
- Source review found Swift accepted host session values that Kotlin rejects and retained session headers on cross-authority redirects. Swift now applies the same printable-ASCII/size limits and strips cookies, visitor IDs, authorization headers, and automatic cookie handling on cross-authority redirects, with regression tests added. This Windows host has no Swift/Xcode toolchain, so macOS execution remains pending.
- Strix remains unavailable locally: Docker is absent and the CLI is not authenticated to a local model provider. No source was uploaded to managed scanning. G12 remains partial.
- Branch audit: local `main` only; no merged local or remote branches or stale remote refs were found. The active `origin/automation/excor-competitiveness` branch remains in use.

**Next:** run the Swift package tests on macOS CI, triage the unverified API-key-shaped archived fixture matches without exposing their values, and continue the release-side security audit. Keep G12 partial until this evidence is reviewed.

## 2026-11-12 — active-source scan and matched-result classification

- A targeted TruffleHog v3.99.8 no-verification scan of `modules/cleanroom`, the native Swift package, samples, `.github`, scripts, API baselines and Gradle tooling returned zero matches. The nine working-tree and 29 history matches remain confined to archived captures and synthetic tests; key-shaped captured values remain unverified.
- The matched eXcor adapter mislabeled its exact bot-check exception as `FAIL/OTHER`. Adapter 0.1.4 now classifies only the exact bot/age challenge prefixes as `BLOCKED/ACCESS_DENIED`; unrelated page errors remain failures. Its 12 offline tests pass, and a targeted live test classified `jNQXAC9IVRw` as a bot-check skip.
- Pre-fix owner-home matched samples (three cold attempts per adapter per ID) are in `modules/samples/black-box-comparison/results/owner-home-2026-11-12-classifier-recheck.*`. eXcor passed full decode and both HTTP offsets 3/3 for `dQw4w9WgXcQ`; `jNQXAC9IVRw` was access-blocked for eXcor in 3/3. InnerTubeX adapter 0.1.2 failed before requests on both IDs. The second ID adds no playable eXcor case; this adapter failure was fixed in 0.1.3 and superseded by the 2026-11-13 paired result.
- InnerTubeX v0.11.6 remains the latest public semantic version tag as of this check. The current eXcor readiness score remains 53/100; G5/G11 remain partial, G12 remains partial pending the archived-match triage and complete release-side audit.

**Next:** get the Swift security changes through macOS CI, explain the InnerTubeX adapter failures through its documented public API boundary without inspecting its implementation, and continue authorized multi-video/player-seek evidence. Keep access blocks separate from technical failures.

## 2026-11-13 — release notice packaging

- Inspection found the existing local Maven JARs omitted `META-INF/LICENSE` and `META-INF/NOTICE`. Root Gradle JAR/ZIP tasks now add and check both entries; a forced JVM, metadata, and source archive build passed, and the local JVM plus multiplatform Maven publications passed with both entries present.
- The Android SDK was found under the Claude app's local package directory. `publishAndroidPublicationToMavenLocal` passed; `eXcor-android-0.2.0.aar` contains both notices. Its module metadata lists QuickJS-KT 1.0.15 as a separate runtime dependency, and inspection found no QuickJS classes in `classes.jar` or native `jni/` entries.
- Aggregate `publishToMavenLocal` still fails on this Windows host when `compileKotlinIosArm64` throws `ClassNotFoundException: org.jetbrains.kotlin.cli.utilities.MainKt`. JVM, multiplatform metadata, and Android publications pass individually; the iOS XCFramework still needs macOS artifact inspection.
- The macOS XCFramework build now copies the notices into the release `.xcframework` root, with a CI file-presence check. That new step has not yet run on this worktree. G10 and the provenance/license review remain partial until the iOS artifact check runs; no remote release was made.
- Safe triage of the saved 2026-11-12 TruffleHog JSON found seven unverified Google-key detections in legacy captures: four map to named client configuration fields and three remain context-unresolved. Two other matches are URI/JWT detector hits in synthetic tests. Only paths, detector names, and field labels were inspected; no matched value was printed or sent, so none is reclassified as verified or harmless.
- Branch audit still shows only `main` locally and `origin/automation/excor-competitiveness` as the active unmerged remote branch; no merged or stale branch was found to delete.

**Next:** run the XCFramework job on exact head and inspect its linked binary and notices, then continue the authorized playback matrix and live comparison. Keep G4/G5/G10/G11/G12 partial until their evidence meets the release gates.


## 2026-11-13 — refreshed active-source secret scan

- TruffleHog v3.99.8 no-verification scanned tracked active library/platform/sample code, docs, CI, scripts, API and Gradle inputs, root metadata, and `.claude/settings.json`. It found zero matches (zero verified); raw output remains local and matched values were not printed. The archived legacy-capture and synthetic-test findings remain unresolved and outside this scan. G12 remains partial.

## 2026-11-13 — partial public UMP reference found

- Review of the MIT-licensed `davidzeng0/innertube` documentation at commit `b04d48d000a84befa5d7b018553b04fa4f98ebd8` found partial `/config` and encrypted `/initplayback` request details, UMP part identifiers, and a `SabrSeek` schema. The docs leave key ABR, continuation, ordering, seek mapping, session and recovery semantics unknown; eXcor still has no authorized UMP exchange fixture or playback decode/seek result.
- No implementation code or schemas were copied. The source improves the G6 research baseline but does not meet the complete-contract re-entry criteria; G6 remains blocked at 0/20. Details are in `docs/UMP_G6_RECHECK_2026-11-12.md` and `docs/UMP_FRAMING_RESEARCH.md`.

## 2026-11-13 — media redirect status hardening

- The shared media probe previously followed any HTTP status from 301–308 except 304. It now follows only the existing explicit 301/302/303/307/308 allowlist used by the HLS probe.
- Extended the MockEngine extraction regression: an allowed 302 still follows an allowlisted node, an off-allowlist destination remains blocked, and a 305 response is rejected even with an allowlisted `Location`. The check failed before the fix and passed after it with `:jvmTest --tests com.excor.EngineStateTest.extractionWorksWithoutTheWatchPageAndFollowsOnlyAllowedMediaRedirects`.
- This local targeted JVM result does not replace exact-head cross-platform CI or live playback evidence; G5/G12 remain partial.

## 2026-11-13 — signed-out comparison expansion

- Rebuilt the isolated paired JVM adapters and ran one cold home-network attempt on five public music-video IDs. The current eXcor build passed extraction and both HTTP byte-offset probes for `dQw4w9WgXcQ`; four other IDs were explicitly access-blocked before media. No second playable ID was established.
- InnerTubeX v0.11.6 returned the adapter's `FAIL/OTHER` result on all five IDs before any request was counted. This is not comparative playback evidence: the adapter did not reach media, and its failure remains unclassified. The direct-audio lane also does not exercise InnerTubeX SABR/HLS playback.
- A follow-up harness diagnostic verified the Ktor counter with loopback and a public-page GET to `youtube.com` (HTTP 200, one request counted). InnerTubeX search/extraction still fail before a counted request; using the documented OkHttp engine or temporarily disabling the counter did not change extraction. This narrows the issue to the API-call/configuration path but leaves its cause unknown; the public-page probe does not establish InnerTube API or media-host reachability and adds no competitor capability evidence.
- The sanitized diagnostic JSONL is kept under ignored `build/tool-bin/owner-home-2026-11-13-expansion.jsonl`. The eXcor source tree is still uncommitted, so the report's base `main` SHA does not identify the exact built source; the run is excluded from the public score and parity claims. Existing repeatable one-ID results remain the comparable baseline.

## 2026-11-13 — public transport lifecycle and seek recheck

- Added idempotent `Excor.close()` to the JVM/Android and iOS facades. The JVM engine uses a private OkHttp dispatcher and connection pool cloned from the host client so closing eXcor does not shut down a host-shared dispatcher. README, maintainer brief, JVM/Android ABI files and Kotlin/Native ABI dump now include the lifecycle contract.
- Closed sample-owned eXcor instances and HTTP clients on all paths in the JVM quickstart, playback diagnostics, comparison adapter, owner-run live-test fixtures and preview apps, including desktop sign-in replacement and app shutdown.
- `:jvmTest --tests com.excor.ApiCheckTest.closingExcorStopsFurtherRequestsAndIsIdempotent` and `:jvmTest --tests com.excor.EngineStateTest.extractionWorksWithoutTheWatchPageAndFollowsOnlyAllowedMediaRedirects` passed. `:preview-desktop:compileKotlin`, `:jvmJar`, and `publishJvmPublicationToMavenLocal` passed. The quickstart rebuilt and returned normally after guarded `UNPLAYABLE` results for dQw4w9WgXcQ, M7lc1UVf-VE, and kJQP7kiw5Fk; it did not start ffplay or access media.
- The Android SDK was located in the Claude app package cache. `:assembleAndroidMain :preview-android:compileDebugKotlin` passed. `updateKotlinAbi` still cannot finish because `:compileKotlinIosArm64` fails with `ClassNotFoundException: org.jetbrains.kotlin.cli.utilities.MainKt`; the JVM/Android ABI signatures and shared Kotlin/Native entry were updated directly. iOS/Xcode API validation remains pending on macOS. G5/G12 remain partial; no new playback or seek parity was established.

## 2026-11-13 — native toolchain recovery and playback outcome classification

- The installed Kotlin/Native 2.4.21 distribution was missing `kotlin-native-compiler-embeddable.jar`; the matching archive was present in Gradle's local dependency cache. After restoring that JAR and restarting Gradle, `:compileKotlinIosArm64 --rerun-tasks` passed (4 tasks executed), then `updateKotlinAbi` and aggregate `publishToMavenLocal` passed with the discovered Android SDK scoped to the Gradle process. The published JVM JAR and Android AAR contain `META-INF/LICENSE` and `META-INF/NOTICE`; `close()` is present in both JVM and Android facades. The earlier `MainKt` failure was a damaged local toolchain, not a source change. Windows still cannot build/inspect the final iOS XCFramework or run Apple simulator tests.
- Live playback tests now treat null metadata, stream, and format results as failures. Only the exact recognized access-challenge exception is skipped, preserving unknown service/parser outcomes as visible failures. `:jvmTest --tests com.excor.LivePlaybackAccessTest --rerun` passed; two new five-second player diagnostics at 90 seconds (`aqz-KE-bpKQ`, `FGBhQbmPwH8`) returned `UNPLAYABLE` before extraction, so neither started ffplay or made a media request. They add no playback or matched InnerTubeX evidence.
- Pinned MIT source reviews for YouTube.js and googlevideo add a richer SABR/UMP research baseline, but their implementation and synthetic tests do not meet the independent contract or authorized decode/seek gate. G6 stays blocked at 0/20; details: `docs/UMP_G6_PRIMARY_SOURCE_AUDIT_2026-11-13.md`.

## 2026-11-13 — current paired playback smoke

- The latest paired run used the existing JDK 27 toolchain and the updated InnerTubeX adapter 0.1.3. All three cold samples per adapter on dQw4w9WgXcQ passed full 3,507,483-byte Opus transfer/decode and forward/back HTTP byte-offset probes. First-media-byte p50/p95 was 5,130/5,578 ms for eXcor and 1,174/1,223 ms for InnerTubeX; decode p50/p95 was 314/372 ms and 372/375 ms respectively. The sanitized [report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-adapter-fix-full.md) and JSONL are checked in.
- Two additional public videos were checked in three cold samples per adapter. InnerTubeX passed full 4,064,225-byte decode and both HTTP byte-offset probes on 9bZkp7q19f0, and full 4,247,110-byte decode plus both probes on CevxZvSJLk8, in 3/3 samples for each. eXcor was classified BLOCKED/ACCESS_DENIED before media on both IDs in 3/3. These are access differences, not additional matched decoder results; no challenge was bypassed or retried. The sanitized [second](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-second-video.md) and [third](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-third-video.md) reports and JSONL are checked in.
- The earlier FAIL/OTHER was an adapter setup bug: its Ktor API client did not install JSON ContentNegotiation, so InnerTube.search failed before HTTP send. Adding JSON serialization in adapter 0.1.3 fixed the live request; the offline apiClientSerializesJsonBodiesBeforeSending loopback regression and :innertubex-jvm:test passed. No InnerTubeX implementation code was inspected. The default Java 25 runtime also cannot load the adapters' Java 26 bytecode; the comparison README documents the Java 26+ runtime requirement.
- The worktree is uncommitted and `mainCommit` identifies base `main`, not a source fingerprint. All three local comparisons are excluded from the public score and release claims; only one video has a shared playable pass, two have access-blocked eXcor results, and no player-driven seeks were observed, so G11 remains partial.
- The full root `:jvmTest` passed on the current Windows worktree: 248 tests, 0 failures and 18 opt-in live/benchmark skips. `:compileAndroidMain`, `:compileKotlinIosArm64`, and aggregate `:check` passed; `:check` verified the Kotlin ABI and compiled shared iOS simulator tests but skipped simulator execution on Windows. These local results do not replace exact-head cross-platform CI.
- A 30-second owner-network `PlaybackDiagnostics` run for the same ID returned `UNPLAYABLE` from metadata before extraction; ffplay did not start, so no player seek was observed. This explicit access result was left intact and not bypassed.
## 2026-11-13 — Swift media credential isolation

- Source review found that the Swift primary API and watch-page fallback paths copied the host session Cookie into returned `ExcorStream.headers`. The Swift live playback test applies those headers to the media URL. This forwarded account cookies outside the YouTube API origin.
- Removed both media-header cookie assignments. Added primary and fallback regressions that require `ExcorStream.headers` and `headers(for:)` to omit Cookie while the player API request retains the host cookie.
- `swift test --filter ExcorFallbackTests` could not run because Swift is not installed on this Windows workstation. Strix 1.7.0 is installed, but Docker and local model credentials are absent; no source was uploaded. G12 remains partial until the Swift tests and macOS CI pass and the broader release-side audit is complete.

## 2026-11-13 — UMP framing decoder fail-closed state

- Caller audit found `UmpFrameDecoder` is currently used only by its synthetic tests. After a malformed prefix, oversize declaration or frame-count limit, it could be called again with retained partial state. It now invalidates and releases that state after feed errors; `finish()` remains resumable after an incomplete-data check to preserve the existing incremental contract.
- The new regression covers malformed bytes, frame-limit errors, repeated calls after failure, and the existing incremental truncation behavior. `:jvmTest --tests com.excor.internal.sabr.UmpFramingTest` passes (7 tests); the subsequent full `:jvmTest` passes (248 tests, 0 failures, 18 opt-in skips); `:compileAndroidMain` and `:compileKotlinIosArm64` also pass. G6 remains blocked at 0/20: the parser is not wired to a transport and no authorized UMP decode/seek evidence exists.


## 2026-11-13 — refreshed active-source secret scan

- TruffleHog v3.99.8 ran with verification disabled against active clean-room, platform and sample sources, documentation, CI, scripts, API baselines, Gradle inputs, root metadata, and `.claude/settings.json`. It returned zero matches and zero verified; output was parsed without printing matched values. Archived legacy captures and synthetic-test findings remain outside this scan and retain their prior unresolved status. G12 remains partial pending Swift/macOS CI and the complete release-side security audit.

## 2026-11-13 — Cobalt TV profile and current-worktree matched recheck

- The signed-out `TVHTML5` identity now uses yt-dlp 2026.10.26's documented Cobalt `tv` profile in the same client slot; the three-attempt cap is unchanged. Its public `profileId` changes from `tv_samsung` to `tv`. Only client data was independently reimplemented; no extractor code was copied. See the [source provenance](../../PROVENANCE.md).
- A direct signed-out `dQw4w9WgXcQ` check returned `UNPLAYABLE` for Cobalt, then passed through VISIONOS: 27 usable formats, full 3,433,755-byte WebM/Opus decode (213.061 s), and midpoint HTTP 206 at byte 1,716,877. A one-off `9bZkp7q19f0` check was skipped after YouTube's access challenge. See the [direct test record](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-cobalt-profile-recheck.md).
- A fresh matched cold check passed once per adapter on the same public ID. Both decoded the same 3,507,483-byte Opus stream and passed forward/back HTTP byte-range probes. First-media-byte was 4,724 ms for eXcor and 1,100 ms for InnerTubeX; decode was 328 ms and 376 ms. This single pair is diagnostic and does not support broad performance claims or player-seek parity. The [report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-cobalt-profile-paired.md) and [sanitized samples](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-cobalt-profile-paired.jsonl) are checked in.
- After the profile change, full `:jvmTest` passed (248 tests, 0 failures, 18 opt-in skips), `:compileKotlinIosArm64` passed, and aggregate `:check` passed with `ANDROID_HOME` scoped to the Claude package-cache SDK. iOS simulator tests were skipped on Windows, and the native Swift package could not be tested because Swift is unavailable. G4/G5/G11 remain partial; the updated profile did not improve the observed dQw result because VISIONOS still supplied the successful stream.

## 2026-11-13 — owner-run direct-audio player seek

- The quickstart previously stopped after `videoInfo` reported `UNPLAYABLE`, even though `Excor.extract` could still try other client profiles. Removed that premature exit and changed its proxy report to preserve request order, including repeated byte offsets.
- One signed-out ffplay run on `dQw4w9WgXcQ` selected VISIONOS WebM audio at 136 kbps. Starting at 30 seconds, Right then Left produced HTTP 206 ranges at byte offsets `0`, `329300`, `980764`, and `824084`; the ordered later offsets confirm one forward and one backward player seek. The 45-second run relayed 966,656 bytes and ended at the configured limit. See the [sanitized report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-player-seek.md).
- The quickstart `run` task built and completed. This is one eXcor audio result on one network, not a matched InnerTubeX player test; video seeks, repeatability, other IDs/networks, cipher playback and HLS/SABR remain unverified. G5 and G11 stay partial; G8 is unchanged.

## 2026-11-13 — SABR/UMP package-reference recheck

- Rechecked the current first-party `@luanrt/googlevideo` package docs and official YouTube API catalog. The package/SABR docs identify 5.0.1, while the UMP module page identifies 4.1.1; its example uses zero-filled synthetic media, not a service exchange. The clean-room fixture inventory still has no authorized UMP exchange vector.
- This adds no independent ABR, continuation, seek mapping, session, redirect, or recovery contract and no decode/seek evidence. No code or schemas were copied; G6 remains blocked at 0/20. See the [dated recheck](../UMP_G6_RECHECK_2026-11-13.md) and [transport research](../UMP_FRAMING_RESEARCH.md).

## 2026-11-13 — signed-out profile order and matched latency follow-up

- The previous current-worktree pair recorded 4,724 ms to first media for eXcor while Cobalt/TVHTML5 was tried before VISIONOS; the separate playback record showed Cobalt returned `UNPLAYABLE` before VISIONOS successfully decoded the same video. Signed-out client selection now tries script-free VISIONOS first, keeps TVHTML5 and WEB as fallbacks, and loads the player script only when a scripted client needs it. Cipher or `n`-transformed media still loads the solver through the existing URL-processing path.
- A synthetic regression confirms successful VISIONOS extraction does not request the fallback player script. Full `:jvmTest` and `:compileKotlinIosArm64` pass.
- The post-change live `LivePlaybackTest.extractedAudioDecodesFullyAndSeeks` run selected VISIONOS, decoded 3,433,755 bytes (213.061 s), and verified a midpoint HTTP 206 at byte 1,716,877 ([run record](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-visionos-first-live.md)). A fresh 05:03 UTC repeat passed with the same complete decode and range offset; both runs remain eXcor-only byte-range checks, not player seeks. At 05:05 UTC, `LivePlaybackTest.extractedVideoAndAudioDecodeAndMux` selected VISIONOS, fully decoded 854x480 H.264/AAC for 213.089 s, and passed a midpoint video byte-range read. The refreshed matched direct-audio run passed all 3/3 cold samples for each adapter with full 3,507,483-byte Opus decode and forward/back HTTP byte-range probes. First-media p50/p95: eXcor 1,146/1,265 ms; InnerTubeX 1,169/1,211 ms. Decode p50/p95: eXcor 307/312 ms; InnerTubeX 365/370 ms. This is one ID/network and a dirty source worktree; player seeks remain unpaired, and the old 5,130 ms result is retained as a historical pre-update baseline. See the [new comparison](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-visionos-first.md) and [sanitized samples](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-visionos-first.jsonl).
- No gate status changes: G4, G5 and G11 remain partial; no remote publication was performed.

## 2026-11-13 — Swift host-session cookie isolation audit

- A targeted review found `ExcorTransport` disabled automatic cookies only when it constructed the default `URLSession`; a host-supplied session could still attach its own cookie jar to an allowed YouTube media URL. This bypassed the library's explicit media-header isolation.
- Every transport request now sets `httpShouldHandleCookies = false`. Explicit `Cookie` headers created for YouTube API and player-script requests remain available, while redirect handling continues to strip credentials across authorities. A regression verifies the host jar is omitted from a cookie-free media request and an explicit test cookie remains on a second request.
- `swift test` cannot run here because the Swift toolchain is unavailable on Windows; this regression and fix remain unverified until macOS CI. G12 stays partial. No cookies, tokens, or account data were used in this audit.

## 2026-11-13 — Synthetic session replacement coverage

- Extended `SessionGenerationTest` with synthetic old/new SAPISID and session-cookie values. After `onSessionChanged()`, the next player request carries only the replacement cookie and a distinct signed authorization hash.
- Existing pending-player, pending-media and pending-format regressions confirm that session changes cancel stale results and prevent old streams from being restored.
- Focused `:jvmTest --tests com.excor.SessionGenerationTest` passed. No account credentials were copied or sent; this does not establish live signed-in playback, so G5 remains partial.

## 2026-11-13 — Warm paired InnerTubeX audio comparison

- The installed comparator launchers initially failed preflight because PATH selected Java 25 for Java 26 bytecode. Using the Gradle-cached Temurin 27 runtime resolved the mismatch; the first attempt aborted before any network requests.
- Three same-instance warm-up plus measured attempts per adapter then passed full decode and both HTTP byte-range probes on `dQw4w9WgXcQ`; each decoded 3,507,483 bytes. Warm first-media p50/p95: eXcor 869/979 ms, InnerTubeX 135/143 ms. Decode p50/p95: eXcor 315/360 ms, InnerTubeX 365/374 ms.
- The normalized report and six sanitized samples are in [warm-full.md](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-warm-full.md), [warm-full-report.json](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-warm-full-report.json) and [warm-full.jsonl](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-warm-full.jsonl). This is the same single signed-out ID/network and dirty worktree as the cold run; JVM startup and player seeks are not measured. G11 remains partial.

## 2026-11-13 — Android compile and emulator media-stack check

- Scoped Gradle to the Android SDK in the Claude package cache. `:assembleAndroidMain`, `:preview-android:compileDebugKotlin`, `:preview-android:assembleDebug` and `:preview-android:assembleDebugAndroidTest` passed.
- On the connected generic Android 16/API 36 emulator, `JoinCheck` passed for synthetic H.264/AAC MP4, audio-only M4A and VP9/Opus WebM joins. Every input sample was written and read back; instrumentation reported `result=ok`.
- The temporary instrumentation package was removed afterward; the pre-existing preview app was left installed. This verifies the Android media join path, not live YouTube transport, HLS or extraction. Cross-platform playback and release gates remain open.

## 2026-11-13 — Locally supported CI subset

- `:checkKotlinAbi :jvmTest :jvmJar :assembleAndroidMain :compileKotlinIosArm64` passed with the cached JDK 27 and Claude package-cache Android SDK. The ABI task also compiled iOS simulator arm64 and x64 targets.
- JUnit reports show 252 JVM tests, 18 opt-in skips, zero failures and zero errors. Android preview debug and instrumentation APKs compiled, and the separate Android API 36 `JoinCheck` emulator run returned `result=ok`.
- iOS simulator tests cannot execute on Windows, and native Swift tests/macOS XCFramework validation remain outstanding. These checks establish builds and synthetic platform behavior, not live playback parity.

## 2026-11-13 — Repeated signed-out player seek

- Repeated the quickstart ffplay run for `dQw4w9WgXcQ` on the same home network. Sent Right then Left with ffplay focused; the waveform refreshed after each key.
- The loopback transport again recorded ordered HTTP 206 ranges at offsets `0`, `329300`, `980764`, and `824084`. The run relayed 983,040 bytes and ended at the configured 45-second limit.
- Updated README and playback matrix with both sanitized runs ([first](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-player-seek.md), [repeat](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-player-seek-repeat.md)). This repeats eXcor audio seeking on one video/network only; it does not compare InnerTubeX player behavior or verify video seeking. G5 and G11 remain partial.

## 2026-11-13 — JVM host-cookie isolation

- A synthetic localhost regression with a host-supplied OkHttp `CookieJar` failed before the fix: the cloned eXcor transport sent the jar cookie and replaced an explicit `Cookie` header on both requests.
- The JVM/Android transport clone now sets `CookieJar.NO_COOKIES`. The regression passes with no automatic cookie and the explicit session header preserved; the host's shared client remains separate.
- The focused regression passed, then `:checkKotlinAbi :jvmTest :jvmJar :assembleAndroidMain :preview-android:compileDebugKotlin` passed. JUnit reports 253 tests, 18 opt-in skips, no failures or errors. G12 remains partial pending Swift/macOS verification and the broader release security audit.

## 2026-11-13 — Android signed-out playback attempt

- Tried the preview on a fresh Android 16/API 36 emulator user. Android stayed at its system “Unlock for all features and data” screen; although `MainActivity` was resumed, no interactive app UI became available, so no playback operation could be invoked. This is an environment blocker, not an app failure or YouTube access refusal; mobile playback and HLS remain unverified.
- Restored the emulator power settings, switched back to user 0 without clearing its app data, and removed only the temporary `ExcorVerify` user created for this attempt. G5/G11 remain partial; no README performance claim changed.

## 2026-11-13 — Desktop preview session-cookie storage

- The desktop preview had persisted the full YouTube cookie header as plaintext in Java Preferences. It now holds an imported or pasted cookie only in memory until sign-out or app exit and removes the legacy preference on startup before creating the signed-out host.
- Updated sign-in copy and README/competitor/release-gate notes. The existing `:preview-desktop:run --args=--self-test` checks that a synthetic legacy preference is removed; it passed on JDK 27. G12 remains partial pending Swift/macOS verification and the full release-side security/provenance audits.

## 2026-11-13 — Android preview Keystore cookie vault

- Replaced the Android preview's plaintext `noBackupFilesDir/youtube-cookie` storage with AES-GCM ciphertext and a per-install AES key from Android Keystore. `AtomicFile` protects upgrades and writes; prior plaintext cookies migrate, unreadable/tampered values are dropped, and sign-out removes both ciphertext and key. Successful Google WebView sign-in clears its duplicate cookie jar after the cookie is captured.
- The shared preview cookie validator now enforces the library's 8 KiB cookie limit before either app accepts a session. Desktop `--self-test` passed with the limit regression; Android API 36 `JoinCheck` passed legacy migration, encrypted round-trip, tamper rejection, and sign-out cleanup of both file and Keystore alias. App and instrumentation APKs also built.
- Only API 36 Keystore runtime behavior was exercised locally; API 26 runtime coverage, Swift/macOS tests, signed-in playback, and the release-side security/provenance audit remain open. G12 stays partial.

## 2026-11-13 — Signed-out Android preview playback

- Repeated the Android preview check on the existing unlocked API 36 emulator user, preserving its app data. The signed-out preview resolved `dQw4w9WgXcQ`, selected 720p H.264 plus `audio/mp4` at 130 kbps from VISIONOS, logged two HTTP 206 playback-probe responses, and rendered different frames across captures more than six seconds apart.
- The first attempt on the temporary emulator user remained blocked at Android's unlock screen and produced no playback result. The successful run used the existing user 0; it was not a mobile-data test and did not use a signed-in session.
- The sanitized [run report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-android-preview-playback.md) records the result. This is one eXcor-only preview pass, not an InnerTubeX Android comparison; it does not test full-duration decode, seeking, multiple IDs/networks or HLS. G5 and G11 remain partial.

## 2026-11-13 — Android 8.0/API 26 preview and Keystore check

- Installed the default x86_64 API 26 system image and ran the current `JoinCheck` instrumentation on an Android 8.0 emulator. `:preview-android:assembleDebug` and `:preview-android:assembleDebugAndroidTest` passed before the APKs were installed.
- Instrumentation returned `api=26` and `result=ok`: synthetic MP4/M4A media joins preserved every sample, WebM was refused before API 29 as intended, and the cookie vault passed legacy migration, encrypted round-trip, tamper rejection, and sign-out file/key cleanup.
- The signed-out preview also launched on API 26, resolved `dQw4w9WgXcQ`, and played 720p H.264 with `audio/mp4` at 130 kbps from VISIONOS. ExoPlayer showed its active pause control; later captures showed the video had advanced to a different scene. The matching API 36 video observation is recorded in the [run report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-android-preview-playback.md).
- This closes the API 26 runtime coverage item for Android Keystore; G12 remains partial pending macOS Swift cookie-isolation tests and the complete release-side security/provenance audit.

## 2026-11-13 — TruffleHog scoped rescan

- TruffleHog v3.99.8 with verification disabled found zero matches in active test roots. It reported seven unverified Google API-key-shaped matches in archived legacy captures and two unverified synthetic URI/JWT test-pattern matches in legacy tests. Matched values were not printed. These remain unverified findings, not confirmed credentials; G12 stays partial pending the complete release-side review.

## 2026-11-13 — Fourth-ID matched direct-audio comparison

- With Java 27, the current comparison adapters ran signed out against `aqz-KE-bpKQ` on the Windows home network. InnerTubeX adapter 0.1.3 passed full 10,332,653-byte Opus decode and forward/backward HTTP byte-range probes in all three cold samples. First-media p50/p95 was 1,243/1,319 ms; decode p50/p95 was 1,206/1,279 ms.
- eXcor 0.1.4 returned `BLOCKED/ACCESS_DENIED` before media in all three attempts and made zero media requests. This is an access differential, not a decoder or performance failure; no access challenge was bypassed. The four-ID set remains one-network diagnostic evidence from the dirty worktree, with only `dQw4w9WgXcQ` a shared media pass. See the [normalized report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-bbb-audio.md) and [sanitized samples](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-bbb-audio.jsonl). G5/G11 remain partial.

## 2026-11-13 — Archived capture API-key-shaped value cleanup

- Context review located seven distinct Google API-key-shaped strings in two archived public YouTube assets: 18 occurrences in `watch-page.html` and two in `player-dce23b30.js`. All 20 occurrences now use `REDACTED-GOOGLE-API-KEY`; values were not validated or sent for verification.
- The legacy `redactCapture` path now scrubs these strings and `CaptureFixtures` rejects any residual before writing. Fixture provenance and handling instructions were updated.
- TruffleHog v3.99.8 ran with verification disabled from its checksum-verified official release. The working-tree scan excluded `.git` and generated build/cache/binary artifacts; it found no key-shaped matches in archived fixtures and only two preexisting synthetic URI/JWT test-pattern matches. No values were printed.
- Git history was not rescanned or rewritten, so old key-shaped values may remain in repository objects. The two sanitized fixture blobs now match the index entries at intermediate `legacy/fixtures` paths; sanitized `modules/legacy/fixtures` copies remain untracked, and the broader path reorganization must be reconciled before committing. G12 remains partial pending Swift/macOS verification and the complete release-side security/provenance audit.

## 2026-11-13 — Fifth-ID matched audio access differential

- On signed-out music video `kJQP7kiw5Fk`, InnerTubeX adapter 0.1.3 passed full 4,678,808-byte Opus decode and both HTTP byte-range probes in 3/3 cold samples on the Windows home network. First-media p50/p95 was 1,377/1,437 ms; decode p50/p95 was 466/495 ms.
- eXcor 0.1.4 returned `BLOCKED/ACCESS_DENIED` before media in 3/3 attempts and made zero media requests. No challenge was bypassed; this adds an access differential, not another shared playback pass or decoder/performance comparison.
- The five-ID set remains one-network evidence from a dirty worktree and is excluded from the public readiness score. See the [normalized report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-fifth-video.md), [JSON summary](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-fifth-video-report.json) and [sanitized samples](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-fifth-video.jsonl). G5/G11 remain partial.

## 2026-11-13 — current worktree JVM and Android checks

- `:jvmTest --rerun-tasks` compiled and passed 253 tests; 18 opt-in cases were skipped, with zero failures or errors.
- `:checkKotlinAbi`, `:assembleAndroidMain` and `:preview-android:compileDebugKotlin` succeeded using the Android SDK bundled with the Codex desktop app; those tasks were up to date.
- `:preview-android:connectedDebugAndroidTest` passed on the online API 36 emulator. `JoinCheck` uses synthetic media and cookie-vault data. `:preview-desktop:run --args=--self-test` passed on Java 27.
- iOS simulator tests and native Swift tests still require macOS; G8 live fault injection and the complete release-side security/provenance review remain open.

## 2026-11-13 — current working-tree and Git-history secret scan

- TruffleHog v3.99.8 ran with verification disabled. Its working-tree filesystem scan, excluding `.git` and generated build/cache/binary artifacts, found no API-key-shaped matches in archived fixtures and only two synthetic URI/JWT findings in archived tests.
- The Git-history scan found 29 unverified findings: 24 API-key-detector hits in three historical YouTube capture paths and five synthetic URI/JWT test findings. Matched values were never printed or verified. The key-shaped strings are from public captured player/page assets; their validity is unknown.
- Current fixture files and the two matching staged blobs are sanitized; Git history was not rewritten, so historical matches remain. G12 stays partial pending Swift/macOS verification and the complete release-side security/provenance review.


## 2026-11-13 — Java 17 JVM compatibility

- The comparison runner's red preflight exposed a runtime incompatibility: the adapters had class-file version 70, while the workstation's Java 25 runtime supports through version 69. The public InnerTubeX v0.11.6 build targets JVM 17 with a JDK 21 toolchain.
- Changed eXcor's JVM library, comparison adapters, quickstart, Android preview and desktop preview to the JDK 21 toolchain and Java 17 bytecode target. The JVM artifact and adapter classes now have class-file major version 61. `publishToMavenLocal`, adapter `installDist`, quickstart `installDist`, desktop self-test, 253 JVM tests (18 opt-in skips), ABI verification and Android compilation passed.
- On the local Java 25 runtime, the repeated signed-out `dQw4w9WgXcQ` comparison passed full 3,507,483-byte Opus decode and forward/back byte-range probes for both adapters in 3/3 cold attempts. First-media p50/p95 was 1,131/1,348 ms for eXcor and 1,392/1,757 ms for InnerTubeX; decode p50/p95 was 319/363 ms and 369/369 ms. See the [report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-java17-runtime-recheck.md) and sanitized JSONL.
- On `M7lc1UVf-VE`, eXcor was `ACCESS_DENIED` before media in 3/3. InnerTubeX passed forward/back range probes but timed out during full decode after 73,728 bytes in 3/3. This is an access/timeout differential, not another shared decode pass; see the [report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-sixth-video.md). G4/G5/G11 remain partial. Java 17 runtime execution, more eligible IDs/networks and InnerTubeX player-seek comparison remain open.

## 2026-11-13 09:18 UTC — autonomous matched cold recheck

- Reused the signed-out public ID `dQw4w9WgXcQ` already confirmed playable by both adapters on the Windows home network. eXcor 0.1.4 and InnerTubeX 0.1.3 each passed full 3,507,483-byte Opus decode and forward/backward HTTP byte-range probes in 3/3 cold samples.
- First-media p50/p95: eXcor 1,438/1,530 ms; InnerTubeX 1,358/1,535 ms. Decode p50/p95: eXcor 332/381 ms; InnerTubeX 378/388 ms. One video/network, no player seek measured, and the source worktree is dirty, so this remains local diagnostic evidence outside the public readiness score. G5/G11 remain partial.
- The sanitized samples and normalized report are in [JSONL](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-autonomy-followup.jsonl), [Markdown](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-autonomy-followup.md) and [JSON](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-autonomy-followup-report.json).

## 2026-11-13 — Android experimental HLS attempt

- Installed the local preview debug build on the Android 16/API 36 emulator and tried HLS for signed-out `dQw4w9WgXcQ`. The manifest step returned `UNPLAYABLE` with “The page needs to be reloaded”; the preview log showed WEB skipped because no host streaming-token provider was configured. No manifest reached Media3, so no HLS playback or seek occurred.
- No challenge was bypassed and no cookies, token, signed URL or raw response was used. This is one ID/network and keeps successful HLS playback unverified; G5 remains partial. See the [sanitized report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-android-hls-attempt.md).

## 2026-11-13 — YouTube Music artist album pagination

- The signed-out owner-network live check resolved Daft Punk from music search, opened its artist page, and found an `MPAD` album shelf route. eXcor had incorrectly prefixed that route with `VL` and did not decode the returned grid page.
- `musicPlaylist` now preserves `MPAD` browse IDs and decodes album grid pages and `gridContinuation` responses. The shared Android/desktop preview now opens those shelf routes too.
- `LiveVideoInfoTest.youTubeMusicArtistAlbumShelfOpensFullDiscography` passed: every page contained typed album items with unique browse IDs, pagination ended within 20 pages, and the list exceeded the artist-page preview. `YouTubeMusicTest` covers the synthetic grid and continuation shapes; the desktop self-test covers `MPAD` shelf routing.
- This is one artist on one network. It does not verify Singles & EPs, playback, other artists, or cloud availability; G7 stays passing while cross-artist/network coverage remains a limit.

## 2026-11-13 — post-profile-order stable-video comparison

- The current paired adapters reran `aqz-KE-bpKQ` on the signed-out Windows home network with three cold samples, full decode, and forward/backward HTTP byte-range probes.
- InnerTubeX 0.11.6 passed full decode and both ranges in 3/3. eXcor 0.2.0 was `ACCESS_DENIED` before media in 3/3 and issued zero media requests. The profile-order update did not turn this into a shared playback case; this remains an access differential, not evidence of an eXcor decode failure.
- The [normalized report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-post-profile-aqz.md) and [sanitized samples](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-post-profile-aqz.jsonl) record the outcomes. This is one ID/network from a dirty worktree and is excluded from public readiness claims; G5 and G11 remain partial.

## 2026-11-13 — paired direct-audio seek check

- eXcor's quickstart ran signed-out FFplay 9.0.2 on `dQw4w9WgXcQ`, starting at 30 seconds. Right then Left produced ordered loopback-proxy range starts `0`, `329300`, `980764`, `824084`, all HTTP 206; FFplay reached its configured 60-second cap without hitting the byte limit.
- InnerTubeX v0.11.6 at exact tag commit `64c7df4d88d27e6964157b3b341f7ec70f88ea6e` ran with anonymous auth and tokens disabled. Cold and warm attempts each passed initial playback, backward seek, and forward seek stages.
- This is one ID/network with different harnesses. It demonstrates seek behavior, not same-harness performance. See the [paired report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-paired-player-seek.md). G5/G11 remain partial.

## 2026-11-13 — extraction-only InnerTubeX recheck

- Removed the successful-path standalone `videoInfo()` request from the eXcor benchmark adapter; it now uses metadata only to classify a missing stream. Rebuilt the launcher and ran six cold plus six same-instance warm samples, interleaved across eXcor and InnerTubeX, with full FFmpeg decode and both byte-range probes.
- Both adapters passed all 6/6 attempts and decoded the same 3,507,483-byte Opus stream. Cold first-media p50/p95 was 1,072/1,378 ms for eXcor and 1,281/1,757 ms for InnerTubeX; warm was 963/1,498 ms and 159/160 ms. Median decode was 352/341 ms for eXcor and 389/379 ms for InnerTubeX (cold/warm).
- The warm latency difference is an optimization target, but the extraction paths differ: eXcor fetches a watch page and probes media on every extraction; InnerTubeX reuses its player configuration. The InnerTubeX request counter missed player requests, so totals are not compared. The run is one ID/network from a dirty worktree, not JVM-startup or player-seek evidence; G11 remains partial.
- The [normalized report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-extract-only.md), [JSON summary](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-extract-only-report.json) and [sanitized samples](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-extract-only.jsonl) preserve the results.

## 2026-11-13 — defer the watch page for script-free playback

- `firstFromClients` now fetches `/watch` only when it reaches a client that requires the player script. The VISIONOS-first signed-out path can build its player request without the watch page; scripted profiles still fetch the page and retain the prior fallback flow.
- The existing MockEngine regression now asserts that successful VISIONOS extraction makes zero watch-page requests and that scripted fallbacks still fetch it. `:jvmTest --tests com.excor.EngineStateTest.extractionWorksWithoutTheWatchPageAndFollowsOnlyAllowedMediaRedirects` passed. The JVM publication and comparison launcher rebuilt successfully.
- A new interleaved signed-out run on `dQw4w9WgXcQ` passed all 12 attempts, including full decode of 3,507,483 bytes and both HTTP byte-range probes for every attempt. First-media p50/p95 was 357/438 ms vs 1,344/1,662 ms cold and 98/106 ms vs 168/187 ms warm (eXcor vs InnerTubeX). Median decode was 321/316 ms vs 367/367 ms (cold/warm).
- This removes one unnecessary network request on the script-free path. The comparison covers one video/network from a dirty worktree; InnerTubeX request counts remain incomplete, and this does not verify player seeks. G11 stays partial. See the [report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-lazy-watch.md), [JSON summary](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-lazy-watch-report.json) and [sanitized samples](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-lazy-watch.jsonl).

## 2026-11-13 — second-video paired follow-up

- Re-ran three interleaved cold samples per adapter on signed-out `aqz-KE-bpKQ`, with full FFmpeg decode and forward/backward HTTP byte-range probes.
- eXcor passed full 10,332,653-byte decode and both range probes in 1/3 attempts; two attempts were `BLOCKED/ACCESS_DENIED` before media. InnerTubeX passed all 3/3 attempts on the same video/network.
- This is a shared successful playback result on a second video, with inconsistent eXcor access on that ID. It does not establish 3/3 repeatability, player seeking, or cross-network reliability; the worktree was dirty. G5/G11 remain partial. See the [report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-lazy-watch-aqz.md), [JSON summary](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-lazy-watch-aqz-report.json) and [sanitized samples](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-lazy-watch-aqz.jsonl).

## 2026-11-13 — cumulative bound for offline UMP framing

- Added a per-response 16 MiB default byte limit to `UmpFrameDecoder`, counted across incremental `feed` calls. This closes a parser resource gap where a sender could stay below each per-read frame cap while extending one response indefinitely.
- A test first reproduced the gap with two 8 MiB frames supplied in separate reads; the second read was accepted before the fix. After the change, the four UMP framing/assembly test classes passed, the full JVM suite passed with `--rerun-tasks`, and `:compileKotlinIosArm64` passed.
- This hardens an offline framing primitive only. It does not implement a SABR client or verify live UMP boundaries, representation selection, authorization, decoding, or seeking. G6 remains blocked at 0/20.

## 2026-11-13 — signed-out TVHTML5-only playback recheck

- Forced the scripted TVHTML5 client for `dQw4w9WgXcQ`, skipping VISIONOS and WEB. The test observed one player-script fetch, then YouTube returned `The page needs to be reloaded.` before a playable stream; there was no media decode, range probe, or seek.
- This is an access/playability refusal, not evidence that signature or `n` solving failed. Ciphered-stream playback remains unverified and G4 stays partial. See the [sanitized attempt report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-tvhtml5-only.md).

## 2026-11-13 — matched adapter run on JDK 17

- Ran both the eXcor 0.1.4 and InnerTubeX v0.11.6 adapters with `JAVA_HOME` set to Temurin JDK 17.0.20.1. On signed-out `dQw4w9WgXcQ`, both passed full 3,507,483-byte Opus decode and forward/backward HTTP byte-range probes in all 3 cold and 3 warm samples.
- Cold first-media p50/p95 was 460/528 ms for eXcor and 1,359/1,447 ms for InnerTubeX; warm was 125/139 ms and 185/193 ms. Cold decode p50/p95 was 383/426 ms and 428/459 ms; warm was 386/451 ms and 452/502 ms. This is one ID/network; the worktree was dirty and the run is excluded from public readiness scoring. HTTP ranges are not player seeks.
- See the [normalized report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-jdk17-direct-audio.md), [JSON summary](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-jdk17-direct-audio-report.json) and [sanitized samples](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-jdk17-direct-audio.jsonl).

## 2026-11-13 — second-ID JDK 17 comparison

- On `aqz-KE-bpKQ`, a fresh signed-out JDK 17 run classified eXcor as `ACCESS_DENIED` before media in 3/3 cold attempts. InnerTubeX 0.11.6 passed full 10,332,653-byte decode and both HTTP range probes in 3/3.
- An earlier JDK 25 rerun on this ID had one shared eXcor decode/range pass in 1/3. These are access/repeatability outcomes; the new run adds no shared media pass and does not show a decoder failure. G5/G11 remain partial.
- See the [normalized report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-jdk17-aqz-repeat.md), [JSON summary](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-jdk17-aqz-repeat-report.json) and [sanitized samples](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-jdk17-aqz-repeat.jsonl).

## 2026-11-13 — live extraction retry after injected 503

- The opt-in signed-out playback test injected one synthetic local HTTP 503 with `Retry-After: 0` after metadata lookup and before the first live player POST. The bounded retry then made one real player POST, selected VISIONOS, fully decoded 3,433,755 bytes and passed a midpoint HTTP 206 range read on `dQw4w9WgXcQ`.
- This verifies one recovery path through live extraction; it did not induce an upstream/network outage or exercise cancellation under a live fault. G8 remains partial. See the [sanitized report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-live-503-recovery.md).

## 2026-11-13 — signed-out JVM HLS verification

- Added an opt-in HLS check that calls the public `extractHls` API and asks FFmpeg to decode up to eight seconds only after a validated manifest is returned. On `dQw4w9WgXcQ`, YouTube returned `UNPLAYABLE` before a manifest; `aqz-KE-bpKQ` and a NASA ISS live stream were blocked by sign-in/bot checks before a manifest.
- None reached FFmpeg, and no InnerTubeX HLS attempt was possible. This is access-limited evidence, not a successful HLS playback result; G5 remains partial. `EXCOR_HLS_ID` is a Gradle test input and the documented live commands force a fresh run. See the [sanitized report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-jvm-hls-attempt.md).

## 2026-11-13 — PR #151 merged and cross-platform state verified

- Merged PR #151 at `4867fd262cfd4d224c01af7fadcf383848ad3fd2`. All nine CI jobs passed at the exact merge commit in [main run 43433273079](https://github.com/bxanedot/eXcor/actions/runs/43433273079), including Linux, Windows, Android API 26/36, preview, reproducible publications, Kotlin/Native iOS tests and frameworks, XCFramework notices, Swift API checks/tests, and the iOS Simulator package build.
- The tracked root layout is seven directories, down from 11 on the previous main. The merged feature branch was deleted. GitHub now lists only `main` and `automation/excor-competitiveness`; the latter remains referenced by the README readiness badge and workflow.
- The live score refreshed successfully in [run 43434527790](https://github.com/bxanedot/eXcor/actions/runs/43434527790): 53/100, Release NO-GO, and main CI success. It is an evidence-readiness score, not a playback-success percentage or direct feature-parity score. G4/G5/G8/G10/G11/G12 remain partial and G6 remains blocked.

## 2026-11-13 — exact-main macOS security and XCFramework notice verification

- Exact-main CI run [43441086314](https://github.com/bxanedot/eXcor/actions/runs/43441086314) passed all nine jobs on `b23993d99e81dfc3d5709bd9281a4c93a1669876`. The macOS job passed Swift package tests, including the populated-cookie-jar regression, and verified `LICENSE` and `NOTICE` in its generated XCFramework.
- No XCFramework artifact was uploaded by that run, so its linked QuickJS/c-vector contents and published artifact remain uninspected. The cookie test is synthetic; signed-in live playback remains unverified. Older captured-fixture rights, strict implementation isolation, and the complete release-security audit remain open, so G12 stays partial.
- Strix could not run locally because Docker and model authentication are unavailable. No repository source was uploaded to managed scanning.

## 2026-11-13 — exact-main two-video direct-audio comparison

- Built eXcor from clean main `4e5e67d5e1bfab3b424831e9cd18d3219409da8f`; the JVM publication and both comparison-adapter distributions completed. The aggregate `publishToMavenLocal` task could not start because this Windows checkout has no configured Android SDK.
- On one signed-out Windows home network, the matched runner collected 24 cold/warm samples for `dQw4w9WgXcQ` and `kJQP7kiw5Fk`. For `dQw4w9WgXcQ`, both adapters passed full decode and forward/backward HTTP byte-range probes in all 6 attempts. First-media p50/p95 was 379/406 ms cold and 118/139 ms warm for eXcor; InnerTubeX was 1,524/1,645 ms cold and 185/189 ms warm. For `kJQP7kiw5Fk`, eXcor was `ACCESS_DENIED` before media in all 6 attempts, while InnerTubeX passed full decode and both ranges in all 6.
- Updated the report to keep video outcomes separate and show first-media/decode p50/p95; all 18 comparison-tool tests passed. These results show one shared playable ID and one access differential on one network, not broad performance/reliability parity. HTTP ranges are not player seeks; G5 and G11 remain partial.
- See the [per-video report](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-main-4e5e67d.md), [JSON summary](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-main-4e5e67d-report.json), and [sanitized samples](../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-main-4e5e67d.jsonl).
