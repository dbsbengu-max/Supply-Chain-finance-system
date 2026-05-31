# Staging 验收签字模板（EA-034）

复制本文件为 `ACCEPTANCE_staging_YYYYMMDD.md`，与 `staging-validation-*.summary.md`、`seed-verify-*.log` 一并归档。

---

## 基本信息

| 字段 | 填写 |
|---|---|
| 环境 | staging |
| 验证日期 | YYYY-MM-DD |
| Git 版本 / Tag | |
| 发布 Ticket | |
| 执行人 | |

## 自动化结果（附 summary 路径）

| 项 | 结果 | 证据文件 |
|---|---|---|
| Seed 核查 | PASS / FAIL | `seed-verify-*.log` |
| 告警 A-01/A-03/A-04 | PASS / FAIL | summary § Steps |
| Pre-flight | PASS / FAIL | summary § Steps |
| **整体验证** | PASS / FAIL | `staging-validation-*.summary.md` |

## 手工确认

- [ ] 四用户密码已重置（无 mock_hash 或已书面豁免）
- [ ] prod profile 启动无 DevDataInitializer 日志
- [ ] EA-029 UAT 抽检 ≥8 条通过
- [ ] Saga MANUAL_REQUIRED = 0

## 签字

| 角色 | 姓名 | 日期 | 签字 |
|---|---|---|---|
| 平台运维 | | | |
| 业务 UAT | | | |
| 技术负责人 | | | |

## 备注

（失败项、豁免说明、后续 action）

---

**通过后：** 可进入试点 prod 上线（`deploy/pilot/README.md`）。
