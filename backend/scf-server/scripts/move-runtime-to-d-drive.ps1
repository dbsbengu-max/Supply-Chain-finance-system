#Requires -RunAsAdministrator
$ErrorActionPreference = "Stop"

$srcJdkRoot = "C:\Program Files\Eclipse Adoptium"
$dstJdkRoot = "D:\Program Files\Eclipse Adoptium"
$jdkName = "jdk-17.0.19.10-hotspot"

$srcPgRoot = "C:\Program Files\PostgreSQL"
$dstPgRoot = "D:\Program Files\PostgreSQL"
$pgService = "postgresql-x64-16"
$pgVersion = "16"

function Ensure-Dir([string]$path) {
    if (-not (Test-Path $path)) {
        New-Item -ItemType Directory -Path $path -Force | Out-Null
    }
}

Write-Host "=== Stop PostgreSQL service ===" -ForegroundColor Cyan
Stop-Service -Name $pgService -Force -ErrorAction Stop
Start-Sleep -Seconds 3

Write-Host "=== Move Eclipse Adoptium JDK ===" -ForegroundColor Cyan
Ensure-Dir $dstJdkRoot
if (Test-Path $srcJdkRoot) {
    if (-not (Test-Path (Join-Path $dstJdkRoot $jdkName))) {
        robocopy $srcJdkRoot $dstJdkRoot /E /MOVE /R:2 /W:2 /NFL /NDL /NJH /NJS | Out-Null
        if ($LASTEXITCODE -ge 8) { throw "robocopy JDK failed with exit code $LASTEXITCODE" }
    }
    if (Test-Path $srcJdkRoot) {
        Remove-Item $srcJdkRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

$newJavaHome = Join-Path $dstJdkRoot $jdkName
if (-not (Test-Path (Join-Path $newJavaHome "bin\java.exe"))) {
    throw "JDK not found at $newJavaHome"
}

Write-Host "=== Move PostgreSQL ===" -ForegroundColor Cyan
Ensure-Dir $dstPgRoot
if (Test-Path $srcPgRoot) {
    if (-not (Test-Path (Join-Path $dstPgRoot $pgVersion))) {
        robocopy $srcPgRoot $dstPgRoot /E /MOVE /R:2 /W:2 /NFL /NDL /NJH /NJS | Out-Null
        if ($LASTEXITCODE -ge 8) { throw "robocopy PostgreSQL failed with exit code $LASTEXITCODE" }
    }
    if (Test-Path $srcPgRoot) {
        Remove-Item $srcPgRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

$newPgBin = "D:\Program Files\PostgreSQL\$pgVersion\bin\pg_ctl.exe"
$newPgData = "D:\Program Files\PostgreSQL\$pgVersion\data"
if (-not (Test-Path $newPgBin)) { throw "PostgreSQL binary not found at $newPgBin" }
if (-not (Test-Path $newPgData)) { throw "PostgreSQL data not found at $newPgData" }

$newBinPath = "`"$newPgBin`" runservice -N `"$pgService`" -D `"$newPgData`" -w"
Write-Host "Updating service binPath -> $newBinPath"
& sc.exe config $pgService binPath= $newBinPath | Out-Null
if ($LASTEXITCODE -ne 0) { throw "sc config failed" }

$regPath = "HKLM:\SOFTWARE\PostgreSQL\Installations\postgresql-x64-$pgVersion"
if (Test-Path $regPath) {
    Set-ItemProperty -Path $regPath -Name "Base Directory" -Value "D:\Program Files\PostgreSQL\$pgVersion\" -ErrorAction SilentlyContinue
    Set-ItemProperty -Path $regPath -Name "Data Directory" -Value $newPgData -ErrorAction SilentlyContinue
}

Write-Host "=== Update JAVA_HOME and PATH ===" -ForegroundColor Cyan
[Environment]::SetEnvironmentVariable("JAVA_HOME", $newJavaHome, "Machine")
$machinePath = [Environment]::GetEnvironmentVariable("Path", "Machine")
$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
$oldJdkBin = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin"
$newJdkBin = Join-Path $newJavaHome "bin"
$oldPgBin = "C:\Program Files\PostgreSQL\16\bin"
$newPgBinPath = "D:\Program Files\PostgreSQL\16\bin"

function Replace-PathEntry([string]$pathValue) {
    $parts = $pathValue -split ';' | Where-Object { $_ -and $_ -ne $oldJdkBin -and $_ -ne $oldPgBin }
    $joined = ($parts -join ';').Trim(';')
    if ($joined -notlike "*$newJdkBin*") { $joined = "$newJdkBin;$joined" }
    if ($joined -notlike "*$newPgBinPath*") { $joined = "$newPgBinPath;$joined" }
    return $joined.Trim(';')
}

[Environment]::SetEnvironmentVariable("Path", (Replace-PathEntry $machinePath), "Machine")
[Environment]::SetEnvironmentVariable("Path", (Replace-PathEntry $userPath), "User")
[Environment]::SetEnvironmentVariable("JAVA_HOME", $newJavaHome, "User")

$env:JAVA_HOME = $newJavaHome
$env:Path = "$newJdkBin;$newPgBinPath;" + [Environment]::GetEnvironmentVariable("Path", "Process")

Write-Host "=== Start PostgreSQL service ===" -ForegroundColor Cyan
Start-Service -Name $pgService
Start-Sleep -Seconds 4
$svc = Get-Service -Name $pgService
if ($svc.Status -ne 'Running') { throw "PostgreSQL failed to start: $($svc.Status)" }

Write-Host "=== Verify ===" -ForegroundColor Cyan
& "$newJavaHome\bin\java.exe" -version
& "$newPgBinPath\psql.exe" -U postgres -d scf -c "SELECT 1 AS ok;" 2>&1

Write-Host "=== DONE ===" -ForegroundColor Green
Write-Host "JAVA_HOME=$newJavaHome"
Write-Host "PostgreSQL data=$newPgData"
