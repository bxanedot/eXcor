# Black-box comparison report tool

This dependency-free Python tool validates normalized adapter results and produces a report. The report tool makes no network requests; separately built public-API adapters live in isolated subprojects below. Do not treat its output as a performance result until adapters have produced samples under the same recorded comparison conditions.

## Input

The input is UTF-8 JSON Lines: one object per adapter attempt. The exact schema is documented in [`docs/BLACK_BOX_COMPARISON_RESEARCH.md`](../../../docs/BLACK_BOX_COMPARISON_RESEARCH.md). Each sample records a full eXcor main commit, UTC run time, OS, network/content category, signed-out session, locale, timeout and decoder version, then adapter/version/runtime, lane, cold/warm phase and outcome.

The validator rejects missing or unknown fields, duplicate JSON keys, duplicate sample identifiers, unsupported classifications, non-finite values, malformed UTF-8, and URL-shaped or free-form diagnostic fields. Rejected input is reported without echoing the line. The schema limits string formats but cannot detect a secret deliberately placed in a field such as a version or run ID, so adapters must keep credentials and raw diagnostics private and emit only normalized values.

## Generate a report

```powershell
python modules/samples/black-box-comparison/report.py --input results.jsonl --format json --output report.json
python modules/samples/black-box-comparison/report.py --input results.jsonl --format markdown --output report.md
```

The report preserves all sanitized per-sample outcomes. It counts access blocks, unsupported transports and technical failures separately. p50/p95 use the nearest-rank method and include PASS samples only; each metric reports its own eligible sample count. Summaries keep each video ID separate and group by adapter/version, lane, cold/warm phase, comparison ID, commit, date, OS, network/content category, session category, timeout, locale and decoder.

## Run a matched owner comparison

`run_matched.py` coordinates the four isolated adapters from one owner-controlled machine and network. First build/install each adapter using the instructions below. For eXcor, publish the library to `mavenLocal()` first with `./gradlew publishToMavenLocal`; install the eXcor, InnerTubeX, and NewPipe launchers with their documented `installDist` commands; install the YouTube.js package with `npm ci --ignore-scripts` in `youtubejs-node`. These steps resolve/build code but make no YouTube requests. Review the separate adapters' license notices before distributing them.

The JVM adapters build with a JDK 21 toolchain and target Java 17 bytecode. The launchers use `JAVA_HOME`/`PATH`, which may differ from the JDK Gradle used to build them. This workstation's Java 25 runtime passed adapter preflight and matched decode/range runs. A separate Temurin JDK 17.0.20.1 run passed 3/3 cold and 3/3 warm decode/range attempts per adapter on `dQw4w9WgXcQ`; see the [normalized report](results/owner-home-2026-11-13-jdk17-direct-audio.md) and [sanitized samples](results/owner-home-2026-11-13-jdk17-direct-audio.jsonl). This is one video/network and not broad runtime or performance coverage.

Run from the repository root, on an authorized network, with public video IDs you are permitted to check:

```powershell
python modules/samples/black-box-comparison/run_matched.py `
  --video-id dQw4w9WgXcQ --video-id kJQP7kiw5Fk `
  --comparison-id owner-home-2026-11-12 `
  --network-category home --content-category music_video `
  --phases cold warm --sample-indices 1 2 3 `
  --timeout-seconds 30 `
  --output results/owner-home-2026-11-12.jsonl
```

The runner captures one shared commit, UTC timestamp, OS, locale, timeout and category context, preflights every selected adapter with `--help`, then executes samples sequentially in an interleaved order. It emits only schema-validated JSON lines, never raw adapter output, and refuses to overwrite an existing output file. `--adapters` can select a subset for diagnosis; subset runs do not provide four-way comparisons. The adapters make signed-out direct-audio requests only. By default, `PASS` means extraction plus an initial read of at most 64 KiB. The paired JVM adapters can opt into full direct-audio decode; player seeks and playback parity remain unmeasured, so G11 stays partial until owner-run results are reviewed.

To compare HTTP byte-range handling between eXcor and InnerTubeX, run the paired JVM adapters with the opt-in flag:

```powershell
python modules/samples/black-box-comparison/run_matched.py `
  --video-id dQw4w9WgXcQ `
  --comparison-id owner-home-ranges-2026-11-12 `
  --network-category home --content-category music_video `
  --phases cold warm --sample-indices 1 2 3 `
  --timeout-seconds 30 --adapters excor innertubex --range-probes `
  --output results/owner-home-ranges-2026-11-12.jsonl
```

