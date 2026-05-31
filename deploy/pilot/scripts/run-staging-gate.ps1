# EA-035 Staging gate — seed archive + StrictStale alerts watch + summary
# Usage:
#   .\run-staging-gate.ps1 -WatchMinutes 30 -WatchIntervalMinutes 5
#   .\run-staging-gate.ps1 -WatchHours 24 -WatchIntervalMinutes 15

param(
    [string]$EnvFile,
    [string]$ArchiveDir,
    [string]$BackendUrl,
    [int]$WatchMinutes = 0,
    [int]$WatchHours = 0,
    [int]$WatchIntervalMinutes = 5,
    [switch]$SkipAlertsWatch
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$PilotRoot = Resolve-Path (Join-Path $ScriptDir "..")
$RepoRoot = Resolve-Path (Join-Path $PilotRoot "..\..")

if (-not $EnvFile) { $EnvFile = Join-Path $PilotRoot ".env" }
if (-not $ArchiveDir) { $ArchiveDir = Join-Path $PilotRoot "evidence\staging" }
$ArchiveDir = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ArchiveDir)
New-Item -ItemType Directory -Force -Path $ArchiveDir | Out-Null

if (-not (Test-Path $EnvFile)) {
    Write-Host "FAIL  Missing $EnvFile — copy .env.staging.example to .env" -ForegroundColor Red
    exit 1
}

Get-Content $EnvFile | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
    $k, $v = $_ -split '=', 2
    if ($k -and $null -ne $v) { Set-Item -Path "env:$k" -Value $v.Trim() }
}

if (-not $BackendUrl) { $BackendUrl = $env:SCF_API_HEALTH_URL }

if ($WatchHours -gt 0) { $WatchMinutes = $WatchHours * 60 }
if ($WatchMinutes -le 0) { $WatchMinutes = 30 }

$ts = Get-Date -Format "yyyyMMdd-HHmmss"
$summaryPath = Join-Path $ArchiveDir "staging-gate-${ts}.summary.md"
$steps = @()
$seedLogPath = $null
$watchLogPath = $null

$gitRev = try { (git -C $RepoRoot rev-parse --short HEAD 2>$null).Trim() } catch { "unknown" }

function Step {
    param([string]$Name, [scriptblock]$Action)
    Write-Host "`n>> $Name" -ForegroundColor Cyan
    & $Action
    $code = $LASTEXITCODE
    $ok = ($code -eq 0)
    $script:steps += [pscustomobject]@{ Step = $Name; OK = $ok; Exit = $code }
    if (-not $ok) { Write-Host "FAIL $Name (exit $code)" -ForegroundColor Red }
    else { Write-Host "PASS $Name" -ForegroundColor Green }
}

Step "verify-pilot-seed (archive)" {
    & (Join-Path $ScriptDir "verify-pilot-seed.ps1") -ArchiveDir $ArchiveDir
    $script:seedLogPath = Get-ChildItem -Path $ArchiveDir -Filter "seed-verify-*.log" |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty Name
}

Step "check-pilot-alerts StrictStale (once)" {
    $alertArgs = @("-StrictStale")
    if ($BackendUrl) { $alertArgs += @("-BackendUrl", $BackendUrl) }
    & (Join-Path $PilotRoot "monitoring\check-pilot-alerts.ps1") @alertArgs
}

if (-not $SkipAlertsWatch) {
    $iterations = [Math]::Max(1, [int]($WatchMinutes / $WatchIntervalMinutes))
    Step "alerts watch ${WatchMinutes}m (StrictStale x$iterations)" {
        $watchArgs = @("-IntervalMinutes", $WatchIntervalMinutes, "-Iterations", $iterations, "-ArchiveDir", $ArchiveDir)
        if ($BackendUrl) { $watchArgs += @("-BackendUrl", $BackendUrl) }
        & (Join-Path $ScriptDir "run-alerts-watch.ps1") @watchArgs
        $script:watchLogPath = Get-ChildItem -Path $ArchiveDir -Filter "alerts-watch-*.log" |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty Name
    }
}

$allPass = ($steps | Where-Object { -not $_.OK }).Count -eq 0
$md = @(
    "# EA-035 Staging Gate Summary",
    "",
    "| Field | Value |",
    "|---|---|",
    "| Timestamp | $(Get-Date -Format o) |",
    "| Git | ``$gitRev`` |",
    "| Env | $($env:SCF_ENV_NAME) |",
    "| Health | $BackendUrl |",
    "| Watch | ${WatchMinutes}m / ${WatchIntervalMinutes}m interval |",
    "| Result | $(if ($allPass) { '**PASS**' } else { '**FAIL**' }) |",
    "",
    "## Evidence",
    "",
    "| Artifact | File |",
    "|---|---|",
    "| seed-verify | $(if ($seedLogPath) { $seedLogPath } else { '—' }) |",
    "| alerts-watch | $(if ($watchLogPath) { $watchLogPath } else { '—' }) |",
    "",
    "## Steps",
    "",
    "| Step | OK | Exit |",
    "|---|---|---|"
)
foreach ($s in $steps) {
    $md += "| $($s.Step) | $(if ($s.OK) { 'PASS' } else { 'FAIL' }) | $($s.Exit) |"
}
$md += ""
$md += "## Post-gate"
$md += ""
$md += "- Report: ``.\scripts\generate-ea035-report.ps1``"
$md += "- Sign-off: ``evidence/staging/GO_NO_GO_CHECKLIST.md``"
$md += ""
$md | Set-Content $summaryPath -Encoding UTF8
Write-Host "`nSummary: $summaryPath" -ForegroundColor DarkGray

if ($allPass) {
    Write-Host ">>> STAGING GATE: PASS <<<" -ForegroundColor Green
    exit 0
}
Write-Host ">>> STAGING GATE: FAIL <<<" -ForegroundColor Red
exit 1
