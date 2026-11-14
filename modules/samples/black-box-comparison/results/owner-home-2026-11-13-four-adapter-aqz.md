# Black-box comparison report

Timings include only PASS samples. Outcome counts include every attempted sample.
The report emits only validated normalized fields; adapters must exclude secrets from those fields.

| Comparison | Video ID | OS | Network | Content | Adapter | Version | Lane | Phase | Samples | PASS | BLOCKED | UNSUPPORTED | FAIL | NOT_RUN | HTTP range F/B | First media p50/p95 (ms) | Decode p50/p95 (ms) |
|---|---|---|---|---|---|---|---|---|---:|---:|---:|---:|---:|---:|---|---:|---:|
| owner-home-2026-11-13-four-adapter-aqz | aqz-KE-bpKQ | windows | home | stable_public_video | excor | 0.1.4-excor0.2.0 | direct_audio | cold | 3 | 0 | 3 | 0 | 0 | 0 | F NOT_TESTED=3; B NOT_TESTED=3 | n/a | n/a |
| owner-home-2026-11-13-four-adapter-aqz | aqz-KE-bpKQ | windows | home | stable_public_video | excor | 0.1.4-excor0.2.0 | direct_audio | warm | 3 | 0 | 3 | 0 | 0 | 0 | F NOT_TESTED=3; B NOT_TESTED=3 | n/a | n/a |
| owner-home-2026-11-13-four-adapter-aqz | aqz-KE-bpKQ | windows | home | stable_public_video | innertubex | 0.1.3-innertubex0.11.6 | direct_audio | cold | 3 | 3 | 0 | 0 | 0 | 0 | F NOT_TESTED=3; B NOT_TESTED=3 | 1463/1513 | n/a |
| owner-home-2026-11-13-four-adapter-aqz | aqz-KE-bpKQ | windows | home | stable_public_video | innertubex | 0.1.3-innertubex0.11.6 | direct_audio | warm | 3 | 3 | 0 | 0 | 0 | 0 | F NOT_TESTED=3; B NOT_TESTED=3 | 179/210 | n/a |
| owner-home-2026-11-13-four-adapter-aqz | aqz-KE-bpKQ | windows | home | stable_public_video | newpipe | 0.1.0-newpipe-0.27.1 | direct_audio | cold | 3 | 3 | 0 | 0 | 0 | 0 | F NOT_TESTED=3; B NOT_TESTED=3 | 1540/1851 | n/a |
| owner-home-2026-11-13-four-adapter-aqz | aqz-KE-bpKQ | windows | home | stable_public_video | newpipe | 0.1.0-newpipe-0.27.1 | direct_audio | warm | 3 | 3 | 0 | 0 | 0 | 0 | F NOT_TESTED=3; B NOT_TESTED=3 | 929/1020 | n/a |
| owner-home-2026-11-13-four-adapter-aqz | aqz-KE-bpKQ | windows | home | stable_public_video | youtubejs | 0.1.1-youtubei.js18.3.0 | direct_audio | cold | 3 | 0 | 0 | 3 | 0 | 0 | F NOT_TESTED=3; B NOT_TESTED=3 | n/a | n/a |
| owner-home-2026-11-13-four-adapter-aqz | aqz-KE-bpKQ | windows | home | stable_public_video | youtubejs | 0.1.1-youtubei.js18.3.0 | direct_audio | warm | 3 | 0 | 0 | 3 | 0 | 0 | F NOT_TESTED=3; B NOT_TESTED=3 | n/a | n/a |

HTTP byte-range probes are reported separately from playback seeks. `rangeProbeResults` in JSON contains the method and per-direction result counts; these probes do not establish decoder or player seek behavior.

First-media and decode table timings show nearest-rank p50/p95 in milliseconds, rounded to whole milliseconds. JSON retains metric-specific eligible sample counts. All latency summaries are grouped by comparison ID, commit, date, video ID, OS, network category, content category, signed-out session, timeout, locale, decoder, adapter version, lane, and cold/warm phase. `eligibleSamples` is shown per metric in JSON.
