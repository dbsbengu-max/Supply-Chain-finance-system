# EA-052 UAT evidence archive

param(
    [switch]$RunAcceptance,
    [switch]$SkipAcceptance,
    [string]$SessionId,
    [string]$SignoffExportPath
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$PilotRoot = Resolve-Path (Join-Path $ScriptDir "..")
$RepoRoot = Resolve-Path (Join-Path $PilotRoot "..\..")
$ArchiveRoot = Join-Path $PilotRoot "evidence\ea052-uat-archive"
$DeliveryDir = Join-Path $PilotRoot "delivery"

if (-not $SessionId) {
    $SessionId = Get-Date -Format "yyyyMMdd-HHmmss"
}

$SessionDir = Join-Path $ArchiveRoot $SessionId
$ScreenshotDir = Join-Path $SessionDir "screenshots"
New-Item -ItemType Directory -Force -Path $SessionDir, $ScreenshotDir | Out-Null

Write-Host "=== EA-052 UAT Archive ===" -ForegroundColor Green
Write-Host "Session: $SessionId"
Write-Host "Folder:  $SessionDir"

# --- Business issue log ---
$issuesTemplate = Join-Path $DeliveryDir "BUSINESS_ISSUE_LOG_TEMPLATE.md"
$issuesDest = Join-Path $SessionDir "BUSINESS_ISSUES.md"
if (-not (Test-Path $issuesDest)) {
    Copy-Item $issuesTemplate $issuesDest
    Write-Host "Created BUSINESS_ISSUES.md from template"
} else {
    Write-Host "BUSINESS_ISSUES.md already exists — skipped"
}

# --- Optional signoff export copy ---
if ($SignoffExportPath -and (Test-Path $SignoffExportPath)) {
    Copy-Item $SignoffExportPath (Join-Path $SessionDir "UAT_SIGNOFF_EXPORT.md") -Force
    Write-Host "Copied signoff export -> UAT_SIGNOFF_EXPORT.md"
} else {
    $placeholder = @(
        "# UAT Signoff Export (placeholder)",
        "",
        "Export from http://127.0.0.1:5173/uat/acceptance and save as:",
        "  $SessionDir\UAT_SIGNOFF_EXPORT.md",
        "",
        "Or re-run:",
        "  .\archive-ea052-uat.ps1 -SessionId $SessionId -SignoffExportPath <path-to-export.md>"
    ) -join "`n"
    Set-Content -Path (Join-Path $SessionDir "UAT_SIGNOFF_EXPORT.md") -Value $placeholder -Encoding UTF8
    Write-Host "Created UAT_SIGNOFF_EXPORT.md placeholder"
}

# --- EA-051 acceptance evidence ---
$acceptanceCopied = $false
if ($RunAcceptance -and -not $SkipAcceptance) {
    Write-Host "`nRunning run-ea051-acceptance.ps1 ..."
    $acceptScript = Join-Path $ScriptDir "run-ea051-acceptance.ps1"
    & $acceptScript
    $acceptExit = $LASTEXITCODE
} else {
    $acceptExit = 0
}

$ea051Dir = Join-Path $PilotRoot "evidence\ea051-acceptance"
if (Test-Path $ea051Dir) {
    $latest = Get-ChildItem $ea051Dir -Filter "ea051-*.summary.txt" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($latest) {
        Copy-Item $latest.FullName (Join-Path $SessionDir $latest.Name)
        $acceptanceCopied = $true
        Write-Host "Copied $($latest.Name)"
    }
}

# --- Copy EA-052 signoff archive template reference ---
$ea052Template = Join-Path $RepoRoot "docs\EA-052_UAT签字包归档_20260601.md"
if (Test-Path $ea052Template) {
    Copy-Item $ea052Template (Join-Path $SessionDir "EA-052_UAT签字包归档_模板.md")
}

# --- Manifest ---
$manifest = @(
    "# EA-052 Archive Manifest",
    "",
    "Session: $SessionId",
    "Created: $(Get-Date -Format o)",
    "Repo: $RepoRoot",
    "",
    "## Files in this folder",
    "",
    "| File | Status |",
    "|---|---|",
    "| ARCHIVE_MANIFEST.md | this file |",
    "| BUSINESS_ISSUES.md | $(if (Test-Path $issuesDest) { 'ready — fill on site' } else { 'missing' }) |",
    "| UAT_SIGNOFF_EXPORT.md | $(if (Test-Path (Join-Path $SessionDir 'UAT_SIGNOFF_EXPORT.md')) { 'present' } else { 'missing' }) |",
    "| ea051-*.summary.txt | $(if ($acceptanceCopied) { 'copied' } elseif ($RunAcceptance) { "exit $acceptExit" } else { 'optional — use -RunAcceptance' }) |",
    "| screenshots/ | empty dir for optional captures |",
    "| EA-052_UAT签字包归档_模板.md | reference |",
    "",
    "## Next steps",
    "",
    "1. Complete M1–M12 on /uat/acceptance; export signoff Markdown into this folder.",
    "2. Fill BUSINESS_ISSUES.md (Blocker/Major/Minor).",
    "3. Update docs/EA-052_UAT签字包归档_20260601.md with Verdict and signatures.",
    "4. Optional: add scans to SIGNED_UAT_PACK.pdf",
    "",
    "## Verdict gate",
    "",
    "- GO trial: no Blockers + A1–A4 PASS + M1–M12 signed pass/skip",
    "- Real production: NOT in scope (DEF-048)"
)
Set-Content -Path (Join-Path $SessionDir "ARCHIVE_MANIFEST.md") -Value ($manifest -join "`n") -Encoding UTF8

Write-Host ""
Write-Host "Archive ready: $SessionDir" -ForegroundColor Cyan
Write-Host "Next: demo M1-M12, export signoff, edit BUSINESS_ISSUES.md"

if ($RunAcceptance -and $acceptExit -ne 0) {
    Write-Host "WARN: run-ea051-acceptance exited $acceptExit — archive folder still created" -ForegroundColor Yellow
    exit $acceptExit
}

exit 0
