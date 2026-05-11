> 所有涉及 AI 多智能体、经营分析、采购、库存、菜品毛利、权限、组织范围、时间范围、多轮追问的改动，必须先阅读 `docs/AI_AGENT_DEVELOPMENT_GUIDE.md`。

> 任何涉及组织范围、门店、部门、集团汇总、权限范围、经营看板查询的改动，必须先阅读 `docs/DOMAIN_ORG_MODEL.md`。本项目中 `distributerId` 是集团/配送商主体 ID；`gb_department.gbDepartmentFatherId = 0` 的记录才是门店；子部门需要归一化到所属门店。

> 本 TODO 是多智能体平台重构的长期开发清单。每次 Agent 或人工修改代码后，都需要同步更新本文件状态，避免任务丢失。
>
> **协作规则**：以本文件为节拍器 — **先做 TODO 勾选项 → 勾选完成项**；有 **REST / 数据结构**变化改 **`docs/API_INTEGRATION.md`**；**权限 `admin`/`roleCode`/能力码** 变更改 **`docs/PERMISSION_MODEL.md`**；有 **SSE 字段/事件**变化改 **`docs/SSE_BACKEND_EVENT_CONTRACT.md`**；有 **新增或行为变更的单测** 在变更记录或 §八中 **补一句测试结果**。**不要**在未解除阶段冻结说明的前提下扩 ReportGraph/MarketingGraph。**阶段收口约定**：任一 **`*_path` / 业务主线** 在本文档勾选 **阶段收口** 或与 PR 等同款「链路可交付」结论时，**须同步** 更新 **`docs/API_INTEGRATION.md`**（至少覆盖该链对应的 **`answer_delta.data.*` 契约**、相关端点与验收说明，字段以 Java DTO 为准）。**当前**：**§「权限与组织范围基础版」第一、二波**（含 **`AiRunScopeIntersectService`**、**`ACCESS_MARKETING_WORKSPACE`** 工作台收口）已与单测对齐；下一阶段按顺序：**DeepSeek 主回答** → **`dish_sales_query` 性能** → 经营概览 → Report/Marketing **首链路**（仅缺陷修复与安全补丁可随时合入）。

# 多智能体平台开发 TODO

> 产品/架构说明见 `src/main/resources/PROJECT_AGENT_ARCHITECTURE.md`，第一阶段技术选型见 `docs/ARCHITECTURE_DECISIONS.md`。

---

## （可选）零、先于全量编译的仓库修复 backlog

**状态**：在 **JDK 17**（`JAVA_HOME` 指向 17，`mvn` 使用 `[debug release 17]`）下 **`mvn clean compile` → BUILD SUCCESS** 已确认。本节原为「收口编译」占位；若 CI 或其他机器仍报错，再按报错逐项打开下列核对。

- [x] ~~`DepartmentTypeCountRow` ↔ `AiQueryScope`~~ — 当前树可编过；若复现 getter 缺失，核对 Lombok 注解处理 / DTO 字段名
- [x] ~~Entity / Lombok / 字段 getter~~ — 同上
- [x] **`mvn test`**：已补充 `AiRunControllerTest`（standalone MockMvc，避免测试切片拉起无数据源的 Mapper）+ `AiAgentTraceSummarizeTest`；JDK 17 下 **BUILD SUCCESS**

---

## 一、第一阶段：基础骨架

- [x] Add `ai.core`（`AiWorkspaceMode`、`AiRunState`、`AgentNode`、`AiGraphRunner`）
- [x] Add `gateway` / `tool` / `security` 桩（`LlmGateway`、`ToolRegistry`、`AiPermissionGuard`）
- [x] Add trace（内存 `AiRunSession` + `AiRunSessionRegistry` + `AiSseEventPublisher`）
- [x] Add graph nodes（`WorkspaceRouterService` + Business 竖切节点 + `businessAgentNodes` Bean）
- [x] Add `AiRunService` + Spring `@Async` + `AiRunController`（`POST/GET` Run、SSE `events`、`stop`）
- [x] **`mvn clean compile` 全项目通过**（JDK 17；勿用 JDK8 跑 Maven，否则 `--release` 失败）

---

## 二、第二阶段：基础设施完善

- [x] 新增并维护 `docs/ARCHITECTURE_DECISIONS.md`（第一阶段技术选型已写入）
- [x] 文档中已定：Spring MVC + SSE、`@Async` + DB Job（后续表）、MySQL Trace/导出/任务/知识 chunk、本地存储起步、OSS/ES/向量/MQ 接口预留
- [x] **统一 REST 前缀与网关约定**：`application.properties` 已设 **`server.servlet.context-path=/api`**；控制器为 `@RequestMapping("ai/runs")`，完整路径 **`/api/ai/runs/**`**（见 `docs/API_INTEGRATION.md`）
- [x] SSE / REST 与前端契约：**`docs/API_INTEGRATION.md`**（扁平信封；终审路径表）
- [ ] **新链路不依赖旧版中心**：新业务走 `AiRunService`/`AiRunController`，不以 `GbAiChatServiceImpl` 为编排中心（可继续做静态检查或 ArchUnit 约束）
- [x] **`LlmGateway`（Agent Graph）**：`DeepSeekLlmGateway` 复用 `DeepSeekCompletionClient`；`ai.agent.llm.stub=true` 时回退 `PlaceholderLlmGateway`
- [x] **`gb_ai_agent_run` / `gb_ai_agent_step`**：DDL 见 `sql/gb_ai_agent_run_step.sql`；Entity/Mapper + `AiAgentTraceService`；Run 生命周期在 `AiRunService.executeRun`，节点边界在 `AiGraphRunner` 落 `gb_ai_agent_step`（异常仅打 WARN，无主链路中断）；`gb_ai_agent_observation` 仍待定

### 公共查询上下文 `AiResolvedQueryContext`

- [x] AiResolvedQueryContext 基础类已创建
- [x] AiResolvedQueryContextResolver 第一版已创建
- [x] AiResolvedQueryContext 接入 `AiRunState#resolvedQueryContext`
- [x] `AiRunService#startRun` 在进入 Graph 前生成并挂载 `resolvedQueryContext`（并打 INFO 摘要日志，不下发 SSE）
- [x] Graph 内各 Node 可通过 `AiRunState#getResolvedQueryContext()` 读取（业务 Tool 迁移后续迭代）
- [ ] **公共语义层（扩展）**：核销/出库词条、时间追问与 `BusinessTimeWindowNode` 对齐、进程外会话记忆、Composer 读 `effective*` 摘要

---

## 三、第三阶段：Agent Graph 核心能力

