# Staging seed & validation evidence

| 产物 | 来源 |
|---|---|
| `seed-verify-*.log` | `verify-pilot-seed.ps1 -ArchiveDir` |
| `staging-gate-*.summary.md` | `run-staging-gate.ps1` / **EA-035** `run-ea035-signoff.ps1` |
| `alerts-watch-*.log` | `run-alerts-watch.ps1`（StrictStale 连续观察） |
| `GO_NO_GO_CHECKLIST.md` | 发布 Go/No-Go 三方签字 |
| `ACCEPTANCE_staging_*.md` | 复制 `ACCEPTANCE_TEMPLATE.md` 填写 |

## EA-035 一键签核（推荐）

```powershell
cd deploy\pilot
copy .env.staging.example .env   # 首次：编辑真实 staging 凭据（无 CHANGE_ME）
.\scripts\run-ea035-signoff.ps1 -WatchMinutes 30 -WatchIntervalMinutes 5
```

24 小时观察：`.\scripts\run-ea035-signoff.ps1 -WatchHours 24 -WatchIntervalMinutes 15`

报告：`docs/EA-035_Staging真实验证与发布签核报告_*.md`（脚本自动生成）

## 分步执行

```powershell
.\scripts\verify-pilot-seed.ps1 -ArchiveDir .\evidence\staging
.\monitoring\check-pilot-alerts.ps1 -BackendUrl $env:SCF_API_HEALTH_URL
.\scripts\pre-flight.ps1 -BackendUrl $env:SCF_API_HEALTH_URL
```

完整清单见 [`staging/STAGING_EXECUTION_CHECKLIST.md`](../staging/STAGING_EXECUTION_CHECKLIST.md)。

## 归档状态

| Environment | Last run | Summary | Seed log | Acceptance |
|---|---|---|---|---|
| staging | _pending_ | — | — | — |
