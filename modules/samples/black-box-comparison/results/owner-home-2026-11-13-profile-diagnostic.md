# Owner-run clean-room client-profile diagnostic

**Observed:** 2026-11-13 on the owner's signed-out Windows home network. The exact probe time was not captured; this summary was written at 19:39 UTC.

This was one `Excor.extract(videoId, maxKbps = 160)` attempt per video using eXcor 0.2.0 and its normal client fallback. A temporary host logger displayed only eXcor's already-sanitized client-profile/status messages and the selected profile/MIME. The diagnostic saved no URLs, headers, cookies, tokens, response bodies, or raw exception text.

| Video | Client-profile observations | Result |
|---|---|---|
| `aqz-KE-bpKQ` | `VISIONOS` returned `LOGIN_REQUIRED` with YouTube's “Sign in to confirm you're not a bot” reason. `TVHTML5` returned the same status. `WEB` was skipped because this host had no streaming-token provider. | No stream; extraction ended with `IllegalStateException`. |
| `dQw4w9WgXcQ` | `VISIONOS` returned 27 usable formats and 3 audio candidates. | eXcor selected `VISIONOS` audio/WebM. |

This identifies the observed aqz failure as a signed-out YouTube access challenge before stream selection. It does not establish the behavior on another network or prove that InnerTubeX and NewPipe use equivalent request profiles. The diagnostic did not run a full media decode, byte-range probes, or player seeks. No account cookies, streaming tokens, or challenge bypass were used.
