# RESEARCH — Minecraft MultiCore Mod

> **Entrega da Fase 0.** Tudo o que sabemos *antes de escrever código*: prior art, a anatomia do
> tick do servidor Minecraft 26.3, a superfície de estado partilhado, concorrência Java, a
> superfície de modding do Fabric, estratégia de compatibilidade e riscos. Escrito para que o
> projeto nunca "perca o seu lugar" após um reset de contexto.
>
> **Legenda de estado:** `✅ verificado (li o código/docs)` · `🔶 documentado pela comunidade` ·
> `❓ precisa de spike` · `⚠️ suposição`.
>
> Última atualização: 2026-10-22

---

## 0. Resumo executivo

| Pergunta | Resposta |
|----------|----------|
| É possível correr um servidor Minecraft em vários cores? | Sim, mas **apenas re-arquitetando o tick**, exatamente como o Folia/Moonrise fazem. Não há um interruptor. |
| Qual é o ganho mais seguro e rápido primeiro? | Infraestrutura + offload de trabalho *já isolado* (I/O de chunks, luz, pré-cálculo de random-ticks, cache de pathfinding) + métricas. |
| O que quebra mods? | Tudo o que assume "uma thread possui o mundo". O Folia afirma compatibilidade ≈ 0 para plugins Bukkit não modificados; devemos ser igualmente honestos para mods Fabric. |
| Como manter mods legacy seguros? | Eles nunca saem da main thread: os seus callbacks de eventos do Fabric são redirecionados para a main thread, e o acesso ao mundo a partir de worker threads é encaminhado de volta. Mods baseados em mixins não podem ser totalmente protegidos (limitação documentada). |
| "N cores" é uma configuração real? | Java não consegue fixar threads a cores. Exponemos **número de threads de trabalho**; afinidade de CPU é um extra opcional da Fase 4 (FFM/JNI ou flags do launcher do SO). |
| Alvo | Mod Fabric server-side para **Minecraft 26.3**, Java 25, Gradle 9.8.0, Loom 1.17-SNAPSHOT, Fabric API 0.160.5+26.3. |

**Conclusão:** a ideia é tecnicamente sólida e existe forte prior art. A parte difícil não é "iniciar
threads" — é **definir a propriedade do estado mutável do mundo** e **manter o ecossistema de mods
legacy vivo**. O roadmap é portanto: *infraestrutura → camada de compatibilidade → tick
regionalizado → polimento → (cliente, só viabilidade)*.

---

## 1. Scope e versões alvo

### 1.1 Stack alvo (bloqueada)

| Componente | Versão | Fonte |
|------------|--------|-------|
| Minecraft | **26.3** | `ClientHarvestMod/gradle.properties` (`minecraft_version=26.3`) |
| Fabric Loader | **0.19.5** | idem, e o jar do loader está em cache local |
| Fabric API | **0.160.5+26.3** | idem |
| Fabric Loom | **1.17-SNAPSHOT** | idem |
| Java | **25** (toolchain) | JDK `C:\Program Files\Java\jdk-25.0.4` instalado |
| Gradle | **9.8.0** (wrapper dist em cache em `~/.gradle/wrapper/dists`) | o build do ClientHarvestMod teve sucesso com 9.8.0 |
| Ambiente | **Server-side** (`DedicatedServerModInitializer`) | ver §6.1 |
| SO | **Linux + Windows** (servidor dedicado) | requisito do utilizador |

⚠️ **Suposição:** a combinação `26.3` / `0.19.5` / `0.160.5` é a que compila hoje (provado por
`ClientHarvestMod/build/libs/rightclick-replant-1.0.0.jar`). Reproduzimos os seus ficheiros de build.

### 1.2 Fora de scope por agora (ver `ROADMAP.md`)

- Lado cliente (a render thread tem restrições fortes; apenas integrated server mais tarde).
- Portes NeoForge/Forge.
- Qualquer alteração ao comportamento do vanilla observável pelos jogadores.

---

## 2. Prior art — o que já existe, o que faz, e o que partiu

Esta é a entrada mais importante: **não somos os primeiros** a tentar isto, e cada projeto
existente diz-nos onde estão as armadilhas.

### 2.1 Folia (PaperMC) — o design de referência ✅🔶

