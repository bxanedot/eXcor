# eXcor

eXcor is a clean-room Kotlin Multiplatform library with a separate Swift package for YouTube and YouTube Music metadata and stream extraction. The host app owns session access and playback; eXcor returns bounded transport details and does not embed a player or download media.

[![Evidence-weighted readiness](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fbxanedot%2FeXcor%2Fautomation%2Fexcor-competitiveness%2Fscore%2Fbadge.json)](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md)

The badge refreshes after main CI and hourly. Its score measures evidence-backed engineering readiness, **not** InnerTubeX feature parity or the percentage of videos that play. The [score report](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md) shows category scores, missing evidence, and CI status; its [machine-readable record](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/score.json) identifies the exact main commit.

## Live comparison with InnerTubeX

Latest exact-main paired direct-audio run: **2026-11-15** at eXcor main [`2818f68`](https://github.com/bxanedot/eXcor/commit/2818f68490e596f26f43f3be9902c3bc762b198b), eXcor 0.2.0 vs InnerTubeX 0.11.6. Both signed-out JVM adapters ran three cold and three warm attempts on one Windows home network, with full audio decode and HTTP byte-range probes.

| Public video | eXcor | InnerTubeX |
|---|---|---|
| `aqz-KE-bpKQ` | `ACCESS_DENIED` before media in 6/6; no media bytes or timing | 6/6 full-decode and forward/backward range passes; first-media p50 1,204 / 140 ms cold / warm; decode p50 1,114 / 1,117 ms |

This run is an access differential, not a shared playback pass or direct speed comparison. A previous exact-main run at `c92b2c0` found a shared case on `dQw4w9WgXcQ`: both adapters fully decoded and passed both ranges in 6/6 cold/warm attempts. First-media p50 cold/warm was 356/93 ms for eXcor and 1,285/141 ms for InnerTubeX; decode p50 was 320/321 ms and 373/384 ms. On `M7lc1UVf-VE`, eXcor was blocked before media in 6/6 and InnerTubeX's full decode timed out after 73,728 bytes in 6/6. Across these separate exact-main runs, the evidence includes one shared successful ID and repeatable access differentials on one network; it does not establish broad reliability or performance parity.

A separate one-attempt-per-adapter FFplay check on `dQw4w9WgXcQ` at the previous exact main commit observed the same initial, forward, and backward HTTP 206 ranges for both clients. It covers audio seeking for one ID; video-seek parity and repeatability remain unverified. HTTP range probes alone are not player seeks.

See the latest [aqz report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-2818-aqz-full.md) and [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-2818-aqz-full.jsonl), plus the previous exact-main [dQ report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-c92-dq-full.md) and [M7 report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-c92-m7lc-full.md). The [paired audio-seek report](modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-c6-player-seek.md) records a separate one-ID check. See also the broader [competitive matrix](docs/COMPETITOR_MATRIX.md). The eXcor-only [video playback report](modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-2a9f-playback.md) records a full audio/video decode and midpoint range read; it is not a matched InnerTubeX video comparison.

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
