# EA-053 pilot patrol — daily quick or weekly full

param(
    [switch]$Quick,
    [switch]$Full,
    [int]$BackendPort = 8080
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "=== EA-053 Pilot Patrol ===" -ForegroundColor Green

if ($Full) {
    & (Join-Path $ScriptDir "run-ea051-acceptance.ps1")
    exit $LASTEXITCODE
}

if ($Quick) {
    $failed = $false
    $pg = Test-NetConnection -ComputerName 127.0.0.1 -Port 5432 -WarningAction SilentlyContinue
    if ($pg.TcpTestSucceeded) { Write-Host "PASS  PostgreSQL :5432" -ForegroundColor Green }
    else { Write-Host "FAIL  PostgreSQL :5432" -ForegroundColor Red; $failed = $true }

    $healthUrl = "http://127.0.0.1:$BackendPort/api/v1/actuator/health"
    try {
        $h = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 5
        if ($h.status -eq "UP") { Write-Host "PASS  $healthUrl" -ForegroundColor Green }
        else { Write-Host "FAIL  health not UP" -ForegroundColor Red; $failed = $true }
    } catch {
        Write-Host "FAIL  $healthUrl — start backend?" -ForegroundColor Red
        $failed = $true
    }

    try {
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:5173/" -UseBasicParsing -TimeoutSec 5
        if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 400) {
            Write-Host "PASS  frontend http://127.0.0.1:5173/" -ForegroundColor Green
        } else {
            Write-Host "FAIL  frontend status $($r.StatusCode)" -ForegroundColor Red
            $failed = $true
        }
    } catch {
        Write-Host "FAIL  frontend — run npm run dev?" -ForegroundColor Red
        $failed = $true
    }

    if ($failed) {
        Write-Host "`n>>> PILOT PATROL (quick): FAIL <<<" -ForegroundColor Red
        exit 1
    }
    Write-Host "`n>>> PILOT PATROL (quick): PASS <<<" -ForegroundColor Green
    Write-Host "Weekly: .\run-pilot-patrol.ps1 -Full  or  .\run-ea051-acceptance.ps1"
    exit 0
}

Write-Host @"

Usage:
  .\run-pilot-patrol.ps1 -Quick    # daily: PG + health + frontend
  .\run-pilot-patrol.ps1 -Full     # weekly: full EA-051 A1-A4

See: deploy/pilot/ops/PILOT_PATROL_CHECKLIST.md

"@
exit 0
