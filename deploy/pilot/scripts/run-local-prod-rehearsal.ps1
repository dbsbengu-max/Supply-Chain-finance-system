# Local prod-profile rehearsal — validates EA-035 scripts on localhost (NOT staging Go)
# Usage:
#   copy ..\.env.local-prod-rehearsal.example ..\.env.local-prod-rehearsal
#   .\run-local-prod-rehearsal.ps1
#   .\run-local-prod-rehearsal.ps1 -WatchMinutes 30 -SkipBackendStart   # backend already running

param(
    [string]$EnvFile,
    [int]$WatchMinutes = 5,
    [int]$WatchIntervalMinutes = 1,
    [switch]$SkipBackendStart,
    [switch]$SkipSignoff,
    [switch]$KeepBackend
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$PilotRoot = Resolve-Path (Join-Path $ScriptDir "..")
$RepoRoot = Resolve-Path (Join-Path $PilotRoot "..\..")
$ArchiveDir = Join-Path $PilotRoot "evidence\local-prod-rehearsal"

if (-not $EnvFile) {
    $EnvFile = Join-Path $PilotRoot ".env.local-prod-rehearsal"
}

function Import-ScfEnvFile {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        throw "Missing $Path — copy .env.local-prod-rehearsal.example first"
    }
    Get-Content $Path | ForEach-Object {
        if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
        $k, $v = $_ -split '=', 2
        if ($k -and $null -ne $v) { Set-Item -Path "env:$k" -Value $v.Trim() }
    }
}

function Find-Mvn {
    if (Get-Command mvn -ErrorAction SilentlyContinue) { return "mvn" }
    $candidates = @(
        "$env:USERPROFILE\.m2\apache-maven-3.9.6\bin\mvn.cmd",
        "$env:LOCALAPPDATA\Temp\apache-maven-3.9.11\bin\mvn.cmd"
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { return $c }
    }
    throw "Maven not found — install Maven or add mvn to PATH"
}

function Find-Java {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
        return (Join-Path $env:JAVA_HOME "bin\java.exe")
    }
    if (Get-Command java -ErrorAction SilentlyContinue) { return "java" }
    $candidates = @(
        "D:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe",
        "C:\Program Files\Eclipse Adoptium\jdk-17\bin\java.exe",
        "C:\Program Files\Java\jdk-17\bin\java.exe"
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { return $c }
    }
    throw "Java 17 not found — install Java 17 or set JAVA_HOME"
}

function Start-BackendJob {
    param(
        [string]$ServerDir,
        [hashtable]$EnvMap
    )

    $jar = Get-ChildItem -Path (Join-Path $ServerDir "target") -Filter "scf-server-*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch "plain" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($jar) {
        $java = Find-Java
        Write-Host "Backend start mode: java -jar $($jar.Name)" -ForegroundColor DarkGray
        return Start-Job -ScriptBlock {
            param($Java, $JarPath, $EnvMap)
            foreach ($key in $EnvMap.Keys) { Set-Item -Path "env:$key" -Value $EnvMap[$key] }
            & $Java -jar $JarPath 2>&1
        } -ArgumentList $java, $jar.FullName, $EnvMap
    }

    $hasMaven = (Get-Command mvn -ErrorAction SilentlyContinue) -or
        (Test-Path "$env:USERPROFILE\.m2\apache-maven-3.9.6\bin\mvn.cmd") -or
        (Test-Path "$env:LOCALAPPDATA\Temp\apache-maven-3.9.11\bin\mvn.cmd")
    if ($hasMaven) {
        $mvn = Find-Mvn
        Write-Host "Backend start mode: Maven spring-boot:run" -ForegroundColor DarkGray
        return Start-Job -ScriptBlock {
            param($Mvn, $Dir, $EnvMap)
            Set-Location $Dir
            foreach ($key in $EnvMap.Keys) { Set-Item -Path "env:$key" -Value $EnvMap[$key] }
            & $Mvn -q spring-boot:run "-Dspring-boot.run.profiles=prod" 2>&1
        } -ArgumentList $mvn, $ServerDir, $EnvMap
    }

    $classes = Join-Path $ServerDir "target\classes"
    $cpJars = Join-Path $ServerDir "target\cp-jars\*"
    if ((Test-Path $classes) -and (Test-Path (Join-Path $ServerDir "target\cp-jars"))) {
        $java = Find-Java
        Write-Host "Backend start mode: compiled classes + cp-jars" -ForegroundColor DarkGray
        return Start-Job -ScriptBlock {
            param($Java, $Dir, $EnvMap)
            Set-Location $Dir
            foreach ($key in $EnvMap.Keys) { Set-Item -Path "env:$key" -Value $EnvMap[$key] }
            & $Java -cp "target/classes;target/cp-jars/*" com.scf.ScfApplication 2>&1
        } -ArgumentList $java, $ServerDir, $EnvMap
    }

    throw "No backend startup method available. Build a jar, install Maven, or prepare target/classes and target/cp-jars."
}

Import-ScfEnvFile -Path $EnvFile
New-Item -ItemType Directory -Force -Path $ArchiveDir | Out-Null

