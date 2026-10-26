# launch-clients-simple.ps1
# Versão simplificada: apenas mostra instruções para lançar clientes manualmente.
# Use este script se o lançamento automático pelo launcher não funcionar.

param(
    [string]$Player1 = "Player1",
    [string]$Player2 = "Player2"
)

Write-Host "=== Como lançar 2 clientes Minecraft (manual) ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. Abra o Minecraft Launcher (ou MultiMC / Prism Launcher)."
Write-Host "2. Crie um perfil para Minecraft 26.3 com Fabric Loader 0.19.5 + Fabric API."
Write-Host "3. Abra o launcher DUAS vezes (duas janelas separadas)."
Write-Host ""
Write-Host "Cliente 1:"
Write-Host "  - Nome de usuário: $Player1"
Write-Host "  - Servidor: localhost:25565"
Write-Host ""
Write-Host "Cliente 2:"
Write-Host "  - Nome de usuário: $Player2"
Write-Host "  - Servidor: localhost:25565"
Write-Host ""
Write-Host "Depois de conectar, ande para áreas separadas (>=500 blocos) para"
Write-Host "gerar chunks independentes — esse é o cenário onde o mod brilha."
Write-Host ""
Write-Host "Depois, no console do servidor, rode:"
Write-Host "  /multicore status"
Write-Host ""
Write-Host "E anote o MSPT no ficheiro test-server/test-results.csv."
