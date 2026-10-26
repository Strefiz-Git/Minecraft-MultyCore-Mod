# launch-clients.ps1
# Lança 2 clientes Minecraft com Fabric 26.3 e conecta ao servidor local.
#
# Pré-requisitos:
#   - Ter o Minecraft Launcher instalado (ou um launcher como MultiMC/Prism)
#   - Ter um perfil "Fabric 26.3" já criado no launcher (Minecraft 26.3 + Fabric Loader 0.19.5 + Fabric API)
#   - O servidor rodando em localhost:25565 (online-mode=false)
#
# Uso:
#   .\test-server\launch-clients.ps1 -Player1 "Jogador1" -Player2 "Jogador2"
#
# Se não tiver um perfil Fabric 26.3, veja a secção "Criar perfil Fabric 26.3" abaixo.

param(
    [string]$Player1 = "Player1",
    [string]$Player2 = "Player2",
    [string]$LauncherPath = "C:\Program Files (x86)\Minecraft Launcher\MinecraftLauncher.exe",
    [string]$GameDir = "$env:APPDATA\.minecraft"
)

$ErrorActionPreference = "Continue"

Write-Host "=== Lançamento de Clientes Minecraft ===" -ForegroundColor Cyan
Write-Host "Launcher: $LauncherPath" -ForegroundColor Gray
Write-Host "Jogo: $GameDir" -ForegroundColor Gray
Write-Host ""

# 1. Verificar se o launcher existe
if (-not (Test-Path $LauncherPath)) {
    Write-Host "AVISO: Launcher não encontrado em '$LauncherPath'" -ForegroundColor Yellow
    Write-Host "Se usar outro launcher (MultiMC, Prism, etc.), ajuste o parâmetro -LauncherPath." -ForegroundColor Yellow
    Write-Host ""
}

# 2. Verificar se existe perfil Fabric 26.3
$fabricDir = Join-Path $GameDir "versions"
$fabricProfiles = Get-ChildItem -Path $fabricDir -Filter "*fabric*" -ErrorAction SilentlyContinue
$mc26Profiles = Get-ChildItem -Path $fabricDir -Filter "*26.3*" -ErrorAction SilentlyContinue

Write-Host "Perfis encontrados em '$fabricDir':" -ForegroundColor Gray
if ($fabricProfiles) {
    $fabricProfiles | ForEach-Object { Write-Host "  - $($_.Name)" -ForegroundColor Gray }
} else {
    Write-Host "  (nenhum)" -ForegroundColor Gray
}

Write-Host ""

# 3. Lançar cliente 1
Write-Host "Lançando cliente 1 ($Player1)..." -ForegroundColor Green
if (Test-Path $LauncherPath) {
    Start-Process -FilePath $LauncherPath -ArgumentList "--username `'$Player1`' --server `'localhost:25565`' --workDir `'$GameDir`'" -WindowStyle Normal
} else {
    Write-Host "  (pule este passo: lance manualmente pelo launcher)" -ForegroundColor Yellow
}

Start-Sleep -Seconds 3

# 4. Lançar cliente 2
Write-Host "Lançando cliente 2 ($Player2)..." -ForegroundColor Green
if (Test-Path $LauncherPath) {
    Start-Process -FilePath $LauncherPath -ArgumentList "--username `'$Player2`' --server `'localhost:25565`' --workDir `'$GameDir`'" -WindowStyle Normal
} else {
    Write-Host "  (pule este passo: lance manualmente pelo launcher)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== Clientes lançados ===" -ForegroundColor Cyan
Write-Host "Agora conecte ambos em: localhost:25565" -ForegroundColor Cyan
Write-Host "Ande para áreas separadas (>=500 blocos) para gerar chunks independentes." -ForegroundColor Cyan
