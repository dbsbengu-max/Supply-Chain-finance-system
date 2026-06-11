# EA-034 Staging validation runner — seed archive + alerts + pre-flight
# Usage:
#   cd deploy\pilot
#   copy .env.staging.example .env   # edit first
#   .\scripts\run-staging-validation.ps1 [-SkipBuild] [-SkipSmoke]

param(
    [string]$EnvFile,
    [string]$ArchiveDir,
    [string]$BackendUrl,
    [switch]$SkipBuild,
    [switch]$SkipSmoke
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$PilotRoot = Resolve-Path (Join-Path $ScriptDir "..")
$RepoRoot = Resolve-Path (Join-Path $PilotRoot "..\..")

function Load-DotEnv {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return $false }
    Get-Content $Path | ForEach-Object {
        if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
        $k, $v = $_ -split '=', 2
        if ($k -and $null -ne $v) { Set-Item -Path "env:$k" -Value $v.Trim() }
    }
    return $true
}

if (-not $EnvFile) { $EnvFile = Join-Path $PilotRoot ".env" }
if (-not $ArchiveDir) { $ArchiveDir = Join-Path $PilotRoot "evidence\staging" }
$ArchiveDir = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ArchiveDir)
New-Item -ItemType Directory -Force -Path $ArchiveDir | Out-Null

if (-not (Load-DotEnv $EnvFile)) {
    Write-Host "FAIL  Missing $EnvFile — copy .env.staging.example to .env" -ForegroundColor Red
    exit 1
}

if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
    Write-Host "FAIL  psql not in PATH" -ForegroundColor Red
    exit 1
}

if (-not $BackendUrl) { $BackendUrl = $env:SCF_API_HEALTH_URL }
if (-not $BackendUrl) { $BackendUrl = "http://localhost:8080/api/v1/actuator/health" }

$ts = Get-Date -Format "yyyyMMdd-HHmmss"
$summaryPath = Join-Path $ArchiveDir "staging-validation-${ts}.summary.md"
$envName = if ($env:SCF_ENV_NAME) { $env:SCF_ENV_NAME } else { "staging" }
$runner = if ($env:STAGING_VALIDATION_RUN_BY) { $env:STAGING_VALIDATION_RUN_BY } else { $env:USERNAME }

$steps = @()

function Record-Step {
    param([string]$Name, [int]$ExitCode, [string]$Detail = "")
    $ok = ($ExitCode -eq 0)
    $script:steps += [pscustomobject]@{
        Step = $Name
        OK = $ok
        ExitCode = $ExitCode
        Detail = $Detail
    }
    $mark = if ($ok) { "PASS" } else { "FAIL" }
    $color = if ($ok) { "Green" } else { "Red" }
    Write-Host "$mark  $Name (exit $ExitCode) $(if ($Detail) { "— $Detail" })" -ForegroundColor $color
}

Write-Host "`n=== EA-034 Staging Validation ===" -ForegroundColor Cyan
Write-Host "Env: $envName | Archive: $ArchiveDir"
Write-Host "Health: $BackendUrl"
Write-Host "Runner: $runner | $(Get-Date -Format o)`n"

# 1. Seed + archive
& (Join-Path $ScriptDir "verify-pilot-seed.ps1") -ArchiveDir $ArchiveDir
Record-Step "Seed verification (archived)" $LASTEXITCODE

# 2. Alerts A-01 / A-03 / A-04
$monitorDir = Join-Path $PilotRoot "monitoring"
& (Join-Path $monitorDir "check-pilot-alerts.ps1") -BackendUrl $BackendUrl
Record-Step "Pilot alerts A-01/A-03/A-04" $LASTEXITCODE

# 3. Pre-flight (build + smoke optional)
$preflightArgs = @("-BackendUrl", $BackendUrl)
if ($SkipBuild) { $preflightArgs += "-SkipBuild" }
if ($SkipSmoke) { $preflightArgs += "-SkipSmoke" }
& (Join-Path $ScriptDir "pre-flight.ps1") @preflightArgs
Record-Step "Pre-flight" $LASTEXITCODE

# Write summary markdown
$seedLogs = Get-ChildItem -Path $ArchiveDir -Filter "seed-verify-*.log" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$allPass = ($steps | Where-Object { -not $_.OK }).Count -eq 0

$md = @(
    "# Staging Validation Summary",
    "",
    "| Field | Value |",
    "|---|---|",
    "| Environment | $envName |",
    "| Timestamp | $(Get-Date -Format o) |",
    "| Runner | $runner |",
    "| Ticket | $($env:STAGING_VALIDATION_TICKET) |",
    "| Health URL | $BackendUrl |",
    "| Seed log | $(if ($seedLogs) { $seedLogs.Name } else { '—' }) |",
    "| Result | $(if ($allPass) { '**PASS**' } else { '**FAIL**' }) |",
    "",
    "## Steps",
    "",
    "| Step | OK | Exit | Detail |",
    "|---|---|---|---|"
)
foreach ($s in $steps) {
    $md += "| $($s.Step) | $(if ($s.OK) { 'PASS' } else { 'FAIL' }) | $($s.ExitCode) | $($s.Detail) |"
}
$md += @(
    "",
    "## Next",
    "",
    "- Fill [ACCEPTANCE_TEMPLATE.md](./ACCEPTANCE_TEMPLATE.md) and attach this summary.",
    "- If FAIL, see [STAGING_EXECUTION_CHECKLIST.md](../../staging/STAGING_EXECUTION_CHECKLIST.md) §5.",
    ""
)
$md | Set-Content -Path $summaryPath -Encoding UTF8
Write-Host "`nSummary: $summaryPath" -ForegroundColor DarkGray

if ($allPass) {
    Write-Host ">>> STAGING VALIDATION: PASS <<<" -ForegroundColor Green
    exit 0
}
Write-Host ">>> STAGING VALIDATION: FAIL <<<" -ForegroundColor Red
exit 1
