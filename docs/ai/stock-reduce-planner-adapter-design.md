# StockReduce Planner Adapter

> **现网**：**`StockReducePlannerAgentAdapter`** + **`StockReducePlannerRealReadBridge`**（Spring Bean）已落地；经 **`PlannerExecutor`** 调用 **`stock_reduce_query`**，**不**在 Adapter/Bridge 内写 SQL。  
> **Harness**：单域 `*_ADAPTER_*` / `*_HYDRATED_*` case **已移除**；主验收见 **[`planner-executor-v1-design.md`](./planner-executor-v1-design.md)** §27。Composite 物化见 **`PlannerCompositeHarnessContext`**。  
> **Planner 基础设施**：**[`planner-executor-v1-design.md`](./planner-executor-v1-design.md)** §12 / §25。

---

## 1. 现有出库 / 核销能力一览（代码事实）

### 1.1 单条 Planner 应对齐的 Tool

| Tool ID | 类 | 与 `StockReduceAgent` 关系 |
|---------|-----|---------------------------|
| **`stock_reduce_query`** | `StockReduceQueryTool` | **唯一**专线 Tool；`StockReduceAgent` / `StockReduceQueryToolExecutor` / `BusinessToolExecutionNode` 均走此 ID |

**C-20 结论**：Planner **RealBridge** 与生产对齐时，应桥接 **`STOCK_REDUCE_QUERY`** + **`StockReduceQueryToolExecutor#executeStockReduceQuery`**，成功则 **`StockReduceAnswerPlanBuilder#attachIfApplicable`**（与 `PurchaseAgent` → `PurchaseOverviewToolExecutor` → `PurchaseAnswerPlanBuilder` 同构）。

### 1.2 关键类与方法（实现阶段导航）

| 层级 | 类 | 说明 |
|------|-----|------|
| Agent | `StockReduceAgent` | `BusinessSubAgent`；`buildStockReduceRequestContext` → `executeStockReduceQuery`；成功则 `StockReduceAnswerPlanBuilder.attachIfApplicable` |
| Executor | `StockReduceQueryToolExecutor` | `buildHarnessToolArgs` / `executeStockReduceQuery`（权限、`ToolRegistry`、写入 `state.toolResults[stock_reduce_query]`） |
| Resolver | `BusinessToolExecutionRequestResolver#buildStockReduceRequestContext` | 时间窗、`dataScope.sqlDepartmentIdsForDomain(stock_reduce)`、`orgScope`、`queryIntent.structuredIntentDetail`、`querySemanticParse.metric.stockReduceType`（观测用）等（**不重读用户原文**） |
| 上下文 DTO | `StockReduceToolRequestContext` | Resolver 输出快照（`startDateIso`/`endDateIso`、`departmentFatherIdForScopedTools`、`stockReduceSqlDepartmentIds` 等） |
| Tool | `StockReduceQueryTool` | `ARG_STOCK_REDUCE_HARNESS_PATH=true` 时走 **自然日历日** 四类合计；否则走嵌入成本链 **仅日营业额日**；读 `GbDepartmentGoodsStockReduceService` |
| AnswerPlan | `StockReduceAnswerPlanBuilder` | 基于 **`toolResults["stock_reduce_query"]`** 信封 **不重跑 SQL** 生成 `StockReduceAnswerPlan` |
| 输出 DTO | `StockReduceAnswerPlan` | `planType`、`reduceType`、`scopeLabel`、`timeLabel`、`summary`、`focusRows`、`secondaryRows`、`debug` |
| Graph | `BusinessToolExecutionNode` | 生产图内与其它 Tool 并列；**Planner Harness 未来**仍不跑整图，仅 Executor 直连 |
| Master | `MasterBusinessAgent` | 出库专线闸门与 orchestration（**C-20 不接、不改**） |

### 1.3 数据从哪来（业务层）

