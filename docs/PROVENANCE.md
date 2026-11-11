# Provenance record

The canonical, detailed source log is [`../PROVENANCE.md`](../PROVENANCE.md). It contains historical records for the preserved implementation and a separate clean-room section for the eXcor Kotlin addition.

The eXcor implementation relies only on the user-provided brief, the allowed permissive sources listed in that clean-room section, dependency documentation, standards and synthetic test inputs. The entries identify versions or commits, licenses and usage. No prohibited repository or GPL/LGPL/AGPL extractor source was used.

## Standalone comparison adapter

- The separate Node project in `samples/black-box-comparison/youtubejs-node/` depends on the published `youtubei.js@18.3.0` npm package, MIT-licensed at the 2026-11-11 review. Its exact transitive package versions and integrity hashes are pinned in `package-lock.json`. It is not a dependency of the eXcor SDK.
- The current API, runtime, and license review is recorded in [`YOUTUBE_JS_ADAPTER_RESEARCH.md`](YOUTUBE_JS_ADAPTER_RESEARCH.md); current URLs and package versions must be rechecked before a benchmark release.
- The adapter is original sample code based on YouTube.js's published [Innertube API](https://ytjs.dev/api/classes/Innertube), [SessionOptions](https://ytjs.dev/api/type-aliases/SessionOptions), [FormatOptions](https://ytjs.dev/api/youtubei.js/namespaces/Types/interfaces/FormatOptions), and [Format API](https://ytjs.dev/api/youtubei.js/namespaces/Misc/classes/Format). It disables player retrieval and never evaluates remote player code; formats requiring deciphering without a direct URL are `UNSUPPORTED`. No competitor implementation code is used or copied.
