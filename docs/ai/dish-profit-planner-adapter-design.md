# DishProfit Planner Adapter — **C-25 生产梳理 + C-26 骨架 + C-27 RealBridge 骨架 + C-28 设计 + C-29 Hydrated 实装**

> **Removed（P1-B Final）**：单域 Harness caseId 与 **`FakeDishProfitPlannerReadBridge`** 已删；主验收用 Composite strict（C-35 / C-48 / C-42）；物化见 **`PlannerCompositeHarnessContext`**。下文单域 curl 仅历史参考。

**C-25**：梳理生产 **`dish_profit_analysis`** 链路与语义，设计 **`DishProfitPlannerReadBridge` / Adapter / DTO** 边界（下文 §1–§4、§6–§8）。**C-26（已落地）**：`com.nongxinle.ai.planner` 内 **DTO**、**`DishProfitPlannerReadBridge`**、**`FakeDishProfitPlannerReadBridge`**、**`DishProfitPlannerAgentAdapter`**；**`PlannerExecutionPlan` 等透传**；Harness **`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_FAKE_OK_CORE`**（**`…_CORE` Removed P1-B2a**）。**C-27（已落地）**：**`DishProfitPlannerRealReadBridge`**；Harness **`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_CORE`** **Removed（P1-B2a）**（默认不物化 **`AiRunState` / `AiResolvedQueryContext`** → 可控 **`DEGRADED`**；摘要 **`plannerDishProfitAdapterHonesty=REAL_BRIDGE_HARNESS_INCOMPLETE_CONTEXT`**）。**C-28**：**`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`** 最小 Hydrated 上下文与验收见 **§7**。**C-29（已落地并已 curl 验收）**：**`AiPlannerExecutorDishProfitAdapterRealBridgeHydratedGraphCase`**、Harness 注册、`AiHarnessReplayService` 注入 **`DishProfitPlannerRealReadBridge`** Bean、`DishProfitPlannerRealReadBridge` 真实调用 **`DishProfitQueryToolExecutor#executeDishProfitAnalysis`** → **`attachForAgentEnvelope(state, false)`**（§7.0 观测表）。**不**改 Master / Resolver / Composer / 既有 Replay 期望。**对标**：[`purchase-planner-adapter-design.md`](./purchase-planner-adapter-design.md)、[`stock-reduce-planner-adapter-design.md`](./stock-reduce-planner-adapter-design.md)、[`planner-executor-v1-design.md`](./planner-executor-v1-design.md) §12、§26。

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
- **菜品行字段（节选）**：标价收入 `listPriceRevenue`、销量 `soldPortionsTotal`、理论成本 `theoryCostAmount`、实际成本 `actualCostAmount`、多种毛利率字段（如 `blendedGrossMarginRateOnListPrice`、`grossMarginRateOnListPrice`）等；Tool 侧 **`summarizeDishRow`** 压缩为 `dishRows`。
- **组合层面汇总**：Tool 对 presented 行求和填 **`listPriceRevenueTotal`**、**`totalTheoreticalCost`**、**`totalActualCostType1`**、**`portfolioGrossProfitAmount`**、**`portfolioBlendedGrossMarginRateOnListPrice`** 等；与 **`businessInsightSummary`** 口径对齐说明见 Tool 内注释。
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

**C-26 Fake Bridge**：合成数据明示 **`listPriceRevenue`** / **`soldPortionsTotal`** 分列；**`ReadResponse.salesAmount`** = 标价收入汇总占位（与生产 **`listPriceRevenueTotal`** 同源语义），**禁止**将「销量/份数排行」冒充「销售额/标价收入」。

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
4. **不**绕过 **`DishProfitQueryToolExecutor` + `DishProfitAnalysisTool` + `DishProfitAnswerPlanBuilder`（经 `DishProfitAgentNode` 派生）** 生产链路 — **C-26 Fake 例外**：仅为 Harness 合成 JSON，**不**声称真实 Tool。
5. **不**改 **Master**、**Resolver 主逻辑**、**Composer 主模板**、**已有 Replay 期望**（新 case **另开**）。

---

