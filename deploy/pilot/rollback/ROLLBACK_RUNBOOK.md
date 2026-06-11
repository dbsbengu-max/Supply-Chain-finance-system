# 试点回滚方案（Rollback Runbook）

## 1. 触发条件

- 上线后 30 分钟内核心接口错误率 > 5% 或登录不可用
- Flyway 迁移失败或数据不一致
- Saga `MANUAL_REQUIRED` / Outbox FAILED 积压无法人工消化
- 安全事件（JWT 泄露、误用 dev 密码）

## 2. 决策与通知

1. 运维值班确认触发条件 → 通知 `SCF_OPS_ONCALL` / 业务负责人
2. 记录：**回滚开始时间、版本、操作人**
3. 若仅前端缺陷：优先 **仅回滚前端静态资源**（§4A）
4. 若后端/DB 问题：**应用回滚 + DB 恢复**（§4B）

## 3. 回滚前快照

```bash
# 应用版本
git rev-parse HEAD > /var/scf/rollback/app-rev.txt

# DB 备份（上线前必须已有；若无则立即备份当前态再决定）
pg_dump -h $SCF_DB_HOST -U $SCF_DB_USER -Fc -n scf -f scf_pre_rollback_$(date +%Y%m%d_%H%M).dump scf

# Flyway 状态
psql -c "SELECT version, success, installed_on FROM scf.flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;"
```

## 4. 回滚步骤

### 4A. 仅前端回滚

1. 停止当前静态站点 / CDN 发布
2. 部署上一已知良好版本 `frontend/scf-web/dist`（或上一 git tag 构建产物）
3. 验证：`npm run smoke` 或手工登录 + 试点闭环页
4. 后端与 DB **不变**

### 4B. 后端应用回滚

1. 停止 scf-server 进程 / 容器
2. 部署 **上一 release JAR**（与回滚前 DB schema 兼容）
3. 确认 `SPRING_PROFILES_ACTIVE=prod` 与密钥未变
4. 启动后检查 `GET /api/v1/actuator/health` → `UP`
5. 检查 Saga 监控台无新增 FAILED

> **注意：** 若本次上线包含 **新 Flyway 版本**，仅回滚 JAR **不够** — 必须执行 §4C。

### 4C. 数据库回滚（慎用）

| 场景 | 做法 |
|---|---|
| 迁移未提交/失败 | 修复 migration，勿 manual 改 history |
| 迁移已成功但业务异常 | 从 **上线前 pg_dump** 恢复到新库或覆盖（需停写） |
| 部分 DML 错误 | 优先业务补偿 SQL；全库 restore 为最后手段 |

**恢复示例：**

```bash
# 停应用 → 断连 → restore
pg_restore -h $HOST -U $USER -d scf_restore -c scf_pre_release.dump
# 切换 datasource 到 scf_restore 或 rename schema（按 DBA 流程）
```

**禁止：** `flyway clean`、直接 DELETE flyway_schema_history 行（除非 DBA 书面批准）。

## 5. 回滚后验证

```powershell
.\deploy\pilot\scripts\pre-flight.ps1 -SkipBuild
cd frontend\scf-web
$env:SMOKE_SKIP_WEBSERVER=1; npm run smoke
```

- [ ] 健康检查 UP
- [ ] platform_admin 可登录
- [ ] 试点闭环 / Saga / 融资 / 清分 / BI 可访问
- [ ] 无新增审计异常

## 6. 事后

- 编写事故摘要（根因、时间线、数据影响）
- 更新 EA-032 发布记录与 Flyway 版本对照表
- 未回滚的 env/配置变更需逐项核对

## 7. 版本对照表（模板）

| 发布 | Git / Tag | Flyway max | 前端 dist | 回滚目标 |
|---|---|---|---|---|
| Pilot-1 | | V1_1_026 | | |
| Pilot-0 (baseline) | | V1_1_025 | | ✓ |
