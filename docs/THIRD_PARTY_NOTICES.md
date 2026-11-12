# Third-party notices

The repository license is Apache-2.0; see [`LICENSE`](../LICENSE). Bundled solver component notices and license texts are in [`NOTICE`](../NOTICE), and the eXcor-specific source/commit record is in [`PROVENANCE.md`](../PROVENANCE.md).

The clean-room Kotlin source includes the yt-dlp/ejs 0.8.0 solver bundles from the PyPI wheel identified in [`PROVENANCE.md`](../PROVENANCE.md). The bundles retain their embedded notices for meriyah (ISC) and astring (MIT). Do not remove those notices when updating the bundles.

The Kotlin Multiplatform SDK also depends on QuickJS-KT 1.0.15. Its native build pins QuickJS and c-vector submodules under MIT; their complete license texts and exact commit identifiers are in [`NOTICE`](../NOTICE) and [`PROVENANCE.md`](../PROVENANCE.md). Include these notices when distributing an eXcor Kotlin/Native framework that links the runtime. The separate Swift package uses JavaScriptCore and does not depend on QuickJS-KT.

Synthetic clean-room fixtures are test inputs. Older captured service responses in `fixtures/` are third-party content, are not covered by Apache-2.0, and are not used by the clean-room tests or included in the published library artifact. Their public-repository distribution remains an open rights-review item in `maintainers/QUESTIONS.md`.
