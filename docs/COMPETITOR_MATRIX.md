# Competitive capability matrix

eXcor is compared on capabilities that are documented or can be observed from outside. Competitor source code is never read or used. Sources are recorded in `PROVENANCE.md`.

**Sources:**
- **InnerTubeX:** public [README](https://github.com/MetrolistGroup/innertubex) and [CHANGELOG.md](https://github.com/MetrolistGroup/innertubex/blob/main/CHANGELOG.md), checked 2026-11-11. The changelog's latest entry is v0.11.6 (2026-11-07), while the README install snippet still pins v0.11.5. These are requirements-level references; implementation source was not inspected.
- **YouTube.js:** public README and guide at https://ytjs.dev, read 2026-11-02 and rechecked 2026-11-10 (the guide has more capabilities than its short README).
- **NewPipeExtractor:** its README, read 2026-11-02.

| Capability | InnerTubeX (public docs) | eXcor (evidence) |
|---|---|---|
| Platforms | Android, JVM, iOS (Kotlin Multiplatform) | Android, JVM, iOS (Kotlin Multiplatform); all build in CI |
| Search, browse, playlists, channels | "browse, search, account, playlist, and player operations", channel discovery | Search with YouTube's own filters, Shorts and suggestions; playlists; mixes; channel tabs (Videos, Shorts, Live, Playlists, Posts) and About panel; related videos; comments; chapters. Live-verified 2026-11-02 |
| YouTube Music | No dedicated music-browse facade described in the consulted README; an IOS_MUSIC probe and music uploads are described | Typed search, filters, continuations, suggestions, artist pages, playlists and radio queues; verified 2026-11-02. Songs and albums were subsequently observed on the owner's home network on 2026-11-03, but remain inaccessible in cloud tests |
| Audio streaming | Direct/HLS/SABR selection and bounded probing. v0.11.5 documents optional late token attachment to tokenless SABR audio and a manually selected TVHTML5_SAMSUNG audio path with up to a 35-second pre-roll wait | Direct URLs with a redirect-aware range probe, HLS/DASH manifests, downloads. Live-verified 2026-11-02 for a video YouTube serves here: full download, ffmpeg decode and a mid-file seek. Most videos are bot-checked from this environment |
| Video streaming | SABR video representation selection for host playback and seekable segmented sources (README) | `extractVideo`: video track by height, frame rate and codec, with matching audio; both probed. Live-verified 2026-11-03 at 480p and 1080p: full download, ffmpeg mux without re-encoding, full decode, mid-file seek |
| SABR/UMP | Audio and video, seekable segmented sources | Not implemented. Responses here that carry a `serverAbrStreamingUrl` also carry direct URLs, which play |
| Player cipher | Kotlin via hosted QuackJS; v0.11.6 fixes an iOS cipher thread-stack overflow | Kotlin player-JS handling with the vendored yt-dlp/ejs solver (Unlicense); G4 live ciphered playback remains unverified |
| Sessions and auth | Isolated authenticated sessions, generation-safe cancellation, host `TokenProvider`, optional scoped authenticated-TV credentials; v0.11.4 rejects authenticated redirects and uses finite timeouts | Host session and PO-token interfaces; session-change invalidation is tested |
| Cross-provider music | Not described | Spotify, Apple Music and Deezer catalogs; matching; playlist conversion; YouTube to and from other providers |
| Performance claims | Changelog reports scoped QuackJS improvements (including 11% faster compilation and 22% lower allocation in v0.11.4); no end-to-end extractor benchmark in the docs reviewed | Measured YouTube and music benchmarks (`docs/PERFORMANCE.md`); no direct apples-to-apples comparison yet |
| Installation | JitPack | `maven-publish` (`com.excor:eXcor:0.2.0`). The full local publish succeeds; `samples/jvm-quickstart` installs it through the README steps and runs live. CI publishes and builds the sample on every push. No remote repository yet |
| Security and diagnostics | Bounded remote bodies and protocol state, strict media URL validation, redacted model strings; v0.11.4 documents auth redirect rejection, finite timeouts and cookie DRM-format rejection | URL allowlist, bounded reads and dependency scan; full diagnostic-string audit remains open in draft PR #87 |
| Status | Experimental 0.x; README install sample v0.11.5, latest changelog v0.11.6 | 0.2.0, draft SDK |

Where InnerTubeX documents a capability that eXcor lacks (SABR/UMP), eXcor can't currently verify one here. Some eXcor features go beyond what the consulted InnerTubeX README explicitly describes; that is not proof InnerTubeX lacks them. For eXcor, see the linked feature-level live tests and their restrictions.

## YouTube.js and NewPipeExtractor

The short READMEs establish platforms and installation. YouTube.js also publishes an extended guide and API docs, while NewPipeExtractor exposes a larger service surface than the short summary below. This table deliberately makes no unsupported claim about their full feature coverage.

| | YouTube.js (documented) | NewPipeExtractor (documented) | eXcor |
|---|---|---|---|
| Runtime | "Node.js, Deno, modern browsers, and more"; JavaScript | Java library for Android and Gradle projects (desugaring below minSdk 33); a "core component of NewPipe" | Kotlin Multiplatform: JVM, Android (minSdk 26), iOS |
| Services | YouTube's InnerTube ("A JavaScript client for YouTube's internal API") | YouTube, SoundCloud, media.ccc.de, Bandcamp, PeerTube | YouTube, YouTube Music; Spotify, Apple Music and Deezer catalogs |
| Installation | npm, Yarn, Deno | JitPack; snapshots as `net.newpipe:extractor` | `maven-publish` to a local repository; CI-verified consumer |

eXcor's per-feature evidence lives in `FEATURE_MATRIX.md` and `EXCOR_ENGINEERING_AUDIT.md`. Full feature-level comparison still requires their public API documentation and reproducible black-box runs; that has not been done. The changelog also documents tokenless SABR audio starts with deferred token attachment, QuackJS updates and iOS cipher reliability fixes. These are documented capabilities, not a black-box verification of playback parity. The README install snippet still names v0.11.5 while the changelog's latest entry is v0.11.6. No latency, success-rate or stream-parity superiority claim is currently justified.
