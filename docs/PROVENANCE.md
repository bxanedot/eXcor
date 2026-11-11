# Provenance record

The canonical, detailed source log is [`../PROVENANCE.md`](../PROVENANCE.md). It contains historical records for the preserved implementation and a separate clean-room section for the eXcor Kotlin addition.

The eXcor implementation relies only on the user-provided brief, the allowed permissive sources listed in that clean-room section, dependency documentation, standards and synthetic test inputs. The entries identify versions or commits, licenses and usage. No prohibited repository or GPL/LGPL/AGPL extractor source was used.

## Standalone comparison adapter

- The separate Node project in `samples/black-box-comparison/youtubejs-node/` depends on the published `youtubei.js@18.3.0` npm package, MIT-licensed at the 2026-11-11 review. Its exact transitive package versions and integrity hashes are pinned in `package-lock.json`. It is not a dependency of the eXcor SDK.
- The adapter is original sample code based on YouTube.js's published [Innertube API](https://ytjs.dev/api/classes/Innertube), [SessionOptions](https://ytjs.dev/api/type-aliases/SessionOptions), [FormatOptions](https://ytjs.dev/api/youtubei.js/namespaces/Types/interfaces/FormatOptions), [Format API](https://ytjs.dev/api/youtubei.js/namespaces/Misc/classes/Format), and [interpreter guide](https://ytjs.dev/guide/getting-started). Player-script evaluation is disabled by default; its opt-in evaluates remote code and must run without credentials or private data. No competitor implementation code is used or copied.