## 7. STORE 单店 Hydrated RealBridge（**C-28 设计 + C-29 实装**）

**目标**：与 **`AiPlannerExecutorStockReduceAdapterRealBridgeHydratedGraphCase`** / 采购 Hydrated 同源——在 Harness 内物化最小 **`AiRunState` + `AiResolvedQueryContext` + `DishProfitPlannerExecutionContext`**，使 **`DishProfitPlannerRealReadBridge`**（Spring Bean）调用 **`DishProfitQueryToolExecutor#executeDishProfitAnalysis`**，再 **`DishProfitAnswerPlanBuilder#attachForAgentEnvelope`**。**仅 STORE 单店**；**不接** Master / 生产 Graph；**不**写新 SQL。

**caseId**：**`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`** — 已注册 **`isPlannerExecutorMockHarnessCase`**、**`AiHarnessReplayService#resolveReplayMode`**、**`AiHarnessReplayPlannerExecutorMock.replay(..., dishProfitPlannerRealReadBridge)`**（第五参数）；GraphCase：**`AiPlannerExecutorDishProfitAdapterRealBridgeHydratedGraphCase`**。

### C-29 成功路径（curl Harness Replay 已验收）

环境与 DB 有 Insight 菜品数据、权限与范围通过时，曾观测：

| 项 | 观测值 |
|----|--------|
| **`caseId`** | **`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`** |
| **组织范围** | **`AiResolvedOrgScope.SCOPE_STORE`**，可见门店 **AAA**（`storeDepartmentId` / 锚点 **`departmentId = 1`**） |
| **`AiRunState`** | **`dishProfitPath = true`**；**`toolResults`** 初始为空，由 **`DishProfitQueryToolExecutor`** 写入 **`dish_profit_analysis`** |
| **RealBridge** | **`DishProfitPlannerRealReadBridge`** 复用生产链路 **`dish_profit_analysis`**（不经 Adapter 伪造） |
| **`overallStatus`** | **`SUCCESS`** |
| **`degradedSteps`** | **`[]`** |
| **菜品毛利步** | **`step_dish_profit_adapter_hydrated`** → **`SUCCESS`** |
| **`usedTools`** | 含 **`dish_profit_analysis`** |
| **`plannerDishProfitAdapterHonesty`** | **`REAL_BRIDGE_HYDRATED_DISH_PROFIT_TOOL_OK`** |

无数据 / 权限不足 / 载荷不足时须诚实 **`DEGRADED`** 与 **`REAL_BRIDGE_HYDRATED_DISH_PROFIT_TOOL_DEGRADED`**（见 §7.6），**不**假 **`SUCCESS`**。

### 7.1 `AiRunState` 最小字段（Hydrated，单店 AAA / `departmentId=1`）

| 字段 | 设计取值 | 说明 |
|------|-----------|------|
| `runId` | 与 Harness 合成 run 对齐（如 **`9_000_000`** + round，与现有 mock 首轮一致时可沿用） | **`executeDishProfitAnalysis(rid, …)`** 首参 |
| `conversationId` | **`0L`**（或会话表占位，与 sisters case 一致即可） | |
| `userId` | **`1L`** | |
| `departmentId` | **`1L`** | 登录/锚点门店根，与 **`AiResolvedOrgScope`** 一致 |
| `distributerId` | **`2L`** | 文档占位；环境可改为真实 **`disId`**（与采购/出库 Hydrated 叙述一致） |
| `resolvedQueryContext` | 指向 §7.2 物化对象 | **须**与 execution context 内 **`AiResolvedQueryContext`** 为**同一实例或等价快照** |
| `toolResults` | **`new HashMap<>()`** | Executor 写入 **`dish_profit_analysis`** 信封 |
| **`dishProfitPath`** | 建议 **`true`** | 与生产「菜品毛利专线」语义对齐；AnswerPlan 附着/观测与 **`DishProfitAgentNode`** 路径一致时可依赖（**以** **`DishProfitAnswerPlanBuilder`** / **`DishProfitAgent`** **实装**为准） |
| **GROUP / 集团广角** | **`groupWarehouseStockOverview`** / **`BusinessToolExecutionNode#shouldRouteGroupWideDishInsight(state)`** 为 **假** | **C-29 STORE** **不做** GROUP 多店聚合；**C-47** 见 **§7.10** |
| **其它 path flags** | 与 DishProfit 专线互斥的 **`businessOverviewPath` / `costInsightPath` / `stockReduceQueryPath`** 等保持 **默认 false** | 避免误触多域编排 |
| **`statStartDate` / `statEndDate`** | 可选；Bridge 可在 **`buildDishProfitRequestContext`** 之后用 **`DishProfitToolRequestContext`** 回写（与 **`DishProfitAgent#execute`** 同源） | 便于日志与后续 Composer 观测 |
| **`aiUserContext`** | **可选** | **`AiPermissionGuard`**：若 **`getAiUserContext() == null`** 则 **允许**调用 Tool（兼容最小构造）；若环境要求真实鉴权，须设 **`roleCode`** + **`permissions`** 含 **`VIEW_DISH_SALES`** 与 **`VIEW_COST`**，且 **非** 采购收敛 / 库房 / 配送等 **拒绝角色**（见 **`AiPermissionGuard#evaluateDishProfitAnalysisInvocation`**） |

