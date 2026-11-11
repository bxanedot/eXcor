# eXcor: competitor engineering plan — 2026-11-10

**Goal:** make the Apache-2.0 clean-room eXcor library a credible, independently reliable alternative to InnerTubeX for supported YouTube and YouTube Music extraction. Success means verifiable streams, stable API, secure operation, installability and low solo-maintainer burden — not hype or the number of APIs.

**Owner/audience:** solo maintainer; no known external users. Primary outcomes are open-source quality and reputation.

## Baseline and honest limits

- Main baseline: bbeee33f70e1ced3987e0ff1c70163310c108af2; artifact com.excor:eXcor:0.2.0, Kotlin Multiplatform JVM/Android/iOS, separate native Swift package and unpublished preview apps.
- Search, metadata, channels, comments, YouTube Music, direct audio and video formats have offline tests and qualified live evidence. Playback is frequently blocked by YouTube's own access checks, so live success must not be generalized.
- The repository's release-gates.json reports G1, G2, G3, G7 and G9 passing; G4, G5, G8, G10, G11 and G12 partial; G6 (SABR/UMP) blocked.
- InnerTubeX's public README documents direct/HLS/SABR transport, UMP seeking, cipher handling, isolated sessions, token-provider interfaces and diagnostics. Its 2026-11-07 changelog shows ongoing iOS and streaming work. eXcor does not currently demonstrate parity in all of those areas.
- YouTube.js serves JavaScript/Deno/browser developers; NewPipeExtractor targets Java and several content services. Platform breadth alone does not prove extractor performance.

**Nonnegotiable boundaries:** Only implement in the clean-room source tree, not quarantined historical src/ or root ejs/. Never copy GPL/LGPL/AGPL extractor source, including InnerTubeX or NewPipeExtractor. Use competitor README and observable public interfaces only for requirements. Do not evade bot checks, consent or DRM. Do not store or log credentials, signed URLs, private captures or token values. Remote release and publishing require owner authorization.

## Priority and sequence

### P0 — Security, reliability and diagnostic consistency

- [x] Redact common URL/credential patterns in untrusted playability status/reason before diagnostics and exception text.
- [x] Add synthetic regression tests covering secrets, bounds and controls without altering ordinary messages.
- [x] Confirm full JVM, Android and iOS CI for exact PR #87 commit `157da30337a4b5c146c8e12b353c5a7ca01a7151`; all six jobs passed in run `42965037745`.
- [x] Audit remaining logs, exceptions and data-class string representations; PR #87 adds synthetic regression coverage for sensitive diagnostics.
- [x] Extend deterministic offline fault injection for retry exhaustion, Retry-After, cancellation during backoff, oversized payloads, media redirects and stale authenticated sessions; covered by merged PRs #80–#82.

Acceptance: green CI with no public-API regression; synthetic secrets never appear in diagnostics; proven faults receive explicit typed outcomes. G8/G12 remain partial pending owner-run live fault verification and broader release-security evidence.

### P1 — Player and direct streaming reliability (highest leverage)

- [ ] Use an opt-in test matrix run by the owner on an authorized network: stable public video IDs, music videos, live content when available, inaccessible/private/deleted cases and videos requiring player-script cipher.
- [ ] Verify signature and n transforms against independently observed player behavior, preserving fail-closed behavior for unsupported variants.
- [ ] Run range reads, full ffmpeg decode, mid-file seek and audio/video mux of eligible direct streams; validate content lengths, MIME types, 403 refusal and redirects.
- [ ] Verify signed-in session replacement and cancellation without copying or uploading account credentials.
- [ ] Reproduce each discovered regression in an offline synthetic test before repair.

Acceptance: qualified real playback/seek/cipher evidence on the supported platforms. Report network access denials separately from parser/transport failures. Do not mark G4/G5 passing without reproducible evidence.

### P2 — SABR/UMP decision and implementation track

