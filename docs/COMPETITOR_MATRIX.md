# Competitive capability matrix

eXcor is compared on capabilities that are documented or can be observed from outside. Competitor source code is never read or used. Sources are recorded in `PROVENANCE.md`.

**Sources:**
- **InnerTubeX:** its README at github.com/MetrolistGroup/innertubex, read 2026-11-02 (v0.11.0 install line).
- **YouTube.js:** its README and the introduction at ytjs.dev/guide, read 2026-11-02.
- **NewPipeExtractor:** its README, read 2026-11-02.

| Capability | InnerTubeX (README) | eXcor (evidence) |
|---|---|---|
| Platforms | Android, JVM, iOS (Kotlin Multiplatform) | Android, JVM, iOS (Kotlin Multiplatform); all build in CI |
| Search, browse, playlists, channels | "browse, search, account, playlist, and player operations", channel discovery | Search with YouTube's own filters, Shorts and suggestions; playlists; mixes; channel tabs (Videos, Shorts, Live, Playlists, Posts) and About panel; related videos; comments; chapters. Live-verified 2026-11-02 |
| YouTube Music | No dedicated API described; an `IOS_MUSIC` probe and music uploads | Typed search with YouTube Music's filters and continuations, suggestions, artist pages, playlists, radio queues; live-verified 2026-11-02. Songs and albums are withheld from this environment |
| Audio streaming | Direct, HLS and SABR transports, format selection, bounded probing | Direct URLs with a redirect-aware range probe, HLS/DASH manifests, downloads. Live-verified 2026-11-02 for a video YouTube serves here: full download, ffmpeg decode and a mid-file seek. Most videos are bot-checked from this environment |
| Video streaming | Described only as part of SABR (next row) | `extractVideo`: video track by height, frame rate and codec, with matching audio; both probed. Live-verified 2026-11-03 at 480p and 1080p: full download, ffmpeg mux without re-encoding, full decode, mid-file seek |
| SABR/UMP | Audio and video, seekable segmented sources | Not implemented. Responses here that carry a `serverAbrStreamingUrl` also carry direct URLs, which play |
| Player cipher | Kotlin, via hosted QuackJS | Kotlin player-JS handling with the vendored yt-dlp/ejs solver (Unlicense) |
| Sessions and auth | Isolated authenticated sessions, generation-safe cancellation, host `TokenProvider` | Host session and PO-token interfaces; session-change invalidation is tested |
| Cross-provider music | Not described | Spotify, Apple Music and Deezer catalogs; matching; playlist conversion; YouTube to and from other providers |
| Performance claims | None quantified | Measured YouTube and music benchmarks (`docs/PERFORMANCE.md`). No comparative claim, since there's nothing to compare against |
| Installation | JitPack | `maven-publish` (`com.excor:eXcor:0.2.0`). The full local publish succeeds; `samples/jvm-quickstart` installs it through the README steps and runs live. CI publishes and builds the sample on every push. No remote repository yet |
| Status | Experimental 0.x | 0.2.0, draft SDK |

Where InnerTubeX documents a capability that eXcor lacks (SABR/UMP), eXcor can't currently verify one here. Where eXcor exceeds its README (YouTube Music, multi-provider catalogs, search filters, comments, chapters, About panel), each row above has a live test.

## YouTube.js and NewPipeExtractor

Their READMEs and introduction pages document platforms and installation, but not a per-feature API list. This table records only what they state; it does not infer the rest.

| | YouTube.js (documented) | NewPipeExtractor (documented) | eXcor |
|---|---|---|---|
| Runtime | "Node.js, Deno, modern browsers, and more"; JavaScript | Java library for Android and Gradle projects (desugaring below minSdk 33); a "core component of NewPipe" | Kotlin Multiplatform: JVM, Android (minSdk 26), iOS |
| Services | YouTube's InnerTube ("A JavaScript client for YouTube's internal API") | YouTube, SoundCloud, media.ccc.de, Bandcamp, PeerTube | YouTube, YouTube Music; Spotify, Apple Music and Deezer catalogs |
| Installation | npm, Yarn, Deno | JitPack; snapshots as `net.newpipe:extractor` | `maven-publish` to a local repository; CI-verified consumer |

eXcor's per-feature evidence lives in `FEATURE_MATRIX.md` and `EXCOR_ENGINEERING_AUDIT.md`. Comparing feature by feature with these two needs their full documentation or black-box runs; that hasn't been done.
