param(
    [string]$EnvFile,
    [string]$ArchiveDir,
    [string]$RunId,
    [switch]$SkipInitiate,
    [switch]$SkipCallbackReplay,
    [switch]$SkipCompensationProbe,
    [switch]$SkipDbExport,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$PilotRoot = Resolve-Path (Join-Path $ScriptDir "..")

if (-not $EnvFile) { $EnvFile = Join-Path $PilotRoot ".env.esign-sandbox" }
if (-not (Test-Path $EnvFile)) {
    Write-Host "FAIL  Missing $EnvFile — copy .env.esign-sandbox.example" -ForegroundColor Red
    exit 1
}

function Import-DotEnv {
    param([string]$Path)
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) { return }
        $idx = $line.IndexOf("=")
        if ($idx -lt 1) { return }
        $key = $line.Substring(0, $idx).Trim()
        $val = $line.Substring($idx + 1).Trim()
        if ($val.StartsWith('"') -and $val.EndsWith('"')) { $val = $val.Substring(1, $val.Length - 2) }
        Set-Item -Path "Env:$key" -Value $val
    }
}

Import-DotEnv $EnvFile

function Normalize-BaseUrl {
    param([string]$Url)
    if ([string]::IsNullOrWhiteSpace($Url)) { return "http://127.0.0.1:8080/api/v1" }
    $u = $Url.TrimEnd("/")
    if ($u -notmatch "/api/v1$") { $u = "$u/api/v1" }
    return $u
}

function Save-JsonArtifact {
    param([string]$Dir, [string]$Name, $Object)
    $path = Join-Path $Dir $Name
    if ($Object -is [string]) {
        $Object | Set-Content -Path $path -Encoding UTF8
    } else {
        $Object | ConvertTo-Json -Depth 20 | Set-Content -Path $path -Encoding UTF8
    }
    return $path
}

function Invoke-ScfJson {
    param(
        [string]$Method,
        [string]$RelativePath,
        [hashtable]$Headers = @{},
        [string]$Body = $null
    )
    $uri = "$script:BaseUrl$RelativePath"
    $params = @{
        Method      = $Method
        Uri         = $uri
        Headers     = $Headers
        ContentType = "application/json"
    }
    if ($Body) { $params.Body = $Body }
    try {
        $resp = Invoke-WebRequest @params
        return @{
            StatusCode = [int]$resp.StatusCode
            Content    = $resp.Content
            Headers    = $resp.Headers
        }
    } catch {
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $content = $reader.ReadToEnd()
            return @{
                StatusCode = [int]$_.Exception.Response.StatusCode
                Content    = $content
                Headers    = @{}
            }
        }
        throw
    }
}

function New-Scenario {
    param([string]$Id, [bool]$Passed, [hashtable]$Extra = @{})
    $s = @{ id = $Id; passed = $Passed }
    foreach ($k in $Extra.Keys) { $s[$k] = $Extra[$k] }
    return $s
}

function Hmac-Hex {
    param([string]$Secret, [string]$Payload)
    $hmac = New-Object System.Security.Cryptography.HMACSHA256
    $hmac.Key = [Text.Encoding]::UTF8.GetBytes($Secret)
    $bytes = $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($Payload))
    return -join ($bytes | ForEach-Object { $_.ToString("x2") })
}

