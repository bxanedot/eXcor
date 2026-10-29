# eXcor implementation plan

This plan follows the user's master engineering plan and the current clean-room repository boundary. The detailed catalog work is in [`superpowers/plans/2026-10-29-metadata-catalog.md`](superpowers/plans/2026-10-29-metadata-catalog.md). Do not include preserved historical source in the clean-room artifact.

## Phase 0 — repository audit and baseline

The active root build uses `cleanroom-src/`, plus a separate native Swift package under `swift/`. Current `main` CI is green. The provenance and functional audit explicitly excludes historical implementation files because the user previously prohibited opening them; whole-repository provenance therefore remains unresolved. See [`REPOSITORY_AUDIT.md`](REPOSITORY_AUDIT.md).

## Phase 1 — stable library core

Keep the existing extraction API source-compatible. Close any correctness gaps in lifecycle, cancellation, typed failures, URL validation, response bounds, platform behavior, and public API verification before expanding claims.

## Phase 2 — catalog and music metadata

Add a shared source-preserving catalog model, provider capability reporting, lookup/search, and pagination. Implement Apple Music against the public iTunes Search/Lookup API first. Then implement Spotify and Deezer only through currently documented, authorized APIs and host-owned credentials. Do not treat a private endpoint as an acceptable fallback.

## Phase 3 — YouTube Music access decision

Verify whether the official YouTube Data API can provide the requested music-specific catalog behavior. If not, mark those operations restricted until an authorized API/access path exists; do not implement undocumented endpoints without permission.

## Phase 4 — cross-platform integration and maturity

Run contract tests across supported KMP targets, integrate supported catalog APIs into the Android consumer, and expand the Swift API only where a verified product contract requires it. Keep the two packages' behavior and capability claims explicit.

## Phase 5 — release readiness

Resolve provenance and fixture-rights review, add working consumer examples and API documentation, tighten CI and license/security checks, and publish only targets with passing reproducible builds and functional smoke tests.

The current capability state and verification limits are in [`FEATURE_MATRIX.md`](FEATURE_MATRIX.md) and [`IMPLEMENTATION_STATUS.md`](IMPLEMENTATION_STATUS.md). A capability is not complete until it has a public API, tests, documented failure behavior, and verified platform support.
