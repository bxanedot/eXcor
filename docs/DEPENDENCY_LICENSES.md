# Dependency licenses

This table covers dependencies declared by the clean-room Kotlin build at the audited commit. Verify resolved versions and licenses again before release; this is not a substitute for the full dependency graph or legal review.

| Dependency | Version | License | Use |
|---|---:|---|---|
| Kotlin / Kotlin Gradle plugin | 2.4.21 | Apache-2.0 | Language, KMP compiler, tests |
| Gradle wrapper | 9.8.2 | Apache-2.0 | Build |
| Android Kotlin Multiplatform Gradle plugin | 9.4.1 | Apache-2.0 | Android target |
| Ktor client core, OkHttp, Darwin, Mock | 3.6.0 | Apache-2.0 | HTTP transport and tests |
| kotlinx.coroutines core and test | 1.11.0 | Apache-2.0 | Structured concurrency and tests |
| kotlinx.serialization JSON | 1.12.0 | Apache-2.0 | Player response and cache data |
| QuickJS-KT | 1.0.15 | Apache-2.0 wrapper; bundled runtime notices apply | Player challenge execution |
| yt-dlp/ejs solver bundles | commit `d48c6303a5a6455a42ef9e0b445149cadea51279` | Unlicense | JavaScript challenge solver |
| meriyah bundled by ejs | 6.1.4 | ISC | JavaScript parser bundled in solver |
| astring bundled by ejs | 1.9.0 | MIT | JavaScript code generator bundled in solver |
| TestNG | 7.0.0 | Apache-2.0 | JVM tests |

Ktor, Kotlin and test dependencies bring transitive artifacts; use Gradle dependency reports for the exact resolved graph on each target. See [`NOTICE`](../NOTICE) for bundled third-party text and [PROVENANCE.md](../PROVENANCE.md) for source records. The separate Swift package has no third-party package dependencies and is documented in its follow-up change.
