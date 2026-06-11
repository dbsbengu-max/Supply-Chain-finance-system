param(
    [Parameter(Mandatory = $true)]
    [string]$ExternalSignRef,
    [string]$CallbackStatus = "SUCCESS",
    [string]$ProviderCode = "ESIGN_HTTP",
    [string]$BaseUrl = $env:SCF_BASE_URL,
    [string]$CallbackSecret = $env:SCF_CONTRACT_SIGN_CALLBACK_TOKEN,
    [string]$IdempotencyKey = "",
    [string]$Nonce = "",
    [string]$SignedAt = "2026-06-01T10:00:00Z"
)

$ErrorActionPreference = "Stop"

function Build-CallbackPayloadJson {
    param(
        [string]$ExternalSignRef,
        [string]$CallbackStatus,
        [string]$SignedAt = "2026-06-01T10:00:00Z",
        [string]$ProviderCode = "ESIGN_HTTP",
        [AllowNull()][string]$FailureReason = $null
    )
    if ($null -eq $FailureReason) {
        return "{`"external_sign_ref`":`"$ExternalSignRef`",`"callback_status`":`"$CallbackStatus`",`"signed_at`":`"$SignedAt`",`"failure_reason`":null,`"provider_code`":`"$ProviderCode`"}"
    }
    return "{`"external_sign_ref`":`"$ExternalSignRef`",`"callback_status`":`"$CallbackStatus`",`"signed_at`":`"$SignedAt`",`"failure_reason`":`"$FailureReason`",`"provider_code`":`"$ProviderCode`"}"
}

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
    $BaseUrl = "http://127.0.0.1:8080/api/v1"
} else {
    $BaseUrl = $BaseUrl.TrimEnd("/")
    if ($BaseUrl -notmatch "/api/v1$") { $BaseUrl = "$BaseUrl/api/v1" }
}
if ([string]::IsNullOrWhiteSpace($CallbackSecret)) {
    $CallbackSecret = "mock-contract-sign-callback-token"
}
if ([string]::IsNullOrWhiteSpace($IdempotencyKey)) {
    $IdempotencyKey = "REPLAY-$ExternalSignRef-$CallbackStatus"
}
if ([string]::IsNullOrWhiteSpace($Nonce)) {
    $Nonce = [guid]::NewGuid().ToString("N")
}

$body = Build-CallbackPayloadJson -ExternalSignRef $ExternalSignRef -CallbackStatus $CallbackStatus -SignedAt $SignedAt -ProviderCode $ProviderCode
$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds().ToString()
$canonical = "$timestamp`n$Nonce`n$body"

$hmac = New-Object System.Security.Cryptography.HMACSHA256
$hmac.Key = [Text.Encoding]::UTF8.GetBytes($CallbackSecret)
$signatureBytes = $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($canonical))
$signature = -join ($signatureBytes | ForEach-Object { $_.ToString("x2") })

$uri = "$BaseUrl/integrations/contracts/sign-callback"
Write-Host "POST $uri"
Write-Host "Body: $body"

$response = Invoke-WebRequest -Method POST -Uri $uri `
    -ContentType "application/json" `
    -Headers @{
        "X-Contract-Sign-Timestamp" = $timestamp
        "X-Contract-Sign-Nonce"     = $Nonce
        "X-Contract-Sign-Signature" = $signature
        "X-Idempotency-Key"         = $IdempotencyKey
    } `
    -Body $body

Write-Host "Status: $($response.StatusCode)"
Write-Host $response.Content
