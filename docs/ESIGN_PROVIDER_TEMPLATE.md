# 电子签章供应商接入模板（ESIGN_PROVIDER_TEMPLATE）

> EA-041 真实外部服务接入前的契约基线。实现新供应商时复制本模板，替换 `{PROVIDER_CODE}` 与 `{VendorName}`。

## 1. 目标

- 与平台 `ContractSignProvider` 适配层对齐，**不修改**编排服务 / 回调 URL / 幂等键语义。
- 明确发起、查询、回调、失败码映射，便于 Codex 侧验签硬化与补偿池接入。

## 2. Java 实现清单

| 文件 | 说明 |
|---|---|
| `{VendorName}ContractSignProvider.java` | 实现 `ContractSignProvider` |
| `{VendorName}SignClient.java` | HTTP/SDK 封装（超时、重试仅用于**出站**） |
| `{VendorName}ErrorMapper.java` | 供应商错误码 → 平台 `failure_reason` |
| `application.yml` | `scf.contract.sign.providers.{code}.*` 配置项（EA-041+ 可 DB 化） |

### 2.1 Provider 接口契约

```java
@Component
public class ExampleContractSignProvider implements ContractSignProvider {

    public static final String CODE = "ESIGN_EXAMPLE";

    @Override
    public String providerCode() { return CODE; }

    @Override
    public String displayName() { return "示例电子签"; }

    @Override
    public String description() { return "对接 XX 电子签开放平台"; }

    @Override
    public SignRequestResult createSignRequest(SignRequestContext context) {
        // 1. 上传/引用 file_id 对应文件
        // 2. 创建签署流程，返回 external_sign_ref
        // 3. SUBMIT_FAILED → 平台记 FAILED 任务并抛 CONTRACT_SIGN_409
        // 4. 成功 → PENDING_CALLBACK + external_sign_ref
    }

    @Override
    public SignStatusResult querySignStatus(String externalSignRef) {
        // 主动查询（补偿池 / 运维手工触发用）
    }
}
```

注册方式：Spring `@Component` 自动注入 `ContractSignProviderRegistry`。

### 2.2 内置 HTTP Adapter（EA-044 · ESIGN_HTTP）

平台已提供通用 HTTP Adapter：`HttpContractSignProvider`（`provider_code=ESIGN_HTTP`）。

| 配置项 | 说明 |
|---|---|
| `scf.contract.sign.http-provider.enabled` | `true` 启用 |
| `base-url` / `create-path` / `status-path` | 供应商 API 地址 |
| `app-id` / `app-secret` | 出站 HMAC 密钥 |

出站签名：`HMAC-SHA256(appSecret, timestamp + "\n" + nonce + "\n" + body)`，请求头 `X-Scf-*`。

生产环境变量前缀：`SCF_CONTRACT_SIGN_HTTP_*`（见 `application-prod.yml`）。

## 3. 发起签署（Outbound）

### 3.1 平台 → 供应商

| 平台字段 | 供应商映射 |
|---|---|
| `context.taskId()` | 幂等业务键 / 扩展字段 |
| `context.documentId()` | 合同业务编号 |
| `context.fileId()` | 待签文件 URL 或 fileId |
| `context.signers()` | 签署方 enterprise_id / 姓名 / 角色 |

### 3.2 返回映射

| 供应商状态 | `SignRequestResult.providerStatus()` | 平台行为 |
|---|---|---|
| 已受理，待签署 | `PENDING_CALLBACK` | 任务 `PENDING_CALLBACK`，单证 `SIGNING` |
| 提交失败 | `SUBMIT_FAILED` | 任务 `FAILED`，单证 `SIGN_FAILED`，可重试 |
| 同步已完成（少见） | `SIGNED` | **不推荐**；仍应走回调以保证幂等 |

## 4. 异步回调（Inbound）

**URL（固定）：** `POST /integrations/contracts/sign-callback`

### 4.1 兼容鉴权（TOKEN）

| Header | 说明 |
|---|---|
| `X-Contract-Sign-Callback-Token` | 与 `scf.contract.sign.callback-token` 一致 |
| `X-Idempotency-Key` | 回调幂等键，建议 `{provider}-{external_sign_ref}-{status}` |

