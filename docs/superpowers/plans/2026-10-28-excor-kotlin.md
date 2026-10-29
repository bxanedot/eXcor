# Kotlin Excor Library Implementation Plan

> **For agentic workers:** Implement this plan task-by-task with `superpowers:executing-plans`. Keep every step test-first and commit each independently reviewable result.

**Goal:** Implement the section 2–6 Kotlin Multiplatform library from `BRIEF.md` while preserving current files and using a fresh source root.

**Architecture:** The root Gradle project stays the published KMP library (`com.excor:eXcor:0.2.0`) and selects only `cleanroom-src/` for its Kotlin source sets. Existing Android app modules and implementation files stay in the tree; library CI tasks do not compile those apps. Shared behavior lives in `commonMain`; transport and player cache use the JVM and iOS source sets.

**Tech Stack:** Kotlin 2.4.21, Gradle 9.8.2, JDK 27/JVM 26, Ktor 3.6.0, kotlinx.coroutines 1.11.0, kotlinx.serialization-json 1.12.0, QuickJS-KT 1.0.15, and the license-checked yt-dlp EJS solver.

**Spec:** `BRIEF.md` sections 1–7; design: `docs/superpowers/specs/2026-10-28-clean-room-eXcor-design.md`.

## Global Constraints

- Expose the section 3 API concepts and signatures under the requested `com.excor` / `Excor*` names; preserve all current tracked files.
- No source or algorithm from prohibited repositories or existing implementation files.
- Use yt-dlp's Unlicense client ordering and algorithm references; record exact source commits and licenses.
- Never log cookies, visitor data, PO tokens, authorization headers or signed media URLs.
- Only accept HTTPS port 443 `/videoplayback` URLs on the exact GoogleVideo/YouTube host allowlist.
- All network requests use the selected host and preserve timeout/cancellation behavior.
- Do not test through live YouTube while the proxy returns 403; keep live tests opt-in and record this in `QUESTIONS.md`.
- Section 5 amendments name further media/catalog features without section 3 APIs or section 6 acceptance tests. Record this ambiguity in `QUESTIONS.md`; do not invent public APIs in the clean-room library.

## Review Focus

- Malformed/hostile media URLs must fail the allowlist before use.
- Missing, malformed or hostile player JSON/script input must return no stream without leaking secrets.
- Cancellation must propagate through Ktor and QuickJS boundaries.
- Null filesDir, disabled configStore, and failed atomic cache writes must retain functional no-cache behavior.
- Authentication, missing PO tokens and excluded clients must not fall through to a client that violates its token policy.

---

### Task 1: Root build selection and API tests

**Files:** Modify `build.gradle.kts`; add tests under `cleanroom-src/jvmTest` and `cleanroom-src/commonTest`.

**Interfaces:** Root Gradle builds the clean-room source root without deleting or compiling any pre-existing implementation source. Tests assert the renamed section 3 Kotlin/JVM signatures and redacted token string output.

- [ ] Add `ApiCheckTest.apiCompilesAgainstBriefApi`, referring to every renamed section 3 member, and run `./gradlew :jvmTest --tests com.excor.ApiCheckTest`; verify expected unresolved-type failures. The leading `:` keeps Gradle from selecting the existing app module's same-named task.
- [ ] Reconfigure root Kotlin source directories to `cleanroom-src/{commonMain,jvmCommonMain,jvmMain,iosMain,commonTest,jvmTest}` and generated fixture paths.
- [ ] Run `./gradlew :jvmTest --tests com.excor.ApiCheckTest`; expect compile success after API types exist, then keep this test as the compile contract.

### Task 2: Public models, host contracts and PO tokens

**Files:** Add `cleanroom-src/commonMain/kotlin/com/excor/Api.kt`, `TokenProvider.kt` and `cleanroom-src/jvmCommonMain/kotlin/com/excor/ExcorHost.kt`.

**Interfaces:** `ExcorStream`, `ExcorAudioFormat`, `ExcorAudioCandidate`, `ExcorSession`, `ExcorLogLevel`, `ExcorConfigStore`, `ExcorHostServices`, `TokenProviderCapabilities`, `PoTokenResult`, `PoTokenProviderKind`, `TokenProvider`, `ExcorHost`.

- [ ] Add `RedactionTest.poTokenStringOmitsEveryTokenValue` (assert all input token strings are absent and presence/missing markers remain) and `ApiCheckTest.apiCompilesAgainstBriefApi`; observe RED on missing types.
- [ ] Implement only the listed data types and interfaces; confirm GREEN.

### Task 3: URL policy and media selection

**Files:** Add `UrlPolicy.kt`, `Selection.kt` and focused common tests.

**Interfaces:** `isAllowedPlaybackUrl`, `selectAudioFormat`. Link parsing is omitted because the specified API accepts a video ID and the amended section 5 link scope has no contract.

- [ ] Add `UrlPolicyTest.acceptsOnlyHttpsAllowedHostsAndExactPath`, `SelectionTest.requireM4aUsesAutoRanking`, `SelectionTest.lowQualityModePrefersLowestM4a`, `SelectionTest.autoPrefersWebmAndRanksChannelsThenRate`, and `SelectionTest.maxKbpsAbove64IsNotACap`; run targeted tests and observe assertion failures.
- [ ] Implement exact host/path/port checks and bitrate/codec/channel/sample-rate ordering; confirm GREEN.

### Task 4: Client identities and player response parsing

