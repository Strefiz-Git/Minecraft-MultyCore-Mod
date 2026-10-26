# Minecraft MultiCore Mod

A **Fabric mod** that lets a normal Minecraft **server** use more than one core — *without*
breaking the mods that were not written for multithreading.

> **Status:** Phase 3 in progress (regionised tick model + scheduler implemented; mixin layer pending).
> The architecture and the full plan are in `ROADMAP.md`; the deep research is in `docs/RESEARCH.md`;
> the independent code review is in `REVIEW.md`.

---

## Table of contents

1. [What it does](#what-it-does)
2. [Why (the honest version)](#why-the-honest-version)
3. [Requirements](#requirements)
4. [Install](#install)
5. [Configuration](#configuration)
6. [Commands](#commands)
7. [Compatibility with Mod Menu](#compatibility-with-mod-menu)
8. [How it works](#how-it-works)
9. [Known limitations](#known-limitations)
10. [Development](#development)
11. [License](#license)

---

## What it does

- Runs the safe, non-world-state work of the server tick on a **configurable pool of worker
  threads**, and gives the results back to the main thread before the tick ends.
- **SAFE mode (default):** mods that have not opted in keep running on the **main thread**, exactly
  as they do today. The mod routes their callbacks back to the main thread.
- **AGGRESSIVE mode (opt-in):** for servers whose modpack has been verified, the world tick itself is
  split into **independent regions** (the Folia-style design) so they run in parallel.
- Configurable number of worker threads (default `1` worker → 2 threads total: 1 main + 1 worker;
  raise it for more cores).
- In-game configuration through **Mod Menu**, with a clear **"restart to apply"** notice.
- `/multicore` command for status, threads, regions and the list of legacy mods.
- Thread-safety instrumentation in debug mode (`debug: true`) — logs off-thread world access.
- CPU-affinity helper (best-effort, prints OS launch commands when JNA is unavailable).

## Why (the honest version)

- Java cannot pin threads to cores, so the setting is a **worker-thread count**, not a core count.
  CPU affinity is an optional extra (see `ROADMAP.md` M4 and `docs/RESEARCH.md` §5.1).
- Minecraft already runs **chunk I/O, chunk generation and light updates** on background threads.
  The remaining bottleneck is the **mutable world state** tick (entities, block ticks, redstone,
  pathfinding), which is why the big win only comes with the regionised tick (M3).
- Folia — the reference implementation — states that unmodified plugins have **~0 compatibility**.
  We take the same position: **a mod is thread-safe only if it declares it**, everything else is
  legacy and stays on the main thread.
- **SAFE mode protects Fabric-event-based mods, not mixin-based mods.** Most Fabric mods use mixins
  (or access the world directly), and those run on vanilla's thread. This is the single biggest gap
  between the original idea and what is technically possible without a server fork. See
  `docs/RESEARCH.md` §7.3.

## Requirements

| Component | Version |
|-----------|---------|
| Minecraft | **26.3** |
| Fabric Loader | **>= 0.19.5** |
| Fabric API | **0.160.5+26.3** (or compatible) |
| Java | **25** (toolchain; Gradle runs on Java 21+) |
| Environment | Dedicated **server** (Linux / Windows). Also loaded on the server side of an integrated server. |
| Optional | [Mod Menu](https://modrinth.com/mod/modmenu) for the in-game config screen |
| Optional | [Spark](https://modrinth.com/mod/spark) for profiling and tick health |

## Install

1. Install Fabric Loader for 26.3 and Fabric API.
2. Drop `multicore-*.jar` into the server's `mods/` folder.
3. (Optional) add Mod Menu if you want the config screen in singleplayer/LAN.
4. Start the server once to generate `config/multicore.json`.
5. Edit the config, then **restart** (thread/region settings are applied at startup).

## Configuration (`config/multicore.json`)

```jsonc
{
  "workerThreads": 1,          // 1 main + 1 worker = 2 threads total; raise for more cores
  "mode": "SAFE",              // SAFE (default) | AGGRESSIVE
  "modWhitelist": [],          // only these mods may use region threads
  "modBlacklist": [],          // always treat these mods as legacy
  "debug": false               // thread-safety instrumentation + verbose logs
}
```

> Changing `workerThreads`, `mode` or the region settings **requires a restart**. The Mod Menu
> screen and `/multicore` save the file but cannot apply it live.

## Commands

| Command | What it does |
|---------|--------------|
| `/multicore status` | Threads, mode, MSPT, per-thread time, metrics rollup |
| `/multicore compat` | Legacy vs compatible mods (counts) |
| `/multicore compat dump` | Detailed list of all classified mods |
| `/multicore regions` | Regions per level and their owner thread (Phase 3) |
| `/multicore reload` | Saves config and shows a restart-required notice |

## Compatibility with Mod Menu

The mod registers a `modmenu` entrypoint and only *suggests* Mod Menu (it is never required). It
uses the common `main` entrypoint, so it also runs on an **integrated server** (singleplayer/LAN):
the Mod Menu screen edits the same `config/multicore.json` the server reads, and the changes apply
after a restart. On a **dedicated server** there is no client GUI — the file and the command are
authoritative.

## How it works

### Architecture

```
MultiCoreMod (entrypoint)
  ├── config: MultiCoreConfig (JSON, loaded at server start)
  ├── thread: WorkerPool (named fixed pool, never the common pool)
  │          TaskScheduler (offload work, barrier, merge on main thread)
  │          MainThread (identifies the server main thread)
  │          ThreadChecker (debug thread-safety warnings)
  │          CpuAffinity (best-effort, optional)
  ├── metrics: TickMetrics (lock-free MSPT + per-thread timing)
  ├── command: MultiCoreCommand (/multicore)
  │            StatusFormatter (pure text formatting)
  ├── compat: ModCompatibility (scan + classify + whitelist/blacklist)
  │           LegacyHandoff (queue callbacks for main thread)
  │           MultiCoreCompat (public API for mod authors)
  │           ModMenuIntegration (optional Mod Menu screen)
  └── region: Region, RegionState, RegionSection (Folia-style model)
              RegionScheduler (parallel tick + barrier)
              RegionMessage/RegionMessageQueue (cross-region)
              RegionLocal, RunOnRegion (public API)
```

### Tick flow (Phase 1 — current)

```
START_SERVER_TICK → metrics.startTick()
  → offloads.submitAll(maxSafety) → submit to worker pool
  → vanilla server tick (main thread)
  → END_SERVER_TICK → scheduler.drainToMain() (barrier + ordered merges)
  → legacyHandoff.drainToMain()
  → metrics.endTick()
```

### Tick flow (Phase 3 — planned)

```
tickChildren mixin → regionizer assigns regions
  → scheduler runs regions on workers (parallel)
  → barrier (all regions finish)
  → legacy callbacks on main thread
  → cross-region messages delivered
  → END_SERVER_TICK
```

### Mod compatibility

A mod is thread-safe **only if** it declares:
```json
"custom": { "multicore": { "threadSafe": true } }
```
in its `fabric.mod.json`. Everything else is legacy and stays on the main thread.

- **SAFE mode (default):** Fabric events dispatch on the main thread; workers never touch world state.
- **AGGRESSIVE mode (opt-in):** events dispatch on region threads; only declared-compatible mods may join.

### Public API for mod authors

```java
// Run code on the server main thread (safe from any thread):
MultiCoreCompat.runOnMain(() -> { /* touch world state */ });

// Check if the current context is thread-safe:
if (MultiCoreCompat.isThreadSafe()) { /* safe to access region data */ }

// Region-local storage (Phase 3):
RegionLocal<Integer> counter = RegionLocal.create();
counter.set(region, counter.get(region) + 1);
```

## Known limitations (read before using)

- **Mixin-based mods are not protected.** SAFE mode protects Fabric-event-based mods; a mod that
  mixes directly into vanilla tick methods may still race.
- **Datapacks** that assume a single thread may misbehave.
- **Not deterministic**: world generation already varies run-to-run with the same seed (see C2ME).
- This is not a replacement for Lithium/C2ME/VMP/Moonrise — it is additive.

See `docs/RESEARCH.md` §7.3 and §8 for the full, honest risk list.

## Tested mods

| Mod | Version | Mode | Status | Notes |
|-----|---------|------|--------|-------|
| _None yet_ | | | | Run the benchmark before claiming compatibility. |

See `docs/BENCHMARKS.md` for the measurement procedure.

## Development

```bash
./gradlew build          # jar in build/libs/
bash scripts/check.sh    # full verification (11 checks)
./gradlew test           # unit tests
```

Java 25 toolchain, Gradle 9.8.0 wrapper, Loom 1.17-SNAPSHOT.

### Project structure

```
src/main/java/com/strefiz/multicore/
  MultiCoreMod.java          # entrypoint (ModInitializer, main)
  config/                    # JSON config + validation
  thread/                    # WorkerPool, TaskScheduler, MainThread, ThreadChecker, CpuAffinity
  metrics/                   # TickMetrics, MetricsRollup
  command/                   # /multicore command + StatusFormatter
  compat/                    # ModCompatibility, LegacyHandoff, MultiCoreCompat, ModMenuIntegration
  region/                    # Region, RegionState, RegionSection, RegionScheduler, RegionMessage
  mixin/                     # (empty — Phase 3 continuation)
src/test/java/...            # 61+ unit tests (pure, no Minecraft dependency)
docs/                        # RESEARCH, ARCHITECTURE, CONVENTIONS, MODULES, TESTING, etc.
test-server/                 # test server + scripts + stress mod + test plan
```

### Documentation map

| File | What it is for |
|------|----------------|
| `ROADMAP.md` | Vision and milestones (M0–M5) |
| `TODO.md` | Task queue (Now/Next/Later/Blocked/Done) |
| `PROGRESS.md` | Current state + session log |
| `DECISIONS.md` | Decision log (D-001 to D-013) |
| `QUESTIONS.md` | Questions for the user + answers |
| `BUGS.md` | Known bugs and their state |
| `REVIEW.md` | Self-reviews (R-001, R-002) |
| `IDEAS.md` | Out-of-scope ideas (inbox) |
| `docs/RESEARCH.md` | Phase 0 research (prior art, tick anatomy, risks) |
| `docs/BENCHMARKS.md` | Benchmark procedure and go/no-go criteria |
| `docs/SPARK-PROFILING.md` | Spark profiling guide |
| `docs/CLIENT-FEASIBILITY.md` | Client feasibility report (Phase 5) |
| `docs/ARCHITECTURE.md` | How the system is organized |
| `docs/CONVENTIONS.md` | Code style, naming, structure |
| `docs/MODULES.md` | Catalog of reusable modules and functions |
| `docs/TESTING.md` | Testing strategy and commands |
| `docs/GLOSSARY.md` | Domain vocabulary |
| `docs/DESIGN.md` | Design system (Mod Menu screen) |

## License

MIT — see `LICENSE`.