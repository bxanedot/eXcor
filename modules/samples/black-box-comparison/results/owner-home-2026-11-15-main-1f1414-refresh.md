# Black-box comparison report

Timings include only PASS samples. Outcome counts include every attempted sample.
The report emits only validated normalized fields; adapters must exclude secrets from those fields.

| Comparison | Video ID | OS | Network | Content | Adapter | Version | Lane | Phase | Samples | PASS | BLOCKED | UNSUPPORTED | FAIL | NOT_RUN | HTTP range F/B | First media p50/p95 (ms) | Decode p50/p95 (ms) |
|---|---|---|---|---|---|---|---|---|---:|---:|---:|---:|---:|---:|---|---:|---:|
| owner-home-2026-11-15-main-1f1414-refresh | dQw4w9WgXcQ | windows | home | music_video | excor | 0.1.5-excor0.2.0 | direct_audio | cold | 1 | 1 | 0 | 0 | 0 | 0 | F PASS=1; B PASS=1 | 2085/2085 | 16951/16951 |
| owner-home-2026-11-15-main-1f1414-refresh | dQw4w9WgXcQ | windows | home | music_video | innertubex | 0.1.5-innertubex0.11.7 | direct_audio | cold | 1 | 1 | 0 | 0 | 0 | 0 | F PASS=1; B PASS=1 | 3748/3748 | 1626/1626 |

HTTP byte-range probes are reported separately from playback seeks. `rangeProbeResults` in JSON contains the method and per-direction result counts; these probes do not establish decoder or player seek behavior.

First-media and decode table timings show nearest-rank p50/p95 in milliseconds, rounded to whole milliseconds. JSON retains metric-specific eligible sample counts. All latency summaries are grouped by comparison ID, commit, date, video ID, OS, network category, content category, signed-out session, timeout, locale, decoder, adapter version, lane, and cold/warm phase. `eligibleSamples` is shown per metric in JSON.
