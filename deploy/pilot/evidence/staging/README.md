# Staging seed & validation evidence

| 产物 | 来源 |
|---|---|
| `seed-verify-*.log` | `verify-pilot-seed.ps1 -ArchiveDir` |
| `staging-validation-*.summary.md` | `run-staging-validation.ps1` |
| `ACCEPTANCE_staging_*.md` | 复制 `ACCEPTANCE_TEMPLATE.md` 填写 |

## 一键执行（推荐）

```powershell
cd deploy\pilot
copy .env.staging.example .env   # 首次：编辑 staging 连接信息
.\scripts\run-staging-validation.ps1
```

仅 DB + 告警 + health（跳过 build/smoke）：

```powershell
.\scripts\run-staging-validation.ps1 -SkipBuild -SkipSmoke
```

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
