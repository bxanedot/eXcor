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
2. **Live test.** `EXCOR_LIVE=1` passed for `dQw4w9WgXcQ` (TV_SAMSUNG, signature and `n`
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
   The same day, the new `EXCOR_LIVE_IDS` survey for `dQw4w9WgXcQ` from the cloud build machine
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
5. **`ExcorHost` defaults.** `tokenProvider` and `filesDir` default to `null`, so hosts
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
11. **JVM failure.** `extract` throws `IllegalStateException("Excor: video … is unplayable
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
    `extractVideo`; and Kotlin/Native renames `ExcorMetadata.description` to `description_`,
    since `NSObject` already has `description`.
28. **`configStore.enabled` and the disk cache (owner's decision, 2026-10-27; settles item 6).**
    While `enabled` is false the JVM disk cache reads and writes nothing, so nothing persists
    (section 4.7); the prepared player stays cached in memory. `enabled` is checked on every
    access, so switching it takes effect at once. Deleting a refused player still happens while
    disabled, so it cannot come back once the cache is enabled again.
29. **`close()`** (added for the app, milestone L0). It stops background work and frees the
    loaded player; the instance must not be used afterwards. On iOS it also closes Excor's own
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
    (`Excor.parse`, no network) ports yt-dlp's URL forms for YouTube's own hosts only
    (`youtube.com`, `www.`/`m.`/`music.youtube.com`, `youtube-nocookie.com`, `youtu.be`; no mirrors,
    ports or userinfo). A watch link with `list=` is a video opened from that list; a list without a
    usable video is the list; `RD…` lists are mixes; `/browse/VL…` is the playlist after `VL`;
    `/browse/MPREb_…` and bare `MPREb_…` IDs are albums. Personal lists (`WL`, `LL`, `LM`, `RDMM`)
    parse but will need a login to resolve. Channel and search links return null. `t`/`start`
    accept seconds, `[h:]m:s` and `1h2m3s`.
32. **Live status.** `ExcorMetadata.liveStatus` follows yt-dlp's reading of `isPostLiveDvr`,
    `isLive` (or the microformat's `isLiveNow`), `isUpcoming` and `isLiveContent`. `isLive` now means
    live right now. `durationSeconds` is null for live-now and upcoming streams, and for a zero
    length. `liveStatus` was appended to `ExcorMetadata`, a type added after BRIEF section 3, so
    code constructing it must pass the new field.
33. **YouTube Music track info.** `ExcorMetadata.music` (track, artists, album, release year and
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
    (opt-in, `EXCOR_CAPTURE=1`) sends the logged-out requests, redacts and checks before writing,
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
    `ExcorMerge.merge` (Android only) copies the encoded samples into one file with Android's
    `MediaExtractor` and `MediaMuxer`, interleaved by time, so nothing is re-encoded. It refuses
    other codec pairs and leaves no partial file. Left out for now: AV1 (whether Android's MP4 muxer
    takes it on every supported Android version is unchecked), HDR VP9 (`vp09.02…`; its colour
    metadata would need carrying over) and iOS (no Apple devices, item 34; AVFoundation also writes
    no WebM). Android 8 and 9 cannot merge WebM: their WebM writer takes VP8/VP9 with Vorbis only,
    and Opus arrives in Android 10 (AOSP `WebmWriter.cpp` at `android-8.0.0_r1`, `9.0.0_r1`,
    `10.0.0_r1`). YouTube offers no Vorbis, so those devices merge MP4, up to 1080p;
    `ExcorMerge.supports(container)` says which, and `merge` refuses up front. Checked in CI by
    `MergeCheck`, a plain `Instrumentation` (androidx.test's runner needs JUnit 4, EPL-1.0), on API
    26 and API 36 emulators. It merges synthetic two-second clips laid out the way YouTube serves
    its formats (fragmented MP4, WebM) and compares every track's sample count. Its first run on
    2026-10-27 merged both on API 36 (MP4: 60 video and 88 audio samples; WebM: 60 and 101); on
    API 26 a merge failed in `MediaMuxer.addTrack` without saying which; Android 8's missing Opus
    explains it. With `supports` in place, API 26 merged MP4 with every sample (60 and 88) and
    refused WebM as intended (PR #18). A real 4K download has not been merged yet. That needs the owner's network and device.
    Merging needs free space for both inputs again until they are deleted. Dubbed audio tracks:
    item 45.
39. **Why a video won't play.** `ExcorUnplayableException` (still an `IllegalStateException`,
    same message) carries `reason`, YouTube's `status` and `youtubeReason`. The sorting follows
    yt-dlp: age gate (`desktopLegacyAgeGateReason` or its age phrases), country block (the
    `errorScreen` subreason), `LIVE_STREAM_OFFLINE` as upcoming, `ERROR` as unavailable, "sign in"
    as sign-in required, everything else other. The five captured refusals from the owner's
    network sort as age-restricted, unavailable, upcoming and other (twice: an ended live stream
    without a recording, a copyright block). The country block and sign-in cases are checked
    against response shapes taken from yt-dlp's code, not captures. The iOS `extract` now
    rethrows the refusal when the watch-page fallback finds nothing too, instead of returning null.
40. **Playlists.** `playlist(playlistId)` asks the logged-out WEB `browse` endpoint for `VL` + the
    ID and returns the title, description, owner and the first page of videos (about 100);
    `playlistMore(continuation)` returns the next page. Each video is a `lockupViewModel`, read as
    yt-dlp's `_extract_lockup_view_model` reads it: title, channel (the metadata part linking to a
    channel; for collaborations, the first channel in the avatar stack's dialog, which can have no
    channel ID), duration from the thumbnail badge, thumbnails. The owner comes from the sidebar, as
    the header's "by …" is localized. No cookie is sent, so only public lists are seen (account
    features come later, item 36). An album's playlist (`OLAK5uy_…`) works the same way; mixes
    (`RD…`) are refused here, as they need the watch-next endpoint. The first page carries two
    continuation tokens: one closing the item section and one as a section of its own. The item
    section's is used; it is the one that fetched the captured second page. Only `lockupViewModel`
    items are read; YouTube's older `playlistVideoRenderer` did not appear in the captures. Checked
    against Codex's captures: 100 + 100 videos of a 1,720-video playlist, and a 50-track album
    playlist with no owner and no next page.
41. **Working without Codex (owner, 2026-10-27).** Claude Code works on the project alone from now
    on. What Codex supplied from the owner's network (live surveys and captures) is no longer
    available: from the cloud build machine YouTube answers with a bot check (the multi-audio
    capture stopped there on 2026-10-27, as designed). Later captures and live checks need the
    owner's own run, or they are left out and said so. The dubbed-audio fix (item 38) will be built
    from yt-dlp's field names and marked as unchecked against a capture.
42. **YouTube Music albums and artists.** `album(MPREb_…)` and `artist(UC…)` ask the logged-out
    `WEB_REMIX` client (yt-dlp's `web_music`) on music.youtube.com. An album gives title, artist and
    channel (from the header's strapline), year (the four-digit part of "Album • 2015", whatever the
    language), cover and tracks (yt-dlp's `_music_reponsive_list_entry`: `playlistItemData.videoId`,
    the first column's title; rows without their own credits are the album artist's). Its
    `playlistId` comes from the page's canonical URL, which is how yt-dlp resolves an album to its
    playlist; in the captures the album's 50 tracks equal its playlist's 50 videos, in order. An
    artist gives name, description, pictures, the top songs, `songsPlaylistId` and
    `videosPlaylistId` (the song shelf's and the video shelf's links to playlists of everything),
    and the releases (albums, singles, EPs; page type `MUSIC_PAGE_TYPE_ALBUM`), up to the 10 per
    shelf the page shows. The "more" pages of releases (`MPAD…` browse IDs) were not captured, so
    an artist with more than ten albums shows ten; their songs are all in `songsPlaylistId`, which
    is what "download an artist" uses. Shelves are found by what they link to, never by their
    localized titles. `parse` now reads channel links (`/channel/UC…`, YouTube Music's
    `/browse/UC…`, a bare `UC…` ID) as `ExcorRef.Channel`; `@handle` links still give null, as
    resolving a handle takes a request.
43. **Mixes and related videos ("recommendations", logged out).** Both come from the WEB `next`
    (watch-next) endpoint. `mix(RD…)` reads the mix panel's videos (`playlistPanelVideoRenderer`,
    as yt-dlp's `_extract_video`: title, short byline for the channel, length text). A video's own
    mix (`RD` + its 11-character ID) starts from that video; other mix kinds (`RDCLAK…`, `RDMM`)
    are asked by playlist ID alone, which no capture covers. Mixes do not end: as in yt-dlp's
    `_extract_inline_playlist`, the next call names the last video's place (playlist, video, index,
    params) and keeps only the videos after it; when none are new, the continuation is null.
    `mixMore` takes that place as an opaque continuation string. `related(videoId)` returns the
    20 videos beside the player (the same `lockupViewModel` items as playlists) and a token;
    `relatedMore` reads the next page from `onResponseReceivedEndpoints`, where yt-dlp finds it.
    That response shape was not captured; the test wraps the captured items in it. These are
    YouTube's logged-out recommendations, not personal ones (account features come later).
44. **Search (logged out).** `search(query)` asks the WEB `search` endpoint, as yt-dlp's
    `_search_results` does without filters, and returns its videos (`videoRenderer`, read like mix
    items by yt-dlp's `_extract_video`, or `lockupViewModel`) and playlists (`lockupViewModel` of
    type playlist), in the order shown; `searchMore` reads the next page from
    `onResponseReceivedCommands`, where yt-dlp finds it (not captured; the test wraps the captured
    sections in that shape). Shelves ("Latest from …"), Shorts grids and the channel card are left
    out. `searchMusic(query)` asks YouTube Music's `search` with `WEB_REMIX` and returns the top
    result card and the result rows: songs and videos (`playlistItemData.videoId`), albums, artists
    and playlists (the browse endpoint's page type); profiles and podcasts are left out. It is one
    page: YouTube Music's top results carry no continuation, and the filtered tabs ("Songs",
    "Albums", …) take `params` that were not captured. Results are classified by what they link to,
    never by the localized "Song • …" labels. In the captures: 16 videos and 3 playlists on the
    YouTube page; 15 songs and videos, 6 playlists, 3 artists and 3 albums on YouTube Music. Search
    suggestions while typing are not built; they use another endpoint that was not captured.
45. **Dubbed audio: the original track.** A video with dubbed tracks lists each audio format once
    per track, with an `audioTrack` (`id`, `displayName`, `audioIsDefault`). `audioFormatsOf` now
    keeps only the best-ranked track's formats, ranked as yt-dlp's `get_language_code_and_preference`
    does: "descriptive" last, "original" in the display name first, then YouTube's default. So
    `extract`, `extractVideo`, `formats` and the merge pairs all play the original. The display name
    comes in the session's language, where "original" may be another word; YouTube's default track
    then decides, which may be a dub in the listener's language. Other languages are not offered
    yet. Unchecked against a capture: the multi-audio capture hit a bot check from the build
    machine (item 41), so the tests add the fields yt-dlp reads to the captured formats.
46. **App, milestone A1: `app-core` (owner, 2026-10-27: the app starts now, in this repository).**
    A Kotlin Multiplatform subproject next to the library (the root project), for the Android app
    first and an iOS app later. `Queue` is immutable: play order, the current track, repeat (off,
    all, one). Shuffle reorders what comes after the current track, once. "Next" on repeat-one
    moves on; only a track that ends repeats. `Playback` looks a track's stream up when the track
    becomes current, since stream URLs expire, and the next track's while the current one plays.
    A stream within 60 seconds of its `expire` time is looked up again, also when repeating. Each
    `Ready` is one start of the track, so a repeat with the same stream is still a new value of
    `state` (`Ready` is not a data class; a `StateFlow` drops a value equal to the last). When
    the platform player is refused a URL (HTTP 403), Excor's `onRefused` is told and the track is
    looked up once more; a second refusal ends in `NO_STREAM`. YouTube's refusals become the
    listener's terms (`Failure`): sign-in, age, unavailable, not yet live, not in this country,
    blocked, and `BOT_CHECK` for "Sign in to confirm you're not a bot" (matched in English only).
    After any failure nothing is fetched ahead and nothing is retried on its own. A failed track is
    not skipped automatically; the app decides what to show. `StreamSource` is the slice of Excor
    it needs, because `Excor` itself is a per-platform class; tests use a fake. 13 tests run on the
    JVM and, in CI, on the iOS simulator. The deprecated `androidLibrary {}` block in both builds is
    now `android {}`, as AGP 9.4 asks.
47. **App, milestone A2: the Android shell.** `android-app/` (Compose, Media3 1.11), minimum Android
    8.0. One screen: a link field (also filled by "Share" from another app), the opened page's
    title, byline and tracks, "Play all", and a now-playing line with previous, play/pause and next
    that also says why a track did not play. A video, a playlist or mix (first page), an album or an
    artist (top songs) opens; paging, search, downloads and a player screen are later milestones.
    `PlaybackService` (a `MediaSessionService`) holds only the current track; the `app-core` queue
    decides what comes next, and the notification's and a headset's next and previous go to it.
    Audio is `extract(videoId, maxKbps = 160)`, fetched with the stream's own headers; a 403 from the
    player goes to `Playback.refused`. Logged out, in the phone's language and region. When Android
    stops the service while paused and it is created again, the current track is set but not
    fetched until play is pressed, so a URL that expired meanwhile cannot start playback on its own
    (the 403 retry would). Shared text keeps its first web link, since some apps add words around
    it. Media3 marks the forwarding player, the progressive source and the data spec as APIs that
    may still change, so a Media3 upgrade may need edits. The service is exported, as media sessions are, so the
    system's controls, headsets and car screens can connect; any app on the phone can then pause or
    skip, as with other music players. No notification permission is asked for: Android 13 exempts
    media session notifications. Release signing, shrinking and an open-source licenses screen come
    with the release milestone. CI lints the app, starts it on the API 26 and 36 emulators (no
    crash, the media service up) and uploads the debug APK. It has not played a track
    anywhere yet: this machine has no KVM for an emulator, and the CI emulators open no link because
    YouTube asks the build machines to sign in (item 41). The Gradle daemon now gets 3 GB
    (`gradle.properties`); merging the app's dex files ran out of the default 512 MB.
48. **App: downloads.** A "Download" on each track and "Download all" on a page open a dialog: audio
    or video, then a quality. For one track the list is what YouTube offers for it (asked when the
    dialog opens): every audio format that can be saved ("Opus · 140 kbps · 3 MB") and every height
    that merges into one file ("1080p60 · MP4 · 85 MB"). For many tracks it is "best audio" or "best
    up to" a height, applied to each. MP4 (H.264 and AAC) wins over WebM at the same height because
    it plays everywhere; above 1080p YouTube has only VP9, so those are WebM. Android 8 and 9 cannot
    write Opus (into WebM or Ogg), so they get AAC and MP4 only, up to 1080p. A height YouTube offers
    only in AV1 (8K, for example) is left out, since nothing merges it without re-encoding. Audio is
    rewritten without re-encoding into a plain .m4a or an Ogg .opus (the new `ExcorMerge.remuxAudio`),
    because YouTube's fragmented MP4 and WebM files confuse many music players. Files go to
    Music/Excor and Movies/Excor through MediaStore (Android 10 and later, no permission) or the
    shared folders with the storage permission (Android 8 and 9). A foreground service of the data
    sync type runs the queue, one track at a time, with progress in a notification; the notification
    permission is asked for on Android 13 and later, and downloads run without it. Each track's
    formats are asked for when its turn comes, as URLs expire. A dropped connection resumes from the
    bytes already written, twice. The queue lives in memory: if Android ends the app, unfinished
    downloads are gone. Android 15 and later allow data sync work six hours a day; past that the rest
    of the queue is marked as stopped. The choice logic is in `app-core` (`Download.kt`, tested on
    the JVM and the iOS simulator); `ExcorFormats.mergePair` makes the library's pair selection usable
    from shared code. No download has run against YouTube yet: the build machines get a bot check.
49. **Stream probe: only a ranged media answer counts (owner's comparison report, 2026-10-27).**
    The one-byte probe accepted any 2xx status and read the whole answer, so a server that ignored
    the range would have sent an entire media file into memory, and an error page served as a
    success would have passed. It now requires a 206 whose `Content-Range` is `bytes 0-0/<length>`
    with a length above zero, turns away HTML, JSON and text answers, and streams the answer
    without reading its body. Other content types still count, so an unexpected but real media
    type is not refused. googlevideo's answer shape (206, `Content-Range`, a media type) is standard
    HTTP; it has not been re-checked live since the change, as the build machines get a bot check.
50. **More client identities, a logged-in order, client health (owner's comparison report P0,
    2026-10-27).** The client profiles moved from the engine into `Clients.kt`, as data, so a
    change on YouTube's side means editing a table. Logged in, the order is now yt-dlp's own
    (`web_embedded`, `tv_samsung`, `web`): the embedded player takes cookies, needs no PO token, and
    says which page embeds it (`thirdParty.embedUrl`, any non-YouTube page per yt-dlp; its value is
    used). Logged out it stays `tv_samsung`, `visionos`, `web`. Both then try `tv` (the Cobalt TV
    client) and `tv_downgraded`, which yt-dlp defines but does not try by default; they need no PO
    token, so they cost a request only when the others failed. Clients that need a PO token for
    their media (`mweb`, `ios`, `android`, `android_vr`, `web_safari`, `web_music`, `web_creator`,
    `tv_simply`) are not added: Excor only gets tokens from the host, and a token minted for WEB is
    not known to serve those clients. A client refused three times in a row across videos (refused
    media, or a failed request; never a refusal of one video, such as an age gate) is tried after
    the others for 30 minutes; a success or a new session clears it. On the owner's network, where
    YouTube refuses TV_SAMSUNG (item 2), this saves a player request and a probe per video. The
    new clients are untested live: the build machines get a bot check.
51. **Live streams: HLS and DASH manifests (owner, 2026-10-27: "add an engine for DASH / HLS").**
    `manifest(videoId)` returns the manifest a client offers, as yt-dlp reads it: HLS when there is
    one (yt-dlp skips DASH while live), else DASH; the `/n/` challenge in its path solved with the
    player; a PO token added as a `/pot/` segment where the client needs one; only
    `https://*.googlevideo.com/api/manifest/…` URLs; and the manifest fetched to see that it is one
    (an M3U8 or an MPD in its first kilobyte), else the client is excluded as for a refused stream.
    Players read manifests themselves, so Excor does not parse them: the app plays a live stream
    through Media3's HLS or DASH source when `extract` finds no single file. The VISIONOS capture
    of an ordinary video already carries an `hls_variant` manifest (demuxed audio), so `manifest`
    works for ordinary videos too. No live stream could be captured (bot check on the build
    machines); the live tests give the VISIONOS capture the fields yt-dlp reads. Not yet: downloading
    a manifest's segments (recording), DASH's own manifests for post-live recordings beyond what
    players do, and SABR. While looking, the fixture check learned that manifest URLs carry the
    requester's address as an `/ip/` path segment; it now redacts those as it does `ip=` (the
    existing capture's is a private address).
52. **Captions and chapters (owner's comparison report P1).** Caption tracks now say whether YouTube
    translates them (`isTranslatable`), the metadata lists the languages it translates into (only
    WEB reports them; 156 in the capture), `urlFor(format, translateTo)` gives a track in each of
    YouTube's formats (VTT, SRT, TTML, JSON3, SRV1-3), and `captionText` fetches it. As yt-dlp:
    `xosf` is dropped (it spoils VTT and JSON positions) and "translating" into the track's own
    language is not asked for (YouTube damages it). Tracks that need a PO token (the `xpe`/`xpv`
    experiment) are still left out, as there is no caption token. `chapters(videoId)` reads the
    watch-next response's player bar, else its chapters panel, else timestamps in the description
    (time then title, or title then time), keeping chapters that start in order within the video's
    duration; it asks the player for that duration, so it costs two requests. No watch-next
    response with chapters was captured, so those tests use the shapes yt-dlp reads.
53. **Comments (owner's comparison report P2).** `comments(videoId, newestFirst)` and
    `commentsMore(continuation)` read public comments logged out, as yt-dlp does: the watch-next
    response names the comment section, whose first page holds the threads; each thread points by
    key at entity payloads holding the text, author, counts and heart; a thread's replies and the
    next page come from continuations. Newest-first takes the header's second sort entry, by place,
    not by its label. Counts are read from YouTube's English wording ("220K"), so other interface
    languages may give none. The captures come from the cloud build machine, which `next` answers
    (only the player gets a bot check). Comment authors are people, so the capture tool replaces
    their channel IDs, handles and pictures with numbered placeholders before writing; the texts
    stay. Community posts are not read yet.
54. **Channels (owner's comparison report P2).** `channel(UC…)` reads a channel's page logged out
    (title, handle, description, avatars, banners, and which of its Videos, Shorts, Live and
    Playlists tabs it has); `channelTab(channelId, tab)` and `channelTabMore(continuation)` list a
    tab, as yt-dlp's tab extractor does. A tab is asked for with the `params` its channel page
    gives, found by the tab's URL rather than its localized title, and kept per channel, so the
    first tab of a channel costs one extra request. Videos and live streams are video lockups,
    Shorts `shortsLockupViewModel`s (title from the overlay, else from their English accessibility
    label), playlists playlist lockups. Captured from the cloud build machine, which `browse`
    answers. The channel's Home, Releases, Posts and Search tabs are not read.
55. **Reliability benchmark (owner's comparison report; BENCHMARKS.md).** `BenchmarkTest`, opt-in,
    runs a fixed set of videos by kind (ordinary, Short, YouTube Music, 4K 60 fps, dubbed audio,
    24/7 live, and three refusals) and records per video the outcome, the identity that served it,
    the time, the network calls, and whether the media answers a 1 KiB ranged GET with exactly 1 KiB
    (live streams: whether the manifest answers). Bot checks are named and make a run not count.
    No result is recorded yet: it has to run on a normal network. A trial from the build machine
    served the first video and then got bot checks. It never runs other extractors; comparing is a
    separate step (BENCHMARKS.md).
56. **Search filters, channels, Shorts, suggestions (owner's comparison report P2).** `search(query,
    type)` asks for one type (videos, Shorts, channels, playlists, movies) with the params YouTube's
    own search page sends for its "Type" filters; every first page also carries YouTube's full list
    of filters (type, duration, upload date, features, sort order) in the session's language, and
    `search(query, filter)` applies one on top of those already in use, as YouTube's page does (its
    response gives each filter's params for the combination; selected and unavailable ones have
    none). Channels are now results (`CHANNEL`). Shorts come in `shorts`, apart from the results,
    because YouTube sends them in grids (65 of them, in three grids, for an unfiltered "rick
    astley"); mixing them in would push the results down. `suggestions(query)` asks the host and
    parameters YouTube's own search box uses, in UTF-8 (`ie`/`oe`; without them Japanese comes back
    in Shift_JIS). Not done: YouTube Music sections (songs, albums, …). With the params from
    YouTube Music's own response, and with yt-dlp's older ones, it answered "No results" from the
    build machine for every section, so nothing would test them; they wait for a capture from a
    normal network.
57. **SoundCloud (owner, 2026-10-27: "Other sites, no DRM", SoundCloud first).** `excor.soundCloud`
    reads public tracks and sets logged out, as yt-dlp's extractor does: the web client's ID from
    SoundCloud's own scripts on its home page (only `a-v2.sndcdn.com` assets are fetched; the ID is
    kept in memory and, when the host allows it, in the config store; a 401 or 403 looks it up again
    once), `resolve` for a link, `tracks?ids=` for a set's tracks that come without details, and a
    transcoding's own URL for a fresh signed stream (accepted only from SoundCloud's media hosts).
    Encrypted transcodings (`ctr-`, `cbc-`, `encrypted-hls`) are DRM: they are left out and the
    track says `hasDrm`; nothing tries to decrypt them. In the captured set, three of six tracks are
    only offered encrypted. Public tracks currently come as HLS (fragmented MP4 AAC), which the app's
    player plays. `soundCloud.download` writes one file: a progressive stream as it comes, an HLS one
    as its init segment and segments in order (one fragmented MP4, an `.m4a`), every URL checked to be
    on SoundCloud's media hosts. The HLS reader takes only a finished media playlist and refuses by
    name a master playlist, a live one, encrypted segments (any `#EXT-X-KEY` but `METHOD=NONE`) and
    byte ranges. Live from the build machine on 2026-10-28, yt-dlp's test track (143 s, `aac_96k`)
    came out as 1,747,394 bytes: `ftyp`, `moov`, then 15 fragments. There is no resuming (a failure
    starts again). Logged-in (Go+) playback, comments, user pages, search and the app's SoundCloud
    screen were not done at first. Since 2026-10-28 the app opens SoundCloud links (a track or a set),
    plays them (a fresh stream per play, HLS through the player's HLS source) and downloads them: the
    best transcoding (a whole track over a preview, then AAC over MP3 over Opus, then the bitrate),
    HLS joined and AAC remuxed into a plain `.m4a`. `MergeCheck` proves that remux on the emulators with
    a synthetic clip laid out as SoundCloud's joined segments. A track only offered encrypted fails
    with its own message ("only offers this one encrypted (DRM)") instead of a generic error.
58. **SABR from yt-dlp's development work (owner: "yt-dlp dev work").** yt-dlp's SABR downloader is
    in its pull request 13515 (not merged, updated 2026-10-27): `downloader/sabr/`, protobuf
    messages under `extractor/youtube/_proto/`, and `_streaming/sabr/`; the 2026.10.26 release has
    none of it. Before porting, its origin has to be clear, because LuanRT/googlevideo, a SABR
    library, is on this project's forbidden list. The code and commit messages credit no other
    project, but the pull request's description and discussion, where such a credit would usually
    be, cannot be read from the build machine (only git is open to it, not GitHub's pages or API).
    Nothing was ported. Needed from the owner: a look at that pull request's description for any
    mention of googlevideo or YouTube.js as a source, and a decision. SABR also cannot be tried from
    the build machine (YouTube bot-checks its player requests), and WEB's SABR formats need a PO
    token, which Excor only takes from the host.
59. **Bandcamp (owner: SoundCloud, then Bandcamp).** Bandcamp answers the cloud build machine with a
    "Client Challenge" page for every track and album page (2026-10-28). That is a bot check: yt-dlp
    retries it with browser impersonation, which this project does not do, so nothing was worked
    around and no Bandcamp code was written blind. `CaptureFixtures` now asks for yt-dlp's test track
    and album pages (`bandcamp-track.html`, `bandcamp-album.html`), stops at a challenge, and redacts
    the signed stream URLs (`bcbits.com/stream/…token=…`) before writing; `FixtureHygieneTest` fails
    on any left in. Needed: a run of `EXCOR_CAPTURE=1 EXCOR_CAPTURE_ONLY=bandcamp-track.html,bandcamp-album.html`
    from a normal network; the parser (yt-dlp's `data-tralbum` reading) follows on those captures.
60. **Audio language for dubbed videos (owner's comparison report P1).** `ExcorMetadata.audioTracks`
    lists a video's audio tracks (language code, YouTube's name, original, default, descriptive), and
    `preferAudioLanguage(code)` makes `extract`, `extractVideo`, `formats` and the watch-page fallback
    take that language's track. The descriptive track is never taken for a language, and a video
    without the language keeps its original track (item 45). It is a preference on the Excor object
    rather than a new argument, so the Swift API keeps its signatures. Read as yt-dlp's
    `get_language_code_and_preference` reads it (the `audioTrack.id` before the dot). Unchecked against
    a capture: no dubbed video's response could be captured from the build machine (item 41).
61. **YouTube HLS variants (for recording live streams).** Not built: it needs a real master playlist,
    and from the build machine VISIONOS answers the 24/7 live stream with "This live stream recording is
    not available." while other clients' player requests get bot checks (2026-10-28). The HLS reader
    of item 57 refuses master and live playlists by name in the meantime.
62. **Community posts (owner's comparison report P2; item 54 left them out).** `channelPosts(UC…)`
    and `channelPostsMore(continuation)` read a channel's Posts tab logged out: each post's text (link
    texts included), where its links go (YouTube's redirect taken off; a video link as its watch URL),
    YouTube's "1 month ago", its like count (from the "4.5K likes" label), its images at their largest,
    and an attached video or playlist, as yt-dlp's `_post_thread_entries` reads posts. The tab is found
    by its URL ("…/posts") among the params the channel page gives, as the other tabs are, so it needs
    no public tab value of its own. The next page comes under `onResponseReceivedEndpoints`. Captured
    from the build machine, which `browse` answers. Polls, quizzes and a post's comments are not read.
63. **Apple Music metadata (owner, 2026-10-28: "Apple Music metadata fetching", "Only metadata", "Image
    metadata?").** `excor.appleMusic` reads songs, albums (with their songs, in disc and track order),
    artists (with the albums they are on, also under other artists' names, as Apple lists them) and
    song and album search from Apple's documented iTunes Search API (`itunes.apple.com/lookup` and
    `/search`; no key, no account), and parses `music.apple.com` song, album and artist links.
    Metadata only, as asked: song previews (`previewUrl`, 30-second audio) are not read, and the capture
    tool replaces them before writing (`FixtureHygieneTest` refuses any left in). Images: the cover
    comes at 100 px, as the documentation gives it, and at 600, 1200 and 3000 px, made by changing the
    size in its URL as Apple's image server serves them; checked from the build machine on 2026-10-28
    (each answered a JPEG of that size), but not documented by Apple, so a size can stop answering.
    Artists have no picture in this API (owner, 2026-10-28: "Artists images?"): `artistImages(id)`
    takes it from the artist's own Apple Music page, its share image (`og:image`, 1200 × 630 as the page
    states), and makes 600, 1200 and 3000 px squares from the same URL, each checked to answer a JPEG
    of that size on 2026-10-28. Only the page's `<head>` is read (about 6 KB of 570 KB), only an image
    on Apple's image server (`is*-ssl.mzstatic.com`) is taken, and an unknown artist (404) has none.
    The capture keeps only the head, and the hygiene test now refuses any signed token (JWT) in a
    fixture, as web players can embed one. Apple's playlist links (`pl.…`) are not in it. Apple allows
    about 20 calls a minute; a refusal throws with its HTTP status. Its terms say promotional content
    (previews, album art) is only to promote store content: what an app shows or saves is the app's
    call, not a clearance given here.