- Divide os chunks carregados de cada mundo em **regiões que ticam de forma independente**; cada
  região tem o seu próprio tick loop executado num thread pool, em paralelo, a 20 TPS. **Não há main
  thread.**
- Regras de regionização (de `docs.papermc.io/folia/reference/region-logic`):
  - Uma **região** = um conjunto de posições de chunk possuídas + um objeto de dados por-região
    (listas de tick, entidades, contadores de tick) apenas tocado pela thread proprietária.
  - O regionizer trabalha em **region sections** = uma grelha `N×N` de chunks (`N` potência de 2,
    `regionSectionCoord = chunkCoord >> log2(N)`), por isso as regiões possuem *secções*, não chunks
    crus.
  - Quatro invariantes:
    1. cada chunk existente tem exatamente uma região proprietária;
    2. cada chunk dentro do raio de merge de um chunk é possuído pela mesma região;
    3. uma **região em ticking não pode crescer** (não pode adquirir novas secções enquanto tica);
    4. uma região está num e só num estado: `transient | ready | ticking | dead`.
  - Regiões em ticking criam regiões "transient" de buffer à sua volta; estas fundem-se de volta
    após o tick.
  - Merge/split ajustam os prazos de tick (redstone tick, current tick) com um offset para preservar
    prazos relativos.
- Contadores de tick: `Current Tick` e `Redstone Time` são **por região**; `Global Game Time` e
  `Daylight Time` vivem na **global region**. Leituras de tempo global pela região usam uma cópia
  feita no início do tick.
- **Global region**: uma tarefa única a correr sempre a 20 TPS para estado não-espacial (game rules,
  tempo global, daylight, comandos de consola, world border, weather).
- Operações inter-região usam `EntityScheduler`, `RegionizedTaskQueue`, global-region task queue e
  `RegionizedData`; teleports são divididos em *transform* + *async place*.
- **O que partiu:** o próprio projeto diz *"expect compatibility at 0"* e requer
  `folia-supported: true` no `plugin.yml`; sem ele o plugin recusa-se a carregar. Plugins region-safe
  devem usar `RegionScheduler`/`EntityScheduler`, usar coleções thread-safe, e nunca partilhar
  `HashMap`/`ArrayList` entre regiões.
- Lição para nós: **compatibilidade opt-in é a resposta aceite pela indústria**, e a maquinaria de
  region section / merge-radius / transient-region é a forma provada. A nossa Fase 3 copia o *modelo*,
  não os patches do Paper.

### 2.2 C2ME (RelativityMC) — concorrência de chunks ✅

- "Concurrent Chunk Management Engine": paraleliza **geração de chunks, I/O e loading**.
- Usa I/O de chunks reescrito; `globalExecutorParallelism` define o tamanho do executor.
- Inclui `CheckedThreadLocalRandom` / "sanitização de comportamento indefinido" para detetar acesso
  off-thread ao random do mundo que corromperia mundos.
- **Compatibilidade:** datapacks que funcionam no vanilla funcionam; **geradores de mundo custom em
  mods podem partir** porque assumem geração single-threaded.
- Moonrise declara-se **incompatível** com C2ME (ambos substituem o sistema de chunks).
- Lição: offload de geração/IO de chunks é um problema resolvido e valioso; **reutilizar ou
  complementar C2ME em vez de duplicá-lo**, e adicionar instrumentação de thread-safety (§4.4).

### 2.3 Lithium (CaffeineMC) ✅

- Otimização geral (cliente + servidor) que "não altera comportamento do vanilla": movimento de
  entidades/colisões/física/tracking, ticking/loading de chunks, retrieval de blocos/entidades,
  coleções, AI, redstone, world gen. Compatível com a maioria dos mods; toggles de otimização por
  config.
- Lição: muitos ganhos algorítmicos single-threaded já existem. O nosso mod deve ser **aditivo**, não
  um substituto do Lithium; e não deve regredir o seu comportamento.

### 2.4 VMP (RelativityMC) ✅

- "Very Many Players": otimização de servidor para alto playercount — area maps para envio de
  pacotes/lookups de jogadores, caches para entity trackers, lookups de fluid state, matching de
  ingredientes, iteração de entidades para colisões, propagador de tickets. Também melhorias de
  time-source e networking no cliente.
- Lição: os hot paths server-side *não* de estado do mundo (fan-out de pacotes, trackers, iteração
  de colisões) já estão a ser otimizados; o nosso offload da Fase 1 não deve duplicar o VMP.

