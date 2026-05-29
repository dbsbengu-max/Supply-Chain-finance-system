# Agent 分工协作指南

本项目遵循《工程 Agent 任务拆解与统一简报 V1.1》的分工模式。

## 项目路径

- 代码：`供应链金融管理系统/`
- 开发包：`../系统开发文档/`

## Agent 分工（当前口径）

| Agent | 职责 |
|---|---|
| **Codex** | 权限穿透扩展、DataScopeHelper、`@RequirePermission` AOP、二次审查、Saga/事务复核 |
| **Cursor** | 业务页面、联调、横向模块（BI/风险/待办/审计）、凭证 MVP 实现 |
| **Claude Code** | 架构风险复核、复杂解释 |

## EA 任务进度（2026-05-27）

### 工程基线 ✅

| 任务 | 状态 | 说明 |
|---|---|---|
| EA-001~003 DDL/字典 | ✅ | 审查报告 + Flyway V1_1_005 |
| EA-002 Flyway 拆分 | ✅ | V1_1_001~020 |
| EA-013 权限映射 | ✅ | P0-1/2 完成；P0-4 AOP 渐进中 |
| EA-014 权限穿透 | ✅ | 26 条 PERM 通过 |

### 核心框架 ✅ 骨架

| 任务 | 模块 |
|---|---|
| EA-004 BPM | `backend/.../bpm/` |
| EA-006 Saga/Outbox | `backend/.../saga/` |
| EA-007 幂等 | `backend/.../idempotency/` |
| EA-008 清分引擎 | `backend/.../clearing/` |

### 核心业务 🟡

| 模块 | 状态 |
|---|---|
| IAM / 客户 / 项目 / 价格 / 订单 | ✅ MVP |
| 贸易代采 | ✅ MVP + BPM |
| 融资 + 放款 Mock | ✅ EA-015 |
| 银行流水 / 清分 / 清分规则 | ✅ EA-016/017 |
| 仓储货权 | ✅ MVP |
| BI 经营看板 | ✅ EA-019/020 |
| 风险预警中心 | ✅ EA-021 |
| 统一待办/消息 | ✅ EA-022 |
| **审计日志中心** | ✅ **EA-023** |
| 数字债权凭证 | ⬜ **EA-024 下一步** |

### 质量与文档

| 文档 | 路径 |
|---|---|
| EA-022 验收 | `系统开发文档/EA-022_统一待办中心验收结果_20260527.md` |
| EA-023 验收 | `系统开发文档/EA-023_审计日志中心验收结果_20260527.md` |
| EA-024 简报 | `系统开发文档/EA-024_数字债权凭证MVP与Saga联调任务简报_20260527.md` |

## API 概览（已实现）

| 前缀 | 说明 |
|---|---|
| `/auth/*` | 登录、身份、权限列表 |
| `/customers/*` `/projects/*` `/pricing/*` `/trade/orders/*` | 主数据与贸易 |
| `/finance/applications/*` | 融资 + 放款 |
| `/accounts/*` | 流水、清分、清分规则 |
| `/warehouse/*` | 仓库、库存 |
| `/bpm/tasks/*` | 审批待办 |
| `/bi/*` | 经营看板 |
| `/risk/alerts/*` | 风险预警 |
| `/inbox/*` | 统一待办 |
| **`/audit/*`** | **审计日志查询（EA-023）** |
| `/files/*` `/ai/ocr/*` `/imports/excel/*` | 文件与 Mock 任务 |

## 回归测试

```powershell
cd backend/scf-server
mvn test "-Dtest=AuditCenterIntegrationTest,InboxCenterIntegrationTest,RiskAlertCenterIntegrationTest"
# 或
mvn -q -Dexec.mainClass=com.scf.EA019RegressionRunner exec:java
```

## 演示账号

| 账号 | 密码 | 角色 |
|---|---|---|
| platform_admin | Admin@123 | 平台（含 AUDIT_VIEW 全项目） |
| funding_user | Fund@123 | 资金方 |
| member_user | Member@123 | 成员企业 |
| warehouse_user | Wh@123 | 仓储 |

## 下一步优先级

1. **EA-024** 数字债权凭证 MVP + Saga 联调（见任务简报）
2. 权限拒绝写审计（EA-023 P2）
3. Maven CI + 浏览器冒烟

## 通用禁止事项

1. 不得绕过权限、状态机、幂等、审计
2. 不得无人值守执行放款、清分、兑付、解押、出库
3. 金融金额禁止浮点数
4. BPM 不得直接改业务表（须 Callback）
5. 不得物理删除金融/审计数据
