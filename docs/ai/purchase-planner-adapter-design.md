# Purchase Planner Adapter — C-15 梳理 + C-16 骨架 + C-18 设计 + C-19 已验收

> **Removed（P1-B Final）**：单域 Harness caseId（**`*_ADAPTER_*`**、**`*_FAKE_OK_*`**、**`*_HYDRATED_*`**）与 **`FakePurchasePlannerReadBridge`** 已删。Planner 主验收请用 Composite strict（C-35 / C-48 / C-42）；Hydrated 物化见 **`PlannerCompositeHarnessContext`**。下文 curl 单域 case 仅作历史参考。

**状态**：**C-16** 已落地 Planner 侧 **DTO + Bridge 骨架**；**C-19** 生产 **`PurchasePlannerRealReadBridge`** 仍用于 Composite strict。  
**对标 / 模板**：营收 **`RevenuePlannerRealReadBridge`** Hydrated（`planner-executor-v1-design.md` §22）；后续 **StockReduce / DishProfit** RealBridge 应对称本 Adapter 模式（§12.5）。

---

## 1. 现有采购能力一览（代码事实）

### 1.1 现网采购 Tool（唯一）

| Tool ID | 类 | 作用 | 与 Planner 专线关系 |
|---------|-----|------|---------------------|
| **`purchase_overview`** | `PurchaseOverviewTool` | `purchase_overview_path` 主链路 + **成本链第 2 步**：按组织范围 + 时间窗聚合入库金额、单量、采购方式拆分、Top 商品/门店/供货商等 | **`PurchaseAgent`**、经营诊断/多 Agent 组合、**Purchase Planner RealBridge** |

> **Historical removed（D-CLEAN-PURCHASE-QUERY-P2）**：`purchase_query` / **`PurchaseQueryTool`** 已删除（曾为窄口径 count/subTotal）；**禁止** 与 **`purchase_overview`** 混用。

**C-15 结论**：Planner 侧「采购专线 RealBridge」与 **`PurchaseAgent` 生产对齐**时，桥接 **`PURCHASE_OVERVIEW`** + **`PurchaseOverviewToolExecutor`**。

### 1.2 关键类与方法（便于实现阶段导航）

| 层级 | 类 | 说明 |
|------|-----|------|
| Agent | `PurchaseAgent` | `BusinessSubAgent`；`buildPurchaseRequestContext` → `executePurchaseOverview`；成功则 `PurchaseAnswerPlanBuilder.attachIfApplicable` |
| Executor | `PurchaseOverviewToolExecutor` | `buildPurchaseOverviewToolArgs` / `executePurchaseOverview`（权限、`ToolRegistry`、写入 `state.toolResults[purchase_overview]`） |
| Resolver | `BusinessToolExecutionRequestResolver#buildPurchaseRequestContext` | 从 `AiRunState` + `AiResolvedQueryContext` 解析时间窗、部门锚点、`dataScope` 采购域 ID、`queryIntent.purchaseSourceType` / `structuredIntentDetail`、orgScope 等（**不重读用户原文**） |
| 上下文 DTO | `PurchaseToolRequestContext` | 上述 Resolver 输出快照 |
| Tool | `PurchaseOverviewTool` | 读 `ToolRequest.args` + `resolvedQueryContext`；内部调用 **`GbDistributerPurchaseGoodsService`**（及门店根展开时 **`GbAiDailyRevenueService#expandStoreRootsToDailyRevenueScopeIds`**） |
| AnswerPlan | `PurchaseAnswerPlanBuilder` | Tool 成功后基于 **已有** `toolResults` 内嵌 overview **不重跑 SQL** 生成 `PurchaseAnswerPlan` |
| 输出 DTO | `PurchaseAnswerPlan` | `planType`、`purchaseSourceType`、`summary`、`focusRows`、`secondaryRows`、`debug` |
| Graph 汇聚 | `BusinessToolExecutionNode` | 生产图内组参数、`MasterBusinessAgent.tryOrchestratePurchaseOverview` 等（**Planner Harness 不跑此图**） |

### 1.3 数据从哪来

- **业务服务**：`GbDistributerPurchaseGoodsService`（计数、聚合、Top 列表等，与 `GbDistributerPurchaseGoodsMapper` 口径一致；见 `PurchaseOverviewTool` 类注释）。
- **门店根 → 查询部门展开**：`GbAiDailyRevenueService#expandStoreRootsToDailyRevenueScopeIds`（与日报/营收域共用展开逻辑）。
- **无新 SQL**：Adapter/Bridge 层只允许经 **现有 Tool** 触达上述服务。

---

## 2. 输入依赖（结构化字段，非 userMessage）

### 2.1 `BusinessToolExecutionRequestResolver#buildPurchaseRequestContext` 使用的 `AiResolvedQueryContext` / `AiRunState`

