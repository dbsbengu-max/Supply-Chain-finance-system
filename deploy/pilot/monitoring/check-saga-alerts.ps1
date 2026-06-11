# EA-033 A-03 / A-04: Saga outbox & compensation alerts (DB probe)
# Usage: .\check-saga-alerts.ps1 [-StrictStale]
#   -StrictStale: fail A-03 if FAILED events older than 30 min exist

param(
    [switch]$StrictStale
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Root = Resolve-Path (Join-Path $ScriptDir "..")
. (Join-Path $Root "scripts\_psql.ps1")

$sqlFile = Join-Path $ScriptDir "check-saga-alerts.sql"

Write-Host "=== EA-033 Saga Alerts (A-03 / A-04) ===" -ForegroundColor Cyan
$cfg = Get-ScfDbConfig -PilotRoot $Root
Write-Host "Target: $($cfg.User)@$($cfg.Host):$($cfg.Port)/$($cfg.Db)"

$code = Invoke-ScfPsql -FilePath $sqlFile -PilotRoot $Root
if ($code -ne 0) { exit 1 }

$failedCnt = (Invoke-ScfPsqlQuery -Query `
    "SELECT COUNT(*) FROM scf.biz_event_outbox WHERE event_status='FAILED';" `
    -PilotRoot $Root).Trim()
$staleCnt = (Invoke-ScfPsqlQuery -Query `
    "SELECT COUNT(*) FROM scf.biz_event_outbox WHERE event_status='FAILED' AND COALESCE(updated_at, created_at) < NOW() - INTERVAL '30 minutes';" `
    -PilotRoot $Root).Trim()
$manualCnt = (Invoke-ScfPsqlQuery -Query `
    "SELECT COUNT(*) FROM scf.biz_compensation_task WHERE compensation_status='MANUAL_REQUIRED';" `
    -PilotRoot $Root).Trim()

$exitCode = 0

if ([int]$failedCnt -gt 0) {
    if ($StrictStale -and [int]$staleCnt -gt 0) {
        Write-Host "FAIL  A-03 outbox FAILED=$failedCnt (stale >30m=$staleCnt)" -ForegroundColor Red
        $exitCode = 3
    } else {
        Write-Host "WARN  A-03 outbox FAILED=$failedCnt (stale >30m=$staleCnt)" -ForegroundColor Yellow
        if ($StrictStale) { $exitCode = 3 }
    }
} else {
    Write-Host "PASS  A-03 outbox FAILED=0" -ForegroundColor Green
}

if ([int]$manualCnt -gt 0) {
    Write-Host "FAIL  A-04 compensation MANUAL_REQUIRED=$manualCnt" -ForegroundColor Red
    $exitCode = [Math]::Max($exitCode, 4)
} else {
    Write-Host "PASS  A-04 compensation MANUAL_REQUIRED=0" -ForegroundColor Green
}

exit $exitCode
