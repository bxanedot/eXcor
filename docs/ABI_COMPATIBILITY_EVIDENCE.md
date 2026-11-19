# Kotlin ABI compatibility verification

This record concerns the clean-room library API only. It does not establish live playback, cipher success, SABR/UMP transport, or release readiness.

## 2026-11-12: enforced ABI check

- Pull request: [#140](https://github.com/bxanedot/eXcor/pull/140)
- Tested PR head: `94a70150b6a06f3532927916258f701831f8ed53`
- [Exact-head CI run 43161179569](https://github.com/bxanedot/eXcor/actions/runs/43161179569): **success** in all six jobs: Linux, Windows, macOS, preview, Android API 26 and Android API 36.
- Squash merge commit: `d694a9195f20a1bd37f7ada1b614ab4f06dc45f3`.
- The change commits Android/JVM and Kotlin/Native iOS ABI reference dumps and makes `:checkKotlinAbi` a blocking macOS CI step. The PR fixed the misplaced Android dump path before the successful exact-head run.

This verifies that the baseline and compatibility task passed the tested CI matrix. It does **not** verify binary compatibility against previously published external versions, owner-operated playback, or a full release-security audit. Post-merge main CI must be checked separately.

Competitiveness rubric impact: **0 points**. The evidence rubric remains 53/100; build/ABI readiness alone cannot award playback or consumer-parity points.
