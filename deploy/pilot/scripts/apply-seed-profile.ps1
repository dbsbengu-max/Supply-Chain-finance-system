# EA-034 Apply decomposed seed profiles (iam | demo | full)
# Usage: .\apply-seed-profile.ps1 -Profile iam|demo|full [-PilotRoot]

param(
    [Parameter(Mandatory)]
    [ValidateSet("iam", "demo", "full")]
    [string]$Profile,
    [string]$PilotRoot,
    [string]$AppliedBy
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "_psql.ps1")

if (-not $PilotRoot) { $PilotRoot = Resolve-Path (Join-Path $PSScriptRoot "..") }
$seedDir = Join-Path $PilotRoot "flyway\seed"
$runner = if ($AppliedBy) { $AppliedBy } elseif ($env:STAGING_VALIDATION_RUN_BY) { $env:STAGING_VALIDATION_RUN_BY } else { $env:USERNAME }

function Apply-SqlFile {
    param([string]$Path, [string]$Label)
    Write-Host "Applying $Label ..." -ForegroundColor Cyan
    $code = Invoke-ScfPsql -FilePath $Path -PilotRoot $PilotRoot
    if ($code -ne 0) { throw "$Label failed (exit $code)" }
}

function Record-Manifest {
    param([string]$Id, [string]$Prof, [string]$Source)
    $exists = (Invoke-ScfPsqlQuery -Query `
        "SELECT CASE WHEN to_regclass('scf.sys_seed_manifest') IS NOT NULL THEN 1 ELSE 0 END;" `
        -PilotRoot $PilotRoot).Trim()
    if ($exists -ne "1") {
        Write-Host "WARN  sys_seed_manifest missing — skip manifest record (deploy V1_1_027)" -ForegroundColor Yellow
        return
    }
    $q = @"
SET search_path TO scf;
INSERT INTO sys_seed_manifest (id, seed_profile, source_file, applied_by)
VALUES ('$Id', '$Prof', '$Source', '$runner')
ON CONFLICT (id) DO UPDATE SET seed_profile = EXCLUDED.seed_profile, applied_at = now(), applied_by = EXCLUDED.applied_by;
"@
    Invoke-ScfPsqlQuery -Query $q -PilotRoot $PilotRoot | Out-Null
}

Write-Host "=== EA-034 Seed Profile: $Profile ===" -ForegroundColor Cyan

switch ($Profile) {
    "iam" {
        Apply-SqlFile (Join-Path $seedDir "seed_iam.sql") "IAM seed"
        Record-Manifest "EA034_IAM" "IAM" "flyway/seed/seed_iam.sql"
    }
    "demo" {
        Apply-SqlFile (Join-Path $seedDir "seed_demo_business.sql") "Demo business seed"
        Record-Manifest "EA034_DEMO" "DEMO" "flyway/seed/seed_demo_business.sql"
    }
    "full" {
        Apply-SqlFile (Join-Path $seedDir "seed_iam.sql") "IAM seed"
        Apply-SqlFile (Join-Path $seedDir "seed_demo_business.sql") "Demo business seed"
        Record-Manifest "EA034_FULL" "FULL" "flyway/seed/seed_iam.sql+seed_demo_business.sql"
    }
}

Write-Host "PASS  Seed profile '$Profile' applied" -ForegroundColor Green
