# Third-party notices

The current repository license is GPL-3.0-only; see [`LICENSE`](../LICENSE). Earlier revisions and contributions retain their Apache-2.0 grants, whose text is at [`docs/licenses/Apache-2.0.txt`](licenses/Apache-2.0.txt). Bundled solver component notices and license texts are in [`NOTICE`](../NOTICE), and the eXcor-specific source/commit record is in [`PROVENANCE.md`](../PROVENANCE.md).

The root Gradle build puts `LICENSE`, `NOTICE` and the preserved Apache-2.0 text at `META-INF/` in JAR and ZIP outputs and checks that all three are present. Both Maven publication CI jobs also check the published Android AAR and its POM in an isolated local Maven repository. The check requires the Android manifest and `classes.jar`, matches all three notice files byte for byte, and checks the GPL-3.0-only POM declaration.

To run the same check, use an empty path for the local Maven repository:

```sh
./gradlew -Dmaven.repo.local="$PWD/build/check-maven" publishToMavenLocal
python3 scripts/check_android_publication.py build/check-maven/com/excor
```

On 2026-11-13, all six Maven POMs were generated with GPL-3.0-only metadata, and the JVM JAR and sources JAR were verified with all three notices. PR #168's macOS CI checks the release XCFramework's root notices and the QuickJS `JS_Eval` symbol separately in its device arm64, simulator arm64, and simulator x86_64 binaries. Remote artifact publication and inspection remain outstanding.

The clean-room Kotlin source includes the yt-dlp/ejs 0.8.0 solver bundles from the PyPI wheel identified in [`PROVENANCE.md`](../PROVENANCE.md). The bundles retain their embedded notices for meriyah (ISC) and astring (MIT). Do not remove those notices when updating the bundles.

The Kotlin Multiplatform SDK also depends on QuickJS-KT 1.0.15. Its native build pins QuickJS and c-vector submodules under MIT; their complete license texts and exact commit identifiers are in [`NOTICE`](../NOTICE) and [`PROVENANCE.md`](../PROVENANCE.md). The release XCFramework packaging step copies those notices alongside the framework. PR #168's macOS check tests `JS_Eval` separately in each device/simulator architecture; it does not inventory every native object or verify a future published package. The separate Swift package uses JavaScriptCore and does not depend on QuickJS-KT.

Synthetic clean-room fixtures under `modules/cleanroom/fixtures/` are test inputs. Older captured service responses in `modules/legacy/fixtures/` are third-party content, are not covered by the repository license, and are not used by the clean-room tests or included in the published library artifact. Their public-repository distribution remains an open rights-review item in `maintainers/QUESTIONS.md`.
