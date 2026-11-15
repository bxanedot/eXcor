# eXcor

eXcor is a clean-room Kotlin Multiplatform library, with a separate Swift package, for YouTube and YouTube Music metadata and stream extraction. The host app owns session access and playback. eXcor returns bounded transport details; it does not embed a player or download media.

[![Live evidence-weighted readiness](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fbxanedot%2FeXcor%2Fautomation%2Fexcor-competitiveness%2Fscore%2Fbadge.json)](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md)

The automated score and [machine-readable report](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/score.json) track the latest main commit, CI state, evidence and release decision. The score is **engineering readiness**, not InnerTubeX feature parity or the percentage of videos that play. The score branch is kept for this live badge/report; its workflow refreshes the evidence snapshot.

## eXcor vs InnerTubeX

**Latest shared paired direct-audio pass:** signed-out eXcor 0.2.0 and InnerTubeX 0.11.7 adapters, on `dQw4w9WgXcQ` and one Windows home network. Both decoded the same 3,507,483-byte Opus stream and passed forward/backward HTTP range reads in all 3 cold and 3 warm attempts. Source: eXcor main `d29f6ae` (2026-11-15).

| Measurement (p50 / p95) | eXcor | InnerTubeX |
|---|---:|---:|
| First media, cold (ms) | 389 / 427 | 1,179 / 1,298 |
| First media, warm (ms) | 99 / 124 | 153 / 169 |
| Full decode, cold (ms) | 462 / 547 | 524 / 546 |
| Full decode, warm (ms) | 512 / 552 | 598 / 1,367 |

In this sample eXcor delivered first media sooner; decode medians were similar. It is one successful video on one network, not a broad performance or reliability ranking. The adapters disabled HLS and SABR, so this does not test InnerTubeX's Remix SABR path. HTTP range reads are not player seeks. See the [paired run report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-d29-v0117.md), [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-d29-v0117.jsonl), and [full eXcor audio/video decode report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-d29-playback.md).

**Latest paired availability check:** on `yydNF8tuVmU` at exact main `acff0a3`, signed-out eXcor 0.2.0 was `BLOCKED/ACCESS_DENIED` before media in all 3 cold and 3 warm attempts. InnerTubeX 0.11.7 decoded a 4,147,964-byte Opus stream and passed both forward/backward HTTP range probes in all six. InnerTubeX first-media p50/p95 was 1,360/2,108 ms cold and 148/161 ms warm; decode p50/p95 was 585/680 ms cold and 611/634 ms warm. eXcor has no media timing here, so these are not side-by-side speed results. This is one signed-out Windows home network and gives no shared decode or broad reliability comparison. See the [report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-acff-yyd-full.md), [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-15-acff-yyd-full.jsonl), and [JSON summary](modules/samples/black-box-comparison/results/owner-home-2026-11-15-acff-yyd-full-report.json).

**Latest matched audio player-seek evidence:** on one signed-out Windows home network, eXcor 0.2.0 and InnerTubeX 0.11.7 each completed three FFplay 9.0.2 Right/Left attempts on `dQw4w9WgXcQ`. Both player windows visibly refreshed after each key; all nine HTTP 206 reads per adapter followed offsets `0 → 497307 → 329300`. Across three runs eXcor relayed 2,244,608 bytes and InnerTubeX 2,211,840 bytes; each run ended at its configured 30-second cap below 256 MiB. The repeats span adjacent documentation-only commits; the adapter source was unchanged. This establishes same-ID, same-network audio-seek repeatability, not broad playback parity. Request-arrival timing is not audible seek latency. See the [first sample](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-b1-player-seek.md) and [two repeat samples](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-17c6-player-seek-repeats.md).

| Capability | Current evidence |
|---|---|
| Direct audio | Both adapters passed full decode and both range reads 6/6 on `dQw4w9WgXcQ`. On `yydNF8tuVmU`, eXcor was `ACCESS_DENIED` before media in 6/6 while InnerTubeX decoded the full stream and passed both ranges 6/6. Earlier `ScMzIvxBSi4` and `aqz-KE-bpKQ` runs showed the same access differential. Causes remain unknown; these are one-network observations. ([latest report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-acff-yyd-full.md), [ScMz report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-scmz-repeat.md), [aqz report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-2818-aqz-full.md)) |
| Audio player seek | Both adapters completed three same-harness FFplay Right/Left samples on `dQw4w9WgXcQ`; all nine per-adapter requests returned HTTP 206 at the initial, forward and backward offsets. Repeatable on one ID/network, not broad parity; video seeking remains open. ([first sample](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-b1-player-seek.md), [repeat samples](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-17c6-player-seek-repeats.md)) |
| Direct video | eXcor independently muxed and decoded 480p H.264/AAC on that video. A matched InnerTubeX video decode and paired direct-video seek result are still missing. ([eXcor report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-d29-playback.md), [paired seek attempts](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-360-video-player-seek.md)) |
| Player cipher | Signature and `n` transforms have synthetic test coverage; recent signed-out samples found no eligible public cipher challenge, so live ciphered playback is unverified. ([feature matrix](docs/FEATURE_MATRIX.md)) |
| SABR/UMP | No independently validated viewer contract or authorized fixture set; playback and seeking remain unimplemented/unverified. ([contract review](docs/SABR_VIEWER_CONTRACT_RECHECK_2026-11-14.md)) |

**Release decision: NO-GO.** The live [score report](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md), [release gates](docs/release-gates.json), and [competitor matrix](docs/COMPETITOR_MATRIX.md) show category coverage and remaining gaps. These are evidence limits, not inferred competitor failures.

## Build

```sh
./gradlew :jvmTest
swift test --package-path modules/platforms/swift
```

YouTube live checks are opt-in. Follow the [playback evidence matrix](docs/PLAYBACK_EVIDENCE_MATRIX.md) and [comparison guide](modules/samples/black-box-comparison/README.md); never store cookies, tokens, signed URLs, or raw responses.

## Project layout

- `modules/cleanroom/` — active Kotlin Multiplatform implementation and tests.
- `modules/platforms/swift/` — native Swift package and tests.
- `modules/platforms/preview/` — unpublished Android and desktop host examples.
- `modules/samples/black-box-comparison/` — isolated public-API adapters and evidence tooling.
- `modules/tooling/` — offline repository checks and readiness scoring.
- `modules/legacy/` — quarantined historical source and fixtures; do not use for new implementation.

Read [architecture](docs/ARCHITECTURE.md), [clean-room policy](docs/CLEAN_ROOM.md), [feature matrix](docs/FEATURE_MATRIX.md), and [provenance](PROVENANCE.md) before implementation work. Never copy competitor implementation code. eXcor is licensed under [GPL-3.0-only](LICENSE).
