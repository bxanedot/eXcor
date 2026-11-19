# Exact-source playback and range validation — 2026-11-14

## Setup

- Source commit: `e990cb32a05c096cba9c02eed7962febf275ef0c`.
- Windows x64, signed-out owner home network; no cookies or token provider.
- Public video: `dQw4w9WgXcQ`.
- Temurin JDK 25.0.4.1; FFmpeg and ffprobe 9.0.2.
- The selected `LivePlaybackTest` run completed 9 tests with zero skips, failures or errors.

## Results

- VISIONOS audio: 3,433,755 bytes of Opus decoded completely in 213.061 seconds. The midpoint 65,536-byte HTTP range at offset 1,716,877 matched its requested `Content-Range` and body length.
- VISIONOS video: 14,103,519 bytes of 480p H.264 (854×480, 25 fps) paired with AAC audio. The test verified the reported video length, streamed exact bounded ranges, muxed both tracks without re-encoding, and decoded the full 213.089-second output. The midpoint video range also matched its requested offset and body length.
- The format list returned 27 formats. All 27 returned HTTP 206 with the requested bytes, matching MIME types and range lengths; none relied on HTTP 200's initial-read fallback.
- Synthetic responses with a wrong range start, mismatched body length, wrong MIME type or excess bytes were rejected. Correct short final ranges and a correctly sized HTTP 200 full response were accepted.

The downloader checks MIME type, `Content-Length` when present, exact `Content-Range` boundaries, body size and stable total length. It bounds each media track to 256 MiB and each range read to 4 MiB; midpoint probes read at most 65,537 bytes to detect an oversized response.

## Limits

This is eXcor-only evidence for one public ID and one network. The run did not include InnerTubeX, and HTTP ranges do not exercise player-controlled seeking. The full-download requests followed redirects automatically; this run did not record each redirect hop or independently recheck redirect policy. Ciphered playback, HLS/SABR, another network and repeatability across IDs remain unverified. Keep G4/G5 partial and issue #78 open.

The command used `$env:EXCOR_LIVE='1'`, `$env:EXCOR_DECODE_ID='dQw4w9WgXcQ'` and `$env:EXCOR_VIDEO_HEIGHT='480'`, then ran `:jvmTest --rerun-tasks` for the audio decode, video/audio mux, format-list range check and six synthetic range checks. Temporary media files were removed by the tests; no signed URLs or response bodies were recorded.
