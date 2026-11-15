# InnerTubeX video-seek resolution classification — 2026-11-15

## Scope

- eXcor main commit recorded in the adapter metadata: `dbbe8c14463eca40adc44b9501b51f4e669c932b`
- InnerTubeX adapter dependency: `0.11.7`
- Signed-out Windows home network; public video `dQw4w9WgXcQ`
- One `--player-seek-video` attempt with the 30-second limit
- The adapter diagnostic change was run from local branch `fix/innertubex-seek-diagnostics`; this is not an exact-main run of eXcor playback code.

## Result

InnerTubeX returned:

```text
player_seek=unavailable outcome=UNSUPPORTED failureClass=UNSUPPORTED_TRANSPORT
```

The existing `NO_PLAYABLE_STREAM` classifier produced this status. The run stopped before FFplay opened and before any media request, so no playback or player-controlled seek was measured. This attempt did not return an access-denied classification.

The two earlier attempts at eXcor main `360c38c` printed `stream_resolution_failed` before FFplay. They ran before this diagnostic fix and remain unclassified; this new result does not establish their causes.

This is one direct-video extraction result on one ID and network. It does not establish general InnerTubeX video incompatibility or parity. G4, G5 and G11 remain partial, issue #78 remains open, and release status remains NO-GO. No raw media URL, response body, cookie or token was retained.
