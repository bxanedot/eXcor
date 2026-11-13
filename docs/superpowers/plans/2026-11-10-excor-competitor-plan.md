# eXcor: competitor engineering plan — 2026-11-10

**Goal:** make the GPL-3.0-only clean-room eXcor library a credible, independently reliable alternative to InnerTubeX for supported YouTube and YouTube Music extraction. Success means verifiable streams, stable API, secure operation, installability and low solo-maintainer burden — not hype or the number of APIs.

**License update (2026-11-13):** the owner changed the current project license target from Apache-2.0 to GPL-3.0-only. The independent source boundary remains unchanged, and earlier Apache-2.0 grants are preserved.

**Owner/audience:** solo maintainer; no known external users. Primary outcomes are open-source quality and reputation.

## Baseline and honest limits

- Main baseline: bbeee33f70e1ced3987e0ff1c70163310c108af2; artifact com.excor:eXcor:0.2.0, Kotlin Multiplatform JVM/Android/iOS, separate native Swift package and unpublished preview apps.
- Search, metadata, channels, comments, YouTube Music, direct audio and video formats have offline tests and qualified live evidence. Playback is frequently blocked by YouTube's own access checks, so live success must not be generalized.
- The repository's release-gates.json reports G1, G2, G3, G7 and G9 passing; G4, G5, G8, G10, G11 and G12 partial; G6 (SABR/UMP) blocked.
- InnerTubeX's public README documents direct/HLS/SABR transport, UMP seeking, cipher handling, isolated sessions, token-provider interfaces and diagnostics. Its 2026-11-07 changelog shows ongoing iOS and streaming work. eXcor does not currently demonstrate parity in all of those areas.
- YouTube.js serves JavaScript/Deno/browser developers; NewPipeExtractor targets Java and several content services. Platform breadth alone does not prove extractor performance.

**Nonnegotiable boundaries:** Only implement in the clean-room source tree, not quarantined `modules/legacy/src/` or `modules/legacy/ejs/`. Never copy GPL/LGPL/AGPL extractor source, including InnerTubeX or NewPipeExtractor. Use competitor README and observable public interfaces only for requirements. Do not evade bot checks, consent or DRM. Do not store or log credentials, signed URLs, private captures or token values. Remote release and publishing require owner authorization.

## Priority and sequence

### P5 layout cleanup

- [x] Keep the active library source in the clearly named `modules/cleanroom/` tree; keep the uncompiled historical source and its EJS bundle together under `modules/legacy/`; group preview apps and the Swift package under `modules/platforms/`; move maintainer records under `docs/maintainers/`, reducing tracked root directories from 13 to 11. Preserve Gradle project IDs, public package coordinates, and historical files.
- [x] Move synthetic fixtures to `modules/cleanroom/fixtures/` and historical captures to `modules/legacy/fixtures/`, reducing tracked root directories from 11 to 10 while preserving fixture contents and updating Kotlin/Swift/build references.
- [x] Group `cleanroom`, `legacy`, `platforms`, and `samples` under `modules/`, reducing tracked root directories from 10 to 7; update Gradle, CI, ignore/attribute rules, docs, and links. Keep the root `api/` ABI baseline in place for Kotlin Gradle validation.

### P0 — Security, reliability and diagnostic consistency

