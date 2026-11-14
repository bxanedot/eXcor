# eXcor

eXcor is a clean-room Kotlin Multiplatform library and separate Swift package for YouTube playback stream extraction and YouTube/YouTube Music metadata. The host application supplies session access, optional token generation, and the media player; eXcor returns bounded transport details and does not embed a player or download media.

[![Evidence-weighted readiness](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fbxanedot%2FeXcor%2Fautomation%2Fexcor-competitiveness%2Fscore%2Fbadge.json)](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md)

**Release status: NO-GO.** The score measures verified engineering capability, not the percentage of videos that play. SABR/UMP playback is not implemented, and player-cipher, multi-video, and cross-network evidence remains incomplete.

## Comparison evidence

The latest matched direct-audio comparisons used eXcor main ad7a6f1 on 2026-11-14. Signed-out JVM adapters ran three cold and three warm attempts per video on one Windows home network. For dQw4w9WgXcQ, both eXcor 0.2.0 and InnerTubeX 0.11.6 fully decoded the same 3,507,483-byte Opus stream and passed forward and backward HTTP byte-range probes in all six attempts.

| Phase | Measure (p50/p95) | eXcor, dQw4w9WgXcQ | InnerTubeX, dQw4w9WgXcQ |
|---|---|---|---|
| Cold | First media (ms) | 395/555 | 1,388/1,488 |
| Warm | First media (ms) | 96/98 | 142/145 |
| Cold | Full decode (ms) | 464/475 | 455/540 |
| Warm | Full decode (ms) | 322/331 | 364/367 |

On a second public video, aqz-KE-bpKQ, eXcor was access-blocked before media in all six attempts. InnerTubeX passed full decode and both byte-range probes in all six; its first-media p50/p95 was 1,033/1,080 ms cold and 148/173 ms warm. This is a two-video comparison on one network using separate extraction adapters, not a broad performance or reliability claim. HTTP byte-range probes are not decoder or player seeks; the adapters recorded player seeks as NOT_TESTED.

A separate [current-main live check](modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-ad7-playback.md) fully decoded 3,433,755 bytes and received HTTP 206 for a midpoint range. The results keep the release at **NO-GO**: ciphered playback, multi-network coverage, video mux and real player-seek evidence remain incomplete.

See the [dQ comparison report](modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-ad7-dq-full.md), [aqz comparison report](modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-ad7-aqz-full.md), [competitive matrix](docs/COMPETITOR_MATRIX.md), and [live readiness score](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md).

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
