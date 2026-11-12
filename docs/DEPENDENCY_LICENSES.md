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
| TestNG | 7.5.1 | Apache-2.0 | JVM tests |

Ktor, Kotlin and test dependencies bring transitive artifacts; use Gradle dependency reports for the exact resolved graph on each target. See [`NOTICE`](../NOTICE) for bundled third-party text and [PROVENANCE.md](../PROVENANCE.md) for source records. The separate Swift package has no third-party package dependencies and is documented in its follow-up change.

## CI vulnerability scan snapshot — 2026-11-12

The all-configuration OSV scan in [CI run 43123548640](https://github.com/bxanedot/eXcor/actions/runs/43123548640) reported eight advisory records among 237 locked packages. Its separate shipped-runtime scan covered 115 packages and reported no issues. A Gradle dependency-lock report generated from main commit `e318753cd37b66699d11157dffa101d4e18a6b0d`, with locking enabled for all configurations, maps the affected artifacts to build-tool configurations that the shipped-runtime scan excludes:

| OSV advisory | Package and version | Gradle configuration |
|---|---|---|
| [GHSA-xxph-c9ww-hj94](https://osv.dev/vulnerability/GHSA-xxph-c9ww-hj94) | `com.google.guava:guava` 33.4.0-jre | `androidLintTool` |
| [GHSA-ccrj-gw8r-854m](https://osv.dev/vulnerability/GHSA-ccrj-gw8r-854m) | `io.opentelemetry:opentelemetry-api` 1.41.0 | `swiftExportClasspathResolvable` |
| [GHSA-rcgg-9c38-7xpx](https://osv.dev/vulnerability/GHSA-rcgg-9c38-7xpx) | `io.opentelemetry:opentelemetry-api` 1.41.0 | `swiftExportClasspathResolvable` |
| [GHSA-j288-q9x7-2f5v](https://osv.dev/vulnerability/GHSA-j288-q9x7-2f5v) | `org.apache.commons:commons-lang3` 3.16.0 | `androidLintTool` |
| [GHSA-7r82-7xv7-xcpj](https://osv.dev/vulnerability/GHSA-7r82-7xv7-xcpj) | `org.apache.httpcomponents:httpclient` 4.5.6 | `androidLintTool` |
| [GHSA-wg6q-6289-32hp](https://osv.dev/vulnerability/GHSA-wg6q-6289-32hp) | `org.bouncycastle:bcpkix-jdk18on` 1.80.2 | `androidLintTool` |
| [GHSA-9pwp-9qqc-pr26](https://osv.dev/vulnerability/GHSA-9pwp-9qqc-pr26) | `org.bouncycastle:bcprov-jdk18on` 1.80.2 | `androidLintTool` |
| [GHSA-qp49-qgx5-5m26](https://osv.dev/vulnerability/GHSA-qp49-qgx5-5m26) | `org.bouncycastle:bcprov-jdk18on` 1.80.2 | `androidLintTool` |

These findings are report-only build-tool dependencies, not entries in the CI shipped-runtime lockfile. Recheck the toolchain configurations before release. This snapshot does not complete the broader release-security audit.

## Toolchain advisory remediation — 2026-11-12

The build now pins OSV-fixed versions only in `androidLintTool` and `swiftExportClasspathResolvable`; published runtime configurations are unchanged. Gradle dependency reports on this source tree confirmed the following selected versions in those configurations:

| Package | Selected version | Advisory records addressed |
|---|---:|---|
| `com.google.guava:guava` | 33.7.2-jre | [GHSA-xxph-c9ww-hj94](https://osv.dev/vulnerability/GHSA-xxph-c9ww-hj94) |
| `io.opentelemetry:opentelemetry-api` | 1.67.0 | [GHSA-ccrj-gw8r-854m](https://osv.dev/vulnerability/GHSA-ccrj-gw8r-854m), [GHSA-rcgg-9c38-7xpx](https://osv.dev/vulnerability/GHSA-rcgg-9c38-7xpx) |
| `org.apache.commons:commons-lang3` | 3.18.0 | [GHSA-j288-q9x7-2f5v](https://osv.dev/vulnerability/GHSA-j288-q9x7-2f5v) |
| `org.apache.httpcomponents:httpclient` | 4.5.14 | [GHSA-7r82-7xv7-xcpj](https://osv.dev/vulnerability/GHSA-7r82-7xv7-xcpj) |
| `org.bouncycastle:bcpkix-jdk18on` | 1.85 | [GHSA-wg6q-6289-32hp](https://osv.dev/vulnerability/GHSA-wg6q-6289-32hp) |
| `org.bouncycastle:bcprov-jdk18on` | 1.85 | [GHSA-9pwp-9qqc-pr26](https://osv.dev/vulnerability/GHSA-9pwp-9qqc-pr26), [GHSA-qp49-qgx5-5m26](https://osv.dev/vulnerability/GHSA-qp49-qgx5-5m26) |

The all-configuration OSV scan remains report-only for tooling, so review its result before each release. This targeted dependency remediation does not complete the broader release-security audit.

## Post-remediation CI scan — 2026-11-12

After the tool-only pins were applied, [CI run 43130621290](https://github.com/bxanedot/eXcor/actions/runs/43130621290) scanned 238 packages across all Gradle configurations and reported no issues; the separate shipped-runtime scan covered 115 packages and also reported no issues. The all-configuration tooling scan remains report-only and should be reviewed before release. G12 remains partial until the broader release-security audit is complete.
