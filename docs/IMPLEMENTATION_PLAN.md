# eXcor implementation plan

This plan follows the repository audit and the master engineering plan. Complete items in order and keep the root project buildable. Do not claim support until tests pass on every advertised platform.

## 1. Resolve current verification and integration failures

- The prior macOS failure was reproduced locally as a Kotlin/Native `QuickJs.use` type mismatch; explicit `try/finally` cleanup now compiles on the iOS target. The iOS fixture reader was also corrected and its test source compiles.
- Push these fixes and rerun the iOS simulator, framework, XCFramework and Swift API checks on macOS. If another failure remains, use its authenticated log before changing more code.
- Decide how to preserve/build the older app modules separately or migrate them to the eXcor API. Do not add compatibility stubs to the clean-room public API without a brief-backed contract.
- Keep Linux/Windows JVM and Android library compilation green.

## 2. Close the brief-defined Kotlin deliverable

- Verify every public API item against the owner-renamed brief contract.
- Expand offline and cross-platform tests only where they validate a required behavior or regression.
- Keep live YouTube tests opt-in; document bot-check failures and do not bypass them.
- Complete dependency, fixture-rights and source-provenance review.

## 3. Complete native eXcorKit follow-up

- Keep the Swift package in the separate deliverable and use the names `eXcorKit` / `ExcorKit`.
- Run macOS `swift test`, iOS Simulator package build and the API checks.
- Record JavaScriptCore cancellation and platform limitations accurately.

## 4. Reassess broader product scope

- Decide which catalog, download and provider features have an approved public API and authorization path.
- Add provider modules only after the API, provenance and offline test design are reviewed.
- Add packaging, examples, benchmarks, API docs and release metadata only for verified targets.

## Explicit non-goals until separately specified

No token minting, DRM circumvention, protected-format extraction, account automation, copying competitor internals, or live-test bypasses.
