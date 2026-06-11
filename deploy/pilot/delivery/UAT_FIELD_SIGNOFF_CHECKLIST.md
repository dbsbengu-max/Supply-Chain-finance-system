# UAT 现场签字 Checklist（EA-052）

**场次**：________________  **日期**：________________  **环境**：Mock/试点 local

---

## A. 环境就绪

- [ ] PostgreSQL `:5432` 可达
- [ ] 后端 health UP（8080）
- [ ] 前端 dev UP（5173）
- [ ] 已打开 `/uat/acceptance`
- [ ] （可选）demo seed 已灌

## B. M1–M12 走查（与页面状态一致）

| ID | 模块 | 演示完成 | UAT 页状态 | 备注 |
|---|---|---|---|---|
| M1 | 登录/身份 | [ ] | pass / fail / skip | |
| M2 | 功能上线 | [ ] | pass / fail / skip | |
| M3 | 试点闭环 | [ ] | pass / fail / skip | |
| M4 | 客户/KYC | [ ] | pass / fail / skip | |
| M5 | 贸易代采 | [ ] | pass / fail / skip | |
| M6 | 仓储货权 | [ ] | pass / fail / skip | |
| M7 | 融资放款 | [ ] | pass / fail / skip | funding_user |
| M8 | 清分 | [ ] | pass / fail / skip | |
| M9 | 签章 | [ ] | pass / fail / skip | |
| M10 | 补偿池 | [ ] | pass / fail / skip | |
| M11 | BI 看板 | [ ] | pass / fail / skip | |
| M12 | 权限穿透 | [ ] | pass / fail / skip | member_user |

## C. 导出与归档

- [ ] 已从 `/uat/acceptance` **导出签字包** Markdown
- [ ] 已保存至 `ea052-uat-archive/<场次>/UAT_SIGNOFF_EXPORT.md`
- [ ] 已运行 `archive-ea052-uat.ps1`（或 `run-ea051-acceptance.ps1`）
- [ ] 已填写 `BUSINESS_ISSUES.md`
- [ ] （可选）关键截图已放入 `screenshots/`

## D. 签字

| 角色 | 姓名 | GO / NO-GO | 日期 | 签名 |
|---|---|---|---|---|
| 业务负责人 | | | | |
| 产品负责人 | | | | |
| 技术负责人 | | | | |
| 运维/实施 | | | | |

**Verdict**：________________ （GO trial / NO-GO / GO trial + 条件）

**条件说明**（若有）：_____________________________________________