### 7.2 `AiResolvedQueryContext` 最小字段（STORE / 门店 **AAA**，`storeDepartmentId=1`）

**说明**：下面 **`intent`/`path`** 以代码常量为准。生产 **`intentCode`** 为 **`AiResolvedQueryIntent.DISH_PROFIT`**（字符串 **`"DISH_PROFIT"`**），**不是**字面 **`DISH_PROFIT_ANALYSIS`**（后者为 Tool ID / Agent 名）。

| 区域 | 字段 | 设计取值 |
|------|------|-----------|
| **时间** | `timeWindow.startDate` / `endDate` / `timeLabel` | 如 **`2026-05-01`..`2026-05-14`** + 可读 `timeLabel`（与 C-27 窗口一致可复用） |
| **组织** | `orgScope.scopeType` | **`AiResolvedOrgScope.SCOPE_STORE`**（**`"STORE"`**） |
| | `orgScope.currentStoreDepartmentId` | **`1L`** |
| | `orgScope.requestDepartmentId` | **`1L`** |
| | `orgScope.visibleStores` | **单元素** **`AiStoreScopeDTO`**：`storeDepartmentId=1`，`storeName="AAA"`（与 StockReduce Hydrated 对齐） |
| | `orgScope.distributerId` | 可与 **`AiRunState.distributerId`** 一致（**`2L`** 占位） |
| **数据范围** | `dataScope` | 与 **StockReduce C-24 Hydrated** 同构：**`queryScopeKind=QUERY_SCOPE_KIND_STORE`**、**`queryStoreIds=[1]`**、**`expandedSqlDepartmentIds`** / **`storeRootDepartmentIds`** / **`visibleStoreIds`** 含门店 **1**、**`queryScopeMode=QUERY_SCOPE_MODE_STORE`**。**`getSqlDepartmentIdsForDomain(SQL_DOMAIN_DISH_PROFIT)`** 须能解析出 **非空** IN 列表（当前实现走 **`resolveSqlQueryDepartmentIds()`**，与 **`expandedSqlDepartmentIds`** 等一致即可） |
| **意图** | `queryIntent.intentCode` | **`AiResolvedQueryIntent.DISH_PROFIT`** |
| | `queryIntent.pathCode` | **`AiResolvedQueryIntent.PATH_DISH_PROFIT`**（**`dish_profit_path`**） |
| | `queryIntent.structuredIntentDetail` | **`AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_OVERVIEW`**（wire **`dish_profit_overview`**） |
| **有效路由** | `effectiveIntentCode` / `effectivePathCode` | 与 **`queryIntent`** 一致 **`DISH_PROFIT`** / **`PATH_DISH_PROFIT`** |
| **菜品收窄** | `mentionedDishName` | **`null`**（C-28 **不做**指定菜详情） |
| **指标类型** | `dishProfitMetricType` | **`"OVERVIEW"`**（与 **`AiQuerySemanticLexicon.dishProfitMetricTypeFromStructuredWire("dish_profit_overview")`** 一致）；Planner **`DishProfitPlannerReadRequest`** 可并列填 **`OVERVIEW`** |
| **`querySemanticParse`** | | **可选**；overview-only v1 可 **null** 或最小空对象（**非**排行/成本子意图则 **勿**填会触发其它 Tool 分支的 metric） |
| **与 Run 对齐** | `runId` / `userId` | 与 **`AiRunState`** 一致便于摘要与排障 |
| **`userContext`** | | 若 RunState 未带 **`AiUserContext`**，可将只读快照放在 **`AiResolvedQueryContext.userContext`**（**以** **`AiPermissionGuard`** **与** `requestWithinOrgScope` **实参链路**为准） |

