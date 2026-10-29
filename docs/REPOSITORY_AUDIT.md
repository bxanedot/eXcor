# Repository audit

Audit snapshot: branch `rewrite-additive`, starting commit `e11f038` (2026-10-29); findings below include the local reproductions and fixes from this review. This is an engineering inventory, not a legal opinion or a release approval.

## Existing layout

- Root Gradle project now builds the independent Kotlin implementation from `cleanroom-src/` with package `com.excor` and artifact `com.excor:eXcor:0.2.0`.
- `src/`, root `ejs/`, `app-core/`, and `android-app/` contain the earlier implementation and app. They are preserved and are not included in the clean-room root source sets.
- `fixtures/` contains earlier captured service responses; `fixtures/cleanroom/` contains synthetic test inputs for the clean-room implementation.
- `swift/` is added on the follow-up `rewrite-swift-additive` branch, not in the Kotlin PR branch. It is not part of this snapshot.
- Root license is Apache-2.0. The root `NOTICE` records bundled solver dependencies. The old fixture rights and the historical source provenance still need review before release.

## Integration and build findings

- `:jvmTest` passes locally with JDK 27 and the toolchain property used by CI.
- Running `:app-core:jvmTest` fails at `:app-core:compileKotlinJvm`: app-core imports old `VyonEx*` models that the new root `com.excor` build does not expose. The clean-room CI intentionally tests the library, not the app modules; app integration is still unresolved.
- PR #41 passes Linux and Windows at `e11f038`; its macOS job failed in the combined “Kotlin tests, iOS targets, frameworks and XCFramework” step. The public job page exposes only “Process completed with exit code 1”. Local cross-target compilation reproduced the first failure: `QuickJs` is not `AutoCloseable` on Kotlin/Native, so common `use` did not compile. Explicit `try/finally` cleanup fixes it. Compiling the iOS test source then exposed a stale Foundation factory call in the fixture reader; that call and its opt-in are fixed as well. Both iOS main and test sources now compile on Linux, but simulator execution and framework linking still require macOS.
- The older PR #40 also has failed Windows, macOS and Linux desktop jobs; both Android device matrix jobs pass. It is a separate draft branch and is not part of this clean-room Kotlin change.
- The cloud machine cannot run iOS simulator tests or link Apple frameworks. YouTube player requests may receive a bot-check response; live tests stay opt-in.

## Clean-room review boundary

The implementation is isolated from the historical source tree and uses only the sources recorded for the clean-room addition in `PROVENANCE.md`. The forbidden repositories and GPL/LGPL/AGPL extractor sources were not opened or used. This audit read project documentation and build configuration and ran the app-core compiler to verify module compatibility; it did not inspect the old extractor implementation.

## Priority findings

1. Push the native compile fixes and verify iOS tests, framework links, XCFramework assembly and Swift API check on macOS CI. If that run still fails, obtain its authenticated log before changing more code.
2. Decide how to migrate or separately build the preserved app modules without mixing their API or provenance into the clean-room artifact.
3. Complete a source-level provenance, dependency-license and fixture-rights review before any release claim.
4. Keep the advertised API and feature matrix aligned with the functions actually implemented and tested.
