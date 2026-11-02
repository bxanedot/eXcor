# Competitive capability matrix

eXcor is compared on capabilities that are documented or can be observed from outside. Competitor source code is never read or used. Sources are recorded in `PROVENANCE.md`.

**Sources:**
- **InnerTubeX:** its README at github.com/MetrolistGroup/innertubex, read 2026-11-02 (v0.11.0 install line).
- **YouTube.js and NewPipeExtractor:** not yet assessed.

| Capability | InnerTubeX (README) | eXcor (evidence) |
|---|---|---|
| Platforms | Android, JVM, iOS (Kotlin Multiplatform) | Android, JVM, iOS (Kotlin Multiplatform); all build in CI |
| Search, browse, playlists, channels | "browse, search, account, playlist, and player operations", channel discovery | Search with YouTube's own filters, Shorts and suggestions; playlists; mixes; channel tabs (Videos, Shorts, Live, Playlists, Posts) and About panel; related videos; comments; chapters. Live-verified 2026-11-02 |
| YouTube Music | No dedicated API described; an `IOS_MUSIC` probe and music uploads | Typed search with YouTube Music's filters and continuations, suggestions, artist pages, playlists, radio queues; live-verified 2026-11-02. Songs and albums are withheld from this environment |
| Audio streaming | Direct, HLS and SABR transports, format selection, bounded probing | Direct URLs with range validation, HLS/DASH manifests, downloads. Offline-tested; live use is blocked here by YouTube's bot check |
| SABR/UMP | Audio and video, seekable segmented sources | Not implemented; nothing can be observed here (no `serverAbrStreamingUrl`) |
| Player cipher | Kotlin, via hosted QuackJS | Kotlin player-JS handling with the vendored yt-dlp/ejs solver (Unlicense) |
| Sessions and auth | Isolated authenticated sessions, generation-safe cancellation, host `TokenProvider` | Host session and PO-token interfaces; session-change invalidation is tested |
| Cross-provider music | Not described | Spotify, Apple Music and Deezer catalogs; matching; playlist conversion; YouTube to and from other providers |
| Performance claims | None quantified | Music benchmark harness (`EXCOR_BENCH=1`); YouTube benchmark pending |
| Installation | JitPack | `maven-publish` (`com.excor:eXcor:0.2.0`). The full local publish succeeds; `samples/jvm-quickstart` installs it through the README steps and runs live. CI publishes and builds the sample on every push. No remote repository yet |
| Status | Experimental 0.x | 0.2.0, draft SDK |

Where InnerTubeX documents a capability that eXcor lacks (SABR/UMP), eXcor can't currently verify one here. Where eXcor exceeds its README (YouTube Music, multi-provider catalogs, search filters, comments, chapters, About panel), each row above has a live test.
