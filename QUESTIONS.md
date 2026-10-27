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
   Logged-in sessions remain untested live.

## Readings of the brief

3. **ISC inside the solver.** The vendored ejs `lib.min.js` bundles meriyah (ISC). Section 2
   lists Apache/MIT/BSD for dependencies; section 1 accepts ISC for companion repositories.
   Kept, since ISC is equivalent to MIT/BSD-2. Confirm.
4. **Fixtures in a public Apache-2.0 repo.** `fixtures/` holds YouTube's own player script,
   HTML and JSON. They are marked as not covered by the license and are never published.
   Alternative: download them at test time. Confirm.
5. **`VyonExHost` defaults.** `tokenProvider` and `filesDir` default to `null`, so hosts
   compile whether or not they override them. `httpClient` has no default.
6. **`configStore`.** Only `enabled` is used (warm-up on `onSessionChanged`). Nothing small is
   worth persisting there: the JVM disk cache holds the prepared player, and iOS must keep
   caches in memory. Should `enabled = false` also turn off the JVM disk cache?
7. **`putString` value** is taken as non-null `String`.
8. **Logged-in identities.** Cookies go with every YouTube request when present; logged-in
   sessions (LOGIN_INFO + SAPISID, as yt-dlp) also send SAPISIDHASH and skip VISIONOS.
   yt-dlp's logged-in default starts with `web_embedded`; it is left out because it answered
   "This video is unavailable" live without the embed page's `encryptedHostFlags`, and no
   account was available to test logged-in behavior.
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
16. **Warm-up cost.** Preparing the 2.9 MB player takes about 6.5 s in QuickJS on the JVM
    build machine. On iOS, with caches in memory only, that happens once per app launch and
    player version (during warm-up). Acceptable?
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
