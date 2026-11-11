# eXcor progress

The continuation file for the production-engineering directive of 2026-11-02. If work is interrupted, resume from **Next tasks**; don't repeat the audit.

**Related files:**
- `HANDOFF.md`: start here in a new session (rules, layout, commands, next tasks).
- `docs/EXCOR_ENGINEERING_AUDIT.md`: the subsystem inventory.
- `docs/release-gates.json`: the G1–G12 status, evidence and commands.
- `docs/COMPETITOR_MATRIX.md`: the comparison with InnerTubeX.

## Current objective

Make eXcor a credible, independently implemented competitor to InnerTubeX by completing the reliability, security, playback verification and transport-compatibility work in the engineering plan. Keep Apache-2.0 clean-room boundaries and require verified release gates.

## Current verified checkpoint — 2026-11-11 08:29 UTC

**Main:** `a23278b8700ca2164d6c0c2ef9f71af28ac99674`. PR #96 and PR #97 are merged. PR #96 added a score refresh after main CI completes; PR #97 records the clean-room SABR/UMP no-go decision.

PR #96 exact-head CI run `42980593554` and PR #97 exact-head CI run `42981433312` passed all six supported-target jobs. Main CI after PR #96 passed all six jobs at `1ae193a61dbd673b2df20fb4a5e5c12a69a2cc77` (run `42982907219`). The main CI run for `a23278b` is in progress (run `42985652336`): Linux, Windows, preview, Android API 26 and Android API 36 passed; macOS is running.

**Evidence-weighted readiness: 53/100. Release: NO-GO.** The score report is not a playback-success percentage. The current report names main `a23278b`, keeps G6 blocked at 0/20, and records CI as `in_progress`. The post-CI workflow-run refresh is pending.

- **G4/G5:** partial. Owner-run player/cipher, repeatable multi-video playback, decode and seek evidence remains incomplete. Issue #78 has no owner results.
- **G6:** blocked, SABR/UMP playback 0/20. Issue #79 is closed with a NO-GO decision for implementation in this release track. Its source review, evidence gaps and reopening criteria are in `docs/UMP_FRAMING_RESEARCH.md`.
- **G8/G12:** partial. Synthetic reliability and redaction coverage is merged; owner-run live fault verification and broader release-security evidence remain.
- **G10/G11:** partial. Remote publishing is unauthorized; a reproducible black-box comparison has not been completed.
- No credentials, signed URLs, raw captures, bot-check bypasses or competitor implementation source were used in this checkpoint.

**Next:** continue issue #78's owner-operated playback matrix only on the owner's authorized device/network. Reopen SABR implementation work only when the independently usable contract, synthetic vectors, hard bounds and owner-authorized audio/video decode-and-seek evidence in `docs/UMP_FRAMING_RESEARCH.md` exist. Do not upgrade gates based on framing, documentation, or CI alone.

## Release gates

| Gate | Status | Note |
|---|---|---|
| G1 Existing eXcor preserved | Passing | Additive changes only |
| G2 Build correctness | Passing | Now includes the shared metadata compile |
| G3 InnerTube operations | Passing | Live-verified |
| G4 Player compatibility | Partial | VISIONOS path verified live; on the owner's network the live player script now loads and solves n and sig, but YouTube serves no video to TVHTML5 there |
| G5 Streaming | Partial | Audio and video extraction, full ffmpeg decode and seek verified for a video YouTube serves here (video at 480p and 1080p, muxed with its audio); most videos are bot-checked |
| G6 SABR/UMP | Blocked | Offline framing, response assembly and regression tests exist; SABR network/playback transport is absent. Responses here carry direct URLs that play |
| G7 YouTube Music | Passing | Songs and albums verified live on the owner's network (2026-11-03); cloud containers don't get them |
| G8 Reliability | Partial | Typed failures after bounded retries, WARN diagnostics; no live fault injection |
| G9 Developer usability | Passing | `samples/jvm-quickstart` runs live from the local publish |
| G10 Release packaging | Partial | Local publish works; no remote release is authorized |
| G11 Competitive analysis | Partial | InnerTubeX from its README; YouTube.js and NewPipeExtractor document only platforms, services and installation |
| G12 Security | Partial | CI OSV scan gates on shipped dependencies (clean); tooling and test findings are report-only |

**Not a release candidate:** SABR network negotiation and playback (G6) are not implemented. Offline framing and bounded assembly do not establish SABR parity. G4 and G5 are verified only for videos YouTube serves to this environment; most are bot-checked.

## Completed in this pass

- **First local run, owner's PC, home network** (2026-11-03): offline suite 168 tests, 18 skipped, 0 failed. Live: `LiveVideoInfoTest` 8 of 9 pass (the benchmark is opt-in), `LiveMusicTest` 2 of 5 (the rest need credentials or `EXCOR_BENCH`), `LivePlaybackTest` 1 of 4: the format list passes; audio decode and video mux skipped (no ffmpeg on that PC); `publicMusicVideosReturnRangeReadableAudio` fails on `kJQP7kiw5Fk`, bot-checked (`LOGIN_REQUIRED`) on every client, as in the cloud. Fixed from what that network showed:
  - YouTube search: the artist's channel now comes as an `officialCardViewModel`; it's read as a channel again;
  - YouTube Music artist pages: the radio and shuffle buttons now use a `watchEndpoint`, so `radioPlaylistId` was null;
  - YouTube Music songs had no duration (it's at the end of the subtitle); verified with a new live test, `youTubeMusicSongsAndAlbums`, since this network offers Songs, Albums and a Top songs shelf;
  - the player-script path (G4) never started: the watch page loads here, but the script URL is in the page config, not `assets.js`; the 2.66 MB script exceeded a 2 MB cap; and the EJS solver needs 256 MiB of QuickJS memory, not 64 (128 runs out). It now solves n and sig against the live player (about 6 s cold), and TVHTML5 sends its request; YouTube answers it `UNPLAYABLE` or `LOGIN_REQUIRED`;
  - unlabeled live-test assertions now say what they saw.
- **Preview apps, from the owner's phone test** (2026-11-03): the owner reported that the APK says "Sign in to confirm you're not a bot" and that Play audio fails with it, that search had no artist, playlist, album or song filters, and that the Android app had no downloads. Now:
  - both apps search YouTube Music by default, with its own filter chips, typed rows, album, playlist and artist pages, Back and More (shared code in `preview-common/`); live-checked from the desktop app under Xvfb: the Videos chip, clearing it, an artist's shelves, "show all" (92 videos), a community playlist, a song's details, and Play audio for `dQw4w9WgXcQ` through the shared range download (3,449,447 bytes of AAC, 213.09 s);
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
- **`samples/jvm-quickstart`:** a standalone consumer that follows the README.
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
- **Consumer:** `./gradlew publishToMavenLocal && ./gradlew -p samples/jvm-quickstart run`
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
