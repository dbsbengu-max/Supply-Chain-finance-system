# EA-047 生产切换闸门 Checklist

| ID | 项 | PASS |
|---|---|---|
| E047-01 | EA-046 证据 verdict=PASS，run_id 已归档 | [ ] |
| E047-02 | 生产 `ESIGN_HTTP` configured=true（base_url / app_id / secret） | [ ] |
| E047-03 | 回调验签 `TIMESTAMP_NONCE_SIGNATURE` 已启用 | [ ] |
| E047-04 | 补偿池 `compensation_pool_enabled=true` | [ ] |
| E047-05 | 灰度模式与 allowlist/percent 已与业务确认 | [ ] |
| E047-06 | `run-ea047-prod-cutover-gate.ps1` 全部 PASS | [ ] |
| E047-07 | 回滚步骤已演练（MODE=OFF 或 percent=0） | [ ] |
| E047-08 | On-call 知晓 `production_rollout` config 字段 | [ ] |

**签字**：____________  **日期**：____________
