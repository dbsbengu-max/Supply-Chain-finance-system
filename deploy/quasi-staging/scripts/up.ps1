param(
    [switch]$Rebuild
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$EnvFile = Join-Path $Root ".env"
$ComposeFile = Join-Path $Root "docker-compose.yml"

if (-not (Test-Path $EnvFile)) {
    Copy-Item -LiteralPath (Join-Path $Root ".env.example") -Destination $EnvFile
    Write-Host "Created $EnvFile from .env.example" -ForegroundColor Yellow
}

$args = @("compose", "--env-file", $EnvFile, "-f", $ComposeFile, "up", "-d")
if ($Rebuild) { $args += "--build" }

& docker @args
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

function Read-EnvValue {
    param([string]$Name, [string]$Default)
    $line = Get-Content $EnvFile | Where-Object { $_ -match "^$Name=" } | Select-Object -First 1
    if (-not $line) { return $Default }
    $value = ($line -split "=", 2)[1]
    if (-not $value) { return $Default }
    return $value
}

$webPort = Read-EnvValue "QS_WEB_PORT" "15173"
$backendPort = Read-EnvValue "QS_BACKEND_PORT" "18080"

Write-Host ""
Write-Host "Quasi staging URLs:" -ForegroundColor Cyan
Write-Host "  Web:     http://localhost:$webPort"
Write-Host "  Backend: http://localhost:$backendPort/api/v1/actuator/health"
Write-Host ""
Write-Host "Next: .\scripts\verify.ps1" -ForegroundColor Green
