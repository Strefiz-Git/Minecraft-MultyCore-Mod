# start-server.ps1
# Inicia o servidor Minecraft Fabric 26.3 com o mod MultiCore (ou sem, para baseline vanilla).
#
# Uso exemplos:
#   .\test-server\start-server.ps1 -WorkerThreads 0 -WithMod $false   # vanilla baseline
#   .\test-server\start-server.ps1 -WorkerThreads 1 -WithMod $true    # 2 threads totais
#   .\test-server\start-server.ps1 -WorkerThreads 3 -WithMod $true    # 4 threads totais
#   .\test-server\start-server.ps1 -WorkerThreads 5 -WithMod $true    # 6 threads totais
#
# Parâmetros:
#   -WorkerThreads : número de threads de trabalho (além da main thread). Padrão 1.
#   -WithMod       : se true, instala o mod; se false, remove (vanilla). Padrão true.
#   -Memory        : memória JVM, ex: "2G" ou "4G". Padrão "2G".

param(
    [int]$WorkerThreads = 1,
    [bool]$WithMod = $true,
    [string]$Memory = "2G"
)

$ErrorActionPreference = "Stop"
$dir = $PSScriptRoot
$modJar = "$dir\mods\multicore-0.1.0.jar"
$configFile = "$dir\config\multicore.json"

Write-Host "=== Minecraft MultiCore — Servidor de Teste ===" -ForegroundColor Cyan
Write-Host "Diretório: $dir" -ForegroundColor Cyan
Write-Host ""

# 1. Garante que não há servidor rodando
Write-Host "Verificando servidores Java existentes..." -ForegroundColor Gray
Get-Process -Name "java" -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 3

# 2. Configura o mod
if ($WithMod) {
    if (-not (Test-Path $modJar)) {
        Write-Host "ERRO: mod jar não encontrado em $modJar" -ForegroundColor Red
        Write-Host "Rode primeiro: ./gradlew build" -ForegroundColor Yellow
        exit 1
    }
    $config = @{
        workerThreads = $WorkerThreads
        mode = "SAFE"
        debug = $false
        modWhitelist = @()
        modBlacklist = @()
    } | ConvertTo-Json -Depth 5
    New-Item -ItemType Directory -Path "$dir\config" -Force | Out-Null
    Set-Content -Path $configFile -Value $config
    Write-Host "MOD ATIVO:" -ForegroundColor Green
    Write-Host "  workerThreads = $WorkerThreads  (total de threads = $($WorkerThreads + 1), incluindo a main)" -ForegroundColor Green
    Write-Host "  mode = SAFE (mods legacy ficam na main thread)" -ForegroundColor Green
} else {
    if (Test-Path $modJar) { Remove-Item $modJar -Force }
    if (Test-Path $configFile) { Remove-Item $configFile -Force }
    Write-Host "MOD REMOVIDO — teste vanilla (baseline)" -ForegroundColor Yellow
}

# 3. Limpa logs antigos
Remove-Item "$dir\logs\latest.log" -ErrorAction SilentlyContinue
Remove-Item "$dir\stdout.log","$dir\stderr.log" -ErrorAction SilentlyContinue

# 4. Inicia o servidor (escreve em logs/latest.log naturalmente)
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "C:\Program Files\Java\jdk-25.0.4\bin\java.exe"
$psi.Arguments = "-Xmx$Memory -jar fabric-server-launch.jar --nogui"
$psi.WorkingDirectory = $dir
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $false
$psi.RedirectStandardError = $false
$psi.CreateNoWindow = $true
$p = [System.Diagnostics.Process]::Start($psi)

Write-Host "`n=== SERVIDOR INICIADO (PID: $($p.Id)) ===" -ForegroundColor Cyan
Write-Host "Conecte os clientes em: localhost:25565" -ForegroundColor Cyan
Write-Host "Para parar: digite 'stop' no console do servidor, ou rode .\test-server\stop-server.ps1" -ForegroundColor Cyan
Write-Host ""

# Espera o servidor ficar pronto (até 40s)
$ready = $false
$timeout = [DateTime]::Now.AddSeconds(40)
while (-not $ready -and [DateTime]::Now -lt $timeout) {
    if (Test-Path "$dir\logs\latest.log") {
        $content = Get-Content "$dir\logs\latest.log" -ErrorAction SilentlyContinue
        if ($content -match "Done \(.*\)! For help") { $ready = $true; break }
    }
    Start-Sleep -Milliseconds 500
}

if ($ready) {
    Write-Host "Servidor pronto para jogadores!" -ForegroundColor Green
} else {
    Write-Host "Aviso: servidor pode não ter iniciado completamente dentro do tempo esperado." -ForegroundColor Yellow
}

# Retorna o objeto do processo
$p
