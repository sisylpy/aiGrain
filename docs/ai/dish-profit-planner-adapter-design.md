# DishProfit Planner Adapter

> **现网**：**`DishProfitPlannerAgentAdapter`** + **`DishProfitPlannerRealReadBridge`**（Spring Bean）已落地；经 **`PlannerExecutor`** 调用 **`dish_profit_analysis`**，**不**在 Adapter/Bridge 内写 SQL。  
> **Harness**：单域 `*_ADAPTER_*` / `*_HYDRATED_*` case **已移除**；主验收见 **[`planner-executor-v1-design.md`](./planner-executor-v1-design.md)** §27。Composite 物化见 **`PlannerCompositeHarnessContext`**。  
> **Planner 基础设施**：**[`planner-executor-v1-design.md`](./planner-executor-v1-design.md)** §12 / §26。

---

## 1. 现有菜品毛利 / DishProfit 能力（代码事实）

### 1.1 单条 Planner 应对齐的 Tool

| Tool ID | 常量 | 类 | 与生产子 Agent 关系 |
|---------|------|-----|---------------------|
| **`dish_profit_analysis`** | `AiBusinessToolIds.DISH_PROFIT_ANALYSIS` | `DishProfitAnalysisTool` | **`DishProfitAgent`**、**`DishProfitQueryToolExecutor`**、`BusinessToolExecutionNode` 均走此 ID |

**C-25 结论**：Planner **RealBridge** 与生产对齐时，应桥接 **`buildDishProfitRequestContext` → `DishProfitQueryToolExecutor#executeDishProfitAnalysis` → `DishProfitAnalysisTool`**；工具快照成功后 **`DishProfitAnswerPlanBuilder#attachForAgentEnvelope`**（与 **`DishProfitAgent#execute`** 同序）。

### 1.2 关键类与方法（导航）

| 层级 | 类 | 说明 |
|------|-----|------|
| Agent | `DishProfitAgent` | `BusinessSubAgent`；`buildDishProfitRequestContext` → `executeDishProfitAnalysis`；成功则 `DishProfitAnswerPlanBuilder.attachForAgentEnvelope` |
| Executor | `DishProfitQueryToolExecutor` | `buildDishProfitAnalysisToolArgs` / `executeDishProfitAnalysis`（权限、`ToolRegistry`、写入 `state.toolResults[dish_profit_analysis]`） |
| Resolver | `BusinessToolExecutionRequestResolver#buildDishProfitRequestContext` | 时间窗、`dataScope.sqlDepartmentIdsForDomain(dish_profit)`、`orgScope`、`queryIntent.structuredIntentDetail`、`mentionedDishName`、`dishProfitMetricType`（**不重读用户原文作路由**） |
| 上下文 DTO | `DishProfitToolRequestContext` | Resolver 输出快照 |
| Tool | `DishProfitAnalysisTool` | 调用 **`GbDepFoodBusinessInsightService#buildInsight`**；按 **`ARG_DISH_PROFIT_STRUCTURED_DETAIL`** 做 **`applyDishProfitPresentation`**（排序/收窄）；**禁止**在 Planner Bridge 内绕过此 Tool 直连 Service |
| Graph 衍生 | `DishProfitAgentNode` | 从 `toolResults` 衍生 **`AiDishProfitOverviewResult`**、选行挂载 **`DishProfitAnswerPlan`**（与 Tool 内 `dishRows` / 结构化 wire 对齐） |
| AnswerPlan | `DishProfitAnswerPlanBuilder` | 校验 **`toolResults[dish_profit_analysis]`** 信封 `success` 后 **`DishProfitAgentNode.computeOverviewAndAttachPlans`** |
| 输出 DTO | `DishProfitAnswerPlan` | `planType`、`sortKey`、`sortDirection`、`topN`、`focusRows`/`secondaryRows`、`debug` |
| 输出 DTO | `AiDishProfitOverviewResult` | 汇总额、毛利率文案、`topProfitDishes` / `lowProfitDishes` / `costDataIncompleteDishes` / `abnormalDishes` 等（SSE / Composer 同源） |
| Master / 图 | `MasterBusinessAgent`、`BusinessToolExecutionNode`、`BusinessDataPlannerNode` | **C-25/C-26 不接、不改** |