- **时间**：`timeWindow` 起止 ISO（或回退 `AiRunState.statStartDate` / `statEndDate`，与营收解析同类辅助方法）。
- **组织**：`orgScope.scopeType`、`visibleStores`（门店根 ID 列表）；`ToolDepartmentResolutionSupport` / `firstVisibleStoreDepartmentId` / `dataScope` 锚点回退。
- **数据范围**：`dataScope` → `purchaseSqlDepartmentIds`、`effectiveSqlDepartmentIds`、`visibleStoreRootIds`（SQL_DOMAIN_PURCHASE）。
- **采购语义（Intent 层）**：`queryIntent.purchaseSourceType`、`queryIntent.structuredIntentDetail`（由 **Resolver/语义层** 填入；Harness Hydrated **直接物化**这些字段即可，**禁止** Adapter 解析用户原文）。

### 2.2 `PurchaseOverviewToolExecutor#buildPurchaseOverviewToolArgs` → Tool `args` 要点

（与 `BusinessToolExecutionNode` 中 `PURCHASE_OVERVIEW` 分支一致。）

| 参数键（常量见 `AiBusinessToolIds`） | 来源/含义 |
|-------------------------------------|-----------|
| `ARG_DIS_ID` | 分销商/组织 ID（**必填级**；`PurchaseOverviewTool` 在校验 disId + 日期） |
| `ARG_START_DATE` / `ARG_STOP_DATE` | 统计区间 |
| `ARG_DEPARTMENT_FATHER_ID` / `ARG_PURCHASE_DEPARTMENT_ID` | **单店/非 group** 时的部门锚点 |
| `ARG_GROUP_PURCHASE_AGGREGATION` | `true` 时走集团聚合分支 + `ARG_RESOLVED_DEPARTMENT_IDS` / `ARG_PARENT_STORE_COUNT` |
| `ARG_PURCHASE_SOURCE_FOCUS` | 来自 `queryIntent.purchaseSourceType`（自采/供货商/ALL 等 wire） |
| `ARG_PURCHASE_NARRATIVE_MODE` | 来自 `queryIntent.structuredIntentDetail`（及 diagnosis 路径的特殊覆盖） |
| `ARG_VISIBLE_STORES` / `ARG_QUERY_SCOPE_BANNER` | 来自 `putPurchaseResolvedScopeArgs`（orgScope 展示） |
| `ARG_AI_ROLE_CODE` | 来自 `AiUserContext`（若有）；权限路径与 `AiPermissionGuard` 相关 |

### 2.3 `AiRunState` 上与采购相关的布尔位（实现 Hydrated 时需对照）

- **`purchaseOverviewPath`**：生产图上表示走采购概览专线；Bridge **若仅调用 Executor** 未必自动设置，需在 `readWithExecutionContext` hydrate 策略中与营收 path 旗标类似地评审（避免误开 group-wide）。
- **`groupPurchaseOverview`**：**必须为 `false`** 才能走 C-15 约定的 **STORE 单店** 参数分支（与 `buildPurchaseOverviewToolArgs` 一致）。

### 2.4 `ToolRequest`

- `executePurchaseOverview` 构建 `ToolRequest` 时设置 **`resolvedQueryContext(state.getResolvedQueryContext())`**，Tool 内可读完整上下文。

---

## 3. 输出形态

### 3.1 `toolResults["purchase_overview"]`

- 为 **Map** 信封（与 `PurchaseOverviewTool` / `AiBusinessToolResponses` 一致）；内含 `purchaseOverview` 业务块、汇总金额、Top 列表键名（如 `goodsPurchaseAmountTop`、`topSuppliers`、`coveredStores` 等，以实现为准）。
- **`PurchaseAnswerPlanBuilder`** 从该 Map **派生** `PurchaseAnswerPlan`，**不**再查库。

### 3.2 `PurchaseAnswerPlan`

- **`planType`**：`PurchaseAnswerPlan` 中 `TYPE_*` 常量（overview / self / supplier / 商品金额排行 / 商品次数排行 / 供货商金额排行 / 门店金额排行等）。
- **`purchaseSourceType`**：与 `AiQuerySemanticLexicon` 的 `SOURCE_*` 对齐（`ALL` / `SELF_PURCHASE` / `SUPPLIER_PURCHASE`）。
- **`summary` / `focusRows` / `secondaryRows` / `debug`**：供 Composer / Harness 探测；**debug.degraded** 可标记降级。

---

## 4. 采购特殊语义如何表达（Planner 边界）

**仅允许**通过 **已解析** 字段表达（与 `PurchaseAnswerPlanBuilder.resolvePlanType` 一致）：

| 用户语境（产品） | `queryIntent.purchaseSourceType`（示例） | `queryIntent.structuredIntentDetail`（`AiQuerySemanticLexicon` 常量） | 典型 `PurchaseAnswerPlan.planType` |
|------------------|------------------------------------------|----------------------------------------------------------------------|-------------------------------------|
| 全部采购 | `ALL` 或缺省 | `purchase_overview_summary` 等 | `PURCHASE_OVERVIEW` |
| 自采 | `SELF_PURCHASE` | 同上或 `purchase_source_*` | `PURCHASE_SELF_OVERVIEW` |
| 向供货商订货 | `SUPPLIER_PURCHASE` | 同上 | `PURCHASE_SUPPLIER_OVERVIEW` |
| 供货商维度（排行） | 视语义 | `supplier_amount_ranking`（经 lexicon canonical） | `PURCHASE_SUPPLIER_AMOUNT_RANKING` |
| 商品金额排行 | 视语义 | `purchase_goods_amount_ranking` | `PURCHASE_GOODS_AMOUNT_RANKING` |
| 商品数量/频次排行 | 视语义 | `purchase_goods_count_ranking` | `PURCHASE_GOODS_COUNT_RANKING` |
| 多店门店金额对比 | 视语义 | `purchase_store_amount_ranking` | `PURCHASE_STORE_AMOUNT_RANKING` |

