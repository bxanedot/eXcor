# Public API feasibility for black-box comparison — sources checked 2026-11-11; implementation updated 2026-11-12

## Scope

This note checks whether a reproducible comparator can drive eXcor, InnerTubeX, YouTube.js and NewPipeExtractor through published interfaces. It uses official project READMEs, changelogs, API documentation and package metadata; no competitor implementation code is used or copied. It establishes adapter feasibility, not measured performance or parity.

## Published interfaces

| Project | Documented runtime and pin | Public extraction surface | Instrumentation and playback notes |
|---|---|---|---|
| eXcor | Kotlin Multiplatform, including JVM; the repository's [quickstart](../modules/samples/jvm-quickstart/src/main/kotlin/PlaybackDiagnostics.kt) is the consumer path. Pin the exact published artifact/build. | `Excor.extract(videoId)` resolves audio and `Excor.extractVideo(...)` resolves matched video and audio; see the [feature matrix](FEATURE_MATRIX.md). | The standalone [`excor-jvm` adapter](../modules/samples/black-box-comparison/excor-jvm/) times extraction and a bounded initial media range; it calls videoInfo only after a missing stream to classify that failure. Its paired `--range-probes` mode adds two bounded HTTP byte-offset probes; `--full-decode` streams a capped complete response to FFmpeg stdin without exposing the signed URL. These measurements do not test player seeks; HLS is experimental and not live-verified. |
| InnerTubeX | Kotlin Multiplatform with Android/JVM/iOS; the [README](https://github.com/MetrolistGroup/innertubex/blob/v0.11.6/README.md) uses JitPack. The README installation pins `v0.11.5`, while the [changelog](https://github.com/MetrolistGroup/innertubex/blob/v0.11.6/CHANGELOG.md) has a latest dated entry `0.11.6` (2026-11-07). The adapter pins `v0.11.6`; the public [desktop API baseline](https://github.com/MetrolistGroup/innertubex/blob/v0.11.6/api/desktop/innertubex.api) exposes `withStreamCapabilities`, `ExtractedStream` getters and the documented extraction types. | README examples expose `InnerTube` operations and `InnerTubeExtractor.extract(videoId, hints, audioQuality)` for stream extraction. `ContentHints.withStreamCapabilities(allowHls=false, allowSabr=false, allowBoundedRange=true)` scopes the adapter to direct audio. | The separate JVM adapter supplies its own Ktor `HttpClient`, counts requests with Ktor's public [`HttpSend`](https://ktor.io/docs/client-http-send.html) plugin, and reports extraction plus a bounded initial range probe. The paired `--range-probes` mode checks two additional bounded HTTP byte offsets, while `--full-decode` streams a known-size body of at most 256 MiB to FFmpeg stdin. Neither checks player seeking. The README describes a separate opt-in harness that paces playback and performs backward/forward seeks across direct/HLS/SABR profiles; its measurements include that harness/player. GitHub repository metadata reports GPL-3.0 ([license metadata](https://api.github.com/repos/MetrolistGroup/innertubex)). Keep the adapter outside the eXcor dependency graph and do not distribute linked binaries before licensing review. |
| YouTube.js | [Getting Started](https://ytjs.dev/guide/getting-started) documents Node.js, Deno and browser setup; the Node fetch path requires Node 16.8 or newer. The adapter pins npm package [`youtubei.js@18.3.0`](https://www.npmjs.com/package/youtubei.js), MIT-licensed when checked 2026-11-11, and uses Node 22. | The [Innertube API](https://ytjs.dev/api/classes/Innertube) exposes `create` and `getBasicInfo`; the returned [VideoInfo](https://ytjs.dev/api/youtubei.js/namespaces/YT/classes/VideoInfo) exposes streaming data and audio `Format` objects with direct URL, bitrate, MIME and audio/video flags ([Format API](https://ytjs.dev/api/youtubei.js/namespaces/Misc/classes/Format)). | The documented [SessionOptions](https://ytjs.dev/api/type-aliases/SessionOptions) passed to `Innertube.create` accepts a custom `fetch`. The adapter selects only URL-backed audio, disables player retrieval and remote script evaluation, and bounds API request/response bodies and the separate media probe. Cipher-only formats are `UNSUPPORTED`. The custom fetch hook and media fetch validate/count redirects. Build CI makes no YouTube requests. |
| NewPipeExtractor | The official [README](https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/README.md) documents JitPack; the [v0.27.1 release](https://github.com/TeamNewPipe/NewPipeExtractor/releases/tag/v0.27.1) is dated 2026-11-10. The consulted [Javadocs](https://teamnewpipe.github.io/NewPipeExtractor/javadoc/) are generated for v0.27.1. The sample pins `com.github.teamnewpipe:NewPipeExtractor:v0.27.1`; upstream license metadata reports GPL-3.0-or-later. | Public APIs expose [`NewPipe.init(Downloader, Localization)`](https://teamnewpipe.github.io/NewPipeExtractor/javadoc/org/schabi/newpipe/extractor/NewPipe.html), [`StreamInfo.getInfo(url)` and `getAudioStreams()`](https://teamnewpipe.github.io/NewPipeExtractor/javadoc/org/schabi/newpipe/extractor/stream/StreamInfo.html), and [`Stream.getContent()` / `getDeliveryMethod()`](https://teamnewpipe.github.io/NewPipeExtractor/javadoc/org/schabi/newpipe/extractor/stream/Stream.html). | A separate Java 21 app uses the documented [`Downloader.execute(Request)`](https://teamnewpipe.github.io/NewPipeExtractor/javadoc/org/schabi/newpipe/extractor/downloader/Downloader.html) boundary, filters to URL-backed progressive HTTP audio, and performs a 64 KiB initial range probe. Public Javadocs describe access-challenge and restricted-content exception classes; these map to `BLOCKED`, while generic unavailability remains separate from parser faults. Its GPL dependency stays outside eXcor. Exact-head CI passed all six jobs in run `43102342730`; obtain licensing review before distributing linked adapter binaries. |

Sources are linked in each row. This note relies on public documentation and metadata; no competitor implementation code is used or copied. Current versions must be re-pinned before each benchmark run. Checked 2026-11-11.

## Comparator design consequences

1. Run one adapter process per project on the same host, network, locale, content set and timeout budget. Pin each artifact/runtime and record its exact version and configuration. This keeps GPL implementations outside the eXcor build while allowing a local black-box run; do not distribute bundled GPL-linked adapters without licensing review.
2. Instrument requests at each documented host boundary: eXcor/InnerTubeX HTTP clients, YouTube.js `fetch`, and NewPipe's `Downloader`. Count library requests separately from media-player requests. Use a shared monotonic-clock contract for extraction start, metadata result, first media byte, and completion.
3. Compare a common **direct-audio lane** using the same eligible video IDs, local decoder, timeout, and range-read procedure. The paired JVM adapters can opt into a shared-version FFmpeg full-body decode capped at 256 MiB. Normalize only fields supported by each selected lane: status category, elapsed times, request count, stream class, MIME/codec, content bytes, decode outcome, range/seek outcome and sample count.
4. Keep a separate **native transport lane** for HLS/SABR/UMP. A transport-specific player/harness changes what is measured, so its results cannot be combined with direct-download timings. Report unavailable transports as `UNSUPPORTED`; service access refusal as `BLOCKED`; and parsing or decode errors as `FAIL`.
5. Emit sanitized JSON only. Never write media URLs, query strings, cookies, Authorization headers, PO tokens, visitor identifiers, or raw service responses. Preserve per-sample outcomes so p50/p95 do not silently discard access denials; publish the eligible sample count alongside each statistic.

The documented interfaces are sufficient to build separate adapters, but they do not establish mutual comparability or player-seek parity. All four adapters cover extraction and an initial media range; only the eXcor/InnerTubeX JVM pair adds optional bounded full decode and HTTP byte-offset probes. `run_matched.py` supplies shared comparison metadata, preflights installed adapters and validates sanitized samples; CI makes no live requests. The YouTube.js runtime is V8, not a media decoder. NewPipe exact-head CI passed in run `43102342730`, YouTube.js in `43105597786`, and InnerTubeX v0.11.6 in `43109304794` at exact head `4e4931cf1011fa8ad37acd89319798365593c59d`. The 2026-11-13 owner-run paired direct-audio comparison passed full 3,507,483-byte decode and forward/back HTTP byte ranges in 3/3 cold samples for both eXcor 0.1.4 and InnerTubeX adapter 0.1.3. First-media-byte p50/p95 was 5,130/5,578 ms vs 1,174/1,223 ms; decode p50/p95 was 314/372 ms vs 372/375 ms. See the [normalized report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-adapter-fix-full.md) and [sanitized samples](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-adapter-fix-full.jsonl). The shared pass covers one video/network from a dirty source worktree. Two additional IDs gave InnerTubeX full decode and byte-range PASS in 3/3 while eXcor was access-blocked before media in 3/3: [9bZkp7q19f0](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-second-video.md), 4,064,225 bytes; and [CevxZvSJLk8](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-third-video.md), 4,247,110 bytes. These are access differences, not additional matched media successes. The paired extraction adapters do not drive players. A separate signed-out same-ID/network check now records eXcor FFplay Right/Left seeks and InnerTubeX v0.11.6 PCM-harness backward/forward stages passing at exact tag commit 64c7df4d88d27e6964157b3b341f7ec70f88ea6e. The harnesses differ, and additional paired playable IDs and networks remain open; see the [seek report](../modules/samples/black-box-comparison/results/owner-home-2026-11-13-paired-player-seek.md). All three comparisons are local diagnostics rather than release evidence. The 2026-11-12 InnerTubeX `FAIL/OTHER` runs used adapter 0.1.2, which lacked Ktor JSON body serialization; 0.1.3 adds it and an offline loopback regression. The second-ID 2026-11-12 recheck remains historical: eXcor was access-blocked on `jNQXAC9IVRw`, so it adds no second playable eXcor video. G11 remains partial.

## Adapter output contract

Each adapter should emit exactly one JSON object per sample with these normalized fields. The offline report validator in `modules/samples/black-box-comparison/report.py` enforces this schema and aggregates only comparable groups:

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
    "rangeProbeMethod": "NOT_TESTED",
    "rangeForwardProbe": "NOT_TESTED",
    "rangeBackwardProbe": "NOT_TESTED",
    "codec": null,
    "mimeType": null
  }
}
```

Allowed outcomes are `PASS`, `BLOCKED`, `UNSUPPORTED`, `FAIL`, and `NOT_RUN`. By default, `PASS` means extraction and an initial bounded media read completed. In paired JVM full-decode mode it also requires a complete bounded decode; neither mode implies player seeking. Optional HTTP byte-offset fields `rangeProbeMethod`, `rangeForwardProbe`, and `rangeBackwardProbe` default to `NOT_TESTED` when omitted from older schema-v1 samples; `forwardSeek` and `backwardSeek` remain `NOT_TESTED` because the adapters do not drive a player. `failureClass` distinguishes `ACCESS_DENIED`, `UNSUPPORTED_TRANSPORT`, `HTTP`, `PARSE`, `DECODE`, `TIMEOUT`, or `OTHER`. Keep raw stdout/stderr private to the adapter process and discard it after parsing; the validator rejects unknown fields and constrains string formats, but adapters must still avoid placing secrets in accepted fields. Record cold first-use and warm same-instance samples separately; a run must use one shared UTC timestamp for all samples so they aggregate together. The report tool preserves each sanitized sample, counts access blocks separately, and computes nearest-rank p50/p95 only from PASS samples in identical adapter/version, lane, phase, and comparison-environment groups; each metric reports its eligible count. The 2026-11-13 reports capture one fully paired video where both adapters passed, plus a second video where InnerTubeX passed and eXcor was access-blocked before media. That second outcome is not a player-seek or paired decoder result.

## YouTube.js adapter implementation notes

The separate Node project pins `youtubei.js@18.3.0` and uses the documented `Innertube.create({ fetch })` hook with `getBasicInfo` to inspect public streaming metadata. It selects only the highest-bitrate URL-backed audio format, disables player retrieval, and never evaluates downloaded player code. Cipher-only cases without direct URLs are `UNSUPPORTED`. API request bodies are capped at 1 MiB, API response bodies at 5 MiB, and the independent media range read at 64 KiB. It omits fetch credentials and raw errors, URLs, and headers from output. The Node 22 CI step installs the lockfile with lifecycle scripts disabled and checks syntax without contacting YouTube.

Cold `extractMs` includes session creation, `getBasicInfo`, and direct-audio selection; it is not metadata-only. The warm phase reuses the same instance and resets request counts before the measured attempt. API and media redirects are manually followed, bounded and counted. YouTube.js does not invoke a media decoder and keeps `decoderVersion` null; only the eXcor/InnerTubeX JVM pair supports optional full decode with a shared FFmpeg version. In the 2026-11-13 paired run both adapters passed full decode and HTTP offset probes in 3/3 cold samples for one video/network; first-media-byte p50 favored InnerTubeX (1,174 ms vs 5,130 ms), while decode p50 was 372 ms vs 314 ms. Compare those timings cautiously because the adapters extraction boundaries differ and the sample is small. Player seeks and broader owner-run coverage remain outstanding. HTTP byte-offset probes must not be described as playback seek results.