- [x] Redact common URL/credential patterns in untrusted playability status/reason before diagnostics and exception text.
- [x] Add synthetic regression tests covering secrets, bounds and controls without altering ordinary messages.
- [x] Confirm full JVM, Android and iOS CI for exact PR #87 commit `157da30337a4b5c146c8e12b353c5a7ca01a7151`; all six jobs passed in run `42965037745`.
- [x] Audit remaining logs, exceptions and data-class string representations; PR #87 adds synthetic regression coverage for sensitive diagnostics.
- [x] Extend deterministic offline fault injection for retry exhaustion, Retry-After, cancellation during backoff, oversized payloads, media redirects and stale authenticated sessions; covered by merged PRs #80–#82.
- [x] Run one owner-authorized live extraction with a single locally injected HTTP 503. The bounded retry recovered and the resulting audio decoded fully; this does not simulate an upstream outage or live cancellation. See `modules/samples/black-box-comparison/results/owner-home-2026-11-13-live-503-recovery.md`.
- [x] Bound host-supplied `ExcorSession` cookie, visitor-data, region and language values before header use; PR #124 passed all six supported-target jobs at exact head `3fa71184c0b01cb6bc7d12259b973ea1c6f268d8` in run `43115947382`.
- [x] Verify Swift per-request automatic-cookie isolation with a host-supplied `URLSession` on macOS. The populated-cookie-jar regression and XCFramework notice check passed in exact-main CI run `43446716487` on `4e5e67d5e1bfab3b424831e9cd18d3219409da8f`; signed-in live playback remains unverified.
- [x] Pin external GitHub Actions to verified official release commit SHAs, grant ordinary CI only `contents: read`, guard the write-enabled score workflow's `workflow_run` path to repository `main` pushes, and schedule monthly Dependabot checks for action updates.

Acceptance: green CI with no public-API regression; synthetic secrets never appear in diagnostics; proven faults receive explicit typed outcomes. G8/G12 remain partial pending broader upstream-fault/cancellation evidence and release-security verification.

### P1 — Player and direct streaming reliability (highest leverage)

- [ ] Use an opt-in test matrix run by the owner on an authorized network: stable public video IDs, music videos, live content when available, inaccessible/private/deleted cases and videos requiring player-script cipher.
- [ ] Verify signature and n transforms against independently observed player behavior, preserving fail-closed behavior for unsupported variants. Offline probe coverage now accepts signature-only and direct-URL `n` challenges and rejects malformed empty `n`; no live transform has been observed.
- [ ] Obtain signed-out TVHTML5 media that eXcor can actually play; the 2026-11-13 isolated attempt on `dQw4w9WgXcQ` fetched the player script once then returned a reload refusal before media and gives no ciphered-playback credit. The corrected verifier at `4154f8e90095a62c5d45469300ed7afea8b1cc1f` also checks direct-URL `n` challenges and fetches player JavaScript only when a challenge exists; twelve signed-out watch pages exposed neither cipher nor direct-URL `n`, so no transform ran. The forced-TV playback test again stopped at the reload refusal before extraction. See `modules/samples/black-box-comparison/results/owner-home-2026-11-13-tvhtml5-only.md` and the expanded `modules/samples/black-box-comparison/results/owner-home-2026-11-13-cipher-recheck.md`.
- [ ] Run range reads, full ffmpeg decode, mid-file seek and audio/video mux of eligible direct streams; validate content lengths, MIME types, 403 refusal and redirects. The exact-main 2026-11-13 two-video comparison fully decoded and range-checked `dQw4w9WgXcQ` in 6/6 cold/warm attempts for both adapters; `kJQP7kiw5Fk` was `ACCESS_DENIED` before media for eXcor in 6/6 while InnerTubeX passed 6/6. This supplies audio only; video mux and real mid-file/player seeks remain open. See `modules/samples/black-box-comparison/results/owner-home-2026-11-13-main-4e5e67d.md`.
- [x] Verify one signed-out public video on the owner's Windows home network: full audio decode plus HTTP byte-range read, and 480p video/audio mux plus full decode and HTTP byte-range read. Actual player seeking and broader reliability remain open.
- [x] Prefer the script-free VISIONOS client before scripted fallbacks for signed-out playback, and defer player-script loading until required. The latest lazy-watch matched run passed full 3,507,483-byte audio decode and both HTTP byte-range probes in all 3 cold and 3 warm attempts per adapter. First-media p50/p95 was 357/438 ms vs 1,344/1,662 ms cold and 98/106 ms vs 168/187 ms warm (eXcor vs InnerTubeX). This is one video/network from a dirty worktree; request counts are incomplete, HTTP ranges are not player seeks, and it does not establish broader performance parity. See `modules/samples/black-box-comparison/results/owner-home-2026-11-13-lazy-watch.md`.
- [x] Verify synthetic signed-in session replacement and cancellation without copying or uploading account credentials. Session-generation tests reject stale player/media/format results; the next player request uses only the replacement cookie and authorization hash. Live signed-in playback remains unverified.
- [ ] Reproduce each discovered regression in an offline synthetic test before repair.

