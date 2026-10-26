# Changelog

Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and [SemVer](https://semver.org/).
Write for **humans**: what changed for people who use it, not internal details.

## [Unreleased]

### Added
- Phase 0 research: `docs/RESEARCH.md` (prior art, 26.3 tick anatomy, Java concurrency, Fabric surface, risks).
- Roadmap M0–M5 and a concrete task queue (`TODO.md`).
- Mod Menu integration requirement (optional dependency, restart warning) — D-007.
- M1 infrastructure: JSON config, named worker pool, offload/merge scheduler, lock-free metrics,
  `/multicore` command, Mod Menu config screen, offload registry with a safety contract.
- Phase 2.1: mod compatibility scan + classification (`compat.multicore.threadSafe` opt-in, Folia-style), whitelist/blacklist overrides, `/multicore compat` and `/multicore compat dump`.
- Phase 2.2: legacy handoff (`LegacyHandoff` queue drained on main thread) + `MultiCoreCompat` public API (`runOnMain`/`isThreadSafe`).
- Phase 2.4: debug thread-checker (`ThreadChecker`) wired into the scheduler, gated by the `debug` config flag.
- Phase 3.1: region model (`Region`, `RegionState`, `RegionSection`); Folia-style four-state region ownership; unit-tested.
- Phase 3.2: region scheduler (`RegionScheduler`); parallel region ticks on the worker pool + main-thread barrier.
- Phase 3.3: cross-region messages (`RegionMessage`, `RegionMessageQueue`) drained on the barrier; problematic-region fallback flag on `Region`.
- Phase 2.3: `RegionLocal` (region-local storage) + `RunOnRegion` (schedule task on region thread); unit-tested.
- Phase 3.4: determinism/corruption tests (`DeterminismTest`) verifying 0 workers ≡ vanilla, SAFE skips world-state offloads, ordering preserved, barrier waits for all work.
- Phase 4.1: CPU affinity helper (`CpuAffinity`) — best-effort, prints OS launch commands, never throws.
- Phase 4.2: client feasibility report (`docs/CLIENT-FEASIBILITY.md`) — recommendation A (server-side only).
- Phase 5: bilingual documentation (English US + Portuguese PT) — README, docs, test-server.
- `docs/BENCHMARKS.md` — the measurement plan and the go/no-go point before Phase 3 (D-012).
- `docs/SPARK-PROFILING.md` — Spark profiling guide.
- `REVIEW.md` — independent review (R-001, R-002) with 5-wave code review.
- `test-server/` — test server scripts, plan, stress mod, result sheets.
- Servidor real testado: mod carrega, comandos funcionam, sem crash. 2 jogadores: MSPT med 8.4→8.9ms (6 threads), max 78→65ms.
- `scripts/benchmark-compare.sh` — automated benchmark script.
- `docs/DOCUMENTATION-PLAN.md` — bilingual documentation plan.
- `REVIEW.md` — an independent review of the M1 code and of an external review (R-001).

### Changed
- The entrypoint is now the common `main`/`ModInitializer`, so the mod also runs on an integrated
  server (singleplayer/LAN) — D-013.
- The README states clearly that SAFE mode protects Fabric-event mods but **not** mixin-based mods.

### Fixed
- The scheduler now waits for offloaded work (barrier) and merges in submission order, not completion
  order.
- Worker tasks report exceptions again (they were captured in unread `Future`s).
- Command output is locale-independent (`Locale.ROOT`).

### Security

### Removed

## [0.0.0] - 2026-10-21

- Project created from the template.