**C-15 v1（STORE Hydrated）**：默认 **`structuredIntentDetail = purchase_overview_summary`** + **`purchaseSourceType = ALL`** 即可验证「最小闭环」；细分语义留作后续 Harness 变体。

---

## 5. 对照 Revenue 的 Planner 类型（**C-16 已实装骨架**）

### 5.1 `PurchasePlannerReadRequest`

包：`com.nongxinle.ai.planner`。字段：`resolvedQueryContextRef`、`timeStart`/`timeEnd`/`timeLabel`、`scopeType`、`visibleStores`（`PurchasePlannerVisibleStore`）、`queryDepartmentIds`、`targetStoreDepartmentId`、`purchaseSourceType`、`structuredIntentDetail`、`answerPlanRef`。**不含** `userMessage`。

### 5.2 `PurchasePlannerExecutionContext`

对齐 **`PlannerRevenueExecutionContext`**：`runState`、`resolvedQueryContext`、各类 ref、`userId`、`departmentId`、`distributerId`、`conversationId`、`runId`、内嵌 **`PurchasePlannerReadRequest plannerReadRequest`**。Hydrated 时：`runState.resolvedQueryContext` 与 context 字段 **同一引用**（未来 RealBridge）。

### 5.3 `PurchasePlannerReadResponse`

`status`（`PurchasePlannerReadStatus`）、`purchaseAmount`、`purchaseCount`、`purchaseSourceType`、`summary`、`focusRows`、`secondaryRows`、`errorCode`、`errorMessage`。

### 5.4 `PurchasePlannerReadBridge` / `FakePurchasePlannerReadBridge` / `PurchasePlannerRealReadBridge`

- **接口**：`PurchasePlannerReadBridge#readPurchase`；仅 Fake / 兼容路径；**不**承载 `ExecutionContext`。
- **Fake**：`FakePurchasePlannerReadBridge`（Harness-only）。
- **Real（C-19 Hydrated）**：`PurchasePlannerRealReadBridge` — `readWithExecutionContext` 经 **`buildPurchaseRequestContext` → `executePurchaseOverview` → `PurchaseAnswerPlanBuilder.attachIfApplicable`**；缺上下文仍 `ADAPTER_*` 降级；**C-17 CORE** 计划不 Hydrate 时不会因本实现误报 SUCCESS。
- **Real 骨架（C-17 CORE）**：同上 Bridge；计划**未**物化 `AiRunState`/`AiResolvedQueryContext` 时命中 `ADAPTER_NO_RUN_STATE` 等（**不**调 Tool）。

### 5.5 `PurchasePlannerAgentAdapter`

`targetAgent` / `targetTool` = `purchase_overview`。**C-16/C-17**：`Fake` / 一般 `ReadBridge` 走 `readPurchase`；`PurchasePlannerRealReadBridge` 走 `readWithExecutionContext`（见 `PlannerAgentAdapterRequest.purchaseExecutionContext`）。禁止用户原文；**C-17 骨架**不调用 `PurchaseOverviewToolExecutor`。

### 5.6 `PlannerExecutionPlan` 衔接

计划级可选 `purchaseReadRequest`、`purchaseExecutionContext`；`PlannerExecutor` 写入单步请求；trace 内对 execution context 做与营收相同的 `runState`/`resolvedQueryContext` 清空。

---

## 6. Purchase Adapter 明确不允许

1. **解析 `userMessage`**（contains/regex/new parser）。
2. **直接写 SQL** 或新建 Mapper 调用。
3. **绕过 `AiResolvedQueryContext`**（时间/组织/意图必须从结构化上下文或 Harness 物化对象来）。
4. **绕过现有 `purchase_overview` Tool/Executor**（不得仅为了 Planner  duplicate 一套查数逻辑）。
5. **未经权限门**：须走 `PurchaseOverviewToolExecutor` 内 **`AiPermissionGuard.evaluateToolInvocation`**。
6. **C-15 阶段**：不接 `MasterBusinessAgent`、不改 Resolver/Composer 主逻辑。

---

## 7. Harness 范围（设计 + **C-19 curl 已验收**）

### 7.1 v1：**STORE 单店 Hydrated**（C-19）

- `orgScope.scopeType = STORE`，**`AiRunState.groupPurchaseOverview = false`**。
- `distributerId`：**必须**满足 Tool 的 `disId` 非空校验（与本地 Harness / DB **真实** `disId` 一致；文档曾用占位 **2**）。
- **C-19**：`PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE` 已成功走 **`purchase_overview`**（见 **§12.1**）。