- [x] Assess whether the minimum transport contract can be defined independently. The current source review found no viewer-side SABR request/response contract or authorized fixtures; document the unknowns and re-entry criteria in `docs/UMP_FRAMING_RESEARCH.md` and closed issue #79.
- [x] Compare three documented options: A) remain direct/HLS-only with an explicit competitive gap; B) implement bounded SABR behind an internal experimental adapter; C) support a separately licensed host-provided transport interface. The dated decision record chooses A as interim scope, defers B and C, and gives re-entry criteria.
- [x] Record a no-go for B until an independently usable specification and authorized test fixtures exist. No SABR negotiation, media decode, or seek path is implied by the offline framing prototype.
- [ ] If the re-entry criteria become available, implement and review bounded framing, bootstrap, audio seek, video selection, and fail-closed error cases separately.
- [ ] Test oversized/truncated frames, malformed lengths, infinite streams, missing segments, redirects and credential isolation before any live transport claim.

The feasibility decision is complete; SABR implementation is deferred. G6 remains blocked until authorized audio/video decode and seek evidence satisfies the re-entry criteria. A framing parser alone is not SABR parity.

### P3 — Feature and platform parity

- [ ] Reverify search, channels, YouTube Music songs/albums/discography and continuations with documented owner-network evidence.
- [ ] Add captions/transcripts only with permitted endpoints and independently verifiable fixtures; otherwise expose an explicit unavailable/unsupported result.
- [ ] Check JVM, Android, Kotlin/Native iOS, and the separate native Swift eXcorKit individually; do not imply parity without tests.
- [ ] Keep optional Spotify/Apple/Deezer catalog expansion subordinate to YouTube extractor reliability.

### P4 — Reproducible black-box competition

- [x] Add an opt-in JVM direct-audio playback runner with paced loopback streaming, a bounded player session, optional duration probe/full decode, URL and redirect checks, and sanitized request/status/Range-offset reports.
- [ ] Owner-run the runner on an authorized network and verify real backward/forward key-driven seeks, Range behavior, and full decode for eligible streams. Keep HLS/DASH and SABR/UMP gaps explicit.
- [ ] Make a comparator that uses only published public APIs of eXcor, InnerTubeX, YouTube.js and NewPipeExtractor.
- [ ] Run each under the same machine/network, eligible video set and timeout budget. Record cold/warm p50 and p95 startup, requests, heap/allocation where measurable, success/failure classifications, seek behavior and sample count.
- [ ] Classify access denial, unsupported transport and parsing faults separately; do not claim competitors failed when the network denies requests.
- [ ] Update COMPETITOR_MATRIX.md with dated evidence and precise source links; never publish signed URLs, tokens or sessions.

### P5 — Solo-maintainer release quality

- [ ] Complete source/fixture provenance and license review, API/ABI compatibility, dependency vulnerability scan and reproducible builds.
- [ ] Provide copyable Kotlin sample, Android/JVM quickstart, clear error guide, changelog, security policy and accurate supported/unsupported feature matrix.
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

The clean-room KMP API now has an experimental HLS master-manifest handoff: it solves supported path challenges, applies only host-provided PO tokens, bounds and validates the manifest response, and follows only validated GoogleVideo manifest redirects. Synthetic tests cover URL policy, token handling, session-header isolation and hostile redirects. It does not proxy child playlists or segments and has no live HLS playback evidence; G5 stays partial and G6 stays blocked.

The Android preview now has a **Try HLS (experimental)** action that feeds `extractHls` output and its media headers to Media3's HLS source. It reports player-ready and sanitized player-error categories. It adds an owner-operated way to exercise HLS, not live evidence; the HLS row in `docs/PLAYBACK_EVIDENCE_MATRIX.md` remains NOT RUN.

The SABR/UMP feasibility decision is recorded in `docs/UMP_FRAMING_RESEARCH.md` and closed issue #79: keep direct plus experimental HLS as the interim transport scope; do not implement an SABR network adapter without the independently usable contract, synthetic vectors, and authorized decode/seek evidence listed there. G6 remains blocked; this decision does not complete the broader competitor goal.

**Next focused evidence:** complete the owner's authorized playback/cipher matrix in issue #78. Keep G4/G5 partial until sanitized outcomes prove repeatable playback, decode and seek. Keep G6 blocked until an independently usable SABR service contract and authorized playable fixtures exist.

References: HANDOFF.md, EXCOR_PROGRESS.md, docs/release-gates.json, docs/FEATURE_MATRIX.md, docs/COMPETITOR_MATRIX.md, docs/ERROR_HANDLING.md, docs/CLEAN_ROOM.md, PROVENANCE.md.
