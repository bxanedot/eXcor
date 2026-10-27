# Open questions

Each item names the reading chosen; nothing was looked up in the old code.

## Blocked by YouTube from the cloud build machine

1. **Watch page.** `GET https://www.youtube.com/watch?v=…` redirected the cloud build machine to
   Google's bot check (`fixtures/watch-page-blocked.html`); I did not try to get around it.
   **Update 2026-10-27:** a real page from the owner's network is now `fixtures/watch-page.html`
   (IP address redacted). Logged out, its audio formats are SABR-only (no `url`, no
   `signatureCipher`), so by the brief's no-SABR rule the iOS watch-page fallback returns nil and
   `audioCandidates` returns an empty list for logged-out sessions. Logged-in pages may differ;
   untested.
2. **Live test.** `VYONEX_LIVE=1` passed for `dQw4w9WgXcQ` (TV_SAMSUNG, signature and `n`
   decoded, ranged GET 206). `kJQP7kiw5Fk` then got "This video is not available" from both
   TVHTML5 and VISIONOS, and afterwards every request from this IP got
   `LOGIN_REQUIRED: Sign in to confirm you're not a bot`. CI does not run the live test.
   **Update 2026-10-27, owner's home network:** before item 23, `extract` returned a TV_SAMSUNG
   stream whose ranged GET got 403. With item 23, all three videos returned VISIONOS streams
   (itag 251). TV_SAMSUNG's URL for `dQw4w9WgXcQ` was refused by the probe (403), and its player
   requests for the other two got `LOGIN_REQUIRED` (bot check), so YouTube was refusing that
   client on this network.
   **Update 2026-10-27, merged `main` at `c7a769c`, owner's network, logged out:** two runs were
   reported, both 2 tests and 0 failures: `extractsRangeFetchableStreams`
   (audio for the three videos) and `extractsVideoAndMetadata` (video + audio streams answering a
   ranged GET, and metadata). Which network adapter carried the traffic was not recorded.
   The same day, the new `VYONEX_LIVE_IDS` survey for `dQw4w9WgXcQ` from the cloud build machine
   got TV_SAMSUNG streams (audio itag 251; video itag 137 + audio 251; each ranged GET 206) and
   WEB metadata, so TV_SAMSUNG answered there while it was refused on the owner's network.
   **Survey on `77054a1`, owner's network (wired), logged out, reported by Codex:** 0 failures; every
   returned URL answered 206 and TV_SAMSUNG was refused (403) again, so VISIONOS served everything.
   A Short gave 608x1080 VP9 (itag 780) + opus; a 60 fps video 1080p60 H.264 (itag 299); an
   age-restricted video `LOGIN_REQUIRED: Sign in to confirm your age` (reported, not bypassed); a
   24/7 live stream `UNPLAYABLE: This live stream recording is not available.`; an upcoming event
   `UNPLAYABLE: This live event will begin in 30 hours.`; a copyright-blocked video its block
   reason. A follow-up run the same day added two YouTube Music auto-generated tracks
   (`MgNrAu2pzNs`, `DbCvuSGfR3Y`, from yt-dlp's test data): VISIONOS opus audio (itag 251) and a
   square 1080x1080 H.264 picture (itag 137), each answering 206; and a made-up ID
   (`AAAAAAAAAAA`): `ERROR: This video is unavailable`, metadata null. Not yet verified: a region
   lock and a private video. The first run showed `isLive` reading true for past and upcoming
   streams and a live stream's time on air as its duration; fixed in item 32.
   Logged-in sessions remain untested live.

## Readings of the brief

3. **ISC inside the solver.** Accept the bundled `meriyah` ISC license under section 1's
   explicit allowance for ISC-licensed companion repositories; `NOTICE` includes the ISC
   license text and `PROVENANCE.md` records the source. Do not describe ISC as legally
   equivalent to MIT or BSD; section 2's separate dependency wording is ambiguous if the
   parser is treated as a standalone dependency.
4. **Fixtures in a public Apache-2.0 repo.** Keep the captured fixtures for deterministic
   offline tests, as section 6 asks. They are YouTube-owned third-party files and are not
   licensed under this repo's Apache-2.0 license; because the repo is public, they are
   distributed publicly. This is an engineering choice, not rights clearance. [YouTube's
   Terms](https://www.youtube.com/t/terms) restrict reproducing or distributing content except
   as authorized by YouTube, with prior permission, or as allowed by applicable law. Downloading
   fixtures at test time would add network dependence and does not by itself resolve that terms
   issue; seek rights/legal review before treating public redistribution as cleared.
5. **`VyonExHost` defaults.** `tokenProvider` and `filesDir` default to `null`, so hosts
   compile whether or not they override them. `httpClient` has no default.
6. **`configStore`.** Decision: when `enabled = false`, disable JVM disk-player-cache reads and
   writes while preserving in-memory caches. This follows section 4.7's promise to work without
   persistence. Implemented in PR #9; see item 28.
7. **`putString` value** is taken as non-null `String`.
8. **Logged-in identities.** Cookies go with every YouTube request when present; logged-in
   sessions (LOGIN_INFO + SAPISID, as yt-dlp) also send SAPISIDHASH and skip VISIONOS.
   yt-dlp's logged-in default starts with `web_embedded`; it is left out because it answered
   "This video is unavailable" live without the embed page's `encryptedHostFlags`, and no
   account was available to test logged-in behavior. Offline tests (`LoggedInTest`) now cover
   this with made-up cookies and tokens: login detection, the SAPISIDHASH values, the headers
   sent, the VISIONOS skip, and the PO-token path. It is still untested against a real account.
9. **Media headers.** `User-Agent` of the identity used, plus `Cookie` only when logged in;
   watch-page streams use the headers listed in 4.3.
10. **`audioCandidates`** has no `skipClients`, so it does not apply the skip/exclusion rule of
    4.3; "as in 4.3" is read as "load the page the same way".
11. **JVM failure.** `extract` throws `IllegalStateException("VyonEx: video … is unplayable
    (STATUS: reason)")` with the first non-OK playability status when no identity succeeded,
    and returns null otherwise (network errors, no usable format).
12. **"Stereo"** is read as exactly 2 channels; more channels get no bonus.
13. **PO token visitor data.** `getPoToken` gets the session's visitor data, else the one
    YouTube returned to an earlier identity in the same call; without either, WEB is skipped.
    The player request then uses the token's own `visitorData`.
14. **`onRefused`** invalidates the in-memory and on-disk prepared player, but does not call
    `tokenProvider.invalidateAttestation()`; the brief does not ask for it.
15. **Player variant.** The `player_ias` script is always used (yt-dlp's default), even when a
    page names `player_es6`; the functions are the same per player version.
16. **Warm-up cost.** Accept background preparation for the initial release because
    `initialize()` returns immediately. The 6.5 s measurement is from the JVM build machine;
    actual iOS startup, first-play and battery impact remain unmeasured. Revisit if device
    measurements show unacceptable latency or battery use.
17. **Toolchain.** Gradle itself runs on JDK 21 (locally and in CI); JDK 27 is the compile
    toolchain and JVM bytecode targets 26, as specified.

## VyonExKit (Swift)

18–22. VyonExKit's questions (platforms, timeouts, API spelling, errors, caching) were dropped with
    the package on 2026-10-27: the owner chose one engine, and iOS apps use the Kotlin XCFramework.

## After delivery

23. **"Whose URL works" (4.2).** After a live run returned a URL that got HTTP 403, the engine
    now probes the chosen URL with a one-byte ranged GET using the returned headers before
    returning it. A refusal is logged at WARN, excludes that profile for 10 minutes (as `onRefused`
    would) and moves on to the next identity. Cost: one small extra request per identity tried.
    A probe cannot catch refusals that depend on how the host fetches (for example a different
    IPv4/IPv6 route in the host's player than in its OkHttp client).
24. **Video (owner's request, 2026-10-27, beyond section 5's "audio").** `extractVideo(videoId,
    maxHeight = 1080, skipClients, requireAvc = false)` uses the same identities, exclusions, URL
    probe and errors as `extract`. It picks the tallest format up to `maxHeight` (the smallest if
    none fits), then frame rate, then H.264 over VP9 over AV1 (H.264 plays everywhere), then
    bitrate. Video-only formats come with the best audio up to 160 kbps (`requireAvc` also means
    AAC audio); without usable audio only combined formats are considered. Both URLs are probed and
    remembered, so `headersFor` and `onRefused` work for either. There is no watch-page fallback:
    logged-out pages are SABR-only (item 1). Should AV1/VP9 win over H.264 at the same height for
    hosts that decode them?
25. **Combined formats.** Only TV_SAMSUNG returns one (itag 18, 360p, ciphered); VISIONOS has
    adaptive formats only. So at most 360p comes as a single URL; anything taller needs the host
    to play video and audio together.
26. **Metadata.** `metadata(videoId)` asks WEB first, which adds the microformat (likes, category,
    publish/upload dates, family-safe, unlisted, Shorts, countries, profile and canonical URLs),
    then VISIONOS (title, author, description, keywords, views, duration, thumbnails). No PO token
    is needed for metadata, cookies go only to WEB, and the call returns null when neither reports
    `videoDetails`. WEB needs the player's `signatureTimestamp`, so a cold call waits for player
    preparation (item 16). Dates are YouTube's ISO 8601 strings, unparsed. Caption tracks are
    listed with their signed `timedtext` URLs (only `https://www.youtube.com/api/timedtext`); like
    yt-dlp, a track whose `exp` is `xpe` or `xpv` needs a PO token, so it is left out. Caption URLs
    are not probed. Dislikes, comments and chapters are not in the player response and are not
    fetched.
27. **iOS names.** Swift does not see Kotlin default arguments, so it passes every parameter of
    `extractVideo`; and Kotlin/Native renames `VyonExMetadata.description` to `description_`,
    since `NSObject` already has `description`.
28. **`configStore.enabled` and the disk cache (owner's decision, 2026-10-27; settles item 6).**
    While `enabled` is false the JVM disk cache reads and writes nothing, so nothing persists
    (section 4.7); the prepared player stays cached in memory. `enabled` is checked on every
    access, so switching it takes effect at once. Deleting a refused player still happens while
    disabled, so it cannot come back once the cache is enabled again.
29. **`close()`** (added for the app, milestone L0). It stops background work and frees the
    loaded player; the instance must not be used afterwards. On iOS it also closes VyonEx's own
    HTTP client. On the JVM the Ktor client stays open: Ktor 3.6.0's OkHttp engine, when closed,
    evicts the connection pool and shuts down the dispatcher of the clients it built, and those
    are shared with the host's `OkHttpClient`.
30. **Visitor IDs in captures.** The four response captures held YouTube's anonymous visitor IDs
    (`visitorData` and its copies). On 2026-10-27 every copy was replaced with
    `REDACTED-VISITOR-DATA`; no test reads them. `FixtureHygieneTest` now fails on a public
    address in `ip=`/`remoteHost`, a cookie value or an unredacted visitor ID, without printing
    it. The remaining `ip=` values are YouTube's `0.0.0.0` placeholder, the capture machine's
    private `fc00::/7` IPv6, or the documentation address.
31. **Owner decisions, 2026-10-27, on the product plan.** BRIEF section 5 amended (links, playlists,
    albums, mixes, music metadata, then search); YouTube captures move out of public view by making
    the repository private (owner's action); the app targets Android + iOS, so the library gains an
    Android target later; search lives in the library, after collections. **Link parsing**
    (`VyonEx.parse`, no network) ports yt-dlp's URL forms for YouTube's own hosts only
    (`youtube.com`, `www.`/`m.`/`music.youtube.com`, `youtube-nocookie.com`, `youtu.be`; no mirrors,
    ports or userinfo). A watch link with `list=` is a video opened from that list; a list without a
    usable video is the list; `RD…` lists are mixes; `/browse/VL…` is the playlist after `VL`;
    `/browse/MPREb_…` and bare `MPREb_…` IDs are albums. Personal lists (`WL`, `LL`, `LM`, `RDMM`)
    parse but will need a login to resolve. Channel and search links return null. `t`/`start`
    accept seconds, `[h:]m:s` and `1h2m3s`.
32. **Live status.** `VyonExMetadata.liveStatus` follows yt-dlp's reading of `isPostLiveDvr`,
    `isLive` (or the microformat's `isLiveNow`), `isUpcoming` and `isLiveContent`. `isLive` now means
    live right now. `durationSeconds` is null for live-now and upcoming streams, and for a zero
    length. `liveStatus` was appended to `VyonExMetadata`, a type added after BRIEF section 3, so
    code constructing it must pass the new field.
33. **YouTube Music track info.** `VyonExMetadata.music` (track, artists, album, release year and
    date) is read from the description of auto-generated YouTube Music uploads, with yt-dlp's
    pattern, so it matches yt-dlp's results, quirks included: an `Artist:` line counts only when
    more lines follow it, and a release date needs a `℗` line before it. Other videos get null.
    Checked live on 2026-10-27 from the cloud build machine: `MgNrAu2pzNs` gave "Voyeur Girl" by
    Stephen on *it's too much love to know my dear* (2019) and `DbCvuSGfR3Y` "Back Around" by
    half·alive on *Conditions Of A Punk* (2021), as in yt-dlp's tests. That machine got no streams for
    either (`UNPLAYABLE: This video is not available`); the owner's network played both (item 2).
34. **No Apple devices (owner, 2026-10-27).** The Android app comes first. iOS stays a library
    target that CI builds and tests on the simulator; an iOS app, device timing (plan S1) and the
    AVPlayer spike (S2) wait for a Mac and an iPhone.
35. **Android target (plan L6, moved earlier at the owner's request).** Built with AGP 9.4.1's
    Kotlin Multiplatform Android library plugin. `compileSdk` is 37 because OkHttp 5.5.0's Android
    artifact demands it; `minSdk` is 26, where `java.nio.file` (the disk cache) starts. The JVM and
    Android share `src/jvmCommonMain` (OkHttp, `java.io.File`), so the host API is identical. No
    tests run on Android yet: QuickJS-KT's Android native library cannot load on the build
    machine's JVM, so the common tests keep running on the JVM and the iOS simulator, and Android
    gets device checks with the sample app. CI builds the AAR on the Windows runner.
36. **Owner decisions, 2026-10-27, second round.** (a) Downloads are in: every quality YouTube
    offers, fetched in ranged chunks, with video and audio merged into one file on the device.
    YouTube never offers 4K (or anything above 360p) as one stream: 4K exists only as VP9 or AV1
    video plus separate audio, and H.264 stops at 1080p, so "4K in one stream" means merging. DRM
    formats stay excluded. YouTube's terms forbid downloading outside its own apps and copyright
    still applies; nothing here is legal clearance. (b) Artist pages and logged-out recommendations
    (related videos, mixes) are in. (c) Account features (library, likes, Premium, members-only)
    come later, as a separate reviewed step: they need an in-app sign-in, stored login cookies and,
    since VISIONOS takes no cookies and TV_SAMSUNG is refused on the owner's network, likely PO
    tokens from the app. (d) Captures go into the public repository after all (reversing item
    31's "private"); the terms question in item 4 stays open. **Capture tool:** `CaptureFixtures`
    (opt-in, `VYONEX_CAPTURE=1`) sends the logged-out requests, redacts and checks before writing,
    and stops at a bot check. A run from the cloud build machine wrote four player responses and
    then hit a bot check; those were discarded because that network answered differently (the
    age-restricted video came back as `UNPLAYABLE: This video is not available` instead of the
    owner network's `LOGIN_REQUIRED: Sign in to confirm your age`). It also showed upcoming
    streams answering `LIVE_STREAM_OFFLINE` with a `liveStreamability` object.
37. **Downloads, library part.** `formats(videoId)` lists every playable format (audio, video,
    combined) of the first identity whose URLs answer; one probe stands for the list, since a
    response's formats share its signature and address checks. The formats are not remembered for
    `headersFor`/`onRefused` (a listing of about 27 would push the playing stream out of the
    64-entry memory); on a refusal the app calls `exclude` and lists again. `download` fetches
    10 MiB ranged requests (yt-dlp's chunk size for YouTube), resumes from a byte offset, accepts
    only allowlisted URLs, and throws on a refused chunk without printing the URL. Merging video and
    audio into one file is item 38.
38. **Merging into one file (Android).** `mergePair(formats, maxHeight, container)` picks the tallest
    video (then frame rate, then bitrate) and the best audio of one codec family: `WEBM` is VP9 +
    Opus, which is how YouTube offers 4K; `MP4` is H.264 + AAC, which stops at 1080p.
    `VyonExMerge.merge` (Android only) copies the encoded samples into one file with Android's
    `MediaExtractor` and `MediaMuxer`, interleaved by time, so nothing is re-encoded. It refuses
    other codec pairs and leaves no partial file. Left out for now: AV1 (whether Android's MP4 muxer
    takes it on every supported Android version is unchecked), HDR VP9 (`vp09.02…`; its colour
    metadata would need carrying over) and iOS (no Apple devices, item 34; AVFoundation also writes
    no WebM). Checked in CI by `MergeCheck`, a plain `Instrumentation` (androidx.test's runner needs
    JUnit 4, EPL-1.0), on API 26 and API 36 emulators. It merges synthetic two-second clips laid out
    the way YouTube serves its formats (fragmented MP4, WebM) and compares every track's sample
    count. A real 4K download has not been merged yet. That needs the owner's network and device.
    Merging needs free space for both inputs again until they are deleted. Open: on a video with
    dubbed audio tracks, the audio choice here and in `extract` ignores which track is the
    original, so it can take a dub. The fix (yt-dlp reads `audioTrack.audioIsDefault`) waits for a
    capture of such a video.
39. **Why a video won't play.** `VyonExUnplayableException` (still an `IllegalStateException`,
    same message) carries `reason`, YouTube's `status` and `youtubeReason`. The sorting follows
    yt-dlp: age gate (`desktopLegacyAgeGateReason` or its age phrases), country block (the
    `errorScreen` subreason), `LIVE_STREAM_OFFLINE` as upcoming, `ERROR` as unavailable, "sign in"
    as sign-in required, everything else other. The five captured refusals from the owner's
    network sort as age-restricted, unavailable, upcoming and other (twice: an ended live stream
    without a recording, a copyright block). The country block and sign-in cases are checked
    against response shapes taken from yt-dlp's code, not captures. The iOS `extract` now
    rethrows the refusal when the watch-page fallback finds nothing too, instead of returning null.