Acceptance: qualified real playback/seek/cipher evidence on the supported platforms. Report network access denials separately from parser/transport failures. Do not mark G4/G5 passing without reproducible evidence.

### P2 — SABR/UMP decision and implementation track

- [x] Assess whether the minimum transport contract can be defined independently. The current source review found no viewer-side SABR request/response contract or authorized fixtures; document the unknowns and re-entry criteria in `docs/UMP_FRAMING_RESEARCH.md` and closed issue #79.
- [x] Compare three documented options: A) remain direct/HLS-only with an explicit competitive gap; B) implement bounded SABR behind an internal experimental adapter; C) support a separately licensed host-provided transport interface. The dated decision record chooses A as interim scope, defers B and C, and gives re-entry criteria.
- [x] Record a no-go for B until an independently usable specification and authorized test fixtures exist. No SABR negotiation, media decode, or seek path is implied by the offline framing prototype.
- [ ] If the re-entry criteria become available, implement and review bounded framing, bootstrap, audio seek, video selection, and fail-closed error cases separately.
- [ ] Complete adversarial transport testing for oversized/truncated frames, malformed lengths, infinite streams, missing segments, redirects and credential isolation. The offline framing primitive now bounds cumulative response bytes (16 MiB default) across incremental reads and has synthetic oversized/truncated/malformed/continuation coverage; live transport, session, missing-segment, redirect and credential behavior remains unimplemented or unverified.

The feasibility decision is complete; SABR implementation is deferred. G6 remains blocked until authorized audio/video decode and seek evidence satisfies the re-entry criteria. A framing parser alone is not SABR parity.

### P3 — Feature and platform parity

- [x] Reverify public search and channel flows with current owner-network evidence. Signed-out live `LiveVideoInfoTest.searchWithContinuation` and `LiveVideoInfoTest.channelDetailsAndUploads` passed on 2026-11-13; this is one workstation/network.
- [x] Verify YouTube Music songs/albums and one full artist album-shelf pagination flow with owner-network evidence. `LiveVideoInfoTest.youTubeMusicArtistAlbumShelfOpensFullDiscography` passed on 2026-11-13; broader artist/network coverage and Singles & EPs remain open.
- [ ] Add captions/transcripts only with permitted endpoints and independently verifiable fixtures; otherwise expose an explicit unavailable/unsupported result.
- [x] Check JVM, Android, Kotlin/Native iOS, and the separate native Swift eXcorKit individually; do not imply parity without tests. Exact-main CI run `43446716487` passed all nine Linux, Windows, Android API 26/36, preview, Maven publication, Kotlin/Native iOS, XCFramework notice, Swift API/test, and iOS Simulator package jobs. This verifies the supported build/test matrix, not live iOS device playback or broad cross-network playback.
- [ ] Keep optional Spotify/Apple/Deezer catalog expansion subordinate to YouTube extractor reliability.

### P4 — Reproducible black-box competition

