# EA-032/033 Pilot seed verification (PowerShell)
# Requires psql or docker postgres — see _psql.ps1
# Usage: .\verify-pilot-seed.ps1 [-ArchiveDir deploy/pilot/evidence/staging]

param(
    [string]$ArchiveDir
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "_psql.ps1")
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

if ($ArchiveDir) {
    $ArchiveDir = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ArchiveDir)
    New-Item -ItemType Directory -Force -Path $ArchiveDir | Out-Null
}

$dbHost = $env:SCF_DB_HOST; if (-not $dbHost) { $dbHost = "localhost" }
$port = $env:SCF_DB_PORT; if (-not $port) { $port = "5432" }
$db   = $env:SCF_DB_NAME; if (-not $db)   { $db = "scf" }
$user = $env:SCF_DB_USER; if (-not $user) { $user = "scf" }
$pass = $env:SCF_DB_PASSWORD

if (-not $pass) {
    Write-Host "FAIL  SCF_DB_PASSWORD not set (use deploy/pilot/.env)" -ForegroundColor Red
    exit 1
}

$env:PGPASSWORD = $pass
$sqlFile = Join-Path $ScriptDir "verify-pilot-seed.sql"

Write-Host "=== EA-032 Seed Verification ===" -ForegroundColor Cyan
Write-Host "Target: ${user}@${dbHost}:${port}/${db}"

$archivePath = $null
if ($ArchiveDir) {
    $ts = Get-Date -Format "yyyyMMdd-HHmmss"
    $archivePath = Join-Path $ArchiveDir "seed-verify-${ts}.log"
    Write-Host "Archive: $archivePath" -ForegroundColor DarkGray
}

try {
    if ($archivePath) {
        @(
            "=== EA-033 Staging Seed Verification ===",
            "Timestamp: $(Get-Date -Format o)",
            "Target: ${user}@${dbHost}:${port}/${db}",
            ""
        ) | Set-Content -Path $archivePath -Encoding UTF8
        $code = Invoke-ScfPsql -FilePath $sqlFile -PilotRoot $Root -LogAppendPath $archivePath
        if ($code -ne 0) { throw "psql exited $code" }
    } else {
        $code = Invoke-ScfPsql -FilePath $sqlFile -PilotRoot $Root
        if ($code -ne 0) { throw "psql exited $code" }
    }
} catch {
    Write-Host "FAIL  Seed verification: $_" -ForegroundColor Red
    exit 1
}

# Quick gate: mock_hash on platform_admin is WARN for prod
$mockCheck = (Invoke-ScfPsqlQuery -Query `
    "SET search_path TO scf; SELECT COUNT(*) FROM sys_user WHERE login_name='platform_admin' AND password_hash='mock_hash';" `
    -PilotRoot $Root).Trim()
if ($mockCheck -eq "1") {
    Write-Host "WARN  platform_admin still has mock_hash — reset password before pilot go-live (prod disables DevDataInitializer)" -ForegroundColor Yellow
    if ($archivePath) { "WARN platform_admin mock_hash=1" | Add-Content -Path $archivePath }
}

Write-Host "PASS  Seed verification SQL completed" -ForegroundColor Green
if ($archivePath) { "RESULT: PASS" | Add-Content -Path $archivePath }
exit 0
