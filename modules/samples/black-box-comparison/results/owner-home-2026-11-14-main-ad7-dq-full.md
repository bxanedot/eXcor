# Black-box comparison report

**Run context:** 2026-11-14 04:44 UTC; exact eXcor main commit `ad7a6f1514c1f320b493c1e62d9bbb6c04c82a5a`; Windows x64; signed-out; home network; music video `dQw4w9WgXcQ`; FFmpeg 9.0.2. The eXcor JVM artifact was published locally from this checkout and its adapter rebuilt. InnerTubeX adapter 0.1.3 uses the pinned v0.11.6 release.

**Command:** `python modules/samples/black-box-comparison/run_matched.py --video-id dQw4w9WgXcQ --comparison-id owner-home-2026-11-14-ad7-dq --network-category home --content-category music_video --phases cold warm --sample-indices 1 2 3 --timeout-seconds 30 --adapters excor innertubex --range-probes --full-decode --output modules/samples/black-box-comparison/results/owner-home-2026-11-14-main-ad7-dq-full.jsonl`

Timings include only PASS samples. Outcome counts include every attempted sample.
The report emits only validated normalized fields; adapters must exclude secrets from those fields.

| Comparison | Video ID | OS | Network | Content | Adapter | Version | Lane | Phase | Samples | PASS | BLOCKED | UNSUPPORTED | FAIL | NOT_RUN | HTTP range F/B | First media p50/p95 (ms) | Decode p50/p95 (ms) |
|---|---|---|---|---|---|---|---|---|---:|---:|---:|---:|---:|---:|---|---:|---:|
| owner-home-2026-11-14-ad7-dq | dQw4w9WgXcQ | windows | home | music_video | excor | 0.1.4-excor0.2.0 | direct_audio | cold | 3 | 3 | 0 | 0 | 0 | 0 | F PASS=3; B PASS=3 | 395/555 | 464/475 |
| owner-home-2026-11-14-ad7-dq | dQw4w9WgXcQ | windows | home | music_video | excor | 0.1.4-excor0.2.0 | direct_audio | warm | 3 | 3 | 0 | 0 | 0 | 0 | F PASS=3; B PASS=3 | 96/98 | 322/331 |
| owner-home-2026-11-14-ad7-dq | dQw4w9WgXcQ | windows | home | music_video | innertubex | 0.1.3-innertubex0.11.6 | direct_audio | cold | 3 | 3 | 0 | 0 | 0 | 0 | F PASS=3; B PASS=3 | 1388/1488 | 455/540 |
| owner-home-2026-11-14-ad7-dq | dQw4w9WgXcQ | windows | home | music_video | innertubex | 0.1.3-innertubex0.11.6 | direct_audio | warm | 3 | 3 | 0 | 0 | 0 | 0 | F PASS=3; B PASS=3 | 142/145 | 364/367 |

HTTP byte-range probes are reported separately from playback seeks. `rangeProbeResults` in JSON contains the method and per-direction result counts; these probes do not establish decoder or player seek behavior.

First-media and decode table timings show nearest-rank p50/p95 in milliseconds, rounded to whole milliseconds. JSON retains metric-specific eligible sample counts. All latency summaries are grouped by comparison ID, commit, date, video ID, OS, network category, content category, signed-out session, timeout, locale, decoder, adapter version, lane, and cold/warm phase. `eligibleSamples` is shown per metric in JSON.
