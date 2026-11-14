# YouTube SABR/UMP: public primary-source contract review

**Checked:** 2026-11-14

## Decision

The public first-party documentation and standards reviewed here do not define a complete YouTube SABR/UMP request/response contract. They are not sufficient, by themselves, to establish an interoperable clean-room implementation of that exchange.

The documented web playback integration is the [IFrame Player API](https://developers.google.com/youtube/iframe_api_reference), which documents embedding and controlling the YouTube player through JavaScript commands, events, states, and parameters. YouTube’s [API catalog](https://developers.google.com/youtube/apis) lists that player API as a public playback product. These pages define player integration behavior, not a raw media-delivery protocol.

YouTube’s public [DASH](https://developers.google.com/youtube/data/live/guides/encoding-with-dash) and [HLS](https://developers.google.com/youtube/data/live/guides/hls-ingestion) guides define live-stream **ingestion from an encoder to YouTube**. They describe DASH uploads using HTTP `PUT`, and HLS uploads of playlists and segments using `PUT` or `POST`. Their direction and purpose are different from a viewer-side SABR/UMP exchange.

## What the standards define

| Source | Defined scope | What it does not establish for YouTube playback |
| --- | --- | --- |
| [YouTube DASH ingestion guide, which cites ISO/IEC 23009-1:2014](https://developers.google.com/youtube/data/live/guides/encoding-with-dash) | Standard referenced by the public YouTube live DASH ingestion guide for encoder-to-YouTube delivery. | A YouTube viewer-side SABR/UMP contract. |
| [RFC 8216 (HLS)](https://www.rfc-editor.org/rfc/rfc8216.html) | HLS playlist, media-file, and client/server behavior; the RFC describes HLS protocol version 7 and is informational. | A YouTube-specific viewer request/response contract. |
| [RFC 9110 (HTTP Semantics)](https://www.rfc-editor.org/rfc/rfc9110.html) | HTTP semantics shared across HTTP versions. | Application-level fields and state transitions that a service has not documented. |
| [W3C Media Source Extensions Recommendation](https://www.w3.org/TR/2016/REC-media-source-20161117/) | Browser APIs for JavaScript-generated media streams, appending segments, and buffering for playback. | How a particular service obtains those bytes or defines its network exchange. |

## Contract elements not found in reviewed official sources

The reviewed sources do not specify a SABR/UMP playback endpoint or its method, request fields and headers, authentication/client context, message framing or schemas, session state and sequencing, continuation rules, representation negotiation, response/error/redirect/retry behavior, or a compatibility/version policy. These elements remain unknown on this evidence; they must not be filled in by extrapolating from HTTP, DASH, HLS, or MSE.

## G6 release-gate assessment

**Do not pass G6 as an interoperable clean-room SABR/UMP implementation based solely on these public sources.** They provide no authoritative service-level contract against which interoperability can be established. Work limited to documented player integration or general standards-conformant DASH/HLS components is a different scope and does not prove SABR/UMP compatibility. This note does not change any release gate or plan status.

## Search limits and process disclosure

On 2026-11-14 I checked public Google/YouTube developer documentation and the standards linked above, including searches for “SABR” and “UMP.” These searches do not prove that no partner-only, unpublished, or otherwise non-public specification exists.

Repository instructions led me to read `AGENTS.md`, `docs/CLEAN_ROOM.md`, and `PROVENANCE.md` for policy context. `PROVENANCE.md` contains earlier implementation-derived summaries and source-access disclosures; I excluded those summaries from fact gathering and citations. I did not open any extractor implementation source. Since the summaries were visible in that repository document, this note does not claim strict researcher isolation. The technical findings above are supported only by the official URLs cited in this note.
