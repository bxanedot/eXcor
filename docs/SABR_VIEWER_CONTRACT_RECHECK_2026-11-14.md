# SABR/UMP viewer-side contract recheck: 2026-11-14

## Public contract not found

**A public, independently implementable SABR/UMP request/response contract and corresponding authorized wire-fixture set could not be established from first-party public sources as of this review.** This is a bounded finding about the sources checked, not proof that no private or partner documentation exists.

There is a narrower supported surface for viewer behavior: YouTube's public IFrame Player API documents embedding a video, playback controls, player state/events, and an official sample video ID. That is suitable for tests of the documented embedded-player API, but it does not expose or specify the underlying media request/response wire protocol ([IFrame Player API](https://developers.google.com/youtube/iframe_api_reference)).

The public YouTube API catalog describes the Data API and Live Streaming API as product APIs; the Data API reference documents video upload, listing, and metadata operations, not a viewer-side media-stream endpoint or SABR/UMP schema ([YouTube APIs](https://developers.google.com/youtube/apis), [Data API reference](https://developers.google.com/youtube/data/reference/rest), [video resource](https://developers.google.com/youtube/data/reference/rest/v3/videos)). Google's Cobalt page describes a platform for running HTML5 applications, including the YouTube TV app; it is not a SABR/UMP protocol specification ([Cobalt](https://developers.google.com/youtube/cobalt)). Targeted searches of the official YouTube developer domain for `SABR`, `UMP`, and `Unified Media Protocol` returned no results. This search result is evidence of what was located, not an exhaustive proof of absence.

The public [DASH live-streaming guide](https://developers.google.com/youtube/data/live/guides/encoding-with-dash) is for encoder-to-YouTube delivery; its endpoint records `PUT` requests from an encoder. The [HLS ingestion guide](https://developers.google.com/youtube/data/live/guides/hls-ingestion) likewise describes an encoder sending playlists and segments to YouTube. These ingestion guides do not specify how a viewer requests SABR/UMP media.

Generic standards do not fill the gap. HTTP specifications define generic request/response and message framing semantics, not the application-specific YouTube message schema ([RFC 9110](https://www.rfc-editor.org/rfc/rfc9110.html), [RFC 9113](https://www.rfc-editor.org/rfc/rfc9113.html), [RFC 9114](https://www.rfc-editor.org/rfc/rfc9114.html)). Likewise, Protocol Buffers' encoding documentation says field names and declared types are resolved from the message definition; a generic encoding guide cannot supply a missing UMP schema ([Protocol Buffers encoding](https://protobuf.dev/programming-guides/encoding/)).

## Separate policy-scope question

The official [YouTube API Services Developer Policies](https://developers.google.com/youtube/terms/developer-policies) prohibit API clients from using undocumented APIs without express permission or reverse-engineering undocumented YouTube API services (III.D.7). They also restrict other technology for accessing or retrieving API Data (III.I). These rules have a defined API Services scope. This review did not determine whether eXcor's direct InnerTube requests fall within it. Resolve that question with legal/compliance review before release; this report makes no legal conclusion.

## Fixture and retention limits

- **Supported now:** use the documented IFrame Player API and its published sample ID for a live integration test of the documented player controls and events. This is not a stored SABR/UMP exchange fixture.
- **Not established:** an exact offline wire fixture copied from a real YouTube playback exchange. The public sources checked do not provide such a fixture or a protocol specification against which one could be validated.
- **Permission limit:** YouTube's API Services Developer Policies say API clients must not download, import, back up, cache, or store copies of YouTube audiovisual content without YouTube's prior written approval; the API Services Terms also say no rights are granted to reproduce or distribute audiovisual content beyond the permitted API use ([Developer Policies, III.E](https://developers.google.com/youtube/terms/developer-policies), [Terms, §16.3](https://developers.google.com/youtube/terms/api-services-terms-of-service)). These terms are scoped to API Services/API clients; this note does not make a legal conclusion about every possible playback capture. A public video, an account owner's upload, or a successful player session alone does not establish permission to retain raw media-bearing request/response fixtures. Obtain written approval that explicitly covers capture, test use, storage, and retention before using such fixtures.
- Locally authored synthetic bytes can test a decoder's mechanics only after a schema is specified; until then, label them protocol-neutral and do not treat passing them as evidence of SABR/UMP compatibility.

## Exact gaps to close

No first-party public source located here specifies:

1. The viewer request endpoint, method, required headers/body, negotiation fields, and how client identity or authorization is represented.
2. The response envelope, part/frame identifiers, length encoding, schema files, ordering rules, and how data continues across responses.
3. The protocol state machine: initialization, track/format choice, media delivery, seek and continuation, live-window behavior, retry/refresh, redirects, end-of-stream, and error meanings.
4. Media and timing semantics: track relationships, codec/init data, timestamps, discontinuities, multiplexing, and any encryption or key-handling rules.
5. A versioned, redistributable, deterministic sample corpus that covers those cases and states what may be retained.

These are unknowns, not assumptions to encode from observed traffic. In particular, this review did not establish that every transport or payload uses any specific generic standard.

## Re-entry criteria

Reopen the viewer-side SABR/UMP implementation question when all of the following are available:

1. A versioned first-party specification, or written first-party documentation access, that defines the request and response schema and the lifecycle/state semantics above.
2. Written authorization covering any real protocol captures and media-bearing fixtures used for tests, including capture, storage, distribution, and retention terms; or an official first-party fixture corpus with an explicit reuse license.
3. At least one deterministic fixture set covering initialization, ordinary media continuation, seek or continuation, an error/refresh path, and termination, with secrets and per-session credentials removed without invalidating the documented test case.
4. A clean-room review confirming the proposed implementation and fixtures are based only on the newly approved documentation and authorized corpus.

Until those conditions are met, the independently verifiable viewer integration boundary is the documented IFrame Player API. No code, tests, release gates, or scores were changed for this research task.

## Sources checked

- [YouTube APIs and services](https://developers.google.com/youtube/apis)
- [YouTube IFrame Player API reference](https://developers.google.com/youtube/iframe_api_reference)
- [YouTube Data API REST reference](https://developers.google.com/youtube/data/reference/rest)
- [YouTube video resource](https://developers.google.com/youtube/data/reference/rest/v3/videos)
- [YouTube Cobalt](https://developers.google.com/youtube/cobalt)
- [YouTube Live DASH ingestion guide](https://developers.google.com/youtube/data/live/guides/encoding-with-dash)
- [YouTube Live HLS ingestion guide](https://developers.google.com/youtube/data/live/guides/hls-ingestion)
- [YouTube API Services Developer Policies](https://developers.google.com/youtube/terms/developer-policies)
- [YouTube API Services Terms of Service](https://developers.google.com/youtube/terms/api-services-terms-of-service)
- [RFC 9110: HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html), [RFC 9113: HTTP/2](https://www.rfc-editor.org/rfc/rfc9113.html), [RFC 9114: HTTP/3](https://www.rfc-editor.org/rfc/rfc9114.html)
- [Protocol Buffers: Encoding](https://protobuf.dev/programming-guides/encoding/)

All web sources were accessed 2026-11-14. Research was limited to official first-party documentation/APIs and public standards; no extractor implementation was opened. One reviewer read `AGENTS.md`, `docs/CLEAN_ROOM.md`, and `PROVENANCE.md` for repository policy, but excluded implementation-derived summaries and does not claim strict researcher isolation. A second review reports that it did not consult repository research or provenance summaries. This report does not claim strict researcher isolation.
