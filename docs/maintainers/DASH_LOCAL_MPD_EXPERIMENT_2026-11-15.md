# Local DASH MPD experiment — 2026-11-15

This diagnostic began at `3faca7e`, stacked on PR #217 (`7fae79e`),
when main was `314dd6e3fa7f44325f280feb826b298a8fb70e17`. It now integrates
main `9a4672a7b6fc9ec791f423853e8ba4f49c16d034` after #217 merged. It supplies no new exact-main playback evidence. DASH playback
remains unsupported; release gates and readiness points are unchanged.

## Results

- The requested live test compiled, then failed before formats/media with
  `Unexpected response code for CONNECT: 403` from the cloud proxy. This is an
  environment access block, not a YouTube refusal, SIDX parser failure, or
  decoder result. The initial Gradle distribution connection refusal was
  resolved by using the environment's configured Java proxy.
- A synthetic XML regression reproduced the draft's two-level SegmentList.
  The builder now keeps initialization, timescale, timeline and segment
  references together in one representation-level SegmentList; that XML
  regression passes. It checks XML structure, not full DASH XSD conformance.
- Generated two-second AAC/H.264 fragmented MP4 tracks exercise the same SIDX
  parser and MPD builder without service access. On Debian FFmpeg
  `7.1.5-0+deb13u1`, the local byte-range MPD decode timed out at 45 seconds in
  two attempts. A separate two-second bounded debug probe on these synthetic
  files logged duplicate FTYP/MOOV and corrupt H.264 packets after a nonzero
  media-range open. This is a failed local-file experiment, not successful
  DASH playback, full decode, or seeking. Other FFmpeg versions are untested.
- The follow-up removes local-file byte ranges from the MPD. It copies each
  validated initialization/media range into a distinct temporary file, then
  decodes that reconstructed SegmentList MPD. On the same FFmpeg 7.1.5 build,
  all three DASH-focused checks pass: XML structure, exact range-copy/truncated-file
  rejection, and complete generated AAC/H.264 decode (at least two SIDX
  references per track). This resolves the local experiment only. It does not
  demonstrate HTTP byte-range MPD consumption, live service DASH, or seeking.
- Linux CI now installs FFmpeg, records its version and runs these three
  DASH checks plus the generated HLS VOD check with EXCOR_LIVE=0. This checks reconstruction without
  a YouTube request. Exact-head CI for this follow-up is still required.
  No production capability is promoted; merge requires green current-head checks.

The only service SIDX/range evidence remains PR #217's existing 2026-11-15
Windows home-network sample: itag 139 init 0–731, index 732–1027, timescale
44,100, duration 9,400,320 ticks, 22 references, first moof at 1,028; itag 135
init 0–740, index 741–1228, timescale 12,800, duration 2,726,912 ticks,
38 references, first moof at 1,229. Six HTTP 206 reads verified those inputs
on one ID/network. This cloud session did not reproduce those reads. A range
probe is not a player seek.

The integrated follow-up JVM suite passed: 284 tests, 0 failures/errors, 26 opt-in
skips (258 executed passes), including XML/segment-copy and malformed-SIDX
regressions. The separate FFmpeg-enabled run passed all four generated HLS/DASH checks.
`git diff --check` and the immutable-action-pin check also passed.

A follow-up reproduced stale skips when only EXCOR_DASH_DECODE changed from
0 to 1: passing it through `environment` alone left :jvmTest UP-TO-DATE.
The opt-in controls (including EXCOR_DECODE_ID and EXCOR_HLS_FIXTURE) are now explicit task input
properties as well. Repeating the same selected tests with flag 0 then 1
reran :jvmTest and changed one skip into a successful synthetic decode.
Unset controls remain unset in the worker environment, preserving built-in
video-ID defaults. Presence is a separate task input so absent and explicitly
empty values do not share a cached result.
See [Gradle incremental-build runtime inputs](https://docs.gradle.org/current/userguide/incremental_build.html).

## Bounds and scope

The experiment downloads complete tracks in validated HTTP ranges to temporary
files, copies initialization and SIDX media ranges to individual segment files,
then synthesizes an MPD. Original full-track files are removed after successful
reconstruction; a temporary directory is removed on success or failure. The
8 KiB copy buffer does not load live tracks into memory. Peak media disk usage
is at most 512 MiB (256 MiB original tracks plus at most 256 MiB copied ranges).
This does not consume a service MPD or implement SDK DASH transport.
The combined declared track length is limited to 256 MiB;
existing downloads validate totals, MIME, Content-Range and actual body length.
Index/probe reads are limited to 64 KiB each. The media client forbids redirects,
uses a 15-second per-call timeout and stops starting calls after a shared
120-second deadline spanning media probes and download chunks (at most another
15 seconds for an in-flight call). Format lookup retains the SDK's existing bounds.
FFmpeg receives local paths only, uses the `file` input protocol allowlist,
explicit audio/video maps and `-xerror`, and has a 45-second process limit,
32 KiB progress-output limit, bounded termination and reader cleanup. Temporary
media is deleted on success and failure. Nonzero SIDX presentation starts are
rejected by this limited builder rather than silently shifting tracks.

No signed URL, session, raw service response or account data is recorded here.
No production code, public API, license, README capability claim or release
status changes.

## Reproduction

```sh
./gradlew :jvmTest
EXCOR_DASH_DECODE=1 ./gradlew :jvmTest --tests 'com.excor.LivePlaybackTest.localDashManifest*'
EXCOR_LIVE=1 EXCOR_DASH_DECODE=1 EXCOR_DECODE_ID=dQw4w9WgXcQ ./gradlew :jvmTest --rerun --tests 'com.excor.LivePlaybackTest.dashManifestDecodesLiveIndexedMp4Tracks'
```

Run Gradle invocations sequentially: simultaneous tasks share test-result files.
Synthetic generation needs FFmpeg with lavfi, AAC and libx264. A failed opt-in
diagnostic is not converted to a skip or counted as playback evidence.

## Official references checked

- [DASH-IF timing model, indexed addressing and SIDX structure](https://dashif.org/Guidelines-TimingModel/#indexed-addressing): index ranges and timescale semantics. This experiment expands SIDX references into a SegmentList; it does not test a player's SegmentBase index consumption.
- [DASH-IF IOP v4.3 (2018)](https://dashif.org/docs/DASH-IF-IOP-v4.3.pdf): public interoperability reference, not a claim that this local MPD satisfies the DASH-IF restricted profile.
- [FFmpeg formats](https://ffmpeg.org/ffmpeg-formats.html#dash): DASH demuxer presents manifest streams.
- [FFmpeg protocols](https://ffmpeg.org/ffmpeg-protocols.html): `offset`/`end_offset` are documented HTTP options; file options do not list them. This is consistent with the observed local-range failure, not proof of all versions' behavior.
- [FFmpeg CLI](https://ffmpeg.org/ffmpeg-all.html): progress ends with `progress=end`; protocol allowlisting and progress alone do not establish successful per-track decode.

Only public documentation was consulted, not FFmpeg or competitor implementation
source. Current online FFmpeg documentation is not pinned to the installed
7.1.5 build; executable observations above are version-specific.
