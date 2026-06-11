# EA-048: real external vendor sandbox re-run + Go/No-Go sign-off bundle
# Flow: preflight -> EA-046 evidence -> EA-047 pre-cutover gate -> ea048 bundle
param(
    [string]$EnvFile,
    [switch]$SkipEa046,
    [switch]$SkipEa047,
    [switch]$AllowLocalSandbox,
    [string]$Ea046RunId = ""
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$PilotRoot = Resolve-Path (Join-Path $ScriptDir "..")
$EvidenceRoot = Join-Path $PilotRoot "evidence\contract-sign-gonogo"

if (-not $EnvFile) {
    $EnvFile = Join-Path $PilotRoot ".env.ea048-real-vendor"
}

function Load-DotEnv {
    param([string]$Path)
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) { return }
        $idx = $line.IndexOf("=")
        if ($idx -lt 1) { return }
        Set-Item -Path "Env:$($line.Substring(0, $idx).Trim())" -Value $line.Substring($idx + 1).Trim()
    }
}

function GateLine {
    param([string]$Id, [bool]$Ok, [string]$Detail = "")
    $script:gonogoChecks += [ordered]@{ id = $Id; pass = $Ok; detail = $Detail }
    if (-not $Ok) { $script:gonogoPass = $false }
    Write-Host $(if ($Ok) { "PASS" } else { "FAIL" }) " $Id $(if ($Detail) { "— $Detail" })"
}

function Write-RedactedEnv {
    param([string]$Source, [string]$Destination)
    $secretPattern = '(?i)(PASSWORD|SECRET|TOKEN|APP_SECRET|PRIVATE_KEY|PUBLIC_KEY|PGPASSWORD|DB_PASSWORD)'
    Get-Content $Source | ForEach-Object {
        $line = $_
        if ($line -match '^\s*#' -or $line -notmatch '=') { return $line }
        $idx = $line.IndexOf("=")
        $key = $line.Substring(0, $idx).Trim()
        if ($key -match $secretPattern) {
            return "$key=***REDACTED***"
        }
        return $line
    } | Set-Content -Path $Destination -Encoding UTF8
}

if (-not (Test-Path $EnvFile)) {
    Write-Host "FAIL  Env file not found: $EnvFile" -ForegroundColor Red
    Write-Host "      copy .env.ea048-real-vendor.example .env.ea048-real-vendor" -ForegroundColor Yellow
    exit 1
}
Load-DotEnv $EnvFile

$runId = "ea048-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
$archiveDir = Join-Path $EvidenceRoot $runId
New-Item -ItemType Directory -Force -Path $archiveDir | Out-Null

$gonogoChecks = @()
$gonogoPass = $true
$ea046Verdict = "SKIP"
$ea046EvidenceRunId = ""
$ea046EvidencePath = ""
$ea047Verdict = "SKIP"
$externalSignRef = ""
$providerRequestId = ""
$providerTraceId = ""

Write-Host "=== EA-048 Real Vendor Sandbox Go/No-Go ===" -ForegroundColor Cyan
Write-Host "RunId: $runId"
Write-Host "Env:   $EnvFile"
Write-Host "Archive: $archiveDir"

# ── Phase 0: Preflight ─────────────────────────────────────────────────────
Write-Host "`n--- Phase 0: Preflight ---" -ForegroundColor Cyan

$placeholder = Select-String -Path $EnvFile -Pattern 'CHANGE_ME' -SimpleMatch
GateLine "G-E048-00" (-not $placeholder) "no CHANGE_ME in env file"

$envName = if ($env:SCF_ENV_NAME) { $env:SCF_ENV_NAME } else { "" }
$isLocal = ($envName -eq "local-quasi-sandbox")
if ($isLocal -and -not $AllowLocalSandbox) {
    GateLine "G-E048-01" $false "SCF_ENV_NAME=local-quasi-sandbox — use real vendor staging (or -AllowLocalSandbox for dry-run)"
} else {
    GateLine "G-E048-01" $true "environment=$envName"
}

GateLine "G-E048-02" ([bool]$env:SCF_CONTRACT_SIGN_HTTP_BASE_URL) "vendor base URL set"
GateLine "G-E048-03" ([bool]$env:SCF_CONTRACT_SIGN_HTTP_APP_ID) "vendor app_id set"
$authMode = if ($env:SCF_CONTRACT_SIGN_HTTP_OUTBOUND_AUTH_MODE) { $env:SCF_CONTRACT_SIGN_HTTP_OUTBOUND_AUTH_MODE.ToUpperInvariant() } else { "HMAC_SHA256" }
if ($authMode -in @("RSA_SHA256", "SM2")) {
    GateLine "G-E048-04" ([bool]$env:SCF_CONTRACT_SIGN_HTTP_PRIVATE_KEY_PEM) "vendor private key set for $authMode"
} else {
    GateLine "G-E048-04" ([bool]$env:SCF_CONTRACT_SIGN_HTTP_APP_SECRET) "vendor app_secret set for HMAC"
}
GateLine "G-E048-05" ($env:EA048_ROLLBACK_ACK -eq "yes") "EA048_ROLLBACK_ACK=yes"
GateLine "G-E048-06" ($env:SCF_CONTRACT_SIGN_ROLLOUT_MODE -eq "ALLOWLIST") "planned ALLOWLIST grayscale"
GateLine "G-E048-07" ([bool]$env:SCF_CONTRACT_SIGN_ROLLOUT_PROJECT_ALLOWLIST) "pilot project allowlist defined"

