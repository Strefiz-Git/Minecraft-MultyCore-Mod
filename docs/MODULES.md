# MODULES — Minecraft MultiCore Mod

> **Catalog** of modules and functions/components that are **reusable**. The first thing to check **before writing new code** (skill `reusable-modules`) and the last thing to update **before closing a task** (skill `code-documentation`).
> Quick search: `bash scripts/find-reuse.sh <term> [term…]`.
> Rule: if it is exported and used (or usable) outside its file, it gets a row here. Code and catalog change in the **same commit**. When they disagree, the code wins.

## Modules

| Module | Path | Purpose (1 sentence) | Public API (summary) | Depends on |
|--------|---------|---------------------|----------------------|------------|
| `config` | `config/` | parse/validate/persist the JSON config | `ConfigStore.load()/save()`, `MultiCoreConfig.normalized()`, `ConfigMode` | — |
| `thread` | `thread/` | own the worker threads and identify the main thread | `WorkerPool.start()/submit()/shutdown()`, `MainThread.isCurrent()`, `TaskScheduler.drainToMain()` | — |
| `metrics` | `metrics/` | lock-free tick and per-thread timing | `TickMetrics.startTick()/endTick()/snapshot()/reset()` | — |
| `command` | `command/` | `/multicore` status/compat/regions/reload | `MultiCoreCommand.register(...)`, `StatusFormatter` | `metrics`, `compat` |
| `compat` | `compat/` | Mod Menu config screen + mod classification | `ModCompatibility.scan()`, `ModClassifier.classify()`, `ModMenuIntegration`, `MultiCoreConfigScreen` | `config` |
| `region` | `region/` | Folia-style region model (Phase 3) | `Region`, `RegionState`, `RegionSection`, `RegionScheduler`, `RegionMessage`, `RegionMessageQueue` | thread |
| _planned_ | `compat/ModCompatibility` | classify mods from `fabric.mod.json` | `classify(String)`, `isLegacy(String)` | `config` |
| _planned_ | `compat/LegacyHandoff` | queue legacy callbacks for the main thread | `enqueue(Runnable)`, `drain()` | `config` |
| _planned_ | `region/Regionizer` | Folia-style region maintenance | `addChunk`, `removeChunk`, `tryMarkTicking`, `markNotTicking` | `config` |
| `region/RegionScheduler` | run regions in parallel and join | `tickRegions(...)`, `awaitBarrier()` | `thread`, `compat` |

> Replace `_planned_` rows with real rows (and real signatures) as the code lands. Do not keep a row
> that no longer exists.

## Reusable functions, types and components

Group by **concept**, not by file. _(Empty until the first module is implemented.)_

