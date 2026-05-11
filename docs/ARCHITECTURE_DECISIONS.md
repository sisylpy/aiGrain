# 第一阶段技术决策（Architecture Decisions）

本文档记录 **餐饮集团 AI 多智能体平台** 在重构与搭建阶段的基础设施选型与边界约定，与 `src/main/resources/PROJECT_AGENT_ARCHITECTURE.md`（产品/多 Agent 架构说明）配合使用。

**总原则**：Spring 生态默认、轻量可替换；优先 **架构清晰、链路可跑通、多智能体可观测**；不先引入过重中间件。上层 **Agent Graph / Tool / OutcomeReview / Export** 仅依赖接口，底层实现可随数据量与并发替换。

---

## ADR-001 知识库（第一阶段）

### 决策

- **不上** 独立向量库（如 Milvus）、**不强制** pgvector、**不先接** Elasticsearch。
- 文档元数据与 chunk **存 MySQL**；检索以 **MySQL 关键词** 为主：`LIKE`，有条件时可用 **`FULLTEXT`**。
- **预留接口、弱绑定供应商**：
  - `EmbeddingService` — 能力预留，第一阶段可不接具体供应商实现或提供 no-op/占位实现。
  - `VectorSearchService` — 接口预留，第一阶段可不落地向量检索实现。

### 第一阶段领域模型与服务（约定命名）

```text
KnowledgeDocument
KnowledgeChunk
KnowledgeSearchService      # MySQL 关键词检索实现
EmbeddingService            # 接口
VectorSearchService         # 接口
```

### 演进路径

数据量增长后：**Elasticsearch + 向量库**，在 **不改上层 Graph** 的前提下替换 `KnowledgeSearchService` / 接入 `VectorSearchService` / 实现 `EmbeddingService`。

---

## ADR-002 异步任务（第一阶段）

### 决策

- **不使用** MQ（RabbitMQ / Redis Stream / Kafka 等）。
- 采用：**Spring `@Async` + 数据库任务表 + 定时扫描（调度）**。

### 数据库与组件（约定）

表：

```text
gb_ai_async_job
```

服务与执行：

```text
AiAsyncJobService
AiJobWorker
AiJobScheduler           # 定时扫描待执行任务 / 补偿
```

### 流程

```text
创建任务 → 写入数据库 → @Async（或 Worker 抢占）执行 → 更新状态 → SSE / 轮询通知前端
```

### 演进路径

任务量增大后：**MQ 或 Redis Stream** 替换任务投递与消费；**表结构可保留**为任务状态与审计，或逐步迁移，**上层仅依赖 `AiAsyncJobService` 契约**。

---

## ADR-003 文件存储（第一阶段）

### 决策

- **默认本地存储**；**对象存储可插拔**。
- 返回前端 **不暴露** 真实磁盘路径或裸 OSS URL；统一经 **下载接口** 鉴权后提供。

### 接口与实现

```text
FileStorageService          # 抽象
LocalFileStorageService     # 默认实现
OssFileStorageService       # 预留（腾讯云 COS / 阿里云 OSS 等）
```

本地根路径建议可配置，例如：

```text
/data/ai-exports/
```

（实际键名以 `application` 配置为准。）

### 下载（与架构文档一致）

```text
GET /api/ai/export/download/{exportRecordId}
```

下载前必须走 **权限与组织范围校验**（见 ADR-004）。

---

## ADR-004 部署与多租户（第一阶段）

### 决策

- **单库多租户**；**不做** 多数据库多租户。
- 通过字段隔离组织与范围，典型字段包括但不限于：

```text
distributer_id
department_id
store_id
group_id
region_id
user_id
```

### 数据与 Run 的强制约定

以下实体/记录在持久化时应携带可归因的 **组织范围字段**（与业务表一致即可）：

- AI Run / Trace
- 报表与导出记录
- 异步任务
- 知识库文档与 chunk

### 权限组件（与 `PROJECT_AGENT_ARCHITECTURE.md` 对齐）

重点保证实现并 **在所有数据访问与下载前调用**：

```text
AiPermissionGuard
AiOrgScopeResolver
AiFieldPermissionService
```

---

## ADR-005 前端协议（第一阶段）

### 决策

- **HTTP REST + SSE**；**不上** WebSocket（第一阶段）。

### REST 路径（约定）

```text
POST /api/ai/runs
GET  /api/ai/runs/{runId}
GET  /api/ai/runs/{runId}/events        # SSE：过程事件流
POST /api/ai/runs/{runId}/stop

GET  /api/ai/export/list
GET  /api/ai/export/download/{exportRecordId}

GET  /api/ai/tasks
POST /api/ai/tasks
```

（具体包名、Controller 拆分以实现为准；路径前缀保持一致便于网关与前端。）

### SSE 事件名（与产品架构文档对齐）

第一阶段事件集合：

```text
run_started
agent_started
agent_finished
tool_started
tool_finished
agent_observation
review_started
review_finished
export_started
export_finished
answer_delta
run_finished
error
```

---

## ADR-006 鉴权（第一阶段）

### 决策

- **兼容现有登录体系**（已有 token / session 则沿用）。
- **不单独** 为第一阶段的 AI 模块重新发明一整套鉴权。

### 新接口必须从当前会话解析（最少）

```text
userId
departmentId
distributerId
role
permissions
```

并注入 `AiRunState` / `AiPermissionGuard` 所需上下文。

---

## ADR-007 与旧 Demo 接口的关系

### 决策

- `GbAiChatServiceImpl` 及 **旧 Chat 接口** 仅作 **业务与数据访问参考**。
- **第一阶段**：旧接口可 **保留不变**；**新能力** 一律走新接口前缀，例如：

```text
/api/ai/runs
/api/ai/export
/api/ai/tasks
```

- **禁止** 为迁就旧 Demo **破坏** 新架构分层（Router、Graph、Tool、Review、Export Center、Trace）。

---

## ADR-008 第一阶段开发栈摘要（对齐执行）

```text
Spring MVC + SSE
Spring @Async + DB Job（gb_ai_async_job + Scheduler）
MySQL：Trace / Report / Export / Task / Knowledge Chunk 等
Local File Storage 起步，FileStorageService 可替换 OSS
OSS / Elasticsearch / Vector DB / MQ 均通过接口预留，不影响 Agent Graph 上层
```

### 第一阶段希望跑通的端到端链路

```text
用户输入 → WorkspaceRouter → AgentGraph → Tool → OutcomeReview → SSE 返回 → Export 下载
```

---

## 修订与演进

- 替换 **ES / OSS / MQ / 向量库** 时：仅替换对应 **接口实现** 与配置，**不修改**：
  - `AgentNode` / `AiGraphRunner` 编排
  - Tool 契约与 Permission 调用点
  - OutcomeReview 与结构化输出契约
  - SSE 事件语义

有重大变更时在本文档追加 **新版本 ADR** 或修订记录（日期 + 简述）。
