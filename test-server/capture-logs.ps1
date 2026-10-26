# capture-logs.ps1
# Monitora o log do servidor em tempo real. Rode em um terminal à parte.
#
# Uso: .\test-server\capture-logs.ps1

$dir = $PSScriptRoot

if (-not (Test-Path "$dir\logs\latest.log")) {
    Write-Host "Aguardando o servidor criar o log..." -ForegroundColor Yellow
    while (-not (Test-Path "$dir\logs\latest.log")) {
        Start-Sleep -Seconds 2
    }
}

Write-Host "=== Monitorando logs do servidor (Ctrl+C para parar) ===" -ForegroundColor Cyan
Get-Content "$dir\logs\latest.log" -Wait -Tail 100
