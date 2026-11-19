# Owner home-network player seek repeat — 2026-11-13

Second signed-out eXcor direct-audio player run on `dQw4w9WgXcQ`, using the same Windows home network and diagnostic as the first pass. This checks repeatability on one case; it is not an InnerTubeX player comparison.

## Run

- Source base: `main` at `a9995256e3629b583f0e15faa4f73479aa54208a`; working tree was dirty.
- Command: `gradlew.bat -p modules/samples/jvm-quickstart run --args="--playback dQw4w9WgXcQ --seconds 45 --seek-seconds 30"`.
- Signed-out stream selected: `VISIONOS`, `audio/webm`, 136 kbps; lookup reported 1,242 ms.
- ffplay opened at 30 seconds. Right then Left were sent to the focused waveform window; the waveform refreshed after both keypresses.
- Ordered upstream HTTP range starts were `0`, `329300`, `980764`, and `824084`; all four responses were HTTP 206. The offsets after Right then Left match the forward/backward sequence observed in the first run.
- The player ended at its configured 45-second limit (`player_result=time_limit`, elapsed 45,340 ms) after relaying 983,040 bytes, below the 256 MiB cap.

## Limits

This is the second eXcor audio seek pass for the same ID and network, not broader repeatability. It does not test video seeking, multiple IDs or networks, or InnerTubeX player seeks. The paired InnerTubeX adapter measures extraction, decode, and byte-range probes only. Keep G5 and G11 partial.
