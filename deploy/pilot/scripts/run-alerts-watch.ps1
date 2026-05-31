# EA-034 Continuous pilot alerts (A-01 + A-03/A-04 StrictStale)
# Usage:
#   .\run-alerts-watch.ps1 -IntervalMinutes 5 -DurationMinutes 60
#   .\run-alerts-watch.ps1 -IntervalMinutes 5 -Iterations 12

param(
    [string]$BackendUrl,
    [string]$ArchiveDir,
    [int]$IntervalMinutes = 5,
    [int]$DurationMinutes = 0,
    [int]$Iterations = 0
)

$ErrorActionPreference = "Continue"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$PilotRoot = Resolve-Path (Join-Path $ScriptDir "..")
$MonitorDir = Join-Path $PilotRoot "monitoring"

if (-not $ArchiveDir) { $ArchiveDir = Join-Path $PilotRoot "evidence\staging" }
$ArchiveDir = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ArchiveDir)
New-Item -ItemType Directory -Force -Path $ArchiveDir | Out-Null

$sessionTs = Get-Date -Format "yyyyMMdd-HHmmss"
$logPath = Join-Path $ArchiveDir "alerts-watch-${sessionTs}.log"

if (-not $BackendUrl) { $BackendUrl = $env:SCF_API_HEALTH_URL }
if (-not $BackendUrl) { $BackendUrl = "http://localhost:8080/api/v1/actuator/health" }

$endAt = if ($DurationMinutes -gt 0) { (Get-Date).AddMinutes($DurationMinutes) } else { $null }
$i = 0
$failCount = 0

function Write-Log {
    param([string]$Line)
    $stamped = "$(Get-Date -Format o) $Line"
    Add-Content -Path $logPath -Value $stamped -Encoding UTF8
    Write-Host $stamped
}

Write-Log "=== EA-034 Alerts Watch START ==="
Write-Log "BackendUrl=$BackendUrl Interval=${IntervalMinutes}m StrictStale=true Log=$logPath"

while ($true) {
    $i++
    Write-Log "--- Iteration $i ---"

    & (Join-Path $MonitorDir "check-pilot-alerts.ps1") -BackendUrl $BackendUrl -StrictStale
    $code = $LASTEXITCODE
    if ($code -ne 0) {
        $failCount++
        Write-Log "RESULT iteration=$i FAIL exit=$code (cumulative_fail=$failCount)"
    } else {
        Write-Log "RESULT iteration=$i PASS"
    }

    if ($Iterations -gt 0 -and $i -ge $Iterations) { break }
    if ($endAt -and (Get-Date) -ge $endAt) { break }

    Start-Sleep -Seconds ($IntervalMinutes * 60)
}

Write-Log "=== EA-034 Alerts Watch END iterations=$i cumulative_fail=$failCount ==="

if ($failCount -gt 0) { exit 1 }
exit 0
