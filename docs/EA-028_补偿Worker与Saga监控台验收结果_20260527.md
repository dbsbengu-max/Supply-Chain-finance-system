# EA-028 补偿任务 Worker + Saga 运营监控台验收结果

生成日期：2026-05-27

## 1. 验收结论

EA-028 将 EA-027 的补偿链路从「只入队」推进为**可执行、可自动重试、可人工介入**的闭环，并提供 Saga 运营监控台供平台管理员处理 backlog。

本次交付覆盖：

- 补偿任务 Worker 轮询 `PENDING` 与到期 `FAILED`，按 1/3/5/10/30 分钟阶梯重试。
- 达最大重试次数后转 `MANUAL_REQUIRED`，写入审计 `SAGA_COMPENSATION_MANUAL`。
- 平台管理员可通过 API 人工重试（`SAGA_COMPENSATION_RETRY`）或批准执行（`SAGA_COMPENSATION_APPROVE`）。
- `MARGIN_UNFREEZE` / `INVENTORY_UNFREEZE` 经 `AgencyPurchaseCompensationHandler` 实际执行，幂等安全（保证金解冻 clamp ≥0）。
- Saga 运营 API：`/saga/ops/summary`、Outbox/补偿任务分页列表、Outbox 人工重试。
- 前端「Saga 监控」页面：汇总卡片、补偿/Outbox 双 Tab、人工重试与批准执行按钮。
- 集成测试 `SagaOpsIntegrationTest`（9 用例）并纳入 `EA019RegressionRunner`。

## 2. 主要实现文件

后端：

- `backend/scf-server/src/main/resources/db/migration/V1_1_026__compensation_worker_saga_ops.sql`
- `backend/scf-server/src/main/java/com/scf/saga/entity/BizCompensationTask.java`（retry/error 字段）
- `backend/scf-server/src/main/java/com/scf/saga/repository/BizCompensationTaskRepository.java`
- `backend/scf-server/src/main/java/com/scf/saga/service/CompensationTaskProcessor.java`
- `backend/scf-server/src/main/java/com/scf/saga/service/CompensationTaskExecutor.java`
- `backend/scf-server/src/main/java/com/scf/saga/service/CompensationTaskService.java`
- `backend/scf-server/src/main/java/com/scf/saga/service/SagaOpsService.java`
- `backend/scf-server/src/main/java/com/scf/saga/controller/SagaOpsController.java`
- `backend/scf-server/src/main/java/com/scf/saga/dto/SagaOpsDtos.java`
- `backend/scf-server/src/main/java/com/scf/agencypurchase/dto/AgencyPurchaseCompensationTaskView.java`
- `backend/scf-server/src/test/java/com/scf/saga/SagaOpsIntegrationTest.java`

前端：

- `frontend/scf-web/src/api/sagaOps.ts`
- `frontend/scf-web/src/views/SagaOpsCenterView.vue`
- `frontend/scf-web/src/router/index.ts`、`permissions.ts`
- `frontend/scf-web/src/layouts/DashboardLayout.vue`
- `frontend/scf-web/src/constants/agencyPurchaseDict.ts`（`PROCESSING` / `MANUAL_REQUIRED`）

## 3. 补偿任务状态机

```text
PENDING → PROCESSING → SUCCESS
                    ↘ FAILED（next_retry_at 调度）→ 到期后再 PROCESSING
                    ↘ 重试 ≥5 次 → MANUAL_REQUIRED
MANUAL_REQUIRED → [批准执行] → PENDING → PROCESSING → SUCCESS
FAILED / MANUAL_REQUIRED → [人工重试] → PENDING（清零 retry）→ PROCESSING
```

与 Outbox 处理器对齐的重试间隔：`{1, 3, 5, 10, 30}` 分钟。

## 4. 权限

| 权限码 | 角色 | 能力 |
|---|---|---|
| `SAGA_OPS_VIEW` | 平台管理员、资金方 | 查看汇总与列表 |
| `SAGA_OPS_MANAGE` | 平台管理员 | 人工重试、批准执行、Outbox 重试 |

## 5. 集成测试用例

| 用例 | 验证点 |
|---|---|
| `ea028WorkerExecutesPendingCompensationAutomatically` | Worker 自动执行 `MARGIN_UNFREEZE` 并解冻保证金 |
| `ea028FailedCompensationSchedulesRetryAndSkipsEarlyRetry` | 失败写入 `next_retry_at`，未到期不重复计数 |
| `ea028MaxRetriesMovesToManualRequired` | 达上限转 `MANUAL_REQUIRED` |
| `ea028ManualRetryCompensationViaApi` | POST `/compensation-tasks/{id}/retry` 修正参数后成功 |
| `ea028ApproveExecuteManualRequiredCompensation` | POST `/approve-execute` 从人工态恢复执行 |
| `ea028OpsSummaryReturnsBacklogCounts` | 汇总 API 反映 pending backlog |
| `ea028FundingUserCanViewButNotRetry` | 资金方只读、无 manage 权限 |
| `ea028WorkerExecutesPendingInventoryUnfreezeAutomatically` | Worker 自动执行 `INVENTORY_UNFREEZE` 并解冻库存 |
| `ea028ManualRetryInventoryUnfreezeViaApi` | 库存补偿失败后 API 人工重试成功 |

## 6. 验证命令

```powershell
$env:JAVA_HOME = "D:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
mvn -f backend/scf-server/pom.xml "-Dtest=AgencyPurchaseSagaIntegrationTest,SagaOpsIntegrationTest" test

cd frontend/scf-web
npm run build
```

前端 build 已通过（2026-05-27）。后端测试需在本地 Maven 环境执行。

## 7. 与 EA-027 的关系

EA-027 在 Saga 失败时入队 `MARGIN_UNFREEZE` / `INVENTORY_UNFREEZE`；EA-028 补齐 Worker 执行、重试调度、人工介入与运营可视化，形成完整补偿闭环。
