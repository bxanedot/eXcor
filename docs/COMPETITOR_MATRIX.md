# Competitive capability matrix

This file tracks eXcor's candidate capability coverage without asserting unverified facts about other projects. The master engineering plan names YouTube.js, InnerTubeX and NewPipeExtractor as comparison targets. Their source code and derived implementation details are prohibited inputs for eXcor.

| Capability area | External capability assessment | eXcor status | Next evidence needed |
|---|---|---|---|
| YouTube audio extraction | Not assessed in this clean-room pass | Implemented on JVM; iOS build not yet verified | Public documentation or authorized black-box tests, then platform smoke tests |
| Catalog search and pagination | Not assessed | Not implemented in clean-room API | Product scope and public API decision |
| Session and account features | Not assessed | Host session and PO-token interfaces only | Authorized account capability and security review |
| Additional media providers | Not assessed | Not implemented in clean-room API | Provider-by-provider authorization and public interface design |
| Cross-platform developer experience | Not assessed | KMP targets configured; macOS CI currently failing | Passing native CI and consumer example |

No competitor source or implementation-derived claim was used to fill this matrix. Update it only from public documentation, published interfaces, release notes or authorized black-box observations, and record sources in `PROVENANCE.md`.