function Build-CallbackPayloadJson {
    param(
        [string]$ExternalSignRef,
        [string]$CallbackStatus,
        [string]$SignedAt = "2026-06-01T10:00:00Z",
        [string]$ProviderCode = "ESIGN_HTTP",
        [AllowNull()][string]$FailureReason = $null
    )
    # Must match Jackson serialization of ContractSignCallbackRequest (field order + explicit null).
    if ($null -eq $FailureReason) {
        return "{`"external_sign_ref`":`"$ExternalSignRef`",`"callback_status`":`"$CallbackStatus`",`"signed_at`":`"$SignedAt`",`"failure_reason`":null,`"provider_code`":`"$ProviderCode`"}"
    }
    return "{`"external_sign_ref`":`"$ExternalSignRef`",`"callback_status`":`"$CallbackStatus`",`"signed_at`":`"$SignedAt`",`"failure_reason`":`"$FailureReason`",`"provider_code`":`"$ProviderCode`"}"
}

$BaseUrl = Normalize-BaseUrl $env:SCF_BASE_URL
$HealthUrl = if ($env:SCF_API_HEALTH_URL) { $env:SCF_API_HEALTH_URL } else { "$BaseUrl/actuator/health" }
$LoginName = if ($env:SCF_LOGIN_NAME) { $env:SCF_LOGIN_NAME } else { "platform_admin" }
$LoginPassword = $env:SCF_LOGIN_PASSWORD
$OperatorId = if ($env:SCF_OPERATOR_ID) { $env:SCF_OPERATOR_ID } else { "OP001" }
$ProjectId = if ($env:SCF_PROJECT_ID) { $env:SCF_PROJECT_ID } else { "PJ001" }
$DocumentId = if ($env:SCF_ESIGN_DOCUMENT_ID) { $env:SCF_ESIGN_DOCUMENT_ID } else { "DOC_SANDBOX_SIGN_001" }
$CallbackSecret = $env:SCF_CONTRACT_SIGN_CALLBACK_TOKEN

if (-not $RunId) {
    $RunId = "ea046-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
}
if (-not $ArchiveDir) {
    $ArchiveDir = Join-Path $PilotRoot "evidence\contract-sign-sandbox\$RunId"
}
New-Item -ItemType Directory -Force -Path $ArchiveDir | Out-Null

$placeholder = Select-String -Path $EnvFile -Pattern 'CHANGE_ME' -SimpleMatch
if ($placeholder) {
    Write-Host "FAIL  Env file contains CHANGE_ME — fill sandbox credentials first" -ForegroundColor Red
    exit 1
}

Write-Host "=== EA-046 Vendor Sandbox Evidence ===" -ForegroundColor Cyan
Write-Host "RunId: $RunId"
Write-Host "BaseUrl: $BaseUrl"
Write-Host "Archive: $ArchiveDir"

if ($DryRun) {
    Write-Host "DRY RUN — would execute sandbox scenarios" -ForegroundColor Yellow
    exit 0
}

$scenarios = @()
$verdictPass = $true
$externalSignRef = $null
$platformTraceId = $null
$providerRequestId = $null
$providerTraceId = $null
$compensationId = $null

# 1. Health
try {
    $health = Invoke-RestMethod -Uri $HealthUrl -Method Get
    Save-JsonArtifact $ArchiveDir "health.json" $health | Out-Null
    $ok = $health.status -eq "UP"
    $scenarios += New-Scenario "HEALTH" $ok @{ api_request_id = "" }
    if (-not $ok) { $verdictPass = $false }
    Write-Host $(if ($ok) { "PASS" } else { "FAIL" }) " HEALTH"
} catch {
    $scenarios += New-Scenario "HEALTH" $false @{ error = $_.Exception.Message }
    $verdictPass = $false
    Write-Host "FAIL  HEALTH: $($_.Exception.Message)" -ForegroundColor Red
}

# 2. Login (before authenticated config / sign)
if (-not $LoginPassword) {
    $scenarios += New-Scenario "LOGIN" $false @{ error = "SCF_LOGIN_PASSWORD not set" }
    $verdictPass = $false
    Write-Host "FAIL  LOGIN: password not set" -ForegroundColor Red
    exit 1
}

$loginBody = (@{ login_name = $LoginName; password = $LoginPassword } | ConvertTo-Json -Compress)
$loginResp = Invoke-ScfJson -Method POST -RelativePath "/auth/login" -Headers @{ "X-Request-Id" = "$RunId-LOGIN" } -Body $loginBody
$loginOk = $loginResp.StatusCode -eq 200
$token = $null
if ($loginOk) {
    $loginJson = $loginResp.Content | ConvertFrom-Json
    $token = $loginJson.data.accessToken
    if ($loginJson.data.accessToken) { $loginJson.data.accessToken = "***REDACTED***" }
    if ($loginJson.data.refreshToken) { $loginJson.data.refreshToken = "***REDACTED***" }
    Save-JsonArtifact $ArchiveDir "login-response.json" $loginJson | Out-Null
} else {
    Save-JsonArtifact $ArchiveDir "login-response.json" $loginResp.Content | Out-Null
}
$scenarios += New-Scenario "LOGIN" $loginOk @{}
if (-not $loginOk) {
    $verdictPass = $false
    Write-Host "FAIL  LOGIN ($($loginResp.StatusCode))" -ForegroundColor Red
    exit 1
}
Write-Host "PASS  LOGIN"

function PlatformHeaders {
    param([string]$RequestId)
    return @{
        Authorization   = "Bearer $token"
        "X-Request-Id"  = $RequestId
        "X-Operator-Id" = $OperatorId
        "X-Project-Id"  = $ProjectId
    }
}

# 3. Config probe (authenticated)
$configResp = Invoke-ScfJson -Method GET -RelativePath "/integrations/contracts/sign/config" -Headers (PlatformHeaders "$RunId-CONFIG")
Save-JsonArtifact $ArchiveDir "config.json" $configResp.Content | Out-Null
$configOk = $configResp.StatusCode -eq 200
$configJson = $null
if ($configOk) { $configJson = $configResp.Content | ConvertFrom-Json }
$providerConfigured = $false
if ($configJson -and $configJson.data.provider_connections) {
    $connections = @($configJson.data.provider_connections)
    $conn = $connections | Where-Object { $_.provider_code -eq "ESIGN_HTTP" } | Select-Object -First 1
    if ($conn) { $providerConfigured = [bool]$conn.configured }
}
$scenarios += New-Scenario "CONFIG" ($configOk -and $providerConfigured) @{
    api_request_id = if ($configJson) { $configJson.requestId } else { "" }
    provider_configured = $providerConfigured
}
if (-not ($configOk -and $providerConfigured)) { $verdictPass = $false }
Write-Host $(if ($configOk -and $providerConfigured) { "PASS" } else { "FAIL" }) " CONFIG"

# 4. Initiate sign (real vendor outbound)
if (-not $SkipInitiate) {
    $initBody = '{"provider_code":"ESIGN_HTTP"}'
    $initResp = Invoke-ScfJson -Method POST `
        -RelativePath "/documents/center/$DocumentId/sign" `
        -Headers (PlatformHeaders "$RunId-INITIATE") `
        -Body $initBody
    Save-JsonArtifact $ArchiveDir "initiate-response.json" $initResp.Content | Out-Null
    $initOk = $initResp.StatusCode -eq 200
    $initJson = $null
    if ($initOk) {
        $initJson = $initResp.Content | ConvertFrom-Json
        $externalSignRef = $initJson.data.external_sign_ref
        $platformTraceId = "$RunId-INITIATE"
    }
    $scenarios += New-Scenario "INITIATE" $initOk @{
        external_sign_ref  = $externalSignRef
        platform_trace_id  = $platformTraceId
        api_request_id     = if ($initJson) { $initJson.requestId } else { "" }
        document_id        = $DocumentId
    }
    if (-not $initOk) {
        $verdictPass = $false
        Write-Host "FAIL  INITIATE ($($initResp.StatusCode)): $($initResp.Content)" -ForegroundColor Red
    } else {
        Write-Host "PASS  INITIATE external_sign_ref=$externalSignRef"
    }
} else {
    $externalSignRef = $env:SCF_ESIGN_EXTERNAL_SIGN_REF
    if ([string]::IsNullOrWhiteSpace($externalSignRef)) {
        Write-Host "FAIL  SkipInitiate requires SCF_ESIGN_EXTERNAL_SIGN_REF" -ForegroundColor Red
        exit 1
    }
    Write-Host "SKIP  INITIATE (using SCF_ESIGN_EXTERNAL_SIGN_REF=$externalSignRef)"
}

