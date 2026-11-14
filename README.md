# eXcor

eXcor is a clean-room Kotlin Multiplatform library with a separate Swift package for YouTube and YouTube Music metadata and stream extraction. The host app owns session access and playback; eXcor returns bounded transport details and does not embed a player or download media.

[![Evidence-weighted readiness](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fbxanedot%2FeXcor%2Fautomation%2Fexcor-competitiveness%2Fscore%2Fbadge.json)](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md)

The badge refreshes after main CI and hourly. Its score measures evidence-backed engineering readiness, **not** InnerTubeX feature parity or the percentage of videos that play. The [score report](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md) shows category scores, missing evidence, and CI status; its [machine-readable record](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/score.json) identifies the exact main commit.

## Live comparison with InnerTubeX

Latest paired direct-audio run: **2026-11-14**, eXcor main [`c6faa41`](https://github.com/bxanedot/eXcor/commit/c6faa41e8eb817210bbdf796bacca710be08653a), eXcor 0.2.0 vs InnerTubeX 0.11.6. Both signed-out JVM adapters ran three cold and three warm attempts per video on one Windows home network.

| Public video | eXcor, full-decode/range result; first-media p50 cold / warm | InnerTubeX, same checks; first-media p50 cold / warm |
|---|---|---|
| `dQw4w9WgXcQ` | 6/6 pass; 353 / 101 ms | 6/6 pass; 1,377 / 154 ms |
| `aqz-KE-bpKQ` | Access denied before media in 6/6 | 6/6 pass; 1,429 / 153 ms |

For `dQw4w9WgXcQ`, full-decode p50 cold / warm was 307 / 310 ms for eXcor and 366 / 360 ms for InnerTubeX. These are two videos on one network using separate extraction adapters. The second result is an access differential, not evidence about its cause. This sample does not support a broad reliability or performance ranking.

A separate one-attempt-per-adapter FFplay check on `dQw4w9WgXcQ` observed the same initial, forward, and backward HTTP 206 ranges for both clients. It covers audio seeking for one ID; it does not establish video-seek parity. HTTP range probes alone are not player seeks.

See the [paired report](modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-c6-paired.md), [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-c6-paired.jsonl), [seek report](modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-c6-player-seek.md), and the broader [competitive matrix](docs/COMPETITOR_MATRIX.md). The eXcor-only [video playback report](modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-2a9f-playback.md) records a full audio/video decode and midpoint range read; it is not a matched InnerTubeX video comparison.

**Release status: NO-GO.** SABR/UMP playback is not implemented. Ciphered-stream playback, repeatable results across eligible videos and networks, and paired video-seek evidence remain incomplete. See the [release gates](docs/release-gates.json) and [playback evidence matrix](docs/PLAYBACK_EVIDENCE_MATRIX.md).

## Build and contribute

```sh
./gradlew :jvmTest
swift test --package-path modules/platforms/swift
```

YouTube live checks are opt-in. The [comparison guide](modules/samples/black-box-comparison/README.md) describes their setup and sanitized outputs. CI runs cross-platform builds and tests in [GitHub Actions](https://github.com/bxanedot/eXcor/actions).

- `modules/cleanroom/` — active Kotlin Multiplatform implementation and tests.
- `modules/platforms/swift/` — Swift package and tests.
- `modules/platforms/preview/` — unpublished Android and desktop host examples.
- `modules/samples/black-box-comparison/` — public-API adapters and evidence tooling.
- `modules/legacy/` — quarantined historical source and fixtures; do not use for new implementation.

Before implementation work, read the [architecture](docs/ARCHITECTURE.md), [clean-room policy](docs/CLEAN_ROOM.md), [feature matrix](docs/FEATURE_MATRIX.md), and [source provenance](PROVENANCE.md). Never copy competitor implementation code. The repository is licensed under the [GNU General Public License v3.0 only](LICENSE).