| Name | Path | Signature | What it does | Pure? | Errors | Used by |
|------|---------|------------|-----------|-------|-------|-----------|
| `ConfigStore` | `config/ConfigStore.java` | `ConfigStore(Path)` · `static defaultStore()` · `MultiCoreConfig load()` · `void save(MultiCoreConfig)` | JSON config load/save in `config/multicore.json`; defaults on any error | no (I/O) | never throws | `MultiCoreMod`, Mod Menu screen |
| `MultiCoreConfig` | `config/MultiCoreConfig.java` | `workerThreads()`, `mode()`, `debug()`, `modWhitelist()`, `modBlacklist()`, `threadCount()`, `multithreaded()`, `normalized()` | Config data + validation | `normalized()` yes, rest no | — | `WorkerPool`, `ModCompatibility` |
| `ConfigMode` | `config/ConfigMode.java` | `SAFE` \| `AGGRESSIVE` · `static fromString(String)` | Execution mode; unknown → `SAFE` | yes | — | `MultiCoreConfig` |
| `WorkerPool` | `thread/WorkerPool.java` | `WorkerPool(String,int)` · `start()` · `boolean submit(Runnable)` · `submitAll(...)` · `shutdown()` | Named worker pool with lifecycle | no | never throws | `MultiCoreMod`, `TaskScheduler` |
| `MainThread` | `thread/MainThread.java` | `static capture()` · `static boolean isCurrent()` · `static Thread get()` | Identify the server main thread | yes | — | all modules |
| `TaskScheduler` | `thread/TaskScheduler.java` | `TaskScheduler(WorkerPool, ThreadChecker)` · `submitOffload(Callable, Consumer)` · `submit(Runnable)` · `drainToMain()` | Offload work, **wait for it**, then merge in submission order on the main thread | no | never throws | `MultiCoreMod`, `region` |
| `ThreadChecker` | `thread/ThreadChecker.java` | `debug(boolean)` · `assertMainThread(String)` · `assertRegionOwner(String, Thread)` | Debug thread-safety warnings | no | never throws | `TaskScheduler`, `MultiCoreMod` |
| `CpuAffinity` | `thread/CpuAffinity.java` | `pinCurrentThread(int)` · `printLaunchCommand(int)` · `isWindows()` · `isLinux()` | Best-effort CPU affinity (Phase 4) | no | never throws | `MultiCoreMod` (optional) |
| `OffloadRegistry` | `thread/OffloadRegistry.java` | `OffloadRegistry(TaskScheduler)` · `register(name, safety, work, merge)` · `submitAll(Safety)` | Named offloads filtered by safety level | no | never throws | `MultiCoreMod` |
| `Safety` | `thread/Safety.java` | `PURE` \| `READ_ONLY` \| `WORLD_STATE` | Contract for how much of the world an offload may touch | yes | — | `OffloadRegistry` |
| `MetricsRollup` | `metrics/MetricsRollup.java` | `MetricsRollup(TickMetrics)` · `String hottest(int)` | Pure "hottest labels" aggregation | yes | — | `MultiCoreMod` |
| `TickMetrics` | `metrics/TickMetrics.java` | `startTick()` · `endTick()` · `beginWork/endWork` · `snapshot()` · `reset()` | Lock-free tick and per-thread timings | no | — | `command`, mixins |
| `MultiCoreCommand` | `command/MultiCoreCommand.java` | `register(CommandDispatcher, StatusSource)` · `registerEvents(StatusSource)` | `/multicore` command tree | no | — | `MultiCoreMod` |
| `StatusFormatter` | `command/StatusFormatter.java` | `status(StatusSource)` · `compat(String)` · `regions(String)` · `reloadWarning()` | Pure command text formatting | yes | — | `MultiCoreCommand` |
| `StatusSource` | `command/StatusSource.java` | `mode()` · `workerThreads()` · `metrics()` · `metricsRollup()` · `compatSummary()` · `compatDump()` | Read-only state for the command | yes | — | `MultiCoreCommand` |
| `ModClassifier` | `compat/ModClassifier.java` | `static classify(ModMetadata)` | Pure: read `custom.multicore.threadSafe` | yes | — | `ModCompatibility` |
| `ModCompatibility` | `compat/ModCompatibility.java` | `scan()` · `isLegacy(id)` · `isCompatible(id)` · `forceLegacy(id)` · `forceCompatible(id)` · `classifications()` | Scan mods + whitelist/blacklist overrides | no | never throws | `MultiCoreMod`, `command` |
| `ModClassification` | `compat/ModClassification.java` | `LEGACY` \| `COMPATIBLE` | Safety level | yes | — | `compat` |
| `LegacyHandoff` | `compat/LegacyHandoff.java` | `create()` · `enqueue(Runnable)` · `drainToMain()` | Queue legacy callbacks for the main thread | no | never throws | `MultiCoreMod` |
| `MultiCoreCompat` | `compat/MultiCoreCompat.java` | `runOnMain(Runnable)` · `isThreadSafe()` · `setHandler(Handler)` | Public API for mod authors | yes | — | `MultiCoreMod` |
| `Region` | `region/Region.java` | `addSection(section)` · `removeSection(section)` · `tryMarkTicking()` · `markTickingOwner(thread)` · `markNotTicking()` · `putRegionLocal/getRegionLocal` | Region lifecycle + section ownership | no | `IllegalStateException` if ticking | `RegionScheduler`, mixins |
| `RegionState` | `region/RegionState.java` | `TRANSIENT` \| `READY` \| `TICKING` \| `DEAD` | Region lifecycle state | yes | — | `Region`, `Regionizer` |
| `RegionSection` | `region/RegionSection.java` | `RegionSection(x,z)` · `static fromChunk(chunkX, chunkZ, chunkShift)` | Section coordinate (N×N chunk grid) | yes | — | `Region`, `Regionizer` |
| `RegionMessage` | `region/RegionMessage.java` | `RegionMessage(source, target, payload, onDeliver)` | Cross-region message payload | yes | — | `RegionMessageQueue` |
| `RegionMessageQueue` | `region/RegionMessageQueue.java` | `create()` · `enqueue(message)` · `drain()` | Per-region message queue | no | never throws | `Region`, `RegionScheduler` |
| `RegionLocal` | `compat/RegionLocal.java` | `create()` · `get(region)` · `set(region, value)` | Region-local storage for mod authors | yes | — | `compat` |
| `RunOnRegion` | `compat/RunOnRegion.java` | `runOnRegion(region, task)` | Schedule task on region owner thread | yes | — | `compat` |
| `ModMenuIntegration` | `compat/ModMenuIntegration.java` | `implements ModMenuApi` · `getModConfigScreenFactory()` | Expose the config screen to Mod Menu | no | — | Mod Menu |

## Known duplications (to resolve)

_None yet._

## Catalog conventions

- Remove the row when you remove the code. Don't leave orphan entries.
- Signature or errors changed → update the row **and** the docstring **and** the module's README.
- A private function (used only inside its own module) does **not** go here.
