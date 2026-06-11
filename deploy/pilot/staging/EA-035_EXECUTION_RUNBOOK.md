# EA-035 Staging 真实验证执行手册

**目标：** 在真实 PostgreSQL + staging backend + 运维脚本环境完成闭环，产出 Go/No-Go 证据。

## 0. 前置（BLOCKER）

1. 合并 PR `feat/ea-033-pilot-prod-hardening`（或含 EA-035 的分支）至 `master`。
2. Staging 部署 backend **含 Flyway 1.1.027**；`flyway_schema_history` 最新 ≥ `1.1.027`。
3. Backend `SPRING_PROFILES_ACTIVE=prod`，health URL 从跳板机可达。
4. 跳板机：`psql`（或 Docker `scf-postgres`）、PowerShell 7+。

## 1. 配置 `.env`（约 10 min）

```powershell
cd <repo>\deploy\pilot
copy .env.staging.example .env
notepad .env
```

必填且不能为 `CHANGE_ME_*`：

- `SCF_DB_HOST` / `SCF_DB_PASSWORD`
- `SCF_API_HEALTH_URL`（例：`http://staging-backend:8080/api/v1/actuator/health`）
- `SCF_JWT_SECRET`（≥256 bit）
- `SCF_ENV_NAME=staging`

校验：

```powershell
Select-String -Path .env -Pattern 'CHANGE_ME'   # 应无输出
```

## 2. 一键 EA-035 签核（推荐）

**30 分钟观察（试点默认）：**

```powershell
.\scripts\run-ea035-signoff.ps1 -WatchMinutes 30 -WatchIntervalMinutes 5
```

**24 小时观察（生产前加强）：**

```powershell
.\scripts\run-ea035-signoff.ps1 -WatchHours 24 -WatchIntervalMinutes 15
```

产出：

| 文件 | 说明 |
|---|---|
| `evidence/staging/staging-gate-*.summary.md` | 闸门总结果 |
| `evidence/staging/seed-verify-*.log` | Seed 归档 |
| `evidence/staging/alerts-watch-*.log` | 连续 StrictStale |
| `docs/EA-035_Staging真实验证与发布签核报告_*.md` | 签核报告（自动生成） |

## 3. 分步（排障用）

```powershell
.\scripts\verify-pilot-seed.ps1 -ArchiveDir .\evidence\staging
.\monitoring\check-pilot-alerts.ps1 -StrictStale
.\scripts\run-alerts-watch.ps1 -IntervalMinutes 5 -Iterations 6 -ArchiveDir .\evidence\staging
.\scripts\generate-ea035-report.ps1
```

## 4. PASS 后

1. 填写 [`GO_NO_GO_CHECKLIST.md`](../evidence/staging/GO_NO_GO_CHECKLIST.md)。
2. 复制 `ACCEPTANCE_TEMPLATE.md` → `ACCEPTANCE_staging_YYYYMMDD.md` 签字。
3. 决策 **Go** → 按 [`../README.md`](../README.md) 进入试点 Prod。

## 5. FAIL 路由

| 失败 | 模块 | 动作 |
|---|---|---|
| seed-verify SQL | DB / Flyway | 补 migrate；查 OP001/PJ001/四用户 |
| Flyway < 1.1.027 | 发布 | 部署含 027 的 backend |
| A-01 health | 运维 / 网络 | 查进程、防火墙、URL |
| A-03 FAILED/stale | Saga | 监控台重试/补偿；清 FAILED |
| A-04 MANUAL_REQUIRED | 补偿 | 人工批准或修复根因 |
| cumulative_fail>0 | 稳定性 | 延长 watch；查间歇性故障 |