### 7.3 v1：**GROUP 双门店根 Hydrated**（C-45，单域探测）

- **`caseId`**：`PLANNER_EXECUTOR_PURCHASE_ADAPTER_GROUP_HYDRATED_CORE`；类 **`AiPlannerExecutorPurchaseAdapterGroupHydratedGraphCase`**。
- **`orgScope.scopeType = GROUP`**，**`visibleStores`** 含门店根 **1 / AAA**、**3 / 汀兰餐厅**；**`AiResolvedDataScope.fromOrgScope(org)`**。
- **`AiRunState.departmentId = null`**（**不**作单店 SQL 锚点）；**`distributerId = 2`**（与 §7.1 占位一致）。
- **`groupPurchaseOverview = true`**：**生产** **`PurchaseOverviewToolExecutor#buildPurchaseOverviewToolArgs`** 在 **`state.isGroupPurchaseOverview()`** 为 true 时写入 **`ARG_GROUP_PURCHASE_AGGREGATION`**，并从 **`resolvedQueryContext`** 提取多店 **`ARG_RESOLVED_DEPARTMENT_IDS`**；为 false 时仅写单店 **`ARG_DEPARTMENT_FATHER_ID`**。**C-45 GROUP 探测必须 true**；**C-19 STORE 必须 false**。
- **诚实摘要**：**`REAL_BRIDGE_HYDRATED_PURCHASE_GROUP_TOOL_OK` / `REAL_BRIDGE_HYDRATED_PURCHASE_GROUP_TOOL_DEGRADED`**；**不**单店假成功。
- **不接** Composite / LLM / Master；观测字段见 **`business-diagnosis-composite-group-design.md` §12**、**`planner-executor-v1-design.md` §24.3.1**。

### 7.2 caseId 一览（**C-16 / C-17** 已注册；**C-18** 设计-only）

| caseId | 类 | 行为 |
|--------|----|------|
| `PLANNER_EXECUTOR_PURCHASE_ADAPTER_CORE` | — | **Removed（P1-B2a）**；原 `AiPlannerExecutorPurchaseAdapterGraphCase` 已删；替代：FAKE_OK / HYDRATED / GROUP / Composite strict |
| `PLANNER_EXECUTOR_PURCHASE_ADAPTER_FAKE_OK_CORE` | `AiPlannerExecutorPurchaseAdapterFakeOkGraphCase` | `FakePurchasePlannerReadBridge`；首步 `SUCCESS`；`plannerPurchaseAdapterHonesty=FAKE_READ_BRIDGE_OK` |
| `PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_CORE` | — | **Removed（P1-B2a）**；原 `AiPlannerExecutorPurchaseAdapterRealBridgeGraphCase` 已删；替代：`PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE` / GROUP / Composite strict |
| `PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE` | `AiPlannerExecutorPurchaseAdapterRealBridgeHydratedGraphCase` | **C-19**：物化最小上下文 + 真实 **`purchase_overview`**；摘要 `plannerPurchaseAdapterHonesty` = `REAL_BRIDGE_HYDRATED_PURCHASE_TOOL_OK` / `REAL_BRIDGE_HYDRATED_PURCHASE_TOOL_DEGRADED` |
| `PLANNER_EXECUTOR_PURCHASE_ADAPTER_GROUP_HYDRATED_CORE` | `AiPlannerExecutorPurchaseAdapterGroupHydratedGraphCase` | **C-45**：**GROUP** + **`groupPurchaseOverview=true`** + 真实 **`purchase_overview`**；摘要 **`REAL_BRIDGE_HYDRATED_PURCHASE_GROUP_TOOL_OK` / `…_GROUP_TOOL_DEGRADED`** |

**推断模式**：`AiHarnessReplayMode.PLANNER_EXECUTOR_PURCHASE_ADAPTER`（`AiHarnessReplayService#resolveReplayMode`）。

**Replay 入口**：`AiHarnessReplayPlannerExecutorMock.replay(req, revenuePlannerRealReadBridge, purchasePlannerRealReadBridge)`（采购 Real case 需非 null 的 `PurchasePlannerRealReadBridge` Bean）。

---

## 8. ~~与 `purchase_query` 的边界~~（Historical removed）

**D-CLEAN-PURCHASE-QUERY-P2**：`purchase_query` Tool 已删；成本链与采购主线均仅 **`purchase_overview`**。

---

## 9. 参考路径（源码）

- `com.nongxinle.ai.agent.business.PurchaseAgent`
- `com.nongxinle.ai.graph.business.PurchaseOverviewToolExecutor`
- `com.nongxinle.ai.graph.business.toolrequest.BusinessToolExecutionRequestResolver#buildPurchaseRequestContext`
- `com.nongxinle.ai.tool.business.PurchaseOverviewTool`
- `com.nongxinle.ai.graph.business.PurchaseAnswerPlanBuilder`
- `com.nongxinle.ai.dto.business.PurchaseAnswerPlan`
- `com.nongxinle.ai.planner.PurchasePlannerRealReadBridge`（C-17 骨架；Harness **`PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_CORE` Removed P1-B2a**）