- **主表 / 实体**：`GbDepartmentGoodsStockReduceEntity` → 表 **`gb_department_goods_stock_reduce`**（与 `GbConstants` 中 **dgsr_type** 口径一致：生产耗用 / 废弃 / 损耗报损 / 退货等类型区分在 **Service/Mapper** 层聚合为 `produceTotal`、`wasteTotal`、`lossTotal`、`returnTotal`）。
- **服务**：`GbDepartmentGoodsStockReduceService`（Planner **禁止**在 Bridge 内直连；仅 **`StockReduceQueryTool`** 内调用）。
- **Harness 专线 SQL 入口**（自然日四类）：`queryReduceAllTypesTotalForRetailDepartmentFathers(Map)` — `params` 含 `departmentFatherIds`、`disId`、`startDate`、`stopDate`。
- **嵌入成本旧径**（非 harness）：`queryReduceAllTypesTotalOnDailyRevenueDays` — **仅统计有日营业额的自然日**（`totalsBasis=DAILY_REVENUE_DAYS_ONLY`）。
- **排行**：
  - 商品 **金额** Top：`queryStockSubtotalTopTimes` → Tool 填 `topGoodsOutboundBySubtotal`；
  - 商品 **出库次数** Top：`queryStockOutboundTimesTopForRetailFathers` → `topGoodsOutboundByOutboundTimes`（**非**独立「重量」排行字段；重量类汇总若存在见 Service 其它方法，**当前 AnswerPlan 商品排行平面以金额/次数为主**）。

---

## 2. Tool 行为与输入依赖（结构化字段）

### 2.1 双路径：`StockReduceQueryTool#execute`

| 分支 | 触发条件 | 口径 |
|------|-----------|------|
| **Harness / 专线日历** | `args(ARG_STOCK_REDUCE_HARNESS_PATH) == true`（Executor **恒设**） | **自然日历日** 四类 subtotal；单店用 **`visibleStores` 仅 1 家** 时取该门店根为 `departmentFatherIds`；集团时 `ARG_GROUP_STOCK_REDUCE_AGGREGATION` + `visibleStores` 多根 |
| **Legacy 嵌入成本** | 上述为 false | 需 `departmentFatherId` + 日期；**仅日营业额覆盖日** |

Planner RealBridge **应对齐 Executor**：即 **`executeStockReduceQuery`** 这条 **harness** 路径（与 `StockReduceAgent` 一致）。

### 2.2 `StockReduceQueryToolExecutor#buildHarnessToolArgs` → 主要 `args` 键

| 参数键（`AiBusinessToolIds`） | 来源 / 含义 |
|------------------------------|-------------|
| `ARG_STOCK_REDUCE_HARNESS_PATH` | **`true`**（专线日历） |
| `ARG_DIS_ID` | 分销商 ID（与 `AiRunState.distributerId` 传入 Executor 一致） |
| `ARG_START_DATE` / `ARG_STOP_DATE` | ISO 日期 |
| `ARG_STOCK_REDUCE_NARRATIVE_MODE` | 来自 `queryIntent.structuredIntentDetail`（经 lexicon 归一，如 count ranking → goods outbound ranking wire） |
| `ARG_GROUP_STOCK_REDUCE_AGGREGATION` | `AiRunState.isGroupStockReduceQuery()` 为 true 时；+ `ARG_RESOLVED_DEPARTMENT_IDS` / `ARG_PARENT_STORE_COUNT` |
| `ARG_DEPARTMENT_FATHER_ID` | **非 group** 时由 dept 锚点写入（Tool 单店仍 **优先 `visibleStores` 唯一项** 覆盖 args，避免集团登录锚点串店） |
| `ARG_VISIBLE_STORES` / `ARG_QUERY_SCOPE_BANNER` | `resolvedQueryContext.orgScope` |
| `ARG_AI_ROLE_CODE` | `AiUserContext`（权限与文案横幅逻辑） |

`ToolRequest` **携带** `resolvedQueryContext(state.getResolvedQueryContext())`，Tool 内单店/集团分支 **强依赖** `orgScope.visibleStores`。

