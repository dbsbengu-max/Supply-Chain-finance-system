# V1_1_004 演示数据 — 试点决策（EA-033）

**决策日期：** 2026-05-31  
**状态：** ✅ **保留（RETAIN）** 于试点 staging / prod

## 背景

`V1_1_004__seed_mock_data.sql` 写入：

- 运营主体 OP001、项目 PJ001
- 四角色用户 U001–U004（`mock_hash`）
- 演示企业、代采/融资/库存等 **业务演示数据**

试点 UAT（EA-029 24 条）、smoke（EA-031）、闭环导航（EA-030）均依赖该数据集。

## 选项对比

| 选项 | 优点 | 缺点 | 试点适用 |
|---|---|---|---|
| **A. 保留 004** | UAT/smoke 零额外准备；与文档账号一致 | prod 含虚构企业与订单 | ✅ **推荐** |
| B. 004 仅 IAM，业务空库 | prod 更“干净” | 需新 migration + 重写 UAT 数据准备 | ❌ 试点成本高 |
| C. 004 拆为 004a IAM + 004b demo | 环境可选加载 | 需 Flyway 重构与 CI 双轨 | 📋 EA-034 |

## 决策

**试点 staging / prod 保留 V1_1_004 全量 seed。**

约束：

1. **prod** 必须 `spring.profiles.active=prod` 且 `scf.dev.password-bootstrap=false` — 禁止 `DevDataInitializer` 写演示密码。
2. 上线前 **运维重置** 四用户密码（不得长期保留 `mock_hash`）。
3. 对外演示数据须标注「测试环境」；真实客户数据不得写入同一库。
4. 若监管或客户要求空业务库，在 **EA-034** 实施选项 C，并 bump Flyway `V1_1_027+`。

## 验收

- [ ] staging 跑 `verify-pilot-seed.ps1 -ArchiveDir evidence/staging` 并归档
- [ ] `platform_admin` 密码已重置（mock_hash 检查为 0 或已文档豁免）
- [ ] 业务方确认演示企业名称可接受出现在试点环境

## 相关

- [`flyway/PRODUCTION_STRATEGY.md`](./PRODUCTION_STRATEGY.md)
- [`docs/EA-033_试点生产安全加固与配置落地_20260531.md`](../../docs/EA-033_试点生产安全加固与配置落地_20260531.md)
