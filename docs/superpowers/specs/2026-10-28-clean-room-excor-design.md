# Clean-room Excor Implementation Design

## Intent

Deliver the Kotlin Multiplatform Excor library in one reviewable PR, then the independent Swift `ExcorKit` in a second. Both implementations follow `BRIEF.md`; YouTube media remains subject to its URL allowlist and OAuth/PO-token boundaries.

## Goals and boundaries

- Implement the exact public APIs, lifecycle, extraction, format selection, URL validation, cache limits, exclusions, redaction, timeout and client behavior in sections 2–4 and tests in section 6.
- Use only the brief and allowed permissively licensed sources. Do not inspect or use the existing implementation files or prohibited legacy sources.
- Keep every tracked file in the current tree. Do not remove the existing Android app or source files from the PR.
- Add native Swift `ExcorKit` as specified in section 8 after the Kotlin PR is open, despite the withdrawn note in that section, because the task explicitly requests it.
- Do not claim CI or platform results until they run. Linux cannot run Xcode or iOS simulators.

## Approaches considered

1. Modify the current implementation in place. This risks relying on source outside the brief and conflicts with the clean-room requirement.
2. Replace the current tree with the clean-room rewrite. This would remove existing files and conflicts with the instruction to preserve the tree.
3. Keep the existing files intact, add a fresh root Gradle source root (`cleanroom-src/`) selected by the root KMP build, and add `swift/` plus dedicated shared fixtures at the repository root. This produces the requested root artifacts without reading, copying or deleting existing implementation files. **Chosen** because it follows the clean-room constraint and preserves the current tree.

The chosen approach means the root KMP build will compile the new clean-room source root, while existing app modules remain in the repository but are outside the library CI task list until separately ported. The CI workflow will exercise the library and Swift package from the root checkout.

## Components and checks

- Kotlin public/platform API and token types: `cleanroom-src/commonMain`, `jvmCommonMain`, `jvmMain` and `iosMain`; API check from the brief.
- Shared parsing, URL allowlist, player selection, response models and cache/exclusion behavior: common Kotlin source and focused `commonTest` cases.
- JVM Ktor/OkHttp transport and atomic three-entry player cache: JVM source and tests.
- Player JavaScript preparation: QuickJS-KT, an explicitly license-checked yt-dlp EJS solver bundle, and a synthetic deterministic challenge fixture because live YouTube access is blocked in this environment.
- Offline fixtures and tests: `fixtures/cleanroom/`, shared by Kotlin and Swift; synthetic test inputs are labeled as such. Capture tests only use responses actually obtained from YouTube.
- Swift actor, Foundation `URLSession`, `JavaScriptCore`, Swift tests and the macOS CI job: `swift/`.
- Append source, license, commit/date and usage details to `PROVENANCE.md`; append the YouTube egress failure and any unresolved behavior to `QUESTIONS.md`.

## Verification and delivery

PR 1 will be based on `main` as `rewrite-additive`, with the Kotlin library, offline tests, provenance and CI. PR 2 will be based on PR 1 as `rewrite-swift-additive`, with `ExcorKit`, shared fixtures and `swift test`/iOS Simulator CI. Existing branches `rewrite` and `rewrite-swift` are left untouched because their current diffs remove files from `main`.

Each PR description will include chosen client identities and rationale, offline and live test status, open `QUESTIONS.md` items, the commit, skipped work, risks, and that the ponytail skill was unavailable. Live YouTube tests/captures are currently blocked by an HTTP proxy 403. GitHub CLI reports its configured token invalid; native Git read access works, while the dry-run push only established that the existing branch was already up to date, not that creating PRs is authorized.
