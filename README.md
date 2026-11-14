# eXcor

eXcor is a clean-room Kotlin Multiplatform library with a separate Swift package for YouTube and YouTube Music metadata and stream extraction. The host app owns session access and playback; eXcor returns bounded transport details and does not embed a player or download media.

[![Evidence-weighted readiness](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fbxanedot%2FeXcor%2Fautomation%2Fexcor-competitiveness%2Fscore%2Fbadge.json)](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md)

The badge refreshes after main CI and hourly. Its score measures evidence-backed engineering readiness, **not** InnerTubeX feature parity or the percentage of videos that play. The [score report](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md) shows category scores, missing evidence, and CI status; its [machine-readable record](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/score.json) identifies the exact main commit.

## Live comparison with InnerTubeX

Latest exact-main paired direct-audio run: **2026-11-15 local** (each report records its UTC timestamp), eXcor main [`c92b2c0`](https://github.com/bxanedot/eXcor/commit/c92b2c0cc14849a42937253cbd7014cf88405670), eXcor 0.2.0 vs InnerTubeX 0.11.6. Both signed-out JVM adapters ran three cold and three warm attempts per video on one Windows home network, with full audio decode and HTTP byte-range probes.

| Public video | eXcor | InnerTubeX |
|---|---|---|
| `dQw4w9WgXcQ` | 6/6 full-decode and forward/backward range passes; first-media p50 356 / 93 ms cold / warm | 6/6 full-decode and range passes; first-media p50 1,285 / 141 ms cold / warm |
| `M7lc1UVf-VE` | `ACCESS_DENIED` before media in 6/6 | First media and both range probes passed in 6/6; full decode timed out after 73,728 bytes in 6/6 |

On `dQw4w9WgXcQ`, full-decode p50 cold / warm was 320 / 321 ms for eXcor and 373 / 384 ms for InnerTubeX. The M7 result is an access block on eXcor and a decode timeout on InnerTubeX, so it is not a shared playback pass or a direct speed comparison. These two IDs on one network do not establish broad reliability or performance parity.

A separate one-attempt-per-adapter FFplay check on `dQw4w9WgXcQ` at the previous exact main commit observed the same initial, forward, and backward HTTP 206 ranges for both clients. It covers audio seeking for one ID; video-seek parity and repeatability remain unverified. HTTP range probes alone are not player seeks.

See the current-head [dQ report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-c92-dq-full.md) and [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-c92-dq-full.jsonl), plus the current-head [M7 report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-c92-m7lc-full.md) and [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-c92-m7lc-full.jsonl). The prior [paired report](modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-c6-paired.md) includes the `aqz-KE-bpKQ` result, where eXcor was blocked and InnerTubeX passed; the [audio-seek report](modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-c6-player-seek.md) records that separate check. See also the broader [competitive matrix](docs/COMPETITOR_MATRIX.md). The eXcor-only [video playback report](modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-2a9f-playback.md) records a full audio/video decode and midpoint range read; it is not a matched InnerTubeX video comparison.

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
