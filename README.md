# eXcor

eXcor is a clean-room Kotlin Multiplatform library with a separate Swift package for YouTube and YouTube Music metadata and stream extraction. The host app owns session access and playback; eXcor returns bounded transport details and does not embed a player or download media.

[![Evidence-weighted readiness](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fbxanedot%2FeXcor%2Fautomation%2Fexcor-competitiveness%2Fscore%2Fbadge.json)](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md)

The badge refreshes after main CI and hourly. Its score measures evidence-backed engineering readiness, **not** InnerTubeX feature parity or the percentage of videos that play. The [score report](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md) shows category scores, missing evidence, and CI status; its [machine-readable record](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/score.json) identifies the exact main commit.

## Live comparison with InnerTubeX

**Readiness score: 53/100 — NO-GO.** This is evidence-backed engineering readiness, not a percentage of InnerTubeX features or videos that play. The live score report and badge above track the current main commit and CI state.

**Latest exact-main paired direct-audio run:** 2026-11-15 at eXcor main [`a983599`](https://github.com/bxanedot/eXcor/commit/a983599f1256209a78a1d30140fdda23a521b328), eXcor 0.2.0 vs InnerTubeX 0.11.6. Signed-out JVM adapters ran three cold and three warm attempts on one Windows home network for `dQw4w9WgXcQ`. Both decoded the same 3,507,483-byte Opus stream and passed forward/backward HTTP byte-range probes in all 6 attempts.

| Measure (p50/p95) | Phase | eXcor | InnerTubeX |
|---|---|---:|---:|
| First media (ms) | Cold | 372/429 | 1,199/1,328 |
| First media (ms) | Warm | 104/105 | 144/144 |
| Full decode (ms) | Cold | 369/402 | 399/433 |
| Full decode (ms) | Warm | 350/371 | 386/420 |

This is a shared direct-audio success on one video and one network, not broad reliability or performance parity. The same exact main also has one paired FFplay audio-seek check: after Right, a 1.5-second pause, and Left, both adapters produced distinct forward and backward HTTP 206 ranges. HTTP range probes from the decoder run are not player seeks.

**Latest paired direct-video diagnostic:** on 2026-11-15, the signed-out adapters ran three cold and three warm attempts for `dQw4w9WgXcQ` on one Windows home network, using eXcor main `ae9e9ae`. eXcor passed the initial and forward/backward HTTP byte-range checks in 6/6, but the 480p full-video decode timed out in 6/6 after receiving about 3.94–3.97 MB. InnerTubeX returned `UNSUPPORTED/UNSUPPORTED_TRANSPORT` before a media request in 6/6. This direct-video result shows no shared decode pass for this case; it does not establish general InnerTubeX video incompatibility or broad playback parity. See the [video report](modules/samples/black-box-comparison/results/owner-home-video-decode-2026-11-15.md) and [sanitized samples](modules/samples/black-box-comparison/results/owner-home-video-decode-2026-11-15.jsonl).

The previous exact-main `aqz-KE-bpKQ` run at `2818f68` showed an access differential: eXcor was `ACCESS_DENIED` before media in 6/6 attempts, while InnerTubeX passed full decode and both ranges in 6/6. On `M7lc1UVf-VE`, the `c92b2c0` run showed the same eXcor access block; InnerTubeX passed ranges but its full decode timed out. These results do not identify a cause. The current paired seek attempt had distinct offsets per adapter (eXcor: `0, 666109, 497307`; InnerTubeX: `0, 497307, 329300`), all HTTP 206. It covers one audio ID and network; video seeking, ciphered playback and cross-network repeatability remain unverified.

See the current [decode report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-a983-dq-full.md), [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-a983-dq-full.jsonl), and [machine-readable summary](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-a983-dq-full-report.json), plus the matched [FFplay seek report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-a983-player-seek-spaced.md). Older comparisons are in the [competitive matrix](docs/COMPETITOR_MATRIX.md).

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