- [x] 完善 `AiRunState`（时间窗字符串、`dataPlanTools`、**`costInsightPath` / `purchaseCostInsightPath` / `couponCostInsightBlocked` / `costIntentConvergenceNote` / `businessOverviewPath`**、`costDiagnosisResult` / `businessOverviewResult` / `outcomeReviewStub`；与架构文档对齐的更多字段按需再加）
- [x] **经营概览支线**（第一版）：`BusinessDataPlannerNode` 话术/宽泛匹配 + `DEFAULT_BUSINESS_OVERVIEW_TOOLS`（4 Tool）+ **`BusinessOverviewAgentNode`** + Composer 汇入 DeepSeek（与成本链互斥：**成本关键词优先**）
- [x] **成本问句意图收敛（第一版）**：**`CostInsightIntentConvergence` + `BusinessDataPlannerNode`**（`purchaseCostInsightPath` / `couponCostInsightBlocked` / 门店问「集团成本」范围说明）；Composer 前缀；单测：**`BusinessDataPlannerCostIntentBranchTest`**、**`CostInsightIntentConvergenceTest`**、**`AiOrgScopeResolverTest`** 增补；详见 **`PERMISSION_MODEL.md` §7**。
- [ ] **`WorkspaceRouterAgent`**：由规则升级为 LLM 结构化路由 + `resources/ai/agents/workspace-router-agent.md`
- [x] 完善 **`BusinessGraph` 成本 + 经营主线**（同上节点 + **`BusinessOverviewAgentNode`**；Composer 经 **`DeepSeekLlmGateway`** 接主模型；**`ai.agent.llm.stub=true`** 时仅确定性摘要 **无外显占位句**）；**成本侧逻辑收口** 见 **§「成本分析主线收口 checklist」**。
- [ ] 完善 **`BusinessGraph`** 其它 Workspace 派发与 MemoryExtractor 等（按文档 §11 继续拆分）
- [ ] 新增并跑通 **`ReportGraph`** 基础流程（结构化 `AiReport` → 预览/导出衔接点）
- [ ] 新增并跑通 **`MarketingGraph`** 基础流程（与 Risk/Review 衔接）
- [ ] **`OutcomeReviewAgent` 基础版**：Rubric 加载、打分、revision 最多 2 次（§17）
- [ ] **`ToolRegistry` 真实工具**（持续推进）：✅ 已接入成本主线 5 套 `AiTool`（营业额/采购/核销/菜品/毛利推导）；❌ 全量业务能力覆盖、单测契约与归档仍待补齐
- [x] **`AiPermissionGuard` 与 Run 链路权限**：✅ **`BusinessToolExecutionNode`**、**`CostDiagnosisAgentNode`** 已接 **`evaluateToolInvocation` / `evaluateCostDiagnosisAgent`**；首轮 **`AiUserContext`/`AiOrgScope`** 挂载于 **`AiRunService`** + **`AiRunState`**；单测：`AiPermissionGuardTest`、`BusinessToolExecutionPermissionTest`、`CostDiagnosisPermissionDeniedTest` 等；**JDK 17 下 `mvn test`** 已通过。

---

## 权限与组织范围基础版（下一阶段优先）

> **顺位**：本节 **早于** DeepSeek 主回答扩写、`dish_sales_query` **性能攻关**、经营概览强化、ReportGraph/MarketingGraph。避免模型与更多 Graph 在**无边界**下拉取经营数据。
>
> **与现有实现对齐**：已有会话域 **`AiQueryScope`**、**`AiScopeResolver`**（对话锚点与子树）、**`AiQueryScopeAccess`**（`departmentUserId` 收窄）；本阶段增补 **Run 级** **`AiUserContext` / `AiOrgScope`**，与 **`AiPermissionGuard`**、Tool 映射贯通，最后在 **Graph 入口**与 **每次 Tool 执行前**双重校验。**不建**复杂 RBAC 管理后台。

### 工件与职责

| 工件 | 说明 |
|------|------|
| **`AiUserContext`** | `userId`、`sourceAdminRole`（`gb_du_admin`）、`roleCode`/`roleName`、`departmentFatherId`、组织锚点、`allowedStoreIds`、`permissions` — 见 **`docs/API_INTEGRATION.md`** 与 **`docs/PERMISSION_MODEL.md`** |
| **`AiOrgScope`** | `scopeType`：`GROUP` / `REGION` / `STORE` / `DEPARTMENT` / `DISTRIBUTER`；及对应 id、 **`storeIds`** 列表等 |
| **`AiUserContextResolver`**（或等价命名） | 从 **`userId`**（及必要时现有 Session / Header）装配 **`AiUserContext`**；可与现有用户/门店员工表 **`GbDepartmentUser*`** 等按需对接 |
| **`AiOrgScopeResolver`** | 由 **`AiUserContext`** + 请求可选锚点解析 **`AiOrgScope`**；可与 **`AiQueryScope`** 合并或事后 **求交**（第一版以保持越权不可得数为原则） |
| **`AiPermissionGuard`**（扩充） | 校验 **permission 字符串集** × **请求的部门/分销商/门店**是否在 **`AiOrgScope`** 内；**`canInvokeTool(AiRunState, ToolRequest)`** 替代恒 `true` |
| **`AiAnswerBoundary`** | 封装「允许的结论范围 / 可对用户解释的边界文案」，供 Composer 与 **SSE 可读提示** 使用（可无状态静态方法 + DTO） |

### 基础角色（来自 `gb_du_admin`，第一版代码表）

| admin（摘录） | AI `roleCode` | 语义（业务端） |
|---------------|----------------|----------------|
| 0 | `GROUP_MANAGER` | 集团管理端 |
| 11 | `STORE_MANAGER` | 门店管理端 |
| … | … | … |

**全量 admin、默认 permissions、组织范围、工作台、Tool 对照**见 **`docs/PERMISSION_MODEL.md`**。**过渡期合成**：`FINANCE_MANAGER`、`MARKETING_MANAGER`（不传 `admin`）。**`PURCHASE_MANAGER` 等伪角色**已废弃，请以 **`STORE_PURCHASER` / `GROUP_PURCHASER` / `REGION_PURCHASER`** 等与库表一致的角色为准。

映射到默认 **permission** 集合与 **scopeType** 已实现于 **`com.nongxinle.ai.mapping.AiRoleMapper`**。

### Tool → permission（第一版）

| Tool / Agent | Permission |
|----------------|------------|
| `RevenueQueryTool` | `VIEW_REVENUE` |
| `PurchaseQueryTool` | `VIEW_PURCHASE` |
| `StockReduceQueryTool` | `VIEW_STOCK` |
| `DishSalesQueryTool` | `VIEW_DISH_SALES` |
| `GrossMarginCalculatorTool` | `VIEW_COST` |
| `CostDiagnosisAgent`（结构化节点，非 Tool id） | `VIEW_COST` |
| （规划）**`ExportReportTool`** | `EXPORT_REPORT` |
| （规划）**Marketing 类 Agent** | `MANAGE_MARKETING` |

### 接入顺序（服务端）

```
POST /api/ai/runs
  → AiUserContextResolver
  → AiOrgScopeResolver
  → AiPermissionGuard（链路级：兜底；无上下文放行）
  → AiRunService → BusinessGraph
  → BusinessWorkspaceRouteNode：WorkspaceRouter + AiWorkspaceAccessGuard（MARKETING_GROWTH → ACCESS_MARKETING_WORKSPACE）
  → BusinessScopeIntersectNode：AiRunScopeIntersectService（请求子树 ∩ 锚点子树）
  → TimeWindow → DataPlanner …
  → 【每个 Tool 执行前】AiPermissionGuard.canInvokeTool（BusinessToolExecutionNode，已实现）
```

越权：**不抛未捕获 500**；沿用 **`AiSseEventPublisher.publishError`**（如 `TOOL_PERMISSION_DENIED`），并在 **`data` 内扩展结构化 `permissionDenied`**（契约见 **`docs/API_INTEGRATION.md`**）。

### checklist