---

**文档版本**：C-15 梳理 + **C-16** DTO/Fake/Adapter + **C-17** CORE 骨架 + **C-18** Hydrated 设计 + **C-19** Hydrated **实装 + curl 验收**（§12）+ **C-45** GROUP Hydrated 单域探测（§7.3；`PLANNER_EXECUTOR_PURCHASE_ADAPTER_GROUP_HYDRATED_CORE`）  
**下一阶（可选）**：其它 GROUP 变体 / 排行 / 自采供货商专项；**不改** Resolver 主逻辑。

---

## 10. C-16/C-17/C-19 实装清单（源码）

- **C-16**：`PurchasePlannerReadStatus`…、`FakePurchasePlannerReadBridge`、`PurchasePlannerAgentAdapter`；计划字段 `purchaseReadRequest` / `purchaseExecutionContext`
- **C-17**：`PurchasePlannerRealReadBridge`（`readWithExecutionContext`）；Harness 负例 case **Removed P1-B2a**；Hydrated/GROUP/Composite 经 `replay(..., purchasePlannerRealReadBridge)`
- **C-19**：`AiPlannerExecutorPurchaseAdapterRealBridgeHydratedGraphCase`；`PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`；`PurchasePlannerRealReadBridge` → `PurchaseOverviewToolExecutor` / `PurchaseAnswerPlanBuilder`

---

## 11. 阶段 C-18/C-19：`PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`（字段权威）

**状态**：**C-19 已实装并 curl 验收**（`AiPlannerExecutorPurchaseAdapterRealBridgeHydratedGraphCase`、`AiHarnessBuiltinCases`、Replay 短路）；`PurchasePlannerRealReadBridge` 接线 **`PurchaseOverviewToolExecutor`**。本节为 **Hydrated 物化字段权威**；**验收观测摘要**见 **§12**；`purchaseOverviewPath` **默认不置**（与营收 Hydrated 一致），**仅**在本 GraphCase 内需佐证时再设并文档化。

### 11.1 目标与 caseId

- **caseId**：`PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`。
- **目标**：在 Harness 计划中物化**最小可用** `AiRunState`、`AiResolvedQueryContext`、`PurchasePlannerReadRequest`、`PurchasePlannerExecutionContext`（**STORE 单店**，**不做 GROUP**），使 **`PurchasePlannerRealReadBridge#readWithExecutionContext`** 在实现接线后能走完整链路：`BusinessToolExecutionRequestResolver#buildPurchaseRequestContext` → **`PurchaseOverviewToolExecutor#executePurchaseOverview`** → **`PurchaseOverviewTool`** →（成功）**`PurchaseAnswerPlanBuilder`** 基于 `toolResults["purchase_overview"]` 附着计划。
- **契约**：与 C-12/C-17 一致 — **`PurchasePlannerReadRequest` 不持有 `ExecutionContext`**；**`PurchasePlannerExecutionContext` 可持有 `plannerReadRequest`**，且 `runState.getResolvedQueryContext()` 与 context 内 **`resolvedQueryContext` 同一引用**（Hydrated）。

### 11.2 `AiRunState`：C-18 最小字段（STORE v1）

| 字段 | C-18 设计值 / 约定 |
|------|---------------------|
| `runId` | 与 `PurchasePlannerExecutionContext.runId` 可解析一致（实现可与营收 Harness 同类 `SYNTHETIC_RUN_ID_BASE` 对齐）。 |
| `conversationId` | 建议非 null；可与 Run 元数据一致。 |
| `userId` | 与 `ToolRequest` / 权限锚点一致。 |
| `departmentId` | **`1`**（与单店门店根对齐；文档语可与营收一致：**AAA / `gb_department_id=1`**）。 |
| `distributerId` | **`2`**（C-18 显式约定：**满足 `PurchaseOverviewTool` / `ARG_DIS_ID` 非空级校验**；具体 ID 以实现与环境 harness 数据为准，**禁止**文档臆造生产 magic number 以外的语义 — 此处 **2** 为设计占位，实现登记真实 `disId`）。 |
| `resolvedQueryContext` | **非 null**；与 `PurchasePlannerExecutionContext.resolvedQueryContext` **同一引用**。 |
| `toolResults` | **`new HashMap<>()`**（或等价空 Map）；执行成功后写入 **`purchase_overview`** 信封。 |
| 与 purchase 相关的 path 旗标 | **`groupPurchaseOverview = false`**（**必须**，以走单店 `buildPurchaseOverviewToolArgs` 分支，见 §2.3）。**`purchaseOverviewPath`**：实现阶段按 `PurchaseOverviewToolExecutor` / 生产对齐评审是否需置 `true`；C-18 **默认倾向**与营收 path 策略类似 — **首版不启 GROUP** 前提下，以代码实测为准（若 Executor 仅依赖 Resolver + Tool 而不读该位，可维持默认 **false**；若缺失导致 args 错误，在 **§11.8 缺口表** 补记）。 |

