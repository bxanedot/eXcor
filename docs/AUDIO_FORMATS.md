# Audio formats

`ExcorAudioFormat` describes one rendition only by what its source states: codec, container, bitrate, sample rate, bit depth, channels, a spatial flag and the raw codec string. Null means "not stated", never "none". `lossless` follows from the codec: FLAC, ALAC and PCM are lossless; everything else is lossy.

## Recognition

- `excorAudioCodecOf` reads RFC 6381 codec strings:

  | Codec string | Codec |
  | --- | --- |
  | `mp4a.40.2` | AAC-LC |
  | `mp4a.40.5` | HE-AAC |
  | `mp4a.40.29` | HE-AACv2 |
  | `mp4a.40.42` | xHE-AAC |
  | `mp4a.40.34`, `mp4a.69`, `mp4a.6B` | MP3 |
  | `mp4a.A5` | AC-3 |
  | `mp4a.A6` | E-AC-3 |
  | `opus` | Opus |
  | `vorbis` | Vorbis |
  | `flac` / `fLaC` | FLAC |
  | `alac` | ALAC |
  | `ac-3` | AC-3 |
  | `ec-3` | E-AC-3 |
  | `ac-4` | AC-4 |
  | `lpcm`, `ipcm`, `fpcm` | PCM |

  Anything else is `OTHER`, never a guess.
- `excorAudioFormatOf` reads a MIME type with an optional `codecs` parameter. A bare `audio/mp4` returns null, because it could hold anything.
- `excorHlsChannelsOf` reads HLS `CHANNELS` values; `16/JOC` is Dolby Atmos in E-AC-3.

## What providers declare

Catalog providers give eXcor no streams, so quality is a declaration (`ExcorAudioTrait`) rather than a format eXcor delivers.

| Provider | Declared per track | eXcor reports |
| --- | --- | --- |
| Apple Music API | `audioVariants`: lossy stereo, lossless, hi-res lossless, Dolby Atmos, Dolby Audio, spatial | The traits. For lossless variants also an ALAC format, because MusicKit's AudioVariant documentation names ALAC: up to 24-bit/48 kHz, and up to 24-bit/192 kHz for hi-res. Exact rates aren't per track, so they stay null. Lossy and Atmos codecs aren't named per track, so no format is claimed. |
| Spotify | Nothing in the Web API | No traits; `AUDIO_TRAITS` is UNSUPPORTED. |
| Deezer | Nothing per track in the public API | No traits. MP3 and FLAC tiers depend on the subscription and are not verified per track. |
| iTunes fallback | Nothing | `AUDIO_TRAITS` is RESTRICTED (needs the Apple Music API). |

eXcor never labels lossy audio lossless, and never transcodes.

## Format selection

`excorSelectAudio(options, preferences)` is deterministic and applies to audio the host is actually entitled to use, such as YouTube streams or the host's own files.

1. **Rejection.** An option is rejected, with the reason recorded, when it:
   - isn't entitled;
   - is protected and `allowProtected` is off;
   - uses a codec outside `supportedCodecs`;
   - is spatial and spatial isn't allowed;
   - exceeds the bitrate, sample-rate or bit-depth cap.

   A missing value never passes for a high one.
2. **Ranking.** The rest are ranked by:
   1. spatial, if preferred;
   2. lossless, if preferred (the default);
   3. bit depth;
   4. sample rate;
   5. bitrate;
   6. unprotected before protected.

   Ties keep the input order.
