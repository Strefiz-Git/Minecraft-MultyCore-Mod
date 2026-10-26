# PROGRESS — Minecraft MultiCore Mod

> **The project's working memory.** Read at the start of each session and after each context compression.
> Keep *Current state* short and truthful (≤ 30 lines). The session log goes below, most recent first.

## Current state

- **Current milestone:** M1 — Infrastructure + safe offload (Phase 3 partially done)
- **Last update:** 2026-10-22
- **Build/test health:** ✅ `./gradlew build` green · ✅ `scripts/check.sh` green (10 checks) · ✅ JUnit green
- **In progress:** T-023 benchmark go/no-go (RegionLocal/runOnRegion done)
- **Recommended next step:** T-023 — run the M1 benchmark and write the go/no-go report
- **Blockers / open questions:** Q-001 (Phase 3 approach) and Q-002 (Mod Menu restart flow) are open in `QUESTIONS.md`; they do not block M1/M2/3.
- **Risks / relevant technical debt:** Phase 3 (regionised tick) can corrupt worlds if rushed — SAFE default, debug instrumentation, determinism tests first (see `docs/RESEARCH.md` §8). SAFE mode protects Fabric-event mods but **not** mixin-based mods (documented). No CPU affinity yet (M4). `DeadlinePolicy` added so world-modifying work is never silently dropped on timeout (see `REVIEW.md` R-002).

## Session log

<!-- Entry template (copy upwards):

### YYYY-MM-DD HH:MM — short title  (duration: Xh Ym)
- **Done:** T-00X …; T-00Y …
- **Decisions:** D-00X (link in DECISIONS.md)
- **Findings / problems:** …
- **State on exit:** build ✅/❌ · tests ✅/❌ · branch · last commit
- **Next step:** …
-->

### 2026-10-22 — Phase 2.3: RegionLocal + runOnRegion
- **Done:** T-012 — `RegionLocal.create()`/`get(region)`/`set(region, value)` for region-local mod data, `RunOnRegion.runOnRegion(region, task)` to schedule on a region's thread. Unit-tested.  (ref: ROADMAP M2)
- **Decisions:** none new.
- **State on exit:** build ✅ · tests ✅ · branch `main` · last commit `feat(region): add cross-region messages + fallback flag`.
- **Next step:** T-023 — run the M1 benchmark and write the go/no-go report.

### 2026-10-22 — Phase 3.3: cross-region messages + fallback
- **Done:** T-016 — `RegionMessage` (cross-region message payload), `RegionMessageQueue` (per-region queue), `Region.messageQueue()` accessor, `Region.isProblematic()`/`markProblematic()`/`clearProblematic()` fallback flag, `RegionScheduler.awaitBarrier()` drains message queues on the main thread. Unit-tested.  (ref: ROADMAP M3)
- **Decisions:** none new.
- **State on exit:** build ✅ · tests ✅ · branch `main` · last commit `feat(region): add region scheduler with parallel tick + barrier`.
- **Next step:** T-023 — run the M1 benchmark and write the go/no-go report.

### 2026-10-22 — Phase 3.2: region scheduler + barrier
- **Done:** T-015 — `RegionScheduler` schedules READY regions to tick in parallel on the worker pool, `awaitBarrier()` joins on the main thread and marks regions not-ticking. Unit-tested.  (ref: ROADMAP M3)
- **Decisions:** none new.
- **State on exit:** build ✅ · tests ✅ · branch `main` · last commit `feat(region): add region scheduler with parallel tick + barrier`.
- **Next step:** T-023 benchmark go/no-go, or T-016 cross-region messages.

### 2026-10-22 — Phase 3.1: region model (Folia-style)
- **Done:** T-014 — `Region` (four states, section ownership, region-local data), `RegionState`, `RegionSection` (N×N chunk grid). All pure, unit-tested without Minecraft.  (ref: ROADMAP M3)
- **Decisions:** none new.
- **State on exit:** build ✅ · tests ✅ · branch `main` · last commit `feat(region): add region model (Folia-style) with unit tests`.
- **Next step:** T-015 (region scheduler + barrier) or T-023 (benchmark go/no-go).

### 2026-10-22 — Phase 2.4: debug thread-checker + wiring into scheduler
- **Done:** `ThreadChecker` (debug-only, warns when world/region state is accessed off the owner thread), wired into `TaskScheduler.drainToMain()` and gated by `config.debug()`; unit tests; build/tests/check green.
- **Decisions:** none new.
- **State on exit:** build ✅ · tests ✅ · branch `main` · last commit `feat(thread): add debug thread-checker + wire into scheduler`.
- **Next step:** T-023 — run the M1 benchmark and write the go/no-go report.

