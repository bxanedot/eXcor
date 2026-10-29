# eXcor feature matrix

This matrix describes the clean-room API in `cleanroom-src/`, not functionality that may exist in preserved legacy folders.

| Capability | eXcor status | Platforms | Evidence / gap |
|---|---|---|---|
| Resolve audio from a YouTube video ID | Implemented | JVM; iOS source configured | `Excor.extract`; offline parser and state tests. Live cloud access is unreliable. |
| Select M4A, low-bitrate or automatic audio | Implemented | Common | `SelectionTest` covers ranking and the 64 kbps threshold. |
| Decode player signature and `n` challenges | Implemented | Common QuickJS runtime | Synthetic challenge test; live capture tests are blocked by cloud bot checks. |
| Restrict returned playback URLs | Implemented | Common | Exact HTTPS/host/port/path policy in `UrlPolicyTest`. |
| Session and PO-token integration | Host interface implemented | JVM and iOS | Host supplies session and optional tokens; eXcor does not mint tokens. |
| Remember stream headers and handle refusal/exclusion | Implemented | Common | Engine state tests; platform integration remains under test. |
| Persistent player cache | Implemented | JVM | Atomic cache and bounded entry tests. |
| In-memory player cache | Implemented | iOS | Native CI verification is currently failing on macOS. |
| Android library compilation | Configured | Android | CI task is configured; the app module is not integrated with this API. |
| Search, playlists, channels, albums and catalog metadata | Not in this clean-room API | — | No contract in the renamed API for these features. |
| Video formats, downloads, remuxing and playback | Not in this clean-room API | — | Deliberately excluded from this draft SDK. |
| SoundCloud, Bandcamp, PeerTube or other providers | Not in this clean-room API | — | Provider work is future scope, subject to the authorization and design gates in the master plan. |
| Native Swift eXcorKit | Follow-up branch | iOS, macOS | Separate deliverable; absent from the Kotlin PR branch. |

“Implemented” does not mean production-ready. A platform is supported only after its CI build and tests pass.