### 11.3 `AiResolvedQueryContext`：C-18 最小字段（STORE v1）

| 子结构 / 语义 | C-18 设计值 / 约定 |
|---------------|---------------------|
| `timeWindow.startDate` / `endDate` | **必填**；与 `PurchasePlannerReadRequest.timeStart` / `timeEnd` **严格对齐**（ISO 或可解析字符串，以 `AiResolvedTimeWindow` 为准）。 |
| `timeWindow.timeLabel` | **建议填**，便于日志与 `timeLabel` 对齐。 |
| `orgScope.scopeType` | **`STORE`**（`AiResolvedOrgScope`/枚举常量以代码为准）。 |
| `orgScope.currentStoreDepartmentId` | **`1`**。 |
| `orgScope.requestDepartmentId` | **`1`**。 |
| `orgScope.visibleStores` | 至少一项：**门店根 `1`**，展示名 **`AAA`**（与营收 Hydrated §22.9 一致）。 |
| `queryIntent.purchaseSourceType` | **`ALL`**（或实现层「默认全部采购」的等价枚举/字符串，与 `AiQuerySemanticLexicon` `SOURCE_*` 对齐）。 |
| `queryIntent.structuredIntentDetail` | **`purchase_overview_summary`**（`AiQuerySemanticLexicon` 常量或代码等价名；最小闭环与 §4 表「全部采购」行一致）。 |
| `effectiveIntentCode` / `effectivePathCode`（根级） | 与 `queryIntent` 采购概览路径一致（Harness 物化 **`PURCHASE_OVERVIEW`** / **`PATH_PURCHASE_OVERVIEW`** 等价常量，以代码为准）。 |

**说明**：`buildPurchaseRequestContext`另可能读取 **`dataScope`**（`purchaseSqlDepartmentIds` / `visibleStoreRootIds` 等）。C-18 **首选** orgScope 单店语义完备使 Resolver 能落锚；若实现时发现仍回退不到锚点，在实现阶段按 **`BusinessToolExecutionRequestResolver`** 实际读取补 **`dataScope` 最小子集**（**不**新 SQL，仅结构化字段）。

### 11.4 `PurchasePlannerReadRequest`：C-18 对齐字段

**不得**内含 `ExecutionContext`。建议与 **`AiResolvedQueryContext` / 计划 ref** 对齐：

| 字段 | C-18 设计值 / 约定 |
|------|---------------------|
| `resolvedQueryContextRef` | 与 `PlannerExecutionPlan.resolvedContextRef` / ExecutionContext 句柄一致。 |
| `timeStart` / `timeEnd` / `timeLabel` | 与 `AiResolvedQueryContext.timeWindow` 一致。 |
| `scopeType` | **`STORE`**。 |
| `visibleStores` | 至少一项 **1 / AAA**（`PurchasePlannerVisibleStore`）。 |
| `queryDepartmentIds` | **`[1]`**。 |
| `targetStoreDepartmentId` | **`1`**。 |
| `purchaseSourceType` | **`ALL`**。 |
| `structuredIntentDetail` | **`purchase_overview_summary`**。 |
| `answerPlanRef` | Adapter / trace 句柄（与营收 Hydrated 同源模式）。 |

### 11.5 `PurchasePlannerExecutionContext`：C-18 必填三元组

| 字段 | 说明 |
|------|------|
| `runState` | **非 null**；**`toolResults` 已初始化**；**`resolvedQueryContext` 与下同引用**。 |
| `resolvedQueryContext` | **非 null**；含 §11.3。 |
| `plannerReadRequest`（及 ref / ids） | **非 null**；与计划 `purchaseReadRequest` 一致；内容 §11.4。 |

（若 DTO 上另有 `userId`、`departmentId`、`distributerId`、`conversationId`、`runId` 等，与 `AiRunState` / 工具入参 **逐项对齐**，同营收 §22.5。）

### 11.6 真实 Bridge 调用顺序（C-19 与 `PurchaseAgent` 契约一致）

1. **`BusinessToolExecutionRequestResolver#buildPurchaseRequestContext`** — 入参 `AiRunState` + `AiResolvedQueryContext`，产出 **`PurchaseToolRequestContext`**（不重读用户原文）。
2. **`PurchaseOverviewToolExecutor#executePurchaseOverview`** — 权限门、组装 `ToolRequest`、`ToolRegistry` 派发。
3. **`PurchaseOverviewTool`** — 现有服务层查数（**无新 SQL**）。
4. **`PurchaseAnswerPlanBuilder`** — 从 **`state.toolResults["purchase_overview"]`** 派生 **`PurchaseAnswerPlan`**（不重跑 SQL）。

**Planner 层入口**（Harness）：`PlannerExecutor` → `PurchasePlannerAgentAdapter` → `PurchasePlannerRealReadBridge#readWithExecutionContext` — 见 **`purchase-planner-adapter-design.md` §12.2**。

### 11.7 验收（诚实）

