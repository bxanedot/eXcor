# eXcor benchmark status

> The existing `BenchmarkTest` harness and `EXCOR_BENCH` variables belong to the preserved earlier implementation; they do not measure the clean-room eXcor engine. There are no verified eXcor benchmark results yet. Do not treat legacy results or commands as eXcor measurements.

# Benchmarks

No measured results yet. The build machines get YouTube's bot check on player requests (QUESTIONS item 41),
so the benchmark has to run on a normal network; until someone runs it, nothing here claims a success rate.
The live surveys in QUESTIONS item 2 are spot checks, not this benchmark.

## Running it

```sh
EXCOR_BENCH=1 ./gradlew jvmTest --tests com.excor.BenchmarkTest --rerun -i
EXCOR_BENCH=1 EXCOR_BENCH_IDS=dQw4w9WgXcQ:ordinary,6oHkCfhlDnc:Short ./gradlew jvmTest --tests com.excor.BenchmarkTest --rerun -i
```

It writes `build/benchmark-report.md` and prints it. Logged out; no URL, cookie or token is ever written.

## What it measures

For each video of a fixed set (`BenchmarkTest.cases`: ordinary videos, a Short, YouTube Music tracks, 4K 60 fps,
dubbed audio, a 24/7 live stream, and three that YouTube refuses: age-restricted, copyright-blocked, nonexistent):

- **Outcome**: media that answers a 1 KiB ranged GET with a 206 and exactly 1024 bytes; or, when there is no single
  file, the live stream's HLS or DASH manifest; or YouTube's refusal and its reason; or an error.
- **Identity** that served it, **time** for the whole extraction in milliseconds, and the **network calls** made.
- **As expected**: playable videos give media or a manifest; refused ones give a refusal, not an error or nothing.
- **Bot checks** are named as such and counted: a run where YouTube asked to confirm the requester is not a bot
  measures the network, not Excor, and does not count. (A trial run from the cloud build machine on 2026-10-27
  served the first video and got bot checks for most of the rest, which is why no result is recorded from there.)

The aggregate is the share of videos that came out as expected. Refusals change over time (an upcoming stream goes
live, a block is lifted), so a "no" on one of those needs a look before it counts as a regression.

## Comparing with other tools

This project does not read or run competing extractors whose licenses the project rules exclude (QUESTIONS item
41 and PROVENANCE.md). A comparison, if wanted, is a separate step: run the other tool on the same videos, on the
same network and at the same time, and set its results beside this report.

## Results

| Date | Network | Commit | As expected | Notes |
|---|---|---|---|---|
| — | — | — | — | not run yet |
