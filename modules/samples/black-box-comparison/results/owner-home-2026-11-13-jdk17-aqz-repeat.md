# Black-box comparison report

Timings include only PASS samples. Outcome counts include every attempted sample.
The report emits only validated normalized fields; adapters must exclude secrets from those fields.

| Comparison | OS | Network | Content | Adapter | Version | Lane | Phase | Samples | PASS | BLOCKED | UNSUPPORTED | FAIL | NOT_RUN | HTTP range F/B |
|---|---|---|---|---|---|---|---|---:|---:|---:|---:|---:|---:|---|
| owner-home-2026-11-13-jdk17-aqz-repeat | windows | home | music_video | excor | 0.1.4-excor0.2.0 | direct_audio | cold | 3 | 0 | 3 | 0 | 0 | 0 | F NOT_TESTED=3; B NOT_TESTED=3 |
| owner-home-2026-11-13-jdk17-aqz-repeat | windows | home | music_video | innertubex | 0.1.3-innertubex0.11.6 | direct_audio | cold | 3 | 3 | 0 | 0 | 0 | 0 | F PASS=3; B PASS=3 |

HTTP byte-range probes are reported separately from playback seeks. `rangeProbeResults` in JSON contains the method and per-direction result counts; these probes do not establish decoder or player seek behavior.

Latency summaries use nearest-rank p50/p95, grouped by comparison ID, commit, date, OS, network category, content category, signed-out session, timeout, locale, decoder, adapter version, lane, and cold/warm phase. `eligibleSamples` is shown per metric in JSON.

**Runtime evidence:** The adapters were launched with `JAVA_HOME` set to Temurin JDK 17.0.20.1; the Windows launchers use `JAVA_HOME\bin\java.exe`. The normalized sample schema records the runtime family as `jvm`, so this note carries the exact runtime version for these samples.
