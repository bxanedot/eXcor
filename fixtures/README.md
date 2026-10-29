# eXcor test fixtures

> The captured files documented below belong to the preserved earlier implementation. They are third-party service content, are not covered by the repository license, and are not used by the clean-room engine. The clean-room suite uses synthetic fixtures under `fixtures/cleanroom/`.

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
| `challenges-dce23b30.json` | `n` and signature challenges from `player-response-tv_samsung.json` with the answers Excor computed; the decoded URLs answered `206`, undecoded ones `403` |
| `player-unplayable-*.json`, `browse-*.json`, `next-*.json`, `search-web.json`, `search-music.json` (14 files; the later cloud captures are listed below) | `CaptureFixtures` (requests and IDs listed there), logged out, run by Codex on the owner's wired home network on 2026-10-27 (PR #15). Redacted by the tool before writing; `heartbeatServerData` in `player-unplayable-upcoming.json` was replaced afterwards |
| `next-watch.json`, `next-comments.json`, `next-comment-replies.json` | `CaptureFixtures`, logged out, from the cloud build machine on 2026-10-27 (the `next` endpoint answers there; only the player gets a bot check): the watch-next response of `dQw4w9WgXcQ`, the first page of its comments (top first), and the first thread's replies. **Comment authors anonymized** by the tool: each author's channel ID, handle and picture became `UCxxxxxxxxxxxxxxAUTHOR01`, `@author01` and `https://yt3.ggpht.com/author01`, numbered in order of appearance. The comment texts are left as written |
| `search-web-channels.json`, `search-web-shorts.json`, `suggest-web.json` | `CaptureFixtures`, logged out, from the cloud build machine on 2026-10-27: the search for "rick astley" with the Channels and Shorts filters (params from YouTube's own filter list), and the search box's suggestions for "rick ast" (`suggestqueries-clients6.youtube.com/complete/search?ds=yt&client=youtube…`, as YouTube's page asks) |
| `soundcloud-track.json`, `soundcloud-playlist.json`, `soundcloud-playlist-tracks.json` | `CaptureFixtures`, logged out, from the cloud build machine on 2026-10-27, asked as yt-dlp asks: `api-v2.soundcloud.com/resolve` for yt-dlp's test track (`ethmusic/lostin-powers-she-so-heavy`) and test set (`the-concept-band/sets/the-royal-concept-ep`), and `tracks?ids=` for the set's track that came without details. **Redacted:** `track_authorization` (a per-track playback token) became `REDACTED-TRACK-AUTHORIZATION`. The web client's ID is in the request URLs only and is never written; no stream URL is captured |
| `browse-channel-posts.json`, `browse-channel-posts-continuation.json` | `CaptureFixtures`, logged out, from the cloud build machine on 2026-10-28: Rick Astley's Posts tab (asked with the `params` the channel page gives for `…/posts`) and its second page. The posts are the channel's own; no comments are in them |
| `apple-song.json`, `apple-album.json`, `apple-artist.json`, `apple-search.json` | `CaptureFixtures`, from the cloud build machine on 2026-10-28: Apple's iTunes Search API lookups of "Never Gonna Give You Up" (song `1773293184`), its album with songs (`1773292758`), Rick Astley with five albums (`669771`), and a song search. Metadata only: every `previewUrl` (a 30-second audio preview) became `REDACTED-PREVIEW`. `apple-artist-page-head.html`: the `<head>` of Rick Astley's Apple Music page, for its share image; no token in it |
| `browse-channel.json`, `browse-channel-videos.json`, `browse-channel-videos-continuation.json`, `browse-channel-shorts.json`, `browse-channel-playlists.json` | `CaptureFixtures`, logged out, from the cloud build machine on 2026-10-27: Rick Astley's channel page (`UCuAXFkgsw1L7xaCfnd5JJOw`), its Videos tab and that tab's second page, its Shorts and its Playlists, each tab asked with the `params` the channel page gives |

When adding captures, before committing:
- replace the requester's IP address (`ip=` parameters, also percent-encoded inside
  `signatureCipher`, and `remoteHost`) with a documentation address such as `192.0.2.1`;
- replace every copy of the visitor ID (`visitorData`, `VISITOR_DATA`, `visitor_data`) with
  `REDACTED-VISITOR-DATA`;
- replace `heartbeatServerData` (an encrypted per-request token) with `REDACTED-HEARTBEAT-DATA`;
- capture logged out, so no cookie values are present;
- for comments, replace each author's channel ID, handle and picture (the tool does this for `next-comment*` files);
- for SoundCloud, replace `track_authorization` with `REDACTED-TRACK-AUTHORIZATION` and keep `client_id` out;
- for Apple's iTunes Search API, replace song previews (`previewUrl`) with `REDACTED-PREVIEW`;
- for Bandcamp pages, replace signed stream URLs (`https://t4.bcbits.com/stream/…`) with
  `https://t4.bcbits.com/stream/REDACTED`.

`FixtureHygieneTest` checks all of these and fails without printing the value it found.

`CaptureFixtures` makes the logged-out captures for errors, collections, search and related videos,
redacting before it writes and refusing to write anything the checks above still flag:

```sh
EXCOR_CAPTURE=1 ./gradlew jvmTest --tests com.excor.CaptureFixtures --rerun -i
EXCOR_CAPTURE=1 EXCOR_CAPTURE_ONLY=player-multi-audio.json ./gradlew jvmTest --tests com.excor.CaptureFixtures --rerun -i   # just these files
```

Run it from a home network: datacenter addresses get different answers and bot checks. It stops at
a bot check rather than working around it. The visitor
IDs in the captures above were replaced on 2026-10-27; nothing else in them was changed.
