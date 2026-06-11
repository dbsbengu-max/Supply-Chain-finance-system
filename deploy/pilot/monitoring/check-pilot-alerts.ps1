# EA-033 Pilot alert runner (A-01, A-03, A-04)
# Usage: .\check-pilot-alerts.ps1 [-BackendUrl] [-StrictStale] [-SkipSaga]

param(
    [string]$BackendUrl,
    [switch]$StrictStale,
    [switch]$SkipSaga
)

$ErrorActionPreference = "Continue"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$failed = $false

& (Join-Path $ScriptDir "check-a01-health.ps1") -BackendUrl $BackendUrl
if ($LASTEXITCODE -ne 0) { $failed = $true }

if (-not $SkipSaga) {
    if ($StrictStale) {
        & (Join-Path $ScriptDir "check-saga-alerts.ps1") -StrictStale
    } else {
        & (Join-Path $ScriptDir "check-saga-alerts.ps1")
    }
    if ($LASTEXITCODE -ne 0) { $failed = $true }
} else {
    Write-Host "SKIP  Saga alerts check (A-03, A-04)" -ForegroundColor Yellow
}

if ($failed) {
    Write-Host "FAIL  Pilot alerts check" -ForegroundColor Red
    exit 1
}
if ($SkipSaga) {
    Write-Host "PASS  Pilot alerts check (A-01; A-03/A-04 skipped)" -ForegroundColor Green
} else {
    Write-Host "PASS  Pilot alerts check (A-01, A-03, A-04)" -ForegroundColor Green
}
exit 0
