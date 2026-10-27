# Test fixtures

Responses captured from YouTube on 2026-10-26 for the offline tests. They
are YouTube's content, kept here only as test input: they are **not** covered by this
repository's Apache-2.0 license and are not part of any published artifact.

| File | Captured from |
|---|---|
| `iframe_api.js` | `GET https://www.youtube.com/iframe_api` |
| `player-dce23b30.js` | `GET https://www.youtube.com/s/player/dce23b30/player_ias.vflset/en_US/base.js` |
| `player-response-tv_samsung.json` | `POST /youtubei/v1/player`, client `TVHTML5` 5.20260707 (Samsung UA), `signatureTimestamp` 20746, video `dQw4w9WgXcQ` |
| `player-response-visionos.json` | same, client `VISIONOS` 1.02 |
| `player-response-web.json` | same, client `WEB` 2.20260708.00.00, no PO token (SABR-only formats) |
| `watch-page-blocked.html` | `GET https://www.youtube.com/watch?v=dQw4w9WgXcQ` from the build machine: a redirect to Google's bot check |
| `watch-page.html` | `GET https://www.youtube.com/watch?v=dQw4w9WgXcQ&hl=en&gl=US` from the owner's home network on 2026-10-27 (captured by Codex). **Redacted:** the requester's IP address, which appeared 64 times in stream URLs and `remoteHost`, is replaced with the documentation address `192.0.2.1` (RFC 5737); visitor IDs replaced as described below. Logged out, player `6b2fce74`, audio formats SABR-only |
| `challenges-dce23b30.json` | `n` and signature challenges from `player-response-tv_samsung.json` with the answers VyonEx computed; the decoded URLs answered `206`, undecoded ones `403` |
| `player-unplayable-*.json`, `browse-*.json`, `next-*.json`, `search-*.json` (14 files) | `CaptureFixtures` (requests and IDs listed there), logged out, run by Codex on the owner's wired home network on 2026-10-27 (PR #15). Redacted by the tool before writing; `heartbeatServerData` in `player-unplayable-upcoming.json` was replaced afterwards |

When adding captures, before committing:
- replace the requester's IP address (`ip=` parameters, also percent-encoded inside
  `signatureCipher`, and `remoteHost`) with a documentation address such as `192.0.2.1`;
- replace every copy of the visitor ID (`visitorData`, `VISITOR_DATA`, `visitor_data`) with
  `REDACTED-VISITOR-DATA`;
- replace `heartbeatServerData` (an encrypted per-request token) with `REDACTED-HEARTBEAT-DATA`;
- capture logged out, so no cookie values are present.

`FixtureHygieneTest` checks all four and fails without printing the value it found.

`CaptureFixtures` makes the logged-out captures for errors, collections, search and related videos,
redacting before it writes and refusing to write anything the checks above still flag:

```sh
VYONEX_CAPTURE=1 ./gradlew jvmTest --tests com.vyonex.CaptureFixtures --rerun -i
VYONEX_CAPTURE=1 VYONEX_CAPTURE_ONLY=player-multi-audio.json ./gradlew jvmTest --tests com.vyonex.CaptureFixtures --rerun -i   # just these files
```

Run it from a home network: datacenter addresses get different answers and bot checks. It stops at
a bot check rather than working around it. The visitor
IDs in the captures above were replaced on 2026-10-27; nothing else in them was changed.
