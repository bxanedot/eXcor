# Open questions

Each item names the reading chosen; nothing was looked up in the old code.

## Blocked by YouTube from the cloud build machine

1. **Watch page.** `GET https://www.youtube.com/watch?v=…` redirected to Google's bot check
   (`fixtures/watch-page-blocked.html`). I did not try to get around it. The watch-page
   parsing fixture is therefore assembled from captured parts (`fixtures/README.md`). Please
   recapture `fixtures/watch-page-assembled.html` from a normal network.
2. **Live test.** `VYONEX_LIVE=1` passed for `dQw4w9WgXcQ` (TV_SAMSUNG, signature and `n`
   decoded, ranged GET 206). `kJQP7kiw5Fk` then got "This video is not available" from both
   TVHTML5 and VISIONOS, and afterwards every request from this IP got
   `LOGIN_REQUIRED: Sign in to confirm you're not a bot`. The live test needs a run from a
   normal network; CI does not run it.

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

18. **Platforms.** Only iOS 15 and macOS 12. watchOS has no JavaScriptCore, and tvOS/visionOS
    could not be compiled from the Linux build machine, so they are not declared.
19. **Timeouts.** `URLSession` has no connect timeout: the dedicated session uses
    `timeoutIntervalForRequest` 20 s (socket) and `timeoutIntervalForResource` 30 s (request).
    It also ignores stored cookies, so only the host session's cookie is sent.
20. **API spelling.** `VyonExConfigStore` uses `getString(_:)`, `putString(_:_:)` and so on;
    `VyonExKitHost.now` is a `Date`; `TokenProviderCapabilities` is added because
    `PoTokenProvider.capabilities` needs a type. `VyonExKit` is a `final class` (Sendable, lock
    inside) rather than an actor, so `headers(for:)`, `onRefused(url:)`, `exclude` and
    `onSessionChanged` stay synchronous like the Kotlin API.
21. **`extract` throws only on cancellation**, like the iOS Kotlin version: a player-API failure
    falls through to the watch page, and anything else returns nil.
22. **No disk cache** in VyonExKit (optional in the brief): the player is prepared again once per
    app launch and player version, during warm-up.
