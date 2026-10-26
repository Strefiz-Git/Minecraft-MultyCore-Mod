# stop-server.ps1
# Para o servidor Minecraft de forma limpa.

Get-Process -Name "java" -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2
Write-Host "Servidor parado." -ForegroundColor Green
