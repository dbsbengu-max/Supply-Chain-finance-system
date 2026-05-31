# EA-032 Pilot seed verification (PowerShell)
# Requires: psql in PATH, deploy/pilot/.env or SCF_DB_* env vars

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

$host = $env:SCF_DB_HOST; if (-not $host) { $host = "localhost" }
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
Write-Host "Target: ${user}@${host}:${port}/${db}"

try {
    psql -h $host -p $port -U $user -d $db -f $sqlFile
    if ($LASTEXITCODE -ne 0) { throw "psql exited $LASTEXITCODE" }
} catch {
    Write-Host "FAIL  Seed verification: $_" -ForegroundColor Red
    exit 1
}

# Quick gate: mock_hash on platform_admin is WARN for prod
$mockCheck = psql -h $host -p $port -U $user -d $db -t -A -c `
    "SET search_path TO scf; SELECT COUNT(*) FROM sys_user WHERE login_name='platform_admin' AND password_hash='mock_hash';"
if ($mockCheck.Trim() -eq "1") {
    Write-Host "WARN  platform_admin still has mock_hash — enable password bootstrap only in dev, or set password before pilot go-live" -ForegroundColor Yellow
}

Write-Host "PASS  Seed verification SQL completed" -ForegroundColor Green
exit 0
