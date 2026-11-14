# eXcor

eXcor is a clean-room Kotlin Multiplatform library and separate Swift package for YouTube playback stream extraction and YouTube/YouTube Music metadata. The host application supplies session access, optional token generation, and the media player; eXcor returns bounded transport details and does not embed a player or download media.

[![Evidence-weighted readiness](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fbxanedot%2FeXcor%2Fautomation%2Fexcor-competitiveness%2Fscore%2Fbadge.json)](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md)

**Release status: NO-GO.** The score measures verified engineering capability, not the percentage of videos that play. SABR/UMP playback is not implemented, and player-cipher, multi-video, and cross-network evidence remains incomplete.

## Comparison evidence

The latest exact-main matched direct-audio run used eXcor main `c6faa41` on 2026-11-14. Signed-out JVM adapters ran three cold and three warm attempts per video on one Windows home network. On `dQw4w9WgXcQ`, eXcor 0.2.0 and InnerTubeX 0.11.6 both fully decoded the same 3,507,483-byte Opus stream and passed forward/backward HTTP byte-range probes in all six attempts.

| Phase | Measure (p50/p95) | eXcor | InnerTubeX |
|---|---|---:|---:|
| Cold | First media (ms) | 353/353 | 1,377/1,399 |
| Warm | First media (ms) | 101/102 | 154/185 |
| Cold | Full decode (ms) | 307/308 | 366/367 |
| Warm | Full decode (ms) | 310/314 | 360/363 |

On the second video, `aqz-KE-bpKQ`, eXcor was classified `ACCESS_DENIED` before media in all six attempts; InnerTubeX passed full decode and both byte-range probes in all six. This is a two-video comparison on one network using separate extraction adapters, not a broad performance or reliability claim. HTTP byte-range probes do not establish player seeks.

A separate same-FFplay audio-seek check on this main commit sent Right then Left to each adapter once. Both produced an initial range plus the same forward/backward offset ranges, all HTTP 206, before the configured 30-second `time_limit`. This one-ID audio result does not test video seeking or broader reliability. See the [paired seek report](modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-c6-player-seek.md), [matched comparison report](modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-c6-paired.md), [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-c6-paired.jsonl), and [competitive matrix](docs/COMPETITOR_MATRIX.md).

The latest exact-main signed-out eXcor-only video run is at `2a9f8dd` (2026-11-14): it fully decoded 3,433,755 bytes of Opus audio and a 480p H.264/AAC mux (854×480, 25 fps, 213.089 s). Midpoint HTTP byte-range reads returned 206. Its three-ID matrix classified one ID as range-readable and two as YouTube `LOGIN_REQUIRED` bot-check access blocks. That eXcor-only run did not compare InnerTubeX video or test ciphered playback or player seeks. See the [exact-main run](modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-2a9f-playback.md), the earlier [36d5 playback run](modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-36d5-playback.md), and [strict range validation](modules/samples/black-box-comparison/results/owner-home-2026-11-14-e990-range-validation.md). Release remains **NO-GO**: ciphered playback, multi-network coverage, repeatable success across eligible IDs, video seeks, and repeated seek evidence remain incomplete.

At exact source commit `e990cb3`, a stricter signed-out rerun fully decoded the same audio/video and verified MIME types, lengths and exact HTTP range boundaries; all 27 listed formats returned valid 206 ranges. This remains eXcor-only evidence for one video/network, not a matched InnerTubeX video test or player-controlled seek. See the [exact-source verification report](modules/samples/black-box-comparison/results/owner-home-2026-11-14-e990-range-validation.md).

See the latest [paired comparison report](modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-c6-paired.md) and [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-c6-paired.jsonl), the previous [dQ](modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-ad7-dq-full.md) and [aqz](modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-ad7-aqz-full.md) reports, the [competitive matrix](docs/COMPETITOR_MATRIX.md), and the [live readiness score](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md).

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
