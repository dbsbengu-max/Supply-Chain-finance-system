# EA-035 Staging real verification + sign-off orchestrator
# Usage:
#   .\run-ea035-signoff.ps1 -WatchMinutes 30 -WatchIntervalMinutes 5
#   .\run-ea035-signoff.ps1 -WatchHours 24 -WatchIntervalMinutes 15

param(
    [string]$EnvFile,
    [string]$ArchiveDir,
    [int]$WatchMinutes = 0,
    [int]$WatchHours = 0,
    [int]$WatchIntervalMinutes = 5,
    [switch]$SkipGate,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$PilotRoot = Resolve-Path (Join-Path $ScriptDir "..")

if (-not $EnvFile) { $EnvFile = Join-Path $PilotRoot ".env" }
if (-not $ArchiveDir) { $ArchiveDir = Join-Path $PilotRoot "evidence\staging" }

if (-not (Test-Path $EnvFile)) {
    Write-Host "FAIL  Missing $EnvFile — copy .env.staging.example to .env" -ForegroundColor Red
    exit 1
}

$placeholder = Select-String -Path $EnvFile -Pattern 'CHANGE_ME' -SimpleMatch
if ($placeholder) {
    Write-Host "FAIL  .env contains CHANGE_ME placeholders — fill staging credentials first" -ForegroundColor Red
    $placeholder | ForEach-Object { Write-Host "  $($_.Line)" -ForegroundColor Yellow }
    exit 1
}

if ($WatchHours -gt 0) {
    $WatchMinutes = $WatchHours * 60
}
if ($WatchMinutes -le 0) { $WatchMinutes = 30 }

Write-Host "=== EA-035 Staging Sign-off ===" -ForegroundColor Cyan
Write-Host "Watch: ${WatchMinutes}m @ ${WatchIntervalMinutes}m interval"

if ($DryRun) {
    Write-Host "DRY RUN — would invoke run-staging-gate.ps1" -ForegroundColor Yellow
    exit 0
}

$gateArgs = @{
    EnvFile               = $EnvFile
    ArchiveDir            = $ArchiveDir
    WatchMinutes          = $WatchMinutes
    WatchIntervalMinutes  = $WatchIntervalMinutes
}

& (Join-Path $ScriptDir "run-staging-gate.ps1") @gateArgs
$gateExit = $LASTEXITCODE

$reportPath = & (Join-Path $ScriptDir "generate-ea035-report.ps1") -ArchiveDir $ArchiveDir

Write-Host "`n=== EA-035 Complete ===" -ForegroundColor Cyan
Write-Host "Report: $reportPath"
if ($gateExit -eq 0) {
    Write-Host ">>> EA-035: GATE PASS — proceed to GO_NO_GO_CHECKLIST signing <<<" -ForegroundColor Green
    exit 0
}
Write-Host ">>> EA-035: GATE FAIL — see report §5 remediation <<<" -ForegroundColor Red
exit $gateExit
