# 试点上线包（Pilot Launch Pack）

EA-032 运维闸门配套目录。上线前按顺序执行：

**Staging 验真（EA-034，prod 之前）：** 见 [`staging/STAGING_EXECUTION_CHECKLIST.md`](./staging/STAGING_EXECUTION_CHECKLIST.md) → `.\scripts\run-staging-validation.ps1`

**Prod 上线：**

1. 填写并注入 [`/.env.example`](./.env.example) 中的环境变量
2. 将 [`application-prod.example.yml`](./application-prod.example.yml) 复制为后端 `application-prod.yml`（或通过 ConfigMap/密钥管理注入）
3. 阅读 [`flyway/PRODUCTION_STRATEGY.md`](./flyway/PRODUCTION_STRATEGY.md) 执行数据库迁移
4. 运行 [`scripts/pre-flight.ps1`](./scripts/pre-flight.ps1)（健康检查 + seed 核查）
5. 按 [`uat/UAT_OPERATION_MANUAL.md`](./uat/UAT_OPERATION_MANUAL.md) 完成 UAT 签字
6. 配置 [`logging/LOGGING_AUDIT.md`](./logging/LOGGING_AUDIT.md)、[`logging/LOGBACK_MOUNT_EVAL.md`](./logging/LOGBACK_MOUNT_EVAL.md) 与 [`monitoring/ALERTING_CHECKLIST.md`](./monitoring/ALERTING_CHECKLIST.md)
7. 试运行 [`monitoring/check-pilot-alerts.ps1`](./monitoring/check-pilot-alerts.ps1)（A-01/A-03/A-04）
8. 确认 [`rollback/ROLLBACK_RUNBOOK.md`](./rollback/ROLLBACK_RUNBOOK.md) 已演练

主文档：[`docs/EA-034_Staging执行包与验收_20260531.md`](../../docs/EA-034_Staging执行包与验收_20260531.md)（EA-033：[`EA-033_…`](../../docs/EA-033_试点生产安全加固与配置落地_20260531.md)）
