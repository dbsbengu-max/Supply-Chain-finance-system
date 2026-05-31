# Logback 配置挂载评估（EA-033）

## 问题

试点 prod 是否需要将 [`logback-spring.example.xml`](../logback-spring.example.xml) 打入 JAR，还是作为 **外部文件挂载**？

## 结论（试点推荐）

| 维度 |  classpath（打入 JAR） | **外部挂载（推荐）** |
|---|---|---|
| 改日志级别/路径 | 需重新发版 | 改文件 + 滚动重启 |
| 密钥/路径泄露风险 | 低 | 低（路径在宿主机） |
| K8s / VM | ConfigMap 需 rebuild | `SCF_LOGBACK_CONFIG=file:/etc/scf/logback-spring.xml` |
| 本地 dev | 默认 Spring 控制台即可 | 可不设置 |

**试点 prod：外部挂载 logback，不强制合入主工程 `src/main/resources`。**

## 落地方式

### 1. 环境变量（已在 `application-prod.yml`）

```yaml
logging:
  config: ${SCF_LOGBACK_CONFIG:classpath:logback-spring.xml}
```

- 未设置 `SCF_LOGBACK_CONFIG` → Spring Boot 默认 logback（当前 dev 行为）
- prod 设置 → `file:/etc/scf/logback-spring.xml`

### 2. VM 部署

```bash
sudo mkdir -p /etc/scf /var/log/scf
sudo cp deploy/pilot/logging/logback-spring.example.xml /etc/scf/logback-spring.xml
# 编辑 LOG_PATH → /var/log/scf
export SCF_LOGBACK_CONFIG=file:/etc/scf/logback-spring.xml
export SCF_LOG_PATH=/var/log/scf
```

### 3. Kubernetes

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: scf-logback
data:
  logback-spring.xml: |
    # paste from logback-spring.example.xml, adjust paths
---
env:
  - name: SCF_LOGBACK_CONFIG
    value: file:/etc/scf/logback-spring.xml
volumeMounts:
  - name: logback
    mountPath: /etc/scf
    readOnly: true
volumes:
  - name: logback
    configMap:
      name: scf-logback
```

### 4. 何时合入 JAR（EA-034+）

- 多环境仅差日志级别、无运维挂载能力时
- 需 JSON 结构化日志且版本与代码强绑定时

合入步骤：复制 example → `backend/scf-server/src/main/resources/logback-spring.xml`，保留 `springProfile prod/dev` 分支。

## 检查清单

- [ ] prod 设置 `SCF_LOGBACK_CONFIG` 或确认接受默认控制台日志（仅容器 stdout 采集时可行）
- [ ] 日志目录磁盘监控（告警 A-06）
- [ ] 日志不含 JWT / 密码 / 银行 token 明文

## Codex 下一轮

- 增加 JSON encoder（Logstash）样例
- 与集中日志 agent（Filebeat）字段对齐
