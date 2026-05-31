# Staging 执行清单（EA-034）

在 **staging 跳板机** 上按序执行。一键脚本见 [`../scripts/run-staging-validation.ps1`](../scripts/run-staging-validation.ps1)。

## 0. 前置条件

| # | 项 | 确认 |
|---|---|---|
| 0.1 | 代码已 merge 至 `master`（或部署与 PR 一致的 tag） | [ ] |
| 0.2 | staging PostgreSQL 14+ 可连，`schema scf` 已 migrate 至 V1_1_026 | [ ] |
| 0.3 | staging backend 以 `SPRING_PROFILES_ACTIVE=prod` 运行 | [ ] |
| 0.4 | 跳板机已装 **psql**、**Node 18+**、**PowerShell 7+** | [ ] |
| 0.5 | 已从 [`.env.staging.example`](../.env.staging.example) 复制为 `deploy/pilot/.env` | [ ] |

## 1. 配置（约 15 min）

```powershell
cd <repo>\deploy\pilot
copy .env.staging.example .env
# 编辑 .env：DB 密码、JWT、SCF_API_HEALTH_URL、SMOKE_BASE_URL
notepad .env
```

核对：

- [ ] `SCF_DEV_PASSWORD_BOOTSTRAP=false`
- [ ] JWT / 银行 token **不是** dev 默认值
- [ ] `SCF_API_HEALTH_URL` 指向 staging backend 健康检查

## 2. 一键自动化验证（约 10–20 min）

**终端 1 — backend**（若尚未运行）：

```powershell
cd <repo>\backend\scf-server
$env:SPRING_PROFILES_ACTIVE="prod"
# 注入 SCF_DB_* / SCF_JWT_SECRET 等（与 .env 一致）
mvn spring-boot:run
```

**终端 2 — frontend**（smoke 需要）：

```powershell
cd <repo>\frontend\scf-web
npm ci
npm run dev
```

**终端 3 — 验证脚本**：

```powershell
cd <repo>\deploy\pilot
.\scripts\run-staging-validation.ps1
```

期望：

- [ ] `seed-verify-*.log` 写入 `evidence/staging/`
- [ ] `staging-validation-*.summary.md` 写入 `evidence/staging/`
- [ ] 终端输出 `>>> STAGING VALIDATION: PASS <<<`

## 3. 手工补项（脚本不覆盖）

| # | 项 | 确认 |
|---|---|---|
| 3.1 | 四用户密码已重置（seed 脚本无 `WARN mock_hash` 或已文档豁免） | [ ] |
| 3.2 | 启动日志无 `Updated dev password for user` | [ ] |
| 3.3 | EA-029 核心 UAT 抽检 ≥8 条（见 UAT 手册 §4） | [ ] |
| 3.4 | Saga 监控台 MANUAL_REQUIRED = 0 | [ ] |

## 4. 证据归档

1. 将 `evidence/staging/seed-verify-*.log` 与 `staging-validation-*.summary.md` 提交到 repo **或** 附到发布 ticket。
2. 复制 [`../evidence/staging/ACCEPTANCE_TEMPLATE.md`](../evidence/staging/ACCEPTANCE_TEMPLATE.md) 为 `ACCEPTANCE_staging_YYYYMMDD.md`，填写签字。
3. 主验收文档：[`docs/EA-034_Staging执行包与验收_20260531.md`](../../docs/EA-034_Staging执行包与验收_20260531.md)

## 5. 失败处理

| 失败项 | 动作 |
|---|---|
| Seed SQL 失败 | 查 Flyway history、DB 连通、权限 |
| A-01 health | 查 backend 进程 / 防火墙 / URL |
| A-03/A-04 Saga | 登录 Saga 监控台处理 FAILED / MANUAL_REQUIRED |
| Smoke 失败 | 确认 frontend dev 已启、`SMOKE_BASE_URL` 正确 |
| PRE-FLIGHT FAIL | 看 summary 中具体子项 |

## 6. 通过后

- [ ] 通知业务 UAT 在 staging 签字（UAT 手册 §6）
- [ ] 进入试点 prod 上线（[`../README.md`](../README.md)）
