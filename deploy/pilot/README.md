# 试点上线包（Pilot Launch Pack）

EA-032 运维闸门配套目录。上线前按顺序执行：

1. 填写并注入 [`/.env.example`](./.env.example) 中的环境变量
2. 将 [`application-prod.example.yml`](./application-prod.example.yml) 复制为后端 `application-prod.yml`（或通过 ConfigMap/密钥管理注入）
3. 阅读 [`flyway/PRODUCTION_STRATEGY.md`](./flyway/PRODUCTION_STRATEGY.md) 执行数据库迁移
4. 运行 [`scripts/pre-flight.ps1`](./scripts/pre-flight.ps1)（健康检查 + seed 核查）
5. 按 [`uat/UAT_OPERATION_MANUAL.md`](./uat/UAT_OPERATION_MANUAL.md) 完成 UAT 签字
6. 配置 [`logging/LOGGING_AUDIT.md`](./logging/LOGGING_AUDIT.md) 与 [`monitoring/ALERTING_CHECKLIST.md`](./monitoring/ALERTING_CHECKLIST.md)
7. 确认 [`rollback/ROLLBACK_RUNBOOK.md`](./rollback/ROLLBACK_RUNBOOK.md) 已演练

主文档：[`docs/EA-032_试点上线包与运维闸门_20260531.md`](../../docs/EA-032_试点上线包与运维闸门_20260531.md)
