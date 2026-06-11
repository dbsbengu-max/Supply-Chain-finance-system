# EA-048 Go/No-Go 证据归档

外部**真实供应商** Sandbox 复跑 + 生产灰度前签核包。

## 目录结构

```
contract-sign-gonogo/
  ea048-YYYYMMDD-HHmmss/
    ea048-*.json          # Go/No-Go 机器可读 bundle
    ea048-*.summary.md    # 人类可读摘要 + 三方签字区
    env.redacted.template.md  # 脱敏 env 快照（不含密钥值）
```

关联 EA-046 证据：`../contract-sign-sandbox/ea046-*/`

## Schema

`ea048-gonogo.schema.json`

## 决策规则

| verdict | 含义 |
|---|---|
| **GO** | EA-046 PASS（真实 vendor）+ EA-047 pre-cutover PASS → 可部署 ALLOWLIST |
| **NO-GO** | 任一阶段 FAIL → **禁止**开启生产灰度 |
