# EA-048 Go/No-Go 签核 Checklist

**规则**：任一 BLOCKER 未勾选 → **NO-GO**；全部 PASS + 脚本 `>>> EA-048: GO <<<` → 可部署 ALLOWLIST。

## A. 凭证与环境（BLOCKER）

| ID | 项 | PASS | 证据 |
|---|---|---|---|
| E048-A1 | 供应商沙箱 appId/密钥/endpoint 已到位 | [ ] | vendor ticket |
| E048-A2 | Staging `ESIGN_HTTP` configured=true | [ ] | config probe |
| E048-A3 | 回调 URL 已在供应商控制台登记 | [ ] | 控制台截图 |
| E048-A4 | `.env.ea048-real-vendor` 无 CHANGE_ME | [ ] | grep 结果 |
| E048-A5 | `SCF_ENV_NAME=vendor-sandbox`（非 local） | [ ] | ea048 json |

## B. EA-046 真实闭环（BLOCKER）

| ID | 项 | PASS | 证据 |
|---|---|---|---|
| E048-B1 | `run-ea048` Phase 1 EA-046 PASS | [ ] | ea046-*.json |
| E048-B2 | external_sign_ref 已归档 | [ ] | ea048 bundle |
| E048-B3 | provider_request_id / provider_trace_id 已归档 | [ ] | ea048 bundle |
| E048-B4 | CALLBACK_REPLAY + 补偿池场景 PASS | [ ] | ea046 scenarios |

## C. EA-047 就绪（BLOCKER）

| ID | 项 | PASS | 证据 |
|---|---|---|---|
| E048-C1 | EA-047 pre-cutover gate PASS | [ ] | 脚本输出 |
| E048-C2 | 计划 ALLOWLIST + project 列表已确认 | [ ] | env / 业务确认 |
| E048-C3 | 回滚方案已读 `EA048_ROLLBACK_ACK=yes` | [ ] | env |

## D. GO 后部署（GO 之后执行，非签核前）

| ID | 项 | PASS |
|---|---|---|
| E048-D1 | 生产部署 `ROLLOUT_MODE=ALLOWLIST` | [ ] |
| E048-D2 | EA-047 正式 gate（无 PreCutover）PASS | [ ] |
| E048-D3 | 24h 监控无 P1 签署/补偿异常 | [ ] |

---

## 签核结论

| 字段 | 填写 |
|---|---|
| ea048 run_id | |
| ea046 run_id | |
| verdict | GO / NO-GO |
| 试点 project allowlist | |
| 日期 | |

| 角色 | 姓名 | 日期 | 签字 |
|---|---|---|---|
| 平台运维 | | | |
| 业务 UAT | | | |
| 技术负责人 | | | |