### 7.3 `DishProfitPlannerReadRequest`（与计划级 slice 一致，供 `ExecutionContext.plannerReadRequest`）

| 字段 | 设计取值 |
|------|-----------|
| `resolvedQueryContextRef` | Harness 常量 ref（与 **`PlannerExecutionPlan.resolvedContextRef`** 对齐） |
| `timeStart` / `timeEnd` / `timeLabel` | 与 **`AiResolvedQueryContext.timeWindow`** 一致 |
| `scopeType` | **`"STORE"`** |
| `visibleStores` | 单店 **`DishProfitPlannerVisibleStore`**：`departmentId=1`，`displayLabel="AAA"` |
| `queryDepartmentIds` | **`List.of(1L)`** |
| `targetStoreDepartmentId` | **`1L`** |
| `structuredIntentDetail` | **`dish_profit_overview`**（常量 **`STRUCTURED_DISH_PROFIT_OVERVIEW`**） |
| `mentionedDishName` | **`null`** |
| `dishProfitMetricType` | **`"OVERVIEW"`**（可选 **`gross_profit_overview`** **勿**与 wire 混用；**权威**仍以 **`structuredIntentDetail` → lexicon → `"OVERVIEW"`** 为准） |
| `answerPlanRef` | Harness 占位 ref |

### 7.4 `DishProfitPlannerExecutionContext`

| 字段 | 要求 |
|------|------|
| `runState` | §7.1 物化实例 |
| `resolvedQueryContext` | §7.2 物化实例（与 **`runState.resolvedQueryContext`** 一致） |
| `plannerReadRequest` | §7.3 |
| `userId` / `departmentId` / `distributerId` / `conversationId` / `runId` | 与 RunState / 计划约定一致（字符串 id 与 **`Long runId`** 转换与 Stock Hydrated 相同） |
| `runStateRef` / `resolvedQueryContextRef` | 可选；Hydrated **以对象为准**，ref 仅追踪 |

### 7.4.1 Hydrated 最小成功上下文（必填小结，C-29）

与 §7.1–§7.4 表一致；成功路径上 **须**满足：