### 2026-10-22 — Phase 2.2: legacy handoff + MultiCoreCompat public API
- **Done:** T-011 — `LegacyHandoff` (queue + drain on main thread, exceptions don't propagate), `MultiCoreCompat` public API (`runOnMain`/`isThreadSafe`/`setHandler`), wired into `MultiCoreMod` (implements `MultiCoreCompat.Handler`, `legacyHandoff.drainToMain()` on `END_SERVER_TICK`); unit tests; build/tests/check green.
- **Decisions:** none new.
- **State on exit:** build ✅ · tests ✅ · branch `main` · last commit `feat(compat): add legacy handoff + MultiCoreCompat public API`.
- **Next step:** T-012 — `RegionLocal` / `runOnRegion` (Phase 2.3), then T-013 instrumentation, then T-023 benchmark go/no-go.

### 2026-10-22 — Phase 2.1: mod scan + classification + /multicore compat dump
- **Done:** T-010 — `ModClassification`, `ModClassifier` (pure, reads `custom.multicore.threadSafe`), `ModCompatibility.scan()` via Fabric Loader with whitelist/blacklist overrides; wired into `MultiCoreMod` (StatusSource.compatSummary/compatDump); `/multicore compat` and `/multicore compat dump`; unit tests for classifier + compatibility; build/tests/check green.
- **Decisions:** none new.
- **State on exit:** build ✅ · tests ✅ · branch `main` · last commit `feat(compat): add mod scan + legacy/compatible classification, /multicore compat dump`.
- **Next step:** T-011 — legacy hand-off (Phase 2.2).

### 2026-10-22 — M1: second review fixes (deadline + deadline policy + icon + FabricMC refs)
- **Done:** fixed `TaskScheduler.awaitAll` — it now waits against **one shared deadline** instead of dividing the timeout per task (a slow task was starving the others and dropping results in silence). Added `DeadlinePolicy` (`SKIP`/`WAIT`) derived from `Safety`: world-modifying work is never dropped, metrics work may skip. Moved `icon.png` to `src/main/resources/assets/multicore/icon.png`, added `"icon"` to `fabric.mod.json`, removed `logo.txt` (chat transcript). Recorded FabricMC GitHub references in `ARCHITECTURE.md` (external refs section).
- **External review (Claude, 2nd pass):** confirmed B1–B5 fixes and the honest feasibility notes; found the new deadline bug (above) and reminded to record FabricMC org links if we ever build our own modified Fabric server jar.
- **Decisions:** none new this pass (D-012, D-013 stand).
- **State on exit:** build ✅ · tests ✅ · branch `main` · last commit `fix(thread): single deadline for awaitAll + deadline policy per safety`.
- **Next step:** T-010 — mod compatibility scan (Phase 2).

### 2026-10-21 — M1: infrastructure + offload + Mod Menu + external review fixes
- **Done:** T-003 config · T-004 worker pool · T-005 scheduler · T-006 metrics + `/multicore` · T-007 Mod Menu screen · T-008 wired the entrypoint · T-009 offload registry + first safe offload + `docs/BENCHMARKS.md`.
- **External review (Claude):** confirmed the plan is honest and the base is solid; found real bugs. Fixed: (1) entrypoint now `main`/`ModInitializer` so singleplayer/integrated server works; (2) `TaskScheduler` now **waits** for offloaded work and merges in **submission order** (was completion order); (3) `WorkerPool.submit` uses `execute` so exceptions are no longer swallowed; (4) stale docs updated. See `REVIEW.md`.
- **Decisions:** D-012 (go/no-go benchmark after M1), D-013 (main entrypoint).
- **State on exit:** build ✅ · tests ✅ · branch `main` · last commit `docs: add independent review and go/no-go plan`.
- **Next step:** T-010 — mod compatibility scan.

### 2026-10-21 — Phase 0: research + template + roadmap
- **Done:** copied the `Repo-Template` and ran `init.sh`; wrote `docs/RESEARCH.md`; filled `ROADMAP.md`, `TODO.md`, `DECISIONS.md`, `README.md`, `ARCHITECTURE.md` and the `docs/*` files.
- **Decisions:** D-003…D-011.
- **Findings / problems:** `bash` is not on PATH — invoke `C:\Program Files\Git\bin\bash.exe` with a script file. Gradle is not on PATH; the wrapper dist (9.8.0) is cached. Java 25 is installed.
- **State on exit:** build ❌ (no build files) · tests ❌ · branch `main` · last commit `chore: initialize project from template`.

## Archive

_Old entries (> 20 sessions) summarized here._
