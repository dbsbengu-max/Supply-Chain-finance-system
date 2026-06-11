# EA-046 local quasi-sandbox: vendor stub + backend:8081 + evidence script
# Simulates vendor sandbox when real credentials unavailable; same script path as production sandbox.
param(
    [int]$ServerPort = 8081,
    [switch]$SkipEvidence,
    [switch]$KeepProcesses
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$PilotRoot = Resolve-Path (Join-Path $ScriptDir "..")
$RepoRoot = Resolve-Path (Join-Path $PilotRoot "..\..")
$ServerDir = Join-Path $RepoRoot "backend\scf-server"
$EnvFile = Join-Path $PilotRoot ".env.esign-sandbox"

$AppId = "ea046-local-sandbox-app"
$AppSecret = "ea046-local-sandbox-secret"
$CallbackToken = "ea046-local-callback-hmac-secret-min-32-chars"

function Find-Mvn {
    if (Get-Command mvn -ErrorAction SilentlyContinue) { return "mvn" }
    $c = "$env:USERPROFILE\.m2\apache-maven-3.9.6\bin\mvn.cmd"
    if (Test-Path $c) { return $c }
    throw "Maven not found"
}

$mvn = Find-Mvn
Write-Host "=== EA-046 Local Quasi-Sandbox ===" -ForegroundColor Cyan

Write-Host "Starting vendor stub..."
$stubJob = Start-Job -ScriptBlock {
    param($ServerDir, $Mvn, $AppId, $AppSecret)
    Set-Location $ServerDir
    & $Mvn -q test-compile exec:java `
        "-Dexec.mainClass=com.scf.contract.support.HttpEsignVendorStubLauncher" `
        "-Dexec.classpathScope=test" `
        "-Dexec.args=$AppId $AppSecret" 2>&1
} -ArgumentList $ServerDir, $mvn, $AppId, $AppSecret

$stubBaseUrl = $null
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 1
    $lines = Receive-Job $stubJob -ErrorAction SilentlyContinue
    if ($lines) {
        $match = $lines | Where-Object { $_ -match '^VENDOR_STUB_BASE_URL=(.+)$' } | Select-Object -Last 1
        if ($match -match '^VENDOR_STUB_BASE_URL=(.+)$') {
            $stubBaseUrl = $Matches[1].Trim()
            break
        }
    }
}
if (-not $stubBaseUrl) {
    Write-Host "FAIL  vendor stub did not start" -ForegroundColor Red
    Stop-Job $stubJob -ErrorAction SilentlyContinue
    Remove-Job $stubJob -Force -ErrorAction SilentlyContinue
    exit 1
}
Write-Host "Vendor stub: $stubBaseUrl"

Write-Host "Seeding test document..."
$env:PGPASSWORD = "scf_dev_pass"
& psql -h 127.0.0.1 -U scf -d scf -f (Join-Path $ScriptDir "seed-ea046-sandbox-document.sql") 2>&1 | Out-Null

Write-Host "Starting backend on port $ServerPort..."
$backendJob = Start-Job -ScriptBlock {
    param($ServerDir, $Mvn, $Port, $StubUrl, $AppId, $AppSecret, $CallbackToken)
    Set-Location $ServerDir
    $env:SPRING_PROFILES_ACTIVE = "prod"
    $env:SERVER_PORT = "$Port"
    $env:SCF_JWT_SECRET = "scf-dev-jwt-secret-change-in-production-min-256-bits!!"
    $env:SCF_DB_HOST = "127.0.0.1"
    $env:SCF_DB_PORT = "5432"
    $env:SCF_DB_NAME = "scf"
    $env:SCF_DB_USER = "scf"
    $env:SCF_DB_PASSWORD = "scf_dev_pass"
    $env:SCF_BANK_CALLBACK_TOKEN = "mock-bank-callback-token"
    $env:SCF_CONTRACT_SIGN_DEFAULT_PROVIDER = "ESIGN_HTTP"
    $env:SCF_CONTRACT_SIGN_CALLBACK_VERIFICATION_MODE = "TIMESTAMP_NONCE_SIGNATURE"
    $env:SCF_CONTRACT_SIGN_CALLBACK_TOKEN = $CallbackToken
    $env:SCF_CONTRACT_SIGN_COMPENSATION_POOL_ENABLED = "true"
    $env:SCF_CONTRACT_SIGN_HTTP_PROVIDER_ENABLED = "true"
    $env:SCF_CONTRACT_SIGN_HTTP_BASE_URL = $StubUrl
    $env:SCF_CONTRACT_SIGN_HTTP_APP_ID = $AppId
    $env:SCF_CONTRACT_SIGN_HTTP_APP_SECRET = $AppSecret
    $env:SCF_CONTRACT_SIGN_HTTP_OUTBOUND_AUTH_MODE = "HMAC_SHA256"
    & $Mvn -q spring-boot:run "-Dspring-boot.run.jvmArguments=-Dserver.port=$Port" 2>&1
} -ArgumentList $ServerDir, $mvn, $ServerPort, $stubBaseUrl, $AppId, $AppSecret, $CallbackToken

$healthUrl = "http://127.0.0.1:$ServerPort/api/v1/actuator/health"
$ready = $false
for ($i = 0; $i -lt 120; $i++) {
    Start-Sleep -Seconds 2
    try {
        $h = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 3
        if ($h.status -eq "UP") { $ready = $true; break }
    } catch { }
}
if (-not $ready) {
    Write-Host "FAIL  backend not ready on $ServerPort" -ForegroundColor Red
    Stop-Job $stubJob, $backendJob -ErrorAction SilentlyContinue
    Remove-Job $stubJob, $backendJob -Force -ErrorAction SilentlyContinue
    exit 1
}
Write-Host "Backend UP: $healthUrl"

@(
    "SCF_BASE_URL=http://127.0.0.1:$ServerPort/api/v1",
    "SCF_API_HEALTH_URL=$healthUrl",
    "SCF_LOGIN_NAME=platform_admin",
    "SCF_LOGIN_PASSWORD=Admin@123",
    "SCF_OPERATOR_ID=OP001",
    "SCF_PROJECT_ID=PJ001",
    "SCF_ESIGN_DOCUMENT_ID=DOC_EA040_SIGN_OK",
    "SCF_ENV_NAME=local-quasi-sandbox",
    "SCF_VENDOR_NAME=HttpEsignVendorStub",
    "SCF_VENDOR_DOC_VERSION=EA-046-local",
    "SCF_CONTRACT_SIGN_DEFAULT_PROVIDER=ESIGN_HTTP",
    "SCF_CONTRACT_SIGN_CALLBACK_TOKEN=$CallbackToken",
    "SCF_CONTRACT_SIGN_CALLBACK_VERIFICATION_MODE=TIMESTAMP_NONCE_SIGNATURE",
    "SCF_CONTRACT_SIGN_COMPENSATION_POOL_ENABLED=true",
    "SCF_CONTRACT_SIGN_HTTP_PROVIDER_ENABLED=true",
    "SCF_CONTRACT_SIGN_HTTP_BASE_URL=$stubBaseUrl",
    "SCF_CONTRACT_SIGN_HTTP_APP_ID=$AppId",
    "SCF_CONTRACT_SIGN_HTTP_APP_SECRET=$AppSecret",
    "SCF_CONTRACT_SIGN_HTTP_OUTBOUND_AUTH_MODE=HMAC_SHA256",
    "SCF_DB_HOST=127.0.0.1",
    "SCF_DB_PORT=5432",
    "SCF_DB_NAME=scf",
    "SCF_DB_USER=scf",
    "SCF_DB_PASSWORD=scf_dev_pass",
    "EA046_RUN_BY=local-sandbox-runner"
) | Set-Content -Path $EnvFile -Encoding UTF8

$exitCode = 0
if (-not $SkipEvidence) {
    & (Join-Path $ScriptDir "run-ea046-sandbox-evidence.ps1") -EnvFile $EnvFile
    $exitCode = $LASTEXITCODE
}

if (-not $KeepProcesses) {
    Stop-Job $stubJob, $backendJob -ErrorAction SilentlyContinue
    Remove-Job $stubJob, $backendJob -Force -ErrorAction SilentlyContinue
}

exit $exitCode
