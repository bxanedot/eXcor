# Maintainer backlog — 2026-11-16 (UTC)

Snapshot after stabilization PRs #213–#228, at main
`5d18c065a5b9a59234643422dbf99b3f347e95d8`. The mission remains evidence-backed
superiority over InnerTubeX and YouTube.js; it is not complete. GPL-3.0-only,
historical Apache grants, third-party notices and the clean-room boundary remain
unchanged. Release remains NO-GO. Unit fixes and automation activation do not
promote live playback gates or establish competitive superiority.

| Priority | Next work | Dependency and verified baseline |
|---|---|---|
| P0 | Protect main and finish security/release governance | The public branch API still reports main unprotected. Current integration access to protection administration and security-alert inventory is restricted (HTTP 403). Recent CI security checks passed; that does not complete the independent security, native-linkage, dependency and archived-fixture/provenance audits. Administrator access is required for protection. |
| P1 | Expand authorized playback/cipher evidence (#78) | Cloud format lookup was blocked by proxy CONNECT 403 before media. Existing owner evidence remains limited by content/network/protocol scope; retain failed and access-blocked samples. Broader comparable content/network coverage, live cipher decode/seek and signed-in playback remain unverified. |
| P1 | Verify DASH/HLS host interoperability and seeks | #216–#218 merged. Generated HLS and reconstructed local DASH segments decode; service DASH transport/MPD playback and seeking remain unsupported. Full live HLS decode/seek still needs authorized evidence. A range probe is not a player seek. |
| P1 | Establish a production SABR/UMP path | G6 remains blocked pending independently verified service contracts and authorized playable fixtures. The bounded framing/assembly primitives and #221/#223 hardening are experimental, not SABR streaming. |
| P1 | Continue core request/error and concurrency reliability | #213/#214 request cancellation/streaming bounds and #225/#226/#228 empty-JSON, close and session warm-up fixes merged. Extend compatibility-preserving error diagnostics and authorized live fault verification; synthetic cancellation is not signed-in playback evidence. |
| P2 | Observe ongoing triage and complete maintenance lifecycle | Fixed labels are active and manually verified on main; observe the first real scheduled/issue-event run. #224 links unverified PR fix claims only. Validated resolution/regression tracking, conservative closure, PR policy automation, and persistent 30-day/7-day obsolete-branch warnings remain open. |
| P2 | Track current competitor releases and API breadth | Last version baseline was checked 2026-11-15: InnerTubeX v0.11.7 and YouTube.js 18.3.0. Scheduled release/interface monitoring and version-matched breadth review remain open. Use public docs/API metadata and independent observations, never competitor implementation code. |
| P2 | Expand comparable benchmarks | #215 corrected first-body timing and incorporates owner-run paired samples. The workloads remain too narrow for superiority; expand comparable cases/environments and retain negative/blocked outcomes. This cloud follow-up produced no live benchmark samples. |
| P3 | Finish installation/API onboarding and release packaging | #219's standalone JVM consumer and exact snippets compiled. Broader native/platform guides, API completeness and reproducible release/linkage verification remain separate work; publication still requires explicit authorization. |

## Maintenance activation evidence

On exact main `051fb71a698f6fe69450c48104cf0153ab8934e0` (2026-11-16):

- [Read-only run 43783266564](https://github.com/bxanedot/eXcor/actions/runs/43783266564): audit succeeded, triage skipped.
- [Apply run 43783544658](https://github.com/bxanedot/eXcor/actions/runs/43783544658): audit and triage succeeded; sanitized output reported one issue, zero PRs and one label update. Before/after metadata confirmed #78 gained `P1` and `maintenance` and remained open.

The workflow is active with daily 06:43 UTC and issue opened/edited/reopened
triggers. A real scheduled-event execution has not yet been observed. Manual
runs default to read-only; the explicit repository disable setting is honored.
The writer adds fixed labels only, preserves human labels, skips security issues,
and never closes issues, merges PRs or deletes branches. See the
[maintenance policy](AUTONOMOUS_MAINTENANCE.md) and #227's validation record.

## Stabilization merge and cleanup audit

These 16 PRs were freshly confirmed merged. Commit links retain the full hashes.
The listed remote topic refs are absent; local topics that existed were removed
only after merge/content confirmation, and stale refs were pruned. The four
squash merges (#217/#220/#221/#222) had patch equivalence verified before local
cleanup. Active/unmerged/base branches and `automation/excor-competitiveness`
are retained. This is confirmed merged-topic cleanup; the scheduled audit still
performs no obsolete-branch deletion.

| PR | Deleted topic | PR head | Merge |
|---|---|---|---|
| [#213](https://github.com/bxanedot/eXcor/pull/213) | `verify/live-request-cancellation` | [ed4484d](https://github.com/bxanedot/eXcor/commit/ed4484dd2fd17ae7cc0b930b0c2045d16e806d4e) | [e874072](https://github.com/bxanedot/eXcor/commit/e874072b5a000825c0afa2e588fcec6e89b1ac71) |
| [#214](https://github.com/bxanedot/eXcor/pull/214) | `fix/stream-bounded-http-responses` | [197171a](https://github.com/bxanedot/eXcor/commit/197171acf4ec391a8284d6e676249e863e661668) | [457bd96](https://github.com/bxanedot/eXcor/commit/457bd96a03e0f7c683dd1fe7840f67358ee1b42f) |
| [#215](https://github.com/bxanedot/eXcor/pull/215) | `verify/streaming-benchmark-reads` | [3c1a6b0](https://github.com/bxanedot/eXcor/commit/3c1a6b0e31a9e2b001933dd1170b201e52a64516) | [c70db5a](https://github.com/bxanedot/eXcor/commit/c70db5a62072925357d41907d6c3c5e02e2de994) |
| [#216](https://github.com/bxanedot/eXcor/pull/216) | `verify/hls-decode-progress` | [dac153e](https://github.com/bxanedot/eXcor/commit/dac153e34b9f5c377bd1209a4279702328a0e6e4) | [d7fd9ef](https://github.com/bxanedot/eXcor/commit/d7fd9ef0390e6cef96a141ce55a8b0a8d80c187e) |
| [#217](https://github.com/bxanedot/eXcor/pull/217) | `verify/dash-indexed-mp4` | [4f8486d](https://github.com/bxanedot/eXcor/commit/4f8486d307d1aee0fa9905a3e824a1edf7e63fee) | [e8ad01e](https://github.com/bxanedot/eXcor/commit/e8ad01ecd5e8494effe3d87aaf40dbde3fd7b373) |
| [#218](https://github.com/bxanedot/eXcor/pull/218) | `work/dash-indexed-mp4-cloud` | [225a9ee](https://github.com/bxanedot/eXcor/commit/225a9ee68a7371dd762eec988dccfc42b7469e1f) | [19e3771](https://github.com/bxanedot/eXcor/commit/19e37714799713739886e2d1cb0645e6408a4466) |
| [#219](https://github.com/bxanedot/eXcor/pull/219) | `docs/jvm-installation-guide` | [a654cf9](https://github.com/bxanedot/eXcor/commit/a654cf91b02345a7afdc42024da3a1509fdcd383) | [65e7bde](https://github.com/bxanedot/eXcor/commit/65e7bde97f2bfcadd6c36bf8e8f8da8ac1001c1f) |
| [#220](https://github.com/bxanedot/eXcor/pull/220) | `automation/issue-triage-audit` | [fcf47f8](https://github.com/bxanedot/eXcor/commit/fcf47f8161214194d2e56940c0dee7f2c4348569) | [b313209](https://github.com/bxanedot/eXcor/commit/b313209e5927a8faedc81aa2bb7811e9d6323097) |
| [#221](https://github.com/bxanedot/eXcor/pull/221) | `fix/ump-response-budget` | [4b998ac](https://github.com/bxanedot/eXcor/commit/4b998ac0e1b3e74b307f7738fb7124185ae55f01) | [724bffa](https://github.com/bxanedot/eXcor/commit/724bffa3fe509fdbc0370750bd0f639e39699394) |
| [#222](https://github.com/bxanedot/eXcor/pull/222) | `ci/cancel-superseded-pr-runs` | [d553e22](https://github.com/bxanedot/eXcor/commit/d553e22510113e2eeaaeb85a8c4313d4fac4b444) | [9a4672a](https://github.com/bxanedot/eXcor/commit/9a4672a7b6fc9ec791f423853e8ba4f49c16d034) |
| [#223](https://github.com/bxanedot/eXcor/pull/223) | `fix/ump-part-count-before-allocation` | [678dc30](https://github.com/bxanedot/eXcor/commit/678dc3008972ed804dd169c8d0c34d14b881927a) | [f27ef53](https://github.com/bxanedot/eXcor/commit/f27ef5318a64f0c62bcc39be445098263eb910a1) |
| [#224](https://github.com/bxanedot/eXcor/pull/224) | `automation/audit-pr-fix-claims` | [948b278](https://github.com/bxanedot/eXcor/commit/948b27804c9463f365c1d8c62a87534ec0a2a19f) | [d379dd4](https://github.com/bxanedot/eXcor/commit/d379dd4b1224a8735c1772121d8b415612d43569) |
| [#225](https://github.com/bxanedot/eXcor/pull/225) | `fix/catalog-empty-json-response` | [4962aac](https://github.com/bxanedot/eXcor/commit/4962aac8d7a15d8843dbf87c66119b75f2da8630) | [2e8f921](https://github.com/bxanedot/eXcor/commit/2e8f921cb5cc6c4a07da32b9ee53a719d817d14f) |
| [#226](https://github.com/bxanedot/eXcor/pull/226) | `fix/cancel-engine-background-on-close` | [441aed7](https://github.com/bxanedot/eXcor/commit/441aed737423bb2d439cabaf69c2ac58b95bcd6d) | [f5c2dfb](https://github.com/bxanedot/eXcor/commit/f5c2dfb24568e5105a6006847361aa58d05518e8) |
| [#227](https://github.com/bxanedot/eXcor/pull/227) | `automation/enable-fixed-label-triage` | [ad419d5](https://github.com/bxanedot/eXcor/commit/ad419d522274c03ac735deca6fbcbd5ca04f1afe) | [051fb71](https://github.com/bxanedot/eXcor/commit/051fb71a698f6fe69450c48104cf0153ab8934e0) |
| [#228](https://github.com/bxanedot/eXcor/pull/228) | `fix/coalesce-session-warmups` | [01ea2ec](https://github.com/bxanedot/eXcor/commit/01ea2eccbe3e819e485b15076b89ca7af893ab7e) | [5d18c06](https://github.com/bxanedot/eXcor/commit/5d18c065a5b9a59234643422dbf99b3f347e95d8) |

The merges used normal repository operations, without an administration override.
#214 received explicit owner approval; fixed-label activation followed the
owner's delegated decision. No release, public package publication or release tag was
created. Gate requirements remain in [release-gates.json](../release-gates.json),
and capability claims remain scoped by the [playback evidence matrix](../PLAYBACK_EVIDENCE_MATRIX.md).