- [x] Add an opt-in JVM direct-audio playback runner with paced loopback streaming, a bounded player session, optional duration probe/full decode, URL and redirect checks, and sanitized request/status/Range-offset reports.
- [x] Run one matched signed-out cold direct-audio set for one public ID and network: eXcor full decode and both HTTP offset probes passed in 3/3 samples; InnerTubeX failed extraction before media requests in 3/3. This is diagnostic evidence, not a comparative playback-success or performance result.
- [ ] Extend the owner-run matrix across eligible IDs/networks and verify real backward/forward player seeks. The new exact-main paired run covers two IDs on one Windows home network, with 3 cold + 3 warm samples per adapter; eXcor and InnerTubeX both pass decode/ranges on one ID, while eXcor is access-blocked on the other. Separate same-ID seek reports show eXcor key-driven seeks plus InnerTubeX v0.11.6 transport seeks using different harnesses. Video seeks, same-harness seek timing and another network remain open ([current comparison](../../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-main-4e5e67d.md), [paired seek report](../../../modules/samples/black-box-comparison/results/owner-home-2026-11-13-paired-player-seek.md)). HTTP byte-range probes alone are not player seeks. Keep HLS/DASH and SABR/UMP gaps explicit.
- [x] Add separate public-API adapters for eXcor, InnerTubeX, YouTube.js and NewPipeExtractor; keep GPL-linked comparators in independent processes.
- [x] Add an opt-in paired eXcor/InnerTubeX JVM mode that validates bounded forward/backward HTTP byte offsets and reports them separately from player-seek fields.
- [x] Verify the InnerTubeX v0.11.6 adapter with exact-head CI; PR #120 passed all six jobs at exact head `4e4931cf1011fa8ad37acd89319798365593c59d` in run `43109304794`. YouTube.js and NewPipeExtractor exact-head builds passed in runs `43105597786` and `43102342730`.
- [x] Verify the separate NewPipeExtractor v0.27.1 adapter with exact-head CI; all six jobs passed in run `43102342730`.
- [x] Verify the hardened YouTube.js adapter at its exact head; all six jobs passed in run `43105597786`.
- [ ] Run each under the same machine/network, eligible video set and timeout budget. Record cold/warm p50 and p95 startup, requests, heap/allocation where measurable, success/failure classifications, seek behavior and sample count. The exact-main two-ID run contains 3 cold + 3 warm attempts per adapter and reports per-video outcomes and first-media/decode p50/p95. YouTube.js and NewPipe, another network, JVM startup and same-harness player-seek timings remain outstanding.
- [x] Classify access denial, unsupported transport and parsing faults separately; do not claim competitors failed when the network denies requests. The matched runner preserves per-sample classifications, and the exact-main run records the `kJQP7kiw5Fk` differential as eXcor `ACCESS_DENIED` rather than a decoder failure.
- [x] Update `COMPETITOR_MATRIX.md` with dated evidence and links to sanitized live comparison samples; never publish signed URLs, tokens or sessions.

### P5 — Solo-maintainer release quality

- [ ] Complete clean-room source/fixture provenance and license review; quarantined historical source and captured-service fixtures remain outside this review.
- [x] Gate CI on OSV findings across all resolved root Gradle configurations, the Node comparison adapter lockfile and shipped runtime configurations. Exact-head CI is required before merge.
- [x] Verify reproducible Maven publications with independent CI builders and compare every file hash; keep remote publication authorization separate.
- [x] Add a committed Kotlin ABI baseline and an exact CI compatibility check. The public-API smoke check remains a separate consumer compile check.
- [x] Provide a copyable Kotlin sample, Android/JVM quickstart, clear error guide, changelog and supported/unsupported feature matrix. The matrix retains dates for owner-run live evidence and now identifies the current CI snapshot and remaining release gaps.
- [x] Add a security policy. README links to it; `SECURITY.md` defines private reporting, scope and credential-safe reproduction guidance.
- [ ] Invite a small amount of independent feedback when ready; do not state any active users or adoption without evidence.
- [ ] Request owner authorization before publishing artifacts or making a ready-to-use announcement.

## Go / no-go