- **`AiRunState`**：**`runId` / `conversationId` / `userId` / `departmentId`（1）/ `distributerId` / `resolvedQueryContext`（与下面同一快照）/ `toolResults`（空 Map）/ `dishProfitPath=true`**；GROUP / 它域 path flags **false**。
- **`AiResolvedQueryContext`**：**`timeWindow`**；**`orgScope`** = **STORE**，**`currentStoreDepartmentId` / `requestDepartmentId` = 1`**，**`visibleStores`** 含 **AAA**；**`dataScope`** 单店 filled（**`queryStoreIds`、`expandedSqlDepartmentIds`、`storeRootDepartmentIds`、`visibleStoreIds`** 等，`SQL_DOMAIN_DISH_PROFIT` 侧 IN 非空）；**`queryIntent`** = **`DISH_PROFIT`** + **`PATH_DISH_PROFIT`** + **`structuredIntentDetail=dish_profit_overview`**；**`effectiveIntentCode` / `effectivePathCode`** 对齐；**`mentionedDishName=null`**；**`dishProfitMetricType=OVERVIEW`**（或与 overview wire 一致）。
- **`DishProfitPlannerExecutionContext`**：**`runState` / `resolvedQueryContext` / `plannerReadRequest`**（§7.3）及 **`userId` / `departmentId` / `distributerId` / `conversationId` / `runId`** 与 Run 对齐。
- **`DishProfitPlannerReadRequest`**：**STORE**、`visibleStores` **AAA**、**`queryDepartmentIds=[1]`**、**`targetStoreDepartmentId=1`**、时间窗与 **`structuredIntentDetail=dish_profit_overview`**、**`mentionedDishName=null`**、**`dishProfitMetricType`** 与 overview 一致。

### 7.5 完整调用链（**C-29 已落地**：PlannerExecutor → 真实 `dish_profit_analysis`）

```text
PlannerExecutor（步 executionMode=ADAPTER）
  → DishProfitPlannerAgentAdapter#invoke(PlannerAgentAdapterRequest)
       → DishProfitPlannerRealReadBridge#readWithExecutionContext(DishProfitPlannerExecutionContext)
            → BusinessToolExecutionRequestResolver#buildDishProfitRequestContext(state, resolvedQueryContext)
            → DishProfitQueryToolExecutor#executeDishProfitAnalysis(…)
            → DishProfitAnalysisTool（内部 GbDepFoodBusinessInsightService#buildInsight）
            →（成功：toolResults[dish_profit_analysis] 信封 success=true）
            → DishProfitAnswerPlanBuilder#attachForAgentEnvelope(state, false)
```

**禁止**：Bridge 内解析 **`userMessage`**、绕过 **`buildDishProfitRequestContext`**、直连 Service/SQL。

### 7.6 验收（curl / Harness 摘要）

| 情形 | 期望 |
|------|------|
| **DB 有 Insight 菜品数据、权限与范围通过** | **`dish_profit` 步 `SUCCESS`**，**`overallStatus=SUCCESS`**，**`degradedSteps=[]`**，**`usedTools` 含 `dish_profit_analysis`**，摘要 **`plannerDishProfitAdapterHonesty=REAL_BRIDGE_HYDRATED_DISH_PROFIT_TOOL_OK`**（**C-29** Harness 常量；与 Stock **`REAL_BRIDGE_HYDRATED_STOCK_REDUCE_TOOL_OK`** 对称） |
| **无数据 / Insight 空 / 字段不足 / 权限拒绝** | **可控 `DEGRADED` 或 Tool `success=false`**，**不抛未捕获异常**，**不**将失败标为 **`SUCCESS`**；摘要诚实降级 **`REAL_BRIDGE_HYDRATED_DISH_PROFIT_TOOL_DEGRADED`**（或细分原因码，**不**假装 Fake OK） |

### 7.7 当前限制（C-29 文档收口）

下列为 **C-29** 时点的 **明确未验证 / 未接入** 范围（避免与 curl SUCCESS 混淆）：

1. **只验证** **`AiResolvedOrgScope.SCOPE_STORE`** **单店**（`departmentId` / 门店根 **1**、**AAA**）；**GROUP 多门店** 见 **§7.10 C-47**（**`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_GROUP_HYDRATED_CORE`**），**与 C-29 STORE case 分列**。
2. **只验证** **菜品毛利概览** wire **`dish_profit_overview`**（**`structuredIntentDetail`** + **`dishProfitMetricType=OVERVIEW`**）；**未验证**指定菜品详情（**`mentionedDishName`** 非空等）。
3. **未验证**毛利率 **最高 / 最低** 排行 wire（**`dish_profit_ranking_high_margin` / `dish_profit_ranking_low_margin`** 等）。
4. **未验证**销量排行 / 销售额排行（**`dish_sales_ranking`** 等；**不**将销量排行冒充销售额排行）。
5. **未验证**成本排行（理论 / 实付等）。
6. **未验证**异常菜品分析 / 关切类组合语义。
7. **第二** 步 **`RecommendationPlannerMockAgentAdapter`** **仍是 mock**，**不**代表生产建议链路。
8. **未接** **`MasterBusinessAgent`** **生产主 Graph**；本 case **仅** Harness + **`PlannerExecutor`** 调试短路。
9. **不**改 **Resolver / Composer** 主逻辑；语义由 Harness **物化** **`AiResolvedQueryContext`** 注入。
10. **C-26 Fake**、**C-27 非 Hydrated RealBridge 骨架** behaviour **不变**。

### 7.8 实装收口说明（C-29）

**`DishProfitPlannerRealReadBridge`**：见源码；**`PlannerExecutor#sanitizePlanForTrace`** 不在 trace 中保留完整 **`AiRunState` / `AiResolvedQueryContext`**。**Harness 摘要**：成功 **`plannerDishProfitAdapterHonesty=REAL_BRIDGE_HYDRATED_DISH_PROFIT_TOOL_OK`**；否则 **`REAL_BRIDGE_HYDRATED_DISH_PROFIT_TOOL_DEGRADED`** + `plannerDishProfitAdapterNote`。

