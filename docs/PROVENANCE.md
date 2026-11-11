# Provenance record

The canonical, detailed source log is [`../PROVENANCE.md`](../PROVENANCE.md). It contains historical records for the preserved implementation and a separate clean-room section for the eXcor Kotlin addition.

The eXcor implementation relies only on the user-provided brief, the allowed permissive sources listed in that clean-room section, dependency documentation, standards and synthetic test inputs. The entries identify versions or commits, licenses and usage. No prohibited repository or GPL/LGPL/AGPL extractor source was used.
## Black-box comparator dependency boundary

The isolated `samples/black-box-comparison/newpipe-jvm/` adapter pins NewPipeExtractor `v0.27.1`, accessed only through its published Java API. NewPipeExtractor is GPL-3.0-or-later; this library remains outside eXcor's build and publication graph. No NewPipeExtractor implementation code is included or copied. The adapter's `LICENSE-NOTICE.md` records the upstream license and the distribution review requirement. The source uses only the public v0.27.1 Javadocs linked in `docs/BLACK_BOX_COMPARISON_RESEARCH.md`.

The standalone `samples/black-box-comparison/youtubejs-node/` adapter pins the MIT-licensed npm package `youtubei.js@18.3.0` as of 2026-11-11. It is not part of eXcor's SDK dependency graph. The adapter uses public `Innertube.create`, `getBasicInfo`, and custom-fetch APIs; it selects only formats that already expose a direct audio URL, disables player retrieval, and never evaluates remote player JavaScript. Its research sources are listed in `YOUTUBE_JS_ADAPTER_RESEARCH.md`.

The adapter's custom fetch caps known request bodies at 1 MiB and API response bodies at 5 MiB; its separate media probe reads at most 64 KiB. It sends no credentials and writes no signed URLs or raw response data to samples.
