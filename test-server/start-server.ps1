param(
    [int]$WorkerThreads = 1,
    [bool]$WithMod = $true,
    [string]$Memory = "2G",
    [bool]$ShowConsole = $true
)

$ErrorActionPreference = "Stop"
$baseDir = $PSScriptRoot
$serverDir = Join-Path $baseDir "server"
$modJar = Join-Path $serverDir "mods\multicore-0.1.0.jar"
$sourceModJar = Join-Path $baseDir "..\build\libs\multicore-0.1.0.jar"
$configFile = Join-Path $serverDir "config\multicore.json"

Write-Host "=== Minecraft MultiCore - Servidor de Teste ===" -ForegroundColor Cyan
Write-Host "Diretorio: $serverDir" -ForegroundColor Cyan
Write-Host ""

# 1. Garante que nao ha servidor rodando
Write-Host "Verificando servidores Java existentes..." -ForegroundColor Gray
Get-Process -Name "java" -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 3

# 2. Configura o mod
if ($WithMod) {
    if (-not (Test-Path $modJar)) {
        if (Test-Path $sourceModJar) {
            New-Item -ItemType Directory -Path (Join-Path $serverDir "mods") -Force | Out-Null
            Copy-Item $sourceModJar $modJar -Force
            Write-Host "Mod jar copiado de build/libs/" -ForegroundColor Green
        } else {
            Write-Host "ERRO: mod jar nao encontrado em $modJar nem em $sourceModJar" -ForegroundColor Red
            Write-Host "Rode primeiro: ./gradlew build" -ForegroundColor Yellow
            exit 1
        }
    }
    $config = @{
        workerThreads = $WorkerThreads
        mode = "SAFE"
        debug = $false
        modWhitelist = @()
        modBlacklist = @()
    } | ConvertTo-Json -Depth 5
    New-Item -ItemType Directory -Path (Join-Path $serverDir "config") -Force | Out-Null
    Set-Content -Path $configFile -Value $config
    Write-Host "MOD ATIVO:" -ForegroundColor Green
    Write-Host "  workerThreads = $WorkerThreads  (total de threads = $($WorkerThreads + 1), incluindo a main)" -ForegroundColor Green
    Write-Host "  mode = SAFE (mods legacy ficam na main thread)" -ForegroundColor Green
} else {
    Remove-Item (Join-Path $serverDir "mods\multicore-0.1.0.jar") -ErrorAction SilentlyContinue
    Remove-Item $configFile -ErrorAction SilentlyContinue
    Write-Host "MOD REMOVIDO - teste vanilla (baseline)" -ForegroundColor Yellow
}

# 3. Limpa logs antigos
Remove-Item (Join-Path $serverDir "logs\latest.log") -ErrorAction SilentlyContinue

# 4. Inicia o servidor
if ($ShowConsole) {
    Write-Host "Iniciando servidor com consola visivel..." -ForegroundColor Cyan
    $proc = Start-Process -FilePath "C:\Program Files\Java\jdk-25.0.4\bin\java.exe" -ArgumentList "-Xmx$Memory -jar fabric-server-launch.jar" -WorkingDirectory $serverDir -WindowStyle Normal -PassThru
    Write-Host "Servidor iniciado (PID: $($proc.Id))" -ForegroundColor Green
    Write-Host "Conecte os clientes em: localhost:25565" -ForegroundColor Cyan
    Write-Host "Digite comandos na janela do servidor (ex: /multicore status, /spark tps)" -ForegroundColor Cyan
    $proc
} else {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "C:\Program Files\Java\jdk-25.0.4\bin\java.exe"
    $psi.Arguments = "-Xmx$Memory -jar fabric-server-launch.jar --nogui"
    $psi.WorkingDirectory = $serverDir
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $false
    $psi.RedirectStandardError = $false
    $psi.CreateNoWindow = $true
    $p = [System.Diagnostics.Process]::Start($psi)

    Write-Host "SERVIDOR INICIADO (PID: $($p.Id))" -ForegroundColor Cyan
    Write-Host "Conecte os clientes em: localhost:25565" -ForegroundColor Cyan
    Write-Host "Para parar: digite 'stop' no console do servidor, ou rode .\test-server\stop-server.ps1" -ForegroundColor Cyan

    $ready = $false
    $timeout = [DateTime]::Now.AddSeconds(40)
    while (-not $ready -and [DateTime]::Now -lt $timeout) {
        if (Test-Path (Join-Path $serverDir "logs\latest.log")) {
            $content = Get-Content (Join-Path $serverDir "logs\latest.log") -ErrorAction SilentlyContinue
            if ($content -match "Done \(.*\)! For help") { $ready = $true; break }
        }
        Start-Sleep -Milliseconds 500
    }

    if ($ready) {
        Write-Host "Servidor pronto para jogadores!" -ForegroundColor Green
    } else {
        Write-Host "Aviso: servidor pode nao ter iniciado completamente dentro do tempo esperado." -ForegroundColor Yellow
    }

    $p
}
