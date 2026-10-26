# Test fixtures

Responses captured from YouTube on 2026-10-26 for the offline tests of both engines. They
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
| `watch-page-assembled.html` | **assembled**, see below |
| `challenges-dce23b30.json` | `n` and signature challenges from `player-response-tv_samsung.json` with the answers VyonEx computed; the decoded URLs answered `206`, undecoded ones `403` |

`watch-page-assembled.html`: the watch page could not be fetched from the cloud build
machine (see `watch-page-blocked.html` and QUESTIONS.md). It is the captured
`https://www.youtube.com/` page (real `ytcfg` and player URL) with
`<script>var ytInitialPlayerResponse = <player-response-tv_samsung.json>;…</script>`
inserted before `</body>`, the form a watch page uses. Replace it with a real capture
from a normal network when possible.
