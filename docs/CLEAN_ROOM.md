# eXcor clean-room policy

The `cleanroom-src/` Kotlin implementation is an independent implementation of the user-provided brief, with owner-approved eXcor naming amendments. It is kept separate from the repository's earlier implementation source.

## Source rules

- Use the brief, applicable standards and documented, permissively licensed public sources listed in the clean-room section of [PROVENANCE.md](../PROVENANCE.md).
- Do not open, clone, search or fetch any repository or local source named in the brief's forbidden list. Do not use GPL, LGPL or AGPL extractor implementations.
- Do not copy competitor source, internal architecture, fixtures or derived algorithms. No competitor implementation was used for this work.
- Record each relied-on external source with version or commit, license, URL and use in [PROVENANCE.md](../PROVENANCE.md).
- Put unresolved scope and behavioral ambiguities in [QUESTIONS.md](../QUESTIONS.md), rather than inventing public API.
- Keep secrets, signed URLs and session values out of logs, diagnostics and `toString()` output.

## Repository boundary

The new Kotlin source is selected from `cleanroom-src/` by the root Gradle build. Existing `src/`, `ejs/`, `app-core/` and `android-app/` content is retained as a separate historical implementation. Its prior provenance record is not a legal clearance and does not establish that it is part of the clean-room build. The app modules currently depend on the older API and need an audited migration before they can consume eXcor.

The old captured responses in `fixtures/` are not used by the clean-room tests. New tests use synthetic data in `fixtures/cleanroom/`. The old captures contain third-party content and remain subject to the limits described in `fixtures/README.md` and `QUESTIONS.md`.

## Naming amendment

The source brief originally names the product VyonEx. The owner's later request controls deliverable naming: Kotlin package `com.excor`, artifact `com.excor:eXcor`, XCFramework `eXcor`, and separate Swift package/module `eXcorKit`. The brief keeps its original names in its detailed contract for traceability and has an amendment at its top.
