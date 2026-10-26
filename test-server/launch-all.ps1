# launch-all.ps1
# Fluxo completo: inicia o servidor (se não estiver) e lança 2 clientes.
#
# Uso:
#   .\test-server\launch-all.ps1 -WorkerThreads 1 -WithMod $true -Player1 "Jogador1" -Player2 "Jogador2"

param(
    [int]$WorkerThreads = 1,
    [bool]$WithMod = $true,
    [string]$Player1 = "Player1",
    [string]$Player2 = "Player2"
)

$ErrorActionPreference = "Continue"
$baseDir = $PSScriptRoot

Write-Host "=== Fluxo Completo: Servidor + 2 Clientes ===" -ForegroundColor Cyan
Write-Host ""

# 1. Verificar se servidor já está rodando
$javaProc = Get-Process -Name "java" -ErrorAction SilentlyContinue
if ($javaProc) {
    Write-Host "Servidor ja parece estar rodando (processo Java encontrado)." -ForegroundColor Yellow
} else {
    Write-Host "Iniciando servidor..." -ForegroundColor Green
    & (Join-Path $baseDir "start-server.ps1") -WorkerThreads $WorkerThreads -WithMod $WithMod
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Falha ao iniciar o servidor." -ForegroundColor Red
        exit 1
    }
    Start-Sleep -Seconds 5
}

# 2. Lançar clientes
Write-Host ""
Write-Host "Lançando clientes..." -ForegroundColor Green
& (Join-Path $baseDir "launch-clients-simple.ps1") -Player1 $Player1 -Player2 $Player2

Write-Host ""
Write-Host "=== PRONTO ===" -ForegroundColor Cyan
Write-Host "Conecte os 2 clientes em localhost:25565 e jogue."
Write-Host "Depois rode: /multicore status  (no console do servidor)"
Write-Host "E anote os resultados em test-server/test-results.csv."