### 1.3 数据从哪来（业务层）

- **服务（Insight 聚合）**：**`GbDepFoodBusinessInsightService#buildInsight(disId, depFatherIdInt, start, stop, null, scopeAllow)`** — 返回 `dishes`、`businessInsightSummary`、`dishProfitStoreCoverage`、`scopeOutboundSubtotals` 等。
- **菜品行字段（节选）**：标价收入 `actualRevenue`、销量 `soldPortionsTotal`、理论成本 `theoryCostAmount`、实际成本 `actualCostAmount`、多种毛利率字段（如 `blendedGrossMarginRateOnListPrice`、`grossMarginRateOnListPrice`）等；Tool 侧 **`summarizeDishRow`** 压缩为 `dishRows`。
- **组合层面汇总**：Tool 对 presented 行求和填 **`actualRevenueTotal`**、**`totalTheoreticalCost`**、**`totalActualCostType1`**、**`portfolioGrossProfitAmount`**、**`portfolioBlendedGrossMarginRateOnListPrice`** 等；与 **`businessInsightSummary`** 口径对齐说明见 Tool 内注释。
- **SQL / Mapper**：在 **`GbDepFoodBusinessInsightService`** 及其实现链之下的持久层；**Planner Adapter 禁止新 SQL / 禁止绕过 Tool**。

---

## 2. Tool 入参依赖（对齐 `DishProfitQueryToolExecutor` / `DishProfitAnalysisTool`）

下列由 **`buildDishProfitAnalysisToolArgs`** 组装，来源以 **`AiRunState` + `AiResolvedQueryContext`** 为主（与用户原文路由无关）：

| 参数键（`AiBusinessToolIds`） | 含义 / 来源要点 |
|------------------------------|------------------|
| `ARG_DIS_ID` | 分销商 ID（`state.distributerId`） |
| `ARG_DEPARTMENT_FATHER_ID` | 部门父/门店根；**集团广角**时可为 `AiInsightDishProfitScope.DEP_FATHER_ID_GROUP_WIDE_Mendian_AGGREGATE_UNDER_DIS_ID`（**C-25 v1 不验证 GROUP**） |
| `ARG_START_DATE` / `ARG_STOP_DATE` | ISO 日期（Resolver 时间窗 → context → Executor） |
| `ARG_RESOLVED_DEPARTMENT_IDS` | `dataScope` / `AiQueryScope` 回退；**SQL IN 过滤**，与 `dishProfitSqlDepartmentIds` 同源语义 |
| `ARG_PARENT_STORE_COUNT` | 门店根数量（单店 STORE 可由 `dataScope` 推导） |
| `ARG_DISH_PROFIT_STRUCTURED_DETAIL` | **`queryIntent.structuredIntentDetail`**（**`AiQuerySemanticLexicon` wire**） |
| `ARG_DISH_NAME_FOCUS_HINT` | **`resolvedQueryContext.mentionedDishName`**（点名菜收窄，`DishProfitAnalysisTool` 内 **contains** 匹配 **在 Tool 内**，非 Adapter 解析用户句） |
| `ARG_QUERY_SCOPE_KIND` / `ARG_QUERY_STORE_IDS` / `ARG_QUERY_REAL_DEPARTMENT_IDS` / `ARG_QUERY_DISTRIBUTER_ID` / `ARG_STORE_TO_DEPARTMENT_IDS` | 来自 **`AiResolvedDataScope`**（STORE 单店时 `QUERY_SCOPE_KIND_STORE` + 单元素 `queryStoreIds` 可覆盖 `depFatherIdInt`，见 Tool 内逻辑） |
| `ARG_AI_ROLE_CODE` | `AiUserContext`（权限） |
| `ARG_USER_QUESTION_HINT` | `state.getNormalizedUserInput()`（**可选**透传；**Adapter 不得**为路由去解析原文；Harness 可置空或仅调试） |

