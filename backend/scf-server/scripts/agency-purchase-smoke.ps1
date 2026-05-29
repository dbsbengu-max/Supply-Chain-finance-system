$ErrorActionPreference = "Stop"
$Base = "http://localhost:8080/api/v1"
$Operator = "OP001"
$Project = "PJ001"

function New-Headers($token) {
    return @{
        Authorization    = "Bearer $token"
        "X-Operator-Id"  = $Operator
        "X-Project-Id"   = $Project
        "X-Request-Id"   = [guid]::NewGuid().ToString()
        "Content-Type"   = "application/json"
    }
}

function Login($loginName, $password) {
    $body = @{ login_name = $loginName; password = $password } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$Base/auth/login" -Method Post -Body $body -ContentType "application/json" -Headers @{ "X-Request-Id" = [guid]::NewGuid().ToString() }
    if (-not $res.success) { throw "Login failed for $loginName : $($res.message)" }
    return $res.data.accessToken
}

function Invoke-Api($method, $path, $token, $body = $null) {
    $params = @{
        Uri     = "$Base$path"
        Method  = $method
        Headers = (New-Headers $token)
    }
    if ($body) { $params.Body = ($body | ConvertTo-Json -Depth 5) }
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

$agencyBody = @{
    order_mode       = "STOCK_ORDER"
    fund_source      = "SELF_FUNDED"
    pickup_type      = "PAYMENT_PICKUP"
    customer_id      = "ENT_MEMBER_001"
    trade_company_id = "ENT_TRADE_001"
    order_id         = "ORD001"
    currency         = "CNY"
    total_amount     = "100000.00"
    remark           = "smoke-test"
}

Write-Host "=== platform_admin full flow ===" -ForegroundColor Cyan
$adminToken = Login "platform_admin" "Admin@123"
$create = Invoke-Api Post "/agency-purchase/applications" $adminToken $agencyBody
if (-not $create.ok) { throw "Create failed: $($create.body | ConvertTo-Json -Compress)" }
$id = $create.body.data.id
Write-Host "CREATE OK id=$id status=$($create.body.data.application_status)"

$updateBody = $agencyBody.Clone()
$updateBody.total_amount = "120000.00"
$update = Invoke-Api Put "/agency-purchase/applications/$id" $adminToken $updateBody
if (-not $update.ok) { throw "Update failed" }
Write-Host "UPDATE OK amount=$($update.body.data.total_amount)"

$submit = Invoke-Api Post "/agency-purchase/applications/$id/submit" $adminToken
if (-not $submit.ok) { throw "Submit failed: $($submit.body | ConvertTo-Json -Compress)" }
Write-Host "SUBMIT OK status=$($submit.body.data.application_status) bpm=$($submit.body.data.bpm_instance_id)"

$detail = Invoke-Api Get "/agency-purchase/applications/$id" $adminToken
if (-not $detail.body.data.bpm_instance_id) { throw "Missing bpm_instance_id on detail" }
Write-Host "DETAIL OK bpm_instance_id=$($detail.body.data.bpm_instance_id)"

$cancel = Invoke-Api Post "/agency-purchase/applications/$id/cancel" $adminToken
if (-not $cancel.ok) { throw "Cancel failed" }
Write-Host "CANCEL OK status=$($cancel.body.data.application_status)"

Write-Host "=== funding_user view-only ===" -ForegroundColor Cyan
$fundToken = Login "funding_user" "Fund@123"
$list = Invoke-Api Get "/agency-purchase/applications?page_no=1&page_size=10" $fundToken
if (-not $list.ok) { throw "Funding list should succeed" }
Write-Host "FUNDING LIST OK count=$($list.body.data.records.Count)"

$fundCreate = Invoke-Api Post "/agency-purchase/applications" $fundToken $agencyBody
if ($fundCreate.ok -or $fundCreate.status -ne 403) { throw "Funding create expected 403 got $($fundCreate.status)" }
Write-Host "FUNDING CREATE 403 OK code=$($fundCreate.body.code)"

Write-Host "=== warehouse_user denied ===" -ForegroundColor Cyan
$whToken = Login "warehouse_user" "Wh@123"
$whList = Invoke-Api Get "/agency-purchase/applications?page_no=1&page_size=10" $whToken
if ($whList.ok -or $whList.status -ne 403) { throw "Warehouse list expected 403 got $($whList.status)" }
Write-Host "WAREHOUSE LIST 403 OK code=$($whList.body.code)"

Write-Host "=== member_user scoped create ===" -ForegroundColor Cyan
$memberToken = Login "member_user" "Member@123"
$memberCreate = Invoke-Api Post "/agency-purchase/applications" $memberToken $agencyBody
if (-not $memberCreate.ok) { throw "Member create own failed" }
Write-Host "MEMBER CREATE OWN OK id=$($memberCreate.body.data.id)"

$badBody = $agencyBody.Clone()
$badBody.customer_id = "ENT_CORE_001"
$memberBad = Invoke-Api Post "/agency-purchase/applications" $memberToken $badBody
if ($memberBad.ok -or $memberBad.status -ne 403) { throw "Member create other expected 403" }
Write-Host "MEMBER CREATE OTHER 403 OK code=$($memberBad.body.code)"

Write-Host "=== ALL SMOKE TESTS PASSED ===" -ForegroundColor Green
