# eXcor engineering audit

Baseline taken 2026-11-02 on `main` after bxanedot/eXcor#55; it is updated as subsystems change. **Verified** means a live check passes from this environment. **Tested** means offline tests only. **Blocked** means an external constraint prevents verification here.

**Project facts:**
- **Code:** Kotlin Multiplatform, package `com.excor`, coordinates `com.excor:eXcor:0.2.0`, built from `modules/cleanroom/`.
- **Targets:** JVM (Java 17 bytecode), Android (minSdk 26, Java 17 bytecode), iOS arm64, simulator arm64 and x64, plus the XCFramework `eXcor`.
- **Separate deliverables:** the `eXcorKit` Swift package under `modules/platforms/swift/`, and the preview apps `preview-android` (APK) and `preview-desktop` (Windows EXE), both unpublished and built on the library.

## Subsystems

| Subsystem | Files | State | Evidence |
|---|---|---|---|
| InnerTube request builder (WEB, WEB_REMIX) | `InnerTube.kt`, `Clients.kt` | Verified | Every live metadata test; `YouTubeMusicTest.requestsGoToYouTubeMusicWithItsClient` |
| Client profiles for playback (web, web_embedded, tv, visionOS) | `Clients.kt`, `ExcorEngine.kt` | Partially verified | VISIONOS plays live without the watch page or a player script. The others are bot-checked here or need the player script, whose watch page answers with a captcha. Per-client DEBUG logs explain each skip |
| Video metadata, with a watch-page fallback when the player withholds details | `VideoInfo.kt` | Verified | `videoMetadataAndCrossProviderMatch`, `videoInfoForABotCheckedVideo`, `EngineStateTest.videoInfoFallsBackToTheWatchPageWhenThePlayerWithholdsDetails` |
| InnerTube read retries (429, 5xx, transport errors; three attempts; Retry-After of up to 5 s; backoff with jitter) | `ExcorEngine.kt` | Tested | `EngineStateTest.innerTubeReadsRetryTransientFailuresAndStopAtTheLimit` |
| Typed request failures and diagnostics | `Api.kt`, `ExcorEngine.kt` | Tested | `EngineStateTest` (exhausted 503, 429 and transport failures map to `SERVER_ERROR`, `RATE_LIMITED` and `NETWORK`; the message carries the kind only); `docs/ERROR_HANDLING.md` |
| Search: filters, Shorts, continuations, suggestions | `YouTubeSearch.kt`, `YouTubeWatch.kt` | Verified | `searchWithContinuation`, `relatedVideosAndComments` |
| Playlists, mixes | `YouTubePlaylist.kt`, `YouTubeWatch.kt` | Verified | `playlistDetailsAndVideos`, the mix checks |
| Channels: five tabs, About panel, failed-tab reporting | `YouTubeChannel.kt` | Verified | `channelDetailsAndUploads`, `EngineStateTest.aChannelTabThatFailsToLoadIsNotReportedAsEmpty` |
| Related videos, comments, chapters, credited songs | `YouTubeWatch.kt` | Verified | `relatedVideosAndComments`, `videoMetadataAndCrossProviderMatch` |
| YouTube Music: search, suggestions, artists, playlists, radio | `YouTubeMusic.kt` | Verified | `youTubeMusicSearchArtistPlaylistAndRadio`, `YouTubeMusicTest` |
| YouTube Music: songs, albums, discographies | `YouTubeMusic.kt` | Tested; Blocked live | Not offered here: no Songs or Albums filter, no `MPREb_` IDs, empty Releases tabs |
| Player script and signature handling | `PlayerJs.kt`, `modules/cleanroom/ejs` | Tested | `PlayerJsTest`; no playable response to apply it to here |
| Stream selection and URL validation | `Selection.kt`, `UrlPolicy.kt`, `UrlParameters.kt` | Tested | `SelectionTest`, `UrlPolicyTest` |
| Direct audio streams | `ExcorEngine.kt`, `PlayerRequest.kt`, `Clients.kt` | Verified (for videos YouTube serves here) | `LivePlaybackTest.extractedAudioDecodesFullyAndSeeks`: full download, ffmpeg decode, duration match, mid-file seek. `EngineStateTest.extractionWorksWithoutTheWatchPageAndFollowsOnlyAllowedMediaRedirects`. Most videos remain bot-checked from here |
| Video streams (video track plus matching audio) | `ExcorEngine.kt` (`extractVideo`), `Selection.kt` (`rankVideoFormats`), `PlayerModels.kt` | Verified (for videos YouTube serves here) | `LivePlaybackTest.extractedVideoAndAudioDecodeAndMux` (480p and 1080p H.264, ffmpeg mux and full decode, seek); `VideoSelectionTest`; `EngineStateTest.videoExtractionPairsTheBestPlayableVideoWithAudioInTheSameContainer` |
| Format list for downloads | `ExcorEngine.kt` (`formats`), `Api.kt` (`ExcorFormatList`) | Verified (for videos YouTube serves here) | `LivePlaybackTest.formatListReportsRangeSupport` reads and validates each response's MIME type, bytes and range metadata; the current run found 27 range-readable formats; `EngineStateTest.formatListHasEveryWholeFileFormatAfterOneProbe` |
| SABR/UMP | — | Missing | A `serverAbrStreamingUrl` now appears in VISIONOS responses, alongside direct URLs that play; there is no documented SABR request specification to implement against independently |
| Captions, transcripts | — | Blocked | No `captions` in the bot-checked player response; `get_transcript` answers "Precondition check failed" |
| Hashtag pages | — | Blocked | Robot check |
| Music catalogs: Deezer, keyless Apple; matching; playlist conversion | `music/` | Verified | `LiveMusicTest` |
| Spotify, full Apple Music API | `music/` | Tested | Need credentials |
| Packaging and installability | `build.gradle.kts`, `modules/samples/jvm-quickstart` | Verified | Full `publishToMavenLocal` on Linux. The sample installs through the README steps and runs live. Fixed: the shared metadata didn't compile (`RegexOption.DOT_MATCHES_ALL` isn't in common Kotlin), so the library couldn't be published until 2026-11-02 |
| Session invalidation and secret redaction | `ExcorEngine.kt`, `Api.kt` | Tested | `EngineStateTest`; `ExcorSession.toString` hides cookies and visitor data |
| Dependency vulnerabilities (OSV-Scanner 2.0.0 in CI) | `.github/workflows/ci.yml` | Verified | CI now gates the full root Gradle graph, the Node comparison adapter lockfile and the shipped-runtime subset. The post-remediation Gradle scans in run 43130621290 found no issues in 238 all-configuration packages or 115 shipped-runtime packages; the broader release-security review remains incomplete |
| Benchmarks | `LiveVideoInfoTest.benchmark`, `LiveMusicTest.benchmark` | Verified | `docs/PERFORMANCE.md` (measured 2026-11-02) |