**Files:** Add `Clients.kt`, `PlayerModels.kt`, `PlayerRequest.kt` and common tests.

**Interfaces:** An immutable client descriptor supplies name, profile ID, API client ID/version, user agent, token policy and JavaScript requirement. Selection uses logged-out `TVHTML5`/`VISIONOS`/`WEB` order and logged-in `WEB_EMBEDDED_PLAYER`/`TVHTML5`/`WEB` order, with a fixed three-request cap and skip/exclusion checks.

- [ ] Add `ClientsTest.loggedOutOrderMatchesYtDlp`, `ClientsTest.loggedInOrderMatchesYtDlp`, `ClientsTest.skipMatchesNameOrProfile`, `ClientsTest.attemptsNeverExceedThree`, `PlayerRequestTest.requestUsesCurrentSessionAndCorrectToken`, and `PlayerModelsTest.malformedResponseReturnsNoFormats`; observe RED.
- [ ] Implement request JSON and response decoding from the documented public response shape; record yt-dlp commit `f264a6cbd1f630894da0f368df00f8aae50140e0` in provenance; confirm GREEN.

### Task 5: Player JS, format URLs and URL remembering

**Files:** Add `PlayerJs.kt`, `MediaFormats.kt`, `StreamMemory.kt`, and common tests. Add only the preprocessed EJS source needed by QuickJS after verifying every bundled dependency license.

**Interfaces:** Resolve signature and `n` challenges with the URL-supplied player script; convert response formats into allowed playable formats; remember at most 64 URL/header/video/profile entries.

- [ ] Add `PlayerJsTest.decodesSyntheticSignature`, `PlayerJsTest.transformsSyntheticNValue`, `MediaFormatsTest.resolvesCipherWithoutDroppingQueryParameters`, `MediaFormatsTest.rejectsSabrAndMissingDirectUrls`, and `StreamMemoryTest.rememberForgetCapAndSessionClear`; observe RED.
- [ ] Implement a QuickJS solver based on the allowed yt-dlp/EJS sources, with deterministic malformed-script failure and cancellation propagation; confirm GREEN.

### Task 6: JVM and iOS transports, lifecycle and cache

**Files:** Add `Engine.kt`, `jvmCommonMain/Excor.kt`, `jvmMain/PlayerCache.kt`, `iosMain/Excor.kt`, and platform/common tests.

**Interfaces:** `Excor(host)` exposes section 3 methods; JVM Ktor uses the host OkHttp client and specified timeouts/cache; iOS uses Ktor Darwin and memory cache. `init`/`initialize`, `onSessionChanged`, `exclude`, `headersFor` and `onRefused` follow section 4.1 and 4.5.

- [ ] Add `EngineTest.warmupReturnsImmediatelyAndLogsFailure`, `EngineTest.exclusionExpiresAtTenMinutesWithFakeClock`, `EngineTest.cancellationPropagates`, `EngineTest.sessionChangeClearsMemoryAndExclusions`, `EngineTest.refusedStreamIsForgottenAndExcluded`, `DiskPlayerCacheTest.nullFilesDirDisablesPersistence`, `DiskPlayerCacheTest.disabledConfigDoesNotDisablePlayback`, and `DiskPlayerCacheTest.keepsThreeNewestEntriesAtomically`; observe RED.
- [ ] Implement shared engine and platform adapters; confirm GREEN.

### Task 7: Watch-page fallback, metadata errors and offline tests

**Files:** Add `WatchPage.kt`, `Unplayable.kt`, fixtures under `fixtures/cleanroom/`, and common tests.

- [ ] Add `WatchPageTest.readsEmbeddedPlayerResponseAndScriptUrl`, `WatchPageTest.nonOkPlayabilityReturnsNoStream`, `EngineTest.audioCandidatesReturnsEveryAllowedFormat`, `EngineTest.audioCandidatesReturnsEmptyAfterFailure`, and `WatchPageTest.mapsMimesAndCodecs`; observe RED.
- [ ] Implement watch-page fallback and failure messages, preserving cancellation.
- [ ] Add synthetic fixtures and `EXCOR_LIVE=1` tests for three public music-video IDs. The live run is skipped in this environment because the proxy denied `www.youtube.com`; keep this separate from offline test results.

### Task 8: Provenance, README, CI and Kotlin PR

**Files:** Modify `PROVENANCE.md`, `QUESTIONS.md`, `README.md`, `.github/workflows/ci.yml`; add `cleanroom-src/jvmTest` API check.

- [ ] Record every used source, commit/date, license, dependency and implementation mapping; note YouTube egress denial and any unknown live behavior.
- [ ] Record the under-specified section 5 amendments in `QUESTIONS.md`; scope this PR's implemented contract to the explicit section 3 API and section 4/6 behaviors.
- [ ] Add Windows JDK 21/27 JVM tests/JAR and macOS iOS simulator/framework/Swift API-check jobs without deleting existing project files.
- [ ] Run `./gradlew :jvmTest :jvmJar` locally; distinguish executed JVM tests from platform-disabled tasks. Leave Android app builds outside this clean-room library workflow.
- [ ] Confirm the full offline suite reports nonzero executed test counts and zero failures; separately report live test as blocked/unrun.
- [ ] Commit as `bxane <bxane@proton.me>` on `rewrite-additive`, push only to `bxanedot/eXcor`, and open the Kotlin PR into `main` only after tests pass.
