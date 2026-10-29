# Swift eXcorKit Package Implementation Plan

> **For agentic workers:** Implement after the Kotlin PR is open. Use `superpowers:executing-plans`; keep each test-first step independently verifiable.

**Goal:** Deliver the pure Swift `eXcorKit` package in a second additive PR, matching the renamed section 3 API and section 8 behavior.

**Architecture:** The `eXcorKit` module uses an `ExcorKit` actor that owns the host, `URLSession`, challenge runtime, in-memory cache and stream exclusions. `Foundation` handles transport/JSON/URLs and `JavaScriptCore` executes the player script and allowed EJS bundle. Kotlin files are not imported or translated as code.

**Tech Stack:** Swift Package Manager; iOS 15 and macOS 12; Foundation, URLSession, JavaScriptCore and XCTest/Swift Testing; no package dependencies.

**Spec:** `BRIEF.md` sections 1, 3–6 and 8; design: `docs/superpowers/specs/2026-10-28-clean-room-eXcor-design.md`.

## Global Constraints

- Build after Kotlin PR is open and branch from that PR as `rewrite-swift-additive`.
- Preserve all current files and put the package in root `swift/`, use `Excor*` public types, and name the module/product `eXcorKit`.
- Match exact Swift-style names/signatures in section 8 and the iOS extraction/fallback behavior in section 4.
- Keep fixture inputs shared from root `fixtures/cleanroom/`; test code resolves this path relative to its source file.
- No token/cookie/log leaks; exact HTTPS playback URL allowlist; propagate task cancellation.
- Do not claim local iOS build results on Linux; rely on the required macOS CI job.
- Section 5's added catalog/download features have no Swift API contract in section 8 beyond the audio extraction behavior; keep them documented as open scope questions rather than guessing.

## Review Focus

- Concurrent actor calls must not cross-contaminate sessions, exclusions or remembered URLs.
- URLSession errors, cancellation and malformed JSON must not become successful streams.
- JavaScriptCore errors and values must be bounded and must not expose tokens.
- A missing token provider must skip clients that require PO tokens and still permit eligible tokenless clients.
- Fixture lookup must work in both local `swift test` and GitHub Actions package checkouts.

---

### Task 1: Swift package skeleton and value/API tests

**Files:** Add `swift/Package.swift`, `swift/Sources/ExcorKit/Types.swift`, `swift/Sources/ExcorKit/ExcorKit.swift`, and `swift/Tests/ExcorKitTests/ApiTests.swift`.

**Interfaces:** `ExcorStream`, `ExcorAudioCandidate`, `ExcorAudioFormat`, `ExcorSession`, `ExcorLogLevel`, `ExcorConfigStore`, `ExcorKitHost`, `PoTokenProvider`, `PoTokenResult`, `PoTokenProviderKind`, `ExcorKit` actor.

- [ ] Write `ApiTests.publicAPICompilesWithSection8Names` and `RedactionTests.poTokenDescriptionOmitsSecretValues`; run `swift test --package-path swift`, expect missing-symbol compilation failures.
- [ ] Implement section 8 value types and protocols and verify macOS `swift test`.

### Task 2: URL allowlist, selection and stream state

**Files:** Add `Links.swift`, `Selection.swift`, `URLPolicy.swift`, and focused tests.

- [ ] Write `URLPolicyTests.acceptsOnlyExactAllowedPlaybackURL`, `SelectionTests.requireM4aUsesAutoRanking`, `SelectionTests.lowQualityPrefersLowestM4a`, `SelectionTests.autoRanksCodecChannelsAndSampleRate`, and `SelectionTests.maxKbpsAbove64IsNotACap`; observe RED.
- [ ] Implement only those tested helpers with exact section 4 behavior; confirm GREEN.

### Task 3: Clients, player JSON, JavaScript challenges and HTTP

**Files:** Add `Clients.swift`, `YouTube.swift`, `PlayerJS.swift`, and `URLSessionTransport.swift`.

**Interfaces:** Client ordering and token policy match the Kotlin plan. The actor tries eligible identities in order, with at most three requests. URLSession uses 30-second request, 15-second connect, 20-second resource/socket timeouts and optional host-provided session.

- [ ] Write `ClientsTests.loggedOutAndAuthenticatedOrdersMatchYtDlp`, `RequestTests.usesCurrentSessionAndToken`, `ResponseTests.rejectsMalformedPlayerData`, `PlayerJSTests.transformsSyntheticSignatureAndN`, and `TransportTests.propagatesCancellation`; observe RED.
- [ ] Implement the documented request/response flow, run the player in JavaScriptCore, enforce URL policy and verify GREEN.

### Task 4: Watch page, cache, lifecycle and parity tests

**Files:** Add watch-page/fallback behavior in `YouTube.swift`, actor state in `ExcorKit.swift`, and tests under `swift/Tests/ExcorKitTests/`.

- [ ] Write `WatchPageTests.readsEmbeddedResponseAndRejectsNonOkStatus`, `EngineTests.audioCandidatesReturnsAllAllowedAudio`, `EngineTests.exclusionExpiresAfterTenMinutes`, `EngineTests.refusedURLIsForgotten`, `EngineTests.sessionChangeClearsActorState`, and `EngineTests.fallbackPreservesCancellation`; observe RED.
- [ ] Implement iOS-style fallback and in-memory cache behavior; confirm GREEN.
- [ ] Add opt-in three-video ranged live tests. Mark them unrun/blocked when the current environment's YouTube proxy denial persists.

### Task 5: Provenance, README, CI and Swift PR

**Files:** Modify `PROVENANCE.md`, `QUESTIONS.md`, `README.md`, `.github/workflows/ci.yml`; add the `swift` package.

- [ ] Record all Swift sources/dependencies and shared fixture origin/license.
- [ ] Carry the section 5 scope question from the Kotlin PR into `QUESTIONS.md`; do not invent unrequested Swift public API.
- [ ] Add macOS `swift test --package-path swift` and unsigned iOS Simulator `xcodebuild` checks; preserve the Kotlin macOS checks.
- [ ] Run Linux Kotlin tests again, then rely on macOS CI for Swift tests and iOS compilation. Report each executed, skipped and blocked result separately.
- [ ] Confirm macOS CI executes a nonzero Swift test count, `swift test`, and an unsigned iOS Simulator build before claiming completion.
- [ ] Commit as `bxane <bxane@proton.me>` on `rewrite-swift-additive`, push only to `bxanedot/eXcor`, and open the second PR into `main` once Kotlin PR is open.
