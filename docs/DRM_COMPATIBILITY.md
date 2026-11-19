# DRM compatibility

eXcor identifies protection and decides whether an **authorized** player can handle a track. It never extracts keys, CDM secrets or decrypted media, and never works around entitlement checks.

## Detection

Two parsers read manifests:

- **`excorHlsProtectionOf(playlist)`** reads `EXT-X-KEY` and `EXT-X-SESSION-KEY`.

  | Field | Value | Meaning |
  | --- | --- | --- |
  | `METHOD` | `AES-128` | Whole-segment encryption. This is not DRM, but it is still reported as protected. |
  | `METHOD` | `SAMPLE-AES`, `SAMPLE-AES-CTR`, `SAMPLE-AES-CENC` | Sample encryption. |
  | `KEYFORMAT` | `com.apple.streamingkeydelivery` | FairPlay |
  | `KEYFORMAT` | `com.microsoft.playready` | PlayReady |
  | `KEYFORMAT` | `org.w3.clearkey` | ClearKey |
  | `KEYFORMAT` | `urn:uuid:<system id>` | The system with that ID |

- **`excorDashProtectionOf(mpd)`** reads `ContentProtection`:
  - `urn:mpeg:dash:mp4protection:2011` gives the ISO/IEC 23001-7 scheme (`cenc`, `cbcs`, `cens`, `cbc1`);
  - `urn:uuid:` gives the system by its DASH-IF registered ID: Widevine `edef8ba9-…`, PlayReady `9a04f079-…`, FairPlay `94ce86fb-…`, ClearKey `e2719d58-…`.

  Unregistered IDs are reported as `UNKNOWN`.

`ExcorProtectionInfo.keyUriPresent` says only whether the manifest names a key or license URI. The URI itself is never kept, because such URIs are often signed or tied to an account.

**Provider statements.** For Spotify, Apple Music and Deezer, full tracks are protected and play only in the provider's client or SDK. None of their public APIs names the DRM system per track, so eXcor reports `PROVIDER_MANAGED` rather than guessing one. Previews are a separate matter: eXcor reports only that a preview exists.

DRML (CC0) was read only for terminology. It covers optical-media and software protection, not streaming, so no DRML classification is used here.

## Playback decisions

`music.playbackOptions(track)` returns an `ExcorPlaybackStatus`:

| Status | When |
| --- | --- |
| `UNPROTECTED_PLAYABLE` | The manifest has no protection. |
| `PROTECTED_AUTHORIZED_ADAPTER` | A ready host adapter for this provider exists and doesn't refuse the track. |
| `PROTECTED_REQUIRES_CLIENT` | Protected, and no ready adapter. This is the default for all three catalogs. |
| `SUBSCRIPTION_REQUIRED` | The adapter says this account isn't entitled. |
| `REGION_RESTRICTED` / `UNAVAILABLE` | The provider says so. |
| `DRM_UNSUPPORTED` | A named DRM system that the host's `ExcorPlatformCapabilities` doesn't list. |

`music.play(track)` calls the adapter, or throws `ExcorPlaybackException(status)`. A protected track is never reported as playable without an adapter.

## ExcorProtectedPlaybackAdapter

The host implements this interface over a licensed SDK: Spotify's app remote or Web Playback SDK, MusicKit, or a Media3 DRM session using the platform CDM. The adapter reports `isReady()` and `isEntitled(track)`, and `play(track)` runs entirely inside the SDK. eXcor ships no adapter, so protected playback is supported only where the host provides one and the SDK supports that platform.

| Provider | Platforms where the official SDK exists | eXcor support |
| --- | --- | --- |
| Apple Music (MusicKit) | iOS, iPadOS, macOS; MusicKit for Android; MusicKit JS on the web | Host adapter only |
| Spotify | Android and iOS app remote (controls the Spotify app); Web Playback SDK in supported browsers | Host adapter only |
| Deezer | Deezer's own SDKs, availability subject to Deezer's developer access | Host adapter only |

eXcor has not tested any adapter on a device.
