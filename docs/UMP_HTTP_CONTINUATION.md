# Experimental UMP response-continuation assembly

**Status:** offline-only clean-room prototype, proposed as a follow-up to #84. The framing and HTTP-response-boundary assembler are not wired into eXcor's network/playback engine, and no UMP stream, SABR request or seek has been verified.

## Why raw byte concatenation is incorrect

The independently published public-domain [UMP format description](https://github.com/gsuberland/UMP_Format/blob/main/UMP_Format.md) documents a type-20 header at the beginning of each HTTP response and permits a type-21 media part to span multiple HTTP responses. A continuation response starts with **another type-20 header**, then a part of the outstanding type with the remaining declared size. Those header bytes are not media.

`UmpFrameDecoder` (from #84) handles ordinary byte-read fragmentation within one stream. `UmpResponseAssembler` handles a distinct situation: **multiple complete, separately framed response bodies**.

## Defined offline behavior

- `acceptResponse(body)` consumes exactly one already-bounded response body, requires a complete type-20 header and returns only completed `UmpFrame` parts.
- An incomplete payload is held until subsequent response bodies. Each must start with a new type-20 header and declare the outstanding part's type and remaining byte count; mismatches fail closed. The returned completed part contains the reassembled payload **without** the intervening HTTP response headers.
- The default maximum part and response sizes are 8 MiB each; the limits can be configured up to 64 MB. The maximum number of completed parts emitted per response is bounded. A malformed response poisons that decoder instance; it cannot resume silently after lost synchronization.
- `finish()` refuses incomplete sequences.
- Synthetic tests cover one part spread across two or three responses, complete responses, empty payloads, missing or contradictory continuations, malformed framing, declared oversize parts, response length bounds and poisoned state.

This is intentionally a strict interpretation of a provisional public-domain description. It may reject valid real-world variations; those must be validated through permitted, owner-operated evidence **before** any production integration.

## Limitations that remain

- Currently accepts complete `ByteArray` response bodies, **not** a streaming callback or zero-copy channel. The caller must already impose its own size bound. It is not suitable for unbounded live-media buffering.
- It does not implement HTTP calls, redirect policy, signed URL handling, authorization, request protobuf encoding, compression, ABR selection, network retries, or seek offsets.
- Type 20 is treated as an opaque header. The implementation does not interpret protected fields, metadata or policies.
- It does not verify actual server responses, media decode or playback.
- No IP-bearing captures or secrets may be committed or logged. Never bypass consent, CAPTCHA, bot checks or DRM.

## Validation and score

The isolated decoder passed a local Kotlin compiler smoke test using hand-built response bytes. The full KMP suite must run in PR CI. After merge this is still **G6 blocked / SABR playback 0 of 20** until independently verified transport and real decoding/seeking exist.

See issue #79, `docs/UMP_FRAMING_RESEARCH.md` from #84 and `docs/PLAYBACK_EVIDENCE_MATRIX.md` from #83.
