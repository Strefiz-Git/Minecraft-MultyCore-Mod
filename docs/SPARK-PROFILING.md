# Spark Profiling Guide for MultiCore

> How to use Spark to profile the Minecraft server with and without the MultiCore mod.
> Spark is a performance profiling mod for Minecraft: https://modrinth.com/mod/spark

## Installation

1. Download Spark for Fabric 26.3: https://modrinth.com/mod/spark
2. Place `spark-1.10.187-fabric.jar` in `test-server/server/mods/`
3. Restart the server

## Key Commands

### TPS (Ticks Per Second)
```
/spark tps
```
Shows TPS from last 5s, 10s, 1m, 5m, 15m, plus tick duration percentiles and CPU usage.
- **Target:** 20.0 TPS (≤ 50 ms/tick MSPT)
- **Good:** *20.0 with low MSPT max
- **Bad:** < 20.0 or high MSPT max (> 100 ms)

### Profiler (CPU sampling)
```
/spark profiler --timeout 60
```
Samples the CPU for 60 seconds and generates a report. Upload the link to a browser.
- Look for: `MinecraftServer.tickServer`, `ServerLevel.tick`, `EntityTickList`, `ChunkMap.tick`
- With the mod: look for `multicore-worker-N` threads doing work

### Health Report
```
/spark health
```
Shows overall server health: TPS, MSPT, memory, CPU, thread count.

### Heap Dump
```
/spark heapdump
```
Dumps the JVM heap for memory analysis. Useful for detecting memory leaks in the worker pool.

## Profiling Procedure for MultiCore

### 1. Baseline (no mod)
```powershell
.\test-server\start-server.ps1 -WorkerThreads 0 -WithMod $false
```
1. Connect 2+ clients, spread apart
2. Wait 3 minutes for stabilization
3. Run `/spark tps` → record MSPT avg/max, TPS
4. Run `/spark profiler --timeout 60` → record top methods
5. Run `/spark health` → record CPU, memory

### 2. With mod, 2 threads
```powershell
.\test-server\stop-server.ps1
.\test-server\start-server.ps1 -WorkerThreads 1 -WithMod $true
```
1. Connect 2+ clients, spread apart
2. Wait 3 minutes
3. Run `/multicore status` → record MSPT, per-thread time
4. Run `/spark tps` → record MSPT avg/max, TPS
5. Run `/spark profiler --timeout 60` → look for worker threads
6. Run `/spark health` → record CPU, memory

### 3. With mod, 4 threads
```powershell
.\test-server\stop-server.ps1
.\test-server\start-server.ps1 -WorkerThreads 3 -WithMod $true
```
Repeat steps from #2.

### 4. With mod, 6 threads
```powershell
.\test-server\stop-server.ps1
.\test-server\start-server.ps1 -WorkerThreads 5 -WithMod $true
```
Repeat steps from #2.

## What to look for in the Spark profiler

### Vanilla (no mod)
- `MinecraftServer.tickServer` → 100% on one thread
- `ServerLevel.tick` → entities, block ticks, redstone all on one thread
- `ChunkMap.tick` → entity ticking on main thread

### With mod (SAFE mode)
- `MinecraftServer.tickServer` → still on main thread (SAFE mode)
- `multicore-worker-N` threads → should show up with offloaded work
- `TaskScheduler.drainToMain` → visible at tick boundary
- `TickMetrics` → should be negligible overhead

### With mod (AGGRESSIVE mode, Phase 3)
- Multiple region threads ticking in parallel
- No single thread dominating the tick time

## Key Metrics to Record

| Metric | Source | Target | Notes |
|--------|--------|--------|-------|
| MSPT (avg) | `/spark tps` or `/multicore status` | ≤ 50 ms | Lower is better |
| MSPT (max) | `/spark tps` | ≤ 100 ms | Spikes indicate lag |
| TPS | `/spark tps` | 20.0 | Must stay at 20 |
| CPU (process) | `/spark tps` | Lower with more threads | Should scale |
| Thread count | `/spark health` | main + workers | Verify pool size |
| Per-thread time | `/multicore status` | Workers doing work | Verify offload is active |

## Spark Profiler URL

After running `/spark profiler --timeout 60`, Spark uploads a report and prints a URL.
Open the URL in a browser to see:
- Flame graph of CPU usage
- Thread list (look for `multicore-worker-N`)
- Hot methods (where time is spent)

## Notes

- Spark's async-profiler engine is **not supported on Windows** (uses Java engine fallback). This is slower but still useful.
- On Linux, Spark uses async-profiler (native, more accurate).
- The profiler adds overhead; don't measure absolute TPS while profiling.
- Use `/spark tps` for accurate TPS measurement (no profiling overhead).
