# 日志与审计（试点 Prod 骨架）

## 1. 应用日志

### 1.1 当前基线

- Spring Boot 默认 Logback（无 `logback-spring.xml`）
- `application.yml` / prod：`logging.level.com.scf=INFO`

### 1.2 试点 prod 建议

| 项 | 建议 |
|---|---|
| 格式 | JSON 单行（便于 ELK/Loki）或 Logback Pattern 含 `%X{traceId}` |
| 级别 | root INFO；`com.scf` INFO；排查时临时 DEBUG（限时） |
| 输出 | stdout + 文件滚动（见 `logback-spring.example.xml`） |
| 保留 | 本地 14 天 / 30GB；集中日志按平台策略 |

**部署：** 复制 [`logback-spring.example.xml`](./logback-spring.example.xml) → `backend/scf-server/src/main/resources/logback-spring.xml`，按环境调整路径。

### 1.3 必打日志场景（代码已部分覆盖）

| 场景 | 位置 | 级别 |
|---|---|---|
| 登录成功/失败 | Auth 层 | INFO / WARN |
| Saga Outbox 失败 | OutboxEventProcessor | ERROR |
| 补偿 FAILED / MANUAL_REQUIRED | CompensationTaskProcessor | ERROR / WARN |
| 人工 Saga 操作 | SagaOpsService + AuditLog | INFO |
| 清分执行 | ClearingService | INFO |
| 未捕获异常 | GlobalExceptionHandler | ERROR |

## 2. 业务审计（DB）

### 2.1 表

- `scf.audit_operation_log` — 用户/系统操作审计
- 字段：`user_id`, `operator_id`, `project_id`, `action`, `object_type`, `object_id`, `before_json`, `after_json`, `ip`, `operation_at`

### 2.2 试点核查

```sql
SET search_path TO scf;
-- 最近 24h 人工 Saga 操作须含 manual_reason（after_json）
SELECT action, object_type, object_id, operation_at
FROM audit_operation_log
WHERE action LIKE 'SAGA_%'
ORDER BY operation_at DESC
LIMIT 20;
```

### 2.3 前端入口

- 菜单 **审计日志**（`AUDIT_VIEW`）
- 试点闭环 / 代采详情快捷入口

### 2.4 合规保留

| 项 | 试点建议 |
|---|---|
| 保留期 | ≥ 180 天（与 idempotency retention 对齐） |
| 导出 | 定期 SQL 导出或只读副本 |
| 篡改 | 表仅 INSERT；无应用层 UPDATE/DELETE |

## 3. 上线检查

```text
[ ] prod 日志级别非 DEBUG（除非窗口期）
[ ] 日志目录可写且磁盘监控已配置
[ ] audit_operation_log 有近期写入（登录后查一条）
[ ] Saga 人工重试后审计可见 manual_reason
[ ] 无密码/token 明文入日志（JWT secret 除外部配置）
```

## 4. Codex 下一轮

- 落地 `logback-spring.xml` 到主工程并验证 JSON 格式
- 定义集中日志采集（Filebeat / Promtail）配置
- 审计表分区或归档策略（数据量增长后）
