# EA-045 真实签章供应商联调 Checklist

> 与 `ESIGN_VENDOR_FIELD_MAP.md`、回调重放脚本配合使用。每项联调完成后打勾并记录 requestId/traceId。

## A. 环境准备

- [ ] 获取供应商正式 API 文档（版本号：________）
- [ ] 配置 `SCF_CONTRACT_SIGN_HTTP_*` 环境变量（见 `application-prod.yml`）
- [ ] 确认 `outbound-auth-mode` 与文档一致（HMAC / RSA / SM2）
- [ ] 配置页 `/integrations/contracts/sign-config` 显示 `provider_connections.configured=true`
- [ ] 回调模式为 `TIMESTAMP_NONCE_SIGNATURE`，`callback-token` 已与供应商共享

## B. 出站 — 发起签署

- [ ] `POST /documents/center/{id}/sign` 返回 `SIGNING` + `external_sign_ref`
- [ ] 供应商侧收到请求，请求头含平台 trace（默认 `X-Request-Id` = task_id）
- [ ] 出站签名验签通过（HMAC / RSA / SM2 按文档）
- [ ] 请求 JSON 字段与 `field-mapping` 一致（对照 ESIGN_VENDOR_FIELD_MAP §3）
- [ ] DB `tr_contract_sign_task` 已写入：
  - [ ] `platform_trace_id`
  - [ ] `provider_request_id`
  - [ ] `provider_trace_id`
  - [ ] `provider_exchange_json`
- [ ] 审计日志 `CONTRACT_SIGN_INITIATED` 含上述 trace 字段

## C. 入站 — 异步回调

- [ ] 供应商回调 `POST /integrations/contracts/sign-callback`
- [ ] 回调头：`X-Contract-Sign-Timestamp` / `Nonce` / `Signature` / `X-Idempotency-Key`
- [ ] SUCCESS 后单证 `sign_status=SIGNED`
- [ ] 相同 `X-Idempotency-Key` 重放 → `idempotent_replay=true`
- [ ] 相同 nonce 第二次 → `403 AUTH_403`（防重放）
- [ ] 使用重放脚本验证：
  - PowerShell: `deploy/pilot/scripts/replay-contract-sign-callback.ps1 -ExternalSignRef <ref>`
  - Bash: `deploy/pilot/scripts/replay-contract-sign-callback.sh <ref> SUCCESS`

## D. 主动查单 / 对账

- [ ] `GET /integrations/contracts/sign/by-ref/{ref}` 可查到任务
- [ ] `POST .../query-status` 返回供应商终态
- [ ] Saga 补偿「主动查单」可用（需 `CONTRACT_SIGN_STATUS_QUERY`）
- [ ] 回调丢失场景：查单 SUCCESS 后可对账至 SIGNED

## E. 失败与重试

- [ ] 供应商返回 FAILED 回调 → 单证 FAILED，可 `POST .../sign/retry`
- [ ] 出站鉴权失败 → `502 CONTRACT_SIGN_502`，不入补偿池
- [ ] 验签失败回调 → `403`，不入补偿池
- [ ] 未知 external_sign_ref → `404` + 补偿池 `MANUAL_REQUIRED`

## F. 生产切换

- [ ] `default-provider` 从 MOCK 切至 ESIGN_HTTP（或专用 Provider）
- [ ] `http-provider.enabled=true`
- [ ] 密钥/私钥不入库、不进 Git（仅环境变量或密钥管理）
- [ ] 监控：按 `provider_trace_id` 可检索审计与任务记录

## G. 进入 EA-046 沙箱证据包

- [ ] 完成本节 A–F 后，使用 `deploy/pilot/.env.esign-sandbox.example` 配置真实沙箱
- [ ] 运行 `run-ea046-sandbox-evidence.ps1` 产出 `ea046-*.json` + summary
- [ ] 详见 `docs/EA-046_供应商Sandbox联调证据包Checklist.md`

## 联调记录模板

| 日期 | 场景 | external_sign_ref | provider_request_id | provider_trace_id | 结果 | 备注 |
|---|---|---|---|---|---|---|
| | 发起签署 | | | | | |
| | SUCCESS 回调 | | | | | |
| | 查单对账 | | | | | |