### 4.2 生产推荐鉴权（EA-041 · TIMESTAMP_NONCE_SIGNATURE）

| Header | 说明 |
|---|---|
| `X-Contract-Sign-Timestamp` | Unix 秒或 ISO-8601 |
| `X-Contract-Sign-Nonce` | 一次性随机串，窗口内不可重复 |
| `X-Contract-Sign-Signature` | `HMAC-SHA256(secret, timestamp + "\n" + nonce + "\n" + body)` |
| `X-Idempotency-Key` | 同上 |

时间窗：`scf.contract.sign.callback-signature-window-seconds`（默认 300s）。

### 4.3 回调 Body（平台标准）

```json
{
  "external_sign_ref": "VENDOR-FLOW-123",
  "callback_status": "SUCCESS",
  "signed_at": "2026-06-01T10:00:00Z",
  "failure_reason": null,
  "provider_code": "ESIGN_EXAMPLE"
}
```

| `callback_status` | 平台任务 | 单证 sign_status |
|---|---|---|
| `SUCCESS` | `SIGNED` | `SIGNED` |
| `FAILED` | `FAILED` | `FAILED` |

## 5. 失败码映射（示例）

| 供应商 code | 平台 failure_reason | 可重试 |
|---|---|---|
| `SIGN_TIMEOUT` | 签署超时 | 是 |
| `SIGN_REJECTED` | 签署方拒签 | 否 |
| `INVALID_SEAL` | 签章无效 | 否 |
| `NETWORK` | 供应商网络异常 | 是 |
| `UNKNOWN` | 未知错误（原文附带 vendor message） | 视运维 |

实现于 `{VendorName}ErrorMapper`，写入 `tr_contract_sign_task.failure_reason`（≤512 字符）。

## 6. 状态查询（补偿用）

`querySignStatus(externalSignRef)` 供以下场景调用（EA-041 后端）：

- 回调丢失超过 SLA
- 补偿池人工「主动查单」
- 乱序回调后的对账

返回 `SignStatusResult` 应与回调语义一致，**不得**绕过幂等直接改单证。

## 7. 补偿池接入点（EA-041 后端）

验签通过但业务处理失败的回调，应写入统一 Saga 补偿池 `biz_compensation_task`：

- `business_type = CONTRACT_SIGN_CALLBACK`
- `business_id = external_sign_ref`
- `compensation_type = CONTRACT_SIGN_CALLBACK_REVIEW`
- `compensation_status = MANUAL_REQUIRED`

| 场景 | reason_code | 建议动作 |
|---|---|---|
| 验签失败 | `AUTH_FAILED` | 直接拒绝，不入补偿池；安全日志留痕 |
| 未知 external_sign_ref | `DATA_404` | 挂起 + 对账 |
| 乱序（已完成又失败） | `STATE_409` | 保留终态 + 记录原始 payload |
| 字段不合法 | `VALID_400` | 联系供应商修正 payload 后重放或忽略 |
| 处理异常 | `PROCESS_ERROR` | 告警 + 人工复核 |

前端「签章配置」页 `compensation_pool_enabled=true` 时，可从配置页跳转到 Saga 运营中心的「签章回调复核」快捷筛选。

## 8. 联调检查清单

- [ ] 发起签署后 `external_sign_ref` 唯一
- [ ] SUCCESS 回调后单证 `SIGNED`，融资 `/documents/validate` 通过
- [ ] FAILED 后可 `POST .../sign/retry`，`retry_count` 递增
- [ ] 相同 `X-Idempotency-Key` 重放返回 `idempotent_replay=true`
- [ ] Token / 签名模式与配置页 `callback_verification_mode` 一致
- [ ] 供应商错误码均映射为可读 `failure_reason`

## 9. 参考

- EA-040 验收：`docs/EA-040_合同签章供应商适配层验收结果_20260531.md`
- Mock 实现：`MockContractSignProvider.java`
- 配置页：`/integrations/contracts/sign-config`
