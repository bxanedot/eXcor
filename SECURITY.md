# Security policy

eXcor is an experimental Kotlin Multiplatform library and has no production release. There are no supported published versions; security fixes are reviewed against the current `main` branch. Backports are not guaranteed.

## Reporting a vulnerability

Please report suspected vulnerabilities privately. If GitHub private vulnerability reporting is available for this repository, use **Security → Advisories → Report a vulnerability**. Otherwise, contact the maintainer through a private contact method listed on the [maintainer's GitHub profile](https://github.com/bxanedot). If no private route is available, open a minimal public issue asking for one; do not include exploit details there.

Include the affected commit or version, target platform, security impact, and a minimal reproduction. Do not send real account credentials, cookies, visitor IDs, PO tokens, signed media URLs, or private user data. Use synthetic values and redact request and response data.

The maintainer will make a reasonable effort to acknowledge and investigate reports, but response and fix timelines are not guaranteed. Please coordinate public disclosure with the maintainer after a fix or mitigation is available.

## Scope

Reports are in scope when they demonstrate a security issue in eXcor's maintained clean-room library, preview applications, samples, or build and release workflows. Examples include credential or signed-URL disclosure, URL-policy bypass, cross-session data leakage, unsafe handling of untrusted response data, or an exploitable resource-exhaustion or code-execution path.

YouTube access denials, rate limits, service changes, unsupported media formats, and vulnerabilities confined to third-party services are not eXcor vulnerabilities by themselves. For a third-party dependency issue, report it to that project's maintainers as well and identify any concrete impact on eXcor.

The historical implementation retained in `modules/legacy/src/` and `modules/legacy/ejs/` is quarantined and is not part of the active build. Do not use it as an input to clean-room fixes; report a vulnerability there with the affected path and commit so it can be assessed separately.
