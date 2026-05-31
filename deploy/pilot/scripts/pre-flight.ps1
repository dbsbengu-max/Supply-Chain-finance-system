# EA-032 Pre-flight checklist (pilot go-live)
# Usage: .\deploy\pilot\scripts\pre-flight.ps1 [-SkipBuild] [-SkipSmoke]

param(
    [switch]$SkipBuild,
    [switch]$SkipSmoke
)

$ErrorActionPreference = "Stop"
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")
$BackendUrl = $env:SCF_API_HEALTH_URL
if (-not $BackendUrl) { $BackendUrl = "http://localhost:8080/api/v1/actuator/health" }

$results = @()

function Record($name, $ok, $detail = "") {
    $script:results += [pscustomobject]@{ Check = $name; OK = $ok; Detail = $detail }
    $mark = if ($ok) { "PASS" } else { "FAIL" }
    $color = if ($ok) { "Green" } else { "Red" }
    Write-Host "$mark  $name $(if ($detail) { "— $detail" })" -ForegroundColor $color
}

Write-Host "`n=== EA-032 Pre-flight ===" -ForegroundColor Cyan

# 1. Backend health
try {
    $r = Invoke-RestMethod -Uri $BackendUrl -Method Get -TimeoutSec 10
    Record "Backend health" ($r.status -eq "UP") "service=$($r.service)"
} catch {
    Record "Backend health" $false $_.Exception.Message
}

# 2. Seed verification
$seedScript = Join-Path $PSScriptRoot "verify-pilot-seed.ps1"
if (Test-Path $seedScript) {
    & $seedScript
    Record "Seed verification" ($LASTEXITCODE -eq 0)
} else {
    Record "Seed verification" $false "script missing"
}

# 3. Frontend build
if (-not $SkipBuild) {
    Push-Location (Join-Path $RepoRoot "frontend\scf-web")
    try {
        npm run build 2>&1 | Out-Null
        Record "Frontend build" ($LASTEXITCODE -eq 0)
    } catch {
        Record "Frontend build" $false $_.Exception.Message
    } finally {
        Pop-Location
    }
} else {
    Record "Frontend build" $true "skipped"
}

# 4. Browser smoke (optional)
if (-not $SkipSmoke) {
    Push-Location (Join-Path $RepoRoot "frontend\scf-web")
    $env:SMOKE_SKIP_WEBSERVER = "1"
    try {
        npm run smoke 2>&1 | Out-Null
        Record "Browser smoke" ($LASTEXITCODE -eq 0)
    } catch {
        Record "Browser smoke" $false $_.Exception.Message
    } finally {
        Pop-Location
    }
} else {
    Record "Browser smoke" $true "skipped"
}

Write-Host "`n=== Summary ===" -ForegroundColor Cyan
$results | Format-Table -AutoSize
$allPass = ($results | Where-Object { -not $_.OK }).Count -eq 0
if ($allPass) {
    Write-Host ">>> PRE-FLIGHT: PASS <<<" -ForegroundColor Green
    exit 0
}
Write-Host ">>> PRE-FLIGHT: FAIL <<<" -ForegroundColor Red
exit 1
