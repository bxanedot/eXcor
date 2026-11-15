# eXcor

eXcor is a clean-room Kotlin Multiplatform library, with a separate Swift package, for YouTube and YouTube Music metadata and stream extraction. The host app owns session access and playback. eXcor returns bounded transport details; it does not embed a player or download media.

[![Live evidence-weighted readiness](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fbxanedot%2FeXcor%2Fautomation%2Fexcor-competitiveness%2Fscore%2Fbadge.json)](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md)

The automated score and [machine-readable report](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/score.json) track the latest main commit, CI state, evidence and release decision. The score is **engineering readiness**, not InnerTubeX feature parity or the percentage of videos that play. The score branch is kept for this live badge/report; its workflow refreshes the evidence snapshot.

## eXcor vs InnerTubeX

**Paired direct audio, 2026-11-15 18:11 UTC.** eXcor 0.2.0 (main `314dd6e`) and InnerTubeX 0.11.7 (adapter `0.1.6`, source `e2ce366`) both decoded Opus and passed the two HTTP range probes in **6/6 attempts each**. This was one public video, `dQw4w9WgXcQ`, signed out on one Windows home network.

| Measurement (p50 / p95) | eXcor | InnerTubeX |
|---|---:|---:|
| First media, cold (ms) | 449 / 915 | 2,361 / 3,736 |
| First media, warm (ms) | 144 / 185 | 169 / 369 |
| Transfer and decode, cold (ms) | 356 / 443 | 331 / 1,041 |
| Transfer and decode, warm (ms) | 343 / 382 | 359 / 489 |

Each phase had three samples per adapter. First media measures from extraction start to the first body read. The adapters exercised direct audio with HLS and SABR disabled. [Run report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-streaming-reads.md) · [Sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-15-streaming-reads.jsonl)

Older InnerTubeX adapter versions through 0.1.5 buffered the initial range before timing the first body read. Use the new run for first-media comparisons. The older results still document completion and variation, including one eXcor sample that took 16,951 ms to transfer and decode. [Earlier paired run](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-1f1414-cold-repeat.md) · [Outlier report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-1f1414-refresh.md)

**Earlier exact-main paired Sintel diagnostic (2026-11-15, main `5bc3036d7e47ba2d0fc3382e6755ae9b5bf56220`):** one signed-out cold attempt for Blender Foundation's public [Sintel](https://www.youtube.com/watch?v=eRsGyueVLvQ) on one Windows home network. eXcor 0.2.0 was `BLOCKED/ACCESS_DENIED` before media; InnerTubeX 0.11.7 read 73,728 bytes and passed both HTTP byte-range probes, then hit the 30-second limit during full audio decode. Neither adapter fully decoded this sample. This is one attempt on one ID/network; it establishes neither the cause of eXcor's access block nor broad reliability or playback parity. See the [report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-sintel-paired.md), [JSON summary](modules/samples/black-box-comparison/results/owner-home-2026-11-15-sintel-paired-report.json), and [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-15-sintel-paired.jsonl).

**Repeated second-ID paired availability check (2026-11-15, main `af7697d`):** signed-out adapters compared the public video `jNQXAC9IVRw` on one Windows home network. Across one cold attempt and two additional cold plus two warm attempts per adapter, eXcor was `BLOCKED/ACCESS_DENIED` before any media request in 5/5; InnerTubeX 0.11.7 fully decoded audio and passed forward/backward HTTP byte-range probes in 5/5. This is an access differential on one ID/network: it does not establish why YouTube denied eXcor, a general implementation failure, player-seek behavior, or broad parity. See the [first report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-zoo-second-id.md), [repeat report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-zoo-second-id-repeats.md), [first samples](modules/samples/black-box-comparison/results/owner-home-2026-11-15-zoo-second-id.jsonl), and [repeat samples](modules/samples/black-box-comparison/results/owner-home-2026-11-15-zoo-second-id-repeats.jsonl).

**Earlier paired availability check:** on `yydNF8tuVmU` at exact main `acff0a3`, signed-out eXcor 0.2.0 was `BLOCKED/ACCESS_DENIED` before media in all 3 cold and 3 warm attempts. InnerTubeX 0.11.7 decoded a 4,147,964-byte Opus stream and passed both forward/backward HTTP range probes in all six. InnerTubeX first-media p50/p95 was 1,360/2,108 ms cold and 148/161 ms warm; decode p50/p95 was 585/680 ms cold and 611/634 ms warm. eXcor has no media timing here, so these are not side-by-side speed results. This is one signed-out Windows home network and gives no shared decode or broad reliability comparison. See the [report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-acff-yyd-full.md), [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-15-acff-yyd-full.jsonl), and [JSON summary](modules/samples/black-box-comparison/results/owner-home-2026-11-15-acff-yyd-full-report.json).

**Latest matched audio player-seek evidence:** on one signed-out Windows home network, eXcor 0.2.0 and InnerTubeX 0.11.7 each completed three FFplay 9.0.2 Right/Left attempts on `dQw4w9WgXcQ`. Both player windows visibly refreshed after each key; all nine HTTP 206 reads per adapter followed offsets `0 → 497307 → 329300`. Across three runs eXcor relayed 2,244,608 bytes and InnerTubeX 2,211,840 bytes; each run ended at its configured 30-second cap below 256 MiB. The repeats span adjacent documentation-only commits; the adapter source was unchanged. This establishes same-ID, same-network audio-seek repeatability, not broad playback parity. Request-arrival timing is not audible seek latency. See the [first sample](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-b1-player-seek.md) and [two repeat samples](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-17c6-player-seek-repeats.md).

| Capability | Current evidence |
|---|---|
| Direct audio | Both adapters passed full decode and both range reads 6/6 on `dQw4w9WgXcQ`. On `yydNF8tuVmU`, eXcor was `ACCESS_DENIED` before media in 6/6 while InnerTubeX decoded the full stream and passed both ranges 6/6. Earlier ScMzIvxBSi4 and aqz-KE-bpKQ runs showed the same access differential; the current-main jNQXAC9IVRw pair reproduced it in five attempts per adapter. On the current-main Sintel sample, eXcor was blocked before media while InnerTubeX passed both HTTP ranges but timed out during full decode; neither decoded it fully. Causes remain unknown; these are one-network observations. ([Sintel report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-sintel-paired.md), [earlier yyd report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-acff-yyd-full.md), [current-main jNQXAC9IVRw report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-zoo-second-id-repeats.md), [ScMz report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-scmz-repeat.md), [aqz report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-2818-aqz-full.md)) |
| Audio player seek | Both adapters completed three same-harness FFplay Right/Left samples on `dQw4w9WgXcQ`; all nine per-adapter requests returned HTTP 206 at the initial, forward and backward offsets. Repeatable on one ID/network, not broad parity; video seeking remains open. ([first sample](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-b1-player-seek.md), [repeat samples](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-17c6-player-seek-repeats.md)) |
| Direct video | eXcor muxed and decoded 480p H.264/AAC on that video. In a separate test, eXcor decoded frames after forward and backward FFprobe seeks in 3/3 attempts. InnerTubeX reported unsupported transport in 3/3 attempts with HLS and SABR disabled. The decoder seek test covered one video track on one network. ([mux report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-main-d29-playback.md), [decoder seek report](modules/samples/black-box-comparison/results/owner-home-2026-11-15-direct-video-decoder-seek.md)) |
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
