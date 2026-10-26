# Benchmarks — Minecraft MultiCore Mod

> How we measure whether the mod actually helps, and the **go/no-go** point before Phase 3.
> Skill: `testing-verification`. Nothing is "faster" until it is measured here.

## Why this exists

Phase 1 is mostly infrastructure: on its own it does **not** speed the game up, because vanilla
already runs chunk I/O, chunk generation and light updates on background threads (see
`docs/RESEARCH.md` §3). The real gain is expected from the regionised tick (Phase 3). This document
is the measurement plan that decides whether Phase 3 is worth building.

## What we measure

| Metric | Source | Target |
|--------|--------|--------|
| MSPT (ms/tick) | `/multicore status` and Spark | lower is better; 20 TPS = ≤ 50 ms |
| Tick time distribution | Spark `/spark tps` + health report | where the time goes |
| Per-thread work | `/multicore status` › per-thread | prove workers are used |
| TPS under load | `/spark tps` | stays at 20 |

## Required setup

- A dedicated server: Fabric 26.3, Fabric API, this mod.
- [Spark](https://modrinth.com/mod/spark) (profiling + tick health).
- A **bot/player simulator** mod to create load (e.g. a headless player simulator). Record which
  one you use.
- Two identical worlds: same seed, same modpack, same JVM flags, same `server.properties`.

## Scenarios

The review's key insight is that the benefit depends entirely on **player distribution**, so we test
both:

### S-A — players together

All simulated players in the same area (one loaded region). Expected: **little or no gain**, because
the whole world is one region. This is the baseline that proves the mod does not make things worse.

### S-B — players spread out

Simulated players far apart (e.g. 4–8 groups, each ~500+ blocks from the next). Expected: **the gain
appears here**, because each group is an independent region. This is the target scenario.

## Procedure

1. **Baseline (no mod).** Start the server without `multicore-*.jar`. Warm up 5 minutes. Run the
   scenario for 10 minutes. Record MSPT (average, 95th percentile) and TPS.
2. **With the mod, SAFE, `workerThreads = 0`.** Same world, same seed. This must be **equivalent to
   the baseline** — if it is not, there is a bug.
3. **With the mod, SAFE, `workerThreads = N`** (N = 2, 4, 6). Record the same numbers for S-A and S-B.
4. **Profile.** Run `/spark profiler --timeout 60` during the worst scenario and look at the top
   methods. Record whether the cost is entities, pathfinding, block ticks, or something else.
5. **Compare** and write the numbers in a table.

## Results table (template)

| Run | Mod | Mode | Workers | Scenario | Avg MSPT | p95 MSPT | TPS | Notes |
|-----|-----|------|---------|----------|----------|----------|-----|-------|
| 1 | no | — | — | S-A | | | | baseline |
| 2 | yes | SAFE | 0 | S-A | | | | must equal run 1 |
| 3 | yes | SAFE | 2 | S-A | | | | |
| 4 | yes | SAFE | 4 | S-A | | | | |
| 5 | yes | SAFE | 2 | S-B | | | | |
| 6 | yes | SAFE | 4 | S-B | | | | |
| 7 | yes | SAFE | 6 | S-B | | | | |

## Methodology

### Automated benchmark (headless, no human intervention)

The `scripts/benchmark-compare.sh` script automates the full benchmark:

1. **Same world, same seed**: uses the same server directory with the same seed.
2. **Fixed JVM flags**: `-Xmx2G` (or configurable).
3. **Fixed scenario**: `dense` (all entities together) or `clusters` (N groups, each PER_CLUSTER entities, spacing SPACING blocks apart).
4. **Multiple configurations**: vanilla (no mod), SAFE mode with 1/3/5 worker threads.
5. **Multiple repetitions**: at least 3 runs per configuration (run the script 3 times).
6. **Warmup**: 5 seconds of warmup before measurement.
7. **Measurement**: 60 seconds of MSPT sampling.
8. **Metrics captured**: avg MSPT, p50, p95, p99, max, ticks >50ms, avgCoresBusy, GC activity, heap usage, thread count.

### Results table (auto-generated from JSON)

| Config | Avg MSPT | p50 | p95 | p99 | Max | >50ms | Cores busy | GC (ms) |
|--------|----------|-----|-----|-----|-----|-------|------------|---------|
| vanilla | | | | | | | | |
| SAFE 2 threads | | | | | | | | |
| SAFE 4 threads | | | | | | | | |
| SAFE 6 threads | | | | | | | | |

### Go/no-go criteria (end of Phase 1)

After the runs, decide with data:

- If the spread scenario shows **measurable MSPT reduction** (> 20% improvement) with 4+ threads, **proceed to Phase 3**.
- If the cost is **chunk generation/IO or light**, the mod should **not** duplicate C2ME/Lithium — reconsider scope.
- If S-B shows no gain, Phase 3 is not worth it for the target hardware; stop and keep M1 as a compatibility/monitoring mod.
- If AGGRESSIVE mode shows instability (crashes, corruption), keep SAFE as the only mode.

After the runs, decide with data:

- If the profile shows the cost is **world-state ticking** (entities, block ticks, pathfinding) and
  the spread scenario has enough independent groups, **proceed to Phase 3**.
- If the cost is **chunk generation/IO or light**, the mod should **not** duplicate C2ME/Lithium —
  reconsider scope.
- If S-B shows no gain, Phase 3 is not worth it for the target hardware; stop and keep M1 as a
  compatibility/monitoring mod.

## Expected ceiling (honest)

The gain is bounded by `min(workerThreads + 1, independentGroups)`:

- Default (1 worker, 2 threads): ceiling ≈ **2×**, before overhead.
- 6 threads and 6+ distant groups: up to ≈ **5–6×** if the hardware has real cores.
- Players together: **≈ 1×** (one region).
- Chunk generation/IO is **not** our gain (vanilla/C2ME already handle it).
