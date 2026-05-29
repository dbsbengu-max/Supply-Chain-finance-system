#Requires -RunAsAdministrator
$ErrorActionPreference = "Stop"

$pgService = "postgresql-x64-16"
$newPgBin = "D:\Program Files\PostgreSQL\16\bin\pg_ctl.exe"
$newPgData = "D:\Program Files\PostgreSQL\16\data"
$newJavaHome = "D:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$newJdkBin = "$newJavaHome\bin"
$newPgBinPath = "D:\Program Files\PostgreSQL\16\bin"
$srcJdkRoot = "C:\Program Files\Eclipse Adoptium"

if (-not (Test-Path $newPgBin)) { throw "Missing $newPgBin" }
if (-not (Test-Path $newPgData)) { throw "Missing $newPgData" }
if (-not (Test-Path "$newJavaHome\bin\java.exe")) { throw "Missing JDK at $newJavaHome" }

Write-Host "=== Point PostgreSQL service to D: ===" -ForegroundColor Cyan
Stop-Service -Name $pgService -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

$binPath = "`"$newPgBin`" runservice -N `"$pgService`" -D `"$newPgData`" -w"
& sc.exe config $pgService binPath= $binPath
if ($LASTEXITCODE -ne 0) { throw "sc config failed with $LASTEXITCODE" }

$regPath = "HKLM:\SOFTWARE\PostgreSQL\Installations\postgresql-x64-16"
if (Test-Path $regPath) {
    Set-ItemProperty -Path $regPath -Name "Base Directory" -Value "D:\Program Files\PostgreSQL\16\"
    Set-ItemProperty -Path $regPath -Name "Data Directory" -Value $newPgData
}

Start-Service -Name $pgService
Start-Sleep -Seconds 4
if ((Get-Service $pgService).Status -ne 'Running') { throw "PostgreSQL did not start" }

Write-Host "=== Update JAVA_HOME / PATH ===" -ForegroundColor Cyan
[Environment]::SetEnvironmentVariable("JAVA_HOME", $newJavaHome, "Machine")
[Environment]::SetEnvironmentVariable("JAVA_HOME", $newJavaHome, "User")

function Normalize-Path([string]$pathValue, [string[]]$remove, [string[]]$prepend) {
    $parts = @()
    if ($pathValue) { $parts = $pathValue -split ';' | Where-Object { $_ } }
    $parts = $parts | Where-Object { $remove -notcontains $_ }
    foreach ($p in ($prepend | Select-Object -Unique)) {
        if ($parts -notcontains $p) { $parts = ,$p + $parts }
    }
    return ($parts -join ';')
}

$remove = @(
    "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin",
    "C:\Program Files\PostgreSQL\16\bin"
)
$prepend = @($newJdkBin, $newPgBinPath)
[Environment]::SetEnvironmentVariable(
    "Path",
    (Normalize-Path ([Environment]::GetEnvironmentVariable("Path", "Machine")) $remove $prepend),
    "Machine")
[Environment]::SetEnvironmentVariable(
    "Path",
    (Normalize-Path ([Environment]::GetEnvironmentVariable("Path", "User")) $remove $prepend),
    "User")

Write-Host "=== Remove leftover C: JDK copy ===" -ForegroundColor Cyan
if (Test-Path $srcJdkRoot) {
    Remove-Item $srcJdkRoot -Recurse -Force
}

Write-Host "=== Verify ===" -ForegroundColor Cyan
& "$newJdkBin\java.exe" -version
$env:PGPASSWORD = "postgres"
& "$newPgBinPath\psql.exe" -U postgres -d scf -c "SELECT current_database() AS db, version();"
Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue

Write-Host "=== COMPLETE ===" -ForegroundColor Green
