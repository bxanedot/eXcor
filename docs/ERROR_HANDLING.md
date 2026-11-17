# Error handling

How eXcor's YouTube and YouTube Music calls report problems. The music-provider API (`Excor.music`) has its own model, `ExcorCatalogException` with `ExcorCatalogError`; see [MUSIC_API.md](MUSIC_API.md).

| Result | Meaning | What to do |
|---|---|---|
| A value | YouTube answered with the expected page | — |
| `null` | YouTube answered, but not with that page: an unavailable, private or removed item, an ID of the wrong kind, a refusal that retrying would not change (such as HTTP 403 or 404), a response with malformed UTF-8, or a response eXcor doesn't recognize | Show "not available". If it happens for content that plays on youtube.com, YouTube may have changed its format; WARN logs name the endpoint and status |
| `ExcorRequestException(RATE_LIMITED, 429)` | Retry policy ended on HTTP 429 | Back off for a while before asking again |
| `ExcorRequestException(SERVER_ERROR, status)` | Retry policy ended on HTTP 500, 502, 503 or 504 | Try again later |
| `ExcorRequestException(NETWORK, null)` | The connection failed on all three attempts: DNS, TLS, timeout or reset. The underlying transport exception is intentionally not attached, because its message may contain sensitive request data | Check connectivity, proxies and the HTTP client |
| `IllegalArgumentException` | An invalid argument (a malformed video, channel or playlist ID, an overlong query or continuation) | Fix the call; retrying won't help |
| `CancellationException` | The caller's coroutine was cancelled | Nothing; it's never retried or wrapped |

## Retries

Metadata reads use this bounded policy before returning an error:

- **Eligible for retry:** HTTP 429, 500, 502, 503, 504 and transport errors.
- **Bounds:** three attempts at most. With no `Retry-After`, backoff is 300 ms, then 600 ms, plus up to 200 ms of jitter. Numeric `Retry-After` values from zero through five seconds are honored exactly.
- **Server delay outside the supported range:** a longer numeric value, an HTTP-date, or a malformed value ends the current request chain immediately with the existing typed HTTP error. It never becomes a shorter automatic retry. HTTP-date delays are not scheduled by this policy.
- **Never retried:** any other status. This policy does not establish a cooldown across separate SDK calls; callers must back off before starting another request.

Stream extraction (`extract`) is separate: a failed player request moves on to the next client instead of throwing.

## Response size limits

The Kotlin library reads player responses, watch pages, scripts, HLS manifests, and catalog responses through Ktor's streaming API. Declared lengths above the relevant limit are rejected from the headers. Bodies without a length are read only until they cross the limit. Direct media probes request a short range and check for a byte before closing the response. The transport can buffer bytes ahead of the application read, so an application
byte cap is not an exact network-byte cap. Oversized metadata is rejected without
waiting for EOF, and its scoped response source is closed. The synthetic loopback
test withholds EOF and checks rejection, closure and no retry; it does not assign
a byte limit to transport read-ahead. [Ktor response documentation](https://ktor.io/docs/client-responses.html#streaming)

## What messages contain

`ExcorRequestException.message` is the failure kind and status only, such as `YouTube request failed: SERVER_ERROR (HTTP 503)`. WARN logs name the InnerTube endpoint and status, such as `InnerTube browse answered HTTP 403`. Neither ever includes URLs with query strings, headers, cookies, visitor data or response bodies.

## Partial results

- **Watch-page metadata:** `ExcorVideoInfo.fromWatchPage` is true when YouTube withheld the player's details and the watch page supplied them. Duration, keywords, category, dates and countries are then null.
- **Playability:** `playable` is false, and `playabilityStatus` and `playabilityReason` carry YouTube's own words, when YouTube withholds the streams. For example `LOGIN_REQUIRED`, "Sign in to confirm you're not a bot".
