# EA-046 供应商 Sandbox 联调证据归档

本目录存放 **真实 endpoint + 真实密钥** 沙箱闭环的证据，与 `docs/EA-046_*` 及 `run-ea046-sandbox-evidence.ps1` 配套。

## 目录约定

```
contract-sign-sandbox/
  README.md
  ea046-evidence.schema.json
  ea046-{YYYYMMDD-HHmmss}/
    ea046-{run_id}.json          # 机器可读证据（符合 schema）
    ea046-{run_id}.summary.md    # 人类可读摘要
    health.json
    config.json
    initiate-response.json
    lookup-response.json
    query-status-response.json
    callback-replay-response.json
    compensation-unknown-response.json
    saga-query-response.json
    db-export.log                # 可选：psql export-contract-sign-evidence.sql
```

## 生成方式

```powershell
cd deploy\pilot
copy .env.esign-sandbox.example .env.esign-sandbox
# 填写供应商沙箱 appId/secret、SCF_BASE_URL、登录密码、测试单证 ID

.\scripts\run-ea046-sandbox-evidence.ps1 -EnvFile .\.env.esign-sandbox
```

## 归档要求

- 不得提交含真实密钥的 `.env.esign-sandbox`、可用 accessToken/refreshToken 或 JSON 中的 secret 字段
- 提交仓库时仅保留 **脱敏** 的 `summary.md` 与 `ea046-*.json`（`config.json` 已脱敏）
- 供应商侧截图/requestId 可放同级 `vendor/` 子目录（PDF/PNG，不进 Git 亦可）

## 关联文档

- Checklist：`docs/EA-046_供应商Sandbox联调证据包Checklist.md`
- 执行手册：`docs/EA-046_Sandbox执行手册.md`
- 字段映射：`docs/ESIGN_VENDOR_FIELD_MAP.md`（填写 vendor 实例）
