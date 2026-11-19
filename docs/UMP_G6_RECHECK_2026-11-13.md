# SABR/UMP G6 transport-contract recheck — 2026-11-13

**Decision: G6 remains blocked at 0/20.** This incremental recheck records the generated package reference and local fixture inventory; the prior primary-source audit remains the baseline for protocol behavior. At the time of this initial recheck, I made no source or release-gate changes. No live YouTube request was made, and no code or schema was copied.

## New or changed evidence

1. The current JSR package overview reports `@luanrt/googlevideo` 5.0.1 under MIT and provides generated API references. The SABR adapter page is version 5.0.1 and documents a player integration surface (`attach`, `setServerAbrFormats`, `setStreamingURL`, `setUstreamerConfig`, and callbacks); the UMP page currently identifies itself as version 4.1.1 and documents reader/writer utilities plus a complete-part callback. The pages are useful references for that library's API, but the version mismatch means they are not a version-matched transport contract. Several exported members still have no documentation. ([package overview and example](https://jsr.io/@luanrt/googlevideo), [SABR adapter API](https://jsr.io/@luanrt/googlevideo/doc/sabr-streaming-adapter), [UMP API](https://jsr.io/@luanrt/googlevideo/doc/ump))
2. The package overview's `mockUmpData()` example uses a synthetic media header and zero-filled media bytes; it demonstrates reader/writer API usage, not an observed service exchange or authorized playback fixture. The [clean-room fixture manifest](../modules/cleanroom/fixtures/README.md) independently lists no UMP exchange vector.

3. The source repository still shows commit `c0e73b283310c771cddc5af8993f0e6b55a7578e` (2026-11-06) as latest on `main`, the same revision examined in the 2026-11-13 primary-source audit. The API reference improves discoverability but adds no newer protocol evidence in this recheck. ([repository](https://github.com/LuanRT/googlevideo), [prior audit](UMP_G6_PRIMARY_SOURCE_AUDIT_2026-11-13.md))

4. The current clean-room fixture manifest declares its fixtures synthetic and lists player-response and metadata inputs, not SABR/UMP exchanges. The fixture file inventory contains no UMP media response or request/response exchange vector; the UMP tests remain synthetic framing/response-boundary coverage. The preserved legacy README identifies its player captures as third-party service content excluded from the clean-room suite. I checked names and README provenance only; I did not inspect raw legacy capture bodies. ([clean-room fixture manifest](../modules/cleanroom/fixtures/README.md), [legacy fixture README](../modules/legacy/fixtures/README.md))

5. A fresh read of the official YouTube API catalog and IFrame Player reference still exposes documented player/API surfaces rather than a raw viewer-side SABR/UMP transport contract. This is an inference from those pages' stated scope, not proof that no other material exists. ([YouTube APIs catalog](https://developers.google.com/youtube/apis), [IFrame Player API](https://developers.google.com/youtube/iframe_api_reference))

## Gate result

The new package reference does not supply an independently usable bootstrap, ABR and representation policy, cross-response continuation/order, general seek mapping, session/redirect/error behavior, or transport hard bounds. No authorized UMP exchange fixture or audio/video full-decode plus mid-stream seek result was found in eXcor. The existing re-entry criteria therefore remain unmet; the G6 record in [release-gates.json](release-gates.json) stays blocked.

## Offline parser hardening follow-up — 2026-11-13

`UmpFrameDecoder` now caps cumulative bytes across all transport-read chunks for one response (16 MiB default), in addition to its existing per-part and per-feed limits. A synthetic regression reproduced the prior gap by feeding two 8 MiB parts across separate reads; the second feed now fails closed and poisons the decoder. The four targeted UMP framing/assembly test classes passed, the full JVM suite passed with `--rerun-tasks`, and `:compileKotlinIosArm64` passed. These checks validate the offline framing primitive only. No SABR request, live UMP exchange, decode, seek, or authorization behavior was tested; G6 remains blocked at 0/20.
