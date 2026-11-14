# eXcor

eXcor is a clean-room Kotlin Multiplatform library and separate Swift package for YouTube playback stream extraction and YouTube/YouTube Music metadata. The host application supplies session access, optional token generation, and the media player; eXcor returns bounded transport details and does not embed a player or download media.

[![Evidence-weighted readiness](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fbxanedot%2FeXcor%2Fautomation%2Fexcor-competitiveness%2Fscore%2Fbadge.json)](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md)

**Release status: NO-GO.** The score measures verified engineering capability, not the percentage of videos that play. SABR/UMP playback is not implemented, and player-cipher, multi-video, and cross-network evidence remains incomplete.

## Comparison evidence

A paired full-decode and HTTP byte-range run from eXcor main `4e5e67d` was recorded on 2026-11-13. It used signed-out JVM adapters on one Windows home network, with three cold and three warm attempts per video.

| Public video | eXcor | InnerTubeX |
|---|---|---|
| `dQw4w9WgXcQ` | 6/6 full audio decodes and forward/backward byte-range checks passed | 6/6 passed |
| `kJQP7kiw5Fk` | Access blocked before media in 6/6 attempts | 6/6 full audio decodes and byte-range checks passed |

On the shared playable video, first-media p50 was 379 ms cold / 118 ms warm for eXcor and 1,524 ms cold / 185 ms warm for InnerTubeX. This is a diagnostic comparison from one network using separate extraction adapters, not a broad performance or reliability claim. HTTP byte-range checks are not decoder or player seeks.

A current-main signed-out spot check on `ddaf925` (2026-11-14) found one range-readable result and two YouTube access blocks across three public music videos. It requested only the first 1 KiB and did not compare InnerTubeX or establish full playback. See the [per-ID result](modules/samples/black-box-comparison/results/owner-home-2026-11-13-per-id-range-matrix.md), [competitive matrix](docs/COMPETITOR_MATRIX.md), [normalized report](modules/samples/black-box-comparison/results/owner-home-2026-11-13-main-4e5e67d-report.json), and [readiness score details](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md).

## Build and verify

```sh
./gradlew :jvmTest
swift test --package-path modules/platforms/swift
```

YouTube live checks are opt-in and use the current network and supplied public video IDs. Their setup and the isolated comparison adapters are documented in the [comparison guide](modules/samples/black-box-comparison/README.md). CI runs the cross-platform builds and tests; see [GitHub Actions](https://github.com/bxanedot/eXcor/actions).

## Where to work

- `modules/cleanroom/` — active Kotlin Multiplatform library source and tests.
- `modules/platforms/swift/` — separate Swift package and tests.
- `modules/platforms/preview/` — unpublished Android and desktop host previews.
- `modules/legacy/` — preserved, uncompiled historical source; do not use it for new implementation work.
- `modules/samples/black-box-comparison/` — public-API comparison adapters, sanitized results, and report tooling.

Before changing behavior, read the [architecture](docs/ARCHITECTURE.md), [feature evidence](docs/FEATURE_MATRIX.md), [release gates](docs/release-gates.json), [competitive engineering plan](docs/superpowers/plans/2026-11-10-excor-competitor-plan.md), and [source provenance](PROVENANCE.md). Keep new implementation inside the active clean-room boundary, keep live checks opt-in, and report access blocks separately from technical failures. The repository is licensed under [GPL-3.0-only](LICENSE).
