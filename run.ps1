param(
    [int]$BackendPort = 8080,
    [int]$FrontendPort = 5173,
    [switch]$SkipBuild,
    [switch]$SkipInstall,
    [switch]$OpenBrowser
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendDir = Join-Path $RepoRoot "backend\scf-server"
$FrontendDir = Join-Path $RepoRoot "frontend\scf-web"
$Jar = Join-Path $BackendDir "target\scf-server-1.1.0-SNAPSHOT.jar"
$LogDir = Join-Path $RepoRoot ".runtime-logs"
$BackendOut = Join-Path $LogDir "backend.out.log"
$BackendErr = Join-Path $LogDir "backend.err.log"
$FrontendOut = Join-Path $LogDir "frontend.out.log"
$FrontendErr = Join-Path $LogDir "frontend.err.log"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-Ok {
    param([string]$Message)
    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Write-Warn {
    param([string]$Message)
    Write-Host "[WARN] $Message" -ForegroundColor Yellow
}

function Fail-WithLog {
    param(
        [string]$Message,
        [string[]]$Logs
    )

    Write-Host "[FAIL] $Message" -ForegroundColor Red
    foreach ($log in $Logs) {
        if (Test-Path -LiteralPath $log) {
            Write-Host ""
            Write-Host "--- Last lines: $log ---" -ForegroundColor DarkGray
            Get-Content -LiteralPath $log -Tail 80
        }
    }
    exit 1
}

function Find-Java {
    $candidates = @(
        "$env:JAVA_HOME\bin\java.exe",
        "D:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe",
        "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe"
    )

    foreach ($path in $candidates) {
        if (-not $path -or -not (Test-Path -LiteralPath $path)) { continue }
        $version = & cmd.exe /c "`"$path`" -version 2>&1"
        if ($LASTEXITCODE -eq 0 -or ($version -match "version")) { return $path }
    }

    $cmd = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    throw "Java 17 not found. Install JDK 17 or set JAVA_HOME."
}

function Find-Maven {
    $cmd = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    $cmd = Get-Command mvn -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    $fallback = Join-Path $env:USERPROFILE ".m2\apache-maven-3.9.6\bin\mvn.cmd"
    if (Test-Path -LiteralPath $fallback) { return $fallback }

    throw "Maven not found. Install Maven or place it at $fallback."
}

function Test-TcpPort {
    param(
        [string]$HostName,
        [int]$Port
    )

    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $task = $client.ConnectAsync($HostName, $Port)
        $ready = $task.Wait(1000)
        $connected = $ready -and $client.Connected
        $client.Close()
        return $connected
    } catch {
        return $false
    }
}

function Wait-TcpPort {
    param(
        [string]$HostName,
        [int]$Port,
        [int]$Seconds
    )

    for ($i = 0; $i -lt $Seconds; $i++) {
        if (Test-TcpPort -HostName $HostName -Port $Port) { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Test-HttpPattern {
    param(
        [string]$Uri,
        [string]$Pattern = ""
    )

    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 3
        if (-not $Pattern) { return $true }
        return $response.Content -match $Pattern
    } catch {
        return $false
    }
}

function Wait-HttpPattern {
    param(
        [string]$Uri,
        [string]$Pattern = "",
        [int]$Seconds
    )

    for ($i = 0; $i -lt $Seconds; $i++) {
        if (Test-HttpPattern -Uri $Uri -Pattern $Pattern) { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Start-Middleware {
    Write-Step "Starting middleware"

    if (Test-TcpPort -HostName "127.0.0.1" -Port 5432) {
        Write-Ok "PostgreSQL is already listening on 127.0.0.1:5432"
        return
    }

    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $docker) {
        throw "Docker is not available and PostgreSQL is not listening on 5432."
    }

    Push-Location $RepoRoot
    try {
        & $docker.Source compose up -d
        if ($LASTEXITCODE -ne 0) { throw "docker compose up -d failed." }
    } finally {
        Pop-Location
    }

    if (-not (Wait-TcpPort -HostName "127.0.0.1" -Port 5432 -Seconds 45)) {
        throw "PostgreSQL did not become ready on 5432."
    }

    Write-Ok "PostgreSQL is ready on 127.0.0.1:5432"
}

function Set-BackendEnv {
    $env:SPRING_PROFILES_ACTIVE = "prod"
    $env:SERVER_PORT = "$BackendPort"
    $env:SCF_DB_HOST = "127.0.0.1"
    $env:SCF_DB_PORT = "5432"
    $env:SCF_DB_NAME = "scf"
    $env:SCF_DB_USER = "scf"
    $env:SCF_DB_PASSWORD = "scf_dev_pass"
    $env:SCF_JWT_SECRET = "scf-dev-jwt-secret-change-in-production-min-256-bits!!"
    $env:SCF_BANK_CALLBACK_TOKEN = "mock-bank-callback-token"
    $env:SCF_CONTRACT_SIGN_CALLBACK_TOKEN = "local-dev-callback-token-min-32-chars-long"
    $env:SCF_CONTRACT_SIGN_DEFAULT_PROVIDER = "MOCK"
    $env:SCF_CONTRACT_SIGN_COMPENSATION_POOL_ENABLED = "true"
}

function Start-Backend {
    Write-Step "Starting backend"

    $healthUrl = "http://127.0.0.1:$BackendPort/api/v1/actuator/health"
    if (Test-HttpPattern -Uri $healthUrl -Pattern "UP") {
        Write-Ok "Backend is already UP: $healthUrl"
        return
    }

    $java = Find-Java
    $maven = Find-Maven
    Write-Host "Java: $java"
    Write-Host "Maven: $maven"

    if (-not $SkipBuild) {
        Write-Host "Building backend jar..."
        Push-Location $BackendDir
        try {
            & $maven -DskipTests clean package
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        } finally {
            Pop-Location
        }
    }

    if (-not (Test-Path -LiteralPath $Jar)) {
        throw "Backend jar not found: $Jar. Run without -SkipBuild first."
    }

    Set-BackendEnv
    if (Test-Path -LiteralPath $BackendOut) { Remove-Item -LiteralPath $BackendOut -Force }
    if (Test-Path -LiteralPath $BackendErr) { Remove-Item -LiteralPath $BackendErr -Force }

    $process = Start-Process `
        -FilePath $java `
        -ArgumentList @("-jar", "`"$Jar`"") `
        -WorkingDirectory $BackendDir `
        -WindowStyle Hidden `
        -RedirectStandardOutput $BackendOut `
        -RedirectStandardError $BackendErr `
        -PassThru

    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2
        if (Test-HttpPattern -Uri $healthUrl -Pattern "UP") {
            Write-Ok "Backend UP, PID=$($process.Id), $healthUrl"
            return
        }
        if ($process.HasExited) {
            Fail-WithLog -Message "Backend exited before health check passed." -Logs @($BackendOut, $BackendErr)
        }
    }

    Fail-WithLog -Message "Backend health check timed out: $healthUrl" -Logs @($BackendOut, $BackendErr)
}

function Start-Frontend {
    Write-Step "Starting frontend"

    $frontendUrl = "http://127.0.0.1:$FrontendPort"
    if (Test-HttpPattern -Uri $frontendUrl) {
        Write-Ok "Frontend is already listening: $frontendUrl"
        return
    }

    $npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
    if (-not $npm) {
        $npm = Get-Command npm -ErrorAction SilentlyContinue
    }
    if (-not $npm) {
        throw "npm not found. Install Node.js first."
    }

    $nodeModules = Join-Path $FrontendDir "node_modules"
    if (-not $SkipInstall -and -not (Test-Path -LiteralPath $nodeModules)) {
        Write-Host "Installing frontend dependencies..."
        Push-Location $FrontendDir
        try {
            & $npm.Source install
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        } finally {
            Pop-Location
        }
    } elseif (-not (Test-Path -LiteralPath $nodeModules)) {
        throw "node_modules not found. Run without -SkipInstall first."
    }

    if (Test-Path -LiteralPath $FrontendOut) { Remove-Item -LiteralPath $FrontendOut -Force }
    if (Test-Path -LiteralPath $FrontendErr) { Remove-Item -LiteralPath $FrontendErr -Force }

    $process = Start-Process `
        -FilePath $npm.Source `
        -ArgumentList @("run", "dev", "--", "--host", "127.0.0.1", "--port", "$FrontendPort") `
        -WorkingDirectory $FrontendDir `
        -WindowStyle Hidden `
        -RedirectStandardOutput $FrontendOut `
        -RedirectStandardError $FrontendErr `
        -PassThru

    for ($i = 0; $i -lt 45; $i++) {
        Start-Sleep -Seconds 1
        if (Test-HttpPattern -Uri $frontendUrl) {
            Write-Ok "Frontend UP, PID=$($process.Id), $frontendUrl"
            return
        }
        if ($process.HasExited) {
            Fail-WithLog -Message "Frontend exited before it became ready." -Logs @($FrontendOut, $FrontendErr)
        }
    }

    Fail-WithLog -Message "Frontend readiness timed out: $frontendUrl" -Logs @($FrontendOut, $FrontendErr)
}

New-Item -ItemType Directory -Path $LogDir -Force | Out-Null

Write-Host "SCF local runtime" -ForegroundColor Cyan
Write-Host "Repo: $RepoRoot"
Write-Host "Logs: $LogDir"

Start-Middleware
Start-Backend
Start-Frontend

Write-Host ""
Write-Host "All services are ready." -ForegroundColor Green
Write-Host "Frontend: http://127.0.0.1:$FrontendPort"
Write-Host "Backend health: http://127.0.0.1:$BackendPort/api/v1/actuator/health"
Write-Host "Login: platform_admin / Admin@123"

if ($OpenBrowser) {
    Start-Process "http://127.0.0.1:$FrontendPort"
}
