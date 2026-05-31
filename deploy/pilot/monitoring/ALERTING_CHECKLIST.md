# 异常告警（试点 Prod 骨架）

## 1. 告警分层

| 层级 | 来源 | 试点实现 | 外部对接（待接） |
|---|---|---|---|
| L1 可用性 | `GET /api/v1/actuator/health` | 脚本 / 负载均衡探活 | K8s liveness、UptimeRobot |
| L2 业务 Saga | Saga 监控台 summary API | 人工巡检 + `MANUAL_REQUIRED > 0` | Cron + Webhook |
| L3 业务风险 | `bi_risk_alert_ticket` | 前端风险中心 + Inbox | 邮件/企微（EA-033） |
| L4 应用错误 | 日志 ERROR | 日志文件关键字 | ELK alert / Loki ruler |
| L5 基础设施 | PostgreSQL / 磁盘 | DBA 监控 | CloudWatch / Prometheus |

## 2. 必配告警规则（试点最小集）

| ID | 条件 | 严重度 | 动作 |
|---|---|---|---|
| A-01 | Health ≠ UP 持续 2min | P0 | 电话 + 回滚评估 |
| A-02 | HTTP 5xx 率 > 5% / 5min | P0 | 查日志 + 回滚 |
| A-03 | Outbox FAILED 计数 > 0 且 30min 未清零 | P1 | Saga 台介入 |
| A-04 | Compensation MANUAL_REQUIRED > 0 | P1 | 运维 + 业务审批 |
| A-05 | 登录失败率突增（> 50/min） | P1 | 安全排查 |
| A-06 | 磁盘使用 > 85%（日志/upload） | P2 | 清理 / 扩容 |
| A-07 | Flyway migration FAILED | P0 | 停应用 + DBA |

## 3. Saga 监控台手工巡检（每日试点期）

**platform_admin** 登录 → **Saga 监控**：

```text
[ ] Outbox 失败 = 0（或可解释）
[ ] 补偿待人工 = 0
[ ] 无超过 24h 的 PENDING 积压
```

API 参考：`GET /api/v1/saga/ops/summary`（需 `SAGA_OPS_VIEW`）。

## 4. 风险预警（应用内）

- 表：`bi_risk_alert_ticket`
- 入口：**风险预警**、**经营看板**、**消息待办**
- 类型：逾期融资、库存异常、凭证异常等（`RiskAlertMaterializer`）

试点期：每日 09:00 业务方确认 HIGH 告警已处理或挂起。

## 5. 告警路由模板

| 严重度 | 通知渠道 | SLA |
|---|---|---|
| P0 | 电话 + 即时消息 | 15 分钟响应 |
| P1 | 即时消息 | 1 小时 |
| P2 | 邮件 / 工单 | 下一工作日 |

填写 `.env` 中 `SCF_OPS_ONCALL`、`SCF_OPS_ESCALATION`。

## 6. 演练

- [ ] 人为停止 backend → A-01 触发
- [ ] 制造测试 Outbox FAILED（test env）→ A-03 流程文档可用
- [ ] 确认告警接收人收到通知

## 7. Codex / 下一轮

- ~~将 A-01、A-03、A-04 落地为脚本~~ → **EA-033** `monitoring/check-*.ps1`
- A-02 / A-05–A-07 落地为 Prometheus rules 或脚本（EA-034）
- Saga summary 定时拉取 + Webhook
- 与 EA-029 G2 补偿演练联动

## 8. EA-033 脚本用法

```powershell
cd deploy\pilot\monitoring
.\check-pilot-alerts.ps1 -BackendUrl http://127.0.0.1:8080/api/v1/actuator/health
.\check-saga-alerts.ps1 -StrictStale   # A-03 含 30min Stale 则 FAIL
```

Cron（Windows 任务计划 / Linux crontab）建议每 **5 分钟** 运行 `check-pilot-alerts.ps1`，失败时调用 `SCF_OPS_ONCALL` 通知流程。