### 2.3 `BusinessToolExecutionRequestResolver#buildStockReduceRequestContext`

从 **`AiRunState` + `AiResolvedQueryContext`** 解析（节选，以代码为准）：

- **时间**：`resolveStartDateIso` / `resolveEndDateIso`（可回退 `statStartDate`/`statEndDate`）。
- **部门锚点**：`resolveToolDepartmentFatherId`、`resolveBuildInsightDepartmentFatherId`；回退 `visibleStores[0]`、`dataScope` 等。
- **`dataScope`**：`SQL_DOMAIN_STOCK_REDUCE` → `stockReduceSqlDepartmentIds`；缺省时单店 Hydrated 可能仅靠 **orgScope** 仍可落锚（与采购 §11.8 **缺口表**同理，实现阶段按事实补 **最小 dataScope**）。
- **语义**：`queryIntent.structuredIntentDetail`；可选 **`querySemanticParse.metric.stockReduceType`**（Resolver 填入 debug；**`StockReduceAnswerPlanBuilder.resolvePlanType` 当前以 `structuredIntentDetail` wire 为主**）。

### 2.4 `StockReduceAnswerPlanBuilder#attachIfApplicable` 前置条件

当 **`toolResults` 已含 `stock_reduce_query`**，或 `dataPlanTools` 含该工具，或 **`AiRunState.isStockReduceQueryPath()`** 为真时进入附着逻辑。Planner 在 **`executeStockReduceQuery` 成功写入 toolResults 后**调用 `attachIfApplicable` 即可附着（**与采购**「执行后 envelopes 已存在」同理）。

---

## 3. 输出形态

### 3.1 `toolResults["stock_reduce_query"]`

- **Map 信封**（`AiBusinessToolResponses` 形态：`success`、`message`、`data`…）；**内层 data**（扁平或嵌套）含：
  - **四类金额**：`produceTotal`、`wasteTotal`、`lossTotal`、`returnTotal`、`grandTotalFourTypes`
  - **`totalsBasis`**：`CALENDAR_NATURAL_DAY`（harness）或 `DAILY_REVENUE_DAYS_ONLY`（legacy）
  - **可选排行**：`topGoodsOutboundBySubtotal`、`topGoodsOutboundByOutboundTimes`、`topStoresOutboundByGrandTotal`（多店金额对比）
  - `rawReduceTotals`、**`groupStockReduceAggregation`** 等

### 3.2 `StockReduceAnswerPlan`

- **`planType`**：`TYPE_STOCK_REDUCE_*`（overview / production / output / waste / loss / return / goods amount ranking / goods count ranking / store amount ranking）
- **`reduceType`**：`ALL`、`TYPE1`…`TYPE4`、`RANKING`（Builder 派生）
- **`summary` / `focusRows` / `secondaryRows` / `debug`**：Composer / Harness 探测

---

## 4. 出库 / 核销特殊语义如何表达（**仅结构化字段**）

**禁止**在 Planner Adapter 内解析 `userMessage`。语义必须由 **`AiResolvedQueryContext.queryIntent.structuredIntentDetail`**（及必要时 **`querySemanticParse.metric`**）承载，与 `StockReduceAnswerPlanBuilder.resolvePlanType` / `AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire` 对齐。

