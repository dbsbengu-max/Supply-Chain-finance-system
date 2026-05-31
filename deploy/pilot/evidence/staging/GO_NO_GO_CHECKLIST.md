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
