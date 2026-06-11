# EA-035 — assemble sign-off report from latest staging evidence
# Usage: .\generate-ea035-report.ps1 [-ArchiveDir] [-OutputPath]

param(
    [string]$ArchiveDir,
    [string]$OutputPath,
    [string]$CodexVerdict,
    [string]$CodexBacklog
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$PilotRoot = Resolve-Path (Join-Path $ScriptDir "..")
$RepoRoot = Resolve-Path (Join-Path $PilotRoot "..\..")

if (-not $ArchiveDir) { $ArchiveDir = Join-Path $PilotRoot "evidence\staging" }
$ArchiveDir = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ArchiveDir)

$ts = Get-Date -Format "yyyyMMdd"
if (-not $OutputPath) {
    $OutputPath = Join-Path $RepoRoot "docs\EA-035_Staging真实验证与发布签核报告_${ts}.md"
}

function Get-LatestFile {
    param([string]$Pattern)
    Get-ChildItem -Path $ArchiveDir -Filter $Pattern -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

$gateSummary = Get-LatestFile "staging-gate-*.summary.md"
$seedLog     = Get-LatestFile "seed-verify-*.log"
$watchLog    = Get-LatestFile "alerts-watch-*.log"

$gateResult = "UNKNOWN"
if ($gateSummary) {
    $gc = Get-Content $gateSummary.FullName -Raw
    if ($gc -match '\*\*PASS\*\*') { $gateResult = "PASS" }
    elseif ($gc -match '\*\*FAIL\*\*') { $gateResult = "FAIL" }
}

$cumFail = "n/a"
if ($watchLog) {
    $wl = Get-Content $watchLog.FullName -Raw
    if ($wl -match 'cumulative_fail=(\d+)') { $cumFail = $Matches[1] }
}

$gitRev = try { (git -C $RepoRoot rev-parse --short HEAD 2>$null).Trim() } catch { "unknown" }
$gitBranch = try { (git -C $RepoRoot branch --show-current 2>$null).Trim() } catch { "unknown" }

$goNoGo = if ($gateResult -eq "PASS" -and ($cumFail -eq "0" -or $cumFail -eq "n/a")) { "**Go (pending sign-off)**" } else { "**No-Go (fix required)**" }

if (-not $CodexVerdict) {
    $CodexVerdict = if ($gateResult -eq "PASS") { "pending Codex review" } else { "BLOCKED - staging-gate not PASS" }
}
if (-not $CodexBacklog) {
    $CodexBacklog = @(
        '- A-03: biz_event_outbox FAILED=0, stale over 30m=0',
        '- A-04: biz_compensation_task MANUAL_REQUIRED=0',
        '- Flyway: >= 1.1.027, sys_seed_manifest FULL backfill',
        '- Permissions: platform_admin has SAGA_OPS_*, AUDIT_VIEW, INBOX_VIEW, RISK_ALERT_VIEW'
    ) -join "`n"
}

$md = @"
# EA-035 Staging 真实验证与发布签核报告

生成时间：$(Get-Date -Format o)  
Git：``$gitBranch`` @ ``$gitRev``

## 1. 执行结论

| 项 | 值 |
|---|---|
| staging-gate | **$gateResult** |
| alerts-watch cumulative_fail | $cumFail |
| **Go / No-Go（自动化）** | $goNoGo |

## 2. 证据索引

| 证据 | 文件 |
|---|---|
| staging-gate summary | $(if ($gateSummary) { $gateSummary.Name } else { '_missing_' }) |
| seed-verify log | $(if ($seedLog) { $seedLog.Name } else { '_missing_' }) |
| alerts-watch log | $(if ($watchLog) { $watchLog.Name } else { '_missing_' }) |
| Go/No-Go 清单 | ``deploy/pilot/evidence/staging/GO_NO_GO_CHECKLIST.md`` |

## 3. Codex 复核

**Verdict：** $CodexVerdict

### A-03 / A-04 Backlog

$CodexBacklog

## 4. 手工签核（PASS 后填写）

见 ``deploy/pilot/evidence/staging/GO_NO_GO_CHECKLIST.md`` 与 ``ACCEPTANCE_staging_YYYYMMDD.md``。

## 5. 下一步

| 决策 | 动作 |
|---|---|
| **Go** | 试点 Prod：pre-flight → migrate → 重置四用户密码 → prod 告警 watch |
| **No-Go** | 按 §5 FAIL 路由修复 → 重跑 ``run-ea035-signoff.ps1`` |

"@

New-Item -ItemType Directory -Force -Path (Split-Path $OutputPath) | Out-Null
Set-Content -Path $OutputPath -Value $md -Encoding UTF8
Write-Host "Report: $OutputPath" -ForegroundColor Green
return $OutputPath