### 2.5 Moonrise (Tuinity) ✅

- Otimização de servidor dedicado/integrado: movimento de entidades/colisões/física/tracking,
  ticking/loading/geração/gravação de chunks, retrieval de blocos/entidades; melhorias de
  responsividade (prioridade de pacotes, menos threads worker em sistemas de baixo core, redução de
  TPS catch-up).
- Conflitos conhecidos: desativa partes conflitantes do Lithium/FerriteCore; incompatível com C2ME.
- Lição: em máquinas de poucos cores o melhor é **menos** threads worker; a nossa config deve tornar
  o número de workers um knob de primeira classe com defaults sensatos.

### 2.6 Outros projetos relevantes 🔶

- **Paper async chunks** — async chunk loading/saving (só I/O, não geração).
- **Sodium** (cliente) — paralelismo na render thread; mostra que a geração de mundo no cliente já
  está fora da render thread (integrated server).
- **Starlight / ScalableLux** — motores de luz reescritos (threaded).
- **MultiPaper / ShreddedPaper** — cores de servidor multithreaded; o mesmo aviso de "reescriver o
  plugin".

### 2.7 O hiato que preenchemos

Todos os projetos acima são (a) um **fork do servidor** (Folia, MultiPaper) ou (b) um **otimizador**
(C2ME/Lithium/VMP/Moonrise). **Ninguém envia um mod de multithreading drop-in para Fabric com um
modo de compatibilidade legacy.** Esse é o nosso nicho: um mod Fabric que pode ser instalado num
servidor normal, com um **modo SAFE** onde mods não modificados continuam a funcionar, e um modo
**AGGRESSIVE** para servidores que optam por ele.

---

## 3. Anatomia do tick do servidor Minecraft 26.3 ✅

Verificado por `javap` contra o jar merged remapeado pelo Loom
(`.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-6974b2190e/26.3/`).

### 3.1 A cadeia de chamadas

```
MinecraftServer.runServer()
  └─ tickServer(BooleanSupplier hasTimeLeft)
       ├─ tickConnection()                // loop de rede/pacotes
       ├─ processPacketsAndTick(boolean)  // pacotes de jogadores e depois tick
       └─ tickChildren(BooleanSupplier)   // <-- a parte interessante
            └─ for each ServerLevel level:
                 level.tick(hasTimeLeft)
                 level.getChunkSource().tick(hasTimeLeft, true)   // sistema de chunks
```

`MinecraftServer` também possui: `getTickCount()`, `getNextTickTime()`, `addTickable(Runnable)`,
`getAllLevels()`, `getLevel(ResourceKey)`, `levelKeys()`, `enforceGameTypeForPlayers(...)`,
`isUnderSpawnProtection(...)`.

`ServerLevel.tick(BooleanSupplier)` é o coração. Membros observados de `ServerLevel` (seleção):

| Membro | Papel | Preocupação de thread-safety |
|--------|-------|------------------------------|
| `players: List<ServerPlayer>` | jogadores neste nível | mutável no join/leave |
| `chunkSource: ServerChunkCache` | sistema de chunks, já tem executors | misto |
| `entityTickList: EntityTickList` | entidades a tickar este tick | **mutável, main-thread only** |
| `entityManager: PersistentEntitySectionManager` | secções/lookups de entidades | **mutável** |
| `blockTicks / fluidTicks: LevelTicks` | scheduled block/fluid ticks | **mutável** |
| `blockEvents / blockEventsToReschedule: List<BlockEventData>` | block events | **mutável** |
| `navigatingMobs: Set<Mob>` + `isUpdatingNavigations: boolean` | batch de pathfinding | **mutável** |
| `pathTypesByPosCache: PathTypeCache` | cache de path type | **mutável** |
| `customSpawners: List<CustomSpawner>` | mob spawning | **mutável** |
| `gameEventDispatcher`, `portalForcer`, `structureManager`, `structureCheck` | sistemas do mundo | **mutável** |
| `sleepStatus`, weather data | global do mundo | **mutável** |
| `handlingTick: boolean` | guard de re-entrância | **mutável** |