**Current outcome: NO-GO** for a ready-to-use InnerTubeX competitor. G6 is missing, player/stream verification is partial, error/security review and comparative tests are incomplete and public publication is not authorized.

Release-candidate criteria:
1. Green supported-target CI with fault/cancellation/security regression coverage.
2. Owner-operated, reproducible player-script and direct-stream live checks (including decode/Range/seek), with clearly separated access denials.
3. Explicit tested transport scope and no implied SABR support without working playback.
4. Completed API/installability and provenance/security/license gates.
5. Current capability and black-box competition matrices with honest samples and limitations.
6. Owner-approved publication decision.

## Execution record

The first implementation slice in this branch adds bounded redaction of YouTube-supplied player status text and synthetic tests. This improves diagnostics safety but does **not** upgrade release gates or claim competitive parity until CI is reviewed.

The JVM quickstart now contains the opt-in direct-audio playback runner. Its code/build status does not upgrade G4/G5: the owner still needs to verify playback, key-driven seeking and full decode on an authorized network, and report only sanitized outcomes.

The clean-room KMP API now has an experimental HLS master-manifest handoff: it solves supported path challenges, applies only host-provided PO tokens, bounds and validates the manifest response, and follows only validated GoogleVideo manifest redirects. Synthetic tests cover URL policy, token handling, session-header isolation and hostile redirects. It does not proxy child playlists or segments. The first signed-out Android 16 attempt returned `UNPLAYABLE` before manifest delivery; successful live HLS playback remains unverified. G5 stays partial and G6 stays blocked.

The Android preview now has a **Try HLS (experimental)** action that feeds `extractHls` output and its media headers to Media3's HLS source. It reports player-ready and sanitized player-error categories. The first owner-operated attempt returned `UNPLAYABLE` before a manifest; the HLS row in `docs/PLAYBACK_EVIDENCE_MATRIX.md` records this failure and keeps successful HLS playback unverified.

The SABR/UMP feasibility decision is recorded in `docs/UMP_FRAMING_RESEARCH.md` and closed issue #79: keep direct plus experimental HLS as the interim transport scope; do not implement an SABR network adapter without the independently usable contract, synthetic vectors, and authorized decode/seek evidence listed there. G6 remains blocked; this decision does not complete the broader competitor goal.

The bounded 2026-11-12 G6 recheck in `docs/UMP_G6_RECHECK_2026-11-12.md` checked public YouTube/Google documentation, HTTP/HLS standards and the fixture inventory. It found no independently usable viewer-side SABR/UMP contract or authorized transport fixture in that scope. This is not a claim that no such specification exists anywhere; the existing no-go and 0/20 gate remain in force.

The 2026-11-13 follow-up found an MIT-licensed public UMP/`/initplayback` description in `davidzeng0/innertube` at `b04d48d000a84befa5d7b018553b04fa4f98ebd8` and recorded it in the G6 recheck and `docs/UMP_FRAMING_RESEARCH.md`. It provides partial request-envelope, part-ID and seek-message schemas, but not a complete ABR/continuation/seek/session contract or authorized media exchange. No code or schemas were copied. This improves the research baseline but does not meet G6 re-entry criteria; G6 remains blocked at 0/20.

The incremental 2026-11-13 [JSR package-doc recheck](../../UMP_G6_RECHECK_2026-11-13.md) found generated SABR/UMP API pages but no version-matched transport contract: the package/SABR page report 5.0.1 while the UMP page reports 4.1.1. Its zero-filled mock-media example is not a service fixture, and the clean-room fixture inventory still has no authorized UMP exchange. G6 remains blocked at 0/20; no code or schema was copied.