**意图 / path（解析层）**：**`AiResolvedQueryIntent.DISH_PROFIT`** + **`PATH_DISH_PROFIT`**（`dish_profit_path`）；**Effective** 字段须与专线一致。

---

## 3. 输出形态

### 3.1 `toolResults["dish_profit_analysis"]`

- **Envelope**：与其他业务 Tool 一致，经 **`AiBusinessToolResponses.envelope`**；内含 **`success`**、`data`（`dishRows`、`buildInsightRequest`、`businessInsightSummary`、覆盖门店、`portfolioGrossMarginRate` 等）。
- **失败**：`success=false`；可能 `permission_denied`（Executor 返回 **`null`**）。

### 3.2 `DishProfitAnswerPlan`（**`DishProfitAnswerPlanBuilder` → `DishProfitAgentNode`**）

- **`planType`**：如 **`TYPE_DISH_LOWEST_MARGIN`**、**`TYPE_DISH_HIGHEST_MARGIN`**、**`TYPE_DISH_PROFIT_REASON`**、**`TYPE_DISH_THEORETICAL_COST`**、**`TYPE_DISH_ACTUAL_OUTBOUND_COST`**、**`TYPE_DISH_PROFIT_RATE`**、**`TYPE_DISH_COST_GAP`**、**`TYPE_DISH_HIGHEST_ACTUAL_COST`**、fallback 类型等（以代码与 **`dish-profit-answer-plan.md`** 为准）。
- **`focusRows` / `secondaryRows`**：结构化行 Map，供 Composer / Deterministic renderer。

### 3.3 `AiDishProfitOverviewResult`（概述卡片）

- 从 Tool 快照 + 行集合 **派生**（**不**在 Adapter 内重算 Insight）。

---

## 4. 菜品毛利特殊语义与 Lexicon Wire（表达规范）

**原则**：语义 **唯一**以 **`AiResolvedQueryContext.queryIntent.structuredIntentDetail`**（及 **`mentionedDishName`**、**`dishProfitMetricType`**）表达；Planner **ReadRequest** 复用 **同一 wire 字符串**；**不**在 Adapter 内做用户原文 contains/regex。

| 产品语义 | Lexicon wire（常量名 → 字符串） | AnswerPlan / 行为要点 |
|----------|----------------------------------|------------------------|
| **菜品毛利概览** | `STRUCTURED_DISH_PROFIT_OVERVIEW` → **`dish_profit_overview`** | 全量 presented 行默认序；概览汇总 + overview 派生 |
| **毛利率最低菜品** | `STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN` → **`dish_profit_ranking_low_margin`** | Tool 内按 **`blendedGrossMarginRateOnListPrice`** **升序**；AnswerPlan **`TYPE_DISH_LOWEST_MARGIN`** 等 |
| **毛利率最高菜品** | `STRUCTURED_DISH_PROFIT_RANKING_HIGH_MARGIN` → **`dish_profit_ranking_high_margin`** | 高毛利排行；**`TYPE_DISH_HIGHEST_MARGIN`** |
| **指定菜品毛利** | **`mentionedDishName` 非空** + 单菜 wire：**`STRUCTURED_DISH_GROSS_MARGIN_QUERY`**（`dish_gross_margin_query`）、**`THEORETICAL_COST`**、**`ACTUAL_OUTBOUND_COST`**、**`COST_GAP`**、**`LOW_PROFIT_REASON`** 等 | Tool **`applyDishProfitPresentation`** 按菜名 **contains** 滤行（**在 Tool 内**） |
| **菜品销售额排行** | `STRUCTURED_DISH_SALES_RANKING` → **`dish_sales_ranking`** | 按 **`soldPortionsTotal` DESC**（销量别名「销售额排行」在产品文案可能混用；**以 Tool 实现为准：当前为销量口径**） |
| **菜品成本排行** | **`STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH/LOW`**、**`THEORETICAL_COST_RANKING_*`** | 实付/理论成本排序 |
| **菜品毛利异常 / 关切** | 组合：**`STRUCTURED_DISH_GAP_RANKING_MAX`**（`dish_gap_ranking_max`）、低毛利排行、overview 派生 **`abnormalDishes`** / **`lowProfitDishes`** | 「异常」在销售域常映射为 **成本缺口排行** + **低毛利率池**；**具体阈值与分桶以 `DishProfitAgentNode` 为准** |

