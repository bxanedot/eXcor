# Working in eXcor

Use this as the short entry point; the linked docs hold the authoritative detail.

## Find the active code

- `modules/cleanroom/` — active Kotlin Multiplatform library, fixtures, and tests.
- `modules/platforms/swift/` — separate native Swift package and tests.
- `modules/platforms/preview/` — unpublished Android and desktop host examples.
- `modules/samples/black-box-comparison/` — isolated comparison adapters and sanitized evidence tooling.
- `modules/legacy/` — historical, uncompiled source and captured fixtures. Preserve it for provenance; do not use it for new implementation or include it in builds.
- `api/` — Kotlin ABI baselines. Review intentional public API changes with the platform/API checks.

Read [architecture](docs/ARCHITECTURE.md) before changing module boundaries and [clean-room policy](docs/CLEAN_ROOM.md) plus [provenance](PROVENANCE.md) before using external material. Do not copy competitor implementation code. Keep the current license and every historical or third-party notice consistent with `LICENSE`, `NOTICE`, and `PROVENANCE.md`.

## Change and verify

- Reproduce behavior defects with a focused synthetic test before changing shared code. Keep new behavior in the active source tree and preserve existing public API unless the documented contract requires a change.
- Start with `./gradlew :jvmTest`; run `swift test --package-path modules/platforms/swift` for Swift changes. CI runs the broader Android and iOS build matrix.
- Keep live YouTube checks opt-in. Follow the [playback evidence matrix](docs/PLAYBACK_EVIDENCE_MATRIX.md); never bypass access refusals or store cookies, tokens, signed URLs, raw responses, or account details.
- Report access blocks separately from technical failures. A byte-range probe is not a decoder or player seek, and a green offline test does not promote a live release gate.
- Update [release gates](docs/release-gates.json) only when the evidence meets their stated scope. Check the [competitive matrix](docs/COMPETITOR_MATRIX.md) and [engineering plan](docs/superpowers/plans/2026-11-10-excor-competitor-plan.md) for outstanding work.
- Do not publish artifacts or announce a release without owner authorization.
- After confirming a PR is merged, delete its local and remote topic branch and prune stale refs; retain active automation branches and branches with unmerged work.

Start with the root [README](README.md) for the current capability and comparison snapshot.