- [x] 新增 **`AiUserContext`**（`com.nongxinle.ai.context`）
- [x] 新增 **`AiUserContextResolver`**（**`POST.userId` → `GbDepartmentUserService#getById` → `gb_du_admin` → `AiRoleMapper`** → `AiUserContext`；**过渡期** `FINANCE_MANAGER`/`MARKETING_MANAGER` 可走合成；**缺行**抛 `IllegalArgumentException`；单测用 **`AiDepartmentUserTestRows`** mock；**902** 建议库内为 **`STORE_MANAGER_APP`** 且 **`departmentId=100`** 以稳定门店锚点）
- [x] 新增 **`AiOrgScope`**
- [x] 新增 **`AiOrgScopeResolver`**（首版：Run 侧 **`AiOrgScope`**；在 **`BusinessScopeIntersectNode`** 收窄请求锚点后再次刷新快照，与会话域 **`AiQueryScope`** 互补——见 **`API_INTEGRATION.md`**）
- [x] 扩充 **`AiPermissionGuard`**（Tool + **`CostDiagnosisAgent`**；无上下文时放行以兼容仅用 bare `AiRunState` 的单测）
- [x] 新增 **`AiAnswerBoundary`**（Composer 前缀 + **`AiPermissionDenied` DTO**）
- [x] 定义 **业务 `admin` → AI `roleCode` + 默认 permissions**（**`AiRoleMapper`**；**`AiRoleCodes`**）
- [x] **Tool 权限映射**落代码（`AiPermissions` / `AiPermissionGuard`）
- [x] **`BusinessToolExecutionNode`**：**逐 Tool** 校验；被拒时 **`tool_finished.skipped`** + SSE **`error.data.permissionDenied`**，**跳过该 Tool** 继续后续（Composer 前缀提示受限）
- [x] **无权限**：结构化 **`permissionDenied`**（可读 `reason` / `suggestedScope` / `requiredPermission` / `subject`）
- [x] **`docs/PERMISSION_MODEL.md`**（权限单一说明）；**`docs/API_INTEGRATION.md`** / **`docs/SSE_BACKEND_EVENT_CONTRACT.md`**
- [x] **`mvn test`**：新增 **`AiUserContextResolverTest`**、**`AiOrgScopeResolverTest`**、**`AiPermissionGuardTest`**、**`BusinessToolExecutionPermissionTest`**、**`CostDiagnosisPermissionDeniedTest`**
- [x] **第二波（Java + 契约）**：**`AiRunScopeIntersectService`**（请求 dept 子树 ∩ 锚点子树，`AiRunState.scopeConvergenceNote`）；**`BusinessScopeIntersectNode`**（SSE：**`ScopeIntersect`**）；**`AiWorkspaceAccessGuard`** + **`ACCESS_MARKETING_WORKSPACE`**（**`WORKSPACE_ACCESS_DENIED`**）；单测：**`AiRunScopeIntersectServiceTest`**、**`AiWorkspaceAccessGuardTest`**；**`docs/API_INTEGRATION.md`** / **`docs/SSE_BACKEND_EVENT_CONTRACT.md`** 已写明解析顺序、`permissionDenied` / **`WORKSPACE_ACCESS_DENIED`** 示例；真机 SSE 仍以本机 **`curl`** 补帧（CI 无监听端口）。

### 完成标准（本阶段）

1. **成本主线**在上述 **三种角色**各跑通一至两条问句。
2. 不同 **`roleCode`** 下 **`AiOrgScope`**（及落库查询条件）可区分预期。
3. **越权**请求：无 SQL 宽泛拉取其它门店/其它区域数据。
4. **无权限**：SSE **`error`** 或约定帧内含 **`permissionDenied`**，可读 `reason` / `suggestedScope`。
5. **`docs/API_INTEGRATION.md`** / **`SSE_BACKEND_EVENT_CONTRACT.md`** 补充契约与 **`permissionDenied` / `WORKSPACE_ACCESS_DENIED`**；**本节 checklist** 第一、二波全勾 **`mvn test`** 绿灯。
6. **越权范围**：**`ScopeIntersect`** 将请求收窄至锚点子树或可解释回退（非 500）；营销工作台 **`MARKETING_GROWTH`** 无 **`ACCESS_MARKETING_WORKSPACE`** 时 **`error` + `run_finished.completed`**（与 §契约一致）。

权限基础版第一、二波勾选完成后，再回到：`DeepSeek` 主回答打磨 → **`dish_sales_query` 性能** → 经营概览深化 → ReportGraph → MarketingGraph。

---

## 四、第四阶段：导出中心 Export Center

- [ ] 新增 `AiExportService` / `ExportRequest` / `ExportResult` / `ExportFormat`（与架构文档 §22 一致）
- [ ] **`gb_ai_export_record` 表**设计与落库
- [ ] **`FileStorageService` + `LocalFileStorageService`**（可配置目录如 `/data/ai-exports/`）
- [ ] 预留 **`OssFileStorageService`**
- [ ] 实现 **`GET .../export/download/{exportRecordId}`**（或 ADR 约定路径）：下载前权限与组织校验，不暴露真实磁盘路径

---

## 五、第五阶段：异步任务

- [ ] **`gb_ai_async_job` 表**设计（与 ADR 一致）
- [ ] `AiAsyncJobService` / `AiJobWorker` / `AiJobScheduler`（`@Async` + 定时扫描占位，后续可换 MQ）
- [ ] 长任务状态：**pending → running → success / failed / cancelled**（命名可与表字段枚举统一）
- [ ] Run 完成后通过 SSE 或轮询协议通知前端（与 REST 契约对齐）

---

## 六、第六阶段：知识库基础能力

- [ ] 知识库 **文档表**、**chunk 表**（MySQL，元数据 + 文本切块）
- [ ] `KnowledgeDocument` / `KnowledgeChunk` 领域模型与持久化
- [ ] **`KnowledgeSearchService`**：第一阶段 MySQL 关键词（LIKE / FULLTEXT）
- [ ] **`EmbeddingService`**、**`VectorSearchService`** 接口占位（不强绑供应商）
- [ ] 预留后续 **Elasticsearch + 向量库** 替换实现路径

---

## 七、第七阶段：营销增长 Agent

- [ ] 独立 **`MarketingGraph`** 与工作空间派发（Router → Marketing 专用节点列表）
- [ ] **`CouponStrategyAgent` / `ComboPackageAgent` / `MarketingRiskAgent` / `WechatArticleWriterAgent`**（与架构 §13、§28 对齐）
- [ ] 结构体 **`AiCouponPlan` / `AiComboPlan`**（及必要 DTO）
- [ ] **营销方案必过 `OutcomeReviewAgent`**（Rubric：`resources/ai/outcomes/`）

---

## 八、第八阶段：测试与验证

