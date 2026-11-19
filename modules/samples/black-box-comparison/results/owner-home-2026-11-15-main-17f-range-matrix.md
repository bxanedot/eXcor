# Owner-home live range matrix

- Exact main: `17f160417d3d98449e3ab6cd6cd07c6c0aa5eada`
- Run time: `2026-11-15T04:43:47Z`
- Environment: Windows x64, JVM, signed out, owner's home network
- Test: `com.excor.LivePlaybackTest.publicMusicVideosRecordRangeOutcomeForEachId`
- Result: one test passed; zero skipped; zero failures

| Public video ID | Outcome |
|---|---|
| `dQw4w9WgXcQ` | `RANGE_READABLE` |
| `kJQP7kiw5Fk` | `ACCESS_BLOCKED` |
| `JGwWNGJdvx8` | `ACCESS_BLOCKED` |
| `aqz-KE-bpKQ` | `ACCESS_BLOCKED` |
| `M7lc1UVf-VE` | `ACCESS_BLOCKED` |

The test continued after each recognized access block. `RANGE_READABLE` means the direct audio stream returned a valid bounded HTTP byte range with matching media type. This is one successful ID on one network; it does not establish full-decode, player-seek, cipher, competitor, or cross-network evidence.

Command:

```powershell
$env:EXCOR_LIVE = '1'
$env:EXCOR_LIVE_IDS = 'dQw4w9WgXcQ,kJQP7kiw5Fk,JGwWNGJdvx8,aqz-KE-bpKQ,M7lc1UVf-VE'
.\gradlew.bat :jvmTest --tests 'com.excor.LivePlaybackTest.publicMusicVideosRecordRangeOutcomeForEachId'
```
