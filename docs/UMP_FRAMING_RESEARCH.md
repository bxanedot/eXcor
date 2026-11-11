# UMP framing: independent, offline prototype

Status: **prototype on feature branch only**. The `UmpFrameDecoder` is not wired into playback, makes no network requests and has not been verified against real YouTube UMP traffic. It must **not** be counted as G6 or InnerTubeX streaming parity.

## Legal and technical source

- Public-domain format facts: [gsuberland/UMP_Format](https://github.com/gsuberland/UMP_Format/blob/main/UMP_Format.md), consulted 2026-11-11. The document explicitly releases its information to the public domain.
- Implementation is independently written Kotlin using those protocol facts. No GPL/LGPL/AGPL extractor source was opened, copied, ported or linked.
- Only **type / length / payload framing** is in scope. No proprietary request contract, protobuf policy handling, token minting, auth, bypass or service transport is implemented.

## Current implementation

`cleanroom-src/commonMain/kotlin/com/excor/internal/sabr/UmpFraming.kt` contains:

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

**G6 remains BLOCKED; SABR/UMP competitiveness remains 0/20.** See issue #79, `docs/release-gates.json`, and `docs/PLAYBACK_EVIDENCE_MATRIX.md` (proposed in #83).
