# Minecraft MultiCore Mod

A **Fabric mod** that lets a normal Minecraft **server** use more than one core — *without*
breaking the mods that were not written for multithreading.

> **Status:** early development (M0/M1). The architecture and the full plan are in `ROADMAP.md`;
> the deep research is in `docs/RESEARCH.md`.

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

## Why (the honest version)

- Java cannot pin threads to cores, so the setting is a **worker-thread count**, not a core count.
  CPU affinity is an optional extra (see `ROADMAP.md` M4).
- Minecraft already runs **chunk I/O, chunk generation and light updates** on background threads.
  The remaining bottleneck is the **mutable world state** tick (entities, block ticks, redstone,
  pathfinding), which is why the big win only comes with the regionised tick (M3).
- Folia — the reference implementation — states that unmodified plugins have **~0 compatibility**.
  We take the same position: **a mod is thread-safe only if it declares it**, everything else is
  legacy and stays on the main thread.

## Requirements

| Component | Version |
|-----------|---------|
| Minecraft | **26.3** |
| Fabric Loader | **>= 0.19.5** |
| Fabric API | **0.160.5+26.3** (or compatible) |
| Java | **25** |
| Environment | The mod ships as `environment: "*"` so **Mod Menu** can show the config screen; all the parallel work is **server-side** (dedicated server or the server side of an integrated server), Linux / Windows. |
| Optional | [Mod Menu](https://modrinth.com/mod/modmenu) for the in-game config screen |

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

## Command

| Command | What it does |
|---------|--------------|
| `/multicore status` | threads, mode, MSPT, per-thread time |
| `/multicore regions` | regions per level and their owner thread (M3) |
| `/multicore compat` | legacy vs compatible mods |
| `/multicore compat dump` | debug report of off-thread world access (debug mode) |

## Compatibility with Mod Menu

The mod registers a `modmenu` entrypoint and only *suggests* Mod Menu (it is never required). It
uses the common `main` entrypoint, so it also runs on an **integrated server** (singleplayer/LAN):
the Mod Menu screen edits the same `config/multicore.json` the server reads, and the changes apply
after a restart. On a **dedicated server** there is no client GUI — the file and the command are
authoritative.

## Known limitations (read before using)

- **SAFE mode protects Fabric-event-based mods, not mixin-based mods.** Most Fabric mods use mixins
  (or access the world directly), and those run on whatever thread vanilla uses. If a mod mixes into
  `Entity.tick()`, `ServerLevel.tick()` or similar, it is **not** protected. This is the single
  biggest gap between the original idea and what is technically possible without a server fork.
- **Datapacks** that assume a single thread may misbehave.
- **Not deterministic**: world generation already varies run-to-run with the same seed (see C2ME).
- This is not a replacement for Lithium/C2ME/VMP/Moonrise — it is additive.

See `docs/RESEARCH.md` §7.3 and §8 for the full, honest risk list.

## Tested mods

| Mod | Version | Mode | Status | Notes |
|-----|---------|------|--------|-------|
| _None yet_ | | | | Run the benchmark before claiming compatibility. |

See `docs/BENCHMARKS.md` for the measurement procedure.

```bash
./gradlew build          # jar in build/libs/
bash scripts/check.sh    # full verification
```

Java 25 toolchain, Gradle 9.8.0 wrapper, Loom 1.17-SNAPSHOT.

## License

MIT — see `LICENSE`.
