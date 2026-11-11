# Public API feasibility for black-box comparison — 2026-11-11

## Scope

This note checks whether a reproducible comparator can drive eXcor, InnerTubeX, YouTube.js and NewPipeExtractor through published interfaces. It uses official project READMEs, changelogs and API documentation only; no competitor implementation source was inspected. It establishes adapter feasibility, not measured performance or parity.

## Published interfaces

| Project | Documented runtime and pin | Public extraction surface | Instrumentation and playback notes |
|---|---|---|---|
| eXcor | Kotlin Multiplatform, including JVM; the repository's [quickstart](../samples/jvm-quickstart/src/main/kotlin/PlaybackDiagnostics.kt) is the consumer path. Pin the exact published artifact/build. | `Excor.extract(videoId)` resolves audio and `Excor.extractVideo(...)` resolves matched video and audio; see the [feature matrix](FEATURE_MATRIX.md). | The standalone [`excor-jvm` adapter](../samples/black-box-comparison/excor-jvm/) now measures metadata, extraction and a bounded initial range read through the public host API. It keeps signed media URLs private. Full decode and backward/forward seek remain unmeasured by this adapter; HLS is experimental and not live-verified. |
| InnerTubeX | Kotlin Multiplatform with Android/JVM/iOS; the [README](https://github.com/MetrolistGroup/innertubex/blob/v0.11.6/README.md) uses JitPack. The README installation pins `v0.11.5`, while the [changelog](https://github.com/MetrolistGroup/innertubex/blob/v0.11.6/CHANGELOG.md) has a latest dated entry `0.11.6` (2026-11-07). The adapter pins `v0.11.6`; the public [desktop API baseline](https://github.com/MetrolistGroup/innertubex/blob/v0.11.6/api/desktop/innertubex.api) exposes `withStreamCapabilities`, `ExtractedStream` getters and the documented extraction types. The documented playback harness is source-built with Gradle, not a standalone published CLI. | README examples expose `InnerTube` operations and `InnerTubeExtractor.extract(videoId, hints, audioQuality)` for stream extraction. `ContentHints.withStreamCapabilities(allowHls=false, allowSabr=false, allowBoundedRange=true)` scopes the adapter to direct audio. | The separate JVM adapter supplies its own Ktor `HttpClient`, counts requests with Ktor's public [`HttpSend`](https://ktor.io/docs/client-http-send.html) plugin, and reports extraction plus a bounded initial range probe. It does not test playback, decode or seek. The README describes an opt-in harness that paces playback and performs backward/forward seeks across direct/HLS/SABR profiles; its measurements include that harness/player. GitHub repository metadata reports GPL-3.0 ([license metadata](https://api.github.com/repos/MetrolistGroup/innertubex)). Keep the adapter outside the eXcor dependency graph and do not distribute linked binaries before licensing review. |
| YouTube.js | [Getting Started](https://ytjs.dev/guide/getting-started) documents Node.js, Deno and browser setup; the Node fetch path requires Node 16.8 or newer. The install page says `youtubei.js@latest`; a benchmark must record and pin the actual resolved package version. | The [Innertube API](https://ytjs.dev/api/classes/Innertube) exposes `create`, `getInfo`, `getStreamingData`, and video-info `download` / `getStreamingInfo` methods. | The [Session API](https://ytjs.dev/api/classes/Session) exposes a custom `fetch` callback for request counting/timing. The [setup guide](https://ytjs.dev/guide/getting-started) says deciphering requires a caller-provided JavaScript interpreter; cipher-required cases must use a reviewed sandbox or be reported as unsupported. Browser streaming requires a proxy ([browser guide](https://ytjs.dev/guide/browser-usage)), so use the documented Node runtime for a same-host comparison. |
| NewPipeExtractor | The [README](https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/README.md) documents JitPack and asks consumers to substitute a release version. The [latest release](https://github.com/TeamNewPipe/NewPipeExtractor/releases/latest) showed `v0.27.1` dated 2026-11-10, while the consulted [StreamInfo Javadocs](https://teamnewpipe.github.io/NewPipeExtractor/javadoc/org/schabi/newpipe/extractor/stream/StreamInfo.html) were generated for `v0.26.5` and the [index](https://teamnewpipe.github.io/NewPipeExtractor/javadoc/index-all.html) for `v0.27.0`; verify the pinned artifact/API before recording a run. | Javadocs expose [`NewPipe.init(Downloader)`](https://teamnewpipe.github.io/NewPipeExtractor/javadoc/org/schabi/newpipe/extractor/NewPipe.html), [`StreamInfo.getInfo(url)`](https://teamnewpipe.github.io/NewPipeExtractor/javadoc/org/schabi/newpipe/extractor/stream/StreamInfo.html), and stream accessors such as `getAudioStreams()`. | A custom [`Downloader`](https://teamnewpipe.github.io/NewPipeExtractor/javadoc/org/schabi/newpipe/extractor/downloader/Downloader.html) is the public request boundary for counting and timing calls. Its streams can feed a common direct-media verifier. GitHub repository metadata reports GPL-3.0 ([license metadata](https://api.github.com/repos/TeamNewPipe/NewPipeExtractor)); keep any adapter out of the eXcor library dependency graph and obtain licensing review before distributing linked adapter binaries. |

Sources are linked in each row. This note checks public documentation and metadata only, not the respective implementation sources. Current versions must be re-pinned before each benchmark run. Checked 2026-11-11.

## Comparator design consequences

1. Run one adapter process per project on the same host, network, locale, content set and timeout budget. Pin each artifact/runtime and record its exact version and configuration. The eXcor and InnerTubeX JVM adapters are now separate standalone Gradle builds. Keep GPL implementations outside the eXcor build; do not distribute bundled GPL-linked adapters without licensing review.
2. Instrument requests at each documented host boundary: eXcor/InnerTubeX HTTP clients, YouTube.js `fetch`, and NewPipe's `Downloader`. Count library requests separately from media-player requests. Use a shared monotonic-clock contract for extraction start, metadata result, first media byte, and completion.
3. Compare a common **direct-audio lane** using the same eligible video IDs, local decoder, timeout, and range-read procedure. Normalize only fields supported by all adapters: status category, elapsed times, request count, stream class, MIME/codec, content bytes, decode outcome, range/seek outcome and sample count.
4. Keep a separate **native transport lane** for HLS/SABR/UMP. A transport-specific player/harness changes what is measured, so its results cannot be combined with direct-download timings. Report unavailable transports as `UNSUPPORTED`; service access refusal as `BLOCKED`; and parsing or decode errors as `FAIL`.
5. Emit sanitized JSON only. Never write media URLs, query strings, cookies, Authorization headers, PO tokens, visitor identifiers, or raw service responses. Preserve per-sample outcomes so p50/p95 do not silently discard access denials; publish the eligible sample count alongside each statistic.

The documented interfaces are sufficient to build separate adapters, but they do **not** establish that the artifacts are mutually comparable or that the current libraries all support the same seek operation. The eXcor adapter covers metadata, extraction and the initial media range; the InnerTubeX adapter covers extraction and the initial media range, with metadata included in extraction timing. Neither adapter decodes or tests seek. YouTube.js and NewPipe adapters, exact-head CI for the InnerTubeX adapter, and owner-run matched comparative measurements remain outstanding. Until then, G11 remains partial and no speed, reliability, or parity claim is supported.

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

Allowed outcomes are `PASS`, `BLOCKED`, `UNSUPPORTED`, `FAIL`, and `NOT_RUN`. For the current eXcor adapter, `PASS` means the requested metadata/extraction and initial bounded media read completed; for the InnerTubeX adapter, it means extraction and the initial bounded media read completed. Neither outcome implies full decode or seek, which stay `NOT_TESTED`. The InnerTubeX metadata timing is null because metadata is returned as part of its extraction call. `failureClass` distinguishes `ACCESS_DENIED`, `UNSUPPORTED_TRANSPORT`, `HTTP`, `PARSE`, `DECODE`, `TIMEOUT`, or `OTHER`. Keep raw stdout/stderr private to the adapter process and discard it after parsing; the validator rejects unknown fields and constrains string formats, but adapters must still avoid placing secrets in accepted fields. Record cold first-use and warm same-instance samples separately; a run must use one shared UTC timestamp for all samples so they aggregate together. The report tool preserves each sanitized sample, counts access blocks separately, and computes nearest-rank p50/p95 only from PASS samples in identical adapter/version, lane, phase, and comparison-environment groups; each metric reports its eligible count. The InnerTubeX adapter pins its standalone GPL-3.0 artifact and is not a dependency of eXcor. No matched competitor measurements are claimed yet.