- [x] **本机使用 JDK17+**跑 Maven（与 `pom.xml` 一致）
- [x] 执行 **`mvn clean compile`** 通过
- [x] 执行 **`mvn test`**（`AiRunController` 冒烟、Trace、`GrossMarginCalculatorToolMissingOutboundTest`、`CostDiagnosisAgentNodeDataIncompleteScenarioTest` 等；全量端到端 SSE 仍为手工/集成）
- [x] 验证 **`POST /api/ai/runs`** + **SSE 真机握手**（`GET /api/ai/runs/{runId}/events`）+ **`stop`**（按需）：见第八节变更记录 **2026-05-09 HTTP/SSE**。前后端契约见 **`docs/API_INTEGRATION.md`**。
- [x] SSE 信封字段：**扁平 JSON**（废止 `{type,payload}`），见 `docs/API_INTEGRATION.md`；`AiSseEventPublisher` 输出 `event`、`runId`、`timestamp`（上海偏移）、`status`、`displayText` 及按需 `agent`/`tool`/`data`/`text`；Business 占位链路含 **`run_started` → … → `answer_delta` → `run_finished`**
- [x] 成本主线 **HTTP/SSE 端到端**（占位 `departmentId`/`distributerId`）：已在对照清单下观察到 **BUSINESS_CHAT、本月窗口、五步 Tool、`costDiagnosis` 语义、`answer_delta` 路径**（详见 `docs/API_INTEGRATION.md`「成本分析主线验证样例」）；**须用真实主键在你们库内再验收数值与 mock=false**
- [ ] **`ai.trace.persist-enabled=true`** 且已执行 `sql/gb_ai_agent_run_step.sql` 后，人工验 **`gb_ai_agent_run`/`gb_ai_agent_step`**：**SUCCESS**/**FAILED**；**skipped 节点不入账**（联调机在私网 MySQL，本仓库 CI 不落库核验）

### 成本分析主线收口 checklist（✅）

与 **`POST /api/ai/runs` + SSE 成本链路** 对齐的逻辑与契约收尾（**本轮不扩** ReportGraph / MarketingGraph）；说明见 **`docs/API_INTEGRATION.md`**、**`docs/SSE_BACKEND_EVENT_CONTRACT.md`**。

- [x] **去掉 「LLM未接入」占位输出**：`PlaceholderLlmGateway` 空串；`StubAnswerComposerNode` 不向用户拼接噪话；生产 **`ai.agent.llm.stub=false`** 走 **`DeepSeekLlmGateway`**
- [x] **毛利率 100% 保护**：核销/出库侧全 0 且仍有营收口径时 **`grossMarginReliable:false`** + 文案 **`毛利率暂不可准确计算`**（单测：**`GrossMarginCalculatorToolMissingOutboundTest`**）
- [x] **核销缺失时结论克制**：`data_incomplete` 摘要 + `ok` 路径不再滥用「总体平稳」
- [x] **`riskLevel` 枚举含 `data_incomplete`**（与 `warning` / `high` 并列记入 API/SSE 契约）
- [x] **问句『帮我看本月成本怎么样』**：业务库整机复跑仍为人手验收项；门禁覆盖 **链路断点类数据截面**（**`CostDiagnosisAgentNodeDataIncompleteScenarioTest`**）
- [x] **变更记录**：本表 **`2026-05-10` 收口行**

**下一阶段（按顺序排期）**：**§权限第一、二波 ✅** → DeepSeek 主回答持续迭代 → **`dish_sales_query` 性能** → 经营概览链路打磨 → ReportGraph/MarketingGraph **首链路**。

- [ ] 验证 **异步任务**状态流转（第五阶段完成后）
- [ ] 验证 **导出文件下载**（第四阶段完成后）

---

## 「基础运行验证」清单（当前阶段收口）

以下为与 `PROJECT_AGENT_ARCHITECTURE`/ADR 对齐的**最小链路**核验项（不按此扩大 Agent 范围）：

- [x] `mvn test` 门禁可跑（含 Smoke、Trace、`GrossMarginCalculatorTool`、`CostDiagnosisAgentNode` **成本收口** 场景等）
- [x] **`AiRunController` / `AiRunService` / `AiGraphRunner` / `AiSseEventPublisher` / `ToolRegistry`** 等骨架 Bean 可被 Spring 扫描装配（请以本机数据源+端口就绪后 **`mvn spring-boot:run`** 为准）
- [x] **整机 HTTP/SSE**：`POST /api/ai/runs` → `runId` → `GET .../events` 收到 **`run_started`、`agent_started`/`finished`、`answer_delta`、`run_finished`** 等（见变更记录实测）
- [x] **整条成本分析链路可联调**：`answer_delta.data.costDiagnosis` 契约见 `docs/API_INTEGRATION.md`；占位 ID 端到端观测见同文档「成本分析主线验证样例」；**Trace 入账须在私网 DB 人手打开 `persist-enabled` 后核验**
- [x] **真实主键抽检（示例）**：`userId=1`、`departmentId=1`、`distributerId=2` — 定性结论见 **`docs/API_INTEGRATION.md`「真实主键抽检记录」**；修复前 **`revenue_query` `success:false`**
- [x] **`revenue_query` 查询异常**：根因为 **`GbAiDailyRevenueMapper.xml#selectStatsByDepartmentId`** 依赖 **`gb_ai_daily_revenue_gross_revenue`**，业务库无该生成列时报未知列 → 见 **`docs/API_INTEGRATION.md`**「失败根因与修复」；已改为 **`dine_in+takeout` 聚合**；**重启后**再用同 ID 复核五步 **`success:true`**

### 当前后台阶段冻结（成本主线联调期）

- **禁止扩大范围**：暂 **不** 新增 **ReportGraph**、**MarketingGraph** 等大 Graph。
- **成本主线**：后端逻辑、占位话术、毛利与 **`riskLevel`** 契约已完成 **收口**（见上文 **cost checklist**）；前端与业务库请以 **『帮我看本月成本怎么样』** 再跑一轮整机验收。
- **下一批放行**：**§权限与组织范围（第一、二波）✅** → **`dish_sales_query` 性能** → ReportGraph/MarketingGraph **首链路**（§性能 backlog）。

### 性能 backlog（联调期只记不排）

- [ ] 优化 **`dish_sales_query`** 耗时（占位 ID 时整机约 **55～60s**；**真实主键抽检** 时该步 **~60s**、整机 **~91～92s**，该 Tool 仍最重），目标将成本分析主线控制在 **10～20 秒内**。**联调阶段不马上大改**；客户端将 **SSE / HTTP read 超时调大**（建议 **≥120s**）即可。

---

## business_overview_path 接入旧版经营看板数据（验收清单）

目标问句：**「这个月经营怎么样」** / **「这个月生意怎么样」** / **「本月经营情况怎么样」**。Tool 序列（第一版）：**`business_overview_query` → `dish_sales_query` → `purchase_query` → `gross_margin_calculator`**（**不**再并行单薄 **`revenue_query`**）；**`gross_margin_calculator`** 在缺 **`revenue_query`** 时从 **`business_overview_query.data.totalRevenue`** 取营业额。

- [x] 新增 **`BusinessOverviewQueryTool`**（不扩展 **`RevenueQueryTool`**）
- [x] 复用 **`GbAiDailyRevenueDashboardServiceImpl#buildStatsDashboard`**（经 **`GbAiDailyRevenueService#getStatsByDepartmentId`**）
- [x] 返回旧版经营指标（中文 **`stats`**、**`mapperSupplement`**、**`anomalyHints`** 等）；缺项用「暂无」，禁止 Composer 编造
- [x] **`BusinessDataPlannerNode`**「经营怎么样 / 生意怎么样 / 本月经营情况」→ **`business_overview_path`**
- [x] **`BusinessOverviewAgentNode`**：基于 **`business_overview_query`** + 后继 Tools 结构化输出（**`riskLevel`、`keyMetrics`、`dashboardStatsCn`** 等）
- [x] **`StubAnswerComposerNode`**：硬性引用真实指标、`numericHeadlineText`；不暴露 **`dataPlanTools` / `toolResults` / `workspaceMode`**
- [x] 门店店长真机回归：「这个月经营怎么样」（只看本门店、**`run_finished.completed`**）— **2026-05-10 抽检通过**（有营业额/天数/日均/订单/客单等具体数）
- [x] 集团管理真机回归：「这个月经营怎么样」— **2026-05-10 真机通过**（见下方 **ResolvedQueryContext 收口项**）
- [x] **`docs/API_INTEGRATION.md`**：**`businessOverview`** 契约段落

验收：**`data.text` 含具体数字**（营业额、日均、订单、客单价等）；**无不根于数据的空泛建议**；SSE **`answer_delta.data.businessOverview`** 可供前端卡片；**不出现内部调试字段泄露**。

**`business_overview_path` + `AiResolvedQueryContext` 阶段收口（2026-05-10）**

- [x] `business_overview_path` 已迁移为读取 **`AiResolvedQueryContext`**（时间窗 + 组织范围）
- [x] 集团经营概览按 **`orgScope.visibleStores`** **门店级别**汇总展示（不将子部门算作独立门店）
- [x] 日营收底层 **`expandStoreRootsToDailyRevenueScopeIds`**（门店 + 直属子部门）取数，聚合与 covered/missing **rollup 回门店**
- [x] 真机验证：集团用户「这个月经营怎么样」通过（2 家门店、子部门不漏数、指标有具体数）

---

## 集团经营概览聚合口径

> **排查结论（2026-05-10，代码核对）**：`BusinessToolExecutionNode` → **`business_overview_query`** / **`revenue_query`** 的 **`departmentFatherId`** 来自 **`AiRunState.departmentId`**（即 **`POST` 请求的 `departmentId`**，收窄仅对**非集团广角**角色由 **`AiRunScopeIntersectService`** 写回锚点）。**`GbAiDailyRevenueController#getStats`** 路径参数文档化为 **「父部门/餐厅 ID」**（与日营收 `department_id`、核销 **`father_id`** 一致）；**画像** **`profileService.getByDepartmentId(departmentId)`**、**原始统计** **`getStatsByDepartmentId(departmentId, …)`**、**`buildStatsDashboard(departmentFatherId, profile, stats, …)`** 全部为 **单体门店经营父 department** 口径。**集团广角**分支 **`fillGroupWideScopeSnapshot`** 只填充 **`scope.resolvedDepartmentIds`** 子树快照，**不**把 Run 上的 **`departmentId`** 替换为某一子门店。  
> **已实现（收入侧 v1 rollup）**：**`GROUP_MANAGER` + `ARG_GROUP_WIDE_OVERVIEW_HINT`** 时，`business_overview_query` 对 **门店根列表** 展开为 **SQL 查账部门 id**（**`GbAiDailyRevenueService#expandStoreRootsToDailyRevenueScopeIds`**，含各店 **直属子部门**，与单店 **`getStatsByDepartmentId`** 一致），再 **`getGroupIncomeAggregateForDepartmentIds`**；展示与 **`coveredStores` / `dataMissingStores`** 仍以 **门店根** 为准。**`buildGroupWideIncomeFlattened`** 输出中文 **`stats`**。**不合并**核销/画像成本，利润率等为「不适用/—」。若 **`visibleStores` / resolved** 未下发，再回退 **`scope.resolvedDepartmentIds`** / 单锚点。

- [x] 明确旧版经营看板单店口径：`departmentFatherId` = 单体门店对应的父 department（与日营收 **`GET /stats/{departmentId}`** 一致）；`restaurant_profile` 按 **同 ID** 查
- [x] `GROUP_MANAGER_APP` 问经营概览时：**第一版仍传请求体 `departmentId`**；已 **不再**仅以「门店餐厅画像未配置」解释集团场景（见 **`BusinessOverviewQueryTool`** + **`ARG_GROUP_WIDE_OVERVIEW_HINT`**）
- [x] 解析集团下属可查门店/部门快照（沿用 **`AiRunState.scope.resolvedDepartmentIds`**，由 **`AiRunScopeIntersectService`** / Resolver 收口；**优先** **`AiResolvedQueryContext.orgScope.visibleStores`**）
- [x] **`business_overview_query` 收入侧**：集团广角 + 门店级 scope → **展开后 id 列表** SQL 聚合 + **`buildGroupWideIncomeFlattened`**（核销/利润率仍为非合并口径）；单店仍为 **`buildStatsDashboard`**
- [ ] 核销、库存、画像类指标：集团是否要 **逐项合并 / 分列单店**，待产品定稿后再改（当前为「不适用/—」提示）
- [x] 集团范围无法用单体画像解释时：**不**把根因话术窄化为「门店餐厅画像未配置」；产出 **可见部门列表的收入 SQL 聚合**（无数据再走失败提示）
- [x] 真机回归：`GROUP_MANAGER_APP`「这个月经营怎么样」（集团汇总营业额、仅门店抬头）；**单元测试**：**`GbAiDailyRevenueDashboardServiceGroupFlattenTest`** 等
- [x] 真机回归：`STORE_MANAGER_APP`「这个月经营怎么样」（见 **`business_overview_path` 验收清单**）

---

## 旧版 AI 回答资产迁移

> 资产盘点表见 **`docs/LEGACY_AI_ANSWER_ASSETS.md`**。原则：旧版满意回答 = 业务资产；新多智能体 = 编排框架；先把 **`GbAiDailyRevenueDashboardService`** / **`GbDepFoodBusinessInsightService`** / **`ai-skill-*.md`** 等价数据接回 Tool 与 Composer，再迭代 prompt。

- [x] 全局搜索旧版经营 / 成本 / 菜品分析相关代码（结论写入 `LEGACY_AI_ANSWER_ASSETS.md`）
- [x] 新增 `docs/LEGACY_AI_ANSWER_ASSETS.md`（持续与代码同步）
- [x] 整理旧版问题 → Service / DTO / 指标 / 回答模板（首版表格与差距说明已落盘）
- [x] `business_overview_path` 接入旧版经营数据（第一版：**`BusinessOverviewQueryTool`** + **`GbAiDailyRevenueDashboardService#buildStatsDashboard`** + SSE **`answer_delta.data.businessOverview`**）
- [ ] `costInsightPath` 对齐旧版成本回答（`ai-skill-cost.md` 与注入块等价信息）
- [x] `dish_profit_path` 接入旧版菜品毛利分析（`GbDepFoodBusinessInsightService` / 看板口径；**`DishProfitAnalysisTool` + `DishProfitAgentNode` + Composer**）
- [ ] DeepSeek prompt 改为基于 `toolResults` 总结，禁止空泛建议、禁止编造未提供数字（经营链：`StubAnswerComposerNode` 已加硬性摘要与 `business_overview_query` 优先引用；占位 LLM 网关仍受此约束表述）
- [x] 真机回归：「这个月经营怎么样」— **店长** ✅、**集团** ✅（见 **`business_overview_path` ResolvedQueryContext 收口项** 与 **§集团经营概览聚合口径**）
- [ ] 真机回归：「本月成本怎么样」
- [x] 真机回归：「菜品毛利怎么样」— **集团** ✅（见下 **`dish_profit_path` 阶段收口**）；门店焦点菜 / 采购·库管拒答等扩展用例仍见 checklist

---

## dish_profit_path 菜品毛利分析

- [x] 查找旧版菜品毛利 / 菜品经营分析 Service / DTO / Prompt
- [x] 在 `docs/LEGACY_AI_ANSWER_ASSETS.md` 增加菜品毛利资产清单（§菜品毛利 / 菜品分析旧版资产）
- [x] 新增 **`DishProfitAnalysisTool`**（`dish_profit_analysis`，复用 `GbDepFoodBusinessInsightService#buildInsight`）
- [x] 将「菜品毛利 / 菜品分析 / 哪些菜赚钱 / 哪些菜亏钱」等路由到 **`dish_profit_path`**（先于泛泛成本「毛利」关键词）
- [x] 返回菜品销售额、理论成本、实际成本、毛利额、毛利率（portfolio + 分项 brief）
- [x] 返回毛利较好菜品、低毛利菜品、异常菜品（`AiDishProfitOverviewResult`）
- [x] **`answer_delta.data.dishProfitOverview`** 返回结构化数据
- [x] **`StubAnswerComposerNode`** 基于 **`dishProfitOverview`** 生成老板可读答复（+ 确定性 fallback）
- **强制规则（与 `DOMAIN_ORG_MODEL.md` §11 对齐）**：集团用户的 `departmentId` 通常是管理部门，不是门店。所有集团范围查询，包括经营概览、菜品毛利、库存、采购、报表，都必须优先根据 `distributerId` 找集团下 `gbDepartmentFatherId=0` 的门店列表，再按门店汇总，不允许直接把集团用户 `departmentId` 当门店 ID。
- [x] **`dish_profit_path` 读取 `AiResolvedQueryContext`**：`timeWindow` 驱动统计区间；集团广角 **`orgScope.visibleStores`** 驱动范围（**`ARG_RESOLVED_DEPARTMENT_IDS`** 为 **门店根经 `expandStoreRootsToDailyRevenueScopeIds` 展开后的查数 id**，与经营概览日营收一致；**菜谱合并**仅 **可见门店根**，集团展示口径仍为门店/集团聚合）
- [x] **菜名**：集团合并 `gb_dep_food` 时 **同名 `foodId` 优先保留非空 `gbDfFoodName` 行**，避免汇总行菜名为空

**`dish_profit_path` 阶段收口（2026-05-10）**

- [x] `dish_profit_path` 已迁移为读取 **`AiResolvedQueryContext`**
- [x] 集团菜品毛利按 **`visibleStores`** 门店范围汇总
- [x] 菜品名称真实关联
- [x] 成本缺失导致的 100% 毛利菜已归入「成本数据不完整」
- [x] **`answer_delta.data.dishProfitOverview`** 契约已写入 **`docs/API_INTEGRATION.md`**
- [x] 真机验证：集团用户「这个月菜品毛利怎么样」通过

- [ ] **真机扩展**：门店「水煮鱼毛利怎么样」等；采购/库管拒答话术

---

## warehouse_stock_overview_path 库房库存概览

> **下一迭代（与 `DOMAIN_ORG_MODEL` / 菜品毛利对齐）**：库存 Tool / Agent 须读取 **`AiRunState.getResolvedQueryContext()`**；集团用户默认按 **`orgScope.visibleStores`** / **`visibleWarehouses`** 汇总；若底层数据挂在子部门，可技术展开取数，但最终展示与统计口径须回到**门店 / 库房**级（见本轮产品约定）。

- [x] 查找旧版库存查询 / 库房库存相关 Service / DTO / Controller（结论见 **`docs/LEGACY_AI_ANSWER_ASSETS.md` §库房库存查询旧版资产**）
- [x] 在 `docs/LEGACY_AI_ANSWER_ASSETS.md` 增加库存资产清单
- [x] 新增 **`WarehouseStockOverviewTool`**（`warehouse_stock_overview`）
- [x] admin=3 问「经营怎么样」时收敛到 **`warehouse_stock_overview_path`**（Planner 已接入；工具链为单一聚合 Tool）
- [x] 返回当前库房库存商品种数、库存金额、库存重量（及批次行数）
- [x] 返回统计区间内入库、核销分型（出品/损耗/报损/退货）及合计
- [x] 返回库存偏低 / 积压偏高 / 早于统计月起始仍有剩余批次（启发式预警列表）
- [x] **`answer_delta.data.warehouseOverview`** 结构化（Composer 从 Tool 信封写入 RunState）
- [x] Composer 基于 **`warehouseOverview`** 生成库管员可读答复（LLM 上下文 + 确定性 fallback）
- [x] **集团管理（`GROUP_MANAGER`）**：「库存怎么样」等开放式库存问法 → **`GROUP_WAREHOUSE_STOCK_OVERVIEW`**（`groupWarehouseStockOverview=true`，Tool 入参 **`groupWarehouseStockAggregation`** + **`resolvedDepartmentIds`**）；按分销户下门店根（`father_id=0`）逐店聚合合并；**`warehouseOverview`** 含 **`scopeType=GROUP`**、`visibleStoreCount` / `dataAvailableStoreCount` / `dataMissingStoreCount`、`coveredStores` / `dataMissingStores`；禁止反问指定门店（见 **`DOMAIN_ORG_MODEL.md` §12**）

**阶段收口（2026-05-10，库存链路真机）**

- [x] `warehouse_stock_overview_path` 已迁移为读取 **`AiResolvedQueryContext`**
- [x] 集团库存按 **`visibleStores`** / **`visibleWarehouses`** 范围汇总
- [x] 店长只看本门店库存
- [x] 库管员只看本人库房/所属门店库存
- [x] 库存回答已区分低库存、库存偏高、早入库待盘点
- [x] 真机验证：集团 / 店长 / 库管员库存查询通过
- [x] **`answer_delta.data.warehouseOverview`** 契约已写入 **`docs/API_INTEGRATION.md`**

---

## `purchase_overview_path` 采购入库概览

**阶段收口（2026-05-10，采购链路真机）**

- [x] `purchase_overview_path` 已迁移为读取 AiResolvedQueryContext
- [x] 集团采购按 visibleStores 门店范围汇总
- [x] 门店采购员只能查看采购 / 核销视角
- [x] 店长只看本门店采购
- [x] 库管员只看库房 / 所属门店入库视角
- [x] 集团采购已修复门店根 + 子部门取数并 rollup 回门店
- [x] 真机验证：集团 / 采购员 / 店长 / 库管员采购查询通过
- [x] `answer_delta.data.purchaseOverview` 契约已写入 docs/API_INTEGRATION.md
- [x] **2026-05-11 采购「老板口径」**：总笔数+总金额；**不再下发/讲述总重量**；**采购方式**（`gb_DPG_purchase_type`：1 自采 / 5 供货商订货 / 其它合并，借鉴 `GbAiChatServiceImpl`+`GbConstants.PurchaseOrderType`，与 **`queryGbPurchaseGoodsCount`** 同 join + `dayuStatus=2`+`typeNotEqual=9`）在分项与总笔数、总金额对账通过后输出；**商品次数/金额 Top** 按名称+标准名合并多 `dis_goods_id`；Composer/`API_INTEGRATION` 同步。
- [ ] **后续**：若生产环境长期出现 `purchaseMethodNote`（分项对账失败），需抽样核对采购行 `purchase_type`/nx 与 `COUNT/SUM`、批次 join 是否一致。（采购方式已与旧版 `appendPurchaseSupplyMixSummary` 对齐。）

## 多轮上下文追问 FollowUpIntentResolver

以下为产品 checklist（与设计对话一致）；与上一段工程状态合并阅读。

- [ ] 保存上一轮 `lastIntent` / `lastPath` / `lastTopic` / `lastTimeWindow` / `lastScope`：**当前**已实现进程内快照 `effectiveQuestion` + `FollowUpPathKind`（等价 path/topic）；**未**单列持久化「结构体 lastScope / lastTimeWindow」（时间窗仍可由扩写后的问句再走 `AiUserQueryTimeWindowResolver` 解析）。
- [x] 识别「这个月呢 / 本月呢 / 那上个月呢 / 换成本月」等含**显式时间口语**的短句追问。
- [x] 追问通过扩写 `normalizedUserInput` 默认继承上一轮话题，仅替换首轮问句中出现的**首个**时间用语。
- [x] 上一轮 `DISH_PROFIT`（`dish_profit_path`）时，追问继续走 **`dish_profit_path`**（经 Planner 再走识别）。
- [x] `BUSINESS_OVERVIEW` / `WAREHOUSE_STOCK` / `COST` / `PURCHASE_COST` 同理。
- [x] 追问命中后对明显「切换域」的子串（如在菜品毛利链路问「库存」「采购」）**不继承**，避免插队。
- [ ] **`intent` JSON / Trace 回填**：表中 `intent MEDIUMTEXT` 已预留升级脚本；**尚未**在每轮完结时写入（仅存内存）。
- [ ] Composer **环比**：相对上一轮的回答对比（待定）。
- [ ] **真机回归**：「上个月菜品利润怎么样？」→「这个月呢？」；不出现 `workspaceMode` / `dataPlanTools` / `toolResults`；`run_finished.completed`。

**工程已实现（本节上方）**：`AiFollowUpIntentSnapshot` + `AiFollowUpConversationMemory`、`FollowUpIntentResolveService`、`BusinessFollowUpIntentResolveNode`、`AiRunService` 成功后 `remember`。

## 变更记录

| 日期 | 说明 |
|------|------|
| 2026-05-11 | **公共查询语义层（v1 骨架）**：`docs/AI_QUERY_SEMANTIC_LEXICON.md`（采购词 + 供货商排行追问）；**`AiConversationTurnMemory` / `AiFollowUpResolution` / `AiConversationMemoryService` / `AiFollowUpResolver`**；**`AiResolvedQueryContext`** 扩展 `previousTurn`、`followUpResolution`、`effective*`；**`AiResolvedQueryContextResolver`** 合并追问、采购结构化补 path、按上一轮 `visibleStoreIds` 收窄集团范围；**`AiRunService`** 扩写 `normalizedUserInput`、`rememberCompletedTurn`、followUp 日志；**`FollowUpPathKind.PURCHASE_OVERVIEW`** + **`FollowUpIntentResolveService`** 早退避免与 Resolver 重复扩写；**`BusinessDataPlannerNode`** 读解析态 path；**`PurchaseOverviewTool`**：`purchaseSourceFocus`、`-1` →「自采」、自采聚焦时清空 `topSuppliers`。**后续**：核销词入库、LLM 追问、Trace 写入 `intent`。 |
| 2026-05-10 | **`purchase_overview_path` 文案与契约收口**：**`PurchaseOverviewTool`** 重量带 **斤**、集团 **`storeCoverageSummary`**、供货商占位名、**`GbDistributerPurchaseGoodsMapper`** Supplier SQL；**`BusinessToolExecutionNode.buildPurchaseQueryScopeBanner`** 集团/采购员/店长/库管开篇；**`StubAnswerComposerNode`** 采购 Composer 与核销全 0 简写；**`docs/API_INTEGRATION.md`** 增补 **`answer_delta.data.purchaseOverview`**；**`TODO_MULTI_AGENT.md`** §purchase_overview_path 勾选。 |
| 2026-05-10 | **库房库存链路阶段收口**：库存 Composer 按 `GROUP_MANAGER` / `STORE_MANAGER` / 库管 / 采购等注入「称谓与开篇」指令，并对 LLM 输出剥离不当「店长」起首；**`docs/API_INTEGRATION.md`** 增补 **`answer_delta.data.warehouseOverview`** 契约（字段以 `WarehouseStockOverviewTool` 为准）；**`TODO_MULTI_AGENT.md`** §warehouse_stock_overview_path 勾选真机与迁移项。 |
| 2026-05-10 | **库房库存概览链路**：**`WarehouseStockOverviewTool`**（`warehouse_stock_overview`）聚合库存种数/金额/重量、区间内入库、核销分型及启发式预警列表；**`warehouse_stock_overview_path`** 工具链改为单一 Tool；**`BusinessToolExecutionNode`** 为 **`stock_query`** / **`warehouse_stock_overview`** 注入 **`disId`**；文档 **`LEGACY_AI_ANSWER_ASSETS.md`** §库房库存、`TODO` §warehouse_stock_overview_path。 |
| 2026-05-09 | 初版：整理已落地骨架与后续阶段待办 |
| 2026-05-09 | **整仓 `mvn clean compile` 在 JDK 17 下 BUILD SUCCESS**；零节与第一阶段编译项勾选收口；第八阶段前两项勾选完成 |
| 2026-05-09 | **基础运行验证**：`mvn test` 通过；Trace 库表 SQL + Run/Step 写库接线；SSE 信封与路由节点展示文案对齐前端示例；`/api` 前缀与端口占用记入待办 |
| 2026-05-09 | **HTTP/SSE 真机握手**：见 `docs/API_INTEGRATION.md`。端口 **8090**（本机当前运行）；注意 Maven 勿用逗号把 `spring.profiles.active=local,--server.port=…` 拼在一起，否则会误启用名为 `--server.port=8091` 的 profile。命令示例：`curl -s -X POST http://localhost:8090/api/ai/runs -H 'Content-Type: application/json' -d '{"userId":1,"message":"联调验证"}'`；`curl -Ns http://localhost:8090/api/ai/runs/<runId>/events`。SSE 含 run_started → 多轮 agent_started/finished（含 WorkspaceRouterAgent、Echo 工具链路、AnswerComposerStub、OutcomeReviewAgent）→ answer_delta（含 data.text）→ run_finished。激活 profile `local` 时 `ai.trace.persist-enabled=false`，本验证未写 Trace 表；建表 DDL 见 `sql/gb_ai_agent_run_step.sql`，生产persist=true可验落库。 |
| 2026-05-09 | **BusinessGraph 成本第一版**：`TimeWindow → DataPlanner → ToolExecution → CostDiagnosisAgent → review_started/finished → AnswerComposerNode`；`answer_delta.data.costDiagnosis`；`POST /api/ai/runs/{runId}/stop` 写入 `API_INTEGRATION` 含 curl。示例问句「帮我看本月成本怎么样」：`POST` body 建议带 `departmentId`（父部门）、`distributerId`；成本类关键词见 `BusinessDataPlannerNode`。旧 **Echo** 竖切从默认 `businessAgentNodes` 移除。 |
| 2026-05-09 | **成本主线验收文档**：`docs/API_INTEGRATION.md` 增补 **`costDiagnosis` 稳定契约**、**验证样例**、**Trace 入账规则**/核验 SQL/Maven `--ai.trace.persist-enabled=true`。占位 ID 端到端已通过；真实主键与 Trace 入库须在业务库人手完成。前端可 **`VITE_USE_MOCK=false`** 只联调本条链路。 |
| 2026-05-09 | **阶段冻结 + 联调约定**：后台不扩 ReportGraph/MarketingGraph；TODO 增加 **`dish_sales_query` 性能项**（目标 10～20s，联调期仅调大超时）；API 文档强调三端点稳定与 **勿依赖 `answerPreview`**。 |
| 2026-05-09 | **真实主键 SSE 抽检**：`departmentId=1`/`distributerId=2`/`userId=1` 跑通成本主线；**`revenue_query` 失败**（`query_failed` 路径）已记入 `API_INTEGRATION`；`dish` **~60s**、整机 **~92s**；`costDiagnosis` 结构完整、`needMoreData=false`。 |
| 2026-05-10 | **`revenue_query` 修复**：`selectStatsByDepartmentId` 去除对 **`gb_ai_daily_revenue_gross_revenue`** 硬依赖，改 **`day_gross`=堂食+外卖**；`RevenueQueryTool` 日志带日期区间。未重启进程时复测仍可能失败；**重启后**同参验收五步全绿，新 `runId` 由环境记录。 |
| 2026-05-10 | **成本主线逻辑收口**：废止外显 **`(LLM未接入)...`**（stub 时仅确定性摘要）；**毛利 Tool** 在核销/出库全 0 时不输出臆造 100%；**`CostDiagnosisAgent`** 增补 **`riskLevel:data_incomplete`**、链路数据不足时综述克制；SSE/API **`riskLevel`** 已对齐；单测：**`GrossMarginCalculatorToolMissingOutboundTest`**、**`CostDiagnosisAgentNodeDataIncompleteScenarioTest`**。业务库请以「**帮我看本月成本怎么样**」再整机复验。 |
| 2026-05-10 | **权限与组织范围第一波（Java）**：`AiUserContext`/`AiOrgScope`/`AiUserContextResolver`/`AiOrgScopeResolver`/`AiAnswerBoundary`/`AiPermissionGuard` 做实；`AiRunState`+`AiRunService` 挂载；`BusinessToolExecutionNode`/`CostDiagnosisAgentNode`/`EchoStubToolNode`/`publishError` 接 `permissionDenied`；`POST` 支持 **`roleCode`**；单测 + **`mvn test`**。 |
| 2026-05-10 | **权限：`gb_du_admin` 主数据**：**`AiRoleMapper`**；**`AiUserContextResolver`** 读 **`gb_department_user`**；**`sourceAdminRole`/扩展权限/严格 org guard**；**`GROUP_MANAGER`**（别名废弃 **`GROUP_BOSS`**）；**`docs/PERMISSION_MODEL.md`**；**`mvn test`**。 |
| 2026-05-10 | **真机成本主线回归**（本机重启后）：同参 `POST` + SSE，**`runId=1778350824377`** — 五 Tool 全 **`success:true`**，**`answer_delta.data.costDiagnosis`** + **`run_finished.completed`**，无权限拒帧；记入 **`API_INTEGRATION.md`**「真机回归备忘」。 |
| 2026-05-10 | **成本意图按角色收敛**：采购类 → **`purchaseCostInsightPath`**；**`COUPON_OPERATOR`** → **`couponCostInsightBlocked`**；门店「集团成本」→ **查询范围** 收窄；**`STORE_MANAGER`** + **`VIEW_PURCHASE`**；**`PERMISSION_MODEL.md`** §7；**`mvn test`**。 |
| 2026-05-10 | **门店经营问句链路**：「这个月经营怎么样」等归入 **`business_overview_path`**（4 Tool：`revenue`/`purchase`/`dish_sales`/`gross_margin`）；**Scope 收窄**话术按岗位白话（店长/区域/采购）；**`StubAnswerComposerNode`** 去除向模型投喂 `workspaceMode`、`dataPlanTools`、`toolResults`；最终答复 **`stripDeveloperFacingLeakage`**；单测补 **`BusinessDataPlannerRoutingTest`、`AiRunScopeIntersectServiceTest`、`StubAnswerComposerNodeTest`**。整体验收仍以门店店长 SSE 实测为准。 |
| 2026-05-10 | **旧版 AI 回答资产**：新增 **`docs/LEGACY_AI_ANSWER_ASSETS.md`**（旧会话 `GbAiChatServiceImpl`、`ai-skill-*`、日营收看板 `GbAiDailyRevenueDashboardService`、菜品 `GbDepFoodBusinessInsightService` 等映射表）；本节 TODO 增补 **「旧版 AI 回答资产迁移」** checklist；实现接入与 DeepSeek 硬规则待后续迭代。 |
| 2026-05-10 | **经营概览接旧版看板（第一版）**：**`BusinessOverviewQueryTool`** 复用 **`buildStatsDashboard`**；**`business_overview_path`** 序列为 **`business_overview_query → dish_sales_query → purchase_query → gross_margin_calculator`**（不再依赖单薄 **`revenue_query`**）；**`gross_margin_calculator`** 缺 **`revenue_query`** 时读 **`business_overview_query.data.totalRevenue`**（**`revenueSource=daily_revenue_overview_dashboard`**）；SSE **`answer_delta.data.businessOverview`**（**`AiBusinessOverviewResult`**）；文档 **`API_INTEGRATION` / `PERMISSION_MODEL` / `TODO` checklist / `LEGACY_AI_ANSWER_ASSETS`** 已同步；真机店长/集团问句仍以本地业务库勾选验收。 |
| 2026-05-10 | **集团广角经营概览（UX + 口径说明）**：**`BusinessToolExecutionNode`** 对 **`GROUP_MANAGER`** 注入 **`groupWideOverviewHint`** → **`business_overview_query`** 失败时 **`failureKind`/`note`/`anomalyHints`** 说明集团 rollup 暂未接入（旧版同源为单体 **`departmentFatherId`**）；新增 TODO **「集团经营概览聚合口径」**；店长真机勾选已记；API  **`businessOverview`** 节增补集团段落。 |
| 2026-05-10 | **集团经营概览回答格式收口**：`BusinessOverviewAgentNode` 集团 `overviewScope` 白话（去掉登记/主体/节点话术）；`GbAiDailyRevenueDashboardServiceImpl#formatStatNumber` 小数统一 **plainString**，修复 **`利润率说明`** 强转；集团 **`数据口径说明`** / Tool **`anomalyHints`** 用语 softer；`StubAnswerComposerNode` 数字 headline + `dashboardStatsCn` 摘录走 **`AiNumericPlainText`**，经营 Composer 系统提示禁止科学计数法 / 短期样本「规模较小」/ 「无需优先关注门店」；新增 **`GroupManagerBusinessOverviewAnswerFormatRegressionTest`**（`GROUP_MANAGER_APP` + stub LLM）；单测 **`BusinessOverviewAgentGroupScopeSmokeTest`** / **`GbAiDailyRevenueDashboardServiceGroupFlattenTest`** 对齐。**请在 JDK 17 下执行 `mvn test` 验收**（本 Agent 环境 javac 不支持 `--release` 时无法代跑）。 |
| 2026-05-10 | **菜品毛利支线（第一版）**：**`looksLikeDishProfitInsight`** → **`dish_profit_path`**（单 Tool **`dish_profit_analysis`**）；**`DishProfitAnalysisTool`** + **`DishProfitAgentNode`**；SSE **`answer_delta.data.dishProfitOverview`**；**`AiPermissionGuard`** 菜品毛利：采购/库房/配送/优惠券等拒答话术；Composer **`DISH_PROFIT_COMPOSER_SYSTEM`**；文档 **`LEGACY_AI_ANSWER_ASSETS`** §菜品毛利、`API_INTEGRATION`、`PERMISSION_MODEL`。**真机勾选**仍以业务库为准。 |
| 2026-05-10 | **追问继承（第一版）**：**`FollowUpIntentResolveService`** + **`BusinessFollowUpIntentResolveNode`**（接在 **`BusinessWorkspaceRouteNode`** 后）；完成 Run 时在 **`AiRunService`** 写 **`AiFollowUpConversationMemory`**；**`TODO_MULTI_AGENT`** §多轮上下文追问；DDL **`intent` MEDIUMTEXT** 与 **`sql/gb_ai_agent_run_intent_extend.sql`**。 |

