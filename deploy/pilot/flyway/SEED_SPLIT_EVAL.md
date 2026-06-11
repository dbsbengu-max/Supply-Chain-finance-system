# V1_1_004 Seed 拆分评估（EA-034）

## 1. 结论

| 项 | 决策 |
|---|---|
| **V1_1_004 Flyway 文件** | **不修改**（checksum 不可变） |
| **IAM / Demo 物理拆分** | ✅ `deploy/pilot/flyway/seed/seed_iam.sql` + `seed_demo_business.sql` |
| **可选应用** | `apply-seed-profile.ps1 -Profile iam|demo|full`（幂等） |
| **Manifest** | Flyway `V1_1_027__seed_profile_manifest.sql` 表 `sys_seed_manifest` |
| **试点 staging** | **保留 FULL**（004 已 migrate + UAT/smoke 依赖 demo） |
| **未来空业务 prod** | greenfield：`001–003` → `apply-seed -Profile iam` → `005–027`（跳过 004 需 EA-035 Flyway 策略） |

## 2. 拆分边界

### IAM（`seed_iam.sql`）

| 对象 | 说明 |
|---|---|
| sys_operator OP001 | 运营主体 |
| sys_project PJ001 | 试点项目 |
| sys_user U001–U004 | 四角色账号 |
| sys_role ×4 | 平台/资金/成员/仓库 |
| md_enterprise ×7 | **身份 FK 必需**（非纯 demo） |
| sys_user_identity ×4 | 默认身份 |

### Demo business（`seed_demo_business.sql`）

| 域 | 代表实体 |
|---|---|
| 主数据 | md_category, md_sku, fx_rate, pr_price_record |
| 交易 | tr_order ORD001, tr_order_item |
| 凭证/融资 | ar_receivable, dv_voucher, cr_credit, fn_finance_application FIN001 |
| 账户/清分 | acct_virtual_account, clearing_rule |
| 仓储 | wh_warehouse, wh_inventory, wh_inventory_lock |
| 物流 | lg_order, lg_node |
| BI | bi_metric ×3 |

### 不在 004、但依赖 demo 的后续 Flyway

| 版本 | 内容 |
|---|---|
| V1_1_016 | FIN_CLEAR_OK、ACC_FUNDING_001 等清分 demo |
| V1_1_006+ | 权限 seed（依赖 role，不依赖 ORD001） |

**IAM-only 环境限制：** 可登录、权限可用，但 **无** UAT 代采/融资/清分 demo 数据；smoke 部分路由可能空列表。

## 3. 环境矩阵

| 环境 | Seed 策略 | 命令 |
|---|---|---|
| 现有 dev/staging（已跑 004） | FULL（Flyway 004） | 无需 apply-seed |
| 现有 + 027 后 | manifest 自动标记 FULL | migrate 027 |
| 刷新 demo（staging） | IAM 已有，补 demo | `apply-seed-profile.ps1 -Profile demo` |
| Greenfield IAM-only（EA-035） | 跳过 004 | 待 Flyway 占位策略 |

## 4. 验证

```powershell
# 拆分文件幂等应用（staging 已有 004 时应全部 DO NOTHING conflict）
.\scripts\apply-seed-profile.ps1 -Profile full

# manifest
psql -c "SELECT * FROM scf.sys_seed_manifest"
```

## 5. EA-035 backlog

- Flyway `repair`/baseline 流程支持 **跳过 V1_1_004** 的 greenfield
- 将 V1_1_016 demo 并入 `seed_demo_business.sql` 维护
- IAM-only staging 环境的 smoke 子集