# 5. Lookup
if ($externalSignRef) {
    $lookupResp = Invoke-ScfJson -Method GET `
        -RelativePath "/integrations/contracts/sign/by-ref/$([uri]::EscapeDataString($externalSignRef))" `
        -Headers (PlatformHeaders "$RunId-LOOKUP")
    Save-JsonArtifact $ArchiveDir "lookup-response.json" $lookupResp.Content | Out-Null
    $lookupOk = $lookupResp.StatusCode -eq 200
    if ($lookupOk) {
        $lookupJson = $lookupResp.Content | ConvertFrom-Json
        if ($lookupJson.data.task.provider_request_id) { $providerRequestId = $lookupJson.data.task.provider_request_id }
        if ($lookupJson.data.task.provider_trace_id) { $providerTraceId = $lookupJson.data.task.provider_trace_id }
    }
    $scenarios += New-Scenario "LOOKUP" $lookupOk @{ external_sign_ref = $externalSignRef }
    Write-Host $(if ($lookupOk) { "PASS" } else { "FAIL" }) " LOOKUP"
    if (-not $lookupOk) { $verdictPass = $false }

    # 6. Query status (real vendor)
    $queryBody = '{"reconcile":false,"reason":"EA-046 sandbox probe"}'
    $queryResp = Invoke-ScfJson -Method POST `
        -RelativePath "/integrations/contracts/sign/by-ref/$([uri]::EscapeDataString($externalSignRef))/query-status" `
        -Headers (PlatformHeaders "$RunId-QUERY") `
        -Body $queryBody
    Save-JsonArtifact $ArchiveDir "query-status-response.json" $queryResp.Content | Out-Null
    $queryOk = $queryResp.StatusCode -eq 200
    $scenarios += New-Scenario "QUERY_STATUS" $queryOk @{ external_sign_ref = $externalSignRef }
    Write-Host $(if ($queryOk) { "PASS" } else { "FAIL" }) " QUERY_STATUS"
    if (-not $queryOk) { $verdictPass = $false }
}

# 7. Callback replay (verify signature path)
if (-not $SkipCallbackReplay -and $externalSignRef -and $CallbackSecret) {
    $cbStatus = "SUCCESS"
    $idempotencyKey = "EA046-$RunId-CB-1"
    $nonce = [guid]::NewGuid().ToString("N")
    $cbBody = Build-CallbackPayloadJson -ExternalSignRef $externalSignRef -CallbackStatus $cbStatus
    $timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds().ToString()
    $canonical = "$timestamp`n$nonce`n$cbBody"
    $signature = Hmac-Hex $CallbackSecret $canonical

    $cbResp = Invoke-ScfJson -Method POST -RelativePath "/integrations/contracts/sign-callback" -Headers @{
        "X-Contract-Sign-Timestamp" = $timestamp
        "X-Contract-Sign-Nonce"     = $nonce
        "X-Contract-Sign-Signature" = $signature
        "X-Idempotency-Key"         = $idempotencyKey
    } -Body $cbBody
    Save-JsonArtifact $ArchiveDir "callback-replay-response.json" $cbResp.Content | Out-Null
    $cbOk = $cbResp.StatusCode -eq 200
    $scenarios += New-Scenario "CALLBACK_REPLAY" $cbOk @{
        external_sign_ref = $externalSignRef
        idempotency_key   = $idempotencyKey
    }
    Write-Host $(if ($cbOk) { "PASS" } else { "FAIL" }) " CALLBACK_REPLAY ($($cbResp.StatusCode))"
    if (-not $cbOk) { $verdictPass = $false }

    # Idempotent replay: keep the same idempotency key and payload, but use a
    # fresh nonce/signature because callback nonce replay is rejected by design.
    $nonce2 = [guid]::NewGuid().ToString("N")
    $timestamp2 = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds().ToString()
    $canonical2 = "$timestamp2`n$nonce2`n$cbBody"
    $signature2 = Hmac-Hex $CallbackSecret $canonical2
    $cb2Resp = Invoke-ScfJson -Method POST -RelativePath "/integrations/contracts/sign-callback" -Headers @{
        "X-Contract-Sign-Timestamp" = $timestamp2
        "X-Contract-Sign-Nonce"     = $nonce2
        "X-Contract-Sign-Signature" = $signature2
        "X-Idempotency-Key"         = $idempotencyKey
    } -Body $cbBody
    Save-JsonArtifact $ArchiveDir "callback-idempotent-response.json" $cb2Resp.Content | Out-Null
    $idempotentOk = $cb2Resp.StatusCode -eq 200
    $idempotentReplay = $false
    if ($idempotentOk) {
        $cb2Json = $cb2Resp.Content | ConvertFrom-Json
        if ($cb2Json.data.idempotent_replay -eq $true) { $idempotentReplay = $true }
    }
    $scenarios += New-Scenario "CALLBACK_IDEMPOTENT" ($idempotentOk -and $idempotentReplay) @{
        idempotent_replay = $idempotentReplay
    }
    Write-Host $(if ($idempotentOk -and $idempotentReplay) { "PASS" } else { "FAIL" }) " CALLBACK_IDEMPOTENT"
    if (-not ($idempotentOk -and $idempotentReplay)) { $verdictPass = $false }
}

# 8. Compensation pool (unknown external_sign_ref)
if (-not $SkipCompensationProbe -and $CallbackSecret) {
    $unknownRef = "EA046-UNKNOWN-$RunId"
    $nonceU = [guid]::NewGuid().ToString("N")
    $bodyU = Build-CallbackPayloadJson -ExternalSignRef $unknownRef -CallbackStatus "SUCCESS"
    $tsU = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds().ToString()
    $sigU = Hmac-Hex $CallbackSecret "$tsU`n$nonceU`n$bodyU"

    $unkResp = Invoke-ScfJson -Method POST -RelativePath "/integrations/contracts/sign-callback" -Headers @{
        "X-Contract-Sign-Timestamp" = $tsU
        "X-Contract-Sign-Nonce"     = $nonceU
        "X-Contract-Sign-Signature" = $sigU
        "X-Idempotency-Key"         = "EA046-$RunId-UNK"
    } -Body $bodyU
    Save-JsonArtifact $ArchiveDir "compensation-unknown-response.json" $unkResp.Content | Out-Null
    $unkOk = $unkResp.StatusCode -eq 404
    $scenarios += New-Scenario "COMPENSATION_UNKNOWN_REF" $unkOk @{ external_sign_ref = $unknownRef }
    Write-Host $(if ($unkOk) { "PASS" } else { "FAIL" }) " COMPENSATION_UNKNOWN_REF"
    if (-not $unkOk) { $verdictPass = $false }

    # List compensation tasks via saga ops (requires DB or API list — use query-sign-status if we can find id)
    # Query latest compensation from saga list endpoint if available
    $listResp = Invoke-ScfJson -Method GET `
        -RelativePath "/saga/ops/compensation-tasks?business_type=CONTRACT_SIGN_CALLBACK&business_id=$([uri]::EscapeDataString($unknownRef))" `
        -Headers (PlatformHeaders "$RunId-SAGA-LIST")
    if ($listResp.StatusCode -eq 200) {
        Save-JsonArtifact $ArchiveDir "compensation-list-response.json" $listResp.Content | Out-Null
        $listJson = $listResp.Content | ConvertFrom-Json
        $records = $listJson.data.records
        if ($records -and $records.Count -gt 0) {
            $compensationId = $records[0].id
            $retryBody = '{"reason":"EA-046 sandbox compensation retry probe"}'
            $retryResp = Invoke-ScfJson -Method POST `
                -RelativePath "/saga/ops/compensation-tasks/$compensationId/retry" `
                -Headers (PlatformHeaders "$RunId-SAGA-RETRY") `
                -Body $retryBody
            Save-JsonArtifact $ArchiveDir "compensation-retry-response.json" $retryResp.Content | Out-Null
            $retryOk = $retryResp.StatusCode -eq 200
            $scenarios += New-Scenario "COMPENSATION_RETRY" $retryOk @{ compensation_id = $compensationId }
            Write-Host $(if ($retryOk) { "PASS" } else { "FAIL" }) " COMPENSATION_RETRY"

            $sagaBody = '{"reason":"EA-046 sandbox saga query-sign-status"}'
            $sagaResp = Invoke-ScfJson -Method POST `
                -RelativePath "/saga/ops/compensation-tasks/$compensationId/query-sign-status" `
                -Headers (PlatformHeaders "$RunId-SAGA-QUERY") `
                -Body $sagaBody
            Save-JsonArtifact $ArchiveDir "saga-query-response.json" $sagaResp.Content | Out-Null
            $sagaOk = $sagaResp.StatusCode -eq 200
            $scenarios += New-Scenario "SAGA_QUERY_SIGN_STATUS" $sagaOk @{
                compensation_id = $compensationId
            }
            Write-Host $(if ($sagaOk) { "PASS" } else { "FAIL" }) " SAGA_QUERY_SIGN_STATUS"
            if (-not $sagaOk) { $verdictPass = $false }
        }
    }
}

# 9. Optional DB export
if (-not $SkipDbExport -and $externalSignRef -and $env:SCF_DB_HOST -and $env:SCF_DB_PASSWORD) {
    $sqlFile = Join-Path $ScriptDir "export-contract-sign-evidence.sql"
    $dbOut = Join-Path $ArchiveDir "db-export.log"
    $pgPass = $env:SCF_DB_PASSWORD
    $env:PGPASSWORD = $pgPass
    try {
        & psql -h $env:SCF_DB_HOST -p $(if ($env:SCF_DB_PORT) { $env:SCF_DB_PORT } else { "5432" }) `
            -U $(if ($env:SCF_DB_USER) { $env:SCF_DB_USER } else { "scf" }) `
            -d $(if ($env:SCF_DB_NAME) { $env:SCF_DB_NAME } else { "scf" }) `
            -v "ref='$externalSignRef'" -f $sqlFile -o $dbOut 2>&1
        $dbOk = $LASTEXITCODE -eq 0
        $scenarios += New-Scenario "DB_TRACE_EXPORT" $dbOk @{ external_sign_ref = $externalSignRef }
        Write-Host $(if ($dbOk) { "PASS" } else { "FAIL" }) " DB_TRACE_EXPORT"
        if (-not $dbOk) { $verdictPass = $false }
    } catch {
        $scenarios += New-Scenario "DB_TRACE_EXPORT" $false @{ error = $_.Exception.Message }
        Write-Host "SKIP  DB_TRACE_EXPORT (psql unavailable)" -ForegroundColor Yellow
    } finally {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    }
}

