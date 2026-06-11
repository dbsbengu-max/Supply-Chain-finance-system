param(
  [string]$BackendDir = "..\..\backend\scf-server",
  [string]$JavaExe = "D:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe",
  [string]$BaseUrl = "http://127.0.0.1:5173"
)

$ErrorActionPreference = "Stop"

$frontendDir = Resolve-Path (Join-Path $PSScriptRoot "..")
$backendPath = Resolve-Path (Join-Path $frontendDir $BackendDir)
$backendOut = Join-Path $backendPath "target\ea050-smoke-backend.out.log"
$backendErr = Join-Path $backendPath "target\ea050-smoke-backend.err.log"
$viteOut = Join-Path $frontendDir "ea050-vite.out.log"
$viteErr = Join-Path $frontendDir "ea050-vite.err.log"

function Wait-HttpOk {
  param(
    [string]$Uri,
    [string]$Pattern,
    [int]$Seconds
  )
  for ($i = 0; $i -lt $Seconds; $i++) {
    Start-Sleep -Seconds 1
    try {
      $content = (Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 3).Content
      if (-not $Pattern -or $content -match $Pattern) {
        return $true
      }
    } catch {
      # Retry until timeout.
    }
  }
  return $false
}

if (-not (Test-Path -LiteralPath $JavaExe)) {
  throw "Java executable not found: $JavaExe"
}

$backend = $null
$frontend = $null

try {
  $backendAlreadyUp = Wait-HttpOk -Uri "http://localhost:8080/api/v1/actuator/health" -Pattern "UP" -Seconds 1
  if (-not $backendAlreadyUp) {
    $backend = Start-Process `
      -FilePath $JavaExe `
      -ArgumentList @("-cp", "target/classes;target/test-classes;target/cp-jars/*", "com.scf.ScfApplication", "--spring.profiles.active=test") `
      -WorkingDirectory $backendPath `
      -WindowStyle Hidden `
      -RedirectStandardOutput $backendOut `
      -RedirectStandardError $backendErr `
      -PassThru

    if (-not (Wait-HttpOk -Uri "http://localhost:8080/api/v1/actuator/health" -Pattern "UP" -Seconds 45)) {
      Write-Host "BACKEND_NOT_READY"
      if (Test-Path -LiteralPath $backendOut) { Get-Content -LiteralPath $backendOut -Tail 80 }
      if (Test-Path -LiteralPath $backendErr) { Get-Content -LiteralPath $backendErr -Tail 80 }
      exit 2
    }
  } else {
    Write-Host "Reusing existing backend on http://localhost:8080"
  }

  $frontend = Start-Process `
    -FilePath "npm.cmd" `
    -ArgumentList @("run", "dev", "--", "--host", "127.0.0.1") `
    -WorkingDirectory $frontendDir `
    -WindowStyle Hidden `
    -RedirectStandardOutput $viteOut `
    -RedirectStandardError $viteErr `
    -PassThru

  if (-not (Wait-HttpOk -Uri "$BaseUrl/login" -Pattern "" -Seconds 30)) {
    Write-Host "FRONTEND_NOT_READY"
    if (Test-Path -LiteralPath $viteOut) { Get-Content -LiteralPath $viteOut -Tail 80 }
    if (Test-Path -LiteralPath $viteErr) { Get-Content -LiteralPath $viteErr -Tail 80 }
    exit 3
  }

  $env:SMOKE_SKIP_WEBSERVER = "1"
  $env:SMOKE_BASE_URL = $BaseUrl
  npx playwright test tests/smoke/pilot-routes.spec.ts --config=playwright.config.ts --reporter=list
  exit $LASTEXITCODE
} finally {
  if ($frontend -and -not $frontend.HasExited) {
    Stop-Process -Id $frontend.Id -Force
  }
  if ($backend -and -not $backend.HasExited) {
    Stop-Process -Id $backend.Id -Force
  }
}
