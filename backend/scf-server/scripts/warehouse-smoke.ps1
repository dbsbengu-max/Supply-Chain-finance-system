# Warehouse inventory rights MVP smoke test
$ErrorActionPreference = "Stop"
$BaseUrl = "http://localhost:8080/api/v1"
$OperatorId = "OP001"
$ProjectId = "PJ001"

function Login($user, $pass) {
    $body = @{ login_name = $user; password = $pass } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -Body $body -ContentType "application/json"
    if (-not $r.success) { throw $r.message }
    return $r.data.accessToken
}

function Headers($token) {
    return @{
        Authorization = "Bearer $token"
        "X-Operator-Id" = $OperatorId
        "X-Project-Id" = $ProjectId
        "X-Request-Id" = [guid]::NewGuid().ToString()
    }
}

Write-Host "=== warehouse smoke ===" -ForegroundColor Cyan
$token = Login "platform_admin" "Admin@123"
$h = Headers $token

$meta = Invoke-RestMethod -Uri "$BaseUrl/warehouse/meta" -Headers $h
Write-Host "meta right_statuses: $($meta.data.right_statuses.Count)"

$invList = Invoke-RestMethod -Uri "$BaseUrl/warehouse/inventories?page_no=1&page_size=20" -Headers $h
Write-Host "inventories: $($invList.data.records.Count)"

$pledged = $invList.data.records | Where-Object { $_.id -eq "INV001" }
if ($pledged) {
    $qtyBody = @{ quantity = 1 } | ConvertTo-Json
    try {
        Invoke-RestMethod -Uri "$BaseUrl/warehouse/outbounds" -Method Post -Headers $h -Body (@{
            inventory_id = "INV001"; quantity = 1
        } | ConvertTo-Json) -ContentType "application/json"
        throw "Expected 409 for pledged outbound"
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -ne 409) { throw $_ }
        Write-Host "OK pledged outbound returns 409" -ForegroundColor Green
    }
}

$batch = "SMOKE-" + [DateTime]::UtcNow.ToString("yyyyMMddHHmmss")
$inbound = Invoke-RestMethod -Uri "$BaseUrl/warehouse/inbounds" -Method Post -Headers $h -Body (@{
    warehouse_id = "WH001"
    sku_id = "SKU_GARLIC_A"
    batch_no = $batch
    owner_id = "ENT_MEMBER_001"
    location_code = "T-01"
    quantity = 10
    valuation_amount = 85000
    currency = "CNY"
} | ConvertTo-Json) -ContentType "application/json"
$invId = $inbound.data.inventory_id
Write-Host "inbound inventory: $invId"

Invoke-RestMethod -Uri "$BaseUrl/warehouse/inventories/$invId/freeze" -Method Post -Headers $h -Body (@{ quantity = 5 } | ConvertTo-Json) -ContentType "application/json" | Out-Null
Write-Host "freeze OK"

Invoke-RestMethod -Uri "$BaseUrl/warehouse/inventories/$invId/pledge" -Method Post -Headers $h -Body (@{ quantity = 5 } | ConvertTo-Json) -ContentType "application/json" | Out-Null
Write-Host "pledge OK"

$rel = Invoke-RestMethod -Uri "$BaseUrl/warehouse/inventories/$invId/release" -Method Post -Headers $h -Body (@{ quantity = 5 } | ConvertTo-Json) -ContentType "application/json"
Invoke-RestMethod -Uri "$BaseUrl/warehouse/release-requests/$($rel.data.id)/approve" -Method Post -Headers $h | Out-Null
Write-Host "release OK"

$whToken = Login "warehouse_user" "Wh@123"
$whH = Headers $whToken
try {
    Invoke-RestMethod -Uri "$BaseUrl/warehouse/inventories?page_no=1" -Headers (@{
        Authorization = "Bearer $whToken"
        "X-Operator-Id" = "OP001"
        "X-Project-Id" = "PJ999"
    })
    throw "Expected 404 for wrong project"
} catch {
    if ($_.Exception.Response.StatusCode.value__ -notin 403,404) { throw $_ }
    Write-Host "OK cross-project blocked" -ForegroundColor Green
}

Write-Host "=== smoke passed ===" -ForegroundColor Green
