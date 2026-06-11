# 试点巡检 Checklist（EA-053）

**环境**：Mock/试点 local  
**周期**：每日（有试用）/ 每周（完整）

---

## A. 每日快速巡检（约 5 分钟）

```powershell
cd deploy\pilot\scripts
.\run-pilot-patrol.ps1 -Quick
```

- [ ] PostgreSQL `:5432` 可达
- [ ] 后端 health UP（8080）
- [ ] 前端可打开 http://127.0.0.1:5173/login
- [ ] `platform_admin` 可登录
- [ ] （可选）smoke 9/9 PASS

| 日期 | 巡检人 | 结果 PASS/FAIL | 备注 |
|---|---|---|---|
| | | | |

---

## B. 每周完整门禁（约 15–20 分钟）

```powershell
.\run-ea051-acceptance.ps1
```

- [ ] A1 回归 171/171
- [ ] A2 build PASS
- [ ] A3 smoke 9/9
- [ ] A4 health UP
- [ ] 证据归档至 `deploy/pilot/evidence/ea051-acceptance/`

| 周次 | 日期 | 结果 | 摘要文件 |
|---|---|---|---|
| W1 | | | |

---

## C. 演示前检查（有对外演示时）

- [ ] `apply-seed-profile.ps1 -Profile demo`（列表空时）
- [ ] 浏览器无痕窗口测登录
- [ ] 确认未误开 `SCF_CONTRACT_SIGN_CALLBACK_VERIFICATION_MODE=TIMESTAMP_NONCE_SIGNATURE` 于 dev shell（见 EA-051 索引）

---

## D. 异常升级

| 级别 | 动作 |
|---|---|
| P0 | 当日 hotfix + 重跑 A1–A4 |
| 非 P0 | 记入 `USER_FEEDBACK_LOG.md`，不紧急改代码 |
| 真实 vendor/银行诉求 | 转 EA-048 台账，不混 pilot PR |

---

## E. 签字（每周可选）

| 角色 | 日期 | 签名 |
|---|---|---|
| 运维/实施 | | |
| 技术 | | |
