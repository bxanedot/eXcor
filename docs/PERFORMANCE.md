# Performance

Measured, not estimated. The benchmarks are opt-in live tests against real YouTube and music services, so results depend on the network and the providers' servers. Compare runs from the same machine only.

**Running them:**
- **YouTube:** `EXCOR_BENCH=1 ./gradlew :jvmTest --tests com.excor.LiveVideoInfoTest.benchmark` writes `build/reports/youtube-bench.txt`.
- **Music providers:** `EXCOR_BENCH=1 ./gradlew :jvmTest --tests com.excor.music.LiveMusicTest.benchmark` writes `build/reports/music-bench.txt`. See [MUSIC_BENCHMARKS.md](MUSIC_BENCHMARKS.md).

## YouTube metadata, 2026-11-02

Environment: a cloud container, Linux amd64, 4 CPUs, JDK 27.0.1, one `Excor` instance and one OkHttp client (connections reused). Requests are counted by an OkHttp interceptor.

| Operation | Samples | Median | p95 | Requests | Failures |
|---|---|---|---|---|---|
| Cold start (construct + first `videoInfo`) | 1 | 1107 ms | — | 1 | 0 |
| `videoInfo` | 10 | 1069 ms | 1171 ms | 18 | 0 |
| `search` | 10 | 777 ms | 885 ms | 10 | 0 |
| `search` + continuation | 5 | 1817 ms | 1868 ms | 10 | 0 |
| `playlist` | 5 | 324 ms | 330 ms | 5 | 0 |
| `channel` (home + tab) | 5 | 542 ms | 577 ms | 10 | 0 |
| `comments` (watch page + comments) | 5 | 1071 ms | 1125 ms | 10 | 0 |
| `musicSearch` | 10 | 551 ms | 578 ms | 10 | 0 |
| `musicRadio` | 5 | 481 ms | 668 ms | 5 | 0 |

The heap grew by about 4 MiB over the whole run.

**Reading the results:**
- **Where the time goes:** almost all of it is YouTube's response time. Parsing is a small share; the offline test suite decodes the same shapes in milliseconds.
- **`videoInfo` request count:** 8 of the 10 videos needed a second request. YouTube's bot check withholds their details from the player response here, so eXcor reads the watch page (see `ExcorVideoInfo.fromWatchPage`). From a network that isn't bot-checked, `videoInfo` makes one request.

**Defects the benchmark found and fixed:**
- **`videoInfo` failures:** before the watch-page fallback, `videoInfo` returned null for 8 of 10 videos from this environment.
- **Transient `musicSearch` failures:** one earlier run had 3 failures and a 6.6 s p95, which no rerun reproduced. InnerTube reads now retry 429, 500, 502, 503, 504 and transport errors: three attempts at most, honoring a Retry-After of up to 5 s, otherwise exponential backoff with jitter.

## Competitor comparison

InnerTubeX's README makes no quantified performance claims. YouTube.js and NewPipeExtractor haven't been benchmarked here. No comparative claim is made until the same operations are measured under the same conditions.
