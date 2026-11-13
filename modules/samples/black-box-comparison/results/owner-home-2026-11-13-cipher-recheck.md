# Signed-out player cipher recheck

Date: 2026-11-13

Source commit: `6341b95` (`test: add live player cipher verification`)

Platform/network/session: Windows x64, owner's home network, signed out

Test: `com.excor.LivePlayerCipherTest.firstPartyCipherAndNTransformsProduceDecodableAudio`

PowerShell command: `$env:EXCOR_LIVE='1'; $env:EXCOR_CIPHER_ID='<video-id>'; .\gradlew.bat :jvmTest --tests com.excor.LivePlayerCipherTest.firstPartyCipherAndNTransformsProduceDecodableAudio --rerun-tasks`

The test fetched the public first-party watch page and parsed these video categories:

| Video ID | Content category | Audio formats | Formats with `signatureCipher` | Eligible `n` challenges | Outcome |
|---|---|---:|---:|---:|---|
| `dQw4w9WgXcQ` | music video | 4 | 0 | 0 | no cipher challenge exposed |
| `kJQP7kiw5Fk` | music video | 4 | 0 | 0 | no cipher challenge exposed |
| `aqz-KE-bpKQ` | stable public video | 16 | 0 | 0 | no cipher challenge exposed |
| `M7lc1UVf-VE` | stable public video | 12 | 0 | 0 | no cipher challenge exposed |

The opt-in test compiled and each live case exited as a skip because the response supplied no eligible cipher challenge. It did not fetch or run the player script, construct a transformed media URL, or request media. No signature, `n` value, signed media URL, cookie or account credential was printed or saved. These outcomes say nothing about whether the solver is correct.

A separate rerun at core source commit `6409c62` on the same Windows home network used `$env:EXCOR_LIVE='1'`, `$env:EXCOR_FORCE_TV='1'` and `$env:EXCOR_DECODE_ID='dQw4w9WgXcQ'` with `com.excor.LivePlaybackTest.extractedAudioDecodesFullyAndSeeks`. The test failed during its initial metadata lookup with YouTube's `The page needs to be reloaded.` refusal, before extraction, decode, range reads or seeking. The refusal is not evidence for or against cipher correctness.

This is a single-network signed-out diagnostic, not a comparison against InnerTubeX. G4/G5 remain partial and issue #78 stays open until a repeatable eligible ciphered stream can be decoded and genuinely sought.
