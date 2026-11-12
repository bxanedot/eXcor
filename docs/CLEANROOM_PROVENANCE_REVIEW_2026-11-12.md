# Clean-room provenance and license evidence review — 2026-11-12

**Baseline revision:** `6146d8d69f725010822f2991cf986353fd0312d4`.

**Follow-up review:** working-tree changes on branch `docs/cleanroom-provenance-review`, 2026-11-12 (not yet committed).

## Scope and limits

This is a records and build-configuration review of `cleanroom/`, the synthetic fixture tree, repository license notices, and the clean-room provenance documents. The historical implementation tree and older captured service responses were not inspected. No tests, live service requests, code-similarity analysis, or legal determination were performed. Statements below describe available evidence and gaps, not legal clearance.

## Evidence that supports the clean-room boundary

- The root Gradle build assigns Kotlin source sets to `cleanroom/$name` and reads the bundled solver inputs from `cleanroom/ejs/`; it also records the generated Kotlin source as coming from an identified yt-dlp/ejs revision ([`build.gradle.kts`](../build.gradle.kts#L38-L63), [`build.gradle.kts`](../build.gradle.kts#L109-L123)). This is evidence about this build’s configured inputs; it does not establish how every line was authored.
- [`PROVENANCE.md`](../PROVENANCE.md#L129-L158) records the allowed references, versions or commits, declared licenses, uses, and synthetic-fixture claim. [`docs/CLEAN_ROOM.md`](CLEAN_ROOM.md#L3-L18) records the policy and separates implementation scope from older repository material.
- The same clean-room record discloses that the assistant session which wrote the music and YouTube code had earlier read the historical implementation, while stating it was not consulted during the clean-room work ([`docs/CLEAN_ROOM.md`](CLEAN_ROOM.md#L24-L42)). This is transparent process evidence, but it does not establish strict implementer isolation. A record review cannot independently prove authorship or absence of uncited influence.

## Bundled yt-dlp/ejs solver

The solver files identify yt-dlp/ejs as their generator and include embedded license notices for Unlicense, astring (MIT), and meriyah (ISC). PyPI’s official 0.8.0 page reports the combined license expression `Unlicense AND MIT AND ISC` and an attestation to the yt-dlp/ejs release source.

The baseline had a provenance-linking gap: it recorded PyPI package version 0.8.0 and Git commit `d48c6303a5a6455a42ef9e0b445149cadea51279`, without hashes of the generated files or the exact artifact supplying their bytes. PyPI’s 0.8.0 attestation names source commit `4fb477f4af56880cfd324c48bd4294a2d2294e50`; the separately recorded `d48…` commit is later and only changes workflow files.

For the follow-up, the official PyPI wheel was downloaded and its SHA-256 matched the hash in PyPI's release metadata. Its bundled files were then copied without modification into both package resource trees. The four current local SHA-256 values match the corresponding files inside that wheel:

| File | SHA-256 |
|---|---|
| `cleanroom/ejs/core.min.js` | `18DA6CE0758B416E7AE645084F4F8801F9F9D59D6C477C05EAA0FF94EBD8CC00` |
| `platforms/swift/Sources/eXcorKit/Resources/ExcorEjsCore.js` | `18DA6CE0758B416E7AE645084F4F8801F9F9D59D6C477C05EAA0FF94EBD8CC00` |
| `cleanroom/ejs/lib.min.js` | `C55987FE697E5B9EE18830163F7AF85327E9BB5C3E674B969D38C8D205EAA577` |
| `platforms/swift/Sources/eXcorKit/Resources/ExcorEjsLib.js` | `C55987FE697E5B9EE18830163F7AF85327E9BB5C3E674B969D38C8D205EAA577` |

The repository `.gitattributes` marks all four files `-text`, preventing Git line-ending conversion from changing the upstream bytes. The build comment and [`PROVENANCE.md`](../PROVENANCE.md#L137-L143) identify the wheel, attested source commit, and runtime components. This closes the local source-to-bundle mapping for these files; it does not establish the contents of later Maven or XCFramework release artifacts.

Primary sources: [yt-dlp/ejs LICENSE at recorded commit](https://github.com/yt-dlp/ejs/blob/d48c6303a5a6455a42ef9e0b445149cadea51279/LICENSE), [yt-dlp/ejs 0.8.0 PyPI metadata and attestation](https://pypi.org/project/yt-dlp-ejs/0.8.0/), [0.8.0 tag](https://github.com/yt-dlp/ejs/tree/0.8.0), [PyPI attested source commit](https://github.com/yt-dlp/ejs/tree/4fb477f4af56880cfd324c48bd4294a2d2294e50).

## QuickJS-KT native runtime

The clean-room Gradle build declares `io.github.dokar3:quickjs-kt:1.0.15` as a common dependency. The local provenance table records the wrapper’s Apache-2.0 license and API use; the dependency-license table now distinguishes it from the QuickJS and c-vector native components ([`PROVENANCE.md`](../PROVENANCE.md#L137-L145), [`docs/DEPENDENCY_LICENSES.md`](DEPENDENCY_LICENSES.md#L5-L17)).

The official QuickJS-KT v1.0.15 build metadata pins two native submodules: QuickJS at `04be246001599f5995fa2f2d8c91a0f198d3f34c` and c-vector at `774773d4cb1e66dd736e90e7f482d4f22af464c6`. Its CMake file compiles QuickJS C files; its JVM JAR task packages generated JNI libraries; its Kotlin/Native build creates static libraries consumed by cinterop. eXcor configures its iOS XCFramework as static. So QuickJS is not copied into `cleanroom/`, but compiled QuickJS code is part of the dependency’s platform runtime and is expected to be linked into the static Kotlin/Native framework. The actual published eXcor framework and Maven artifacts were not inspected here, so exact contents and notice propagation still need artifact-level confirmation.

The follow-up adds the full MIT license texts for both pinned native components to the repository [`NOTICE`](../NOTICE), as well as the full Unlicense text for the solver bundle. This improves repository-level notice coverage. The exact published Maven artifacts and iOS XCFramework have still not been inspected, so this review does not establish how each deliverable contains, links, or propagates those notices. The separate Swift package declares no third-party package dependency and uses Apple JavaScriptCore, so QuickJS-KT is not part of that Swift package’s dependency graph ([`platforms/swift/Package.swift`](../platforms/swift/Package.swift), [`PROVENANCE.md`](../PROVENANCE.md#L160-L167)).

Primary sources: [QuickJS-KT v1.0.15 `.gitmodules`](https://github.com/dokar3/quickjs-kt/blob/v1.0.15/.gitmodules), [QuickJS-KT native build](https://github.com/dokar3/quickjs-kt/blob/v1.0.15/quickjs/native/CMakeLists.txt), [QuickJS-KT native library packaging](https://github.com/dokar3/quickjs-kt/blob/v1.0.15/build-logic/src/main/kotlin/com/dokar/quickjs/applyQuickJsNativeBuildTasks.kt), [QuickJS MIT license at pinned commit](https://github.com/bellard/quickjs/blob/04be246001599f5995fa2f2d8c91a0f198d3f34c/LICENSE), [c-vector MIT license at pinned commit](https://github.com/eteran/c-vector/blob/774773d4cb1e66dd736e90e7f482d4f22af464c6/LICENSE), [QuickJS-KT Maven metadata](https://central.sonatype.com/artifact/io.github.dokar3/quickjs-kt).

## Clean-room fixtures

The `fixtures/cleanroom/` inventory contains 15 files: one synthetic watch page, two synthetic player responses, five Apple/iTunes response examples, and seven Spotify response examples. A new [`fixtures/cleanroom/README.md`](../fixtures/cleanroom/README.md) maps each file to its consuming tests and records the declared synthetic origin and placeholder identifiers. It also states that per-file author, creation-date, and generator metadata were not preserved, so the manifest is a maintainer declaration rather than independent proof. During this follow-up, the Spotify identifier found in official API examples was replaced by a repeated-digit placeholder in three fixtures and the corresponding test inputs; this removes that known real sample identifier but does not independently prove every value's origin.

The older captured service-response fixtures are expressly outside this review. Their rights and public-repository distribution remain a separate open item documented in [`docs/THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md#L5-L7) and `maintainers/QUESTIONS.md`.

## Evidence needed to close this review item

1. **Completed in the follow-up:** record the exact PyPI wheel hash and attested source commit, verify the downloaded artifact hash, copy its two JS files byte-for-byte into the Kotlin and Swift trees, record all four file hashes, and disable text normalization for those files.
2. Inspect the actual published JVM/Android artifacts and the iOS XCFramework to record whether QuickJS and c-vector code is embedded or linked and which upstream notices reach each deliverable. The local Maven cache and current working tree are not substitutes for release artifacts.
3. **Partially completed:** add the per-file fixture manifest and remove the known documentation-example ID; preserve stronger origin records if the fixtures are regenerated or recreated. Existing author, creation-date, and generator metadata are unavailable.
4. Keep the implementer disclosure visible. If stronger strict-isolation evidence is a release requirement, obtain a review or rewrite from an implementer who has never seen the historical implementation; this review did not establish that stronger claim.

**Conclusion:** The follow-up closes the local ejs bundle-to-wheel mapping and adds fixture-level declarations and repository notices. The review remains open because published JVM/Android and iOS artifacts were not inspected, older captured service-response fixtures were outside scope, and the available records cannot independently prove strict implementation isolation or every fixture value's origin. This is an engineering evidence review, not legal clearance.