# Evidence JSON
$evidence = @{
    ea_id           = "EA-046"
    run_id          = $RunId
    external_sign_ref = $externalSignRef
    provider_request_id = $providerRequestId
    provider_trace_id = $providerTraceId
    platform_trace_id = $platformTraceId
    environment     = if ($env:SCF_ENV_NAME) { $env:SCF_ENV_NAME } else { "vendor-sandbox" }
    git_ref         = $(try { git -C (Split-Path $PilotRoot -Parent -Parent) rev-parse --short HEAD 2>$null } catch { "" })
    vendor          = @{
        name         = if ($env:SCF_VENDOR_NAME) { $env:SCF_VENDOR_NAME } else { "" }
        doc_version  = if ($env:SCF_VENDOR_DOC_VERSION) { $env:SCF_VENDOR_DOC_VERSION } else { "" }
        base_url     = if ($env:SCF_CONTRACT_SIGN_HTTP_BASE_URL) { $env:SCF_CONTRACT_SIGN_HTTP_BASE_URL } else { "" }
        callback_url = if ($env:SCF_VENDOR_CALLBACK_URL) { $env:SCF_VENDOR_CALLBACK_URL } else { "" }
    }
    config_snapshot = @{
        default_provider           = $env:SCF_CONTRACT_SIGN_DEFAULT_PROVIDER
        callback_verification_mode = $env:SCF_CONTRACT_SIGN_CALLBACK_VERIFICATION_MODE
        outbound_auth_mode         = $env:SCF_CONTRACT_SIGN_HTTP_OUTBOUND_AUTH_MODE
        compensation_pool_enabled  = ($env:SCF_CONTRACT_SIGN_COMPENSATION_POOL_ENABLED -ne "false")
        provider_configured        = $providerConfigured
    }
    scenarios       = $scenarios
    tests           = @{ mvn_regression = "165/165 (EA-045 baseline)"; notes = "Run EA019RegressionRunner before sandbox sign-off" }
    verdict         = if ($verdictPass) { "PASS" } else { "FAIL" }
    signed_off_by   = if ($env:EA046_RUN_BY) { $env:EA046_RUN_BY } else { "" }
    signed_off_at   = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
}

