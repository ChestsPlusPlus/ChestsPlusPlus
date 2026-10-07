# Benchmarking v2 against v3

v2 runs on Paper 1.21.7 and v3 on Paper 26.3, so their tick times can't be compared directly: Minecraft itself changed in between. The
benchmark measures each plugin against the same server without it, on its own Minecraft version, and compares those overheads.

| Setup | Server | Plugins |
|---|---|---|
| `v2-baseline` | Paper 1.21.7 | probe only |
| `v2` | Paper 1.21.7 | probe + ChestsPlusPlus v2 |
| `v3-baseline` | Paper 26.3 | probe only |
| `v3` | Paper 26.3 | probe + ChestsPlusPlus v3 |

## Running it

```bash
./gradlew benchmarkWorlds -Pchestsplusplus.acceptMinecraftEula=true
```

```bash
./gradlew benchmark -Pchestsplusplus.acceptMinecraftEula=true
```

- In PowerShell, quote each `-P` argument (`"-Pchestsplusplus.acceptMinecraftEula=true"`): unquoted, PowerShell splits it at the
  first dot. Setting `chestsplusplus.acceptMinecraftEula=true` in `~/.gradle/gradle.properties` saves typing it.
- `benchmarkWorlds` needs Maven for the v2 jar, as `v2UpgradeFixture` does (see [testing.md](testing.md#v2-upgrade)). Rebuild the worlds
  after changing the layout or the v3 import. Changing other v3 code only needs `benchmark`, which always uses the current jar.
- `benchmark` takes about 30 minutes with the defaults (four setups, each a 2-minute warm-up and 5 minutes measured). It writes
  `build/reports/benchmark/report.md` and `results.json`.
- Close everything else that uses the CPU, and keep the machine plugged in. Compare runs from the same machine only.

| Property (`-Pchestsplusplus.benchmark.<name>`) | Default | |
|---|---|---|
| `chestlinks`, `autocrafters`, `filters` | 100, 25, 25 | Cells in the world (`benchmarkWorlds`) |
| `warmup`, `duration` | 120, 300 | Seconds before and during measuring. Cells run dry after about 10 minutes, so keep the sum below that |
| `rounds` | 1 | Repeats every setup in turn; the report shows medians. Use 3 when a difference is small |
| `only` | all | Comma-separated setups, e.g. `v3-baseline,v3` to check a v3 change quickly |
| `jfr` | false | Also records a JFR profile of each measured window into the report folder |

## The world

`v2fixture benchmark` lays the cells out through v2's own classes, then v3 upgrades a copy, imports the groups and converts the
filters. Both worlds have the same blocks. Every cell ends in a barrel, and the report counts what reached the barrels while measuring.

- **ChestLink**: a chest of cobblestone feeds a hopper into a linked chest. A hopper under a second chest in the same group drains it.
- **AutoCraft**: a linked chest of coal and sticks sits on a crafting table that makes torches into a hopper below.
- **Filter**: a hopper filtered to andesite pulls from a chest of andesite and dirt.

Mob spawning, the daylight and weather cycles and random ticks are off, and nobody joins. The cells' chunks are force-loaded and
`pause-when-empty-seconds` is 0, so the server keeps ticking.

## Reading the report

- **Main thread CPU ms/tick** is the steadiest figure for plugin overhead. It counts only the server thread's work and ignores the sleep
  between ticks. **MSPT** (milliseconds per tick) is the time each tick took. The p95 and p99 percentiles show spikes the mean hides.
- On Windows, thread CPU time is counted in steps of about 15 ms, so the CPU figures under-count ticks that take under a millisecond
  (the baselines, mostly). Trust MSPT there, or run on Linux.
- **Process CPU** also counts the other threads, such as v3's database writer.
- **Work done** shows both plugins moved the same items. When one moved less, its overhead is cheaper than it looks.
- The baselines do less hopper work than the plugin runs: nothing drains a ChestLink, so the feeding hopper stops once its chest is full.
  The overhead therefore includes the cost of moving those items as well as the plugin's own code. That cost is the same for v2 and v3,
  so the comparison between them is still fair.
- A difference smaller than the gap between rounds is noise.

## Finding out why

`-Pchestsplusplus.benchmark.jfr=true` writes a `.jfr` per setup while the benchmark measures. Open it in JDK Mission Control and use the
Method Profiling and Allocations pages, filtered to `com.jamesdpeters`. The hot paths in [AGENTS.md](../AGENTS.md#runtime-rules) shouldn't
allocate at all.

For a live server, [spark](https://spark.lucko.me/) (`/spark profiler`) gives the same view as a flame graph.