### 7.10 C-47：`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_GROUP_HYDRATED_CORE`（GROUP Hydrated 单域探测）

**目标**：与 **C-44/C-45/C-46** 同构 — **仅** **`DishProfitPlannerRealReadBridge`** → **`dish_profit_analysis`**；**不接** Composite / 其他域 / LLM。**caseId**：**`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_GROUP_HYDRATED_CORE`**；类 **`AiPlannerExecutorDishProfitAdapterGroupHydratedGraphCase`**；**`resolveReplayMode`** 仍属 **`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER`**。

| 区域 | 与 C-29 差异要点 |
|------|------------------|
| **`AiResolvedOrgScope`** | **`SCOPE_GROUP`**；**`currentStoreDepartmentId` / `requestDepartmentId`**：**`null`**；**`visibleStores`**：**AAA(1)** + **汀兰餐厅(3)** |
| **`dataScope`** | **`AiResolvedDataScope.fromOrgScope(org)`** |
| **`AiRunState`** | **`departmentId=null`**；**`distributerId=2`**；**`dishProfitPath=true`**；**`toolResults`** 初值空 **`Map`**；**须** **`aiUserContext`**： **`AiRoleCodes.GROUP_MANAGER`** + **`permissions`** = **`AiRoleMapper.permissionsForAiRole(GROUP_MANAGER)`** — 因 **`shouldRouteGroupWideBusinessOverview`** 在 **`getAiUserContext()==null`** 时 **直接 false**（若仅 **`SCOPE_GROUP`** 而无 user，集团广角 **不开**；**非**用单店结果冒充 GROUP） |
| **`DishProfitPlannerReadRequest`** | **`scopeType=GROUP`**；**`queryDepartmentIds=[1,3]`**；**`targetStoreDepartmentId=null`**；**`structuredIntentDetail=dish_profit_overview`**；**`mentionedDishName=null`**；**`dishProfitMetricType=OVERVIEW`** |
| **诚实摘要** | **`REAL_BRIDGE_HYDRATED_DISH_PROFIT_GROUP_TOOL_OK`** / **`REAL_BRIDGE_HYDRATED_DISH_PROFIT_GROUP_TOOL_DEGRADED`** |

Tool / Insight **真不支持** GROUP 或环境无数据时 **必须 DEGRADED**，**禁止**静默改 **`SCOPE_STORE`** + AAA **假成功**。

---

**原 7.8 待核实表（设计期）已归档**：实现以 **`AiPlannerExecutorDishProfitAdapterRealBridgeHydratedGraphCase`** 与 **`DishProfitPlannerRealReadBridge`** 为准。

### 7.9 跨域：四条 Hydrated RealBridge（curl 均已跑通，C-29 止）

| 域 | caseId（Hydrated） | Tool ID（真实侧） |
|----|-------------------|-------------------|
| 营收 | **`PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`** | **`revenue_query`** |
| 采购 | **`PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`** | **`purchase_overview`** |
| 出库/核销 | **`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`** | **`stock_reduce_query`** |
| 菜品毛利 | **`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`** | **`dish_profit_analysis`**（**STORE**，C-29） |
| 菜品毛利 | **`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_GROUP_HYDRATED_CORE`** | **`dish_profit_analysis`**（**GROUP**，C-47 / §7.10） |

