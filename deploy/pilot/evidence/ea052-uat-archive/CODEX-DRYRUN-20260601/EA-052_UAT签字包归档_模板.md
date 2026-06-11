# EA-052 UAT 签字包归档

**日期**：2026-06-01（模板 — 现场完成后填写）  
**范围**：Mock/试点 M1–M12 现场验收  
**场次 ID**：`<YYYYMMDD-HHmmss>` → 见 `deploy/pilot/evidence/ea052-uat-archive/`

---

## 1. 签核结论

| 项 | 结论 |
|---|---|
| 自动化 A1–A4 | 待归档 / PASS / FAIL |
| 手动 M1–M12 | 待现场 / PASS / 部分 fail |
| Blocker 数量 | 0 |
| **Verdict** | **待签字** → GO trial / NO-GO / GO trial + 条件 |

> 口径：本签字 **不代表** 真实生产或 EA-048 vendor Go/No-Go。

---

## 2. M1–M12 汇总

> 正式明细以 `/uat/acceptance` 导出的 `UAT_SIGNOFF_EXPORT.md` 为准；下表为归档副本占位。

| ID | 模块 | 状态 | 备注 |
|---|---|---|---|
| M1 | 登录/身份 | pending | |
| M2 | 功能上线 | pending | |
| M3 | 试点闭环 | pending | |
| M4 | 客户/KYC | pending | |
| M5 | 贸易代采 | pending | |
| M6 | 仓储货权 | pending | |
| M7 | 融资放款 | pending | |
| M8 | 清分 | pending | |
| M9 | 签章 | pending | |
| M10 | 补偿池 | pending | |
| M11 | BI 看板 | pending | |
| M12 | 权限穿透 | pending | |

---

## 3. 自动化门禁（归档引用）

| ID | 判据 | 归档文件 |
|---|---|---|
| A1 | 171/171 | `ea051-*.summary.txt` |
| A2 | build PASS | 同上 |
| A3 | smoke 9/9 | 同上 |
| A4 | health UP | 同上 |

---

## 4. 业务问题清单

见同场次：`BUSINESS_ISSUES.md`

---

## 5. 签字区

| 角色 | 姓名 | 结论 | 日期 | 备注 |
|---|---|---|---|---|
| 业务负责人 | | GO / NO-GO | | |
| 产品负责人 | | GO / NO-GO | | |
| 技术负责人 | | GO / NO-GO | | |
| 运维/实施 | | GO / NO-GO | | |

---

## 6. 归档路径

```
deploy/pilot/evidence/ea052-uat-archive/<场次>/
  ARCHIVE_MANIFEST.md
  UAT_SIGNOFF_EXPORT.md
  BUSINESS_ISSUES.md
  ea051-*.summary.txt
  screenshots/          (optional)
```

---

## 7. 不在本次签字范围

同 EA-050 §6：真实 vendor、银行、生产灰度、UKey/SMS — 见 DEF-048 / BLOCKER-P。
