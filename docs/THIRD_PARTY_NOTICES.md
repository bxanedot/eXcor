# Third-party notices

The repository license is Apache-2.0; see [`LICENSE`](../LICENSE). Bundled solver component notices and license texts are in [`NOTICE`](../NOTICE), and the eXcor-specific source/commit record is in [`PROVENANCE.md`](../PROVENANCE.md).

The root Gradle build places `LICENSE` and `NOTICE` at `META-INF/` in its JAR and ZIP archive tasks and fails those tasks if either entry is missing. This covers the Maven JARs and Android AAR. The JVM, metadata, source JARs, and `eXcor-android-0.2.0.aar` were verified locally. The AAR metadata lists QuickJS-KT as a separate runtime dependency; its `classes.jar` contains no QuickJS classes and the AAR has no `jni/` entries. The macOS CI job now copies both files into the release XCFramework root and checks that they are present; that new check still needs an exact-head CI run.

The clean-room Kotlin source includes the yt-dlp/ejs 0.8.0 solver bundles from the PyPI wheel identified in [`PROVENANCE.md`](../PROVENANCE.md). The bundles retain their embedded notices for meriyah (ISC) and astring (MIT). Do not remove those notices when updating the bundles.

The Kotlin Multiplatform SDK also depends on QuickJS-KT 1.0.15. Its native build pins QuickJS and c-vector submodules under MIT; their complete license texts and exact commit identifiers are in [`NOTICE`](../NOTICE) and [`PROVENANCE.md`](../PROVENANCE.md). The release XCFramework packaging step copies those notices alongside the framework. Its linked binary and notice-bearing output still need inspection on macOS CI before that release gate can pass. The separate Swift package uses JavaScriptCore and does not depend on QuickJS-KT.

Synthetic clean-room fixtures under `modules/cleanroom/fixtures/` are test inputs. Older captured service responses in `modules/legacy/fixtures/` are third-party content, are not covered by Apache-2.0, and are not used by the clean-room tests or included in the published library artifact. Their public-repository distribution remains an open rights-review item in `maintainers/QUESTIONS.md`.
