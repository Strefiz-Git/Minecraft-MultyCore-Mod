# Spark Profiling Guide for MultiCore

> Como usar o Spark para profilear o servidor Minecraft com e sem o mod MultiCore.
> Spark é um mod de profiling de desempenho para Minecraft: https://modrinth.com/mod/spark

## Instalação

1. Descarregue o Spark para Fabric 26.3: https://modrinth.com/mod/spark
2. Coloque o `spark-1.10.187-fabric.jar` em `test-server/server/mods/`
3. Reinicie o servidor

## Comandos principais

### TPS (Ticks Per Second)
```
/spark tps
```
Mostra TPS dos últimos 5s, 10s, 1m, 5m, 15m, além de percentis de duração do tick e uso de CPU.
- **Alvo:** 20.0 TPS (≤ 50 ms/tick MSPT)
- **Bom:** *20.0 com max MSPT baixo
- **Mau:** < 20.0 ou max MSPT alto (> 100 ms)

### Profiler (amostragem de CPU)
```
/spark profiler --timeout 60
```
Amostra a CPU por 60 segundos e gera um relatório. Carregue o link num browser.
- Procurar: `MinecraftServer.tickServer`, `ServerLevel.tick`, `EntityTickList`, `ChunkMap.tick`
- Com o mod: procurar threads `multicore-worker-N` a fazer trabalho

### Health Report
```
/spark health
```
Mostra a saúde geral do servidor: TPS, MSPT, memória, CPU, contagem de threads.

### Heap Dump
```
/spark heapdump
```
Faz dump do heap da JVM para análise de memória. Útil para detetar memory leaks no worker pool.

## Procedimento de profiling para o MultiCore

### 1. Baseline (sem mod)
```powershell
.\test-server\start-server.ps1 -WorkerThreads 0 -WithMod $false
```
1. Conecte 2+ clientes, espalhados
2. Espere 3 minutos para estabilizar
3. Corra `/spark tps` → registe MSPT avg/max, TPS
4. Corra `/spark profiler --timeout 60` → registe métodos top
5. Corra `/spark health` → registe CPU, memória

### 2. Com mod, 2 threads
```powershell
.\test-server\stop-server.ps1
.\test-server\start-server.ps1 -WorkerThreads 1 -WithMod $true
```
1. Conecte 2+ clientes, espalhados
2. Espere 3 minutos
3. Corra `/multicore status` → registe MSPT, tempo por thread
4. Corra `/spark tps` → registe MSPT avg/max, TPS
5. Corra `/spark profiler --timeout 60` → procure threads worker
6. Corra `/spark health` → registe CPU, memória

### 3. Com mod, 4 threads
```powershell
.\test-server\stop-server.ps1
.\test-server\start-server.ps1 -WorkerThreads 3 -WithMod $true
```
Repita os passos de #2.

### 4. Com mod, 6 threads
```powershell
.\test-server\stop-server.ps1
.\test-server\start-server.ps1 -WorkerThreads 5 -WithMod $true
```
Repita os passos de #2.

## O que procurar no profiler do Spark

### Vanilla (sem mod)
- `MinecraftServer.tickServer` → 100% numa única thread
- `ServerLevel.tick` → entidades, block ticks, redstone tudo numa thread
- `ChunkMap.tick` → ticking de entidades na main thread

### Com mod (modo SAFE)
- `MinecraftServer.tickServer` → ainda na main thread (modo SAFE)
- Threads `multicore-worker-N` → devem aparecer com trabalho offloaded
- `TaskScheduler.drainToMain` → visível na fronteira do tick
- `TickMetrics` → overhead negligenciável

### Com mod (modo AGGRESSIVE, Phase 3)
- Múltiplas region threads a tickar em paralelo
- Nenhuma thread única a dominar o tempo de tick

## Métricas chave a registar

| Métrica | Fonte | Alvo | Notas |
|---------|-------|------|-------|
| MSPT (avg) | `/spark tps` ou `/multicore status` | ≤ 50 ms | Menor é melhor |
| MSPT (max) | `/spark tps` | ≤ 100 ms | Picos indicam lag |
| TPS | `/spark tps` | 20.0 | Deve manter-se em 20 |
| CPU (process) | `/spark tps` | Menor com mais threads | Deve escalar |
| Contagem de threads | `/spark health` | main + workers | Verificar tamanho do pool |
| Tempo por thread | `/multicore status` | Workers a fazer trabalho | Verificar que o offload está ativo |

## URL do profiler do Spark

Depois de correr `/spark profiler --timeout 60`, o Spark carrega um relatório e imprime um URL.
Abra o URL num browser para ver:
- Flame graph do uso de CPU
- Lista de threads (procurar `multicore-worker-N`)
- Hot methods (onde o tempo é gasto)

## Notas

- O motor async-profiler do Spark **não é suportado no Windows** (usa o engine Java, mais lento mas ainda útil).
- No Linux, o Spark usa async-profiler (nativo, mais preciso).
- O profiler adiciona overhead; não meça TPS absoluto enquanto profila.
- Use `/spark tps` para medição precisa de TPS (sem overhead de profiling).