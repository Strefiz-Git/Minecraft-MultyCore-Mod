# Conventions — Minecraft MultiCore Mod

> Loaded automatically in every session (via `opencode.json › instructions`). **Keep it short and practical.**
> Precedence: what existing code already does > this document > the agent's general preferences.

## Structure

- Code in `src/main/java/com/strefiz/multicore/`, tests in `src/test/java/` mirroring it.
- One module = one responsibility. Files < ~300 lines, functions < ~40 lines (guidelines, not dogma).
- Group by feature: `config`, `thread`, `metrics`, `command`, `compat`, `region`, `mixin`.
- No generic `utils`/`helpers`: group by concept.

## Reuse and modules

- **Search before writing:** `docs/MODULES.md` + `bash scripts/find-reuse.sh <terms>`.
- Pure functions, explicit inputs/outputs, no globals; I/O and state at the edges; dependencies as parameters.
- 1-2-3 rule: 1st consumer → private to the feature · 2nd consumer → `shared/<concept>/` · 3rd case → generalize.
- Each module: single entry point (minimal public API), `README.md`, tests on the public API. Always import through the entry point.

## Naming

- Files/folders: `PascalCase.java` for types; packages lowercase.
- Types/classes: `PascalCase` · methods/variables: `camelCase` · constants: `UPPER_SNAKE_CASE`.
- Names say **what**, not **how**. Booleans: `isX`, `hasX`, `canX`.
- Thread names: `multicore-worker-N` (visible in crash reports and `/multicore status`).

## Style

- Java, formatted with the default IntelliJ/Gradle style used by the sibling mods (4 spaces, no tabs).
- Comments explain **why**; code explains **how**. No dead code, no orphan `TODO`s.
- Never swallow an exception silently; log with a level and a useful message.

## Documentation

- Docstrings on **everything** exported: what, parameters, return, errors, side effects, example.
- `README.md` in each module; a line in `docs/MODULES.md` for whatever is reusable.
- Language: identifiers and docstrings in English; in-game strings in `assets/multicore/lang/{en_us,pt_pt}.json`.
- **Bilingual docs:** English (US) is the source of truth. User-facing docs (`README.md`, `docs/RESEARCH.md`,
  `docs/BENCHMARKS.md`, `docs/SPARK-PROFILING.md`, `docs/CLIENT-FEASIBILITY.md`, `test-server/TEST-PLAN.md`)
  get a Portuguese translation in `docs/pt/` (or `README-pt.md` at root). Contributor/agent-facing docs
  (`AGENTS.md`, `ARCHITECTURE.md`, `CONVENTIONS.md`, `MODULES.md`, `TESTING.md`, `GLOSSARY.md`,
  `DESIGN.md`, `ROADMAP.md`, `TODO.md`, `PROGRESS.md`, `DECISIONS.md`, `QUESTIONS.md`, `BUGS.md`,
  `REVIEW.md`, `IDEAS.md`, `CHANGELOG.md`) stay English-only. See `docs/DOCUMENTATION-PLAN.md`.
- Code and docs change in the same commit.

## Errors and logging

- Errors handled at the boundaries; actionable messages.
- Use SLF4J (`org.slf4j.Logger`) via `LogManager.getLogger("multicore")`; levels `debug/info/warn/error`.
- Never log secrets or personal data.

## Thread-safety rules (project-specific)

- World state is only touched by its **owner thread**. Any other access goes through a queue.
- Never call `Thread.sleep`/`wait` on the server main thread; never block a region thread on another region.
- Worker threads must be created by `WorkerPool` (named), never by ad-hoc `new Thread(...)`.
- Offloaded work must not advance RNG or mutate world state.

## Dependencies

- Prefer the JDK and what Minecraft already bundles (Gson, FastUtil, Guava, Brigadier, SLF4J).
- Mod Menu is `compileOnly` + `localRuntime` (never a hard dependency).
- New dependency: justify it in `DECISIONS.md`.

## Git

- Conventional Commits (in English). Branches: `feat/…`, `fix/…`, `chore/…` (long-running work can live in `ai/<topic>`).

## Stack-specific (Java 25 + Minecraft Fabric 26.3)

- Build: Gradle 9.8.0 wrapper, Loom 1.17-SNAPSHOT, Java 25 toolchain, Fabric API 0.160.5+26.3.
- Commands: `./gradlew build` · `bash scripts/check.sh`.
- Mixins: declare in `multicore.mixins.json`; keep them thin and put logic in modules.
- Config: JSON via Gson in `config/multicore.json`.
- Entrypoint: `"main"` → `ModInitializer` (runs on dedicated **and** integrated servers).

## Commands

See the table in `AGENTS.md` §1.
