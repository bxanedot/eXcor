# UMP framing: independent, offline prototype

Status: **offline prototype in the current worktree; not a transport**. The `UmpFrameDecoder` is not wired into playback, makes no network requests and has not been verified against real YouTube UMP traffic. It must **not** be counted as G6 or InnerTubeX streaming parity.

## Legal and technical source

- Public-domain format facts: [gsuberland/UMP_Format](https://github.com/gsuberland/UMP_Format/blob/main/UMP_Format.md), consulted 2026-11-11. The document explicitly releases its information to the public domain.
- Additional partial reference: the MIT-licensed [davidzeng0/innertube repository](https://github.com/davidzeng0/innertube/blob/b04d48d000a84befa5d7b018553b04fa4f98ebd8/LICENSE) at commit `b04d48d000a84befa5d7b018553b04fa4f98ebd8` has public [`/initplayback` notes](https://github.com/davidzeng0/innertube/blob/b04d48d000a84befa5d7b018553b04fa4f98ebd8/googlevideo/initplayback.md), [UMP part notes](https://github.com/davidzeng0/innertube/blob/b04d48d000a84befa5d7b018553b04fa4f98ebd8/googlevideo/ump.md), and generated protocol schemas. Reviewed 2026-11-13 as research only; no implementation code or schema files were copied into eXcor.
- The existing framing prototype is independently written Kotlin and uses only limited type/length facts. No third-party code or protocol schema has been copied into eXcor, and no GPL/LGPL/AGPL extractor implementation was opened, ported or linked.
- Only **type / length / payload framing** is in scope. No proprietary request contract, protobuf policy handling, token minting, auth, bypass or service transport is implemented.

## Current implementation

`modules/cleanroom/commonMain/kotlin/com/excor/internal/sabr/UmpFraming.kt` contains:

1. An explicit 1-to-5-byte prefix-sized UMP integer decoder. The five-byte form discards the lower prefix bits and decodes four value bytes in little-endian order; this is **not** a protobuf varint.
2. A state machine for fragmented **byte reads** (including split integer fields and payloads).
3. A configurable per-part cap (default 8 MiB, maximum configurable 64 MB), a 16 MiB default cumulative response-byte cap across all feeds, and a maximum emitted frame count per input chunk.
4. Rejection of invalid prefixes, truncated parts and oversize payload declarations before allocating their payloads.
5. Decoder state is invalidated after malformed input or frame-limit errors, so callers cannot resume from a corrupted parse state. A `finish()` check that finds an incomplete frame leaves the decoder resumable, matching the incremental-feed contract.
6. Diagnostic rendering of part type and payload length only, never data.

The tests use **hand-constructed synthetic bytes** for every integer width, multiple frames, fragmented reads, truncation, overflow/caps, and redacted diagnostic rendering. They are not captures.

## Known missing engineering work

- The format description contains discrepancies in some illustrative integer decoding formulas. Multi-byte field placement requires independent verification against owner-authorized traffic before the parser can be treated as production interoperable.
- A real UMP response may restart with a new header when media is split **across separate HTTP responses**. The current parser handles only arbitrary *byte read boundaries within one continuous framing stream*; it does not stitch HTTP-response-level media continuations.
- No independently validated ABR request shape, media selection policy, retry state, redirection, seek, error mapping, live-stream handling or correct track assembly is implemented.
- The additional public reference describes a `/config` bootstrap, encrypted `/initplayback` request envelope, some UMP part identifiers and a `SabrSeek` message with media-time and timescale fields. It does not establish the complete ABR policy, seek-to-segment mapping, cross-response continuation/order, failure/retry semantics, session rules or redirect behavior; several documented parts remain explicitly unknown.
- Real frames can be larger than the prototype's size bound; if so, adapt via bounded streaming callbacks rather than raising memory limits blindly.
- Publicly documented client policy and protobuf message names are not proof that the live service will accept a particular request or that content is authorized.

## Gate for the next phase

Before enabling any network call, obtain permission to test a video that YouTube serves without bypassing consent or bot checks. Use an owner-produced, **locally retained** sanitized description of the framing behavior, not raw signed URLs, cookies, tokens or IP-bearing captures in this repository.

Then validate integer widths and cross-response continuation semantics, implement independent framing fixtures, test bounded media emission and cancellation, and only finally add a separate opt-in transport path with full decode + seek verification. Document unsupported service responses rather than circumventing them.

**G6 remains BLOCKED; SABR/UMP competitiveness remains 0/20.** See [closed issue #79](https://github.com/bxanedot/eXcor/issues/79) for the no-go decision, `docs/release-gates.json`, and `docs/PLAYBACK_EVIDENCE_MATRIX.md`.

## Decision record — 2026-11-11

**Decision: NO-GO for a SABR/UMP network adapter in the current release track. G6 stays BLOCKED at 0/20.** Continue to support and improve the independently verified direct/HLS paths, while stating SABR as unsupported. This defers G6 until its contract and evidence gates are met; it does not claim that an independent implementation is impossible.

### Evidence reviewed

- Google's public [YouTube APIs catalog](https://developers.google.com/youtube/apis) lists the IFrame Player API as the public integration for embedded playback, and the Live Streaming API for managing broadcasts and streams.
- The [IFrame Player API reference](https://developers.google.com/youtube/iframe_api_reference) documents player controls, state/current-time queries, and seeking within the embedded player. It does not specify a caller's raw media requests, SABR bootstrap, representation identifiers, UMP response semantics, or segment assembly.
- Google's [Live Streaming API implementation guide](https://developers.google.com/youtube/data/live/guides/implementation/broadcasts-and-streams) documents broadcast creation, stream configuration, binding, and state transitions. Inference: because the guide covers publisher-side broadcast creation and ingestion settings, it does not define the viewer-side SABR/UMP service contract.
- The public-domain [UMP framing document](https://github.com/gsuberland/UMP_Format/blob/main/UMP_Format.md) is used only for the limited type/length/payload framing described above. Its framing facts do not define the service request contract; the existing discrepancies and lack of independent interoperability fixtures remain.

This is a bounded review of the sources above as checked on 2026-11-11. It is not a claim that no other private or public material exists. No InnerTubeX, YouTube.js, NewPipeExtractor, or other extractor implementation code was used or copied.

### Contract readiness

| Area | Evidence in eXcor today | Decision |
|---|---|---|
| Frame bounds | Offline decoder limits each part to 8 MiB by default and cumulative response input to 16 MiB by default across reads; it rejects malformed/truncated declarations. Production maxima remain unknown. | Keep prototype-only; limits are engineering bounds, not measured service requirements. |
| Bootstrap/request shape | No independently specified viewer request contract or authorized fixture. | Unknown; no network request. |
| Representation selection | No independent rule for catalog, selection, or format transitions. | Unknown; do not infer from frame types. |
| Ordering, gaps, completion | Offline framing does not establish media ordering, missing-part recovery, end-of-stream, or cross-response assembly semantics. | Unknown; no media assembler. |
| Seek | The public embed player's seek controls do not define SABR segment/time mapping. | No transport seek support. |
| Cancellation/retry/redirect | Generic client safeguards do not establish SABR service behavior or credentials/session rules. | No transport-specific retry or redirect policy. |
| Memory/backpressure | Per-part, per-feed frame-count and cumulative per-response byte caps bound the local prototype. No authorized service-size distribution or end-to-end streaming measurements exist. | Keep bounded streaming as a design requirement; do not buffer presumed whole responses. |
| iOS | Shared framing source may compile for KMP targets, but there is no iOS SABR playback or seek run. | No iOS parity claim. |

“Unknown” means the reviewed evidence does not support a claim; it is not a guess about undocumented server behavior.

### Options considered

| Option | Outcome | Reason |
|---|---|---|
| A. Keep direct/HLS as the supported transport scope | **Proceed as the interim scope** | It preserves the paths with existing qualified evidence and makes the SABR gap clear. It does not count as G6 completion. |
| B. Add an internal experimental SABR adapter | **No-go now** | Implementing bootstrap, selection, ordering, seeking, and completion would require guessing without an independent service contract and authorized interoperability fixtures. |
| C. Add a host-supplied transport interface | **Defer** | A generic host adapter could consume host-provided media, but it would not implement or verify SABR and would add public API and maintenance burden without a known consumer. Revisit if a real integration need appears. |

### Reopen criteria for option B

Start an implementation proof only after all of these are available:

1. An independently usable contract covers request bootstrap, representation selection, response/continuation boundaries, ordering, completion/errors, time-to-segment seek mapping, cancellation, and redirect/session behavior. It must come from public documentation or a clean-room description based on permitted, owner-authorized observations; no extractor implementation source.
2. Synthetic vectors are derived from that contract and cover each documented message path, malformed/truncated inputs, cross-response continuation, missing segments, seek boundaries, end-of-stream, cancellation, and bounds. Keep credentials, signed URLs, raw captures, and media bodies out of the repository.
3. The adapter design states hard limits for frame size, queued bytes, and in-flight work, plus fail-closed redirect and diagnostic rules. Never increase a cap solely to make a guessed fixture pass.
4. All supported-target CI passes, including common/JVM/Android/iOS builds and deterministic transport tests.
5. An owner-run, authorized playback matrix records at least one eligible audio and one eligible video case, each with full decode and a mid-stream seek. Record access denials separately; never bypass consent, bot checks, or DRM.

Until then, retain the current 0/20 G6 score and blocked release status. A framing parser, documentation review, or successful build alone does not satisfy these gates.

## Public protocol reference follow-up — 2026-11-13

The archived MIT-licensed `davidzeng0/innertube` repository at commit `b04d48d000a84befa5d7b018553b04fa4f98ebd8` adds a useful partial description of the viewer path. Its `/initplayback` notes describe fetching `/config`, encrypting the player request with a configured client key, and carrying that request inside a `OnesieRequestProto`. The schema exposes ABR state, streamer context and buffered ranges; the UMP notes describe media, end, redirect, error, seek and player-reload part identifiers. The `SabrSeek` schema carries media time, a timescale and a seek source. See the linked [license](https://github.com/davidzeng0/innertube/blob/b04d48d000a84befa5d7b018553b04fa4f98ebd8/LICENSE), [`/initplayback` notes](https://github.com/davidzeng0/innertube/blob/b04d48d000a84befa5d7b018553b04fa4f98ebd8/googlevideo/initplayback.md), [UMP notes](https://github.com/davidzeng0/innertube/blob/b04d48d000a84befa5d7b018553b04fa4f98ebd8/googlevideo/ump.md), [`OnesieRequestProto`](https://github.com/davidzeng0/innertube/blob/b04d48d000a84befa5d7b018553b04fa4f98ebd8/protos/video_streaming/onesie_request_proto.proto), and [`SabrSeek`](https://github.com/davidzeng0/innertube/blob/b04d48d000a84befa5d7b018553b04fa4f98ebd8/protos/video_streaming/sabr_seek.proto).

This is an unofficial and incomplete description. It leaves important message fields or purposes unknown and does not provide verified end-to-end ABR selection, request continuation behavior, cross-response media ordering, seek-to-segment mapping, error recovery, cancellation, session handling or redirect policy. eXcor has no authorized UMP exchange fixture, full audio/video decode or seek result. The source improves the documented research baseline but does not meet G6 re-entry criteria. No implementation was copied, and G6 remains **BLOCKED at 0/20**.

## Pinned-source audit — 2026-11-13

The MIT-licensed [YouTube.js and googlevideo source audit](UMP_G6_PRIMARY_SOURCE_AUDIT_2026-11-13.md) adds a more detailed bootstrap, ABR request, UMP part, seek, retry and cancellation model. The repositories' synthetic tests still do not establish YouTube service behavior, general seek-to-segment mapping, cross-response continuation, eXcor's hard limits, or authorized decode-and-seek playback. No code or schemas were copied; G6 remains blocked at 0/20.

## Generated package-reference recheck — 2026-11-13

The current JSR package page lists `@luanrt/googlevideo` 5.0.1 (MIT), but its SABR adapter reference page is version 5.0.1 while the UMP reference page identifies as 4.1.1. The pages document player integration and framing APIs, not a version-matched independent YouTube transport contract. The package example constructs `mockUmpData()` with synthetic headers and zero-filled media, so it is API usage rather than an authorized service exchange. See the [package overview](https://jsr.io/@luanrt/googlevideo), [SABR adapter API](https://jsr.io/@luanrt/googlevideo/doc/sabr-streaming-adapter), and [UMP API](https://jsr.io/@luanrt/googlevideo/doc/ump), plus the dated [G6 recheck](UMP_G6_RECHECK_2026-11-13.md).

The reviewed official YouTube API catalog lists public player/widget and data/reporting APIs; its IFrame reference describes controlling an embedded player. Inferring that these pages do not specify raw viewer-side SABR/UMP behavior is limited to their stated scope and is not proof that no other material exists. No code or schemas were copied, and G6 remains blocked at 0/20.
