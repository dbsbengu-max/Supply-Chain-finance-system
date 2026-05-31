# EA-033 A-03 / A-04: Saga outbox & compensation alerts (DB probe)
# Usage: .\check-saga-alerts.ps1 [-StrictStale] 
#   -StrictStale: fail A-03 if FAILED events older than 30 min exist

param(
    [switch]$StrictStale
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Root = Resolve-Path (Join-Path $ScriptDir "..")

function Load-DotEnv {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return }
    Get-Content $Path | ForEach-Object {
        if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
        $k, $v = $_ -split '=', 2
        if ($k -and $null -ne $v) { Set-Item -Path "env:$k" -Value $v.Trim() }
    }
}

Load-DotEnv (Join-Path $Root ".env")

$dbHost = $env:SCF_DB_HOST; if (-not $dbHost) { $dbHost = "localhost" }
$port = $env:SCF_DB_PORT; if (-not $port) { $port = "5432" }
$db   = $env:SCF_DB_NAME; if (-not $db)   { $db = "scf" }
$user = $env:SCF_DB_USER; if (-not $user) { $user = "scf" }
$pass = $env:SCF_DB_PASSWORD

if (-not $pass) {
    Write-Host "FAIL  SCF_DB_PASSWORD not set" -ForegroundColor Red
    exit 1
}

$env:PGPASSWORD = $pass
$sqlFile = Join-Path $ScriptDir "check-saga-alerts.sql"

Write-Host "=== EA-033 Saga Alerts (A-03 / A-04) ===" -ForegroundColor Cyan
Write-Host "Target: ${user}@${dbHost}:${port}/${db}"

& psql -h $dbHost -p $port -U $user -d $db -f $sqlFile
if ($LASTEXITCODE -ne 0) { exit 1 }

$failedCnt = (psql -h $dbHost -p $port -U $user -d $db -t -A -c `
    "SET search_path TO scf; SELECT COUNT(*) FROM biz_event_outbox WHERE event_status='FAILED';").Trim()
$staleCnt = (psql -h $dbHost -p $port -U $user -d $db -t -A -c `
    "SET search_path TO scf; SELECT COUNT(*) FROM biz_event_outbox WHERE event_status='FAILED' AND updated_at < NOW() - INTERVAL '30 minutes';").Trim()
$manualCnt = (psql -h $dbHost -p $port -U $user -d $db -t -A -c `
    "SET search_path TO scf; SELECT COUNT(*) FROM biz_compensation_task WHERE compensation_status='MANUAL_REQUIRED';").Trim()

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