The 2026-11-13 artifact review found that existing Maven JARs omitted the repository license and notice. Root JAR/ZIP tasks now package and verify both files under `META-INF`; forced JVM, metadata and source archives pass, as do the local JVM, multiplatform and Android publications. The Android AAR contains both notices and lists QuickJS-KT as a separate runtime dependency, with no QuickJS classes or JNI entries bundled. The macOS XCFramework build now attaches and checks notices at the XCFramework root. Aggregate publication first failed locally because the installed Kotlin/Native 2.4.21 directory lacked its compiler JAR; after restoring the matching cached artifact, `:compileKotlinIosArm64 --rerun-tasks`, `updateKotlinAbi`, and aggregate `publishToMavenLocal` passed. The iOS binary still needs exact-head macOS artifact inspection. The P5 provenance/license item remains open, and no remote publication was performed.

The 2026-11-11 public API feasibility review in `docs/BLACK_BOX_COMPARISON_RESEARCH.md` confirms documented extraction and request-instrumentation surfaces for all four comparison targets. InnerTubeX and NewPipeExtractor are GPL-3.0, so do not add their libraries to eXcor's dependency graph; any separately launched benchmark adapters need licensing review before distribution. The note defines direct-audio and native-transport lanes plus a sanitized result contract. The separate NewPipeExtractor v0.27.1 adapter passed all six exact-head CI jobs in run `43102342730`. The YouTube.js adapter uses only direct-URL audio, disables player retrieval and remote code evaluation, and caps API request/response bodies; all six exact-head jobs passed in run `43105597786`. The InnerTubeX v0.11.6 adapter merged in PR #120 and passed all six exact-head jobs in run `43109304794` at `4e4931cf1011fa8ad37acd89319798365593c59d`. At that stage, no matched measurements existed; G11 remained partial.

The dependency-free report validator in `modules/samples/black-box-comparison/` now enforces the sanitized sample contract, preserves blocked/unsupported/failure outcomes, and calculates p50/p95 only within equivalent adapter, lane, phase and environment groups. CI covers its synthetic input validation. The validator does not run or compare any extractor; the separate public-API adapters are implemented, but owner-run matched measurements are still required; G11 remains partial.

The owner-operated `modules/samples/black-box-comparison/run_matched.py` runner now preflights selected adapters, applies one comparison context, and writes only validated sanitized samples to a new JSONL file. CI checks its CLI without building the owner-installed adapters or making network requests. It improves repeatability for G11 but does not add full decode/seek measurement or owner evidence; G11 remains partial. V8 was removed from the YouTube.js `decoderVersion` field because it is a JavaScript runtime, not a media decoder.

The comparison runner now has an opt-in `--range-probes` mode for exactly the eXcor and InnerTubeX JVM adapters. After the initial range, each adapter requests 4 KiB at a later byte offset and then 4 KiB at an earlier offset; the report keeps these HTTP byte-range outcomes separate from decoder/player seek fields. This measures server range handling only. It does not perform full decode or prove playback seeking, so G11 remains partial pending owner-run decode, player-seek and matched comparison evidence.

The standalone JVM playback diagnostics now enforce one shared 256 MiB media-body budget across `ffplay`, `ffprobe`, and `ffmpeg`, with synthetic regressions for concurrent proxy reservations and prompt child-process termination after the cap is reached. The budget improves the bounds on owner-run live diagnostics; it does not prove playback or promote G8/G4/G5.

A follow-up audit found that `ffprobe` observed the 60-second timeout but not the shared byte-limit signal, so it could stay alive until that timeout after the proxy rejected further body reads. `ffprobe` now uses the same process watcher as `ffplay` and `ffmpeg`; the regression confirms immediate termination when the byte-limit signal is set. This tightens local diagnostics resource bounds without upgrading playback evidence or release-gate status.