`ServerLevel` public tick API: `tick(BooleanSupplier)`, `tickTime()`, `tickChunk(LevelChunk,int)`,
`tickNonPassenger(Entity)`, `tickThunder`, `tickPrecipitation`, `tickCustomSpawners(boolean)`,
`runBlockEvents()`, `tickBlock/tickFluid(BlockPos,...)`, `tickPassenger(...)`,
`startTickingChunk(LevelChunk)`, `waitForEntities(ChunkPos,int)`.

### 3.2 O sistema de chunks

- `ServerChunkCache` possui um `ChunkTaskDispatcher` e um `MainThreadExecutor`
  (`ReentrantBlockableEventLoop`).
- `ChunkMap` possui `entityMap: Int2ObjectMap<TrackedEntity>`, `poiManager: PoiManager`,
  `ticketStorage`, `chunkStatusListener`, e o `Executor` para chunk tasks.
- `ChunkMap.tick(BooleanSupplier)` conduz o **ticking de entidades** (via secções de entidade) e
  `ChunkMap.prepareTickingChunk(...)` / `prepareEntityTickingChunk(...)` retornam
  `CompletableFuture<ChunkResult<LevelChunk>>` — **a geração de chunks já é async** e entregue de
  volta à main thread.
- `ThreadedLevelLightEngine` já corre atualizações de luz num `ConsecutiveExecutor`/`ChunkTaskDispatcher`
  e apenas aplica resultados na main thread.

### 3.3 Scheduled ticks

`LevelTicks<T>` (usado para `Block` e `Fluid`) tem `tick(long currentTick, int maxTicks,
BiConsumer<BlockPos,T> consumer)`, `schedule(...)`, `hasScheduledTick(...)`, `willTickThisTick(...)`,
`clearArea(...)`, `copyArea(...)`, `count()`. É uma estrutura **plain, não sincronizada** indexada
por containers `ChunkPos`.

### 3.4 O que isto significa para nós

- **Já paralelo/isolado no vanilla:** I/O de chunks, geração de chunks, atualizações de luz. Não
  reconstruir.
- **Ainda main-thread e mutável:** ticking de entidades (`entityTickList`), scheduled block/fluid
  ticks, block events, pathfinding, mob spawning, game events, portais, contadores globais do mundo.
- Portanto a Fase 3 deve **tomar posse destas estruturas por região**, exatamente como o Folia.

---

## 4. Estado mutável partilhado e a superfície de race conditions

Esta seção é a lista de "o que pode correr mal" que conduz o design.

### 4.1 O modelo de propriedade do estado do mundo

O único modelo seguro é a **propriedade**: a cada instante, para cada pedaço de estado do mundo,
existe exatamente uma thread autorizada a *escrever* nele, e as outras threads comunicam através
de uma fila.

| Estado | Proprietário hoje | Proprietário após Fase 3 |
|--------|-------------------|--------------------------|
| Lista de entidades / secções de entidade de uma região | main thread | thread da região |
| Scheduled block/fluid ticks de uma região | main thread | thread da região |
| Block events de uma região | main thread | thread da região |
| Pathfinding (`navigatingMobs`, path type cache) | main thread | thread da região (ou offloaded & merged) |
| Tempo global de jogo / daylight / weather / game rules | main thread (global region) | global region |
| Chunk map / entity map (índice espacial) | main thread | tem de ser particionável ou locked |
| POI manager | main thread | particionado por região ou locked |
| Estado de rede/tracker | main + loops Netty | inalterado (Netty já é async) |

### 4.2 Interações cross-region (as perigosas)

Qualquer uma destas a cruzar uma fronteira de região requer uma **mensagem**, nunca acesso direto:

