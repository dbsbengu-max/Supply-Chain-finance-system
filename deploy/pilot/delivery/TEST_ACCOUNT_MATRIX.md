# 测试账号矩阵

**环境**：Mock / 试点（dev bootstrap 密码，**生产须轮换**）  
**灌数**：`apply-seed-profile.ps1 -Profile iam` 或 `full`

---

## 1. 账号总表

| 登录名 | 密码 | 角色/身份 | 典型项目 | 主要用途 |
|---|---|---|---|---|
| `platform_admin` | `Admin@123` | 平台管理员 | PJ001 | 全链路演示、UAT M1–M11、补偿池、签章、清分 |
| `funding_user` | `Fund@123` | 资金方 | PJ001 | 融资审批、放款（M7） |
| `member_user` | `Member@123` | 成员企业 | PJ001 | 代采/融资申请；权限穿透 M12 |
| `warehouse_user` | `Wh@123` | 仓储方 | PJ001 | 库存、仓单（扩展演示） |

> 密码来源：IAM seed / dev bootstrap。试点 prod 须 `password_hash ≠ mock_hash`。

---

## 2. 权限与菜单对照

| 能力 | platform_admin | funding_user | member_user | warehouse_user |
|---|---|---|---|---|
| 功能上线 / UAT 验收 | ✅ | — | — | — |
| 试点闭环 | ✅ | ✅ | ✅ | ✅ |
| 客户/KYC | ✅ | 读 | 读 | — |
| 贸易代采 | ✅ | 读 | ✅ 申请 | — |
| 库存货权 | ✅ | 读 | 读 | ✅ |
| 融资管理 | ✅ | ✅ 审批/放款 | ✅ 申请 | — |
| 清分中心（查看） | ✅ | ✅ | 读 | — |
| 清分执行 | ✅ | 视配置 | ❌ M12 | ❌ |
| 补偿池 | ✅ | — | — | — |
| 签章中心 | ✅ | — | 视配置 | — |
| BI 看板 | ✅ | 视配置 | — | — |
| 审计日志 | ✅ | — | — | — |

「视配置」= 以 seed 中 `sys_role_permission` 为准；验收以 M12 实测为准。

---

## 3. UAT 场景映射

| UAT ID | 推荐账号 | 入口 |
|---|---|---|
| M1 | platform_admin | `/login` |
| M2 | platform_admin | `/launch/hub` |
| M3 | platform_admin | `/pilot/closure` |
| M4 | platform_admin | `/customers` |
| M5 | platform_admin | `/agency-purchase/applications` |
| M6 | platform_admin | `/warehouse/inventories` |
| M7 | funding_user | `/finance/applications` |
| M8 | platform_admin | `/accounts/clearing` |
| M9 | platform_admin | `/documents/center` |
| M10 | platform_admin | `/saga/ops?tab=compensation` |
| M11 | platform_admin | `/bi/dashboard` |
| M12 | member_user | `/accounts/clearing` |

---

## 4. 核查 SQL

```powershell
cd deploy\pilot\scripts
psql -h 127.0.0.1 -U scf -d scf -f verify-pilot-seed.sql
```

---

## 5. 安全提醒

- 本矩阵仅用于 **Mock/试点 / UAT**，禁止原样用于公网生产。  
- 生产环境：独立账号、MFA、密码策略、密钥注入见 EA-033/EA-047。