$uploadDir = $env:SCF_FILE_STORAGE_DIR
if ($uploadDir -and -not [System.IO.Path]::IsPathRooted($uploadDir)) {
    $uploadDir = Join-Path (Join-Path $RepoRoot "backend\scf-server") $uploadDir
}
if ($uploadDir) {
    New-Item -ItemType Directory -Force -Path $uploadDir | Out-Null
    $env:SCF_FILE_STORAGE_DIR = $uploadDir
}

Write-Host "=== Local Prod Rehearsal ===" -ForegroundColor Cyan
Write-Host "Env: $($env:SCF_ENV_NAME) | Archive: $ArchiveDir"
Write-Host "NOTE: PASS here is local-rehearsal only — NOT staging Go/No-Go" -ForegroundColor Yellow

$backendJob = $null
if (-not $SkipBackendStart) {
    try {
        $health = Invoke-RestMethod -Uri $env:SCF_API_HEALTH_URL -TimeoutSec 3
        if ($health.status -eq "UP") {
            Write-Host "Backend already UP — skip start" -ForegroundColor DarkGray
        }
    } catch {
        Write-Host "Starting backend (prod profile)..." -ForegroundColor Cyan
        $serverDir = Join-Path $RepoRoot "backend\scf-server"
        $backendJob = Start-BackendJob -ServerDir $serverDir -EnvMap @{
            SCF_DB_HOST                 = $env:SCF_DB_HOST
            SCF_DB_PORT                 = $env:SCF_DB_PORT
            SCF_DB_NAME                 = $env:SCF_DB_NAME
            SCF_DB_USER                 = $env:SCF_DB_USER
            SCF_DB_PASSWORD             = $env:SCF_DB_PASSWORD
            SCF_JWT_SECRET              = $env:SCF_JWT_SECRET
            SCF_JWT_EXPIRATION_MS       = $env:SCF_JWT_EXPIRATION_MS
            SCF_BANK_CALLBACK_TOKEN     = $env:SCF_BANK_CALLBACK_TOKEN
            SCF_FILE_STORAGE_DIR        = $env:SCF_FILE_STORAGE_DIR
            SCF_FILE_STORAGE_BUCKET     = $env:SCF_FILE_STORAGE_BUCKET
            SCF_FILE_MAX_SIZE_BYTES     = $env:SCF_FILE_MAX_SIZE_BYTES
            SCF_DEV_PASSWORD_BOOTSTRAP  = $env:SCF_DEV_PASSWORD_BOOTSTRAP
            SCF_LOG_LEVEL_ROOT          = $env:SCF_LOG_LEVEL_ROOT
            SCF_LOG_LEVEL_COM_SCF       = $env:SCF_LOG_LEVEL_COM_SCF
            SERVER_PORT                 = $env:SERVER_PORT
            SPRING_PROFILES_ACTIVE      = "prod"
        }

        $deadline = (Get-Date).AddMinutes(8)
        $up = $false
        while ((Get-Date) -lt $deadline) {
            Start-Sleep -Seconds 5
            try {
                $health = Invoke-RestMethod -Uri $env:SCF_API_HEALTH_URL -TimeoutSec 5
                if ($health.status -eq "UP") { $up = $true; break }
            } catch { }
            Write-Host "  waiting for health..." -ForegroundColor DarkGray
        }
        if (-not $up) {
            if ($backendJob) { Receive-Job $backendJob -Keep | Select-Object -Last 30 }
            throw "Backend did not become healthy within 8 minutes"
        }
        Write-Host "Backend UP" -ForegroundColor Green
    }
}

if ($SkipSignoff) {
    Write-Host "SkipSignoff — backend ready for manual tests" -ForegroundColor Yellow
    exit 0
}

$gateArgs = @{
    EnvFile              = $EnvFile
    ArchiveDir           = $ArchiveDir
    WatchMinutes         = $WatchMinutes
    WatchIntervalMinutes = $WatchIntervalMinutes
}
& (Join-Path $ScriptDir "run-staging-gate.ps1") @gateArgs
$gateExit = $LASTEXITCODE

& (Join-Path $ScriptDir "generate-ea035-report.ps1") -ArchiveDir $ArchiveDir | Out-Null

Write-Host "`nEvidence: $ArchiveDir" -ForegroundColor DarkGray
if ($gateExit -eq 0) {
    Write-Host ">>> LOCAL PROD REHEARSAL: PASS (not staging Go) <<<" -ForegroundColor Green
} else {
    Write-Host ">>> LOCAL PROD REHEARSAL: FAIL <<<" -ForegroundColor Red
}

if ($backendJob -and $KeepBackend) {
    Write-Host "Backend job still running (Id=$($backendJob.Id)). Stop: Stop-Job $($backendJob.Id); Remove-Job $($backendJob.Id)" -ForegroundColor DarkGray
} elseif ($backendJob) {
    Stop-Job $backendJob -ErrorAction SilentlyContinue
    Remove-Job $backendJob -ErrorAction SilentlyContinue
}

exit $gateExit