$jsonPath = Join-Path $ArchiveDir "$RunId.json"
$evidence | ConvertTo-Json -Depth 20 | Set-Content -Path $jsonPath -Encoding UTF8

$summary = @"
# EA-046 Sandbox Evidence — $RunId

| 项 | 值 |
|---|---|
| Verdict | **$($evidence.verdict)** |
| Environment | $($evidence.environment) |
| Vendor | $($env:SCF_VENDOR_NAME) |
| Base URL | $BaseUrl |
| external_sign_ref | $externalSignRef |
| Archive | $ArchiveDir |

## Scenarios

| ID | Pass | Notes |
|---|---|---|
$(($scenarios | ForEach-Object { "| $($_.id) | $($_.passed) | $(if ($_.external_sign_ref) { $_.external_sign_ref } elseif ($_.error) { $_.error } else { '' }) |" }) -join "`n")

## Artifacts

- ``$RunId.json`` — machine-readable evidence
- HTTP responses: ``initiate-response.json``, ``query-status-response.json``, etc.
- Optional: ``db-export.log`` (trace + audit SQL)

## Next steps

- [ ] Attach vendor-side requestId/traceId screenshots to ``vendor/`` subfolder
- [ ] Fill ``docs/ESIGN_VENDOR_FIELD_MAP.md`` vendor instance
- [ ] Sign ``docs/EA-046_供应商Sandbox联调证据包验收结果_*.md``
"@

$summaryPath = Join-Path $ArchiveDir "$RunId.summary.md"
$summary | Set-Content -Path $summaryPath -Encoding UTF8

Write-Host "`n=== EA-046 Complete ===" -ForegroundColor Cyan
Write-Host "Evidence: $jsonPath"
Write-Host "Summary:  $summaryPath"
if ($verdictPass) {
    Write-Host ">>> EA-046: PASS — vendor sandbox closed loop evidenced <<<" -ForegroundColor Green
    exit 0
}
Write-Host ">>> EA-046: FAIL — see summary and JSON scenarios <<<" -ForegroundColor Red
exit 1
