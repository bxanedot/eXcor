# Owner home-network player seek check — 2026-11-13

This is one signed-out eXcor direct-audio playback run on `dQw4w9WgXcQ` from the Windows home network. It supplements the paired extraction/decode reports; it is not an InnerTubeX player comparison.

## Run

- Source base: `main` at `a9995256e3629b583f0e15faa4f73479aa54208a`; working tree was dirty.
- Command: `gradlew.bat -p modules/samples/jvm-quickstart run --args="--playback dQw4w9WgXcQ --seconds 45 --seek-seconds 30"`.
- Signed-out stream selected: `VISIONOS`, `audio/webm`, 136 kbps; lookup reported 4,849 ms.
- The quickstart initially stopped when metadata reported `UNPLAYABLE`, before trying other extraction profiles. The diagnostic now proceeds to extraction so a different profile can still supply the stream.
- ffplay opened the waveform window at 30 seconds. With the player focused, Right then Left produced this ordered upstream HTTP Range sequence: `0`, `329300`, `980764`, `824084`.
- All four upstream responses were HTTP 206. The request after Right moved forward from the 30-second startup offset; the request after Left moved back from that later offset. This is one observed forward/backward player-seek pass, not a duration or seek-accuracy benchmark.
- The player was stopped by the diagnostic's 45-second limit (`player_result=time_limit`); it relayed 966,656 bytes without reaching the 256 MiB cap.

## Limits

One ID and one network only. This run did not test video playback, full-file decode, ciphered playback, HLS, SABR/UMP, or InnerTubeX's player. The paired InnerTubeX adapter measures direct-audio extraction and byte-range probes, not player seeks. Keep G5 and G11 partial.
