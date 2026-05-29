# 供应链金融管理系统 (SCF System V1.1)

面向中国-东盟跨境贸易场景的供应链金融平台，覆盖客户准入、订单贸易、仓储货权、融资授信、数字债权凭证、清分引擎、BPM 审批与 Saga/Outbox 跨域事务。

## 技术栈

| 层级 | 技术 |
|---|---|
| 后端 | Java 17, Spring Boot 3.2, Spring Data JPA, Flyway, PostgreSQL 14+ |
| 前端 | Vue 3, TypeScript, Vite, Element Plus, Pinia |
| 契约 | OpenAPI 3.0 (`docs/openapi_v1_1_core.yaml`) |
| 容器 | Docker Compose (PostgreSQL) |

## 目录结构

```text
供应链金融管理系统/
├── backend/scf-server/     # Spring Boot 后端
├── frontend/scf-web/       # Vue 3 前端
├── docs/                   # 开发包文档引用与 OpenAPI
├── scripts/                # 启动与工具脚本
├── docker-compose.yml
├── AGENTS.md               # Agent 分工协作指南
└── README.md
```

## 快速启动

### 1. 启动数据库

```bash
docker compose up -d
```

### 2. 启动后端

```bash
cd backend/scf-server
./mvnw spring-boot:run
```

默认端口：`8080`，API 前缀：`/api/v1`

### 3. 启动前端

```bash
cd frontend/scf-web
npm install
npm run dev
```

默认端口：`5173`

### 4. Mock 登录账号

| 账号 | 密码 | 角色 |
|---|---|---|
| platform_admin | Admin@123 | 平台运营商 |
| funding_user | Fund@123 | 资金方 |
| member_user | Member@123 | 融资客户 |
| warehouse_user | Wh@123 | 仓库方 |

## 开发包文档

完整 PRD 与 V1.1 开发包位于上级目录：

`../系统开发文档/`

关键文档：

- 产品开发需求文档 V1.0
- 工程 Agent 任务拆解与统一简报 V1.1
- PRD 数据字典 / API 契约 / 权限矩阵 / ER 设计

## Agent 分工（EA 任务）

| 阶段 | 任务编号 | 说明 |
|---|---|---|
| 工程基线 | EA-001~003, EA-013 | DDL 审查、Flyway 拆分、数据字典一致性、权限映射 |
| 核心框架 | EA-004~008 | BPM、Saga/Outbox、幂等、清分引擎 |
| 核心业务 | EA-009~010 | 凭证流转、仓储货权 |
| 质量保障 | EA-014~017 | 权限穿透、UAT、PR Review |

详见 [AGENTS.md](./AGENTS.md)

## 模块开发顺序

1. IAM（登录、身份切换、权限）
2. 客户/KYC、项目配置
3. 订单贸易、价格管理
4. 仓储货权、数字债权凭证
5. 授信融资、清分
6. BPM 深度审批、BI、Saga 补偿台
