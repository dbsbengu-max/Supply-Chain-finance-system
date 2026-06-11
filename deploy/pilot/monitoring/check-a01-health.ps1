# EA-033 A-01: Health probe (P0 if not UP)
# Usage: .\check-a01-health.ps1 [-BackendUrl] [-TimeoutSec]

param(
    [string]$BackendUrl,
    [int]$TimeoutSec = 10
)

$ErrorActionPreference = "Stop"
if (-not $BackendUrl) { $BackendUrl = $env:SCF_API_HEALTH_URL }
if (-not $BackendUrl) { $BackendUrl = "http://localhost:8080/api/v1/actuator/health" }

Write-Host "A-01 Health check: $BackendUrl" -ForegroundColor Cyan

try {
    $resp = Invoke-RestMethod -Uri $BackendUrl -Method Get -TimeoutSec $TimeoutSec
    $status = $resp.status
    if ($status -eq "UP") {
        Write-Host "PASS  A-01 health status=UP service=$($resp.service)" -ForegroundColor Green
        exit 0
    }
    Write-Host "FAIL  A-01 health status=$status (expected UP)" -ForegroundColor Red
    exit 2
} catch {
    Write-Host "FAIL  A-01 health unreachable: $_" -ForegroundColor Red
    exit 2
}