| 产品语境 | `structuredIntentDetail`（`AiQuerySemanticLexicon` 常量 / wire） | 典型 `StockReduceAnswerPlan.planType` |
|----------|------------------------------------------------------------------|----------------------------------------|
| 全部出库 / 核销四类汇总 | `STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY`（`stock_reduce_overview`） | `TYPE_STOCK_REDUCE_OVERVIEW` |
| 生产耗用 | `STRUCTURED_PRODUCE_CONSUME`（`produce_consume`） | `TYPE_STOCK_REDUCE_PRODUCTION_OVERVIEW` |
| 出品（口径同 type1） | `STRUCTURED_PRODUCE_OUTPUT`（`produce_output`） | `TYPE_STOCK_REDUCE_OUTPUT_OVERVIEW` |
| 废弃 / 过期 | `STRUCTURED_WASTE`（`waste`） | `TYPE_STOCK_REDUCE_WASTE_OVERVIEW` |
| 损耗 / 报损 | `STRUCTURED_LOSS`（`loss`） | `TYPE_STOCK_REDUCE_LOSS_OVERVIEW` |
| 退货 | `STRUCTURED_RETURN`（`return`） | `TYPE_STOCK_REDUCE_RETURN_OVERVIEW` |
| 商品出库 **金额** 排行 | `STRUCTURED_GOODS_OUTBOUND_RANKING`（`goods_outbound_ranking`） | `TYPE_STOCK_REDUCE_GOODS_AMOUNT_RANKING` |
| 商品出库 **次数** 排行（count wire 归一到同一 Tool 分支） | `STRUCTURED_GOODS_OUTBOUND_COUNT_RANKING` → Executor 内可能归一为 `STRUCTURED_GOODS_OUTBOUND_RANKING` | `TYPE_STOCK_REDUCE_GOODS_COUNT_RANKING` |
| 门店出库 **金额** 对比（多店） | `STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING`（`store_outbound_amount_ranking`） | `TYPE_STOCK_REDUCE_STORE_AMOUNT_RANKING` |

**C-20 Hydrated v1（STORE 单店）**：建议仅验证 **`stock_reduce_overview`**（与采购 `purchase_overview_summary` 地位对称）；**GROUP / 多店排行** 留 **`REAL_BRIDGE_HYDRATED` 后续迭代**。

**意图 / 路径**：生产侧 `effectiveIntentCode = STOCK_REDUCE_QUERY`、`effectivePathCode = PATH_STOCK_REDUCE_QUERY`（与 `StockReduceAgent#supports` 一致）。

---

## 5. Planner 侧类型（**C-21 已落地 `com.nongxinle.ai.planner`**）

包名：`com.nongxinle.ai.planner`（与 `PurchasePlanner*` / `RevenuePlanner*` 并列）。

### 5.1 `StockReducePlannerReadRequest`（**C-21**）

结构化只读切片，**不含** `userMessage`，**不含**对 `ExecutionContext` 的反向引用。

| 字段 | 说明 |
|------|------|
| `resolvedQueryContextRef` | 与 `PlannerExecutionPlan.resolvedContextRef` 对齐 |
| `timeStart` / `timeEnd` / `timeLabel` | 与 `AiResolvedQueryContext.timeWindow` 对齐 |
| `scopeType` | 如 `STORE`（Harness CORE v1） |
| `visibleStores` | `StockReducePlannerVisibleStore`（`departmentId` + `displayLabel`） |
| `queryDepartmentIds` | 单店占位 ID 等 |
| `targetStoreDepartmentId` | 门店根，与 visible 对齐 |
| `reduceType` | 与 `StockReduceAnswerPlan` 分型对齐（如 `ALL` / `TYPE1`…） |
| `structuredIntentDetail` | **wire**，如 `stock_reduce_overview` |
| `totalsBasis` | 如 `CALENDAR_NATURAL_DAY`（结构化标签；Fake 不接库） |
| `answerPlanRef` | trace / 计划句柄 |

### 5.2 `StockReducePlannerReadResponse`（**C-21**）

| 字段 | 说明 |
|------|------|
| `status` | `StockReducePlannerReadStatus`：`OK` / `DEGRADED` / `FAILED` |
| `grandTotalAmount` | 四类合计（Fake 为合成值） |
| `produceTotal` / `wasteTotal` / `lossTotal` / `returnTotal` | 分型金额 |
| `totalsBasis` | 与 Request / Tool 口径标签对齐 |
| `summary` / `focusRows` / `secondaryRows` | 浅表 Map / 行 |
| `errorCode` / `errorMessage` | Bridge 降级 |

