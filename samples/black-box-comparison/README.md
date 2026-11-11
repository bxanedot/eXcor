# Black-box comparison report tool

This dependency-free Python tool validates normalized adapter results and produces a report. It makes no network requests and does not contain competitor adapters. Do not treat its output as a performance result until separately built public-API adapters have produced samples under the same recorded comparison conditions.

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

## YouTube.js Node adapter

The separate project in [`youtubejs-node`](youtubejs-node/) pins the MIT package `youtubei.js@18.3.0`. It uses the documented `Innertube.create` and `getStreamingData` APIs with `type: "audio"`; it has no eXcor SDK dependency. Install and run it from that directory with Node.js 20 or newer:

```powershell
npm ci --ignore-scripts
$runDateUtc = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$mainCommit = git -C ../../.. rev-parse main
node comparison-adapter.mjs --video-id dQw4w9WgXcQ --comparison-id run-home-01 --main-commit $mainCommit --run-date-utc $runDateUtc --network-category home --content-category music_video --phase cold --sample-index 1 --timeout-seconds 30
```

Running the adapter makes signed-out YouTube requests. It uses the documented `Innertube.create({ fetch })` hook for request counting, disables player retrieval, and selects only URL-backed audio formats. It never evaluates downloaded player JavaScript; videos that require deciphering and have no direct audio URL are reported as `UNSUPPORTED`. The custom fetch boundary caps API request bodies at 1 MiB and response bodies at 5 MiB; the separate media probe validates HTTPS hosts and redirects and reads at most 64 KiB. Fetch credentials are omitted, credential headers are removed, and no URL, header or raw exception text is emitted. The npm lockfile pins transitive dependencies; CI installs with lifecycle scripts disabled and checks JavaScript syntax without making YouTube requests.

For a cold sample, `extractMs` includes YouTube.js session creation, `getBasicInfo`, and selection of the highest-bitrate URL-backed audio format. For a warm sample, the adapter warms one instance, clears counters, then measures another call on the same instance. `requestCount` includes each custom API-fetch call, validated API redirect, and media request attempt. API redirects are followed only for GET/HEAD requests to allowlisted HTTPS YouTube hosts; media redirects are separately validated. `firstMediaByteMs` starts at the measured extraction attempt. `metadataMs` and `startupMs` remain null; decode and backward/forward seeks remain `NOT_TESTED`. `PASS` means stream selection and the initial bounded media read completed.

## Current limits

The eXcor JVM and YouTube.js Node adapters cover extraction and an initial-range probe only. The InnerTubeX adapter is tracked in PR #120 and NewPipeExtractor in PR #122; exact-head CI is pending for both. Matched comparative measurements remain outstanding. GPL-3.0 adapters for InnerTubeX and NewPipeExtractor must remain separate processes and outside the eXcor library dependency graph. Obtain licensing review before distributing any linked adapter binaries. Owner-run live playback remains separate from this report tool.
