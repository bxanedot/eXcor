# eXcor architecture

## Build boundary

The root Gradle project publishes the draft KMP library from `modules/cleanroom/`. Its public Kotlin API is in `com.excor`. The preserved `modules/legacy/src/` implementation is not compiled at all. The preview apps (`preview-android`, `preview-desktop`) are separate unpublished modules under `modules/platforms/preview/` that use the library.

## Repository layout

- `api/` holds the Kotlin ABI validation baseline used by Gradle.

- `modules/cleanroom/` is the active clean-room Kotlin Multiplatform source tree selected by Gradle.
- `modules/legacy/src/` and `modules/legacy/ejs/` retain the old, uncompiled implementation and its bundled files.
- `modules/platforms/preview/` contains the Android and desktop preview apps; their Gradle project IDs remain `:preview-android` and `:preview-desktop`.
- `modules/platforms/swift/` contains the separate native Swift package.
- `docs/maintainers/` keeps the source brief, open questions, benchmark notes, and dated handoff records out of the repository root.

## Request path

1. `Excor` delegates lifecycle and extraction to the common `ExcorEngine`.
2. The host supplies the current session, HTTP client, clock, logging, cache settings, and optional `TokenProvider`.
3. The engine tries a small, ordered set of YouTube client identities, respecting explicit skips, token requirements and temporary exclusions.
4. Player responses are decoded into bounded models. The QuickJS-based player adapter resolves the signature and throttling challenges using the allowed bundled EJS solver.
5. Audio selection applies the documented codec, bitrate, channel and sample-rate ordering.
6. Direct-media `UrlPolicy` accepts only HTTPS port 443 `/videoplayback` URLs. Experimental HLS manifest handoff uses a separate GoogleVideo allowlist; SABR/UMP playback remains unsupported.
7. The stream carries request headers required by the host. Remembered URL/header pairs support `headersFor` and `onRefused` without exposing values in logs.

## Platform responsibilities

- **JVM / Android host:** Ktor OkHttp transport based on the host OkHttp client, bounded timeouts, and an optional atomic player-data cache under the host files directory.
- **iOS:** Ktor Darwin transport and in-memory player-data cache. Watch-page fallback behavior is implemented for the iOS path.
- **Host application:** owns authentication, session acquisition, PO-token generation if authorized, media playback, media storage, retries and user-facing errors.

## Data and security boundaries

Cookies, visitor data, PO tokens, signed URLs and authorization headers are sensitive. They are not written to logs or string representations. Responses are bounded and malformed player data fails closed. Cancellation propagates through coroutine and JavaScript execution boundaries where supported.

The SDK returns transport information; it does not embed a player or download media. Search, catalog, video, download and non-YouTube provider modules are outside this API.