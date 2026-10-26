# Minecraft MultiCore Mod

Um **mod Fabric** que permite a um **servidor** Minecraft normal usar mais do que um core — *sem*
quebrar os mods que não foram escritos para multithreading.

> **Estado:** Fase 3 em progresso (modelo de tick regionalizado + scheduler implementado; mixin layer pendente).
> A arquitetura e o plano completo estão em `ROADMAP.md`; a pesquisa profunda está em `docs/RESEARCH.md`;
> a revisão independente do código está em `REVIEW.md`.

---

[English](README.md) | [Português](README-pt.md)

---

## Índice

1. [O que faz](#o-que-faz)
2. [Porquê (a versão honesta)](#porquê-a-versão-honesta)
3. [Requisitos](#requisitos)
4. [Instalação](#instalação)
5. [Configuração](#configuração)
6. [Comandos](#comandos)
7. [Compatibilidade com Mod Menu](#compatibilidade-com-mod-menu)
8. [Como funciona](#como-funciona)
9. [Limitações conhecidas](#limitações-conhecidas)
10. [Desenvolvimento](#desenvolvimento)
11. [Licença](#licença)

---

## O que faz

- Executa o trabalho seguro do tick do servidor (que não toca em estado partilhado do mundo) num
  **pool configurável de threads de trabalho**, e devolve os resultados à main thread antes do tick
  acabar.
- **Modo SAFE (por defeito):** mods que não optaram por suporte continuam a correr na **main
  thread**, exatamente como hoje. O mod redireciona os seus callbacks para a main thread.
- **Modo AGGRESSIVE (opt-in):** para servidores cujo modpack foi verificado, o tick do mundo é
  dividido em **regiões independentes** (design estilo Folia) para correrem em paralelo.
- Número de threads de trabalho configurável (por defeito `1` worker → 2 threads no total: 1 main +
  1 worker; aumente para mais cores).
- Configuração in-game através do **Mod Menu**, com aviso claro de **"reinício necessário"**.
- Comando `/multicore` para status, threads, regiões e lista de mods legacy.
- Instrumentação de thread-safety em modo debug (`debug: true`) — regista acessos off-thread a estado do mundo.
- Helper de afinidade de CPU (best-effort, imprime comandos do SO quando JNA não está disponível).

## Porquê (a versão honesta)

- Java não consegue fixar threads a cores, por isso a configuração é um **número de threads de
  trabalho**, não um número de cores. Afinidade de CPU é um extra opcional (ver `ROADMAP.md` M4 e
  `docs/RESEARCH.md` §5.1).
- O Minecraft já corre **I/O de chunks, geração de chunks e atualizações de luz** em threads de
  fundo. O bottleneck restante é o tick de **estado mutável do mundo** (entidades, block ticks,
  redstone, pathfinding), por isso o ganho grande só vem com o tick regionalizado (M3).
- O Folia — a implementação de referência — afirma que plugins não modificados têm **~0
  compatibilidade**. Tomamos a mesma posição: **um mod é thread-safe apenas se o declarar**,
  tudo o resto é legacy e fica na main thread.
- **O modo SAFE protege mods baseados em eventos do Fabric, não mods baseados em mixins.** A maioria
  dos mods Fabric usa mixins (ou acede ao mundo diretamente), e esses correm na thread do vanilla.
  Este é o maior hiato entre a ideia original e o que é tecnicamente possível sem um fork do
  servidor. Ver `docs/RESEARCH.md` §7.3.

## Requisitos

| Componente | Versão |
|------------|--------|
| Minecraft | **26.3** |
| Fabric Loader | **>= 0.19.5** |
| Fabric API | **0.160.5+26.3** (ou compatível) |
| Java | **25** (toolchain; Gradle corre em Java 21+) |
| Ambiente | Servidor **dedicado** (Linux / Windows). Também carregado no servidor integrado. |
| Opcional | [Mod Menu](https://modrinth.com/mod/modmenu) para o ecrã de config in-game |
| Opcional | [Spark](https://modrinth.com/mod/spark) para profiling e saúde do tick |

## Instalação

1. Instale o Fabric Loader para 26.3 e o Fabric API.
2. Coloque o `multicore-*.jar` na pasta `mods/` do servidor.
3. (Opcional) Adicione o Mod Menu se quiser o ecrã de config no singleplayer/LAN.
4. Inicie o servidor uma vez para gerar `config/multicore.json`.
5. Edite a config e depois **reinicie** (definições de threads/regiões são aplicadas no arranque).

## Configuração (`config/multicore.json`)

```jsonc
{
  "workerThreads": 1,          // 1 main + 1 worker = 2 threads no total; aumente para mais cores
  "mode": "SAFE",              // SAFE (por defeito) | AGGRESSIVE
  "modWhitelist": [],          // só estes mods podem usar region threads
  "modBlacklist": [],          // estes mods são sempre legacy
  "debug": false               // instrumentação de thread-safety + logs verbosos
}
```

> Alterar `workerThreads`, `mode` ou as definições de região **requer reinício**. O ecrã do Mod Menu
> e o `/multicore` guardam o ficheiro mas não o aplicam em runtime.

## Comandos

| Comando | O que faz |
|---------|-----------|
| `/multicore status` | Threads, modo, MSPT, tempo por thread, metrics rollup |
| `/multicore compat` | Legacy vs compatíveis (contagens) |
| `/multicore compat dump` | Lista detalhada de todos os mods classificados |
| `/multicore regions` | Regiões por nível e thread owner (Phase 3) |
| `/multicore reload` | Guarda a config e mostra aviso de reinício necessário |

## Compatibilidade com Mod Menu

O mod regista um entrypoint `modmenu` e apenas *sugere* o Mod Menu (nunca é obrigatório). Usa o
entrypoint comum `main`, por isso também corre num **servidor integrado** (singleplayer/LAN): o
ecrã do Mod Menu edita o mesmo `config/multicore.json` que o servidor lê, e as alterações aplicam-se
após reinício. Num **servidor dedicado** não há GUI de cliente — o ficheiro e o comando são
autoritativos.

## Como funciona

### Arquitetura

```
MultiCoreMod (entrypoint)
  ├── config: MultiCoreConfig (JSON, carregado no arranque do servidor)
  ├── thread: WorkerPool (pool fixo nomeado, nunca o common pool)
  │          TaskScheduler (offload de trabalho, barrier, merge na main thread)
  │          MainThread (identifica a thread principal do servidor)
  │          ThreadChecker (avisos de thread-safety em debug)
  │          CpuAffinity (best-effort, opcional)
  ├── metrics: TickMetrics (MSPT lock-free + tempo por thread)
  ├── command: MultiCoreCommand (/multicore)
  │            StatusFormatter (formatação de texto pura)
  ├── compat: ModCompatibility (scan + classificação + whitelist/blacklist)
  │           LegacyHandoff (enfileira callbacks para a main thread)
  │           MultiCoreCompat (API pública para autores de mods)
  │           ModMenuIntegration (ecrã do Mod Menu opcional)
  └── region: Region, RegionState, RegionSection (modelo estilo Folia)
              RegionScheduler (tick paralelo + barrier)
              RegionMessage/RegionMessageQueue (cross-region)
              RegionLocal, RunOnRegion (API pública)
```

### Fluxo do tick (Phase 1 — atual)

```
START_SERVER_TICK → metrics.startTick()
  → offloads.submitAll(maxSafety) → submete ao worker pool
  → tick do servidor vanilla (main thread)
  → END_SERVER_TICK → scheduler.drainToMain() (barrier + merges ordenados)
  → legacyHandoff.drainToMain()
  → metrics.endTick()
```

### Fluxo do tick (Phase 3 — planeado)

```
mixin tickChildren → regionizer atribui regiões
  → scheduler corre regiões nos workers (paralelo)
  → barrier (todas as regiões terminam)
  → callbacks legacy na main thread
  → mensagens cross-region entregues
  → END_SERVER_TICK
```

### Compatibilidade com mods

Um mod é thread-safe **apenas se** declarar:
```json
"custom": { "multicore": { "threadSafe": true } }
```
no seu `fabric.mod.json`. Tudo o resto é legacy e fica na main thread.

- **Modo SAFE (por defeito):** eventos do Fabric disparam na main thread; workers nunca tocam em estado do mundo.
- **Modo AGGRESSIVE (opt-in):** eventos disparam nas region threads; apenas mods declarados como compatíveis podem participar.

### API pública para autores de mods

```java
// Correr código na main thread do servidor (seguro de qualquer thread):
MultiCoreCompat.runOnMain(() -> { /* tocar em estado do mundo */ });

// Verificar se o contexto atual é thread-safe:
if (MultiCoreCompat.isThreadSafe()) { /* seguro aceder a dados da região */ }

// Armazenamento por-região (Phase 3):
RegionLocal<Integer> counter = RegionLocal.create();
counter.set(region, counter.get(region) + 1);
```

## Limitações conhecidas (leia antes de usar)

- **Mods baseados em mixins não são protegidos.** O modo SAFE protege mods baseados em eventos do
  Fabric; um mod que faça mixin direto em métodos de tick do vanilla pode ainda ter race
  conditions.
- **Datapacks** que assumem uma única thread podem comportar-se mal.
- **Não é determinista:** a geração de mundo já varia de execução para execução com a mesma seed
  (ver C2ME).
- Este não é um substituto do Lithium/C2ME/VMP/Moonrise — é aditivo.

Ver `docs/RESEARCH.md` §7.3 e §8 para a lista completa e honesta de riscos.

## Mods testados

| Mod | Versão | Modo | Estado | Notas |
|-----|--------|------|--------|-------|
| _Nenhum ainda_ | | | | Corra o benchmark antes de reclamar compatibilidade. |

Ver `docs/BENCHMARKS.md` para o procedimento de medição.

## Desenvolvimento

```bash
./gradlew build          # jar em build/libs/
bash scripts/check.sh    # verificação completa (11 checks)
./gradlew test           # testes unitários
```

Java 25 toolchain, Gradle 9.8.0 wrapper, Loom 1.17-SNAPSHOT.

### Estrutura do projeto

```
src/main/java/com/strefiz/multicore/
  MultiCoreMod.java          # entrypoint (ModInitializer, main)
  config/                    # config JSON + validação
  thread/                    # WorkerPool, TaskScheduler, MainThread, ThreadChecker, CpuAffinity
  metrics/                   # TickMetrics, MetricsRollup
  command/                   # comando /multicore + StatusFormatter
  compat/                    # ModCompatibility, LegacyHandoff, MultiCoreCompat, ModMenuIntegration
  region/                    # Region, RegionState, RegionSection, RegionScheduler, RegionMessage
  mixin/                     # (vazio — continuação da Phase 3)
src/test/java/...            # 61+ testes unitários (puros, sem dependência do Minecraft)
docs/                        # RESEARCH, ARCHITECTURE, CONVENTIONS, MODULES, TESTING, etc.
test-server/                 # servidor de teste + scripts + stress mod + plano de teste
```

### Mapa de documentação

| Ficheiro | Para quê serve |
|----------|----------------|
| `ROADMAP.md` | Visão e milestones (M0–M5) |
| `TODO.md` | Fila de tarefas (Now/Next/Later/Blocked/Done) |
| `PROGRESS.md` | Estado atual + log de sessões |
| `DECISIONS.md` | Log de decisões (D-001 a D-013) |
| `QUESTIONS.md` | Perguntas para o utilizador + respostas |
| `BUGS.md` | Bugs conhecidos e o seu estado |
| `REVIEW.md` | Auto-revisões (R-001, R-002) |
| `IDEAS.md` | Ideias fora de scope (inbox) |
| `docs/RESEARCH.md` | Pesquisa da Phase 0 (prior art, anatomia do tick, riscos) |
| `docs/BENCHMARKS.md` | Procedimento de medição e critérios go/no-go |
| `docs/SPARK-PROFILING.md` | Guia do Spark |
| `docs/CLIENT-FEASIBILITY.md` | Relatório de viabilidade do cliente (Phase 5) |
| `docs/ARCHITECTURE.md` | Como o sistema está organizado |
| `docs/CONVENTIONS.md` | Estilo de código, nomenclatura, estrutura |
| `docs/MODULES.md` | Catálogo de módulos e funções reutilizáveis |
| `docs/TESTING.md` | Estratégia de testes e comandos |
| `docs/GLOSSARY.md` | Vocabulário do domínio |
| `docs/DESIGN.md` | Sistema de design (ecrã do Mod Menu) |
| `docs/DOCUMENTATION-PLAN.md` | Plano de documentação bilíngue (EN US + PT PT) |

## Licença

MIT — ver `LICENSE`.