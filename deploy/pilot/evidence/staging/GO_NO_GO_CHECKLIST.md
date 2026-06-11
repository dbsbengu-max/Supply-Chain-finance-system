# EA-035 Go / No-Go 发布签核清单

**用途：** Staging 真实验证通过后，三方签字决定是否进入试点 Prod。  
**规则：** 任一 **BLOCKER** 未勾选 → **No-Go**；全部 BLOCKER + 必需 PASS → **Go**。

---

## A. 自动化闸门（BLOCKER）

| ID | 项 | 判据 | PASS | 证据 |
|---|---|---|---|---|
| G-A1 | staging-gate 总结果 | `>>> STAGING GATE: PASS <<<` | [ ] | `staging-gate-*.summary.md` |
| G-A2 | seed-verify | SQL 完成，四用户 ACTIVE，无 SQL 错误 | [ ] | `seed-verify-*.log` |
| G-A3 | A-01 健康检查 | health 200 / UP | [ ] | summary § Steps |
| G-A4 | A-03 StrictStale | outbox FAILED=0 或 stale=0 | [ ] | `alerts-watch-*.log` |
| G-A5 | A-04 StrictStale | MANUAL_REQUIRED=0 | [ ] | `alerts-watch-*.log` |
| G-A6 | alerts-watch 连续观察 | 30min 或 24h，`cumulative_fail=0` | [ ] | `alerts-watch-*.log` 末行 |
| G-A7 | Flyway 版本 | ≥ `1.1.027`（含 seed manifest） | [ ] | seed log §7 |

## B. 配置与安全（BLOCKER）

| ID | 项 | 判据 | PASS | 备注 |
|---|---|---|---|---|
| G-B1 | prod profile | `SPRING_PROFILES_ACTIVE=prod` | [ ] | |
| G-B2 | DevDataInitializer | 启动日志无 `Updated dev password` | [ ] | |
| G-B3 | 四用户密码 | 无 `mock_hash` 或书面豁免 | [ ] | |
| G-B4 | JWT / 银行 token | 非 dev 默认值 | [ ] | |
| G-B5 | `SCF_DEV_PASSWORD_BOOTSTRAP` | `false` | [ ] | |

## C. 业务 UAT（BLOCKER 子集 + 推荐）

| ID | 项 | 判据 | PASS | 参考 |
|---|---|---|---|---|
| G-C1 | EA-029 G1 主链路 | 抽检 ≥8/24 条 PASS | [ ] | UAT 手册 §4 |
| G-C2 | EA-029 G2 Saga | MANUAL_REQUIRED=0，补偿演练已知 | [ ] | Saga 监控台 |
| G-C3 | EA-029 G3 权限 | 抽检 5 条穿透 PASS | [ ] | |
| G-C4 | 试点闭环页 | `/pilot/closure` 可访问 | [ ] | EA-030 |

## D. 工程与发布（推荐）

| ID | 项 | PASS |
|---|---|---|
| G-D1 | PR EA-030～035 已合入 master / 部署 tag 一致 | [ ] |
| G-D2 | 后端回归 110/110（或 staging 等价 CI） | [ ] |
| G-D3 | Rollback 方案已读并确认 | [ ] |
| G-D4 | On-call / 升级路径已配置 | [ ] |

## E. 签章供应商生产切换 EA-047（**真实生产灰度** BLOCKER；试点演示可用 Mock/HTTP Adapter）

> 当前功能上线 / 试点演示：**不强制** G-E1 为真实 vendor；本地 quasi-sandbox 或 HTTP Stub 闭环即可。进入 **ALLOWLIST 生产灰度** 前须满足 G-E1～G-E5 且 EA-048 GO。

| ID | 项 | 判据 | PASS | 证据 |
|---|---|---|---|---|
| G-E1 | EA-046 Sandbox 闭环 | verdict=PASS，含 trace/callback/补偿 | [ ] | `ea046-*.json` |
| G-E2 | ESIGN_HTTP configured | config API `provider_connections[0].configured=true` | [ ] | gate / config.json |
| G-E3 | 灰度路由 | `production_rollout.routed_to_production` 符合预期 | [ ] | config API |
| G-E4 | EA-047 cutover gate | `>>> EA-047 Gate: PASS <<<` | [ ] | `run-ea047-prod-cutover-gate.ps1` |
| G-E5 | 回滚演练 | MODE=OFF 后新单走 MOCK | [ ] | 变更记录 |

## F. EA-048 真实供应商 Go/No-Go（**后置待办**，不阻塞当前功能上线 / 试点演示）

> **2026-06-01 决策**：DEF-048 暂缓。当前主线为 EA-049（可演示、可测试、可试点）。本节仅在 **真实供应商生产灰度** 前为 BLOCKER；本地 Mock/HTTP Adapter 与 quasi-sandbox 证据不替代本节。

| ID | 项 | 判据 | PASS | 证据 |
|---|---|---|---|---|
| G-F0 | 待办状态 | DEF-048 = Deferred，恢复条件已登记 | [ ] | `EA-049_功能上线优先级与后置待办登记_20260601.md` |
| G-F1 | EA-048 编排 | `>>> EA-048: GO <<<` | [ ] | `ea048-*.json` |
| G-F2 | 真实 vendor EA-046 | verdict=PASS，`environment≠local-quasi-sandbox` | [ ] | `ea046-*.json` |
| G-F3 | EA-047 pre-cutover | Phase 2 PASS | [ ] | ea048 summary |
| G-F4 | 三方签核 | Checklist E048 全 BLOCKER | [ ] | `EA-048_GoNoGo签核Checklist.md` |
| G-F5 | ALLOWLIST 部署 | **仅 GO 后**执行 G-E048-D1～D3 | [ ] | 变更单 |

---

## 签核结论

| 字段 | 填写 |
|---|---|
| 日期 | |
| Git commit / tag | |
| staging-gate summary | |
| alerts-watch 时长 | 30 min / 24 h |
| **决策** | **Go** / **No-Go** |
| No-Go 原因 / 修复 ticket | |

| 角色 | 姓名 | 日期 | 签字 |
|---|---|---|---|
| 平台运维 | | | |
| 业务 UAT | | | |
| 技术负责人 | | | |
