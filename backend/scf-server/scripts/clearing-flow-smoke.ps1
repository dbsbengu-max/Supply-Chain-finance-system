$ErrorActionPreference = "Stop"
$Base = "http://localhost:8080/api/v1"
$Operator = "OP001"
$Project = "PJ001"
$FinanceId = "FIN_CLEAR_OK"
$RepayAccount = "ACC_REPAY_001"

function New-Headers($token) {
    return @{
        Authorization  = "Bearer $token"
        "X-Operator-Id" = $Operator
        "X-Project-Id"  = $Project
        "X-Request-Id"  = [guid]::NewGuid().ToString()
        "Content-Type"  = "application/json"
    }
}

function Login($loginName, $password) {
    $body = @{ login_name = $loginName; password = $password } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$Base/auth/login" -Method Post -Body $body -ContentType "application/json" -Headers @{ "X-Request-Id" = [guid]::NewGuid().ToString() }
    if (-not $res.success) { throw "Login failed: $($res.message)" }
    return $res.data
}

function Switch-Identity($token, $identityId) {
    $res = Invoke-RestMethod -Uri "$Base/auth/switch-identity" -Method Post -Body (@{ identity_id = $identityId } | ConvertTo-Json) -Headers (New-Headers $token)
    if (-not $res.success) { throw "Switch identity failed: $($res.message)" }
    return $res.data.accessToken
}

function Invoke-Api($method, $path, $token, $body = $null, $extraHeaders = @{}) {
    $headers = New-Headers $token
    foreach ($k in $extraHeaders.Keys) { $headers[$k] = $extraHeaders[$k] }
    $params = @{ Uri = "$Base$path"; Method = $method; Headers = $headers }
    if ($body) { $params.Body = ($body | ConvertTo-Json -Depth 6) }
    try {
        $res = Invoke-RestMethod @params
        return @{ ok = $true; status = 200; body = $res }
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
            $text = $reader.ReadToEnd()
            $json = $text | ConvertFrom-Json
            return @{ ok = $false; status = [int]$resp.StatusCode; body = $json }
        }
        throw
    }
}

Write-Host "=== funding_user clearing E2E (API) ===" -ForegroundColor Cyan
$session = Login "funding_user" "Fund@123"
$token = $session.accessToken
$pjIdentity = ($session.identities | Where-Object { $_.projectId -eq $Project } | Select-Object -First 1)
if (-not $pjIdentity) { throw "No PJ001 identity for funding_user" }
$token = Switch-Identity $token $pjIdentity.identityId
Write-Host "LOGIN + SWITCH OK project=$Project enterprise=$($pjIdentity.enterpriseId)"

$summary = Invoke-Api Get "/accounts/summary" $token
if (-not $summary.ok) { throw "Account summary failed: $($summary.body | ConvertTo-Json -Compress)" }
Write-Host "SUMMARY OK accounts=$($summary.body.data.Count)"

$flowNo = "BROWSER-E2E-$(Get-Date -Format 'yyyyMMddHHmmss')"
$import = Invoke-Api Post "/accounts/bank-flows/import" $token @{
    flows = @(
        @{
            account_id = $RepayAccount
            external_flow_no = $flowNo
            amount = "120000.00"
            currency = "CNY"
            counterparty_name = "E2E Test"
            flow_time = (Get-Date).ToUniversalTime().ToString("o")
        }
    )
}
if (-not $import.ok) { throw "Import failed: $($import.body | ConvertTo-Json -Compress)" }
$flowId = $import.body.data[0].id
Write-Host "IMPORT OK flow=$flowId amount=120000"

$match = Invoke-Api Post "/accounts/bank-flows/$flowId/match" $token @{ finance_id = $FinanceId }
if (-not $match.ok) { throw "Match failed: $($match.body | ConvertTo-Json -Compress)" }
Write-Host "MATCH OK finance=$FinanceId"

$entry = Invoke-Api Get "/accounts/clearing/entry?finance_id=$FinanceId" $token
if (-not $entry.ok) { throw "Entry failed: $($entry.body | ConvertTo-Json -Compress)" }
$ruleId = $entry.body.data.clearing_rules[0].id
Write-Host "ENTRY OK outstanding=$($entry.body.data.outstanding_principal) rule=$ruleId"

$calc = Invoke-Api Post "/accounts/clearing/calculate" $token @{
    finance_id = $FinanceId
    bank_flow_id = $flowId
    clearing_rule_id = $ruleId
}
if (-not $calc.ok) { throw "Calculate failed: $($calc.body | ConvertTo-Json -Compress)" }
Write-Host "CALCULATE OK repayment=$($calc.body.data.repayment_amount) principal=$($calc.body.data.allocation.principal_amount)"

$idempotencyKey = [guid]::NewGuid().ToString()
$exec = Invoke-Api Post "/accounts/clearing/execute" $token @{
    finance_id = $FinanceId
    bank_flow_id = $flowId
    clearing_rule_id = $ruleId
} @{
    "X-Idempotency-Key" = $idempotencyKey
    "X-Secondary-Auth-Token" = "MOCK-APPROVED"
}
if (-not $exec.ok) { throw "Execute failed: $($exec.body | ConvertTo-Json -Compress)" }
Write-Host "EXECUTE OK repayment_id=$($exec.body.data.repayment_id) finance_status=$($exec.body.data.finance_status)"

$summaryAfter = Invoke-Api Get "/accounts/summary" $token
Write-Host "SUMMARY AFTER OK first_balance=$($summaryAfter.body.data[0].balance)"

Write-Host "=== ALL STEPS PASSED ===" -ForegroundColor Green
