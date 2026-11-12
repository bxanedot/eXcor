# Black-box comparison report tool

This dependency-free Python tool validates normalized adapter results and produces a report. The report tool makes no network requests; separately built public-API adapters live in isolated subprojects below. Do not treat its output as a performance result until adapters have produced samples under the same recorded comparison conditions.

## Input

The input is UTF-8 JSON Lines: one object per adapter attempt. The exact schema is documented in [`docs/BLACK_BOX_COMPARISON_RESEARCH.md`](../../docs/BLACK_BOX_COMPARISON_RESEARCH.md). Each sample records a full eXcor main commit, UTC run time, OS, network/content category, signed-out session, locale, timeout and decoder version, then adapter/version/runtime, lane, cold/warm phase and outcome.

The validator rejects missing or unknown fields, duplicate JSON keys, duplicate sample identifiers, unsupported classifications, non-finite values, malformed UTF-8, and URL-shaped or free-form diagnostic fields. Rejected input is reported without echoing the line. The schema limits string formats but cannot detect a secret deliberately placed in a field such as a version or run ID, so adapters must keep credentials and raw diagnostics private and emit only normalized values.

## Generate a report

```powershell
python samples/black-box-comparison/report.py --input results.jsonl --format json --output report.json
python samples/black-box-comparison/report.py --input results.jsonl --format markdown --output report.md
```

The report preserves all sanitized per-sample outcomes. It counts access blocks, unsupported transports and technical failures separately. p50/p95 use the nearest-rank method and include PASS samples only; each metric reports its own eligible sample count. Groups separate adapter/version, lane, cold/warm phase, comparison ID, commit, date, OS, network/content category, session category, timeout, locale and decoder.

## Run a matched owner comparison

`run_matched.py` coordinates the four isolated adapters from one owner-controlled machine and network. First build/install each adapter using the instructions below. For eXcor, publish the library to `mavenLocal()` first with `./gradlew publishToMavenLocal`; install the eXcor, InnerTubeX, and NewPipe launchers with their documented `installDist` commands; install the YouTube.js package with `npm ci --ignore-scripts` in `youtubejs-node`. These steps resolve/build code but make no YouTube requests. Review the separate adapters' license notices before distributing them.

Run from the repository root, on an authorized network, with public video IDs you are permitted to check:

```powershell
python samples/black-box-comparison/run_matched.py `
  --video-id dQw4w9WgXcQ --video-id kJQP7kiw5Fk `
  --comparison-id owner-home-2026-11-12 `
  --network-category home --content-category music_video `
  --phases cold warm --sample-indices 1 2 3 `
  --timeout-seconds 30 `
  --output results/owner-home-2026-11-12.jsonl
```

The runner captures one shared commit, UTC timestamp, OS, locale, timeout and category context, preflights every selected adapter with `--help`, then executes samples sequentially in an interleaved order. It emits only schema-validated JSON lines, never raw adapter output, and refuses to overwrite an existing output file. `--adapters` can select a subset for diagnosis; subset runs do not provide four-way comparisons. The adapters make signed-out direct-audio requests only. A `PASS` still means extraction plus an initial read of at most 64 KiB: full decode, backward/forward seek and playback parity remain unmeasured, so G11 stays partial until owner-run results and those measurements are reviewed.

To compare HTTP byte-range handling between eXcor and InnerTubeX, run the paired JVM adapters with the opt-in flag:

```powershell
python samples/black-box-comparison/run_matched.py `
  --video-id dQw4w9WgXcQ `
  --comparison-id owner-home-ranges-2026-11-12 `
  --network-category home --content-category music_video `
  --phases cold warm --sample-indices 1 2 3 `
  --timeout-seconds 30 --adapters excor innertubex --range-probes `
  --output results/owner-home-ranges-2026-11-12.jsonl
```

Each measured attempt reads the initial range, then requests 4 KiB near 75% of the reported media length and 4 KiB near 25%. The report records whether those HTTP byte offsets were honored. `rangeForwardProbe` and `rangeBackwardProbe` describe the request order only; `forwardSeek` and `backwardSeek` remain `NOT_TESTED` because these probes do not exercise a decoder or player. Samples without a numeric content length or with media shorter than 16 KiB report `UNAVAILABLE`. This opt-in mode requires exactly the eXcor and InnerTubeX adapters so an unsupported adapter cannot silently emit incomplete probe data.

## eXcor JVM adapter

