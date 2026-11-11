# eXcor progress

The continuation file for the production-engineering directive of 2026-11-02. If work is interrupted, resume from **Next tasks**; don't repeat the audit.

**Related files:**
- `HANDOFF.md`: start here in a new session (rules, layout, commands, next tasks).
- `docs/EXCOR_ENGINEERING_AUDIT.md`: the subsystem inventory.
- `docs/release-gates.json`: the G1–G12 status, evidence and commands.
- `docs/COMPETITOR_MATRIX.md`: the comparison with InnerTubeX.

## Current objective

Finish the YouTube and YouTube Music surface that this environment can verify, keep the library installable, and record precisely what is blocked.

## Release gates

| Gate | Status | Note |
|---|---|---|
| G1 Existing eXcor preserved | Passing | Additive changes only |
| G2 Build correctness | Passing | Now includes the shared metadata compile |
| G3 InnerTube operations | Passing | Live-verified |
| G4 Player compatibility | Partial | VISIONOS path verified live; on the owner's network the live player script now loads and solves n and sig, but YouTube serves no video to TVHTML5 there |
| G5 Streaming | Partial | Audio and video extraction, full ffmpeg decode and seek verified for a video YouTube serves here (video at 480p and 1080p, muxed with its audio); most videos are bot-checked |
| G6 SABR/UMP | Blocked | Not implemented; responses here carry direct URLs that play |
| G7 YouTube Music | Passing | Songs and albums verified live on the owner's network (2026-11-03); cloud containers don't get them |
| G8 Reliability | Partial | Typed failures after bounded retries, WARN diagnostics; no live fault injection |
| G9 Developer usability | Passing | `samples/jvm-quickstart` runs live from the local publish |
| G10 Release packaging | Partial | Local publish works; no remote release is authorized |
| G11 Competitive analysis | Partial | InnerTubeX from its README; YouTube.js and NewPipeExtractor document only platforms, services and installation |
| G12 Security | Partial | CI OSV scan gates on shipped dependencies (clean); tooling and test findings are report-only |

**Not a release candidate:** G6 (SABR/UMP) is not implemented. G4 and G5 are verified only for videos YouTube serves to this environment; most are bot-checked.

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
- PR #86 proposes bounded assembly across separate complete HTTP response bodies. It is still under review and its macOS checks were queued in the latest check. It does not change the G6 gate.
- PR #87 is a draft security/diagnostics audit PR. Its supported-target CI is pending and none of its changes are included in the readiness score.
- Merged PRs #76, #80, #81 and #82 have supported-target CI evidence recorded on issue #77. They add diagnostic redaction, deterministic retry/redirect fault tests, session-generation invalidation, and bounded response reads. G8 and G12 remain partial because owner-run live fault injection, complete diagnostic-surface review, and reproducible playback/cipher evidence are still outstanding.

The InnerTubeX matrix was rechecked against its public README and changelog on 2026-11-10; the latest documented release remains v0.11.6 (2026-11-07). No competitor implementation source was inspected.

**Release decision: NO-GO.** No credentials, signed URLs, raw captures, or bot-check bypasses were used.