Each measured attempt reads the initial range, then requests 4 KiB near 75% of the reported media length and 4 KiB near 25%. The report records whether those HTTP byte offsets were honored. `rangeForwardProbe` and `rangeBackwardProbe` describe the request order only; `forwardSeek` and `backwardSeek` remain `NOT_TESTED` because these probes do not exercise a decoder or player. Samples without a numeric content length or with media shorter than 16 KiB report `UNAVAILABLE`. This opt-in mode requires exactly the eXcor and InnerTubeX adapters so an unsupported adapter cannot silently emit incomplete probe data.

To download and decode the complete direct-audio stream with FFmpeg, add `--full-decode` to the same paired JVM command. It can be combined with `--range-probes`:

```powershell
python modules/samples/black-box-comparison/run_matched.py `
  --video-id dQw4w9WgXcQ `
  --comparison-id owner-home-decode-2026-11-12 `
  --network-category home --content-category music_video `
  --phases cold warm --sample-indices 1 2 3 `
  --timeout-seconds 30 --adapters excor innertubex --range-probes --full-decode `
  --output results/owner-home-decode-2026-11-12.jsonl
```

Full decode requires exactly those two adapters. The runner records one sanitized FFmpeg version for both. Each adapter requires a known media size no larger than 256 MiB, requests `Range: bytes=0-`, validates that a `206` response covers the complete known object, and streams exactly the expected bytes to FFmpeg through stdin. A `200` response is accepted only if its content length and bytes read match the known size. The signed media URL is never passed to FFmpeg or written to the report. Missing FFmpeg, unknown length or an oversized stream yields `decode: UNSUPPORTED`; HTTP, timeout and decoder failures retain their own outcomes. `decodeMs` covers the full transfer and decoder process time. This is a whole-file decode check, not playback startup or interactive seeking, so `startupMs`, `forwardSeek` and `backwardSeek` remain unmeasured.

## eXcor JVM adapter

The standalone adapter in [`excor-jvm`](excor-jvm/) uses the published eXcor JVM API and emits one normalized JSON line. Build it with `./gradlew -p modules/samples/black-box-comparison/excor-jvm installDist` on Linux/macOS or `.\gradlew.bat -p modules/samples/black-box-comparison/excor-jvm installDist` on Windows, after the library has been published to `mavenLocal()` (the Linux CI job does this first). Building and testing the adapter makes no YouTube requests; Gradle may resolve Maven artifacts. Running it performs signed-out metadata and extraction calls, then requests at most the first 64 KiB of the selected media stream using a bounded HTTP Range request. The optional `--range-probes` flag adds two bounded 4 KiB offset requests; `--full-decode` downloads and decodes the full stream under the 256 MiB cap. It never prints the signed media URL or request headers, and the URL is not passed to FFmpeg. The whole-sample timeout is limited to 30 seconds to match eXcor's current per-request cap.

Example from the repository root after building:

```powershell
$runDateUtc = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$mainCommit = git rev-parse main
$adapter = (Resolve-Path "modules/samples/black-box-comparison/excor-jvm/build/install/excor-black-box-adapter/bin/excor-black-box-adapter.bat").Path
$line = & $adapter --video-id dQw4w9WgXcQ --comparison-id run-home-01 --main-commit $mainCommit --run-date-utc $runDateUtc --network-category home --content-category music_video --phase cold --sample-index 1 --timeout-seconds 30
if ($LASTEXITCODE -ne 0) { throw "adapter failed; no sample written" }
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::AppendAllText("results.jsonl", ($line -join "`n") + "`n", $utf8NoBom)
```

Use the same comparison ID, commit, UTC run time, environment categories and timeout for every sample in one run. The timeout budget covers warm-up plus the measured attempt. The adapter resets counters after its same-instance warm-up, so `warm` timings and counts cover only the measured attempt. `requestCount` is adapter-observed traffic, not a portable network counter; the current InnerTubeX adapter does not count all extractor player requests, so do not compare request totals. `mediaRequestCount` counts the bounded media attempts; `rangeRequests` counts requests with a byte-range header, including the initial probe, optional offset probes and full-object transfer. `firstMediaByteMs` is measured from immediately before extraction; `startupMs` is null because the adapter cannot measure JVM process launch. `heapDeltaBytes` is an approximate live heap change, not an allocation count. Without `--full-decode`, decode remains `NOT_TESTED` and `decodeMs` is null. With it, `decodeMs` includes full media transfer and FFmpeg decode; backward/forward player seeks remain `NOT_TESTED` in either mode. Full playback and seek evidence still belongs in the owner-run matrix.

## InnerTubeX JVM adapter

