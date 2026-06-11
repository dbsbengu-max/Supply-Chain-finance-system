# 电子签章供应商字段映射模板（EA-045）

> 拿到供应商正式 API 文档后，按本节填写映射并同步到 `application-prod.yml` 的 `scf.contract.sign.http-provider` 配置。

## 1. 供应商信息

| 项 | 填写 |
|---|---|
| 供应商名称 | （例：XX 电子签） |
| provider_code | `ESIGN_HTTP` 或新建 `{Vendor}ContractSignProvider` |
| 文档版本 / 日期 | |
| 联调环境 base-url | |

## 2. 出站鉴权

| 平台配置项 | 供应商文档字段 | 说明 |
|---|---|---|
| `outbound-auth-mode` | | `HMAC_SHA256` / `RSA_SHA256` / `SM2` |
| `app-id` | AppId / clientId | |
| `app-secret` | AppSecret | HMAC 模式 |
| `private-key-pem` | 商户私钥 | RSA/SM2 模式 |
| `public-key-pem` | 平台公钥 | 验签联调（可选） |
| `platform-trace-header` | Request-Id / traceId 头 | 默认 `X-Request-Id`，值为平台 `task_id` |

### 2.1 签名串（平台默认 canonical）

```
{timestamp}\n{nonce}\n{requestBody}
```

| 模式 | 请求头 | 算法 |
|---|---|---|
| HMAC_SHA256 | X-Scf-App-Id, X-Scf-Timestamp, X-Scf-Nonce, X-Scf-Signature | HMAC-SHA256(appSecret, canonical) |
| RSA_SHA256 | 同上 | SHA256withRSA，Signature Base64 |
| SM2 | 同上 + X-Scf-Sign-Algorithm: SM3withSM2 | SM3withSM2，Signature Base64 |

若供应商 canonical 不同，应新建 `{Vendor}SignClient` 而非改通用 Adapter。

## 3. 发起签署 — 请求字段映射

平台 `SignRequestContext` → 供应商 JSON（配置路径：`http-provider.field-mapping.*`）

| 平台字段 | 默认 JSON 键 | 供应商字段名 | 类型 | 必填 | 备注 |
|---|---|---|---|---|---|
| taskId | task_id | | string | Y | 幂等键，同时作为 platform_trace_id 出站 |
| documentId | document_id | | string | Y | |
| fileId | file_id | | string | Y | 文件 URL 或 fileId |
| documentNo | document_no | | string | | |
| businessType | business_type | | string | | |
| businessId | business_id | | string | | |
| signers[] | signers | | array | Y | |
| signers.enterpriseId | enterprise_id | | string | Y | |
| signers.signerName | signer_name | | string | | |
| signers.signerRole | signer_role | | string | | |

### 3.1 YAML 示例（供应商字段名不同）

```yaml
scf:
  contract:
    sign:
      http-provider:
        field-mapping:
          task-id: bizTaskNo
          document-id: contractId
          file-id: fileUrl
          signers: signerList
          signer-enterprise-id: orgCode
          signer-name: userName
          signer-role: signRole
```

## 4. 发起签署 — 响应字段映射

| 平台读取配置 | 默认 JSON 键 | 供应商字段名 | 说明 |
|---|---|---|---|
| `response-external-ref-field` | external_sign_ref | flowId | 写入 task.external_sign_ref |
| `response-provider-status-field` | provider_status | | 优先 |
| `response-status-field` | status | | 备选 |
| `response-request-id-field` | request_id | | 写入 provider_request_id |
| `response-trace-id-field` | trace_id | | 写入 provider_trace_id |
| `vendor-request-id-header` | X-Vendor-Request-Id | | 响应头备选 |
| `vendor-trace-id-header` | X-Vendor-Trace-Id | | 响应头备选 |

### 4.1 状态映射

| 供应商状态 | 平台 providerStatus | 平台行为 |
|---|---|---|
| ACCEPTED / PENDING / SIGNING | PENDING_CALLBACK | 等待回调 |
| FAILED / REJECTED | SUBMIT_FAILED | 409，可重试 |
| SUCCESS / SIGNED | SIGNED | 少见同步成功 |

## 5. 主动查单 — 响应映射

| 平台读取 | 默认键 | 供应商键 |
|---|---|---|
| status | status | |
| signed_at | signed_at | finishTime |
| failure_reason | failure_reason | reason |

## 6. 入站回调（固定，勿改）

- URL: `POST /integrations/contracts/sign-callback`
- Body: `external_sign_ref`, `callback_status`, `signed_at`, `failure_reason`, `provider_code`
- 鉴权: `TIMESTAMP_NONCE_SIGNATURE`（生产）或 `TOKEN`（开发）

## 7. 错误码映射

在 `{Vendor}ErrorMapper` 或响应 `failure_reason` 中维护：

| 供应商 code | 平台 failure_reason | 可重试 |
|---|---|---|
| SIGN_TIMEOUT | 签署超时 | 是 |
| SIGN_REJECTED | 签署方拒签 | 否 |
| AUTH_FAILED | 鉴权失败 | 否 |
| NETWORK | 网络异常 | 是 |

## 8. 留痕字段（EA-045）

`tr_contract_sign_task` 持久化：

| 列 | 来源 |
|---|---|
| platform_trace_id | 出站 `X-Request-Id`（默认 = task_id） |
| provider_request_id | 响应头/体 request_id |
| provider_trace_id | 响应头/体 trace_id |
| provider_exchange_json | 供应商响应摘要（≤2000 字符） |

审计日志 `CONTRACT_SIGN_INITIATED` 同步写入上述 ID，便于与供应商工单对齐。

## 9. 沙箱证据包（EA-046）

字段映射填妥并 staging 配置生效后：

1. 复制 `deploy/pilot/.env.esign-sandbox.example` → `.env.esign-sandbox`
2. 将本节映射同步到 env / `application-prod.yml`
3. 执行 `run-ea046-sandbox-evidence.ps1`，归档 `ea046-*.json`
4. 将 `provider_request_id` / `provider_trace_id` 与供应商工单号写入联调记录表

详见 `docs/EA-046_Sandbox执行手册.md`。