## Defects found and fixed in this pass

- **The library couldn't be published:** the common metadata compilation failed in `Lyrics.kt` and `Drm.kt`. CI now publishes and builds a consumer on every push.
- **A failed channel tab looked empty:** a tab that failed to load came back as an empty list (bxanedot/eXcor#54).
- **Too fast for Apple's keyless limit:** iTunes calls shared a 20-per-second budget against Apple's documented limit of about 20 per minute (bxanedot/eXcor#55).
- **Extraction returned nothing even when YouTube served a playable stream.** There were three causes:
  - it required the HTML watch page, which answers with a captcha (HTTP 429) here, before trying any client;
  - VISIONOS's player request carried youtube.com's Referer, which YouTube answers with 403;
  - the playback probe treated googlevideo's 302 to another node as a failure, because the client follows no redirects.

  Now extraction goes on without the page for clients that don't need it. VISIONOS sends no browser Origin or Referer. The probe follows up to 3 redirects, each only to an allowlisted target, using an absolute Location verbatim (rebuilding it broke the signed query).
- **`videoInfo` null for most videos:** it returned null for 8 of 10 videos from a bot-checked environment. It now falls back to the watch page.
- **Transient failures were final:** InnerTube reads now retry, within bounds.
- **Mix continuation lost:** a mix's continuation was dropped when its last item didn't decode (bxanedot/eXcor#54).
