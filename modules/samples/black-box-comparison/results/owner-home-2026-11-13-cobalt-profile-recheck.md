# Current-worktree signed-out playback recheck

- Run time: 2026-11-13 03:34:01 UTC
- Base commit: `a9995256e3629b583f0e15faa4f73479aa54208a`
- Source tree: dirty local worktree; this run used the updated Cobalt TV profile
- Environment: owner's Windows home network, signed out
- Public video: `dQw4w9WgXcQ`
- Test: `LivePlaybackTest.extractedAudioDecodesFullyAndSeeks`, one sample
- Result: full audio decode and midpoint HTTP range read passed in 6.324 seconds

The `TVHTML5` Cobalt profile returned `UNPLAYABLE` (“The page needs to be reloaded”). VISIONOS then returned 27 usable formats and a WebM/Opus stream. FFmpeg decoded all 3,433,755 bytes (213.061 seconds); the midpoint request at byte 1,716,877 returned HTTP 206 at the requested offset.

This direct playback test is eXcor-only. A separate [current-worktree matched report](owner-home-2026-11-13-cobalt-profile-paired.md) records one cold sample per adapter on the same video; both passed full decode and both HTTP byte-range probes. Its single-sample timings do not establish a broad performance ranking. HTTP byte ranges do not test player-driven seeking. No access challenge was bypassed.

A separate run for `9bZkp7q19f0` was skipped after YouTube's access challenge blocked live verification; it produced no playback result.
