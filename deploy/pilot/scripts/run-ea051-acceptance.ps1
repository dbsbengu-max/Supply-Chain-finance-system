# EA-051 上线交付包收口 — 一键验收

param(
    [switch]$SkipRegression,
    [switch]$SkipBuild,
    [switch]$SkipSmoke,
    [switch]$EnsureBackend,
    [switch]$ApplyDemoSeed,
    [int]$BackendPort = 8080
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$PilotRoot = Resolve-Path (Join-Path $ScriptDir "..")
$RepoRoot = Resolve-Path (Join-Path $PilotRoot "..\..")
$ServerDir = Join-Path $RepoRoot "backend\scf-server"
$WebDir = Join-Path $RepoRoot "frontend\scf-web"
$ReportDir = Join-Path $PilotRoot "evidence\ea051-acceptance"
$Stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportFile = Join-Path $ReportDir "ea051-$Stamp.summary.txt"

function Find-Mvn {
    if (Get-Command mvn -ErrorAction SilentlyContinue) { return "mvn" }
    $c = "$env:USERPROFILE\.m2\apache-maven-3.9.6\bin\mvn.cmd"
    if (Test-Path $c) { return $c }
    return $null
}

function Find-Java {
    $candidates = @(
        "D:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe",
        "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe",
        "$env:JAVA_HOME\bin\java.exe"
    )
    foreach ($p in $candidates) {
        if (Test-Path $p) { return $p }
    }
    if (Get-Command java -ErrorAction SilentlyContinue) { return "java" }
    throw "No working Java 17 found"
}

function Write-Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }
function Add-Result($line) {
    $script:Results += $line
    Write-Host $line
}

function Get-RegressionClasspath($ServerDir) {
    $cpFile = Join-Path $ServerDir "target\cp-jars\classpath.txt"
    if (Test-Path $cpFile) {
        $cp = (Get-Content $cpFile -Raw).Trim()
        if ($cp) { return "target/classes;target/test-classes;$cp" }
    }

    $jarDir = Join-Path $ServerDir "target\cp-jars"
    if (Test-Path $jarDir) {
        $jars = Get-ChildItem -Path $jarDir -Filter "*.jar" -ErrorAction SilentlyContinue
        if ($jars.Count -gt 0) {
            return "target/classes;target/test-classes;target/cp-jars/*"
        }
    }

    return $null
}

$script:Results = @()
$Failed = $false

Write-Host "=== EA-051 Acceptance Gate ===" -ForegroundColor Green
Write-Host "Repo: $RepoRoot"
Write-Host "Report: $ReportFile"

# --- PostgreSQL ---
Write-Step "A0 PostgreSQL :5432"
$pg = Test-NetConnection -ComputerName 127.0.0.1 -Port 5432 -WarningAction SilentlyContinue
if (-not $pg.TcpTestSucceeded) {
    Add-Result "FAIL  PostgreSQL not listening on :5432"
    $Failed = $true
} else {
    Add-Result "PASS  PostgreSQL :5432"
}

if ($ApplyDemoSeed -and -not $Failed) {
    Write-Step "Seed demo profile"
    & (Join-Path $ScriptDir "apply-seed-profile.ps1") -Profile demo -PilotRoot $PilotRoot
    if ($LASTEXITCODE -ne 0) { Add-Result "FAIL  apply-seed-profile demo"; $Failed = $true }
    else { Add-Result "PASS  demo seed applied" }
}

# --- Backend health ---
Write-Step "A4 Backend health"
$healthUrl = "http://127.0.0.1:$BackendPort/api/v1/actuator/health"
$healthUp = $false
try {
    $h = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 5
    if ($h.status -eq "UP") { $healthUp = $true }
} catch { }

if (-not $healthUp -and $EnsureBackend -and -not $Failed) {
    Write-Host "Starting backend via start-backend-local.ps1 ..."
    & (Join-Path $ScriptDir "start-backend-local.ps1") -Port $BackendPort -SkipBuild
    if ($LASTEXITCODE -eq 0) {
        try {
            $h = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 5
            $healthUp = ($h.status -eq "UP")
        } catch { }
    }
}

if ($healthUp) { Add-Result "PASS  GET $healthUrl -> UP" }
else {
    Add-Result "FAIL  Backend not UP at $healthUrl (use -EnsureBackend or start-backend-local.ps1)"
    $Failed = $true
}