**C-26 Fake Bridge**：合成数据明示 **`actualRevenue`** / **`soldPortionsTotal`** 分列；**`ReadResponse.salesAmount`** = 标价收入汇总占位（与生产 **`actualRevenueTotal`** 同源语义），**禁止**将「销量/份数排行」冒充「销售额/标价收入」。

---

## 5. Planner 侧 DTO 与组件（**C-26 已创建**）

### 5.1 `DishProfitPlannerReadRequest`

- **不得**包含用户聊天原文。
- **字段**（与 `PlannerExecutor` 透传一致）：
  - `resolvedQueryContextRef`、`timeStart` / `timeEnd`、`timeLabel`
  - `scopeType`（Harness FAKE_OK：**`STORE`**）
  - `visibleStores`：`List<DishProfitPlannerVisibleStore>`
  - `queryDepartmentIds`、`targetStoreDepartmentId`
  - **`structuredIntentDetail`**（Lexicon wire）
  - **`mentionedDishName`**、**`dishProfitMetricType`**
  - `answerPlanRef`

### 5.2 `DishProfitPlannerReadResponse`

- `status`（`DishProfitPlannerReadStatus`：`OK` / `DEGRADED` / `FAILED`）
- `planType`、`grossProfitAmount`、`grossProfitRate`
- **`salesAmount`**（标价收入汇总语义，见 §4 末）
- `costAmount`
- `dishRows`、`focusRows`、`secondaryRows`、`summary`
- `errorCode`、`errorMessage`

### 5.3 `DishProfitPlannerExecutionContext`

- **`AiRunState` `runState` / `String runStateRef`**
- **`AiResolvedQueryContext` `resolvedQueryContext` / `String resolvedQueryContextRef`**
- **`Long userId` / `departmentId` / `distributerId`**；**`String conversationId` / `runId`**
- **`DishProfitPlannerReadRequest plannerReadRequest`**（只表达「查什么」；**不得**内嵌本 `ExecutionContext`）
- **C-27**：真实读入口 **`DishProfitPlannerRealReadBridge#readWithExecutionContext(ctx)`** 仅消费本 DTO；**`DishProfitPlannerReadRequest`** 单独亦可由计划/步注入，仅作「要查什么」载荷。
- **约束**：`plannerReadRequest` **不得**反向引用本对象。

### 5.4 `DishProfitPlannerAgentAdapter`

- **`TARGET_AGENT`** = `BusinessAgentNames.DISH_PROFIT_ANALYSIS`；**`TARGET_TOOL`** = `AiBusinessToolIds.DISH_PROFIT_ANALYSIS`
- **`readBridge == null`** → `DEGRADED`，`ADAPTER_NO_REAL_CONTEXT:read_bridge_null:…`
- 有 Bridge 时校验 `resolvedQueryContextRef`、时间窗、scope
- **`DishProfitPlannerRealReadBridge`**：`PlannerAgentAdapterRequest` **须**带 **`dishProfitExecutionContext`**；缺省时 **`DISH_PROFIT_REAL_BRIDGE_NO_PLANNER_EXECUTION_CONTEXT_ON_READ`**；否则合并 **`plannerReadRequest`** 后调用 **`readWithExecutionContext`**
- 其他 **`DishProfitPlannerReadBridge`**：调用 **`readDishProfit(readRequest)`**
- **C-27 骨架路径**：**`new DishProfitPlannerRealReadBridge()`** 不含 Bean 依赖时 **不**调用 **`DishProfitQueryToolExecutor`**；**C-29 Hydrated Bean** 路径则真实走 Tool（见 5.5）。

