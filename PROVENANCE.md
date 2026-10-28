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
| yt-dlp 2026.10.26 sdist from PyPI (`pypi.org/pypi/yt-dlp/2026.10.26/json`) | 2026.10.26 | Unlicense (`LICENSE` in that sdist) | `extractor/soundcloud.py` (rows below); compared with 2026.10.21 for the YouTube files VyonEx ports: only Python 3.11 date-time cleanups changed |
| yt-dlp pull request 13515 head, fetched read-only with git (`refs/pull/13515/head`) | head of 2026-10-27 | not merged; the repository's license is the Unlicense | **read only to check provenance**, nothing ported: the file list (`downloader/sabr/`, `extractor/youtube/_proto/`, `_streaming/sabr/`) and its code and commit messages searched for credits to other projects; see `QUESTIONS.md` item 58 |
| yt-dlp, `pip download yt-dlp==2026.10.21 --no-binary :all:` from PyPI | 2026.10.21, git `8cdf4fea48ecf4064387375301d7f294ebdb17c1` (`yt_dlp/version.py`) | Unlicense (`LICENSE` in that sdist) | see the yt-dlp table below |
| yt-dlp/ejs, PyPI `yt-dlp-ejs` 0.8.0 (sdist for the TypeScript sources, wheel for the built scripts) | 0.8.0 | Unlicense (`LICENSE` in both) | `ejs/core.min.js` and `ejs/lib.min.js` copied unmodified; `src/yt/solver/*.ts` read to learn the `jsc()` input/output and the `_result.n` / `_result.sig` contract of a prepared player, which `PlayerJs.kt` relies on |
| meriyah (bundled inside ejs `lib.min.js`) | 6.1.4 | ISC (header of `lib.min.js`) | JavaScript parser the solver runs in QuickJS; not read or modified |
| astring (bundled inside ejs `lib.min.js`) | 1.9.0 | MIT (header of `lib.min.js`) | code generator the solver runs in QuickJS; not read or modified |
| QuickJS-KT sources jar, Maven Central `io.github.dokar3:quickjs-kt:1.0.15:sources` | 1.0.15 | Apache-2.0 (POM) | API signatures of `QuickJs.create/evaluate/close` |
| Ktor `ktor-client-okhttp-jvm` 3.6.0 jar from Maven Central, `OkHttpEngine` bytecode read with `javap` | 3.6.0 | Apache-2.0 (POM) | learned that closing the engine shuts down the dispatcher and connection pool of the clients it built, so the JVM `close()` leaves the Ktor client open (`QUESTIONS.md` item 29) |
| YouTube public endpoints, observed live from the build machine | 2026-10-26 | YouTube's content (fixtures only) | `iframe_api`, player script `dce23b30`, `youtubei/v1/player` with the three identities, `www.youtube.com/` page; the watch page answered with a bot-check redirect. Captures are in `fixtures/` |
| YouTube watch page, captured by Codex on the owner's home network | 2026-10-27 | YouTube's content (fixture only) | `fixtures/watch-page.html`, requester IP replaced with `192.0.2.1`; used by the watch-page tests |
| YouTube logged-out responses (unplayable players, browse, next, search), captured by Codex with `CaptureFixtures` on the owner's home network | 2026-10-27 | YouTube's content (fixtures only) | 14 files in `fixtures/` (listed in `fixtures/README.md`), visitor IDs and heartbeat token redacted; input for the playlist, album, artist, search and related-video work |
| YouTube's search page and response, observed from the build machine: `searchFilterOptionsDialogRenderer` (filter groups, labels, `searchEndpoint.params`, `FILTER_STATUS_SELECTED` / `DISABLED`), `gridShelfViewModel` Shorts grids; the home page's `SBOX_SETTINGS` and its base script's suggest request (`suggestqueries-clients6.youtube.com/complete/search?ds=yt`, `hl`, `gl`, `client=youtube`) and response parsing (JSON array, optional JSONP wrapper) | 2026-10-27 | YouTube's content (fixtures only); nothing copied from the script | `VyonExSearchType` params, `VyonExSearchFilter`, `Lists.kt` `searchFiltersOf`, `suggestionsOf`; `Engine.suggestions`. Captures `search-web-channels.json`, `search-web-shorts.json`, `suggest-web.json` |
| SoundCloud's home page, API responses (`api-v2.soundcloud.com` `resolve`, `tracks?ids=`, a transcoding's stream answer) and an HLS playlist's first lines, observed from the build machine | 2026-10-27 | SoundCloud's content (fixtures only) | `SoundCloudEngine`; captures `soundcloud-track.json`, `soundcloud-playlist.json`, `soundcloud-playlist-tracks.json` (yt-dlp's test track and set), `track_authorization` redacted; no client ID or stream URL is stored |
| RFC 8216 (HTTP Live Streaming), rfc-editor.org | read 2026-10-28 | IETF document | which tags make a media playlist one finished stream (`#EXT-X-ENDLIST`, `#EXT-X-MAP`, `#EXT-X-KEY` `METHOD`, `#EXT-X-BYTERANGE`, `#EXT-X-STREAM-INF`) and how URIs resolve; `Hls.kt`. Nothing copied |
| yt-dlp 2026.10.26 `extractor/bandcamp.py` | read 2026-10-28 | Unlicense | the test URLs `CaptureFixtures` asks for and the "Client Challenge" page it stops at (yt-dlp's impersonated retry is not followed); no Bandcamp code yet |
| Apple's iTunes Search API documentation, https://performance-partners.apple.com/search-api | read 2026-10-28 | Apple's documentation; terms of use in it | `/lookup` (by ID, `entity=song` for an album's songs, `entity=album` for an artist's albums) and `/search` parameters (`term`, `media`, `entity`, `limit` 1–200, `country`), the result fields, the rate limit (about 20 calls a minute) and the promotional-content terms; `AppleMusic.kt`, `internal/AppleMusic.kt` |
| Apple's iTunes Search API responses and artwork URLs, observed from the build machine | 2026-10-28 | Apple's content (fixtures only) | captures `apple-song.json`, `apple-album.json`, `apple-artist.json`, `apple-search.json` (song previews replaced); the larger artwork sizes (600, 1200, 3000 px from the `100x100bb` URL) checked to answer JPEGs of those sizes |
| [YouTube Terms of Service](https://www.youtube.com/t/terms) | accessed 2026-10-27 | Terms of Service, not a content license | assessed public redistribution risk for captured fixtures; see `QUESTIONS.md` item 4 |
| AOSP `frameworks/av` `media/libstagefright/webm/WebmWriter.cpp` at `android-8.0.0_r1`, `android-9.0.0_r1`, `android-10.0.0_r1` (android.googlesource.com) | read 2026-10-27 | Apache-2.0 | which audio codecs Android's WebM muxer accepts per version (Vorbis only before Android 10, Opus from Android 10); `VyonExMerge.supports`. Nothing copied |
| AndroidX Media3 sources jars `media3-common`, `media3-session`, `media3-exoplayer` 1.11.1 (Google's Maven) | read 2026-10-27 | Apache-2.0 (POM) | how the app's player adds next and previous for the session (`ForwardingSimpleBasePlayer`, as `ForwardingPlayer`'s own documentation advises), that a session's play button prepares an idle player, and that `ExoPlayer.prepare` acts only when idle; `PlaybackService`. Nothing copied |
| Apache License 2.0 text, https://www.apache.org/licenses/LICENSE-2.0.txt | fetched 2026-10-26 | — | `LICENSE` |
| Synthetic clips made on the build machine with FFmpeg 6.1.1 (Ubuntu package) from its own `testsrc` pattern and `sine` tone; no outside media | 2026-10-27 | generated output; no FFmpeg code is in the repository | `src/androidDeviceTest/resources/clips/`, used by `MergeCheck`. 64x64, 30 fps, 2 s: `video-avc.mp4` (libx264, `-g 30`) and `audio-aac.m4a` (aac 32k) written with `-movflags frag_keyframe+empty_moov+default_base_moof`, like YouTube's fragmented MP4; `video-vp9.webm` (libvpx-vp9, `-g 30 -b:v 50k`) and `audio-opus.webm` (libopus 24k, 48 kHz); `audio-aac-hls.m4a` (2026-10-28): a 3 s 440 Hz `sine` in AAC 32k written as HLS fMP4 (`-f hls -hls_time 1 -hls_segment_type fmp4`), its init segment and four segments joined in playlist order, as `soundCloud.download` joins SoundCloud's |

Vendored files: `ejs/core.min.js` sha256 `18da6ce0758b416e7ae645084f4f8801f9f9d59d6c477c05eaa0ff94ebd8cc00`,
`ejs/lib.min.js` sha256 `c55987fe697e5b9ee18830163f7af85327e9bb5c3e674b969d38c8d205eaa577`. Their
SHA3-512 values equal the allowlist in yt-dlp 2026.10.21 `yt_dlp/extractor/youtube/jsc/_builtin/vendor/_info.py`.

### What came from yt-dlp 2026.10.21

| yt-dlp location | VyonEx location | What |
|---|---|---|
| `extractor/youtube/_video.py` `_DEFAULT_CLIENTS`, `_DEFAULT_AUTHED_CLIENTS`, `_get_requested_clients` | `Clients.kt` `LOGGED_OUT_ORDER`, `LOGGED_IN_ORDER`, `Engine.extract` | identity order (logged out: tv_samsung, visionos, web; logged in: web_embedded, tv_samsung, web); no cookie-less client when logged in. The `tv` and `tv_downgraded` fallbacks after them are VyonEx's choice, not a yt-dlp default |
| `extractor/youtube/_base.py` `INNERTUBE_CLIENTS` (`tv_samsung`, `visionos`, `web`, `web_embedded`, `tv`, `tv_downgraded`), `WEB_PO_TOKEN_POLICIES`, `build_innertube_clients`, `_fix_embedded_ytcfg` | `Clients.kt` `IDENTITIES` | client names, numeric ids, versions, user agents, device fields; which ones need the JS player, cookies and a GVS PO token |
| `_base.py` `generate_api_headers`, `_call_api`, `_extract_context` | `Engine.playerResponse`, `playerRequestBody` | player endpoint, `prettyPrint=false`, `X-YouTube-Client-Name/Version`, `Origin`, `X-Goog-Visitor-Id`, `X-Origin`, `timeZone`/`utcOffsetMinutes` |
| `_base.py` `_make_sid_authorization`, `_get_sid_authorization_header`, `_get_sid_cookies`, `_has_auth_cookies` | `Youtube.kt` `sidAuthorization`, `isLoggedIn` | ported: SAPISIDHASH / SAPISID1PHASH / SAPISID3PHASH, logged-in test |
| `_video.py` `_generate_player_context`, `_get_checkok_params`, `_extract_player_response` | `playerRequestBody` | `playbackContext.contentPlaybackContext` (`html5Preference`, `signatureTimestamp`), `contentCheckOk`, `racyCheckOk`, `serviceIntegrityDimensions.poToken` |
| `_video.py` `_extract_formats_and_subtitles` (`get_manifest_n_challenge`, the `hlsManifestUrl` / `dashManifestUrl` handling: n challenge as a `/n/` path segment, PO token as a `/pot/` segment before HLS's `/file|playlist/index.m3u8`, DASH skipped while live) | `Youtube.kt` `manifestUrlOf`, `Engine.manifest` | which manifest to take and how its URL is completed; nothing about parsing manifests (players read them) |
| `_video.py` `_SUBTITLE_FORMATS`, `_get_subtitles` / `process_language` (`fmt`, dropping `xosf`, `tlang` unless it is the track's own language, translation languages), `_extract_chapters_from_json`, `_extract_chapters_from_engagement_panel`; `common.py` `_extract_chapters_helper`, `_extract_chapters_from_description` | `Api.kt` `urlFor`, `Youtube.kt` `chaptersFrom`, `chaptersOfNext`, `chaptersOfDescription`, `Engine.chapters`, `Engine.captionText` | caption formats and translation; where chapters are and how they are checked (in order, within the duration; description lines time-then-title or title-then-time) |
| `_video.py` `_comment_entries` (`extract_header` sort entries by position, `extract_thread`: `commentThreadRenderer` / `commentViewModel`, `commentKey` / `toolbarStateKey` into `frameworkUpdates` entity payloads, replies' continuation), `_extract_comment` (fields of `commentEntityPayload`, `heartState`, `pinnedText`), `utils` `parse_count` | `Comments.kt` | where comments, their authors, counts, pins and hearts, replies and the next page are |
| `_tab.py` `_extract_metadata_from_tabs` (`channelMetadataRenderer`, `pageHeaderViewModel` banner), `_extract_tab_renderers`, `_extract_selected_tab`, `_rich_entries` (lockups, `shortsLockupViewModel` and its English "… views - play Short" label), `_grid_entries`, `_extract_lockup_view_model` (playlist lockups) | `Channels.kt` | where a channel's details, its tabs, and each tab's videos, Shorts and playlists are; tabs told apart by URL |
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
| `_tab.py` `_extract_channel_renderer` (`channelId`, `title`, `thumbnail`), `_search_results` (`params` sent with `query`), `_rich_entries` (`shortsLockupViewModel`) | `Lists.kt` `channelResultOf`, the Shorts of `webSearchPageOf`; `Engine.search(query, params)` | channel results and Shorts grids in search; the filter params themselves come from YouTube's response (row below) |
| `soundcloud.py` (2026.10.26) `SoundcloudBaseIE._update_client_id` (the web client's ID from the home page's scripts, last first, `client_id\s*:\s*"…"`), `_call_api` (looking the ID up again once after a 401 or 403), `_extract_info_dict` (transcodings: `ctr-`/`cbc-`/`encrypted-hls` as DRM and skipped, `abr` presets skipped, `/hls` URLs as HLS, `snipped` and `/preview/` as previews, `secret_token` passed on), `_resolv_url`, `_IMAGE_REPL_RE` / `_ARTWORK_MAP` (`t500x500`), `SoundcloudIE._VALID_URL`, `SoundcloudPlaylistBaseIE._extract_set` (tracks without details asked with `tracks?ids=`) | `internal/SoundCloud.kt`, `SoundCloud.kt` | how SoundCloud tracks, sets and streams are read |
| `_tab.py` (2026.10.26) `_post_thread_entries` (`backstagePostThreadRenderer` → `post.backstagePostRenderer`, attached `videoRenderer` and `playlistRenderer`, video links in `contentText.runs`), `_post_thread_continuation_entries` | `Posts.kt`; `Engine.channelPosts`, `channelPostsMore` | where posts, their attachments and their links are; the image (`backstageImageRenderer`, `postMultiImageRenderer`), like-count label and redirect-link fields come from YouTube's own response (captures below) |
| `_video.py` `get_language_code_and_preference` (`audioTrack.displayName` containing "descriptive" −10 or "original" 10, `audioIsDefault` 5, else −1) | `Youtube.kt` `audioFormatsOf`, `audioTrackRank` | which audio track of a dubbed video is kept |
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

From Maven Central, and the AndroidX ones from Google's Maven; licenses as declared in each POM.

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
| Android Gradle Plugin (`com.android.kotlin.multiplatform.library`, `com.android.application`), from Google's Maven | 9.4.1 | Apache-2.0 | build |
| Kotlin Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) | 2.4.21 | Apache-2.0 | build (app) |
| Android SDK Platform 37 (`android.jar` to compile against) | 37.0 | Android SDK License Agreement (Google) | build only, not redistributed |
| OkHttp Android artifact (`okhttp-android`, via Ktor's OkHttp engine) | 5.5.0 | Apache-2.0 | main (Android) |
| QuickJS-KT Android artifact (`quickjs-kt-android`, bundles QuickJS natives, MIT) | 1.0.15 | Apache-2.0 | main (Android) |
| org.gradle.toolchains.foojay-resolver-convention | 1.0.0 | Apache-2.0 | build |
| Jetpack Compose from Google's Maven (BOM 2026.09.00: ui, foundation, runtime, animation 1.12.1; material3 1.4.0), activity-compose 1.13.0, lifecycle-runtime-compose 2.11.0, and the AndroidX libraries they bring (core 1.18.0, lifecycle, savedstate, collection, annotation, emoji2, window, navigationevent, profileinstaller, startup, tracing and others) | as listed | Apache-2.0 | main (app) |
| AndroidX Media3 `media3-exoplayer`, `media3-session`, `media3-exoplayer-hls`, `media3-exoplayer-dash` (+ common, datasource, decoder, extractor, container, database), and `androidx.media` | 1.11.1, 1.7.0 | Apache-2.0 | main (app) |
| kotlinx-coroutines-android | 1.11.0 | Apache-2.0 | main (app) |
| Guava (`-android`, + failureaccess, listenablefuture; via Media3) | 33.3.1-android | Apache-2.0 (parent POM) | main (app) |
| JSpecify (via Media3) | 1.0.0 | Apache-2.0 | main (app) |
| GitHub Actions `actions/checkout@v4`, `actions/setup-java@v4`, `actions/upload-artifact@v4` | v4 | MIT | CI |
| Android Emulator and AOSP x86_64 system images (API 26, 36), installed by `sdkmanager` on the CI runner | current at run time | Android SDK License Agreement (Google) | CI only (`MergeCheck`, the app start check), not redistributed |

The JVM tests run on TestNG rather than kotlin-test's default JUnit 4, which is EPL-1.0.
