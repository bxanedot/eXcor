# Provenance

Every external source this work relied on. Nothing on BRIEF section 1's forbidden list was
opened, cloned, searched or fetched: no `bxanedot/vyon*`, none of the listed local folders,
no XERJ index, and no GPL/LGPL/AGPL extractor (NewPipe, InnerTube*, Metrolist, ViMusic,
SimpMusic, LibreTube, Piped, Invidious and the like). Where yt-dlp code comments mention
such projects, the linked material was not followed.

## Sources read or ported

| Source | Version / date | License (checked) | Used for |
|---|---|---|---|
| yt-dlp, `pip download yt-dlp==2026.10.21 --no-binary :all:` from PyPI | 2026.10.21, git `8cdf4fea48ecf4064387375301d7f294ebdb17c1` (`yt_dlp/version.py`) | Unlicense (`LICENSE` in that sdist) | see the yt-dlp table below |
| yt-dlp/ejs, PyPI `yt-dlp-ejs` 0.8.0 (sdist for the TypeScript sources, wheel for the built scripts) | 0.8.0 | Unlicense (`LICENSE` in both) | `swift/Sources/VyonExKit/ejs/core.min.js` and `lib.min.js` copied unmodified (used by both engines); `src/yt/solver/*.ts` read to learn the `jsc()` input/output and the `_result.n` / `_result.sig` contract of a prepared player, which `PlayerJs.kt` relies on |
| meriyah (bundled inside ejs `lib.min.js`) | 6.1.4 | ISC (header of `lib.min.js`) | JavaScript parser the solver runs in QuickJS; not read or modified |
| astring (bundled inside ejs `lib.min.js`) | 1.9.0 | MIT (header of `lib.min.js`) | code generator the solver runs in QuickJS; not read or modified |
| QuickJS-KT sources jar, Maven Central `io.github.dokar3:quickjs-kt:1.0.15:sources` | 1.0.15 | Apache-2.0 (POM) | API signatures of `QuickJs.create/evaluate/close` |
| YouTube public endpoints, observed live from the build machine | 2026-10-26 | YouTube's content (fixtures only) | `iframe_api`, player script `dce23b30`, `youtubei/v1/player` with the three identities, `www.youtube.com/` page; the watch page answered with a bot-check redirect. Captures are in `fixtures/` |
| Apache License 2.0 text, https://www.apache.org/licenses/LICENSE-2.0.txt | fetched 2026-10-26 | — | `LICENSE` |

Vendored files: `swift/Sources/VyonExKit/ejs/core.min.js` sha256 `18da6ce0758b416e7ae645084f4f8801f9f9d59d6c477c05eaa0ff94ebd8cc00`,
`swift/Sources/VyonExKit/ejs/lib.min.js` sha256 `c55987fe697e5b9ee18830163f7af85327e9bb5c3e674b969d38c8d205eaa577`. Their
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
| `_video.py` `_extract_formats_and_subtitles` | `audioFormatsOf`, `playableUrl`, `Engine.playableFormats` | `signatureCipher` fields (`url`, `s`, `sp` defaulting to `signature`), `n` transform, `pot` parameter, skipping live/OTF/DRM formats and URL-less (SABR) formats |
| `_base.py` `_YT_INITIAL_PLAYER_RESPONSE_RE` | `parseWatchPage` | `ytInitialPlayerResponse =` marker |
| `extractor/youtube/jsc/_builtin/ejs.py` `_construct_stdin` | `PlayerJs.kt` `preparePlayer` | how the scripts are run: lib, `Object.assign(globalThis, lib)`, core, `jsc({type:'player', …, output_preprocessed:true})` |

VyonExKit (Swift) is a port of this Kotlin code, so the same rows apply to
`swift/Sources/VyonExKit/YouTube.swift`, `PlayerJS.swift` and `VyonExKit.swift`.
YouTubeKit (MIT) was **not** read or used, so `NOTICE` carries no YouTubeKit entry.

Written from the brief without an outside source: format selection, URL allowlist, remembered
streams, exclusions, lifecycle, logging/redaction, the disk cache, watch-page JSON scanning,
the Gradle build and the CI workflow.

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
| TestNG | 7.0.0 | Apache-2.0 | test (JVM) |
| JCommander (via TestNG) | 1.72 | Apache-2.0 | test (JVM) |
| Gradle wrapper | 9.8.2 | Apache-2.0 | build |
| org.gradle.toolchains.foojay-resolver-convention | 1.0.0 | Apache-2.0 | build |
| GitHub Actions `actions/checkout@v4`, `actions/setup-java@v4` | v4 | MIT | CI |
| VyonExKit: Foundation (`URLSession`, `JSONSerialization`), JavaScriptCore, CryptoKit, XCTest | Apple SDK | system frameworks | Swift package, no package dependencies |

The JVM tests run on TestNG rather than kotlin-test's default JUnit 4, which is EPL-1.0.