- **C-19 curl**：在 §12.1 条件下曾观测 **Purchase Hydrated** 全绿：`overallStatus=SUCCESS`、`degradedSteps=[]`、采购步 `SUCCESS`、`usedTools` 含 **`purchase_overview`**，**`plannerPurchaseAdapterHonesty = REAL_BRIDGE_HYDRATED_PURCHASE_TOOL_OK`**。
- **若 DB 在选定 `departmentId` + 时间窗内有可读采购数据、且字段满足 Tool/Executor**（通用契约）：
  - 采购步 **`PlannerStepResult.status = SUCCESS`**；
  - **`plannerExecutorTrace.overallStatus = SUCCESS`**；
  - **`usedTools` 含 `purchase_overview`**（及 trace 中与采购 Agent 对齐的 `usedAgents` 项，以实现为准）；
  - Replay 摘要 **`plannerPurchaseAdapterHonesty = REAL_BRIDGE_HYDRATED_PURCHASE_TOOL_OK`**。
- **若无数据、空 payload、权限拒绝、部门/ dis 解析失败**：
  - **可控 `DEGRADED` 或步级失败**；**不得**抛未捕获异常；**不得**假装 SUCCESS；
  - 摘要 **`REAL_BRIDGE_HYDRATED_PURCHASE_TOOL_DEGRADED`** + `plannerPurchaseAdapterNote`（或等价）承载步状态、`degradedReason` / `errorMessage`（与 §22.0 营收模式对齐）。

### 11.8 实现前「还可能缺」的字段（缺口表，不乱补默认值）

实现阶段若最小集仍失败，按**代码事实**先后排查（**本阶段不**改 Resolver 主逻辑，**仅**让 Harness 物化对象满足现有入口）：

1. **`AiPermissionGuard`**：`AiUserContext` 非 null 时缺采购相关 view 权限 → `executePurchaseOverview` 短路；**可选** `aiUserContext == null` 快车道（同营收 §22.3 文档策略），或补最小 **VIEW_PURCHASE**（**确切常量名以 `AiRoleCodes` / Guard 为准**）。
2. **`dataScope` / SQL_DOMAIN_PURCHASE**：`purchaseSqlDepartmentIds`、`effectiveSqlDepartmentIds`、`visibleStoreRootIds` 等 — 若 Resolver 在单店下仍要求，则 Hydrated **追加最小 `AiResolvedDataScope`**（ID **1** 与 orgScope 对齐），**禁止**随意编造与门店无关的 ID。
3. **`statStartDate` / `statEndDate`（`AiRunState`）**：若 `buildPurchaseRequestContext` 或 ToolArgs 路径回退读 state 日期，需与 `timeWindow` **一致**（对标营收 §22.3）。
4. **`ARG_AI_ROLE_CODE` 等 Tool 参数**：若 Tool 硬依赖 `AiUserContext` 内角色码，Harness 需补齐。
5. **`purchaseOverviewPath`**：若 Executor/Node 某分支依赖该位才能写入正确 `args`，在 §11.2 默认值上**以单测或 curl 证据**修订文档，而非提前虚构。

### 11.9 C-18 明确不做（与 C-17 延伸一致）

- **GROUP** 多门店 / `ARG_GROUP_PURCHASE_AGGREGATION` 系统性验证。
- **supplier** 单独维度 Harness（非 `ALL` 默认源）。
- **自采/供货商筛选**（非 `purchaseSourceType = ALL` 的变体 case）。
- **商品金额/数量排行**、`structuredIntentDetail` 非 `purchase_overview_summary` 的排行类意图。
- **任何新 SQL / 新 Mapper / 新用户原文 contains/regex**。

---

## 12. C-19 收口：curl 验收事实 + 调用链 + 最小上下文速查 + 后续 Adapter 模板

本节是 **C-19 文档收口**：便于 **StockReduce / DishProfit** 等域按同一模式落地 **Hydrated RealBridge + Harness case**，**不**新写 SQL、**不**接 Master 生产主链路。字段级权威仍以 **§11** 为准。

### 12.1 curl Harness 已观测成功（`PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`）

在 **STORE 单店**、**`departmentId = 1` / 可见门店 AAA**、**`purchaseSourceType = ALL`**、**`structuredIntentDetail = purchase_overview_summary`**、`groupPurchaseOverview = false` 的 Hydrated 计划下，且 DB / 权限 / `distributerId` 与本环境一致时，Replay 曾观测到：

| 观测项 | 值 |
|--------|-----|
| `overallStatus` | **SUCCESS** |
| `degradedSteps` | **`[]`** |
| 采购步 `step_purchase_adapter_hydrated` | **SUCCESS** |
| 该步 `usedTools` | 含 **`purchase_overview`** |
| 根摘要 `plannerPurchaseAdapterHonesty` | **`REAL_BRIDGE_HYDRATED_PURCHASE_TOOL_OK`** |

**未验证即不宣称**：其它 org 形状、非 ALL 采购源、GROUP、supplier 维度、排行类 `structuredIntentDetail` 等（见 §12.4）。

### 12.2 完整真实调用链（C-19；自上而下）

与生产 **`PurchaseAgent`** 同源，经 Planner Harness **ADAPTER** 注入：