### 5.5 `DishProfitPlannerReadBridge` / **`FakeDishProfitPlannerReadBridge`** / **`DishProfitPlannerRealReadBridge`**

- **`FakeDishProfitPlannerReadBridge`**：Harness-only；返回 **`OK`** 与合成 `dishRows`（**`HARNESS_HONESTY_FAKE_READ_BRIDGE_OK`**）
- **`DishProfitPlannerRealReadBridge`**：
  - **`readDishProfit`**：一律降级，提示走 **`readWithExecutionContext`**
  - **`readWithExecutionContext`**：缺 **`AiRunState`** → **`ADAPTER_NO_RUN_STATE`**；缺 **`AiResolvedQueryContext`** → **`ADAPTER_NO_RESOLVED_CONTEXT`**；依赖未注入（**`new`** 骨架）→ **`DISH_PROFIT_REAL_READ_BRIDGE_SKELETON`** / **`ERROR_SKELETON_NO_TOOL`**
  - **C-29（Spring Bean + Harness §7 物化上下文）**：**`BusinessToolExecutionRequestResolver#buildDishProfitRequestContext`** → **`DishProfitQueryToolExecutor#executeDishProfitAnalysis`** → **`DishProfitAnalysisTool`** → **`DishProfitAnswerPlanBuilder#attachForAgentEnvelope(state, false)`**（见 §7.5、§9）

### 5.6 `PlannerExecutionPlan` / `PlannerStepExecutionRequest` / `PlannerAgentAdapterRequest`

- **`dishProfitReadRequest`**、**`dishProfitExecutionContext`** 已透传；**`PlannerExecutor#sanitizePlanForTrace`** 清空 execution context 内 `runState` / `resolvedQueryContext`

---

## 6. Adapter / Bridge **禁止项**（硬边界）

1. **不**解析 **`userMessage`** / **不**新增用户原文 **contains / regex** 路由。
2. **不**在 Bridge 内 **直连 Service / Mapper / 新 SQL**。
3. **不**绕过 **`AiResolvedQueryContext`**（时间、门店、`dataScope`、`structuredIntentDetail`、`mentionedDishName` 须由上下文注入或 Harness 物化）。
4. **不**绕过 **`DishProfitQueryToolExecutor` + `DishProfitAnalysisTool` + `DishProfitAnswerPlanBuilder`** 生产链路。
5. **不改** Resolver/Composer **主逻辑**；生产 **`finalAnswerText`** 不由 Planner Adapter 写入。

---


## 7. 与 PlannerExecutor / Composite 的关系

详见 **[`planner-executor-v1-design.md`](./planner-executor-v1-design.md)** §12 / §26 / §27。

| 项 | 说明 |
|----|------|
| **Adapter 输入** | `PlannerAgentAdapterRequest` + `DishProfitPlannerExecutionContext` |
| **Adapter 输出** | `PlannerAgentAdapterResult`；**不**产出 `finalAnswerText` |
| **stepId** | 由 `PlannerExecutionPlan` 定义（Composite 内 `step_dish_profit_*` 等） |
| **ReadBridge** | `DishProfitPlannerRealReadBridge#readWithExecutionContext` → `buildDishProfitRequestContext` → `executeDishProfitAnalysis` → `DishProfitAnswerPlanBuilder#attachForAgentEnvelope` |
| **Harness** | **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE`**（C-35）、**`…_GROUP_CORE`**（C-48）、降级 **C-42** |

---

## 8. 参考路径（源码）

- `com.nongxinle.ai.planner.DishProfitPlannerAgentAdapter`
- `com.nongxinle.ai.planner.DishProfitPlannerRealReadBridge`
- `com.nongxinle.ai.agent.business.DishProfitAgent`
- `com.nongxinle.ai.graph.business.DishProfitQueryToolExecutor`
- `com.nongxinle.ai.tool.business.DishProfitAnalysisTool`
- `com.nongxinle.ai.graph.business.DishProfitAnswerPlanBuilder`
- `docs/ai/dish-profit-answer-plan.md` — AnswerPlan / Composer 边界
