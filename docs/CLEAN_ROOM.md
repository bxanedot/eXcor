# eXcor clean-room policy

The `modules/cleanroom/` Kotlin implementation is an independent implementation of the user-provided brief, with owner-approved eXcor naming amendments. It is kept separate from the repository's earlier implementation source.

The current distribution license is GPL-3.0-only. This license change does not change the independent-source policy below or permit copying competitor implementation code.

## Source rules

- Use the brief, applicable standards and documented, permissively licensed public sources listed in the clean-room section of [PROVENANCE.md](../PROVENANCE.md).
- Do not open, clone, search or fetch any repository or local source named in the brief's forbidden list. Do not use GPL, LGPL or AGPL extractor implementations.
- Do not copy competitor source, internal architecture, fixtures or derived algorithms. No competitor implementation was used for this work.
- Record each relied-on external source with version or commit, license, URL and use in [PROVENANCE.md](../PROVENANCE.md).
- Put unresolved scope and behavioral ambiguities in [QUESTIONS.md](maintainers/QUESTIONS.md), rather than inventing public API.
- Keep secrets, signed URLs and session values out of logs, diagnostics and `toString()` output.

## Repository boundary

The new Kotlin source is selected from `modules/cleanroom/` by the root Gradle build. Existing `modules/legacy/src/` and `modules/legacy/ejs/` content is retained as a separate historical implementation. Its prior provenance record is not a legal clearance and does not establish that it is part of the clean-room build. Nothing compiles it: the apps that used it, and the `historical-engine/` module that built it for them, were removed on 2026-11-03. The preview apps that replaced them use only the clean-room library.

The old captured responses in `modules/legacy/fixtures/` are not used by the clean-room tests. New tests use synthetic data in `modules/cleanroom/fixtures/`. The old captures contain third-party content and remain subject to the limits described in `modules/legacy/fixtures/README.md` and `maintainers/QUESTIONS.md`.

## Naming amendment

The source brief's naming amendment controls the new deliverables: Kotlin package `com.excor`, artifact `com.excor:eXcor`, XCFramework `eXcor`, and separate Swift package/module `eXcorKit`. API names in sections 2–8 use this mapping. Section 1 retains exact identifiers for forbidden historical sources. Existing historical implementations remain separate and are not thereby API-compatible with the clean-room deliverables.

## Music engine (`modules/cleanroom/commonMain/kotlin/com/excor/music/`, 2026-11-01)

The music engine was written from:
- the providers' official documentation (Spotify Web API reference and its 2026 changelogs and migration guide, Apple Music API and MusicKit documentation, Deezer's public API responses);
- standards (RFC 6381, RFC 8216bis, ISO/IEC 23001-7 and 23009-1 descriptors, DASH-IF system IDs, FIPS 180-4, RFC 7636);
- live responses from the keyless Deezer and iTunes endpoints, read only for field names.

The reference projects named in the brief were read only at README level:
- Stash (GPL-3.0): its features only;
- spotify-downloader (MIT, archived): its accepted input identifiers only, with its credential and decryption sections skipped;
- DRML (CC0): its terminology only.

No code of theirs was opened.

**Disclosure.** The engine was implemented by the same assistant session that earlier wrote, and read in full, the historical engine in `modules/legacy/src/`, including that engine's Spotify, Deezer and Apple Music catalog code. During this work `modules/legacy/src/` was not opened or consulted. The music engine's structure and tests were written afresh against the sources above.

However, the separation rests on that working practice, not on an implementer who never saw the historical code. Anyone who needs strict implementer isolation should treat this package as not meeting it, and have it reviewed or reimplemented by someone who has not seen `modules/legacy/src/`.

Its fixtures are synthetic test inputs stored under `modules/cleanroom/fixtures/`. The per-file manifest maps them to their offline tests and records the declared origin. This is a process record, not independent proof of authorship or legal clearance.

`VideoInfo.kt`, `YouTubeSearch.kt`, `YouTubePlaylist.kt`, `YouTubeChannel.kt`, `YouTubeWatch.kt`, `YouTubeMusic.kt` and `InnerTube.kt` (YouTube metadata, search, playlists, channels, related videos and comments, 2026-11-01) fall under the same disclosure. It reads YouTube's own player response, using fields observed live (`videoDetails`, `microformat.playerMicroformatRenderer`, `playabilityStatus`), and reuses the clean-room client identities. `modules/legacy/src/` and InnerTubeX were not opened.
