# EA-048 外部真实供应商 Sandbox 复跑 + Go/No-Go 签核包

**日期**：2026-06-01  
**前置**：EA-045 集成包、EA-046 证据脚本、EA-047 灰度策略  
**目标**：拿到真实 endpoint/appId/密钥后，在 **Staging/Pilot** 上复跑 EA-046，通过 EA-047 就绪闸门，产出 **GO/NO-GO** 签核包；**仅 GO 后**才允许部署 `ALLOWLIST` 小流量生产灰度。

## 当前执行策略（2026-06-01）

EA-048 暂缓执行，作为“真实供应商切换/生产灰度前置待办”保留；当前不阻塞系统功能开发和试点功能上线。开发主线转入 EA-049：完成核心功能、演示闭环、UAT 可执行用例、Mock/HTTP Adapter 可用路径和上线体验收口。

EA-048 恢复执行前，不要求团队提供真实 vendor 执行结果；但不得将本地 quasi-sandbox 结果等同于真实供应商 Go/No-Go。

## 与 EA-046 / EA-047 的关系

```
┌─────────────────────────────────────────────────────────────┐
│  EA-048 编排（本包）                                         │
├─────────────────────────────────────────────────────────────┤
│  Phase 0  Preflight（env、config 探针）                      │
│  Phase 1  EA-046 真实供应商 Sandbox 闭环证据                  │
│  Phase 2  EA-047 pre-cutover 就绪闸门（检查供应商、验签、补偿池与计划灰度） │
│  Output   ea048-gonogo-*.json → GO / NO-GO                   │
└─────────────────────────────────────────────────────────────┘
         │ GO
         ▼
  部署 ALLOWLIST + 复跑 EA-047（无 -PreCutover）
         │
         ▼
  小流量生产灰度（试点 project）
```

| 阶段 | 本地 quasi-sandbox（已完成） | EA-048 真实 vendor |
|---|---|---|
| 用途 | 脚本/回归自证 | 供应商联调签核 |
| `SCF_ENV_NAME` | `local-quasi-sandbox` | `vendor-sandbox` |
| EA-046 verdict | 参考 | **BLOCKER** |
| 能否进生产灰度 | 否 | GO 后可以 |

## 执行步骤

### 1. 准备凭证与 Staging（约 30 min）

1. 向供应商获取：沙箱 base URL、appId、密钥、回调白名单、API 文档版本。
2. Staging 后端 `prod` profile + Flyway ≥ `1.1.034`，HTTP 供应商 env 已注入（与 `.env` 一致）。
3. 在 Staging DB 准备测试单证（已审核、待签署）；必要时执行 `seed-ea046-sandbox-document.sql` 或业务 UAT 单证。
4. 供应商控制台登记回调 URL：`…/api/v1/integrations/contracts/sign-callback`。

### 2. 填写 env（约 10 min）

```powershell
cd deploy\pilot
copy .env.ea048-real-vendor.example .env.ea048-real-vendor
notepad .env.ea048-real-vendor
```

必填：`SCF_BASE_URL`、登录密码、真实 `SCF_CONTRACT_SIGN_HTTP_*`、回调 HMAC、`SCF_ESIGN_DOCUMENT_ID`、试点 `SCF_CONTRACT_SIGN_ROLLOUT_PROJECT_ALLOWLIST`、`EA048_ROLLBACK_ACK=yes`。

校验：

```powershell
Select-String -Path .env.ea048-real-vendor -Pattern 'CHANGE_ME'   # 应无输出
.\scripts\verify-contract-sign-config.ps1 -EnvFile .\.env.ea048-real-vendor
```

### 3. 一键 Go/No-Go

```powershell
.\scripts\run-ea048-real-vendor-gonogo.ps1 -EnvFile .\.env.ea048-real-vendor
```

产出：`evidence/contract-sign-gonogo/ea048-YYYYMMDD-HHmmss/`

| 文件 | 说明 |
|---|---|
| `ea048-*.json` | GO/NO-GO bundle（含 EA-046 run_id、trace、checks） |
| `ea048-*.summary.md` | 签核摘要 |
| `env.redacted.template.md` | 脱敏后的执行 env 快照 |

### 4. GO 后的生产动作（不得跳过）

1. **部署** Pilot/Prod 环境变量：
   - `SCF_CONTRACT_SIGN_ROLLOUT_MODE=ALLOWLIST`
   - `SCF_CONTRACT_SIGN_ROLLOUT_PROJECT_ALLOWLIST=<试点 project>`
2. **复跑** EA-047 正式闸门（确认已路由到生产供应商）：

```powershell
# .env 中 EA046_EVIDENCE_* 已由 EA-048 写入或手动填写
.\scripts\run-ea047-prod-cutover-gate.ps1 -EnvFile .\.env.ea048-real-vendor
```

3. **观察 24h**：签署成功率、补偿池 `CONTRACT_SIGN_CALLBACK`、Saga 查单。
4. 无异常后再考虑 `PERCENT` 放量（见 EA-047）。

## NO-GO 处理

- 不得设置 `SCF_CONTRACT_SIGN_ROLLOUT_MODE=ALLOWLIST`。
- 保留 EA-046 证据与供应商 ticket 号，修复后整包重跑 EA-048。
- 常见 FAIL：vendor 401/403（密钥或签名）、回调验签失败、单证 404、compensation 未入池。

## 交付清单

| 项 | 路径 |
|---|---|
| env 模板 | `deploy/pilot/.env.ea048-real-vendor.example` |
| 编排脚本 | `deploy/pilot/scripts/run-ea048-real-vendor-gonogo.ps1` |
| Go/No-Go schema | `deploy/pilot/evidence/contract-sign-gonogo/ea048-gonogo.schema.json` |
| Checklist | `docs/EA-048_GoNoGo签核Checklist.md` |
| 验收模板 | `docs/EA-048_外部真实供应商GoNoGo验收结果_20260601.md` |

## 三方签核

GO 决策需：平台运维 + 业务 UAT + 技术负责人（见 Checklist 与 `GO_NO_GO_CHECKLIST.md` §F）。
