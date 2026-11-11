# Black-box comparison report tool

This dependency-free Python tool validates normalized adapter results and produces a report. It makes no network requests. Do not treat its output as a performance result until separately built public-API adapters have produced samples under the same recorded comparison conditions.

## Input

The input is UTF-8 JSON Lines: one object per adapter attempt. The exact schema is documented in [`docs/BLACK_BOX_COMPARISON_RESEARCH.md`](../../docs/BLACK_BOX_COMPARISON_RESEARCH.md). Each sample records a full eXcor main commit, UTC run time, OS, network/content category, signed-out session, locale, timeout and decoder version, then adapter/version/runtime, lane, cold/warm phase and outcome.

The validator rejects missing or unknown fields, duplicate JSON keys, duplicate sample identifiers, unsupported classifications, non-finite values, malformed UTF-8, and URL-shaped or free-form diagnostic fields. Rejected input is reported without echoing the line. The schema limits string formats but cannot detect a secret deliberately placed in a field such as a version or run ID, so adapters must keep credentials and raw diagnostics private and emit only normalized values.

## Generate a report

```powershell
python samples/black-box-comparison/report.py --input results.jsonl --format json --output report.json
python samples/black-box-comparison/report.py --input results.jsonl --format markdown --output report.md
```

The report preserves all sanitized per-sample outcomes. It counts access blocks, unsupported transports and technical failures separately. p50/p95 use the nearest-rank method and include PASS samples only; each metric reports its own eligible sample count. Groups separate adapter/version, lane, cold/warm phase, comparison ID, commit, date, OS, network/content category, session category, timeout, locale and decoder.

## eXcor JVM adapter

The standalone adapter in [`excor-jvm`](excor-jvm/) uses the published eXcor JVM API and emits one normalized JSON line. Build it with `./gradlew -p samples/black-box-comparison/excor-jvm installDist` on Linux/macOS or `.\gradlew.bat -p samples/black-box-comparison/excor-jvm installDist` on Windows, after the library has been published to `mavenLocal()` (the Linux CI job does this first). Building and testing the adapter makes no YouTube requests; Gradle may resolve Maven artifacts. Running it performs signed-out metadata and extraction calls, then requests at most the first 64 KiB of the selected media stream using a bounded HTTP Range request. It never prints the signed media URL or request headers. The whole-sample timeout is limited to 30 seconds to match eXcor's current per-request cap.

Example from the repository root after building:

```powershell
$runDateUtc = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$mainCommit = git rev-parse main
$adapter = (Resolve-Path "samples/black-box-comparison/excor-jvm/build/install/excor-black-box-adapter/bin/excor-black-box-adapter.bat").Path
$line = & $adapter --video-id dQw4w9WgXcQ --comparison-id run-home-01 --main-commit $mainCommit --run-date-utc $runDateUtc --network-category home --content-category music_video --phase cold --sample-index 1 --timeout-seconds 30
if ($LASTEXITCODE -ne 0) { throw "adapter failed; no sample written" }
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::AppendAllText("results.jsonl", ($line -join "`n") + "`n", $utf8NoBom)
```

Use the same comparison ID, commit, UTC run time, environment categories and timeout for every sample in one run. The timeout budget covers warm-up plus the measured attempt. The adapter resets request counters after its same-instance warm-up, so `warm` timings and counts cover only the measured attempt. `requestCount` includes eXcor API calls and media request attempts; `mediaRequestCount` is the media subset. `firstMediaByteMs` is measured from the start of that attempt; `startupMs` is null because the adapter cannot measure JVM process launch. `heapDeltaBytes` is an approximate live heap change, not an allocation count. `PASS` means metadata/extraction and the initial media-byte probe completed; decode and backward/forward seeks remain `NOT_TESTED`, and `decodeMs` remains null. Full playback, decode and seek evidence still belongs in the owner-run matrix.

## InnerTubeX JVM adapter

The separate adapter in [`innertubex-jvm`](innertubex-jvm/) pins the public API artifact `com.github.MetrolistGroup.innertubex:innertubex:v0.11.6`. Build it independently with `./gradlew -p samples/black-box-comparison/innertubex-jvm installDist`. It has no eXcor SDK dependency and is not wired into the root Gradle build. The pinned artifact is GPL-3.0; read [`LICENSE-NOTICE.md`](innertubex-jvm/LICENSE-NOTICE.md) before building or distributing it. CI compiles and exercises only synthetic tests; neither action makes YouTube requests.

At runtime, the adapter uses the documented `InnerTubeExtractor.extract` API with `wantVideo=false` and disables HLS and SABR through `ContentHints.withStreamCapabilities`. It performs the same signed-out direct-audio initial-range probe as the eXcor adapter, with the same 30-second maximum sample budget. Every media URL and redirect is validated; only allowlisted non-credential headers are forwarded; response reads stop at 64 KiB. Output never includes signed URLs, request headers or raw exception text.

`extractMs` covers the extractor call, including metadata returned as part of that call. `metadataMs` remains null because extraction exposes metadata with the stream rather than as a separately timed operation. `requestCount` includes instrumented InnerTubeX client calls and media requests; `mediaRequestCount` and `rangeRequests` count media attempts. `startupMs`, decode and both seek results remain null/`NOT_TESTED`. `PASS` means extraction plus a bounded initial media read completed; it is not full playback or seek evidence. A stream that only uses a non-direct transport is reported as `UNSUPPORTED` in this direct-audio lane.

Run it as a separate process with the same flags shown in the eXcor JVM example, using `innertubex-jvm/build/install/innertubex-black-box-adapter/bin/innertubex-black-box-adapter` (or `.bat` on Windows). Keep the two adapter binaries separate. Do not distribute a linked InnerTubeX binary until licensing review is complete.

## Current limits

Both available JVM adapters cover extraction and a bounded initial-range probe only. YouTube.js and NewPipeExtractor adapters, full decode, backward/forward seek measurements, and matched owner-run samples remain outstanding. GPL-3.0 InnerTubeX and NewPipeExtractor adapters must stay in separate processes and outside the eXcor library dependency graph. Obtain licensing review before distributing any linked adapter binaries. Owner-run live playback remains separate from this report tool.
