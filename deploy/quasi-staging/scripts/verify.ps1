param(
    [int]$WatchMinutes = 30,
    [int]$WatchIntervalMinutes = 5,
    [switch]$SkipAlertsWatch
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$RepoRoot = Resolve-Path (Join-Path $Root "..\..")
$EnvFile = Join-Path $Root ".env"
$ArchiveDir = Join-Path $Root "evidence"
$ComposeFile = Join-Path $Root "docker-compose.yml"

if (-not (Test-Path $EnvFile)) {
    Write-Host "FAIL  Missing $EnvFile. Run .\scripts\up.ps1 first." -ForegroundColor Red
    exit 1
}

$env:SCF_PILOT_ENV_FILE = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($EnvFile)

Get-Content $EnvFile | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
    $k, $v = $_ -split '=', 2
    if ($k -and $null -ne $v) { Set-Item -Path "env:$k" -Value $v.Trim() }
}

if (Get-Command docker -ErrorAction SilentlyContinue) {
    Write-Host "Waiting for quasi-staging backend and seed-passwords ..." -ForegroundColor Cyan
    $healthUrl = $env:SCF_API_HEALTH_URL
    if (-not $healthUrl) { $healthUrl = "http://localhost:18080/api/v1/actuator/health" }
    $healthy = $false
    for ($i = 0; $i -lt 60; $i++) {
        try {
            $r = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 3
            if ($r.status -eq "UP") { $healthy = $true; break }
        } catch {}
        Start-Sleep -Seconds 2
    }
    if (-not $healthy) {
        Write-Host "FAIL  Backend health did not become UP: $healthUrl" -ForegroundColor Red
        exit 1
    }

    $seedOk = $false
    for ($i = 0; $i -lt 30; $i++) {
        $exitCode = docker inspect -f "{{.State.ExitCode}}" scf-qs-seed-passwords 2>$null
        if ($LASTEXITCODE -eq 0 -and $exitCode -eq "0") { $seedOk = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $seedOk) {
        Write-Host "FAIL  seed-passwords did not complete successfully" -ForegroundColor Red
        docker compose --env-file $EnvFile -f $ComposeFile logs seed-passwords
        exit 1
    }
}

$gateArgs = @(
    "-EnvFile", $EnvFile,
    "-ArchiveDir", $ArchiveDir,
    "-WatchMinutes", $WatchMinutes,
    "-WatchIntervalMinutes", $WatchIntervalMinutes
)
if ($SkipAlertsWatch) { $gateArgs += "-SkipAlertsWatch" }

& (Join-Path $RepoRoot "deploy\pilot\scripts\run-staging-gate.ps1") @gateArgs
exit $LASTEXITCODE
