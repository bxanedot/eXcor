# Handoff: eXcor

This is the maintainer entry point. Current capability and comparison evidence live in the root README; the release decision and gate evidence live in `docs/IMPLEMENTATION_STATUS.md` and `docs/release-gates.json`. Check the current GitHub and worktree state before relying on a dated checkpoint.

## Start here

- [`AGENTS.md`](../../AGENTS.md): repository rules, source boundaries, verification, and branch cleanup.
- [`README.md`](../../README.md): supported scope, latest comparison evidence, build entry points.
- [`IMPLEMENTATION_STATUS.md`](../IMPLEMENTATION_STATUS.md): current release decision and verified gaps.
- [`release-gates.json`](../release-gates.json): authoritative G1–G12 status, evidence, commands, and blockers.
- [Competitive engineering plan](../superpowers/plans/2026-11-10-excor-competitor-plan.md): ordered work and completion criteria.
- [`EXCOR_PROGRESS.md`](EXCOR_PROGRESS.md): dated investigation and verification log; read its latest checkpoint first.
- [`QUESTIONS.md`](QUESTIONS.md): unresolved maintainer decisions.

## Current boundaries

- The current distribution license is GPL-3.0-only. Earlier Apache-2.0 grants remain recorded separately. Read [`CLEAN_ROOM.md`](../CLEAN_ROOM.md) and [`PROVENANCE.md`](../../PROVENANCE.md) before using external material.
- Keep the release NO-GO until every release gate has evidence at its stated scope. Never promote a gate based on a narrower offline or one-network check.
- Issue [#78](https://github.com/bxanedot/eXcor/issues/78) tracks owner-run playback, cipher, decode, and real seek evidence. Preserve access denials as a separate result from technical failures.
- G6 SABR/UMP remains blocked. Read the [viewer-contract review](../SABR_VIEWER_CONTRACT_RECHECK_2026-11-14.md) and [UMP framing research](../UMP_FRAMING_RESEARCH.md) before touching that prototype.
- The root README's readiness badge reads from `automation/excor-competitiveness`; keep that active score branch during branch cleanup.

## Work locations

- `modules/cleanroom/` — active Kotlin Multiplatform library and synthetic tests.
- `modules/platforms/swift/` — separate Swift package.
- `modules/platforms/preview/` — unpublished Android and desktop host examples.
- `modules/samples/black-box-comparison/` — isolated adapters, sanitized reports, and comparison tooling.
- `modules/legacy/` — quarantined historical source and captures; keep it out of new implementation and builds.

Use [`ARCHITECTURE.md`](../ARCHITECTURE.md) for request flow and platform responsibilities. Use the root `AGENTS.md` and README for current build and verification commands instead of copying command lists into this handoff.