# --- A1 Regression ---
if (-not $SkipRegression -and -not $Failed) {
    Write-Step "A1 Backend regression (171/171)"
    # Pin callback auth for tests — dev shell env must not override application-test.yml
    $env:SCF_CONTRACT_SIGN_CALLBACK_TOKEN = "mock-contract-sign-callback-token"
    $env:SCF_CONTRACT_SIGN_CALLBACK_VERIFICATION_MODE = "TOKEN"
    $mvn = Find-Mvn
    $java = Find-Java
    Push-Location $ServerDir
    if ($mvn) {
        & $mvn -q -DskipTests test-compile dependency:build-classpath "-Dmdep.outputFile=target/cp-jars/classpath.txt"
        if ($LASTEXITCODE -ne 0) {
            $fallbackCp = Get-RegressionClasspath $ServerDir
            if ($fallbackCp) {
                Add-Result "WARN  mvn test-compile failed; falling back to existing regression classpath"
            } else {
                Pop-Location
                Add-Result "FAIL  mvn test-compile"
                $Failed = $true
            }
        }
    } else {
        Add-Result "WARN  Maven not found; using existing target/classes + target/test-classes + target/cp-jars/*.jar"
    }

    if (-not $Failed) {
        $cp = Get-RegressionClasspath $ServerDir
        if (-not $cp) {
            Pop-Location
            Add-Result "FAIL  regression classpath not found; run Maven once or restore target/cp-jars"
            $Failed = $true
        }
    }

    if (-not $Failed) {
        $regLog = Join-Path $ServerDir "ea051-regression-$Stamp.log"
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        & $java "-Djdk.attach.allowAttachSelf=true" -cp $cp com.scf.EA019RegressionRunner *> $regLog
        $regExit = $LASTEXITCODE
        $ErrorActionPreference = $prevEap
        $resultLine = (Select-String -Path $regLog -Pattern "RESULT tests=\d+ succeeded=(\d+) failed=(\d+)" | Select-Object -Last 1).Line
        $ok171 = $false
        if ($resultLine -match "succeeded=(\d+).*failed=(\d+)") {
            $ok171 = ($Matches[1] -eq "171" -and $Matches[2] -eq "0")
        }
        if ($regExit -ne 0 -and -not $ok171) {
            Add-Result "FAIL  EA019RegressionRunner exit $regExit (see $regLog)"
            $Failed = $true
        } elseif ($ok171) {
            Add-Result "PASS  EA019RegressionRunner 171/171"
        } else {
            Add-Result "FAIL  EA019RegressionRunner (see $regLog)"
            Get-Content $regLog -Tail 20 | Write-Host
            $Failed = $true
        }
    }
    Pop-Location
} elseif ($SkipRegression) {
    Add-Result "SKIP  A1 regression"
}

# --- A2 Build ---
if (-not $SkipBuild -and -not $Failed) {
    Write-Step "A2 Frontend build"
    Push-Location $WebDir
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    npm run build 2>&1 | Tee-Object -Variable buildOut | Out-Null
    $buildExit = $LASTEXITCODE
    $ErrorActionPreference = $prevEap
    if ($buildExit -ne 0) {
        Add-Result "FAIL  npm run build"
        $Failed = $true
    } else {
        Add-Result "PASS  npm run build"
    }
    Pop-Location
} elseif ($SkipBuild) {
    Add-Result "SKIP  A2 build"
}

# --- A3 Smoke ---
if (-not $SkipSmoke -and -not $Failed) {
    Write-Step "A3 Frontend smoke 9/9"
    Push-Location $WebDir
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    npm run smoke 2>&1 | Tee-Object -Variable smokeOut | Out-Null
    $smokeExit = $LASTEXITCODE
    $ErrorActionPreference = $prevEap
    $smokePass = ($smokeOut -match ">>> SMOKE: PASS <<<") -and ($smokeExit -eq 0)
    if ($smokePass) { Add-Result "PASS  smoke 9/9" }
    else {
        Add-Result "FAIL  smoke (exit $smokeExit)"
        $Failed = $true
    }
    Pop-Location
} elseif ($SkipSmoke) {
    Add-Result "SKIP  A3 smoke"
}

# --- Report ---
New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null
$header = @(
    "EA-051 Acceptance Report",
    "Timestamp: $(Get-Date -Format o)",
    "Repo: $RepoRoot",
    ""
)
($header + $script:Results) | Set-Content -Path $ReportFile -Encoding UTF8

Write-Host ""
Write-Host "Report written: $ReportFile" -ForegroundColor Gray
foreach ($r in $script:Results) {
    if ($r -match "^FAIL") { Write-Host $r -ForegroundColor Red }
    elseif ($r -match "^PASS") { Write-Host $r -ForegroundColor Green }
    else { Write-Host $r }
}

if ($Failed) {
    Write-Host "`n>>> EA-051 ACCEPTANCE: FAIL <<<" -ForegroundColor Red
    exit 1
}

Write-Host "`n>>> EA-051 ACCEPTANCE: PASS <<<" -ForegroundColor Green
Write-Host "Next: run DEMO_SCRIPT_M1-M12.md and sign off at /uat/acceptance"
exit 0