### 5.3 `StockReducePlannerExecutionContext`（**C-21 骨架**）

对称 `PurchasePlannerExecutionContext`（未来 RealBridge Hydrate 用）：`runState`、`resolvedQueryContext`、各类 ref、`plannerReadRequest`。**C-21** Executor trace 仍会 `sanitize` 清空重对象。

### 5.4 `StockReducePlannerAgentAdapter`（**C-21**）

- `targetAgent` = `BusinessAgentNames.STOCK_REDUCE_QUERY`
- `targetTool` = `AiBusinessToolIds.STOCK_REDUCE_QUERY`
- 无 `StockReducePlannerReadBridge` → `DEGRADED`，`ADAPTER_NO_REAL_CONTEXT:read_bridge_null:…`
- **`FakeStockReducePlannerReadBridge`** → `SUCCESS`，`usedAgents`/`usedTools` 含上述常量

### 5.5 `StockReducePlannerRealReadBridge`（**C-22 + C-24**）

- **C-22**：Harness 内 **`new StockReducePlannerRealReadBridge()`**（无依赖）在上下文缺失时诚实降级；上下文齐全仍返回 **`STOCK_REDUCE_REAL_READ_BRIDGE_SKELETON`**（不调 Tool）。
- **C-24**：**`@Component`** 由 Spring 注入 **`StockReduceQueryToolExecutor`** + **`BusinessToolExecutionRequestResolver`** 时，**`readWithExecutionContext`** 走 **`buildStockReduceRequestContext` → `executeStockReduceQuery` → `StockReduceAnswerPlanBuilder#attachIfApplicable`**（**禁止** Bridge 内 SQL / 直连 `GbDepartmentGoodsStockReduceService`）。

---

## 6. StockReduce Adapter 明确不允许

1. **解析 `userMessage`**（contains/regex/new parser）。
2. **直接写 SQL** 或 Bridge 内直连 `GbDepartmentGoodsStockReduceService`。
3. **绕过 `AiResolvedQueryContext`**。
4. **绕过 `stock_reduce_query` Tool / Executor / `StockReduceAnswerPlanBuilder`**（生产 Real 路径）。
5. **未经权限门**：须走 `executeStockReduceQuery` 内 **`AiPermissionGuard.evaluateToolInvocation`**。
6. **不改** Resolver/Composer **主逻辑**；生产 **`finalAnswerText`** 不由 Planner Adapter 写入。

---


## 7. 与 PlannerExecutor / Composite 的关系

详见 **[`planner-executor-v1-design.md`](./planner-executor-v1-design.md)** §12 / §25 / §27。

| 项 | 说明 |
|----|------|
| **Adapter 输入** | `PlannerAgentAdapterRequest` + `StockReducePlannerExecutionContext` |
| **Adapter 输出** | `PlannerAgentAdapterResult`；**不**产出 `finalAnswerText` |
| **stepId** | 由 `PlannerExecutionPlan` 定义（Composite 内 `step_stock_*` 等） |
| **ReadBridge** | `StockReducePlannerRealReadBridge#readWithExecutionContext` → `buildStockReduceRequestContext` → `executeStockReduceQuery` → `StockReduceAnswerPlanBuilder` |
| **Harness** | **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE`**（C-35）、**`…_GROUP_CORE`**（C-48）、降级 **C-42** |

---

## 8. 参考路径（源码）

- `com.nongxinle.ai.planner.StockReducePlannerAgentAdapter`
- `com.nongxinle.ai.planner.StockReducePlannerRealReadBridge`
- `com.nongxinle.ai.agent.business.StockReduceAgent`
- `com.nongxinle.ai.graph.business.StockReduceQueryToolExecutor`
- `com.nongxinle.ai.tool.business.StockReduceQueryTool`
- `com.nongxinle.ai.graph.business.StockReduceAnswerPlanBuilder`
