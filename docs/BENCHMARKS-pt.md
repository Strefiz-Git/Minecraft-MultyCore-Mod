# Benchmarks — Minecraft MultiCore Mod

> Como medimos se o mod realmente ajuda, e o ponto de **go/no-go** antes da Fase 3.
> Skill: `testing-verification`. Nada é "mais rápido" até ser medido aqui.

## Porquê isto existe

A Fase 1 é maioritariamente infraestrutura: por si só **não** acelera o jogo, porque o vanilla já
corre I/O de chunks, geração de chunks e atualizações de luz em threads de fundo (ver
`docs/RESEARCH.md` §3). O ganho real é esperado do tick regionalizado (Fase 3). Este documento é o
plano de medição que decide se a Fase 3 vale a pena construir.

## O que medimos

| Métrica | Fonte | Alvo |
|---------|-------|------|
| MSPT (ms/tick) | `/multicore status` e Spark | menor é melhor; 20 TPS = ≤ 50 ms |
| Distribuição do tempo de tick | Spark `/spark tps` + health report | onde vai o tempo |
| Tempo por thread | `/multicore status` › per-thread | provar que os workers são usados |
| TPS sob carga | `/spark tps` | mantém-se em 20 |

## Setup necessário

- Um servidor dedicado: Fabric 26.3, Fabric API, este mod.
- [Spark](https://modrinth.com/mod/spark) (profiling + tick health).
- Um mod de **bot/simulador de jogador** para criar carga (ex: um simulador de jogador headless).
  Registe qual usou.
- Dois mundos idênticos: mesma seed, mesmo modpack, mesmas flags de JVM, mesmo `server.properties`.

## Cenários

A insight chave da review é que o benefício depende inteiramente da **distribuição de jogadores**, por
isso testamos ambos:

### S-A — jogadores juntos

Todos os jogadores simulados na mesma área (uma região carregada). Esperado: **pouco ou nenhum
ganho**, porque o mundo inteiro é uma região. Este é o baseline que prova que o mod não piora as
coisas.

### S-B — jogadores espalhados

Jogadores simulados longe uns dos outros (ex: 4–8 grupos, cada um a ~500+ blocos do próximo).
Esperado: **o ganho aparece aqui**, porque cada grupo é uma região independente. Este é o cenário
alvo.

## Procedimento

1. **Baseline (sem mod).** Inicie o servidor sem `multicore-*.jar`. Warm up de 5 minutos. Corra o
   cenário por 10 minutos. Registe MSPT (média, percentil 95) e TPS.
2. **Com o mod, SAFE, `workerThreads = 0`.** Mesmo mundo, mesma seed. Isto tem de ser
   **equivalente ao baseline** — se não for, há um bug.
3. **Com o mod, SAFE, `workerThreads = N`** (N = 2, 4, 6). Registe os mesmos números para S-A e S-B.
4. **Profile.** Corra `/spark profiler --timeout 60` durante o pior cenário e veja os métodos top.
   Registe se o custo é entidades, pathfinding, block ticks, ou outra coisa.
5. **Compare** e escreva os números numa tabela.

## Tabela de resultados (template)

| Run | Mod | Modo | Workers | Cenário | Avg MSPT | p95 MSPT | TPS | Notas |
|-----|-----|------|---------|---------|----------|----------|-----|-------|
| 1 | não | — | — | S-A | | | | baseline |
| 2 | sim | SAFE | 0 | S-A | | | | deve igualar run 1 |
| 3 | sim | SAFE | 2 | S-A | | | | |
| 4 | sim | SAFE | 4 | S-A | | | | |
| 5 | sim | SAFE | 2 | S-B | | | | |
| 6 | sim | SAFE | 4 | S-B | | | | |
| 7 | sim | SAFE | 6 | S-B | | | | |

## Go / no-go (fim da Fase 1)

Depois das runs, decida com dados:

- Se o profile mostrar que o custo é **ticking de estado do mundo** (entidades, block ticks,
  pathfinding) e o cenário espalhado tem grupos independentes suficientes, **prossiga para a Fase 3**.
- Se o custo é **geração/IO de chunks ou luz**, o mod **não** deve duplicar C2ME/Lithium —
  reconsiderar scope.
- Se S-B não mostrar ganho, a Fase 3 não vale a pena para o hardware alvo; pare e mantenha M1 como
  mod de compatibilidade/monitorização.

## Teto esperado (honesto)

O ganho é limitado por `min(workerThreads + 1, gruposIndependentes)`:

- Default (1 worker, 2 threads): teto ≈ **2×**, antes do overhead.
- 6 threads e 6+ grupos distantes: até ≈ **5–6×** se o hardware tiver cores reais.
- Jogadores juntos: **≈ 1×** (uma região).
- Geração/IO de chunks **não** é o nosso ganho (vanilla/C2ME já tratam disso).

## Metodologia

### Benchmark automatizado (headless, sem intervenção humana)

O script `scripts/benchmark-compare.sh` automatiza o benchmark completo:

1. **Mesmo mundo, mesma seed**: usa o mesmo diretório de servidor com a mesma seed.
2. **Flags de JVM fixas**: `-Xmx2G` (ou configurável).
3. **Cenário fixo**: `dense` (todas as entidades juntas) ou `clusters` (N grupos, cada um com
   PER_CLUSTER entidades, spacing SPACING blocos de distância).
4. **Múltiplas configurações**: vanilla (sem mod), modo SAFE com 1/3/5 threads de trabalho.
5. **Múltiplas repetições**: pelo menos 3 runs por configuração (corra o script 3 vezes).
6. **Warmup**: 5 segundos de warmup antes da medição.
7. **Medição**: 60 segundos de amostragem de MSPT.
8. **Métricas capturadas**: MSPT avg, p50, p95, p99, max, ticks >50ms, avgCoresBusy, atividade de
   GC, uso de heap, contagem de threads.

### Tabela de resultados (auto-gerada a partir de JSON)

| Config | Avg MSPT | p50 | p95 | p99 | Max | >50ms | Cores ocupados | GC (ms) |
|--------|----------|-----|-----|-----|-----|-------|----------------|---------|
| vanilla | | | | | | | | |
| SAFE 2 threads | | | | | | | | |
| SAFE 4 threads | | | | | | | | |
| SAFE 6 threads | | | | | | | | |

### Critérios go/no-go (fim da Fase 1)

Depois das runs, decida com dados:

- Se o cenário espalhado mostrar **redução mensurável de MSPT** (> 20% de melhoria) com 4+ threads,
  **prossiga para a Fase 3**.
- Se o custo é **geração/IO de chunks ou luz**, o mod **não** deve duplicar C2ME/Lithium —
  reconsiderar scope.
- Se S-B não mostrar ganho, a Fase 3 não vale a pena para o hardware alvo; pare e mantenha M1 como
  mod de compatibilidade/monitorização.
- Se o modo AGGRESSIVE mostrar instabilidade (crashes, corrupção), mantenha SAFE como único modo.