if (-not $gonogoPass) {
    Write-Host "`n>>> EA-048: FAIL — fix preflight before continuing <<<" -ForegroundColor Red
    exit 1
}

& (Join-Path $ScriptDir "verify-contract-sign-config.ps1") -EnvFile $EnvFile
if ($LASTEXITCODE -ne 0) {
    GateLine "G-E048-08" $false "verify-contract-sign-config failed"
    exit 1
}
GateLine "G-E048-08" $true "config probe"

# ── Phase 1: EA-046 real vendor evidence ─────────────────────────────────────
Write-Host "`n--- Phase 1: EA-046 vendor sandbox evidence ---" -ForegroundColor Cyan

if (-not $SkipEa046) {
    $ea046Args = @{ EnvFile = $EnvFile }
    if ($Ea046RunId) { $ea046Args["RunId"] = $Ea046RunId }
    & (Join-Path $ScriptDir "run-ea046-sandbox-evidence.ps1") @ea046Args
    if ($LASTEXITCODE -ne 0) {
        $ea046Verdict = "FAIL"
        GateLine "G-E048-10" $false "EA-046 evidence script failed"
    } else {
        $ea046Verdict = "PASS"
        GateLine "G-E048-10" $true "EA-046 closed loop"
    }
} else {
    $ea046Verdict = if ($env:EA046_EVIDENCE_VERDICT) { $env:EA046_EVIDENCE_VERDICT } else { "SKIP" }
    GateLine "G-E048-10" ($ea046Verdict -eq "PASS") "EA-046 skipped; EA046_EVIDENCE_VERDICT=$ea046Verdict"
}

# Locate latest EA-046 JSON (this run or env override)
if ($env:EA046_EVIDENCE_RUN_ID) {
    $ea046EvidenceRunId = $env:EA046_EVIDENCE_RUN_ID
} else {
    $ea046Dir = Join-Path $PilotRoot "evidence\contract-sign-sandbox"
    $latest = Get-ChildItem -Path $ea046Dir -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($latest) {
        $ea046EvidenceRunId = $latest.Name
        $candidate = Join-Path $latest.FullName "$($latest.Name).json"
        if (Test-Path $candidate) {
            $ea046Json = Get-Content $candidate -Raw | ConvertFrom-Json
            if ($ea046Json.verdict) { $ea046Verdict = $ea046Json.verdict }
            $externalSignRef = $ea046Json.external_sign_ref
            $providerRequestId = $ea046Json.provider_request_id
            $providerTraceId = $ea046Json.provider_trace_id
            $ea046EvidencePath = "deploy/pilot/evidence/contract-sign-sandbox/$ea046EvidenceRunId/"
        }
    }
}

$env:EA046_EVIDENCE_VERDICT = $ea046Verdict
$env:EA046_EVIDENCE_RUN_ID = $ea046EvidenceRunId

GateLine "G-E048-11" ($ea046Verdict -eq "PASS") "EA-046 verdict=$ea046Verdict run_id=$ea046EvidenceRunId"
if ($isLocal -and $ea046Verdict -eq "PASS" -and -not $AllowLocalSandbox) {
    GateLine "G-E048-12" $false "real vendor required (not local-quasi-sandbox)"
} elseif ($ea046Verdict -eq "PASS") {
    GateLine "G-E048-12" ($envName -ne "local-quasi-sandbox") "vendor environment=$envName"
} else {
    GateLine "G-E048-12" $false "EA-046 not PASS"
}

# ── Phase 2: EA-047 pre-cutover readiness ───────────────────────────────────
Write-Host "`n--- Phase 2: EA-047 pre-cutover readiness ---" -ForegroundColor Cyan

if (-not $SkipEa047 -and $gonogoPass) {
    & (Join-Path $ScriptDir "run-ea047-prod-cutover-gate.ps1") -EnvFile $EnvFile -PreCutover
    if ($LASTEXITCODE -ne 0) {
        $ea047Verdict = "FAIL"
        GateLine "G-E048-20" $false "EA-047 pre-cutover gate"
    } else {
        $ea047Verdict = "PASS"
        GateLine "G-E048-20" $true "EA-047 pre-cutover gate"
    }
} else {
    $ea047Verdict = "SKIP"
}

$finalVerdict = if ($gonogoPass -and $ea046Verdict -eq "PASS" -and $ea047Verdict -eq "PASS") { "GO" } else { "NO-GO" }

