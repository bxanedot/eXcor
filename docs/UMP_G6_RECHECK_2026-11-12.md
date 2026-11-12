# SABR/UMP G6 contract recheck — 2026-11-12

## Decision

**NO-GO remains in force. G6 stays blocked at 0/20.** This review found no new independently usable viewer-side SABR/UMP contract or authorized interoperability fixture in the public primary sources checked or the repository fixture inventory. The 2026-11-11 gate record still lists the missing complete contract, authorized playable stream, decode, and seek evidence as blockers ([release gates](release-gates.json)); the re-entry criteria are in [UMP framing research](UMP_FRAMING_RESEARCH.md).

This is a bounded finding, not a claim that no SABR/UMP specification exists anywhere. No live YouTube requests were made, and no competitor implementation code was inspected or used.

## What the public sources establish

Google's public [YouTube APIs and services catalog](https://developers.google.com/youtube/apis) describes the IFrame Player API as an embedded player integration, the Data API as app features such as video metadata and playlists, and the Live Streaming API as broadcast and stream management. The [IFrame Player API reference](https://developers.google.com/youtube/iframe_api_reference) documents player controls, state queries, and time-based `seekTo` behavior. These are documented player/API surfaces; the reviewed pages do not specify a caller's raw viewer-media bootstrap, SABR request schema, representation-selection policy, UMP response lifecycle, or mapping from a seek time to transport media. This is an inference from the documented scope, not proof that other material does not exist.

The [Live Streaming API guide](https://developers.google.com/youtube/data/live/guides/implementation/broadcasts-and-streams) covers creating broadcasts and ingestion streams, binding them, and transitioning broadcast state. It does not supply the viewer-side SABR/UMP contract needed by G6.

The standards checked cover lower-level or different protocols: [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110) defines general HTTP semantics, while [RFC 8216](https://www.rfc-editor.org/rfc/rfc8216) describes HTTP Live Streaming. Neither is a YouTube SABR/UMP service contract. The independently published [UMP format description](https://github.com/gsuberland/UMP_Format/blob/main/UMP_Format.md) is explicitly public-domain, but the existing eXcor notes use it only for limited framing facts and record discrepancies and the absence of owner-authorized interoperability validation ([UMP framing research](UMP_FRAMING_RESEARCH.md), [HTTP continuation prototype](UMP_HTTP_CONTINUATION.md)). It does not fill the request, selection, seek, authorization/session, error, or playback evidence gaps.

## Fixture inventory

The [fixture README](../fixtures/README.md) describes the historical `player-response-web.json` and `watch-page.html` captures as advertising SABR-only formats. The filename inventory contains no UMP media-response body or complete request/response exchange. Separately, the [playback evidence matrix](PLAYBACK_EVIDENCE_MATRIX.md) contains no completed SABR/UMP full-decode-and-seek result. These player/page captures are not UMP transport interoperability fixtures. The README also says the historical captures are third-party service content and are not used by clean-room tests; I did not inspect their raw contents.

The existing offline UMP prototypes have hand-constructed synthetic framing/continuation cases, not vectors derived from a complete independently usable service contract. Their documentation explicitly says no UMP stream, SABR request, or seek has been verified ([UMP framing research](UMP_FRAMING_RESEARCH.md), [HTTP continuation prototype](UMP_HTTP_CONTINUATION.md)). I do not reclassify the provenance of any existing capture; the inventory simply does not establish an authorized SABR/UMP playback fixture.

## Re-entry criteria status

| Existing criterion | Status on 2026-11-12 | Evidence gap |
|---|---|---|
| Complete independent contract for bootstrap, representation selection, response continuation/order/completion/errors, time-to-media seek, cancellation, redirects, and session behavior | **Not met** | No such contract was found in the checked official docs or standards; the existing notes scope UMP work to offline framing. |
| Contract-derived synthetic vectors for documented paths and failure cases | **Not met** | Existing hand-built vectors cover framing and response-boundary assembly only; they do not validate service negotiation, segment ordering, seek, or completion. |
| Adapter hard bounds, fail-closed redirect and diagnostic rules | **Not met for a transport adapter** | The offline prototype has local part/response caps, but there is no production transport with queued-byte and in-flight limits to assess. |
| Supported-target CI plus deterministic transport tests | **Not met for G6** | CI/build evidence for an offline framing prototype cannot validate a missing SABR adapter or transport behavior. This review did not run tests. |
| Owner-authorized audio and video playback with full decode and a mid-stream seek | **Not met** | No such SABR/UMP result is recorded in the [playback evidence matrix](PLAYBACK_EVIDENCE_MATRIX.md); no live requests were made for this review. |

## Search scope and confidence

I checked the official YouTube API catalog, IFrame Player API reference, Live Streaming API implementation guide, and searched Google/YouTube developer documentation for `SABR`, `UMP`, `application/vnd.yt-ump`, and viewer playback media-request protocol terms. I also checked the public HTTP and HLS RFCs and the repository's SABR/UMP notes, release-gate record, and fixture README/file inventory. The targeted official-documentation search did not surface a viewer-side SABR/UMP contract. That search is not exhaustive across all public or private material, so the conclusion is limited to these sources and this checkout.

**Result:** the prior no-go criteria remain unmet. G6 should remain blocked until the contract, contract-derived vectors, transport limits/CI, and authorized audio/video decode-and-seek evidence are supplied.