The standalone `modules/samples/black-box-comparison/excor-jvm/` adapter now exercises the published eXcor JVM API in a separate process. It measures metadata, extraction and a capped initial range read, with request counts and sanitized JSON output; its CI build and synthetic checks make no YouTube requests. The NewPipe adapter is a separate JVM application with a pinned GPL-3.0-or-later dependency, public-API-only calls, bounded allowlisted networking, no-op upstream logging and sanitized JSON; exact-head CI passed all six jobs in run `43102342730`. The YouTube.js adapter pins MIT package 18.3.0 and uses its public metadata/custom-fetch APIs, selects only direct-URL audio, caps request and response bodies, and never evaluates remote player JavaScript; all six exact-head jobs passed in run `43105597786`. The InnerTubeX adapter uses its pinned public v0.11.6 API in a separate GPL-linked process; all six exact-head CI jobs passed in run `43109304794`.

The eXcor and InnerTubeX JVM comparison adapters now support optional `--full-decode`. The runner records one shared FFmpeg version; each adapter requires a known media size of at most 256 MiB, uses a full direct-audio GET and streams bytes to FFmpeg stdin under the sample timeout. Signed media URLs are not passed to the decoder. The report keeps decode results separate from HTTP byte-range probes and player seek fields. PR #137 merged as `ed0f64b5ff8bcf3389b5f4b9ecdbe0864a5b07a7` after all six supported CI jobs passed at exact head `6af9d8cff0e22de6592c114c06f71aa3db77729c` in run `43153053461`. This improves G11's measurement capability but does not supply owner-run results or playback-seek evidence; G11 remains partial.

## 2026-11-12 — owner-run matched playback follow-up

On one signed-out Windows home-network video, the updated eXcor adapter requested `Range: bytes=0-`, validated the complete object range, and passed full transfer+FFmpeg decode plus both offset probes in three cold samples. InnerTubeX v0.11.6 failed extraction in all three samples before media access, so the run proves repeatable eXcor operation for this case but does not compare successful media playback or performance. The standalone audio and 480p video tests also passed full decode and HTTP range reads. None of these checks drives player seeking. See `modules/samples/black-box-comparison/results/owner-home-2026-11-12-range-full.md`; G4/G5/G11 remain partial.

A second-ID signed-out follow-up on 2026-11-12 was blocked before media: YE7VzlLtp-4 returned YouTube's bot-check reason for audio and 480p video, while BaW_jenozKc returned no metadata. LivePlaybackTest skips only the exact known bot/age access challenge; null metadata remains an unclassified failure. Classifier and wrapper regressions passed; the full JVM suite passed (228 passed, 0 failed, 18 skipped). This adds no playback credit and G5 remains partial.

## 2026-11-13 — paired direct-audio and access differential

The corrected InnerTubeX adapter 0.1.3 and eXcor 0.1.4 both passed full 3,507,483-byte Opus decode and forward/back HTTP byte-range probes in 3/3 cold samples on `dQw4w9WgXcQ`. Two additional IDs (`9bZkp7q19f0`, 4,064,225 bytes; `CevxZvSJLk8`, 4,247,110 bytes) passed the same InnerTubeX checks in 3/3 while eXcor was access-blocked before media in 3/3. Those are access differences, not additional shared media passes. All three IDs were tested on one signed-out home network from an uncommitted worktree; HTTP ranges are not player seeks. See the three sanitized reports under `modules/samples/black-box-comparison/results/`; G11 remains partial.

**Next focused evidence:** continue the owner's playback/cipher matrix in issue #78 and run matched comparisons where access is permitted. The current home-network set has one video with both adapters passing in 3/3; a later rerun on a second video yielded 1/3 eXcor versus 3/3 InnerTubeX full-decode/range passes. Three other videos showed InnerTubeX decode/range passes while eXcor was blocked before media, and one further ID had an InnerTubeX decode timeout after successful range probes. Keep G4/G5 partial until sanitized outcomes prove repeatable playback, decode and seek. Keep G6 blocked until an independently usable SABR service contract and authorized playable fixtures exist.

