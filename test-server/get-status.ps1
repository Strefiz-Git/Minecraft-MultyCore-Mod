# get-status.ps1
# Envia um comando ao servidor rodando e mostra o resultado do log.
#
# Uso exemplos:
#   .\test-server\get-status.ps1 -Command "multicore status"
#   .\test-server\get-status.ps1 -Command "multicore compat"
#   .\test-server\get-status.ps1 -Command "multicore compat dump"

param(
    [string]$Command = "multicore status"
)

$ErrorActionPreference = "Stop"
$dir = $PSScriptRoot

$p = Get-Process -Name "java" -ErrorAction SilentlyContinue
if (-not $p) {
    Write-Host "Nenhum servidor Java rodando. Inicie primeiro com .\test-server\start-server.ps1" -ForegroundColor Red
    exit 1
}

# Envia comando pelo stdin do servidor
$stdin = $p.StandardInput
$stdin.WriteLine($Command)
$stdin.Flush()
Start-Sleep -Seconds 2

Write-Host "Resultado do comando '$Command':" -ForegroundColor Cyan
Get-Content "$dir\logs\latest.log" -Tail 15 -ErrorAction SilentlyContinue | Select-String -Pattern "MultiCore|MSPT|ticks|threads|legacy|compatible|An unexpected|error"
