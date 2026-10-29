# eXcor implementation status

Updated 2026-10-29. This repository is in draft development; it is not a verified multi-platform release. The native compile fixes below are local and await a fresh macOS CI run.

## Deliverables

| Deliverable | State |
|---|---|
| Clean-room Kotlin API and audio engine | Implemented in `cleanroom-src/`; PR #41 is open |
| Offline Kotlin JVM tests | Pass locally with JDK 27 (`:jvmTest`) |
| iOS main and test source compilation | Pass locally for `iosSimulatorArm64` targets; simulator execution still requires macOS |
| Linux / Windows CI | Pass on PR #41 at commit `e11f038` |
| macOS KMP / iOS / XCFramework CI | Previous run failed; local cross-target compile failures are fixed, macOS rerun pending |
| Android app integration | Not working against the new root API; `:app-core:jvmTest` still fails because historical `Excor*` model signatures do not match the clean-room API |
| Native Swift `eXcorKit` | Implemented on follow-up branch `rewrite-swift-additive`; no PR opened yet; macOS verification still required |
| Clean-room source provenance | Recorded in the clean-room section of `PROVENANCE.md`; full repository and dependency audit remains open |

## Latest checks observed

- PR #41 uses commit `e11f038`: Linux and Windows jobs succeeded, macOS job failed. The public job page reports `Process completed with exit code 1` for “Kotlin tests, iOS targets, frameworks and XCFramework”; the exact failing task is not visible without sign-in.
- PR #40 is a separate, older draft at commit `eb698c2`: Windows, macOS and Linux desktop jobs failed; Android device jobs for API 26 and 36 succeeded.
- Local verification after the fixes: `:compileKotlinIosSimulatorArm64`, `:compileTestKotlinIosSimulatorArm64`, `:jvmTest` and `:jvmJar` pass. `:iosSimulatorArm64Test` and framework linking cannot run on Linux. `:assembleAndroidMain` could not be run locally because no Android SDK is installed. `:app-core:jvmTest` still fails because app-core imports the older API.

## Risks and limits

- The root build currently selects only `cleanroom-src/`, so the preserved app modules cannot resolve their earlier API.
- The previous macOS check’s full logs are unavailable in this unauthenticated session. Local reproduction identified and fixed the first two iOS compilation issues; the next CI run must confirm the native job.
- Cloud YouTube requests can receive a bot-check response; live extraction tests are opt-in and no workaround is used.
- Older captured fixtures are third-party content and remain subject to the rights-review question in `QUESTIONS.md`.
- Nothing in this status establishes release readiness or legal clearance.
