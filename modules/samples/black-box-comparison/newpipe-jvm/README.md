# NewPipeExtractor black-box adapter

This standalone JVM application drives the public NewPipeExtractor API for the `direct_audio` comparison lane. It pins `com.github.teamnewpipe:NewPipeExtractor:v0.27.1` and is not a dependency of eXcor. NewPipeExtractor is GPL-3.0-or-later; obtain licensing review before distributing an adapter binary or a package that includes the linked library. See [`LICENSE-NOTICE.md`](LICENSE-NOTICE.md).

The adapter uses only the published `NewPipe`, `StreamInfo`, `AudioStream`, `Stream`, and `Downloader` APIs. It selects the highest-bitrate URL-backed `PROGRESSIVE_HTTP` audio stream, then requests its first 64 KiB. HLS and DASH streams are reported as `UNSUPPORTED`; the adapter does not decode audio or test seeks.

## Build

Requires JDK 21 or newer and the repository's Gradle wrapper. From the repository root:

```sh
./gradlew -p modules/samples/black-box-comparison/newpipe-jvm assemble installDist
```

On Windows, use `./gradlew.bat` from the repository root.

Build and CI resolve the pinned NewPipeExtractor dependency but do not contact YouTube. This task builds and installs the standalone distribution; it does not run test tasks.

## Run

The launcher is `modules/samples/black-box-comparison/newpipe-jvm/build/install/newpipe-black-box-adapter/bin/newpipe-black-box-adapter` on Linux/macOS, or the `.bat` launcher on Windows.

```sh
newpipe-black-box-adapter \
  --video-id dQw4w9WgXcQ \
  --comparison-id run-home-01 \
  --main-commit FULL_EXCOR_COMMIT \
  --run-date-utc 2026-11-11T18:30:00Z \
  --network-category home \
  --content-category music_video \
  --phase cold \
  --sample-index 1 \
  --timeout-seconds 30
```

Replace `FULL_EXCOR_COMMIT` with the full 40-character eXcor commit hash (without spaces). Each invocation emits one normalized JSON line. A warm sample performs an unmeasured same-process extraction first; one deadline covers warm-up and the measured sample, then the request counters reset.

The deadline is capped at 30 seconds. Each extractor response is capped at 5 MiB, outbound request bodies at 1 MiB, redirects at four hops, and media reads at 65,536 bytes. HTTPS requests are limited to YouTube, Google API and GoogleVideo hosts. Every redirect is revalidated. The adapter does not forward cookies, authorization, proxy credentials, or other non-allowlisted headers, does not persist response cookies, and replaces NewPipe logging with a no-op logger before extraction.

The output excludes media URLs, query strings, request/response bodies, headers, and exception messages. `requestCount` includes extractor and media request attempts; `mediaRequestCount` and `rangeRequests` identify the media subset. `extractMs` covers `StreamInfo.getInfo` (metadata and stream extraction are not separately exposed by this API), `firstMediaByteMs` is measured from the attempt start, `startupMs` is null, and decode/forward-seek/backward-seek remain `NOT_TESTED`. `PASS` means extraction returned a direct audio URL and the bounded initial media read returned bytes; it does not prove full playback.

Run every adapter separately under matching host, network, video set, timeout, locale, and comparison metadata. HTTP 401/403 responses and documented NewPipe bot, age, geographic, private, and paid access restrictions are recorded as `BLOCKED`; generic content unavailability is kept separate from parser faults. Do not omit blocked samples or treat them as extractor failures.