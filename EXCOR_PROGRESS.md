# eXcor progress

The continuation file for the production-engineering directive of 2026-11-02. If work is interrupted, resume from **Next tasks**; don't repeat the audit.

**Related files:**
- `docs/EXCOR_ENGINEERING_AUDIT.md`: the subsystem inventory.
- `docs/release-gates.json`: the G1–G12 status, evidence and commands.
- `docs/COMPETITOR_MATRIX.md`: the comparison with InnerTubeX.

## Current objective

Finish the YouTube and YouTube Music surface that this environment can verify, keep the library installable, and record precisely what is blocked.

## Release gates

| Gate | Status | Note |
|---|---|---|
| G1 Existing eXcor preserved | Passing | Additive changes only |
| G2 Build correctness | Passing | Now includes the shared metadata compile |
| G3 InnerTube operations | Passing | Live-verified |
| G4 Player compatibility | Blocked | Bot check: no playable player response here |
| G5 Streaming | Blocked | `LivePlaybackTest` fails here for that reason |
| G6 SABR/UMP | Blocked | Not implemented; nothing to observe here |
| G7 YouTube Music | Passing | For what this environment can observe; songs and albums are withheld |
| G8 Reliability | Partial | Typed failures after bounded retries, WARN diagnostics; no live fault injection |
| G9 Developer usability | Passing | `samples/jvm-quickstart` runs live from the local publish |
| G10 Release packaging | Partial | Local publish works; no remote release is authorized |
| G11 Competitive analysis | Partial | InnerTubeX from its README; YouTube.js and NewPipeExtractor document only platforms, services and installation |
| G12 Security | Partial | CI OSV scan gates on shipped dependencies (clean); tooling and test findings are report-only |

**Not a release candidate:** G4–G6 are blocked in this environment, and the directive's acceptance standard requires real playback validation.

## Completed in this pass

- **YouTube Music** (`YouTubeMusic.kt`): typed search, suggestions, artist pages, playlists and radio. Live-verified.
- **`excorDefaultHost()`:** constructing `Excor` needs no boilerplate.
- **Publishing fix:** the library couldn't be published (shared metadata compile error). It is fixed, and CI now publishes and builds a consumer.
- **`samples/jvm-quickstart`:** a standalone consumer that follows the README.
- **README:** Installation and Quick start sections.
- **`videoInfo` watch-page fallback:** it returned null for 8 of 10 videos from this environment.
- **InnerTube read retries:** 429, 5xx and transport errors; three attempts.
- **YouTube benchmark:** results in `docs/PERFORMANCE.md`.
- **Merged earlier today:** mixes and the failed-tab fix (bxanedot/eXcor#54), playlist conversion and the iTunes budget (bxanedot/eXcor#55).

## Commands

- **Offline suite and targets:**
  `./gradlew :jvmTest :assembleAndroidMain :compileKotlinIosArm64 :compileKotlinIosSimulatorArm64 :compileKotlinIosX64 :compileCommonMainKotlinMetadata`
  Latest: 150 tests, 14 skipped (the opt-in live tests and benchmarks), 0 failed.
- **Live YouTube and YouTube Music:**
  `EXCOR_LIVE=1 ./gradlew :jvmTest --tests com.excor.LiveVideoInfoTest`
  Latest: 7 of 7 pass; the benchmark is opt-in (`EXCOR_BENCH=1`).
- **Live music:** `EXCOR_LIVE=1 ./gradlew :jvmTest --tests com.excor.music.LiveMusicTest`
  Latest: pass. Spotify and the full Apple Music API are skipped without credentials.
- **Consumer:** `./gradlew publishToMavenLocal && ./gradlew -p samples/jvm-quickstart run`
  Latest: all six steps "ok".
- **Playback:** `EXCOR_LIVE=1 ./gradlew :jvmTest --tests com.excor.LivePlaybackTest`
  Fails here (bot check). It must be run on a normal network.

## Known blockers

- **Bot check:** YouTube answers every playback client from this environment with `LOGIN_REQUIRED` ("Sign in to confirm you're not a bot") or "unavailable". There is no `streamingData` and no SABR URL, so G4–G6 can't be verified here, and eXcor does not bypass bot checks.
- **Withheld YouTube Music catalog:** songs, albums and discographies aren't offered to this environment.
- **Maven Central 429s:** builds here use a local init script that serves a few modules from `~/.m2`. CI is unaffected.

## Next tasks

1. Done:
   - YouTube benchmark (`docs/PERFORMANCE.md`), InnerTube read retries, `videoInfo` watch-page fallback;
   - YouTube.js and NewPipeExtractor documentation assessment;
   - OSV dependency scan (bxanedot/eXcor#57);
   - typed request failures (`docs/ERROR_HANDLING.md`) and `CHANGELOG.md`.
2. If the intermittent nulls seen once in a full live run recur, read the WARN logs (endpoint and status) and fix the cause.
3. On a normal network: run `LivePlaybackTest`. If playback works there, SABR/UMP work can start against real responses.
