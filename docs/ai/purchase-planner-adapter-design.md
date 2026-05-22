# Purchase Planner Adapter

> **现网**：**`PurchasePlannerAgentAdapter`** + **`PurchasePlannerRealReadBridge`**（Spring Bean）已落地；经 **`PlannerExecutor`** 调用 **`purchase_overview`**，**不**在 Adapter/Bridge 内写 SQL。  
> **Harness**：单域 `*_ADAPTER_*` / `*_HYDRATED_*` case **已移除**；主验收见 **[`planner-executor-v1-design.md`](./planner-executor-v1-design.md)** §27（**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE`** 等）。Composite 物化见 **`PlannerCompositeHarnessContext`**。  
> **Planner 基础设施**：**[`planner-executor-v1-design.md`](./planner-executor-v1-design.md)** §12（**`PlannerExecutor`**、**`PlannerAgentAdapterStepExecutor`**、**`StepResult`**）。

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
6. **不改** Resolver/Composer **主逻辑**；生产 **`finalAnswerText`** 不由 Planner Adapter 写入。

---

## 7. 与 PlannerExecutor / Composite 的关系

详见 **[`planner-executor-v1-design.md`](./planner-executor-v1-design.md)** §12 / §27（避免本文重复 Executor 契约）。

| 项 | 说明 |
|----|------|
| **Adapter 输入** | `PlannerAgentAdapterRequest` + `PurchasePlannerExecutionContext`（`runState`、`resolvedQueryContext`、`plannerReadRequest`） |
| **Adapter 输出** | `PlannerAgentAdapterResult`（`status`、`usedTools`、`usedAgents`、读响应摘要）；**不**产出 `finalAnswerText` |
| **stepId** | 由 `PlannerExecutionPlan` 定义（如 Composite 内 `step_purchase_*`）；以现网计划 JSON 为准 |
| **ReadBridge** | `PurchasePlannerRealReadBridge#readWithExecutionContext` → `buildPurchaseRequestContext` → `executePurchaseOverview` → `PurchaseAnswerPlanBuilder` |
| **Harness** | **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE`**（C-35）、**`…_GROUP_CORE`**（C-48）、降级 **C-42** |

---

## 8. ~~与 `purchase_query` 的边界~~（Historical removed）

**D-CLEAN-PURCHASE-QUERY-P2**：`purchase_query` Tool 已删；成本链与采购主线均仅 **`purchase_overview`**。

---

## 9. 参考路径（源码）

- `com.nongxinle.ai.planner.PurchasePlannerAgentAdapter`
- `com.nongxinle.ai.planner.PurchasePlannerRealReadBridge`
- `com.nongxinle.ai.agent.business.PurchaseAgent`
- `com.nongxinle.ai.graph.business.PurchaseOverviewToolExecutor`
- `com.nongxinle.ai.graph.business.toolrequest.BusinessToolExecutionRequestResolver#buildPurchaseRequestContext`
- `com.nongxinle.ai.tool.business.PurchaseOverviewTool`
- `com.nongxinle.ai.graph.business.PurchaseAnswerPlanBuilder`
- `com.nongxinle.ai.dto.business.PurchaseAnswerPlan`
