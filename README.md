# eXcor

eXcor is a clean-room Kotlin Multiplatform library with a separate Swift package for YouTube and YouTube Music metadata and stream extraction. The host app owns session access and playback; eXcor returns bounded transport details and does not embed a player or download media.

[![Evidence-weighted readiness](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fbxanedot%2FeXcor%2Fautomation%2Fexcor-competitiveness%2Fscore%2Fbadge.json)](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md)

The badge refreshes after main CI and hourly. Its score measures evidence-backed engineering readiness, **not** InnerTubeX feature parity or the percentage of videos that play. The [score report](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md) shows category scores, missing evidence, and CI status; its [machine-readable record](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/score.json) identifies the exact main commit.

## Live comparison with InnerTubeX

**Readiness score: 53/100 — NO-GO.** This is evidence-backed engineering readiness, not a percentage of InnerTubeX features or videos that play. The live score report and badge above track the current main commit and CI state.

**Latest exact-main paired direct-audio run:** 2026-11-15 at eXcor main [`d29f6ae`](https://github.com/bxanedot/eXcor/commit/d29f6ae3e433c103748b12634c56f193788ac8cc), eXcor 0.2.0 vs InnerTubeX 0.11.7. Signed-out JVM adapters ran three cold and three warm attempts on one Windows home network for `dQw4w9WgXcQ`. Both decoded the same 3,507,483-byte Opus stream and passed forward/backward HTTP byte-range probes in all six attempts.

| Measure (p50/p95) | Phase | eXcor | InnerTubeX |
|---|---|---:|---:|
| First media (ms) | Cold | 389/427 | 1,179/1,298 |
| First media (ms) | Warm | 99/124 | 153/169 |
| Full decode (ms) | Cold | 462/547 | 524/546 |
| Full decode (ms) | Warm | 512/552 | 598/1,367 |

eXcor had lower first-media p50/p95 in this sample; decode medians were similar, while InnerTubeX warm p95 was 1,367 ms. This is one shared direct-audio success on one video and one network, not broad reliability or performance parity. The direct-audio adapter disables HLS and SABR, so this run does not test the [v0.11.7 Remix SABR preference](https://github.com/MetrolistGroup/innertubex/releases/tag/v0.11.7). These HTTP range probes are not player seeks. A separate paired FFplay audio-seek check at main `a983599` produced initial, forward and backward HTTP 206 reads for both adapters on the same video/network; it was one attempt per adapter and does not verify video seeking.

**Exact-main eXcor audio/video playback check (source `d29f6ae`):** two signed-out JVM tests passed on the same video and Windows home network. eXcor decoded 3,433,755 bytes of Opus audio and muxed/decoded 480p H.264/AAC video (854×480, 25 fps, 14,103,519-byte video track). Mid-file HTTP ranges passed for both tracks. These checks do not exercise player-controlled seeking. See the [sanitized report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-d29-playback.md).

**Latest owner-network range matrix (source `17f1604`):** one signed-out JVM run classified five public IDs: `dQw4w9WgXcQ` returned a readable audio range; `kJQP7kiw5Fk`, `JGwWNGJdvx8`, `aqz-KE-bpKQ`, and `M7lc1UVf-VE` were access-blocked. This is one video pass on one network, not broad reliability, cipher or seek evidence. See the [sanitized matrix report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-17f-range-matrix.md).

**Latest paired direct-video diagnostic:** on 2026-11-15, the signed-out adapters ran three cold and three warm attempts for `dQw4w9WgXcQ` on one Windows home network, using eXcor main `ae9e9ae` and InnerTubeX v0.11.6. eXcor passed the initial and forward/backward HTTP byte-range checks in 6/6, but the 480p full-video decode timed out in 6/6 after receiving about 3.94–3.97 MB. InnerTubeX returned `UNSUPPORTED/UNSUPPORTED_TRANSPORT` before a media request in 6/6. This direct-video result shows no shared decode pass for this case; it does not establish general InnerTubeX video incompatibility or broad playback parity. See the [video report](modules/samples/black-box-comparison/results/owner-home-video-decode-2026-11-15.md) and [sanitized samples](modules/samples/black-box-comparison/results/owner-home-video-decode-2026-11-15.jsonl).

The previous exact-main `aqz-KE-bpKQ` run at `2818f68` showed an access differential: eXcor was `ACCESS_DENIED` before media in 6/6 attempts, while InnerTubeX passed full decode and both ranges in 6/6. On `M7lc1UVf-VE`, the `c92b2c0` run showed the same eXcor access block; InnerTubeX passed ranges but its full decode timed out. These results do not identify a cause. The paired seek check at exact main `a983599` had distinct offsets per adapter (eXcor: `0, 666109, 497307`; InnerTubeX: `0, 497307, 329300`), all HTTP 206. It covers one audio ID and network; video seeking, ciphered playback and cross-network repeatability remain unverified.

See the current [paired decode report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-d29-v0117.md), [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-d29-v0117.jsonl), and [machine-readable summary](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-d29-v0117-report.json), plus the eXcor-only [audio/video report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-d29-playback.md). Older comparisons are in the [competitive matrix](docs/COMPETITOR_MATRIX.md).

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
- `modules/tooling/` — offline repository checks and readiness scoring.
- `modules/legacy/` — quarantined historical source and fixtures; do not use for new implementation.

Before implementation work, read the [architecture](docs/ARCHITECTURE.md), [clean-room policy](docs/CLEAN_ROOM.md), [feature matrix](docs/FEATURE_MATRIX.md), and [source provenance](PROVENANCE.md). Never copy competitor implementation code. The repository is licensed under the [GNU General Public License v3.0 only](LICENSE).
