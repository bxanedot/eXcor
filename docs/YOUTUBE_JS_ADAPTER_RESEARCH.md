# YouTube.js adapter feasibility — 2026-11-11

## Finding

A standalone Node.js adapter is feasible for YouTube.js's documented signed-out direct-audio path. It can initialize `Innertube`, retrieve video info, select or request an audio format, resolve a deciphered URL where the supplied player support permits it, and measure a separately bounded initial media range request. This proves adapter feasibility only; it does not demonstrate successful playback or performance parity.

The current npm `latest` tag is `18.3.0`; the official GitHub release page identifies v18.3.0 as released on 2026-10-23. Pin `youtubei.js@18.3.0` and record the actual lockfile resolution and Node version in each run. The repository and npm package identify the license as MIT. Keep it in a separate process and preserve its license notice if redistributing the adapter; it does not belong in eXcor's dependency graph. ([npm package](https://www.npmjs.com/package/youtubei.js), [v18.3.0 release](https://github.com/LuanRT/YouTube.js/releases/tag/v18.3.0), [license](https://github.com/LuanRT/YouTube.js/blob/main/LICENSE))

## Public adapter surface

- [Getting Started](https://ytjs.dev/guide/getting-started) documents Node.js, Deno, and modern-browser support. Its Node fetch path requires Node.js 16.8 or later, plus `EventTarget` and `CustomEvent`. It documents `Innertube.create()` and a configurable `fetch` function. The adapter should use a current supported Node release, not target the documented minimum.
- The [Innertube API](https://ytjs.dev/api/classes/Innertube) exposes `create`, `getBasicInfo(video_id)`, `getInfo(target)`, and `getStreamingData(video_id, options)`. The [VideoInfo API](https://ytjs.dev/api/youtubei.js/namespaces/YT/classes/VideoInfo) exposes `basic_info`, `streaming_data`, and `getStreamingInfo()`. The [MediaInfo API](https://ytjs.dev/api/youtubei.js/namespaces/Mixins/classes/MediaInfo) documents `chooseFormat`, `download`, and `getStreamingInfo`; the [Format API](https://ytjs.dev/api/youtubei.js/namespaces/Misc/classes/Format) exposes audio/video flags, bitrate, MIME type, URL, and `decipher(player?)`.
- For a direct-audio lane, select an audio-only format from the documented format information, resolve it through the public decipher method only when supported, then use a separate host-owned HTTP fetch to request the first bounded byte range. Apply an allowlist, a shared deadline, a redirect limit with revalidation, and a streamed byte cap. Do not emit the signed URL or its query string. YouTube.js's configurable `fetch` is useful for counting and timing library API requests; keep those separate from the adapter's media probe.
- Treat `metadataMs` and `extractMs` carefully. `getInfo` returns a combined `VideoInfo` object, so a single call does not independently measure metadata and stream extraction. Either use distinct documented calls and report their separate elapsed time and requests, or report one combined extraction duration without claiming a metadata-only measurement. No live results should be included in the adapter build CI.

## Important limits

- The current [getting-started guide](https://ytjs.dev/guide/getting-started#providing-a-custom-javascript-interpreter) says deciphering needs a caller-provided JavaScript interpreter; YouTube.js does not ship one. The page's illustrative `Function`-constructor approach evaluates fetched player code and is unsuitable as an unreviewed benchmark default. Do not execute untrusted player code in the adapter without an explicitly reviewed isolation boundary. If no safe interpreter is available, classify cipher-required cases as `UNSUPPORTED` and retain the observed access/parser outcome for other cases.
- `retrieve_player` defaults to enabled; disabling it can reduce session setup time but also disables format deciphering, according to the [session configuration docs](https://ytjs.dev/guide/getting-started#configuration-options). Session initialization and player retrieval affect cold-start timing, so report cold and warm samples separately and do not fold an unmeasured warm cache into a cold result.
- The documented [`download`](https://ytjs.dev/api/classes/Innertube) method returns a readable stream, not a decoder or player result. The browser guide demonstrates DASH manifest generation and browser playback through dash.js, with a proxy required. Neither a returned stream URL nor a successful HTTP range probe establishes media decode, backward/forward seeking, or playback startup. Those measurements require the same external decoder/player procedure as other direct-audio adapters.
- The project homepage describes GoogleVideo modules for UMP/SABR, but the direct-audio adapter described here does not exercise that transport. Keep it in a separate native-transport lane and make no SABR parity claim from this adapter. ([project homepage](https://ytjs.dev/))

## Decision

Proceed with an isolated adapter for YouTube.js 18.3.0 for signed-out direct audio, subject to exact-head CI and a safe policy for player deciphering. Use the documented `fetch` hook for library request instrumentation and an independently bounded media probe. Keep session startup, extraction, media probe, and any external decode/seek measurements distinct. Do not include credentials or persistent visitor data, run live YouTube requests in CI, or distribute raw session/media URLs. Until an owner-run matched sample set includes decode and backward/forward seek, the adapter contributes to G11 feasibility only and does not upgrade competitive or release gates.

## Sources checked

- [YouTube.js npm package metadata and README](https://www.npmjs.com/package/youtubei.js) — latest version, runtime overview, MIT package license.
- [Official v18.3.0 release](https://github.com/LuanRT/YouTube.js/releases/tag/v18.3.0) — release tag and date.
- [Official repository license](https://github.com/LuanRT/YouTube.js/blob/main/LICENSE) — MIT terms.
- [Getting Started](https://ytjs.dev/guide/getting-started) — runtime prerequisites, initialization, custom fetch, player retrieval and interpreter requirement.
- [Innertube API](https://ytjs.dev/api/classes/Innertube), [VideoInfo API](https://ytjs.dev/api/youtubei.js/namespaces/YT/classes/VideoInfo), [MediaInfo API](https://ytjs.dev/api/youtubei.js/namespaces/Mixins/classes/MediaInfo), and [Format API](https://ytjs.dev/api/youtubei.js/namespaces/Misc/classes/Format) — public extraction and stream metadata methods.
- [Browser usage](https://ytjs.dev/guide/browser-usage) — DASH and proxy limits for browser streaming.
- [Project homepage](https://ytjs.dev/) — separate GoogleVideo UMP/SABR modules.

No YouTube.js implementation source was used or copied. No local tests or live YouTube requests were run.