The standalone adapter in [`excor-jvm`](excor-jvm/) uses the published eXcor JVM API and emits one normalized JSON line. Build it with `./gradlew -p samples/black-box-comparison/excor-jvm installDist` on Linux/macOS or `.\gradlew.bat -p samples/black-box-comparison/excor-jvm installDist` on Windows, after the library has been published to `mavenLocal()` (the Linux CI job does this first). Building and testing the adapter makes no YouTube requests; Gradle may resolve Maven artifacts. Running it performs signed-out metadata and extraction calls, then requests at most the first 64 KiB of the selected media stream using a bounded HTTP Range request. The optional `--range-probes` flag adds two bounded 4 KiB offset requests. It never prints the signed media URL or request headers. The whole-sample timeout is limited to 30 seconds to match eXcor's current per-request cap.

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

At runtime, the adapter uses the documented `InnerTubeExtractor.extract` API with `wantVideo=false` and disables HLS and SABR through `ContentHints.withStreamCapabilities`. It performs a signed-out direct-audio initial-range probe within the 30-second maximum sample budget. The media URL and each media redirect are validated; only allowlisted non-credential headers are forwarded; media response reads stop at 64 KiB. Output never includes signed URLs, request headers or raw exception text.

`extractMs` covers the extractor call, including metadata returned as part of that call. `metadataMs` remains null because extraction exposes metadata with the stream rather than as a separately timed operation. `requestCount` includes instrumented InnerTubeX client calls and media requests; `mediaRequestCount` and `rangeRequests` count media attempts. With `--range-probes`, `rangeRequests` includes the two offset requests. `startupMs`, decode and both player seek results remain null/`NOT_TESTED`. `PASS` means extraction plus a bounded initial media read completed; it is not full playback or seek evidence. A stream that only uses a non-direct transport is reported as `UNSUPPORTED` in this direct-audio lane. Keep this GPL-linked adapter separate from eXcor and do not distribute it before licensing review.

## YouTube.js Node adapter

The standalone project in [`youtubejs-node`](youtubejs-node/) pins MIT-licensed `youtubei.js@18.3.0` and uses the public `Innertube.create({ fetch })` and `getBasicInfo` APIs. Build it with Node.js 22:

```powershell
cd samples/black-box-comparison/youtubejs-node
npm ci --ignore-scripts
$runDateUtc = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$mainCommit = git -C ../../.. rev-parse main
node comparison-adapter.mjs --video-id dQw4w9WgXcQ --comparison-id run-home-01 --main-commit $mainCommit --run-date-utc $runDateUtc --network-category home --content-category music_video --phase cold --sample-index 1 --timeout-seconds 30
```

The adapter selects the highest-bitrate audio format that already has a direct URL. It disables player retrieval and never evaluates downloaded player JavaScript; cipher-only formats are reported as `UNSUPPORTED`. The custom fetch wrapper caps known API request bodies at 1 MiB and response bodies at 5 MiB. The separate media probe validates HTTPS hosts and redirects and reads at most 64 KiB. Fetch credentials are omitted, credential headers are removed, and no signed URL, header or raw exception text is emitted. CI installs the pinned lockfile with scripts disabled and checks JavaScript syntax without making YouTube requests.

For a cold sample, `extractMs` includes YouTube.js session creation, `getBasicInfo`, and direct-audio selection. A warm sample reuses one instance and resets counters after warm-up. `metadataMs` and `startupMs` remain null; decode and backward/forward seek remain `NOT_TESTED`. `PASS` means format selection and the initial bounded media read completed.

## NewPipeExtractor JVM adapter

The standalone adapter in [`newpipe-jvm`](newpipe-jvm/) pins NewPipeExtractor `v0.27.1` and emits the same sanitized sample contract. It uses the public `Downloader` boundary for request counts and timings, selects URL-backed progressive HTTP audio, and probes at most the first 64 KiB. It does not decode or test seeks. The dependency is GPL-3.0-or-later and stays outside eXcor's library graph; review licensing before distributing the linked adapter. Build it with `./gradlew -p samples/black-box-comparison/newpipe-jvm assemble installDist`. The build resolves the pinned dependency but makes no YouTube requests.
## Current limits

The eXcor, InnerTubeX, YouTube.js and NewPipeExtractor adapters cover extraction and an initial-range probe. The eXcor and InnerTubeX JVM pair can also opt into bounded HTTP byte-offset probes; those are not player seeks. Full decode, playback seeks and matched owner-run results remain outstanding. NewPipeExtractor v0.27.1 passed all six exact-head CI jobs in run `43102342730`; YouTube.js passed all six in run `43105597786`; the InnerTubeX v0.11.6 adapter merged in PR #120 and passed all six jobs in run `43109304794` at exact head `4e4931cf1011fa8ad37acd89319798365593c59d`. GPL-3.0 adapters for InnerTubeX and NewPipeExtractor stay separate from eXcor's dependency graph; obtain licensing review before distributing linked adapter binaries. Owner-run live playback remains separate from this report tool.