- Entidade (ou jogador) a mover/teleportar da região A para a região B.
- Redstone / atualizações de bloco / explosões a propagarem-se através de uma fronteira.
- `copyArea` / movimentos de estruturas que abrangem regiões.
- Teleporte de portal / mudança de dimensão.
- Chunk load/unload perto de uma fronteira (invariantes #2/#3).
- Um mod legacy a tocar em dados de outra região a partir da main thread.

### 4.3 Estruturas de dados de mods

A orientação do Folia aplica-se verbatim: `HashMap`/`ArrayList` partilhados entre regiões são
fonte de corrupção de dados; usar `ConcurrentHashMap`, atomics, ou armazenamento **por-região**.
Temos de expor a mesma orientação na nossa API (um accessor `RegionLocal<T>`).

### 4.4 Instrumentação ("sanitização de comportamento indefinido")

O C2ME envia `CheckedThreadLocalRandom`. Nós enviaremos, em modo debug:
- um **thread-check no acesso ao mundo** que regista/lança quando uma thread não-proprietária toca
  em dados de região;
- um **relatório de acesso de mods legacy** (`/multicore compat dump`) listando qual mod tocou em quê.

Isto é o que torna o modo SAFE confiável em vez de "geralmente funciona".

---

## 5. Modelo de concorrência Java para este projeto

### 5.1 Threads vs cores ✅

- Java **não tem API portável para fixar uma thread a um core**. `Thread.setPriority` é apenas uma
  dica.
- A JVM mapeia threads Java para threads de SO (1:1) a menos que se usem virtual threads. **Virtual
  threads são inadequadas aqui**: o nosso trabalho é CPU-bound e de longa duração sem blocking, por
  isso threads de plataforma são as corretas (e virtual threads também não podem ser fixadas).
- Afinidade de CPU tem de vir de:
  1. o **launcher do SO** (`taskset`/`numactl` no Linux, `start /affinity` no Windows) —
     recomendado, zero dependências; ou
  2. **código nativo** (JNI ou a API **Foreign Function & Memory** do Java 22+) a chamar
     `sched_setaffinity` (Linux) / `SetThreadAffinityMask` (Windows).
- **Decisão (D-006):** expor `workerThreads` na config; fornecer afinidade como funcionalidade
  *opcional, best-effort* da Fase 4 por detrás de uma flag, com um aviso claro de que pode ser
  no-op em algumas plataformas.

### 5.2 Primitivas que vamos usar

| Necessidade | Primitiva | Nota |
|-------------|-----------|------|
| Pool de workers | `ExecutorService` (pool fixo, threads nomeadas, `ThreadFactory`) | **nunca** o common pool |
| Fan-out/join por tick | `CyclicBarrier` / `CountDownLatch` / `CompletableFuture.allOf` | a barrier alinha-se com a fronteira do tick |
| Hand-off cross-thread | `LinkedBlockingQueue<Runnable>` + drain na main thread | para callbacks legacy |
| Dados por-região | `RegionLocal<T>` custom | espelha o `RegionizedData` do Folia |
| Métricas | `LongAdder`, `LongAccumulator`, ring buffers lock-free | sem locks no hot path |
| Reload de config | objeto de config imutável `volatile` | single-writer |

### 5.3 Scheduling: EDF-like, por região

O Folia agenda regiões num executor earliest-start-time-first (EDF-like) para que uma região que
fique para trás não arraste as outras. O nosso scheduler implementará a mesma ideia para a Fase 3
(baseado em `region-deadline`), e uma fila work-stealing simples para offloads da Fase 1.

---

## 6. Superfície de modding do Fabric (verificado contra os jars reais de 26.3) ✅

### 6.1 Entrypoints

| Interface | Método | Chave do entrypoint | Uso |
|-----------|--------|---------------------|-----|
| `net.fabricmc.api.ModInitializer` | `onInitialize()` | `main` | init comum (ambos os lados) |
| `net.fabricmc.api.DedicatedServerModInitializer` | `onInitializeServer()` | `server` | **o nosso entrypoint** |
| `EnvType` | `CLIENT` / `SERVER` | — | verificações de ambiente |

### 6.2 Eventos que precisamos (`fabric-lifecycle-events-v1` 4.1.9)

- `ServerLifecycleEvents.SERVER_STARTING / SERVER_STARTED / SERVER_STOPPING / SERVER_STOPPED`
  (`ServerStarting(Server)`, `ServerStarted(Server)`, `ServerStopping(Server)`, `ServerStopped(Server)`).
- `ServerTickEvents.START_SERVER_TICK(MinecraftServer)` / `END_SERVER_TICK` /
  `START_LEVEL_TICK(ServerLevel)` / `END_LEVEL_TICK(ServerLevel)`.

Estes são eventos de **before/after dispatch**: não canceláveis, chamados sincronicamente na thread em
que o vanilla está. Este é o hook que nos permite (a) medir e (b) conduzir o nosso scheduler.

### 6.3 Comandos (`fabric-command-api-v2` 3.1.2)

- `CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> { ... })`
- Construir comandos com Brigadier (`LiteralArgumentBuilder`,
  `Commands.literal(...).requires(...)`).

### 6.4 Mixins

- SpongePowered Mixin padrão; declarar em `src/main/resources/<modid>.mixins.json`, com refmap
  quando visar membros não-óbvios.
- `@Inject`, `@Redirect`, `@WrapOperation` (mixins do Fabric API / mixin extras oficiais) para
  hookar `MinecraftServer.tickServer` / `tickChildren` / `ServerLevel.tick`.
- **Mods baseados em mixin correm dentro do vanilla na thread em que o vanilla está** — esta é a
  limitação fundamental do modo SAFE (ver §7.3).

### 6.5 Leitura de metadata de mods para compatibilidade (`fabric-loader` 0.19.5)

```java
FabricLoader.getInstance().getAllMods();                 // Collection<ModContainer>
ModContainer.getMetadata();                              // ModMetadata
ModMetadata.containsCustomValue(String);                 // boolean
ModMetadata.getCustomValue(String);                      // CustomValue
CustomValue.getAsObject()  -> CvObject  (Iterable<Entry<String,CustomValue>>)
CustomValue.getAsString()  -> String
CustomValue.getAsNumber()  -> Number
CustomValue.getAsBoolean() -> boolean
```

O `fabric.mod.json` suporta um objeto `"custom"` arbitrário, por isso um mod pode declarar:

```json
"custom": { "multicore": { "threadSafe": true } }
```

⚠️ **Suposição (D-005):** um mod que **não** declare `threadSafe: true` é tratado como **legacy**,
mesmo que o autor simplesmente se tenha esquecido. Isto espelha a regra `folia-supported` do Folia e
é a única forma de ser seguro por defeito. Mods legacy continuam a funcionar na main thread.

### 6.6 Ler a identidade do próprio mod a partir de um listener (Fase 2)

Para distinguir listeners legacy vs compatíveis no momento do *dispatch*, vamos mapear um objeto
listener para um mod via a sua classe `ProtectionDomain` → `CodeSource` → caminho do jar →
`ModContainer`. Isto requer um mixin na implementação de eventos do Fabric para capturar registos
(spike planeado, §9).

---

## 7. Estratégia de compatibilidade (o coração do produto)

### 7.1 Dois modos

| Modo | Quem corre onde | Quando usar |
|------|-----------------|-------------|
| **SAFE** (por defeito) | Todos os eventos do Fabric são despachados na **main thread**; region ticks correm em workers mas redirecionam callbacks legacy; acesso ao mundo a partir de workers é encaminhado de volta. | Servidores normais com mods não modificados. |
| **AGGRESSIVE** (opt-in) | Eventos disparam na thread da região; mods devem optar por `threadSafe: true`. Mods legacy devem partir. | Servidores onde o autor verificou cada mod. |

### 7.2 Porque SAFE é o default certo

- A própria conclusão do Folia: compatibilidade opt-in, compatibilidade ≈ 0 caso contrário. O
  requisito do utilizador ("mods sem suporte continuam a funcionar") exige o mesmo.
- O modo SAFE deve ser **equivalente ao vanilla quando `workerThreads = 0`** (ver critérios de
  aceitação).

### 7.3 Limitações honestas (devem estar no README)

1. **Mods baseados em mixin não podem ser protegidos.** Se um mod faz mixin em `Entity.tick()` e
   corre numa region thread, vai ter race. O modo SAFE protege mods *baseados em eventos do Fabric*.
2. **Datapacks** que assumem execução single-threaded podem também comportar-se mal.
3. **Corrupção do mundo é possível** se um mod legacy escrever em dados de outra região a partir da
   main thread enquanto essa região tica. A nossa instrumentação de debug visa *detetar* isto, não
   preveni-lo.
4. Não é substituto para testar no seu modpack específico.

### 7.4 A API de compatibilidade (Fase 2)

- Uma API pública `MultiCoreCompat`:
  - `boolean isThreadSafe()` no nosso próprio contexto;
  - `RegionLocal<T>` para armazenamento por-região;
  - `MultiCoreCompat.runOnRegion(Entity/BlockPos, Runnable)` / `runOnMain(Runnable)`;
  - uma declaração no `fabric.mod.json` `"multicore": { "threadSafe": true }` para autores de mods.

---

## 8. Registo de riscos

| # | Risco | Severidade | Mitigação |
|---|-------|------------|-----------|
| R1 | Tick regionalizado corrompe estado do mundo | **Crítico** | SAFE por defeito; thread-check de debug; testes de determinismo; nunca misturar refactor com mudança de comportamento; fallback à main thread em qualquer anomalia. |
| R2 | Mods legacy baseados em mixin correm em race e crasham | Alto | Documentar; modo SAFE protege eventos; fornecer `runOnMain`; reportar mods incompatíveis. |
| R3 | Deadlocks entre region threads (cross-region locks) | Alto | Nunca segurar um lock de região enquanto se espera por outra região; usar apenas mensagens async; impor uma ordenação global em operações multi-região. |
| R4 | Regressão de performance em máquinas de poucos cores | Médio | Número de workers configurável; default conservador; lição do Moonrise ("menos threads em low-core"). |
| R5 | Não-determinismo (C2ME já avisa que world gen varia run-to-run) | Médio | Não reclamar determinismo; documentar; comparar com vanilla por seed apenas vagamente. |
| R6 | Incompatibilidade com C2ME / Moonrise / VMP | Médio | Detetar e desativar subsistemas sobrepostos; documentar combos testados. |
| R7 | Hand-off de dispatch de eventos do Fabric torna-se bottleneck | Médio | Hand-off apenas em modo SAFE; mods compatíveis optam por eventos na thread da região. |
| R8 | Snapshots/renames de métodos do vanilla | Médio | Pin a 26.3; re-verificar mixins a cada release; manter mixins pequenos. |
| R9 | Ambiente de build (Loom SNAPSHOT, sem Gradle no PATH) | Baixo/Médio | Cache da dist do Gradle 9.8.0; commitar o wrapper; documentar toolchain Java 25. |
| R10 | Edições de config no Mod Menu não são live (precisam restart) e um servidor dedicado não tem GUI de cliente | Baixo | Avisar no ecrã; documentar ficheiro + `/multicore` como autoritativos em servidores; aplicar config apenas no arranque. |

---

## 9. Questões em aberto / spikes planeados

- **S1 (≤15 min):** pode um mixin na implementação `ArrayBackedEvent` do Fabric capturar o mapeamento
  listener→mod de forma fiável? Necessário para hand-off per-listener (e não per-event) em SAFE. Se
  não, fallback para "hand off de todos os eventos do Fabric em modo SAFE".
- **S2:** corpo exato de `MinecraftServer.tickChildren` no 26.3 (decompilar ou inferir do bytecode) para
  sabermos o loop exato que estamos a substituir.
- **S3:** quais offloads específicos dão ganhos mensuráveis de MSPT no 26.3 sem tocar em estado do
  mundo (candidatos: pré-cálculo de random-tick, warm de cache de pathfinding, lookups de POI).
- **S4:** afinidade de CPU via FFM é viável em Linux e Windows sem um helper nativo?

---

## 10. Fontes

- Folia overview — <https://docs.papermc.io/folia/reference/overview/>
- Folia region logic — <https://docs.papermc.io/folia/reference/region-logic/>
- Folia migration guide (DeepWiki) — <https://deepwiki.com/PaperMC/Folia/3.4-migration-guide>
- C2ME — <https://github.com/RelativityMC/C2ME-fabric>
- Lithium — <https://github.com/CaffeineMC/lithium>
- VMP — <https://modrinth.com/mod/vmp-fabric>
- Moonrise — <https://github.com/Tuinity/Moonrise>
- Fabric lifecycle events README — <https://github.com/fabricmc/fabric-api/blob/26.2/fabric-lifecycle-events-v1/README.md>
- Fabric events — <https://docs.fabricmc.net/develop/events/>
- Fabric mixin intro — <https://wiki.fabricmc.net/tutorial:mixin_introduction>
- `fabric.mod.json` spec — <https://docs.fabricmc.net/develop/loader/fabric-mod-json>
- Java virtual threads — <https://docs.oracle.com/en/java/javase/25/core/virtual-threads.html>
- CPU affinity via JNI — <https://medium.com/@shirshakupadhayay182/breaking-out-of-the-jvm-implementing-cpu-affinity-with-jni-96d7a7720626>
- Local evidence: `ClientHarvestMod/` (working 26.3 Fabric build), Loom-remapped `minecraft-merged-…-26.3.jar`, cached Fabric API/loader jars.