An earlier signed-out matched comparison on 2026-11-13 used `aqz-KE-bpKQ` (stable public video): InnerTubeX adapter 0.1.3 full-decoded 10,332,653 bytes of Opus and passed both HTTP byte-range probes in 3/3 cold samples; first-media p50/p95 was 1,243/1,319 ms and decode p50/p95 was 1,206/1,279 ms. eXcor 0.1.4 was `BLOCKED/ACCESS_DENIED` before media in 3/3, with zero media requests. A later rerun passed eXcor decode and both probes in 1/3 attempts; InnerTubeX passed 3/3 and two eXcor attempts remained blocked. Both runs are one-network diagnostics from a dirty worktree. G5/G11 remain partial. See `modules/samples/black-box-comparison/results/owner-home-2026-11-13-bbb-audio.md` and `modules/samples/black-box-comparison/results/owner-home-2026-11-13-lazy-watch-aqz.md` with their sanitized JSONL.

References: docs/maintainers/HANDOFF.md, docs/maintainers/EXCOR_PROGRESS.md, docs/release-gates.json, docs/FEATURE_MATRIX.md, docs/COMPETITOR_MATRIX.md, docs/ERROR_HANDLING.md, docs/CLEAN_ROOM.md, PROVENANCE.md.

## 2026-11-13 — fifth-ID matched access differential

- Added `kJQP7kiw5Fk` as a signed-out music-video sample. InnerTubeX passed full 4,678,808-byte Opus decode and forward/backward HTTP byte-range probes in 3/3; eXcor was `BLOCKED/ACCESS_DENIED` before media in 3/3. This is one-network diagnostic evidence and does not add a shared playable ID.
- See `modules/samples/black-box-comparison/results/owner-home-2026-11-13-fifth-video.md`; keep G5/G11 partial and preserve the access-denial classification.


## 2026-11-13 — Java 17 compatibility and runtime recheck

The installed comparison adapters initially failed preflight on the workstation's Java 25 runtime because their Java 26 bytecode used class-file version 70. InnerTubeX v0.11.6's public build config targets JVM 17 with a JDK 21 toolchain. eXcor now builds its library, comparison adapters, quickstart and previews with JDK 21 and Java 17 bytecode. `publishToMavenLocal`, both adapter `installDist` tasks, quickstart/desktop builds, 253 JVM tests (18 skipped), ABI verification and Android compilation passed. The comparison adapters run on the local Java 25 runtime. These diagnostics came from a dirty local worktree and are not public score credit. A repeated `dQw4w9WgXcQ` cold comparison passed full 3,507,483-byte decode and forward/backward byte ranges in 3/3 attempts per adapter; first-media p50/p95 was 1,131/1,348 ms for eXcor and 1,392/1,757 ms for InnerTubeX, and decode p50/p95 was 319/363 ms and 369/369 ms.

A separate stable-public ID, `M7lc1UVf-VE`, yielded eXcor `ACCESS_DENIED` before media in 3/3; InnerTubeX passed both range probes but timed out during full decode after 73,728 bytes in 3/3. This does not add another shared media pass. See the sanitized reports under `modules/samples/black-box-comparison/results/`. G4/G5/G11 remain partial; Java 17 runtime execution, broader eligible IDs/networks and player-seek parity remain unverified.

## 2026-11-13 — signed-out cipher recheck

At source commit `6341b95`, the opt-in first-party watch-page probe requested `dQw4w9WgXcQ`, `kJQP7kiw5Fk`, `aqz-KE-bpKQ` and `M7lc1UVf-VE` signed out on the owner's Windows home network. The responses parsed as playable and exposed 4, 4, 16 and 12 audio formats respectively, but zero audio formats had `signatureCipher`; no `n`/signature transform or media request was attempted. A separate rerun of the forced-TV integration test failed during initial metadata lookup with YouTube's reload refusal, before extraction or media. This is access/client evidence, not a solver pass or failure. G4/G5 and issue #78 remain open; see `modules/samples/black-box-comparison/results/owner-home-2026-11-13-cipher-recheck.md`.
