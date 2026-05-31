# Flyway 生产迁移策略（试点）

## 1. 原则

| 原则 | 说明 |
|---|---|
| 单一来源 | 所有 DDL/DML 变更仅通过 `backend/scf-server/src/main/resources/db/migration/` |
| 只增不改 | 已发布环境的 migration 文件**禁止修改**；修正用新 `V1_1_0XX__*.sql` |
| JPA 校验 | `spring.jpa.hibernate.ddl-auto=validate`，Flyway 建表后 Hibernate 仅校验 |
| 禁止 clean | 生产 `flyway.clean-disabled=true`，禁止 `flyway:clean` |
| 可重复 | 同一版本包 + 同一 DB 快照 → 迁移结果一致 |

## 2. 命名与版本

- 格式：`V1_1_{NNN}__{snake_description}.sql`
- 当前基线：`V1_1_001` … `V1_1_026`（含 mock seed `004`、权限 seed `006`、试点 demo 数据）
- 下一编号从 `027` 起

## 3. 试点环境分类

| 环境 | Flyway 行为 | Seed 策略 |
|---|---|---|
| 本地 dev | 自动 migrate；`DevDataInitializer` 写演示密码 | V1_1_004 mock + 006 权限 |
| CI / test | H2 或 PG test profile | `src/test/resources/sql/*` |
| **试点 prod** | 空库或 staging 快照 → migrate 至 latest | 保留 004/006；**禁用** dev 密码 bootstrap |

> **风险项：** `V1_1_004__seed_mock_data.sql` 含演示企业与订单。试点若需「空业务库 + 仅 IAM」，需 EA-033 拆分为 `seed_iam` / `seed_demo`（本阶段仅文档标记，不改动 migration）。

## 4. 上线前检查（DBA / 运维）

```text
[ ] 目标库 PostgreSQL ≥ 14，schema `scf` 由 V1_1_001 创建
[ ] 备份：pg_dump -Fc -n scf ... （见 rollback 文档）
[ ] flyway_schema_history 无 FAILED 记录
[ ] SELECT version, description, success FROM scf.flyway_schema_history ORDER BY installed_rank;
[ ] 应用启动日志无 Flyway Validate 错误
[ ] 迁移窗口：低峰期；预估 V1_1_001–026 空库 < 5 min（视硬件）
```

## 5. 执行方式

**推荐（应用启动）：**

```bash
export SPRING_PROFILES_ACTIVE=prod
# 配置 SCF_DB_* / datasource
java -jar scf-server.jar
# Flyway 在 Spring Boot 启动时自动 migrate
```

**可选（CI 预检，需 flyway CLI 或 testcontainers）：**

```bash
# 仅 validate，不 migrate（需 flyway CLI 与 JDBC URL）
flyway -url=jdbc:postgresql://host:5432/scf -user=scf -password=*** \
  -schemas=scf -locations=filesystem:backend/scf-server/src/main/resources/db/migration validate
```

## 6. 失败处理

| 现象 | 动作 |
|---|---|
| checksum mismatch | **禁止**改历史 SQL；用 repair 仅当 DBA 确认文件被误改且与 prod 一致 |
| migration FAILED | 查 `flyway_schema_history` + 应用日志；修复 SQL 后 **新** migration，勿改已失败版本 |
| 部分成功 | 回滚应用版本 + 按 ROLLBACK_RUNBOOK 恢复 DB 备份 |

## 7. Codex / 下一轮

- 确认试点 prod 是否接受 V1_1_004 演示数据
- 评估 `DevDataInitializer` 加 `@Profile("!prod")` 或 `SCF_DEV_PASSWORD_BOOTSTRAP` 开关
- 记录每次上线的 `flyway_schema_history` 最高 version 到发布记录
