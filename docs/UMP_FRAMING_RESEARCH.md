# UMP framing: independent, offline prototype

Status: **merged prototype in main (PRs #84, #86, #90 and #91); offline only**. The `UmpFrameDecoder` is not wired into playback, makes no network requests and has not been verified against real YouTube UMP traffic. It must **not** be counted as G6 or InnerTubeX streaming parity.

## Legal and technical source

- Public-domain format facts: [gsuberland/UMP_Format](https://github.com/gsuberland/UMP_Format/blob/main/UMP_Format.md), consulted 2026-11-11. The document explicitly releases its information to the public domain.
- Implementation is independently written Kotlin using those protocol facts. No GPL/LGPL/AGPL extractor source was opened, copied, ported or linked.
- Only **type / length / payload framing** is in scope. No proprietary request contract, protobuf policy handling, token minting, auth, bypass or service transport is implemented.

## Current implementation

`cleanroom/commonMain/kotlin/com/excor/internal/sabr/UmpFraming.kt` contains:

1. An explicit 1-to-5-byte prefix-sized UMP integer decoder. The five-byte form discards the lower prefix bits and decodes four value bytes in little-endian order; this is **not** a protobuf varint.
2. A state machine for fragmented **byte reads** (including split integer fields and payloads).
3. A configurable per-part cap (default 8 MiB, maximum configurable 64 MB) and maximum emitted frame count per input chunk.
4. Rejection of invalid prefixes, truncated parts and oversize payload declarations before allocating their payloads.
5. Diagnostic rendering of part type and payload length only, never data.

The tests use **hand-constructed synthetic bytes** for every integer width, multiple frames, fragmented reads, truncation, overflow/caps, and redacted diagnostic rendering. They are not captures.

## Known missing engineering work

- The format description contains discrepancies in some illustrative integer decoding formulas. Multi-byte field placement requires independent verification against owner-authorized traffic before the parser can be treated as production interoperable.
- A real UMP response may restart with a new header when media is split **across separate HTTP responses**. The current parser handles only arbitrary *byte read boundaries within one continuous framing stream*; it does not stitch HTTP-response-level media continuations.
- No independently validated ABR request shape, media selection policy, retry state, redirection, seek, error mapping, live-stream handling or correct track assembly is implemented.
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
| Frame bounds | Offline decoder has a configurable per-part cap (8 MiB default, 64 MB maximum) and rejects malformed/truncated declarations; production payload maxima are unknown. | Keep prototype-only; do not raise limits without measurements. |
| Bootstrap/request shape | No independently specified viewer request contract or authorized fixture. | Unknown; no network request. |
| Representation selection | No independent rule for catalog, selection, or format transitions. | Unknown; do not infer from frame types. |
| Ordering, gaps, completion | Offline framing does not establish media ordering, missing-part recovery, end-of-stream, or cross-response assembly semantics. | Unknown; no media assembler. |
| Seek | The public embed player's seek controls do not define SABR segment/time mapping. | No transport seek support. |
| Cancellation/retry/redirect | Generic client safeguards do not establish SABR service behavior or credentials/session rules. | No transport-specific retry or redirect policy. |
| Memory/backpressure | Frame caps bound a local prototype, but no authorized service-size distribution or end-to-end streaming measurements exist. | Keep bounded streaming as a design requirement; do not buffer presumed whole responses. |
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
