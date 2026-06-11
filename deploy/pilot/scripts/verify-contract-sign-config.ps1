param(
    [string]$EnvFile,
    [string]$BaseUrl
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$PilotRoot = Resolve-Path (Join-Path $ScriptDir "..")
if (-not $EnvFile) { $EnvFile = Join-Path $PilotRoot ".env.esign-sandbox" }

if (Test-Path $EnvFile) {
    Get-Content $EnvFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) { return }
        $idx = $line.IndexOf("=")
        if ($idx -lt 1) { return }
        Set-Item -Path "Env:$($line.Substring(0,$idx).Trim())" -Value $line.Substring($idx+1).Trim()
    }
}

if (-not $BaseUrl) { $BaseUrl = $env:SCF_BASE_URL }
if ([string]::IsNullOrWhiteSpace($BaseUrl)) { $BaseUrl = "http://127.0.0.1:8080/api/v1" }
$BaseUrl = $BaseUrl.TrimEnd("/")
if ($BaseUrl -notmatch "/api/v1$") { $BaseUrl = "$BaseUrl/api/v1" }

$loginName = if ($env:SCF_LOGIN_NAME) { $env:SCF_LOGIN_NAME } else { "platform_admin" }
$loginPassword = $env:SCF_LOGIN_PASSWORD
$operatorId = if ($env:SCF_OPERATOR_ID) { $env:SCF_OPERATOR_ID } else { "OP001" }
$projectId = if ($env:SCF_PROJECT_ID) { $env:SCF_PROJECT_ID } else { "PJ001" }

if (-not $loginPassword) {
    Write-Host "FAIL  SCF_LOGIN_PASSWORD not set in env file" -ForegroundColor Red
    exit 1
}

Write-Host "POST $BaseUrl/auth/login"
$loginBody = (@{ login_name = $loginName; password = $loginPassword } | ConvertTo-Json -Compress)
$login = Invoke-RestMethod -Method POST -Uri "$BaseUrl/auth/login" -ContentType "application/json" -Body $loginBody -Headers @{ "X-Request-Id" = "EA046-CONFIG-LOGIN" }
$token = $login.data.accessToken
if (-not $token) {
    Write-Host "FAIL  login did not return accessToken" -ForegroundColor Red
    exit 1
}

$headers = @{
    Authorization   = "Bearer $token"
    "X-Operator-Id" = $operatorId
    "X-Project-Id"  = $projectId
    "X-Request-Id"  = "EA046-CONFIG-PROBE"
}

Write-Host "GET $BaseUrl/integrations/contracts/sign/config"
$resp = Invoke-RestMethod -Uri "$BaseUrl/integrations/contracts/sign/config" -Headers $headers
$data = $resp.data
Write-Host "default_provider: $($data.default_provider)"
Write-Host "callback_verification_mode: $($data.callback_verification_mode)"
Write-Host "compensation_pool_enabled: $($data.compensation_pool_enabled)"
if ($data.production_rollout) {
    $r = $data.production_rollout
    Write-Host "production_rollout.mode: $($r.mode)"
    Write-Host "production_rollout.effective_provider: $($r.effective_provider_for_context)"
    Write-Host "production_rollout.routed_to_production: $($r.routed_to_production)"
}
foreach ($c in $data.provider_connections) {
    Write-Host "provider $($c.provider_code): configured=$($c.configured) outbound_auth_mode=$($c.outbound_auth_mode)"
    if (-not $c.configured) {
        Write-Host "FAIL  ESIGN_HTTP not configured" -ForegroundColor Red
        exit 1
    }
}
Write-Host "PASS  contract sign config ready for sandbox" -ForegroundColor Green
exit 0
