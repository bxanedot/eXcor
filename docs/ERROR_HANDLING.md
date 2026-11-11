# Error handling

How eXcor's YouTube and YouTube Music calls report problems. The music-provider API (`Excor.music`) has its own model, `ExcorCatalogException` with `ExcorCatalogError`; see [MUSIC_API.md](MUSIC_API.md).

| Result | Meaning | What to do |
|---|---|---|
| A value | YouTube answered with the expected page | — |
| `null` | YouTube answered, but not with that page: an unavailable, private or removed item, an ID of the wrong kind, a refusal that retrying would not change (such as HTTP 403 or 404), or a response eXcor doesn't recognize | Show "not available". If it happens for content that plays on youtube.com, YouTube may have changed its format; WARN logs name the endpoint and status |
| `ExcorRequestException(RATE_LIMITED, 429)` | HTTP 429 on all three attempts | Back off for a while before asking again |
| `ExcorRequestException(SERVER_ERROR, status)` | HTTP 500, 502, 503 or 504 on all three attempts | Try again later |
| `ExcorRequestException(NETWORK, null)` | The connection failed on all three attempts: DNS, TLS, timeout or reset. The underlying transport exception is intentionally not attached, because its message may contain sensitive request data | Check connectivity, proxies and the HTTP client |
| `IllegalArgumentException` | An invalid argument (a malformed video, channel or playlist ID, an overlong query or continuation) | Fix the call; retrying won't help |
| `CancellationException` | The caller's coroutine was cancelled | Nothing; it's never retried or wrapped |

## Retries

Metadata reads are retried before an exception is thrown:
- **Retried:** HTTP 429, 500, 502, 503, 504 and transport errors.
- **Bounds:** three attempts at most. A `Retry-After` of up to 5 s is honored, otherwise backoff is 300 ms, then 600 ms, plus up to 200 ms of jitter.
- **Never retried:** any other status.

Stream extraction (`extract`) is separate: a failed player request moves on to the next client instead of throwing.

## What messages contain

`ExcorRequestException.message` is the failure kind and status only, such as `YouTube request failed: SERVER_ERROR (HTTP 503)`. WARN logs name the InnerTube endpoint and status, such as `InnerTube browse answered HTTP 403`. Neither ever includes URLs with query strings, headers, cookies, visitor data or response bodies.

## Partial results

- **Watch-page metadata:** `ExcorVideoInfo.fromWatchPage` is true when YouTube withheld the player's details and the watch page supplied them. Duration, keywords, category, dates and countries are then null.
- **Playability:** `playable` is false, and `playabilityStatus` and `playabilityReason` carry YouTube's own words, when YouTube withholds the streams. For example `LOGIN_REQUIRED`, "Sign in to confirm you're not a bot".
