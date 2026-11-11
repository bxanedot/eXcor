# Public API feasibility for black-box comparison — 2026-11-11

## Scope

This note checks whether a reproducible comparator can drive eXcor, InnerTubeX, YouTube.js and NewPipeExtractor through published interfaces. Its findings and implementation are based on official project READMEs, changelogs, API documentation and package metadata; no competitor implementation code is used or copied. It establishes adapter feasibility, not measured performance or parity.

## Published interfaces

| Project | Documented runtime and pin | Public extraction surface | Instrumentation and playback notes |
|---|---|---|---|
| eXcor | Kotlin Multiplatform, including JVM; the repository's [quickstart](../samples/jvm-quickstart/src/main/kotlin/PlaybackDiagnostics.kt) is the consumer path. Pin the exact published artifact/build. | `Excor.extract(videoId)` resolves audio and `Excor.extractVideo(...)` resolves matched video and audio; see the [feature matrix](FEATURE_MATRIX.md). | The standalone [`excor-jvm` adapter](../samples/black-box-comparison/excor-jvm/) now measures metadata, extraction and a bounded initial range read through the public host API. It keeps signed media URLs private. Full decode and backward/forward seek remain unmeasured by this adapter; HLS is experimental and not live-verified. |
| InnerTubeX | Kotlin Multiplatform with Android/JVM/iOS; the [README](https://github.com/MetrolistGroup/innertubex/blob/main/README.md) uses JitPack. The README installation pins `v0.11.5`, while the [changelog](https://github.com/MetrolistGroup/innertubex/blob/main/CHANGELOG.md) has a latest dated entry `0.11.6` (2026-11-07). The documented playback harness is source-built with Gradle, not a standalone published CLI. | README examples expose `InnerTube` operations and `InnerTubeExtractor.extract(videoId, hints, audioQuality)` for stream extraction. | A separately launched JVM adapter can supply its own Ktor `HttpClient`. The README describes an opt-in harness that paces playback and performs backward/forward seeks across direct/HLS/SABR profiles; it is a useful transport-specific lane, but its measurements include that harness/player. GitHub repository metadata reports GPL-3.0 ([license metadata](https://api.github.com/repos/MetrolistGroup/innertubex)). Do not add it as a dependency of the eXcor library artifact. |
| YouTube.js | [Getting Started](https://ytjs.dev/guide/getting-started) documents Node.js, Deno and browser setup; the library's Node fetch path requires Node 16.8 or newer. The official [npm package](https://www.npmjs.com/package/youtubei.js) was version 18.3.0 and MIT-licensed when checked 2026-11-11; the separate adapter currently requires Node 20 for its abort-signal APIs. | The [Innertube API](https://ytjs.dev/api/classes/Innertube) exposes `create` and `getStreamingData`; documented [FormatOptions](https://ytjs.dev/api/youtubei.js/namespaces/Types/interfaces/FormatOptions) selects `type: "audio"`, and the returned [Format](https://ytjs.dev/api/youtubei.js/namespaces/Misc/classes/Format) exposes URL, MIME type and audio/video flags. | Documented [SessionOptions](https://ytjs.dev/api/type-aliases/SessionOptions) accepts a custom `fetch` callback. The [setup guide](https://ytjs.dev/guide/getting-started) says cipher deciphering needs a caller-provided interpreter and shows `Function` as an example. The adapter disables evaluation by default; its explicit opt-in uses that documented hook in a separate signed-out process. This evaluates remote code and is not an OS sandbox. The adapter validates media URLs and redirects, caps its media read, and emits sanitized results. CI installs the pinned npm lockfile with scripts disabled and checks syntax; it makes no YouTube requests. |
| NewPipeExtractor | The [README](https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/README.md) documents JitPack and asks consumers to substitute a release version. The [latest release](https://github.com/TeamNewPipe/NewPipeExtractor/releases/latest) showed `v0.27.1` dated 2026-11-10, while the consulted [StreamInfo Javadocs](https://teamnewpipe.github.io/NewPipeExtractor/javadoc/org/schabi/newpipe/extractor/stream/StreamInfo.html) were generated for `v0.26.5` and the [index](https://teamnewpipe.github.io/NewPipeExtractor/javadoc/index-all.html) for `v0.27.0`; verify the pinned artifact/API before recording a run. | Javadocs expose [`NewPipe.init(Downloader)`](https://teamnewpipe.github.io/NewPipeExtractor/javadoc/org/schabi/newpipe/extractor/NewPipe.html), [`StreamInfo.getInfo(url)`](https://teamnewpipe.github.io/NewPipeExtractor/javadoc/org/schabi/newpipe/extractor/stream/StreamInfo.html), and stream accessors such as `getAudioStreams()`. | A custom [`Downloader`](https://teamnewpipe.github.io/NewPipeExtractor/javadoc/org/schabi/newpipe/extractor/downloader/Downloader.html) is the public request boundary for counting and timing calls. Its streams can feed a common direct-media verifier. GitHub repository metadata reports GPL-3.0 ([license metadata](https://api.github.com/repos/TeamNewPipe/NewPipeExtractor)); keep any adapter out of the eXcor library dependency graph and obtain licensing review before distributing linked adapter binaries. |

Sources are linked in each row. This note checks public documentation and metadata only, not the respective implementation sources. Current versions must be re-pinned before each benchmark run. Checked 2026-11-11.

## Comparator design consequences

1. Run one adapter process per project on the same host, network, locale, content set and timeout budget. Pin each artifact/runtime and record its exact version and configuration. This keeps GPL implementations outside the eXcor build while allowing a local black-box run; do not distribute bundled GPL-linked adapters without licensing review.
2. Instrument requests at each documented host boundary: eXcor/InnerTubeX HTTP clients, YouTube.js `fetch`, and NewPipe's `Downloader`. Count library requests separately from media-player requests. Use a shared monotonic-clock contract for extraction start, metadata result, first media byte, and completion.
3. Compare a common **direct-audio lane** using the same eligible video IDs, local decoder, timeout, and range-read procedure. Normalize only fields supported by all adapters: status category, elapsed times, request count, stream class, MIME/codec, content bytes, decode outcome, range/seek outcome and sample count.
4. Keep a separate **native transport lane** for HLS/SABR/UMP. A transport-specific player/harness changes what is measured, so its results cannot be combined with direct-download timings. Report unavailable transports as `UNSUPPORTED`; service access refusal as `BLOCKED`; and parsing or decode errors as `FAIL`.
5. Emit sanitized JSON only. Never write media URLs, query strings, cookies, Authorization headers, PO tokens, visitor identifiers, or raw service responses. Preserve per-sample outcomes so p50/p95 do not silently discard access denials; publish the eligible sample count alongside each statistic.

The documented interfaces are sufficient to build separate adapters, but they do **not** establish that the artifacts are mutually comparable or that the current libraries all support the same seek operation. The eXcor and YouTube.js adapters cover extraction and the initial media range only; neither decodes media or tests seeks. The InnerTubeX adapter is in separate PR #120; NewPipeExtractor, exact-head CI review for each adapter, and owner-run comparative measurements remain outstanding. Until matched samples include real playback, decode and seek evidence, G11 remains partial and no speed, reliability or parity claim is supported.

## Adapter output contract

Each adapter should emit exactly one JSON object per sample with these normalized fields. The offline report validator in `samples/black-box-comparison/report.py` enforces this schema and aggregates only comparable groups:

```json
{
  "schemaVersion": 1,
  "comparison": {
    "id": "run-20261111-home-01",
    "mainCommit": "<full 40- or 64-character eXcor commit>",
    "runDateUtc": "2026-11-11T18:30:00Z",
    "os": "linux",
    "networkCategory": "home",
    "contentCategory": "music_video",
    "sessionCategory": "signed_out",
    "timeoutSeconds": 120,
    "locale": "en-US",
    "decoderVersion": "ffmpeg-7.1"
  },
  "adapter": "excor",
  "adapterVersion": "0.2.0",
  "runtime": "jvm",
  "lane": "direct_audio",
  "phase": "cold",
  "sampleIndex": 1,
  "videoId": "dQw4w9WgXcQ",
  "outcome": "PASS",
  "failureClass": null,
  "measurements": {
    "metadataMs": 0,
    "extractMs": 0,
    "startupMs": 0,
    "firstMediaByteMs": null,
    "decodeMs": null,
    "requestCount": 0,
    "mediaRequestCount": 0,
    "bytesRead": 0,
    "rangeRequests": 0,
    "heapDeltaBytes": null,
    "backwardSeek": "NOT_TESTED",
    "forwardSeek": "NOT_TESTED",
    "decode": "NOT_TESTED",
    "codec": null,
    "mimeType": null
  }
}
```

Allowed outcomes are `PASS`, `BLOCKED`, `UNSUPPORTED`, `FAIL`, and `NOT_RUN`. For the current eXcor and YouTube.js adapters, `PASS` means extraction and an initial bounded media read completed; it does not imply full decode or seek, which stay `NOT_TESTED`. `failureClass` distinguishes `ACCESS_DENIED`, `UNSUPPORTED_TRANSPORT`, `HTTP`, `PARSE`, `DECODE`, `TIMEOUT`, or `OTHER`. Keep raw stdout/stderr private to the adapter process and discard it after parsing; the validator rejects unknown fields and constrains string formats, but adapters must still avoid placing secrets in accepted fields. Record cold first-use and warm same-instance samples separately; a run must use one shared UTC timestamp for all samples so they aggregate together. The report tool preserves each sanitized sample, counts access blocks separately, and computes nearest-rank p50/p95 only from PASS samples in identical adapter/version, lane, phase, and comparison-environment groups; each metric reports its eligible count. No comparative measurements are claimed yet.

## YouTube.js adapter implementation notes

The separate Node project in `samples/black-box-comparison/youtubejs-node/` pins `youtubei.js@18.3.0` (MIT), instruments the documented `SessionOptions.fetch` boundary, selects audio through `getStreamingData`, and verifies a bounded direct-media range. By default, player-script evaluation is disabled; the explicit `--allow-player-js-eval` flag opts into the guide's documented `Platform.shim.eval` / `Function` example. That evaluates remote code and is not an OS sandbox, so use the flag only in a dedicated signed-out process with no credentials, secrets, or private inputs. The wrapper omits fetch credentials and strips credential headers; raw errors, stream URLs and request headers are never serialized. It records the Node V8 version and eval mode in `decoderVersion`.

This implementation does not prove that the media is fully playable or seekable. API requests use the documented custom fetch hook; the adapter manually follows and counts up to four redirects only for GET/HEAD requests to allowlisted HTTPS YouTube hosts. Media redirects are also manual, validated and counted. This keeps request counts observable and prevents redirects to unapproved hosts.