The separate adapter in [`innertubex-jvm`](innertubex-jvm/) pins the public API artifact `com.github.MetrolistGroup.innertubex:innertubex:v0.11.6`. Build it independently with `./gradlew -p modules/samples/black-box-comparison/innertubex-jvm installDist`. It has no eXcor SDK dependency and is not wired into the root Gradle build. The pinned artifact is GPL-3.0; read [`LICENSE-NOTICE.md`](innertubex-jvm/LICENSE-NOTICE.md) before building or distributing it. CI compiles and exercises only synthetic tests; neither action makes YouTube requests.

At runtime, the adapter uses the documented `InnerTubeExtractor.extract` API with `wantVideo=false` and disables HLS and SABR through `ContentHints.withStreamCapabilities`. It performs a signed-out direct-audio initial-range probe within the 30-second maximum sample budget. The media URL and each media redirect are validated; only allowlisted non-credential headers are forwarded; media response reads stop at 64 KiB. Output never includes signed URLs, request headers or raw exception text.

`extractMs` covers the extractor call, including metadata returned as part of that call. `metadataMs` remains null because extraction exposes metadata with the stream rather than as a separately timed operation. The request counter is attached to the supplied Ktor client; the v0.11.6 extractor uses a configured `postClient`, and live samples show the counter missing player traffic. Treat `requestCount` as incomplete and do not compare it with eXcor. `mediaRequestCount` counts media attempts and `rangeRequests` counts each request carrying a byte-range header, including the initial probe, two optional offset probes and the full-object `bytes=0-` request used by `--full-decode`. Without `--full-decode`, decode remains `NOT_TESTED`; with it, `decodeMs` includes full media transfer and FFmpeg decode. `startupMs` and both player seek results remain null/`NOT_TESTED`. `PASS` means extraction plus the enabled media checks completed; it is not player or seek evidence. A stream that only uses a non-direct transport is reported as `UNSUPPORTED` in this direct-audio lane. Keep this GPL-linked adapter separate from eXcor and do not distribute it before licensing review.

## YouTube.js Node adapter

The standalone project in [`youtubejs-node`](youtubejs-node/) pins MIT-licensed `youtubei.js@18.3.0` and uses the public `Innertube.create({ fetch })` and `getBasicInfo` APIs. Build it with Node.js 22:

```powershell
cd modules/samples/black-box-comparison/youtubejs-node
npm ci --ignore-scripts
$runDateUtc = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$mainCommit = git -C ../../.. rev-parse main
node comparison-adapter.mjs --video-id dQw4w9WgXcQ --comparison-id run-home-01 --main-commit $mainCommit --run-date-utc $runDateUtc --network-category home --content-category music_video --phase cold --sample-index 1 --timeout-seconds 30
```

The adapter selects the highest-bitrate audio format that already has a direct URL. It disables player retrieval and never evaluates downloaded player JavaScript; cipher-only formats are reported as `UNSUPPORTED`. The custom fetch wrapper caps known API request bodies at 1 MiB and response bodies at 5 MiB. The separate media probe validates HTTPS hosts and redirects and reads at most 64 KiB. Fetch credentials are omitted, credential headers are removed, and no signed URL, header or raw exception text is emitted. CI installs the pinned lockfile with scripts disabled and checks JavaScript syntax without making YouTube requests.

For a cold sample, `extractMs` includes YouTube.js session creation, `getBasicInfo`, and direct-audio selection. A warm sample reuses one instance and resets counters after warm-up. `metadataMs` and `startupMs` remain null; decode and backward/forward seek remain `NOT_TESTED`. `PASS` means format selection and the initial bounded media read completed.

## NewPipeExtractor JVM adapter

The standalone adapter in [`newpipe-jvm`](newpipe-jvm/) pins NewPipeExtractor `v0.27.1` and emits the same sanitized sample contract. It uses the public `Downloader` boundary for request counts and timings, selects URL-backed progressive HTTP audio, and probes at most the first 64 KiB. It does not decode or test seeks. The dependency is GPL-3.0-or-later and stays outside eXcor's library graph; review licensing before distributing the linked adapter. Build it with `./gradlew -p modules/samples/black-box-comparison/newpipe-jvm assemble installDist`. The build resolves the pinned dependency but makes no YouTube requests.
## Current limits

**Latest owner-run rerun after VISIONOS-first selection (2026-11-13):** three cold samples per adapter on one signed-out video/network. eXcor and InnerTubeX both passed full 3,507,483-byte Opus decode and both HTTP byte-range probes in 3/3. First-media p50/p95 was 1,146/1,265 ms for eXcor and 1,169/1,211 ms for InnerTubeX; decode p50/p95 was 307/312 ms and 365/370 ms. This dirty-worktree diagnostic does not establish broad performance parity or player seeks. See the [report](results/owner-home-2026-11-13-visionos-first.md) and [sanitized samples](results/owner-home-2026-11-13-visionos-first.jsonl).

