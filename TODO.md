# TODO — Minecraft MultiCore Mod

> The agent's task queue. Order = priority. A task = something that fits in a short session and has **verifiable acceptance criteria**.
> Format: `- [ ] T-NNN [P0–P3] [size S/M/L] Title — acceptance: …  (ref: ROADMAP M#, BUGS B-NNN)`
> P0 = blocks everything · P1 = current milestone · P2 = important · P3 = when there is time.
> L tasks (> ~2h) must be broken down before starting (skill `planning`).

## Now

_Empty — Phase 3.2 region scheduler done; next is T-023 benchmark go/no-go._

## Next

- [ ] T-017 [P2] [M] Phase 3.4 — determinism/corruption tests — acceptance: same seed + same tick count produces identical world state vs vanilla in SAFE mode  (ref: ROADMAP M3)
- [ ] T-023 [P2] [M] Run the M1 benchmark and write the go/no-go report — acceptance: fill the table in `docs/BENCHMARKS.md` for players-together and players-spread scenarios (with/without the mod), include a Spark profile, and state go/no-go for Phase 3  (ref: D-012)

## Later

- [ ] T-018 [P3] [M] Phase 4.1 — test on Linux + Windows dedicated servers — acceptance: documented results, no OS-specific crash  (ref: ROADMAP M4)
- [ ] T-019 [P3] [S] Phase 4.2 — optional CPU affinity — acceptance: flag enables FFM/JNI affinity or prints the correct launcher flags; clear no-op warning  (ref: ROADMAP M4, D-006)
- [ ] T-020 [P3] [M] Phase 4.3 — README + tested-mods table + `/multicore` subcommands  (ref: ROADMAP M4)
- [ ] T-021 [P3] [M] Phase 5.1 — client feasibility report — acceptance: written report, no client code merged  (ref: ROADMAP M5)

## Blocked (waiting for the user's answer — see QUESTIONS.md)

- [ ] T-022 [P1] [S] Confirm Phase 3 approach + Mod Menu restart flow — acceptance: user answers in `QUESTIONS.md`; then adjust M3 tasks  (ref: QUESTIONS Q-001, Q-002)

## Done (last 20; the rest goes to PROGRESS.md › Archive)

- [x] T-000 [P1] [S] Apply the repository template — acceptance: template copied, placeholders replaced, first commit  (ref: ROADMAP M0)
- [x] T-000b [P1] [M] Phase 0 research — acceptance: `docs/RESEARCH.md` written from web + local jar inspection; Mod Menu requirement recorded  (ref: ROADMAP M0)
- [x] T-001 [P1] [M] Scaffold the Gradle/Fabric 26.3 project — acceptance: wrapper (9.8.0) committed, `./gradlew build` produces `build/libs/multicore-0.1.0.jar`  (ref: ROADMAP M0)
- [x] T-002 [P1] [M] Fill the management docs for the real stack — acceptance: `AGENTS.md` commands real, `ARCHITECTURE.md` filled, `scripts/check.sh` green (10 checks)  (ref: ROADMAP M0)
- [x] T-003 [P1] [M] Phase 1.1 — config module — acceptance: JSON config with `workerThreads`/`mode`/lists/`debug`, validated and round-trip tested  (ref: ROADMAP M1, D-006, D-008)
- [x] T-004 [P1] [M] Phase 1.2 — worker thread manager — acceptance: named pool, lifecycle, test proves work runs off the main thread  (ref: ROADMAP M1)
- [x] T-005 [P1] [M] Phase 1.3 — offload/merge scheduler — acceptance: offload on workers, merge on main before tick end, ordering tested  (ref: ROADMAP M1)
- [x] T-006 [P1] [M] Phase 1.4 — metrics + `/multicore status` — acceptance: lock-free MSPT/per-thread metrics + command, formatter unit-tested  (ref: ROADMAP M1)
- [x] T-007 [P1] [M] Phase 1.5 — Mod Menu config screen — acceptance: integration + screen + lang files, persists, restart warning, optional dependency  (ref: ROADMAP M1, D-007)
- [x] T-008 [P1] [M] Wire the entrypoint — acceptance: config/pool/scheduler/metrics/command wired; lifecycle events; build green  (ref: ROADMAP M1)
- [x] T-009 [P1] [S] First safe offload + benchmark — acceptance: `OffloadRegistry` with a `Safety` contract, `MetricsRollup` offload, `docs/BENCHMARKS.md`  (ref: ROADMAP M1)
- [x] T-010 [P2] [M] Phase 2.1 — mod scan — acceptance: `ModCompatibility.scan()`, `ModClassifier.classify()` reads `custom.multicore.threadSafe`, whitelist/blacklist overrides, `/multicore compat` + `dump`  (ref: ROADMAP M2, D-005)
- [x] T-011 [P2] [M] Phase 2.2 — legacy hand-off — acceptance: `LegacyHandoff` queue + drain on main thread, `MultiCoreCompat.runOnMain`/`isThreadSafe` public API, exceptions don't propagate  (ref: ROADMAP M2)
- [x] T-014 [P2] [L] Phase 3.1 — region model — acceptance: `Region`, `RegionState`, `RegionSection`; four states; add/remove section guarded by ticking; unit-tested in isolation  (ref: ROADMAP M3)
- [x] T-015 [P2] [L] Phase 3.2 — region scheduler + barrier — acceptance: per-level regions tick in parallel on workers, main thread joins, legacy callbacks run after  (ref: ROADMAP M3)
- [x] T-016 [P2] [L] Phase 3.3 — cross-region messages + fallback — acceptance: `RegionMessage`/`RegionMessageQueue`, drain on barrier, problematic flag on Region  (ref: ROADMAP M3)
- [x] T-012 [P2] [M] Phase 2.3 — `RegionLocal` / `runOnRegion` — acceptance: region-local storage + cross-region run helper documented, tested, listed in `docs/MODULES.md`  (ref: ROADMAP M2)
