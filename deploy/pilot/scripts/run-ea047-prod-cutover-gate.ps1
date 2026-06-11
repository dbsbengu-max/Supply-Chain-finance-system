# EA-047: production cutover gate for contract-sign vendor grayscale
param(
    [string]$EnvFile = (Join-Path (Split-Path $PSScriptRoot -Parent) ".env.prod-cutover"),
    [switch]$DryRun,
    [switch]$PreCutover
)

$ErrorActionPreference = "Stop"

function Load-DotEnv {
    param([string]$Path)
    Get-Content $Path | ForEach-Object {
        if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
        $parts = $_ -split '=', 2
        if ($parts.Count -eq 2) {
            Set-Item -Path "Env:$($parts[0].Trim())" -Value $parts[1].Trim()
        }
    }
}

if (-not (Test-Path $EnvFile)) {
    Write-Host "FAIL  Env file not found: $EnvFile" -ForegroundColor Red
    exit 1
}
Load-DotEnv $EnvFile

$checks = @()
$pass = $true

function GateCheck {
    param([string]$Id, [bool]$Ok, [string]$Detail = "")
    $script:checks += [ordered]@{ id = $Id; pass = $Ok; detail = $Detail }
    if (-not $Ok) { $script:pass = $false }
    Write-Host $(if ($Ok) { "PASS" } else { "FAIL" }) " $Id $(if ($Detail) { "— $Detail" })"
}

Write-Host "=== EA-047 Production Cutover Gate$(if ($PreCutover) { ' (pre-cutover readiness)' }) ===" -ForegroundColor Cyan

GateCheck "G-E047-01" ($env:EA046_EVIDENCE_VERDICT -eq "PASS") "EA-046 verdict must be PASS"
GateCheck "G-E047-02" ([bool]$env:EA046_EVIDENCE_RUN_ID) "EA-046 run_id required"

if ($PreCutover) {
    GateCheck "G-E047-03" ($env:SCF_CONTRACT_SIGN_ROLLOUT_MODE -eq "ALLOWLIST") "planned rollout mode ALLOWLIST"
    GateCheck "G-E047-03B" ([bool]$env:SCF_CONTRACT_SIGN_ROLLOUT_PROJECT_ALLOWLIST) "project allowlist defined"
    GateCheck "G-E047-03C" ($env:EA048_ROLLBACK_ACK -eq "yes") "rollback plan acknowledged (EA048_ROLLBACK_ACK=yes)"
} else {
    GateCheck "G-E047-03" ($env:SCF_CONTRACT_SIGN_ROLLOUT_MODE -in @("ALLOWLIST", "PERCENT", "FULL")) "rollout mode active for cutover"
}

$healthUrl = if ($env:SCF_API_HEALTH_URL) { $env:SCF_API_HEALTH_URL } else { "" }
if ($healthUrl) {
    try {
        $health = Invoke-RestMethod -Method GET -Uri $healthUrl
        GateCheck "G-E047-03A" ($health.status -eq "UP") "health=$($health.status)"
    } catch {
        GateCheck "G-E047-03A" $false $_.Exception.Message
    }
}

$loginBody = (@{ login_name = $env:SCF_LOGIN_NAME; password = $env:SCF_LOGIN_PASSWORD } | ConvertTo-Json -Compress)
$baseUrl = if ($env:SCF_BASE_URL -match "/api/v1$") { $env:SCF_BASE_URL.TrimEnd("/") } else { "$($env:SCF_BASE_URL.TrimEnd('/'))/api/v1" }
try {
    $login = Invoke-RestMethod -Method POST -Uri "$baseUrl/auth/login" -ContentType "application/json" -Body $loginBody
    $token = $login.data.accessToken
    GateCheck "G-E047-04" ($null -ne $token) "login"
} catch {
    GateCheck "G-E047-04" $false $_.Exception.Message
    $token = $null
}

if ($token) {
    $headers = @{
        Authorization   = "Bearer $token"
        "X-Operator-Id" = $env:SCF_OPERATOR_ID
        "X-Project-Id"  = $env:SCF_PROJECT_ID
        "X-Request-Id"  = "ea047-gate-config"
    }
    try {
        $cfg = Invoke-RestMethod -Method GET -Uri "$baseUrl/integrations/contracts/sign/config" -Headers $headers
        $conn = $cfg.data.provider_connections | Where-Object { $_.provider_code -eq "ESIGN_HTTP" } | Select-Object -First 1
        GateCheck "G-E047-05" ($conn.configured -eq $true) "ESIGN_HTTP configured"
        GateCheck "G-E047-05A" ($cfg.data.callback_verification_mode -eq "TIMESTAMP_NONCE_SIGNATURE") "callback signature mode"
        GateCheck "G-E047-05B" ($cfg.data.compensation_pool_enabled -eq $true) "compensation pool enabled"
        $rollout = $cfg.data.production_rollout
        if ($PreCutover) {
            GateCheck "G-E047-06" ($rollout.mode -eq "OFF" -or $rollout.mode -eq $env:SCF_CONTRACT_SIGN_ROLLOUT_MODE) "server rollout not yet switched (OK pre-cutover)"
        } else {
            GateCheck "G-E047-06" ($rollout.mode -eq $env:SCF_CONTRACT_SIGN_ROLLOUT_MODE) "rollout mode matches env"
            GateCheck "G-E047-07" ($rollout.routed_to_production -eq $true) "current project routed to production"
            GateCheck "G-E047-08" ($rollout.effective_provider_for_context -eq "ESIGN_HTTP") "effective provider ESIGN_HTTP"
        }
    } catch {
        GateCheck "G-E047-05" $false $_.Exception.Message
    }
}

Write-Host "`n=== EA-047 Gate: $(if ($pass) { 'PASS' } else { 'FAIL' }) ===" -ForegroundColor $(if ($pass) { "Green" } else { "Red" })
if ($DryRun) { exit 0 }
exit $(if ($pass) { 0 } else { 1 })
