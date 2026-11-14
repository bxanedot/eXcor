# eXcor vs. InnerTubeX: 53/100

**Evidence-weighted readiness, not a live media playback success percentage.**

Release: **NO-GO** · main CI: **queued** · rubric reviewed 2026-11-13

| Category | Points |
|---|---:|
| YouTube and YouTube Music metadata | 17/20 |
| Direct audio/video streaming | 11/20 |
| Player and cipher compatibility | 8/15 |
| SABR/UMP playback | 0/20 |
| Reliability and security | 11/15 |
| Platforms and developer usability | 6/10 |

## Missing verification

- 3 points — Complete catalog coverage (unverified; G7)
- 5 points — Multiple eligible videos and networks verified (blocked; G5)
- 4 points — HLS/DASH transport and seeking verification (unverified; G5)
- 5 points — Live ciphered stream decodes and seeks (blocked; G4)
- 2 points — Owner-run signed-in playback (unverified; G4)
- 5 points — Authorized SABR request negotiation (blocked; G6)
- 5 points — SABR audio assembly and decoding (blocked; G6)
- 5 points — SABR video assembly and decoding (blocked; G6)
- 5 points — SABR seeking and stream recovery (blocked; G6)
- 2 points — Authorized live fault verification (blocked; G8)
- 2 points — Full diagnostic and release security audit (unverified; G12)
- 2 points — Owner-authorized reproducible remote release (unverified; G10)
- 2 points — Verified consumer API parity (unverified; G10)

CI is checked live but never awards streaming/playback points.
Offline UMP framing, bot checks, and passing compilation alone do not establish SABR parity.
A 100/100 score requires every release gate to pass.

[Inspect main CI](https://github.com/bxanedot/eXcor/actions/runs/43629614796)
