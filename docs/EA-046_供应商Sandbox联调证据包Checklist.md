# EA-046 供应商 Sandbox 联调证据包 Checklist

> 在 EA-045「代码可联调」基础上，用**真实沙箱 endpoint / appId / 密钥 / 回调地址**跑通闭环，并归档可审计证据。  
> 自动化：`deploy/pilot/scripts/run-ea046-sandbox-evidence.ps1`  
> 证据目录：`deploy/pilot/evidence/contract-sign-sandbox/{run_id}/`

## 0. 前置（BLOCKER）

- [ ] 已合并含 EA-044/045 的 backend（Flyway ≥ `1.1.034`）
- [ ] 供应商沙箱账号：appId、密钥（HMAC 或 RSA/SM2 私钥）、API 文档版本号
- [ ] 供应商控制台已登记回调 URL：`{公网}/api/v1/integrations/contracts/sign-callback`
- [ ] 复制 `deploy/pilot/.env.esign-sandbox.example` → `.env.esign-sandbox`，无 `CHANGE_ME`
- [ ] 沙箱库存在测试单证 `SCF_ESIGN_DOCUMENT_ID`（`review_status=APPROVED`，`sign_status=PENDING`）
- [ ] `verify-contract-sign-config.ps1` 输出 PASS

## A. 发起签章（真实出站）

- [ ] `POST /documents/center/{id}/sign` → `SIGNING` + `external_sign_ref`
- [ ] 供应商沙箱收到请求，验签通过
- [ ] 供应商响应头/body 含 requestId/traceId（与 `vendor-*-header` / `response-*-field` 配置一致）
- [ ] DB `tr_contract_sign_task` 写入 `platform_trace_id`、`provider_request_id`、`provider_trace_id`
- [ ] 审计 `CONTRACT_SIGN_INITIATED` 含 trace 字段
- [ ] 证据：`initiate-response.json` + 供应商侧截图

## B. 查询状态（真实查单）

- [ ] `GET .../by-ref/{ref}` 命中任务
- [ ] `POST .../query-status` 返回供应商终态（`reconcile=false` 探测）
- [ ] 审计 `CONTRACT_SIGN_STATUS_QUERY` 可查
- [ ] 证据：`lookup-response.json`、`query-status-response.json`

## C. 回调验签

- [ ] 供应商真实回调 SUCCESS **或** 使用重放脚本（沙箱无推送时）：
  - `replay-contract-sign-callback.ps1 -ExternalSignRef <ref>`
- [ ] 验签模式 `TIMESTAMP_NONCE_SIGNATURE`，头：Timestamp / Nonce / Signature / Idempotency-Key
- [ ] SUCCESS 后单证 `sign_status=SIGNED`
- [ ] 相同 Idempotency-Key + 相同 payload + **新 nonce/新签名** 重放 → `idempotent_replay=true`
- [ ] 证据：`callback-replay-response.json`、`callback-idempotent-response.json`

## D. 补偿池 + Saga 重试

- [ ] 未知 `external_sign_ref` 回调 → `404 DATA_404` + 补偿池 `MANUAL_REQUIRED`
- [ ] Saga 列表可查到 `business_type=CONTRACT_SIGN_CALLBACK`
- [ ] `POST /saga/ops/compensation-tasks/{id}/retry` 可执行
- [ ] `POST .../query-sign-status` 主动查单（需 `CONTRACT_SIGN_STATUS_QUERY`）
- [ ] 证据：`compensation-unknown-response.json`、`saga-query-response.json`

## E. 用户侧重试（可选）

- [ ] 模拟 FAILED 回调 → 单证 `FAILED`
- [ ] `POST .../sign/retry` 重新发起，命中真实供应商
- [ ] `retry_count` 递增且 ≤ `max-retry-count`

## F. 审计 trace 留痕

- [ ] `export-contract-sign-evidence.sql` 导出 task / audit / compensation 行
- [ ] `platform_trace_id` 与 API `X-Request-Id` 可对照
- [ ] `provider_trace_id` 与供应商工单/requestId 可对照
- [ ] 证据：`db-export.log`（脱敏后归档）

## G. 证据包签核

- [ ] `ea046-{run_id}.json` 符合 `ea046-evidence.schema.json`，`verdict=PASS`
- [ ] `ea046-{run_id}.summary.md` 已填写
- [ ] 填写 `docs/EA-046_供应商Sandbox联调证据包验收结果_*.md`
- [ ] **不**将真实密钥提交 Git

## 联调记录（与 EA-045 扩展）

| 日期 | 场景 | external_sign_ref | provider_request_id | provider_trace_id | 供应商 ticket | 结果 |
|---|---|---|---|---|---|---|
| | 沙箱发起 | | | | | |
| | 查单 | | | | | |
| | 回调/重放 | | | | | |
| | 补偿+Saga | | | | | |