# ── Bundle ───────────────────────────────────────────────────────────────────
$bundle = @{
    ea_id                 = "EA-048"
    run_id                = $runId
    environment           = $envName
    vendor                = @{
        name         = $env:SCF_VENDOR_NAME
        doc_version  = $env:SCF_VENDOR_DOC_VERSION
        base_url     = $env:SCF_CONTRACT_SIGN_HTTP_BASE_URL
        callback_url = $env:SCF_VENDOR_CALLBACK_URL
        ticket       = $env:SCF_VENDOR_TICKET
    }
    ea046                 = @{
        run_id              = $ea046EvidenceRunId
        verdict             = $ea046Verdict
        evidence_path       = $ea046EvidencePath
        external_sign_ref   = $externalSignRef
        provider_request_id = $providerRequestId
        provider_trace_id   = $providerTraceId
    }
    ea047                 = @{
        phase   = "pre-cutover"
        verdict = $ea047Verdict
    }
    rollout_plan          = @{
        mode              = $env:SCF_CONTRACT_SIGN_ROLLOUT_MODE
        project_allowlist = $env:SCF_CONTRACT_SIGN_ROLLOUT_PROJECT_ALLOWLIST
        fallback_provider = $env:SCF_CONTRACT_SIGN_ROLLOUT_FALLBACK_PROVIDER
    }
    checks                = $gonogoChecks
    verdict               = $finalVerdict
    signed_off_by         = $env:EA048_RUN_BY
    signed_off_role       = $env:EA048_SIGN_OFF_ROLE
    signed_off_at         = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    next_step_on_go       = @(
        "Deploy prod with SCF_CONTRACT_SIGN_ROLLOUT_MODE=ALLOWLIST and project allowlist",
        "Re-run run-ea047-prod-cutover-gate.ps1 (without -PreCutover) on pilot prod",
        "Monitor compensation pool + sign success rate 24h before PERCENT ramp"
    )
}

$jsonPath = Join-Path $archiveDir "$runId.json"
$bundle | ConvertTo-Json -Depth 20 | Set-Content -Path $jsonPath -Encoding UTF8

$checkTableLines = ($gonogoChecks | ForEach-Object { "| $($_.id) | $($_.pass) | $($_.detail) |" }) -join [Environment]::NewLine
$ea046VerdictDisplay = $bundle.ea046['verdict']
$rolloutMode = $env:SCF_CONTRACT_SIGN_ROLLOUT_MODE
$rolloutAllowlist = $env:SCF_CONTRACT_SIGN_ROLLOUT_PROJECT_ALLOWLIST

$summary = @(
    "# EA-048 Go/No-Go — $runId"
    ""
    "| 项 | 值 |"
    "|---|---|"
    "| **决策** | **$finalVerdict** |"
    "| EA-046 | $ea046VerdictDisplay / ``$ea046EvidenceRunId`` |"
    "| EA-047 (pre-cutover) | $ea047Verdict |"
    "| external_sign_ref | $externalSignRef |"
    "| provider_request_id | $providerRequestId |"
    "| provider_trace_id | $providerTraceId |"
    "| 计划灰度 | $rolloutMode -> $rolloutAllowlist |"
    ""
    "## Checks"
    ""
    "| ID | Pass | Detail |"
    "|---|---|---|"
    $checkTableLines
    ""
    "## Artifacts"
    ""
    "- ``$runId.json`` — Go/No-Go bundle"
    "- EA-046: ``$ea046EvidencePath``"
    ""
    "## If GO"
    ""
    "1. Deploy prod with SCF_CONTRACT_SIGN_ROLLOUT_MODE=ALLOWLIST + allowlist"
    "2. Re-run run-ea047-prod-cutover-gate.ps1 (no -PreCutover); expect routed_to_production=true"
    "3. Monitor sign success rate and compensation pool 24h before PERCENT ramp"
    ""
    "## If NO-GO"
    ""
    "Fix FAIL checks and re-run run-ea048-real-vendor-gonogo.ps1. Do NOT enable production ALLOWLIST."
) -join [Environment]::NewLine

$summaryPath = Join-Path $archiveDir "$runId.summary.md"
$summary | Set-Content -Path $summaryPath -Encoding UTF8

Write-RedactedEnv -Source $EnvFile -Destination (Join-Path $archiveDir "env.redacted.template.md")

Write-Host "`n=== EA-048 Complete ===" -ForegroundColor Cyan
Write-Host "Bundle:  $jsonPath"
Write-Host "Summary: $summaryPath"
if ($finalVerdict -eq "GO") {
    Write-Host ">>> EA-048: GO — may proceed to ALLOWLIST production grayscale <<<" -ForegroundColor Green
    exit 0
}
Write-Host ">>> EA-048: NO-GO — do NOT enable production ALLOWLIST <<<" -ForegroundColor Red
exit 1
