# Pasta de testes do servidor Minecraft MultiCore

Esta pasta contém tudo o necessário para executar um servidor dedicado Fabric 26.3 com o mod MultiCore e realizar testes de desempenho comparativos (com/sem mod, 2/4/6 threads).

## Estrutura

```
test-server/
├── start-server.ps1      # Inicia o servidor com configuração escolhida
├── stop-server.ps1       # Para o servidor limpo
├── get-status.ps1        # Envia comando /multicore e mostra resultado
├── capture-logs.ps1      # Monitora o log em tempo real (janela à parte)
├── server.properties     # Configuração do servidor (modo offline, sem pause)
├── eula.txt              # Aceite da EULA do Minecraft
└── TEST-PLAN.md          # Este arquivo: plano de teste detalhado
```

## Pré-requisitos

- Java 25 instalado (`C:\Program Files\Java\jdk-25.0.4\bin\java.exe`)
- O mod compilado: `build/libs/multicore-0.1.0.jar` (gerado por `./gradlew build`)
- Fabric API 0.160.5+26.3 já baixada pelo Gradle (está no cache do Gradle)

## Primeiro uso

1. Na raiz do projeto, rode:
   ```powershell
   ./gradlew build
   ```

2. Copie os artefatos para a pasta do servidor (faça isso uma vez):
   ```powershell
   # Copia o jar do mod e o fabric-api para a pasta test-server/mods/
   New-Item -ItemType Directory -Path "test-server/mods" -Force | Out-Null
   Copy-Item "build/libs/multicore-0.1.0.jar" "test-server/mods/" -Force
   Copy-Item "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\net.fabricmc.fabric-api\fabric-api\0.160.5+26.3\*\fabric-api-0.160.5+26.3.jar" "test-server/mods/" -Force
   ```

3. Gere o servidor Fabric (se ainda não tiver):
   ```powershell
   # Baixe o fabric-installer.jar de https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.1.2/
   # e rode:
   java -jar fabric-installer.jar server -downloadMinecraft -dir test-server
   ```

## Como usar

### Iniciar o servidor

```powershell
# Vanilla (sem mod) — baseline
.\test-server\start-server.ps1 -WorkerThreads 0 -WithMod $false

# Com mod, 2 threads totais (1 worker + 1 main)
.\test-server\start-server.ps1 -WorkerThreads 1 -WithMod $true

# Com mod, 4 threads totais (3 workers + 1 main)
.\test-server\start-server.ps1 -WorkerThreads 3 -WithMod $true

# Com mod, 6 threads totais (5 workers + 1 main)
.\test-server\start-server.ps1 -WorkerThreads 5 -WithMod $true
```

O servidor escuta em `localhost:25565` (modo `online-mode=false`, qualquer nome de usuário funciona).

### Parar o servidor

No console do servidor digite `stop`, ou rode:
```powershell
.\test-server\stop-server.ps1
```

### Verificar status do mod

```powershell
.\test-server\get-status.ps1 -Command "multicore status"
.\test-server\get-status.ps1 -Command "multicore compat"
```

Ou digite diretamente no console do servidor enquanto ele roda.

### Monitorar logs em tempo real (janela separada)

```powershell
.\test-server\capture-logs.ps1
```

## Clientes (manual)

1. Abra o Minecraft Launcher (ou MultiMC/Prism).
2. Crie um perfil **Minecraft 26.3 + Fabric Loader 0.19.5 + Fabric API**.
3. Abra **2 instâncias** do jogo.
4. Conecte ambas em `localhost:25565`.
5. Ande para áreas **separadas** (≥500 blocos) para gerar chunks independentes — esse é o cenário onde o mod traz mais ganho.

## Plano de teste

Preencha à medida que for testando:

| # | Configuração | workerThreads | Jogadores | MSPT avg | MSPT max | TPS estável? |
|---|-------------|---------------|-----------|----------|----------|--------------|
| 1 | Vanilla (sem mod) | 0 | 2 | | | |
| 2 | Com mod, 2 threads | 1 | 2 | | | |
| 3 | Com mod, 4 threads | 3 | 2 | | | |
| 4 | Com mod, 6 threads | 5 | 2 | | | |

### Fluxo rápido

```powershell
# 1. Vanilla
.\test-server\stop-server.ps1
.\test-server\start-server.ps1 -WorkerThreads 0 -WithMod $false
# (conecte 2 clientes, jogue 3 min, anote MSPT)

# 2. 2 threads
.\test-server\stop-server.ps1
.\test-server\start-server.ps1 -WorkerThreads 1 -WithMod $true
# (/multicore status, anote)

# 3. 4 threads
.\test-server\stop-server.ps1
.\test-server\start-server.ps1 -WorkerThreads 3 -WithMod $true
# (/multicore status, anote)

# 4. 6 threads (opcional)
.\test-server\stop-server.ps1
.\test-server\start-server.ps1 -WorkerThreads 5 -WithMod $true
```

## Planilha de resultados

Guarde os números em `test-server/test-results.csv` (abre no Excel, LibreOffice Calc ou Google Sheets).  
Colunas: `test_id`, `data`, `pc_nome`, `scenario`, `mod_present`, `worker_threads`, `total_threads`, `players`, `seed`, `duration_min`, `mspt_avg`, `mspt_max`, `mspt_last`, `tps_stable`, `notes`.

As especificações do PC ficam em `test-server/test-metadata.csv` (CPU, RAM, SO, Java, etc.) — preencha uma vez e guarde junto com os resultados.

Uma linha de exemplo já está no ficheiro; basta duplicar e preencher à medida que testa cada etapa.

## Onde ficam os logs

- `test-server/logs/latest.log` — log principal do servidor
- `test-server/stdout.log`, `test-server/stderr.log` — output do Java (quando redirecionado)

## Fluxo completo de um teste

```
1. .\test-server\stop-server.ps1
2. .\test-server\start-server.ps1 -WorkerThreads X -WithMod $true/false
3. Aguarde "Done (X.XXXs)! For help, type \"help\""
4. Conecte 2 clientes em localhost:25565
5. Jogue 2-3 minutos (ande em áreas separadas)
6. No console do servidor: /multicore status
7. Anote MSPT avg/max e threads
8. Repita para a próxima configuração
```
