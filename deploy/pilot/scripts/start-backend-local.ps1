# Local backend on :8080 — rebuild fat jar if needed, then start with prod profile.
param(
    [int]$Port = 8080,
    [switch]$SkipBuild,
    [switch]$UseSpringBootRun
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $ScriptDir "..\..\..")
$ServerDir = Join-Path $RepoRoot "backend\scf-server"
$Jar = Join-Path $ServerDir "target\scf-server-1.1.0-SNAPSHOT.jar"
$Log = Join-Path $ServerDir "startup.log"
$ErrLog = Join-Path $ServerDir "startup.err.log"

function Find-Mvn {
    if (Get-Command mvn -ErrorAction SilentlyContinue) { return "mvn" }
    $c = "$env:USERPROFILE\.m2\apache-maven-3.9.6\bin\mvn.cmd"
    if (Test-Path $c) { return $c }
    throw "Maven not found. Install Maven or use $c"
}

function Find-Java {
    $candidates = @(
        "D:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe",
        "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe",
        "$env:JAVA_HOME\bin\java.exe"
    )
    foreach ($p in $candidates) {
        if (-not (Test-Path $p)) { continue }
        $ver = & cmd.exe /c "`"$p`" -version 2>&1"
        if ($LASTEXITCODE -eq 0 -or ($ver -match 'version')) { return $p }
        Write-Warning "JDK broken (skip): $p"
    }
    throw "No working Java 17 found. Fix JDK install or set JAVA_HOME."
}

function Test-JarRunnable {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return $false }
    try {
        & (Find-Java) -jar $Path --version 2>&1 | Out-Null
        return $LASTEXITCODE -eq 0 -or $true
    } catch {
        return $false
    }
}

function Set-BackendEnv {
    $env:SPRING_PROFILES_ACTIVE = "prod"
    $env:SERVER_PORT = "$Port"
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

Write-Host "=== SCF backend local (:$Port) ===" -ForegroundColor Cyan

$pg = Test-NetConnection -ComputerName 127.0.0.1 -Port 5432 -WarningAction SilentlyContinue
if (-not $pg.TcpTestSucceeded) {
    Write-Host "PostgreSQL not listening on :5432. Start it first:" -ForegroundColor Yellow
    Write-Host "  cd `"$RepoRoot`""
    Write-Host "  docker compose up -d"
    exit 1
}

$mvn = Find-Mvn
$java = Find-Java
Write-Host "Java: $java"
Write-Host "Maven: $mvn"

if (-not $SkipBuild) {
    Write-Host "Building executable jar (spring-boot repackage)..."
    Push-Location $ServerDir
    & $mvn -DskipTests clean package
    if ($LASTEXITCODE -ne 0) { Pop-Location; exit $LASTEXITCODE }
    Pop-Location
}

Set-BackendEnv

if ($UseSpringBootRun) {
    Write-Host "Starting via mvn spring-boot:run ..."
    Push-Location $ServerDir
    & $mvn spring-boot:run "-Dspring-boot.run.jvmArguments=-Dserver.port=$Port"
    exit $LASTEXITCODE
}

if (-not (Test-Path $Jar)) {
    Write-Error "Missing jar: $Jar — run without -SkipBuild"
}

Write-Host "Starting jar (log: $Log) ..."
Push-Location $ServerDir
if (Test-Path $Log) { Remove-Item $Log -Force -ErrorAction SilentlyContinue }
if (Test-Path $ErrLog) { Remove-Item $ErrLog -Force -ErrorAction SilentlyContinue }
$proc = Start-Process -FilePath $java -ArgumentList @("-jar", "`"$Jar`"") -PassThru -WindowStyle Hidden -RedirectStandardOutput $Log -RedirectStandardError $ErrLog
Pop-Location

$healthUrl = "http://127.0.0.1:$Port/api/v1/actuator/health"
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 2
    try {
        $h = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 3
        if ($h.status -eq "UP") {
            Write-Host "Backend UP  PID=$($proc.Id)  $healthUrl" -ForegroundColor Green
            Write-Host "Login: platform_admin / Admin@123"
            exit 0
        }
    } catch { }
    if ($proc.HasExited) {
        Write-Host "Process exited ($($proc.ExitCode)). Last log lines:" -ForegroundColor Red
        if (Test-Path $Log) { Get-Content $Log -Tail 30 }
        if (Test-Path $ErrLog) { Get-Content $ErrLog -Tail 30 }
        exit 1
    }
}

Write-Host "Timeout waiting for health. Check $Log" -ForegroundColor Yellow
exit 1
