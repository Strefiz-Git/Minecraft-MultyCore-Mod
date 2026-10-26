# Documentation Plan — MultiCore (Bilingual: English US + Portuguese PT)

> **Status:** Plan only — no files translated yet. Approved by the user on 2026-10-22.
> **Goal:** make the project global (English US as primary) while keeping a complete
> Portuguese (pt-PT) track for the Portuguese-speaking community.

---

## 1. Principles

1. **English (US) is the source of truth.** Every document is written first in English.
   Portuguese is a faithful translation, never a divergence.
2. **One canonical location per document.** No duplicated content; translations live in
   a dedicated `docs/pt/` tree and reference the English original.
3. **In-game strings stay bilingual** via `assets/multicore/lang/{en_us,pt_pt}.json` (already
   in place).
4. **AI-facing docs stay English only.** `AGENTS.md`, `DECISIONS.md`, `TODO.md`, `PROGRESS.md`,
   `QUESTIONS.md`, `BUGS.md`, `REVIEW.md`, `IDEAS.md`, `ROADMAP.md` are for agents and
   contributors — English only. Translating them would create stale copies and confuse agents.
5. **User-facing docs get both languages.** `README.md`, `docs/RESEARCH.md`, `docs/BENCHMARKS.md`,
   `docs/SPARK-PROFILING.md`, `docs/CLIENT-FEASIBILITY.md`, `test-server/TEST-PLAN.md`.
6. **Code comments and identifiers stay English.** Java identifiers, log messages, and
   docstrings are English. In-game strings use translatable keys.

---

## 2. Language split

### English (US) only — developer/agent facing

| File | Rationale |
|------|-----------|
| `AGENTS.md` | AI agent instructions (must be English) |
| `ROADMAP.md` | Milestones (agent + contributor facing) |
| `TODO.md` | Task queue (agent-facing) |
| `PROGRESS.md` | Session log (agent-facing) |
| `DECISIONS.md` | ADR log (agent-facing) |
| `QUESTIONS.md` | Questions for the user (agent-facing) |
| `BUGS.md` | Bug tracker (agent-facing) |
| `REVIEW.md` | Self-reviews (agent-facing) |
| `IDEAS.md` | Ideas inbox (agent-facing) |
| `docs/ARCHITECTURE.md` | Code structure (contributor-facing) |
| `docs/CONVENTIONS.md` | Code style (contributor-facing) |
| `docs/MODULES.md` | Module catalog (contributor-facing) |
| `docs/TESTING.md` | Testing strategy (contributor-facing) |
| `docs/GLOSSARY.md` | Domain terms (contributor-facing) |
| `docs/DESIGN.md` | Design system (contributor-facing) |

### Bilingual (English US + Portuguese PT) — user-facing

| English (source of truth) | Portuguese translation |
|---------------------------|------------------------|
| `README.md` | `README-pt.md` (or `docs/pt/README.md`) |
| `docs/RESEARCH.md` | `docs/pt/RESEARCH.md` |
| `docs/BENCHMARKS.md` | `docs/pt/BENCHMARKS.md` |
| `docs/SPARK-PROFILING.md` | `docs/pt/SPARK-PROFILING.md` |
| `docs/CLIENT-FEASIBILITY.md` | `docs/pt/CLIENT-FEASIBILITY.md` |
| `test-server/TEST-PLAN.md` | `test-server/TEST-PLAN-pt.md` |
| In-game strings (`en_us.json`) | `pt_pt.json` (already in place) |

### English only — code & config
- All Java identifiers, docstrings, log messages, error messages
- `fabric.mod.json` (description in English; `custom.multicore` keys in English)
- All config keys in `multicore.json`

---

## 3. Folder structure for bilingual docs

```
docs/
├── ARCHITECTURE.md          # English (contributor)
├── CONVENTIONS.md           # English (contributor)
├── MODULES.md               # English (contributor)
├── TESTING.md               # English (contributor)
├── GLOSSARY.md              # English (contributor)
├── DESIGN.md                # English (contributor)
├── RESEARCH.md              # English (user-facing)
├── BENCHMARKS.md            # English (user-facing)
├── SPARK-PROFILING.md       # English (user-facing)
├── CLIENT-FEASIBILITY.md    # English (user-facing)
└── pt/                      # Portuguese translations
    ├── README.md            # or README-pt.md at root
    ├── RESEARCH.md
    ├── BENCHMARKS.md
    ├── SPARK-PROFILING.md
    └── CLIENT-FEASIBILITY.md
test-server/
├── TEST-PLAN.md             # English (user-facing)
└── TEST-PLAN-pt.md          # Portuguese
```

**Alternative (simpler):** keep Portuguese translations at the root with a `-pt` suffix:
`README-pt.md`, `BENCHMARKS-pt.md`, etc. This avoids a separate `docs/pt/` folder.

**Recommendation:** use `docs/pt/` for docs and `README-pt.md` at root (matches GitHub convention).

---

## 4. README language switcher

Add to the top of `README.md`:

```markdown
[English](README.md) | [Português](README-pt.md)
```

And in `README-pt.md`:
```markdown
[English](README.md) | [Português](README-pt.md)
```

---

## 5. Translation workflow

1. **Write in English first.** All new content is authored in English (US).
2. **Translate to Portuguese** using the same structure, same headings, same code blocks.
3. **Code blocks, commands, file paths, and identifiers are NOT translated** — they stay in English.
4. **Update both in the same commit.** When a doc changes, the translation changes too.
5. **Review both.** A doc review (`/review`) checks both languages for consistency.

### What NOT to translate

- Code blocks (commands, JSON, Java)
- File paths (`src/main/java/...`, `config/multicore.json`)
- Identifiers (`workerThreads`, `mode`, `MultiCoreConfig`)
- Mod names (Lithium, C2ME, VMP, etc.)
- URLs and links

### What TO translate

- Prose (explanations, warnings, descriptions)
- Table headers (when user-facing)
- Error messages in docs (not in code)
- Scenario descriptions in benchmarks

---

## 6. In-game strings (already done)

The `assets/multicore/lang/` folder already has both languages:
- `en_us.json` — English (US)
- `pt_pt.json` — Portuguese (Portugal)

Convention: Portuguese strings use pt-PT orthography (e.g., "Depuração", "Threads de trabalho").

---

## 7. Implementation order

1. Create `README-pt.md` at root (translate README.md)
2. Create `docs/pt/` directory with translations of user-facing docs
3. Create `test-server/TEST-PLAN-pt.md`
4. Add language switcher to `README.md` and `README-pt.md`
5. Update `docs/CONVENTIONS.md` with the bilingual convention (section 11)
6. Update `AGENTS.md` §9 to mention bilingual docs
7. Add a CI check (optional): verify that `docs/pt/` has the same file names as `docs/` (minus contributor-only docs)

---

## 8. Maintenance

- **When a doc changes:** update both languages in the same commit.
- **When a new doc is added:** decide if it's user-facing (bilingual) or contributor-facing (English only).
- **Translation quality:** Portuguese should be natural, not machine-translated. Use the same tone as the English version.
- **Review:** `/review` checks both languages for consistency.

---

## 9. Decision needed

- **A (recommended):** implement the bilingual structure as described above.
- **B:** keep everything in English only (simpler, but less accessible to pt-PT users).

Default recommendation: **A**. Already decided in D-011 ("Project is documented in English; UI text in pt-PT/en-US"). This plan extends that decision to user-facing docs.