**Latest paired rerun after lazy watch-page fetching (2026-11-13):** three cold and three same-instance warm attempts per adapter passed full decode of 3,507,483 bytes and both HTTP byte-range probes in every sample. Cold first-media p50/p95 was 357/438 ms for eXcor and 1,344/1,662 ms for InnerTubeX; warm was 98/106 ms and 168/187 ms. Median decode was 321/316 ms for eXcor and 367/367 ms for InnerTubeX (cold/warm). The engine now skips the watch-page request when its selected profile does not need a player script; scripted fallbacks still fetch it. These are one-video, one-network results from a dirty worktree; they do not measure JVM startup or player seeking. Request counts remain incomparable because the InnerTubeX adapter misses extractor player traffic. See the [report](results/owner-home-2026-11-13-lazy-watch.md), [JSON summary](results/owner-home-2026-11-13-lazy-watch-report.json) and [sanitized samples](results/owner-home-2026-11-13-lazy-watch.jsonl).

**JDK 17 second-ID repeat (2026-11-13):** On `aqz-KE-bpKQ`, eXcor was access-blocked before media in 3/3 cold samples; InnerTubeX decoded 10,332,653 bytes and passed both byte-range probes in 3/3. An earlier Java 25 rerun passed eXcor in 1/3 and InnerTubeX in 3/3, confirming inconsistent access across runs rather than another repeatable shared decode. See the [report](results/owner-home-2026-11-13-jdk17-aqz-repeat.md) and [sanitized samples](results/owner-home-2026-11-13-jdk17-aqz-repeat.jsonl).

The eXcor, InnerTubeX, YouTube.js and NewPipeExtractor adapters cover extraction and an initial-range probe. The eXcor and InnerTubeX JVM pair can opt into bounded HTTP byte-offset probes and full FFmpeg decode; these are not player seeks. An earlier three-sample shared baseline, [2026-11-13 report](results/owner-home-2026-11-13-adapter-fix-full.md) and [sanitized samples](results/owner-home-2026-11-13-adapter-fix-full.jsonl), records both adapters fully decoding the same 3,507,483-byte Opus stream and passing both offset probes in 3/3 cold attempts on one signed-out Windows home-network video. InnerTubeX reached the first media byte sooner (p50 1,174 ms vs 5,130 ms); eXcor's median decode was slightly faster (314 ms vs 372 ms). Two other IDs gave InnerTubeX full-decode/range PASS 3/3 while eXcor was access-blocked before media 3/3; see the [second](results/owner-home-2026-11-13-second-video.md) and [third](results/owner-home-2026-11-13-third-video.md) reports. A later pre-order [matched recheck](results/owner-home-2026-11-13-cobalt-profile-paired.md) rebuilt eXcor 0.2.0 with the Cobalt TV profile and passed one cold sample per adapter on the same ID: both decoded all 3,507,483 bytes and passed both offset probes. Raw first-media-byte times were 4,724 ms for eXcor and 1,100 ms for InnerTubeX; decode times were 328 ms and 376 ms. These single-sample timings are diagnostic, not a broad performance ranking. A separate eXcor-only run logged Cobalt as UNPLAYABLE, then decoded through VISIONOS; see its [run record](results/owner-home-2026-11-13-cobalt-profile-recheck.md). All these owner-run results are local diagnostics, not public readiness credit. Player seeks and broader multi-video/network decode parity remain outstanding. The older [2026-11-12 matched report](results/owner-home-2026-11-12-range-full.md) and [sanitized samples](results/owner-home-2026-11-12-range-full.jsonl) are historical: InnerTubeX returned `FAIL/OTHER` before it reached media probing. The earlier [2026-11-12 direct-audio report](results/owner-home-2026-11-12-direct-audio.md) records the full-transfer timeout before its range request was corrected. The 2026-11-12 adapter 0.1.4 follow-up also predates the InnerTubeX JSON fix; see the [pre-fix report](results/owner-home-2026-11-12-classifier-recheck.md). NewPipeExtractor v0.27.1 passed all six exact-head CI jobs in run `43102342730`; YouTube.js passed all six in run `43105597786`; the InnerTubeX v0.11.6 adapter merged in PR #120 and passed all six jobs in run `43109304794` at exact head `4e4931cf1011fa8ad37acd89319798365593c59d`. GPL-3.0 adapters for InnerTubeX and NewPipeExtractor stay separate from eXcor's dependency graph; obtain licensing review before distributing linked adapter binaries.
