# Provenance

The library code was written by Claude in a separate cloud environment with no access to the
owner's PC. Codex contributed the watch-page capture, live-test runs and results, and
documentation; its part ended on 2026-10-27 at the owner's decision. During local workspace discovery, Codex listed directory and file metadata under
the forbidden `Desktop\vyon-main` and `Documents\Codex\2026-*` locations and ran `git status`
in `Desktop\vyon-main`; it did not read source-file contents from those locations. This records
Codex's actions and contributions, not a legal or release determination.

## Sources read or ported

| Source | Version / date | License (checked) | Used for |
|---|---|---|---|
| yt-dlp, `pip download yt-dlp==2026.10.21 --no-binary :all:` from PyPI | 2026.10.21, git `8cdf4fea48ecf4064387375301d7f294ebdb17c1` (`yt_dlp/version.py`) | Unlicense (`LICENSE` in that sdist) | see the yt-dlp table below |
| yt-dlp/ejs, PyPI `yt-dlp-ejs` 0.8.0 (sdist for the TypeScript sources, wheel for the built scripts) | 0.8.0 | Unlicense (`LICENSE` in both) | `ejs/core.min.js` and `ejs/lib.min.js` copied unmodified; `src/yt/solver/*.ts` read to learn the `jsc()` input/output and the `_result.n` / `_result.sig` contract of a prepared player, which `PlayerJs.kt` relies on |
| meriyah (bundled inside ejs `lib.min.js`) | 6.1.4 | ISC (header of `lib.min.js`) | JavaScript parser the solver runs in QuickJS; not read or modified |
| astring (bundled inside ejs `lib.min.js`) | 1.9.0 | MIT (header of `lib.min.js`) | code generator the solver runs in QuickJS; not read or modified |
| QuickJS-KT sources jar, Maven Central `io.github.dokar3:quickjs-kt:1.0.15:sources` | 1.0.15 | Apache-2.0 (POM) | API signatures of `QuickJs.create/evaluate/close` |
| Ktor `ktor-client-okhttp-jvm` 3.6.0 jar from Maven Central, `OkHttpEngine` bytecode read with `javap` | 3.6.0 | Apache-2.0 (POM) | learned that closing the engine shuts down the dispatcher and connection pool of the clients it built, so the JVM `close()` leaves the Ktor client open (`QUESTIONS.md` item 29) |
| YouTube public endpoints, observed live from the build machine | 2026-10-26 | YouTube's content (fixtures only) | `iframe_api`, player script `dce23b30`, `youtubei/v1/player` with the three identities, `www.youtube.com/` page; the watch page answered with a bot-check redirect. Captures are in `fixtures/` |
| YouTube watch page, captured by Codex on the owner's home network | 2026-10-27 | YouTube's content (fixture only) | `fixtures/watch-page.html`, requester IP replaced with `192.0.2.1`; used by the watch-page tests |
| YouTube logged-out responses (unplayable players, browse, next, search), captured by Codex with `CaptureFixtures` on the owner's home network | 2026-10-27 | YouTube's content (fixtures only) | 14 files in `fixtures/` (listed in `fixtures/README.md`), visitor IDs and heartbeat token redacted; input for the playlist, album, artist, search and related-video work |
| [YouTube Terms of Service](https://www.youtube.com/t/terms) | accessed 2026-10-27 | Terms of Service, not a content license | assessed public redistribution risk for captured fixtures; see `QUESTIONS.md` item 4 |
| AOSP `frameworks/av` `media/libstagefright/webm/WebmWriter.cpp` at `android-8.0.0_r1`, `android-9.0.0_r1`, `android-10.0.0_r1` (android.googlesource.com) | read 2026-10-27 | Apache-2.0 | which audio codecs Android's WebM muxer accepts per version (Vorbis only before Android 10, Opus from Android 10); `VyonExMerge.supports`. Nothing copied |
| Apache License 2.0 text, https://www.apache.org/licenses/LICENSE-2.0.txt | fetched 2026-10-26 | — | `LICENSE` |
| Synthetic clips made on the build machine with FFmpeg 6.1.1 (Ubuntu package) from its own `testsrc` pattern and `sine` tone; no outside media | 2026-10-27 | generated output; no FFmpeg code is in the repository | `src/androidDeviceTest/resources/clips/`, used by `MergeCheck`. 64x64, 30 fps, 2 s: `video-avc.mp4` (libx264, `-g 30`) and `audio-aac.m4a` (aac 32k) written with `-movflags frag_keyframe+empty_moov+default_base_moof`, like YouTube's fragmented MP4; `video-vp9.webm` (libvpx-vp9, `-g 30 -b:v 50k`) and `audio-opus.webm` (libopus 24k, 48 kHz) |

Vendored files: `ejs/core.min.js` sha256 `18da6ce0758b416e7ae645084f4f8801f9f9d59d6c477c05eaa0ff94ebd8cc00`,
`ejs/lib.min.js` sha256 `c55987fe697e5b9ee18830163f7af85327e9bb5c3e674b969d38c8d205eaa577`. Their
SHA3-512 values equal the allowlist in yt-dlp 2026.10.21 `yt_dlp/extractor/youtube/jsc/_builtin/vendor/_info.py`.

### What came from yt-dlp 2026.10.21

| yt-dlp location | VyonEx location | What |
|---|---|---|
| `extractor/youtube/_video.py` `_DEFAULT_CLIENTS`, `_DEFAULT_AUTHED_CLIENTS`, `_get_requested_clients` | `Youtube.kt` `IDENTITIES`, `Engine.extract` | identity order; skipping cookie-less clients when logged in |
| `extractor/youtube/_base.py` `INNERTUBE_CLIENTS` (`tv_samsung`, `visionos`, `web`) | `Youtube.kt` `IDENTITIES` | client names, numeric ids, versions, user agents, device fields; which ones need the JS player, cookies and a GVS PO token |
| `_base.py` `generate_api_headers`, `_call_api`, `_extract_context` | `Engine.playerResponse`, `playerRequestBody` | player endpoint, `prettyPrint=false`, `X-YouTube-Client-Name/Version`, `Origin`, `X-Goog-Visitor-Id`, `X-Origin`, `timeZone`/`utcOffsetMinutes` |
| `_base.py` `_make_sid_authorization`, `_get_sid_authorization_header`, `_get_sid_cookies`, `_has_auth_cookies` | `Youtube.kt` `sidAuthorization`, `isLoggedIn` | ported: SAPISIDHASH / SAPISID1PHASH / SAPISID3PHASH, logged-in test |
| `_video.py` `_generate_player_context`, `_get_checkok_params`, `_extract_player_response` | `playerRequestBody` | `playbackContext.contentPlaybackContext` (`html5Preference`, `signatureTimestamp`), `contentCheckOk`, `racyCheckOk`, `serviceIntegrityDimensions.poToken` |
| `_video.py` `_extract_signature_timestamp` | `PlayerJs.kt` `signatureTimestamp` | ported regex for `signatureTimestamp`/`sts` |
| `_video.py` `_download_player_url`, `_PLAYER_JS_VARIANT_MAP['main']`, `_construct_player_url` | `Players.current`, `Players.get`, `playerIdOf` | player version from `iframe_api`; always fetching the `player_ias` variant |
| `_video.py` `_is_agegated` (`AGE_GATE_REASONS`), `_real_extract` (`errorScreen.playerErrorMessageRenderer` `reason`/`subreason`, the "not made this video available in your country" check, "sign in" reasons) | `Youtube.kt` `unplayableOf` | how a refusal is sorted; the reason strings to match |
| `_video.py` `_extract_formats_and_subtitles` | `audioFormatsOf`, `playableUrl`, `Engine.playableFormats` | `signatureCipher` fields (`url`, `s`, `sp` defaulting to `signature`), `n` transform, `pot` parameter, skipping live/OTF/DRM formats and URL-less (SABR) formats |
| `_base.py` `_YT_INITIAL_PLAYER_RESPONSE_RE` | `parseWatchPage` | `ytInitialPlayerResponse =` marker |
| `_video.py` `YoutubeIE._VALID_URL`; `_base.py` `_PLAYLIST_ID_RE`; `_tab.py` `YoutubeTabIE._real_extract` (`VL`/`MP` browse IDs) | `Links.kt` `parseRef` | link forms (`watch?v=`, `v/`, `embed/`, `e/`, `shorts/`, `live/`, `youtu.be/`, not `videoseries`/`live_stream`), playlist ID pattern, `VL` prefix; YouTube's own hosts only |
| `_video.py` `_real_extract` (`start`/`t` query and fragment) and `utils.parse_duration` | `Links.kt` `startSeconds` | the subset used: seconds, `[h:]m:s`, `1h2m3s` |
| `_video.py` `_list_formats` (`live_status`) and the zero-duration rule in `_real_extract` | `metadataOf` | `VyonExLiveStatus` from `isPostLiveDvr`, `isLive`/`isLiveNow`, `isUpcoming`, `isLiveContent` |
| `_video.py` `_real_extract`, the "Auto-generated by YouTube." description pattern | `Youtube.kt` `musicInfoOf` | track, artists, album, release year and date of YouTube Music uploads |
| `_tab.py` `_extract_lockup_view_model` (video `contentId`, title, channel from `metadataRows` with a `browseEndpoint`, the collaborator fallback through the avatar stack's dialog, duration badge), `_extract_metadata_from_tabs` / continuation handling | `Lists.kt` `lockupItemOf`, `playlistOf`, `continuationPageOf`; `Engine.playlist`, `playlistMore` | which fields make a list item and where the next page's token is |
| `_tab.py` `_music_reponsive_list_entry` (track `playlistItemData.videoId`, first column's title), `YoutubeTabIE._real_extract` (album `MP…` resolved to its playlist through `microformat…urlCanonical`; music `/browse/UC…` as a channel); `_base.py` `INNERTUBE_CLIENTS` `web_music` | `Lists.kt` `MUSIC_WEB`, `albumOf`, `artistOf`; `Links.kt` channel links | how YouTube Music album and artist pages are read |
| `_tab.py` `_extract_inline_playlist` (mix paging through `next` with the last video's `watchEndpoint`: `videoId`, `index`, `params` defaulting to `OAE%3D`; keeping only videos after the last one), `_playlist_entries` / `_extract_video` (`playlistPanelVideoRenderer`), continuation items under `onResponseReceivedActions`/`onResponseReceivedEndpoints`; `_base.py` `_extract_continuation` (`continuationItemRenderer` and `continuationItemViewModel`) | `Lists.kt` `mixOf`, `mixMoreOf`, `relatedOf`, `continuationPageOf`, `listPageOf`; `Engine.mix`, `mixMore`, `related`, `relatedMore` | how mixes and related videos are read and paged |
| `_tab.py` `YoutubeSearchIE` / `_search_results` (the `search` endpoint, `query`, result sections, continuation under `onResponseReceivedCommands`), `_extract_video` (`videoRenderer`: `ownerText`/`shortBylineText`, `lengthText`), `_extract_lockup_view_model` (playlist lockups, `collectionThumbnailViewModel`); YouTube Music search through `web_music` | `Lists.kt` `webSearchOf`, `webSearchMoreOf`, `musicSearchOf`; `Engine.search`, `searchMore`, `searchMusic` | how search results are read and classified |
| `_base.py` `INNERTUBE_CLIENTS` (`web_music`: `WEB_REMIX`, 67, `1.20260707.12.00`), `_call_api`; `_tab.py` (`browse`, `next`, continuations); `_search.py` (`search`) | `CaptureFixtures` (test tool) | request shapes for the captures; test IDs (playlist `PLsyOSbh5bs16vubvKePAQ1x3PhKavfBIl`, album `MPREb_gTAcphH99wE` / `OLAK5uy_l1m0thk3g31NmIIz_vMIbWtyv7eZixlH0`) from yt-dlp's tests |
| `_video.py` `_extract_formats_and_subtitles` (`CHUNK_SIZE = 10 << 20`, `http_chunk_size`) | `Engine.download` | ranged requests of 10 MiB |
| `extractor/youtube/jsc/_builtin/ejs.py` `_construct_stdin` | `PlayerJs.kt` `preparePlayer` | how the scripts are run: lib, `Object.assign(globalThis, lib)`, core, `jsc({type:'player', …, output_preprocessed:true})` |

VyonExKit, a Swift port of this code, was removed on 2026-10-27 at the owner's request (iOS apps
use the Kotlin XCFramework); it is in the git history up to that date.
YouTubeKit (MIT) was **not** read or used, so `NOTICE` carries no YouTubeKit entry.

Written from the brief without an outside source: format selection, URL allowlist, remembered
streams, exclusions, lifecycle, logging/redaction, the disk cache, watch-page JSON scanning,
the Gradle build and the CI workflow.

Written from the captured player responses in `fixtures/` (what YouTube returned, no outside
code), added at the owner's request on 2026-10-27: video format selection (`VideoFormat`,
`selectVideo`; combined formats recognised by the two codecs in their `mimeType`) and the
metadata mapping (`metadataOf`, from `videoDetails`, `microformat.playerMicroformatRenderer` and
`captions.playerCaptionsTracklistRenderer`). Test IDs in `LinksTest` are public IDs from yt-dlp's test data. The rule that a caption URL whose `exp` is `xpe` or `xpv`
needs a PO token comes from yt-dlp 2026.10.21 `_video.py` (`_real_extract`, `requires_pot`).

## Dependencies

All from Maven Central; licenses as declared in each POM.

| Dependency | Version | License | Scope |
|---|---|---|---|
| Kotlin stdlib, kotlin-test, kotlin-test-testng | 2.4.21 | Apache-2.0 | main / test |
| kotlinx-coroutines-core (+ -slf4j, -test) | 1.11.0 | Apache-2.0 | main / test |
| kotlinx-serialization-json | 1.12.0 | Apache-2.0 | main |
| kotlinx-io-core, -bytestring (via Ktor; read fixtures in tests) | 0.9.1 | Apache-2.0 | main / test |
| Ktor client core, okhttp, darwin (+ http, io, utils, events, network, sse, websockets, serialization) | 3.6.0 | Apache-2.0 | main |
| OkHttp | 5.5.0 | Apache-2.0 | main (JVM) |
| Okio | 3.18.2 | Apache-2.0 | main (JVM) |
| QuickJS-KT (bundles QuickJS natives, MIT) | 1.0.15 | Apache-2.0 | main |
| org.jetbrains:annotations | 13.0, 23.0.0 | Apache-2.0 | main |
| slf4j-api (via Ktor) | 2.0.19 | MIT | main (JVM) |
| ktor-client-mock (serves fixtures to the engine in tests) | 3.6.0 | Apache-2.0 | test |
| TestNG | 7.0.0 | Apache-2.0 | test (JVM) |
| JCommander (via TestNG) | 1.72 | Apache-2.0 | test (JVM) |
| Gradle wrapper | 9.8.2 | Apache-2.0 | build |
| Android Gradle Plugin (`com.android.kotlin.multiplatform.library`), from Google's Maven | 9.4.1 | Apache-2.0 | build |
| Android SDK Platform 37 (`android.jar` to compile against) | 37.0 | Android SDK License Agreement (Google) | build only, not redistributed |
| OkHttp Android artifact (`okhttp-android`, via Ktor's OkHttp engine) | 5.5.0 | Apache-2.0 | main (Android) |
| QuickJS-KT Android artifact (`quickjs-kt-android`, bundles QuickJS natives, MIT) | 1.0.15 | Apache-2.0 | main (Android) |
| org.gradle.toolchains.foojay-resolver-convention | 1.0.0 | Apache-2.0 | build |
| GitHub Actions `actions/checkout@v4`, `actions/setup-java@v4` | v4 | MIT | CI |
| Android Emulator and AOSP x86_64 system images (API 26, 36), installed by `sdkmanager` on the CI runner | current at run time | Android SDK License Agreement (Google) | CI only (`MergeCheck`), not redistributed |

The JVM tests run on TestNG rather than kotlin-test's default JUnit 4, which is EPL-1.0.