```text
PlannerExecutor（ADAPTER + PlannerAgentAdapterStepExecutor）
  → PurchasePlannerAgentAdapter（targetTool = purchase_overview）
       → PurchasePlannerRealReadBridge#readWithExecutionContext
            → BusinessToolExecutionRequestResolver#buildPurchaseRequestContext
            → PurchaseOverviewToolExecutor#executePurchaseOverview
            → PurchaseOverviewTool（现有服务层）
            → PurchaseAnswerPlanBuilder#attachIfApplicable（读 toolResults["purchase_overview"]）
```

**不含**：`userMessage` 解析；Bridge 内手写 SQL；`GbDistributerPurchaseGoodsService` 直连（仅 Tool 内）。

### 12.3 最小成功上下文速查（必填级；与 §11 表一致）

以下为 **Hydrated GraphCase 已物化** 的**最小集**；缺任一项可能导致 Resolver / Tool / Builder 降级。

| 对象 | 必填要点 |
|------|-----------|
| **`AiRunState`** | `runId`、`conversationId`、`userId`、`departmentId`（**1**）、`distributerId`（环境真实 **`disId`**）、`resolvedQueryContext`（与 ExecutionContext **同引用**）、`toolResults`（**非 null**，可空 Map）、`groupPurchaseOverview`（**false**）。 |
| **`AiResolvedQueryContext`** | `timeWindow`（起止 + 建议 `timeLabel`）、`orgScope`（**STORE**，`currentStoreDepartmentId` / `requestDepartmentId` / `visibleStores` **1/AAA**）、`queryIntent`（`purchaseSourceType=ALL`，`structuredIntentDetail=purchase_overview_summary`）、根级 **`effectiveIntentCode` / `effectivePathCode`** 与采购概览一致、`runId`/`userId` 等与 Run 对齐。 |
| **`PurchasePlannerExecutionContext`** | **`runState`、`resolvedQueryContext`、`plannerReadRequest` 非 null**；ref / `userId` / `departmentId` / `distributerId` / `conversationId` / `runId` 字符串与 `AiRunState` 一致。 |
| **`PurchasePlannerReadRequest`** | `resolvedQueryContextRef`；时间与 `timeWindow` 一致；**STORE** + `queryDepartmentIds=[1]` + `targetStoreDepartmentId=1` + `visibleStores`；**ALL** + **`purchase_overview_summary`**；`answerPlanRef`。 |

### 12.4 当前验证范围与限制（诚实边界）

| 项 | C-19 现状 |
|----|-----------|
| **组织** | **仅验证 STORE 单店**（`departmentId`/门店根 **1**，AAA 展示名）。 |
| **采购语义** | **仅验证 `purchaseSourceType = ALL`** + **`purchase_overview_summary`**。 |
| **GROUP / 多门店聚合** | **未**在 Harness 系统性验证。 |
| **自采 / 供货商筛选** | **未**验证（非 ALL 变体）。 |
| **supplier 维度** | **未**验证。 |
| **商品金额 / 数量排行等** | **未**验证（其它 `structuredIntentDetail`）。 |
| **计划第 2 步 recommendation** | **仍为 mock**（`RecommendationPlannerMockAgentAdapter`），非生产建议链。 |
| **Master / 生产 Graph** | **未**接入；本 case 为 **Planner mock Replay 短路**。 |
| **trace** | **不**输出完整 `AiRunState` / `AiResolvedQueryContext` 大对象（与营收 Hydrated 同类策略）。 |

### 12.5 后续 Adapter 模板（StockReduce / DishProfit）

对标 **本采购 C-19** 与 **营收 Hydrated（§22）**：

1. **命名与结构**：`*PlannerRealReadBridge`（或域内约定名）+ `*PlannerAgentAdapter` + `*ExecutionContext` / `*ReadRequest`；**真实入口**与 `PurchasePlannerRealReadBridge#readWithExecutionContext` 同类 — **ExecutionContext 带齐 `AiRunState` + `AiResolvedQueryContext`**。
2. **执行链**：**仅**复用现有 **`BusinessToolExecutionRequestResolver`**（或域内已有 `build*RequestContext`）→ **`StockReduceQueryToolExecutor` / `DishProfitQueryToolExecutor`**（或等价）→ **现有 Tool Bean** → **现有 `*AnswerPlanBuilder`**（从 **`toolResults`** 附着 AnswerPlan）；**禁止** Bridge 内新 SQL、禁止直连报表 Service（除非与现有 Tool 已统一）。
3. **Harness**：独立 **`…_REAL_BRIDGE_HYDRATED_CORE`** `caseId`，物化 **最小** `AiRunState` + `AiResolvedQueryContext`；诚实摘要常量对称 **`REAL_BRIDGE_HYDRATED_*_TOOL_OK` / `…_DEGRADED`**。
4. **验收**：成功时 **`overallStatus=SUCCESS`**、**`degradedSteps=[]`**、步级 **`usedTools` 含域 ToolId**；失败 **可控降级**，不假 SUCCESS。

---
