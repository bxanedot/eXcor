# Owner-home HLS and video-seek follow-up — 2026-11-15

- Exact main: `59f87af558ff749e1e1477598f7c9aeaa982f575`
- Environment: Windows x64, JVM, signed out, owner's home network
- Content: public music video `dQw4w9WgXcQ`

## HLS playback

`LivePlaybackTest.hlsManifestDecodesForHostPlayer` completed as one skipped test, with zero failures or errors. The test classified a recognized YouTube access refusal before receiving an HLS manifest. FFmpeg did not run, so this is not a decode result. The JUnit timestamp was `2026-11-15T05:14:44.709Z`.

Command:

```powershell
$env:EXCOR_LIVE = '1'
$env:EXCOR_HLS_ID = 'dQw4w9WgXcQ'
.\gradlew.bat :jvmTest --tests 'com.excor.LivePlaybackTest.hlsManifestDecodesForHostPlayer'
```

## Video player-seek attempts

Two `excor-jvm --player-seek-video` runs reached the 30-second `time_limit`. Each relayed 753,664 bytes over two HTTP 206 requests, with range starts `[0, 2900]`; the 256 MiB cap was not reached. The desktop automation inventory did not expose the FFplay window, so no Right/Left keys were sent. These reads do not prove rendered playback or a player seek, and no InnerTubeX video-seek attempt was made.

The two comparison IDs were `owner-home-main-59f-video-seek-2026-11-15` at `2026-11-15T05:07:55Z` and `owner-home-main-59f-video-player-seek-2026-11-15` at `2026-11-15T05:10:30Z`.

## Interpretation

This recheck adds no successful HLS decode or video player-seek evidence and does not change the 53/100 evidence-weighted readiness score. G4/G5/G11 remain partial; G6 SABR/UMP remains blocked. The HLS result is an access refusal, while the video-seek attempts are incomplete because player input was unavailable.