**C-29** 与 **C-47** 均经 **`PlannerExecutor` 短路**，**不经** Master；C-29 为单店，C-47 为 GROUP 探测（诚实 **`…_GROUP_TOOL_*`**）。

---

## 8. Harness `caseId`（**C-26/C-27 已注册**；**C-29 / C-47 Hydrated 已注册**）

| caseId | 状态 |
|--------|------|
| **`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_CORE`** | **Removed（P1-B2a）** — 替代：FAKE_OK / HYDRATED / GROUP / Composite strict |
| **`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_FAKE_OK_CORE`** | **C-26** — `FakeDishProfitPlannerReadBridge`；步 **`SUCCESS`**；**`plannerDishProfitAdapterHonesty=FAKE_READ_BRIDGE_OK`** |
| **`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_CORE`** | **Removed（P1-B2a）** — 替代：`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_CORE` / GROUP / Composite strict |
| **`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`** | **C-29** — **`AiPlannerExecutorDishProfitAdapterRealBridgeHydratedGraphCase`**；**Spring Bean** **`DishProfitPlannerRealReadBridge`**；步 **`step_dish_profit_adapter_hydrated`**；真实 **`dish_profit_analysis`**；摘要 **`REAL_BRIDGE_HYDRATED_DISH_PROFIT_TOOL_OK`** / **`…_DEGRADED`**；**STORE** |
| **`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_GROUP_HYDRATED_CORE`** | **C-47** — **`AiPlannerExecutorDishProfitAdapterGroupHydratedGraphCase`**；同上 Bean；**`scopeType=GROUP`**、双店 **1+3**；**`AiUserContext=GROUP_MANAGER`**（§7.10）；摘要 **`REAL_BRIDGE_HYDRATED_DISH_PROFIT_GROUP_TOOL_OK`** / **`…_GROUP_TOOL_DEGRADED`** |

**`AiHarnessReplayMode`**：**`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER`**（**C-26/C-27/C-29/C-47** 已注册于 `isPlannerExecutorMockHarnessCase`）。

---

## 9. `DishProfitPlannerRealReadBridge` 接线（**C-29 Hydrated**）

完整栈（含 **`PlannerExecutor` → `DishProfitPlannerAgentAdapter`**）见 **§7.5**。下列为 Bridge 之后的 **Tool / AnswerPlan** 段缩影：

```text
PlannerExecutor（ADAPTER）
  → DishProfitPlannerAgentAdapter#invoke
       → DishProfitPlannerRealReadBridge#readWithExecutionContext
            → BusinessToolExecutionRequestResolver#buildDishProfitRequestContext(runState, resolvedQueryContext)
            → DishProfitQueryToolExecutor#executeDishProfitAnalysis(...)
            → DishProfitAnalysisTool（GbDepFoodBusinessInsightService#buildInsight）
            → DishProfitAnswerPlanBuilder#attachForAgentEnvelope(state, false)
```

---

## 10. 参考文档与源码路径

- **`docs/ai/dish-profit-answer-plan.md`** — AnswerPlan 类型与 Composer 契约  
- **`docs/ai/harness-composer-architecture.md`** — 分层  
- `com.nongxinle.ai.agent.business.DishProfitAgent`  
- `com.nongxinle.ai.graph.business.DishProfitQueryToolExecutor`  
- `com.nongxinle.ai.graph.business.toolrequest.BusinessToolExecutionRequestResolver#buildDishProfitRequestContext`  
- `com.nongxinle.ai.tool.business.DishProfitAnalysisTool`  
- `com.nongxinle.ai.graph.business.DishProfitAnswerPlanBuilder`  
- `com.nongxinle.ai.conversation.AiQuerySemanticLexicon`  

---

**文档版本**：**C-25** + **C-26** + **C-27** + **C-28** §7 设计 + **C-29** **`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`** **已实装并已 curl 验收**（Bridge + Harness + 摘要诚实 **`REAL_BRIDGE_HYDRATED_*`**；§7.0、§7.7、§7.9）。
