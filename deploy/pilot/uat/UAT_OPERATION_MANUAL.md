# 试点 UAT 操作手册

生成：EA-032 | 关联 EA-029 闸门 G1–G7

## 1. 适用范围

- **环境：** 试点生产（Pilot Prod）或预发布（Staging，配置与 prod 一致）
- **角色：** 业务 UAT 负责人、平台运维、Codex 复核
- **账号：** 见 §2

## 2. 测试账号

| 登录名 | 默认密码（dev bootstrap） | 身份 | 用途 |
|---|---|---|---|
| `platform_admin` | `Admin@123` | 平台管理员 / PJ001 | 全链路、Saga、审计 |
| `funding_user` | `Fund@123` | 资金方 / PJ001 | 融资审批、放款、清分 |
| `member_user` | `Member@123` | 成员企业 | 代采、融资申请 |
| `warehouse_user` | `Wh@123` | 仓库方 | 库存、仓单 |

> 试点 prod 上线前须确认密码已轮换，且 `password_hash ≠ mock_hash`（见 seed 核查脚本）。

## 3. 自动化冒烟（上线前必跑）

```powershell
# 终端 1：后端 prod profile + DB
# 终端 2：前端
cd frontend/scf-web
npm run dev

# 终端 3
cd frontend/scf-web
$env:SMOKE_SKIP_WEBSERVER=1
npm run smoke
```

期望：`>>> SMOKE: PASS <<<`（6 项：登录、试点闭环、Saga、融资、清分、BI）。

## 4. 手工 UAT 主链路（24 条）

完整用例表见 [`docs/EA-029_端到端试点闭环与上线闸门_20260527.md`](../../docs/EA-029_端到端试点闭环与上线闸门_20260527.md) §3。

**推荐操作顺序（platform_admin）：**

1. 登录 → 工作台 → **试点闭环** 向导逐步进入
2. **贸易代采** → 创建/提交/审批（或复用 ORD001 演示单）
3. **Saga 监控** → Outbox SUCCESS；失败时补偿任务 + 人工原因重试
4. **融资管理** → 创建/审批/放款（`funding_user` 切换身份）
5. **银行流水** → 导入/匹配
6. **清分中心** → 试算 → 执行
7. **数字凭证** → 签发 → 还款释放 → 兑付
8. **经营看板** → KPI 与融资状态分布
9. **审计日志** → 检索 `SAGA_` 人工操作含 `manual_reason`

## 5. 异常补偿演练（4 条，G2）

见 EA-029 §4：模拟 Outbox 失败、补偿 FAILED、MANUAL_REQUIRED 人工批准、业务单跳转。

## 6. 权限穿透（5 条，G3）

| ID | 操作 | 预期 |
|---|---|---|
| P-01 | 无 `SAGA_OPS_VIEW` 用户访问 `/saga/ops` | 403 / 禁止页 |
| P-02 | 无 `SAGA_OPS_MANAGE` | 无重试/批准按钮 |
| P-03 | 无 `CLEARING_EXECUTE` | 不可执行清分 |
| P-04 | 无 `BI_EXPORT` | 无导出按钮 |
| P-05 | 无 `AUDIT_VIEW` | 无审计菜单 |

## 7. 签字记录

| 项 | 负责人 | 日期 | PASS/FAIL | 备注 |
|---|---|---|---|---|
| 自动化 smoke | | | | |
| UAT 主链路 24 条 | | | | |
| 补偿演练 4 条 | | | | |
| 权限 5 条 | | | | |
| 审计 4 条 | | | | |
| BI 3 条 | | | | |

## 8. 缺陷处理

- **Blocker：** 禁止上线，修复后回归 §3–§6
- **Major：** 评估 workaround，运维记录已知问题
- **Minor：** 记入 EA-033+ backlog
