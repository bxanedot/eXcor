# Music benchmarks

`EXCOR_LIVE=1 EXCOR_BENCH=1 ./gradlew :jvmTest --tests com.excor.music.LiveMusicTest --rerun` writes `build/reports/music-bench.txt`. The benchmark measures:

- cold start;
- median and p95 latency per operation;
- request counts, from `ExcorMusic.requestCount`;
- the offline CPU cost of matching;
- the heap delta.

## Measured 2026-11-01

Environment:

- a cloud container with 4 vCPU (Intel Xeon, 2.1 GHz) and JDK 27;
- all traffic through the environment's egress proxy, so latency includes the proxy and a cloud-to-provider route;
- one run, keyless providers only.

Treat these numbers as this environment's, not as a general claim.

| Operation | n | Median | p95 | Requests |
| --- | --- | --- | --- | --- |
| Cold start: construct, then first Deezer track | 1 | 187 ms | – | 1 |
| Deezer track, uncached | 10 | 197 ms | 216 ms | 9 (one was already cached) |
| Deezer track, cached | 10 | 0 ms | 0 ms | 0 |
| Deezer search | 5 | 233 ms | 238 ms | 5 |
| iTunes (Apple keyless) track | 5 | 57 ms | 134 ms | 5 |
| Cross-provider match Deezer→Apple | 3 | 4 ms | 4 ms | 1 (later runs served from the cache) |
| Playlist sync, 50 tracks, fresh | 3 | 784 ms | 784 ms | 6 (2 per sync: snapshot plus one page of 50) |
| Rank 1,000 candidates, offline | 20 | 29 ms per ranking | – | 0 |

Heap delta after the run: 1,620 KiB, including the response cache. The cache is bounded at 256 entries, each kept 5 minutes.

## Not measured

- Spotify and the Apple Music API: no credentials in this environment.
- iOS and Android devices.
- Peak memory under a large library sync.

No comparison with other libraries has been made, so no performance claim is made against them.
