# StockReduce Planner Adapter — C-20 梳理 + C-21 DTO/Fake + C-22 RealBridge 骨架 + C-23/C-24 **Hydrated RealBridge（已实装）**

> **Removed（P1-B Final）**：单域 Harness caseId 与 **`FakeStockReducePlannerReadBridge`** 已删；主验收用 Composite strict（C-35 / C-48 / C-42）；物化见 **`PlannerCompositeHarnessContext`**。下文单域 curl 仅历史参考。

**状态**：**C-20** 梳理 **`stock_reduce_query`** 链路；**C-21** DTO、Fake Adapter、Harness FAKE_OK；**C-22** **`StockReducePlannerRealReadBridge`**（`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_CORE` **Removed P1-B2a**）。**C-24** 实装并 **Harness curl Replay 已验收**：**`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`** 下 **STORE 单店（`departmentId=1` / AAA）** 真实调用 **`stock_reduce_query`**，**`overallStatus=SUCCESS`**、**`degradedSteps=[]`**、出库步 **SUCCESS**、**`usedTools` 含 `stock_reduce_query`**、**`plannerStockReduceAdapterHonesty=REAL_BRIDGE_HYDRATED_STOCK_REDUCE_TOOL_OK`**（见 **§7.3.0**）。**C-46**：独立 Harness **`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_GROUP_HYDRATED_CORE`** — **GROUP** 双店 + **`groupStockReduceQuery=true`**（见 **§7.3.10**）。**仍**未接 Master / Resolver / Composer **主链路**。  
**对标**：[`purchase-planner-adapter-design.md`](./purchase-planner-adapter-design.md)（采购）、`planner-executor-v1-design.md` §12 / §25。

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
6. **C-20/C-21**：不接 **`MasterBusinessAgent`**、不改 Resolver/Composer **主逻辑**。

---

## 7. Harness 范围（**设计预期**）

### 7.1 v1：**STORE 单店**（Hydrated 最小上下文见 **§7.3**）

- `orgScope.scopeType = STORE`，**`AiRunState.groupStockReduceQuery = false`**（与 `buildHarnessToolArgs` 非集团分支一致）。
- **`visibleStores` 仅 1 条**（与 `StockReduceQueryTool.resolveHarnessSingleStoreFatherId` 注释一致：单店锚点以 visible 为准）。
- `distributerId`：满足 Tool **`disId`** 校验（占位 ID 以环境为准，参见采购 §11.2）。
- **`purchaseOverviewPath` 对称物**：出库可选 `stockReduceQueryPath`；**RealBridge 若仅调 Executor**，可按采购 C-19 策略 **默认 false**，以 **`toolResults` 写入 + attach** 为准；若实测 attach 门前置条件过严再 **仅 Hydrated case** 置位并文档化。

### 7.2 Harness caseId（**C-21**：CORE / FAKE_OK；**C-22**：REAL_BRIDGE_CORE 骨架）

| caseId | 类 | 预期行为 |
|--------|-----|----------|
| `PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_CORE` | — | **Removed（P1-B2a）**；原 `AiPlannerExecutorStockReduceAdapterGraphCase` 已删；替代：FAKE_OK / HYDRATED / GROUP / Composite strict |
| `PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_FAKE_OK_CORE` | `AiPlannerExecutorStockReduceAdapterFakeOkGraphCase` | `FakeStockReducePlannerReadBridge`；首步 `SUCCESS`；`plannerStockReduceAdapterHonesty=FAKE_READ_BRIDGE_OK`；trace `usedTools` 含 `stock_reduce_query` |
| `PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_CORE` | — | **Removed（P1-B2a）**；原 `AiPlannerExecutorStockReduceAdapterRealBridgeGraphCase` 已删；替代：`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE` / GROUP / Composite strict |
| `PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE` | `AiPlannerExecutorStockReduceAdapterRealBridgeHydratedGraphCase` | **`StockReducePlannerRealReadBridge`（Spring Bean）**；物化最小 `AiRunState`/`AiResolvedQueryContext` → 真实 **`executeStockReduceQuery`**；成功则 `plannerStockReduceAdapterHonesty=REAL_BRIDGE_HYDRATED_STOCK_REDUCE_TOOL_OK`；失败诚实 **`REAL_BRIDGE_HYDRATED_STOCK_REDUCE_TOOL_DEGRADED`**（非异常）；轮次 `pass` 当且仅当 `overallStatus=SUCCESS`；**未**设 `stockReduceQueryPath`（Tool 写入后 `attachIfApplicable` 仍触发，见 §2.4） |
| `PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_GROUP_HYDRATED_CORE` | `AiPlannerExecutorStockReduceAdapterGroupHydratedGraphCase` | **C-46**：**GROUP** + **`groupStockReduceQuery=true`**（**`StockReduceQueryToolExecutor#buildHarnessToolArgs`**：`ARG_GROUP_STOCK_REDUCE_AGGREGATION` + 多店 **`ARG_RESOLVED_DEPARTMENT_IDS`**）→ 真实 **`stock_reduce_query`**；诚实 **`REAL_BRIDGE_HYDRATED_STOCK_REDUCE_GROUP_TOOL_OK`** / **`REAL_BRIDGE_HYDRATED_STOCK_REDUCE_GROUP_TOOL_DEGRADED`** |

**推断模式**：`AiHarnessReplayMode.PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER`（**C-21/C-22/C-24/C-46**）。

---

### 7.3 `PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`（**C-24 已实装**，STORE 单店）

**范围**：对齐 **采购 C-19 Hydrated** / **营收 Hydrated**：Harness 计划内物化最小公共上下文，**`StockReducePlannerRealReadBridge#readWithExecutionContext`** 调用 **`StockReduceQueryToolExecutor#executeStockReduceQuery`**，不经 Master / 生产 Graph / Resolver 主链路。

**刻意不做**：C-24 **本 case 不**验 **GROUP**（**C-46 §7.3.10** 独立验证）；耗用/废弃/损失/退货单独筛选；商品金额/次数/重量排行；新 SQL；用户原文 contains/regex。

#### 7.3.0 C-24 curl 验收（已观测）

在 **`caseId = PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`**、**STORE** 组织范围、门店 **AAA**（**`departmentId` / 可见门店根 = `1`**）下，Harness Replay **已成功**走通真实 Tool：

| 观测项 | 值 |
|--------|-----|
| `overallStatus` | **`SUCCESS`** |
| `degradedSteps` | **`[]`** |
| 出库步（`step_stock_reduce_adapter_hydrated`） | **`SUCCESS`** |
| `plannerExecutorTrace.usedTools` | **含 `stock_reduce_query`**（与 `AiBusinessToolIds.STOCK_REDUCE_QUERY` 一致） |
| 摘要 `plannerStockReduceAdapterHonesty` | **`REAL_BRIDGE_HYDRATED_STOCK_REDUCE_TOOL_OK`** |
| **Tool 口径** | Harness 路径 **`totalsBasis = CALENDAR_NATURAL_DAY`**（自然日历日四类合计；见 §3.1 / `StockReduceQueryTool`） |

**`StockReducePlannerRealReadBridge`**（Spring Bean）在此 case 中 **复用生产同源链路**：`buildStockReduceRequestContext` → **`executeStockReduceQuery`** → **`StockReduceQueryTool`** → **`StockReduceAnswerPlanBuilder#attachIfApplicable`**（完整串见 **§7.3.6**）。

#### 7.3.1 CaseId 与 Harness 形状

- **caseId**：`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`
- **类**：`AiPlannerExecutorStockReduceAdapterRealBridgeHydratedGraphCase`
- **Executor**：`PlannerExecutor(ADAPTER)` + `StockReducePlannerAgentAdapter`（**Spring 注入** `StockReducePlannerRealReadBridge`；`AiHarnessReplayPlannerExecutorMock.replay(..., stockReducePlannerRealReadBridge)`；**非** C-22 的 `new`）
- **计划字段**：`stockReduceReadRequest` + `stockReduceExecutionContext`，其中 `executionContext` 内 **`runState` / `resolvedQueryContext` 非空**（与 C-22 缺 Hydrate 对照）

#### 7.3.2 `AiRunState` 最小字段（STORE 单店 Harness）

与采购 Hydrated 对齐常数：**`departmentId = 1`**（AAA 门店根）、**`distributerId = 2`**（满足 Tool `disId` 非空；**环境不一致时换为库内真实分销商 ID**，与 `purchase-planner-adapter-design.md` §11.2 同策略）。

| 字段 | 设计值 / 说明 |
|------|----------------|
| `runId` | 与 Harness 首轮 synthetic 对齐，如 `9_000_000L`（或 `Long.parseLong(ctx.getRunId())` 可解析的长整型） |
| `conversationId` | `0L`（Harness synthetic） |
| `userId` | `1L`（与采购 Hydrated 一致；须满足 `AiPermissionGuard` 可放行 **STOCK_REDUCE_QUERY**） |
| `departmentId` | **`1L`** |
| `distributerId` | **`2L`**（环境校准） |
| `resolvedQueryContext` | 与 §7.3.3 **同一对象引用**（与 `state.resolvedQueryContext` 一致） |
| `toolResults` | **`new HashMap<>()`**，由 Executor 写入 `stock_reduce_query` 结果 |
| `groupStockReduceQuery` | **`false`**（单店；为 `true` 会走集团聚合分支，**本 Hydrated v1 不验证**） |
| `stockReduceQueryPath` | **默认 `false`**（对称采购 C-19：`purchaseOverviewPath` 未强制）；仅以 Resolver + `executeStockReduceQuery` + `attachIfApplicable` 为准。**若实装后某分支必须置 `true`，仅限本 GraphCase 显式写入并回修本文** |
| `businessDiagnosisPath` | **`false`**（默认）；仅当未来复用诊断内嵌叙事时再议 |
| 其他 path / `group*` | 保持默认 **`false`**，避免误触其他 Tool 链 |

可选（**建议实装前核对 `PurchasePlannerRealReadBridge.hydrateRunStateFromContext`**）：从 `AiResolvedQueryContext` 反灌 `statStartDate` / `statEndDate`（yyyy-MM-dd），减少 `resolveStartDateIso` 对 `state` 的依赖。

#### 7.3.3 `AiResolvedQueryContext` 最小字段（STORE · AAA）

| 区域 | 字段 | 设计值 / 说明 |
|------|------|----------------|
| 顶层 | `runId` | 与 `AiRunState.runId` **一致**（便于日志） |
| 顶层 | `userId` | `1L`（与 state 一致） |
| `timeWindow` | `startDate` / `endDate` | 固定窗口，如 `2026-05-01`～`2026-05-14`（`LocalDate`） |
| `timeWindow` | `timeLabel` | 人类可读区间说明（与 Harness 消息一致即可） |
| `orgScope` | `scopeType` | **`AiResolvedOrgScope.SCOPE_STORE`**（即 `STORE`） |
| `orgScope` | `currentStoreDepartmentId` | **`1L`** |
| `orgScope` | `requestDepartmentId` | **`1L`** |
| `orgScope` | `visibleStores` | **单元素** `AiStoreScopeDTO`：`storeDepartmentId=1`，`storeName="AAA"` |
| `queryIntent` | `intentCode` | **`AiResolvedQueryIntent.STOCK_REDUCE_QUERY`**（`"STOCK_REDUCE_QUERY"`） |
| `queryIntent` | `pathCode` | **`AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY`**（`"stock_reduce_query_path"`） |
| `queryIntent` | `structuredIntentDetail` | **`AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY`**（wire 值 **`"stock_reduce_overview"`**） |
| 顶层 | `effectiveIntentCode` / `effectivePathCode` | 与 `queryIntent` 对齐（`STOCK_REDUCE_QUERY` / `PATH_STOCK_REDUCE_QUERY`） |

**`AiResolvedDataScope`（C-24 已设最小）**：`BusinessToolExecutionRequestResolver#buildStockReduceRequestContext` 在 `dataScope != null` 时读取 `sqlDepartmentIdsForDomain(STOCK_REDUCE)` 等。C-24 **`AiPlannerExecutorStockReduceAdapterRealBridgeHydratedGraphCase`** 内已补 **STORE 单店最小 `dataScope`**（`queryScopeKind=STORE`、`queryStoreIds`/`expandedSqlDepartmentIds`/`storeRootDepartmentIds` 与部门 **1** 对齐），与 **`orgScope`** 一致，避免 Resolver debug 中 `stock_reduce` SQL 域全空（**仍禁止**改 Resolver 主逻辑 / 新 SQL）。

#### 7.3.4 `StockReducePlannerReadRequest`（与计划切片一致）

| 字段 | 设计值 |
|------|--------|
| `resolvedQueryContextRef` | Harness 占位 ref（与 `PlannerExecutionPlan.resolvedContextRef` 对齐） |
| `timeStart` / `timeEnd` | 与 `AiResolvedQueryContext.timeWindow` **同一对 LocalDate** |
| `timeLabel` | 与 `timeWindow.timeLabel` 可同文案 |
| `scopeType` | **`STORE`** |
| `visibleStores` | 单店：`StockReducePlannerVisibleStore(departmentId=1, displayLabel="AAA")` |
| `queryDepartmentIds` | **`List.of(1L)`** |
| `targetStoreDepartmentId` | **`1L`** |
| `reduceType` | **`StockReduceAnswerPlan.REDUCE_TYPE_ALL`**（`"ALL"`，四类型合计 / 概览口径；**非**单类型筛选） |
| `structuredIntentDetail` | **`STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY`**（`stock_reduce_overview`） |
| `totalsBasis` | **`CALENDAR_NATURAL_DAY`**（与 Fake/C-21 Harness 一致） |
| `answerPlanRef` | Harness 占位 ref |

#### 7.3.5 `StockReducePlannerExecutionContext`

| 字段 | 说明 |
|------|------|
| `runState` | §7.3.2 物化的 **`AiRunState`（非 null）** |
| `resolvedQueryContext` | §7.3.3 物化的 **`AiResolvedQueryContext`（非 null）** |
| `plannerReadRequest` | §7.3.4 的 **`StockReducePlannerReadRequest`**（与 `PlannerExecutionPlan.stockReduceReadRequest` 同内容或可共享 builder） |
| `userId` / `departmentId` / `distributerId` / `conversationId` / `runId` | 与 state / Harness 对齐（便于 Bridge 与采购对称 `resolveRunId`） |

#### 7.3.6 C-24 真实调用链（PlannerExecutor → Tool，已跑通）

Harness **不经** `MasterBusinessAgent`、**不经** 生产 Resolver/图；仅 **ADAPTER** 模式下：

```text
PlannerExecutor（ADAPTER + PlannerAgentAdapterStepExecutor）
  → StockReducePlannerAgentAdapter#invoke
       → StockReducePlannerRealReadBridge#readWithExecutionContext
            → （校验 + hydrateRunStateFromContext）
            → BusinessToolExecutionRequestResolver#buildStockReduceRequestContext(runState, resolvedQueryContext)
            → StockReduceQueryToolExecutor#executeStockReduceQuery(runId, state, deptForScopedTools, dis, start, stop, envelopes)
            → StockReduceQueryTool
            → StockReduceAnswerPlanBuilder#attachIfApplicable(runState)
  → （第 2 步）RecommendationPlannerMockAgentAdapter（建议步仍为 mock）
```

**`deptForScopedTools` / `dis` / `start` / `stop`**：从 **`StockReduceToolRequestContext`** 与 **`state.getDistributerId()`**（及 ExecutionContext 回填）抽取（对称 **`PurchasePlannerRealReadBridge`**）。

#### 7.3.7 最小成功上下文 · 必填字段（C-24 GraphCase）

下列字段共同构成 **curl 已验证** 的最小闭环（缺一不可的含义以 **Bridge + Resolver + Tool** 实际校验为准；此为审计清单）。

**`AiRunState`（必填）**：`runId`、`conversationId`、`userId`、`departmentId`（**`1L`** / AAA）、`distributerId`（**`2L`** 或环境真实 `disId`）、`resolvedQueryContext`（与下文同一引用）、`toolResults`（**`new HashMap<>()`**，由 Executor 写入 **`stock_reduce_query`**）、`groupStockReduceQuery`（**`false`**）。

**`AiResolvedQueryContext`（必填）**：`orgScope`（**`SCOPE_STORE`**，`currentStoreDepartmentId` / `requestDepartmentId` **= 1**，**`visibleStores`** 单条 AAA）、`timeWindow`（起止 **`LocalDate`** + `timeLabel`）、`queryIntent`（**`STOCK_REDUCE_QUERY`** / **`PATH_STOCK_REDUCE_QUERY`**，**`structuredIntentDetail = stock_reduce_overview`**）、`effectiveIntentCode` / `effectivePathCode` 与意图对齐；**`dataScope`**（C-24 已设 STORE 最小展开，见 §7.3.3）；**`querySemanticParse.metric.stockReduceType = ALL`**（与概览一致）。

**`StockReducePlannerExecutionContext`（必填）**：**非 null** 的 **`runState`**、**`resolvedQueryContext`**、**`plannerReadRequest`**；以及 **`userId` / `departmentId` / `distributerId` / `conversationId` / `runId`（字符串）** 与 Harness 首轮 synthetic 对齐，供 Bridge `resolveRunId`。

**`StockReducePlannerReadRequest`（必填）**：**时间窗**（`timeStart`+`timeEnd` 或 `timeLabel`）、**`scopeType=STORE`**、**可解析范围**（`targetStoreDepartmentId` / `queryDepartmentIds` / `visibleStores` 至少其一含 **`1`**）、**`structuredIntentDetail`** 与 **`stock_reduce_overview`** 对齐、**`reduceType=ALL`**、**`totalsBasis=CALENDAR_NATURAL_DAY`**（结构化标签）、**`resolvedQueryContextRef`** / **`answerPlanRef`**（Harness 句柄）。

#### 7.3.8 验收与诚实摘要字段

| 条件 | 预期 |
|------|------|
| DB 在窗口内**有**出库/核销数据、权限通过、payload 合法 | 出库步 **`SUCCESS`**，`overallStatus` **`SUCCESS`**，`degradedSteps` **`[]`**，`plannerExecutorTrace.usedTools` 含 **`stock_reduce_query`**，摘要 **`plannerStockReduceAdapterHonesty=REAL_BRIDGE_HYDRATED_STOCK_REDUCE_TOOL_OK`**（命名对称 `REAL_BRIDGE_HYDRATED_PURCHASE_TOOL_OK`） |
| 无数据、字段不足、权限拒绝、Tool `success=false`、`AnswerPlan` attach 失败（如 `debug.failureReason`） | **`DEGRADED`**（Bridge 信封），**不得**未捕获异常、**不得**伪 **`SUCCESS`**；摘要 **`REAL_BRIDGE_HYDRATED_STOCK_REDUCE_TOOL_DEGRADED`** |

轮次 **`pass`**：对称采购 Hydrated — 当且仅当 **`overallStatus == SUCCESS`**。

#### 7.3.9 C-24 后可选观测项（非阻塞）

1. **`AiUserContext`**：`executeStockReduceQuery` 在 **`state.getAiUserContext()==null`** 时守卫仍可放行；若未来收紧策略，仅在 **GraphCase** 补最小 `AiUserContext`。  
2. **`stockReduceQueryPath`**：C-24 **未**置 `true`；附着依赖 **`toolResults` 已含 `stock_reduce_query`**（见 §2.4）。  
3. **环境 `distributerId`**：文档占位 **2**；生产库不一致时需换为真实 **`disId`**（同采购 §11.2）。

---

#### 7.3.10 C-46：`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_GROUP_HYDRATED_CORE`（GROUP Hydrated 探测）

| 项 | 规格 |
|----|------|
| **类** | **`AiPlannerExecutorStockReduceAdapterGroupHydratedGraphCase`** |
| **`scopeType` / `visibleStores`** | **`GROUP`**；门店根 **1 / AAA**、**3 / 汀兰餐厅** |
| **`AiResolvedDataScope`** | **`fromOrgScope(org)`**（**`allVisibleStores=true`**，`expandedSqlDepartmentIds` 含多店） |
| **`queryIntent` / 语义** | **`STOCK_REDUCE_QUERY`** / **`PATH_STOCK_REDUCE_QUERY`**；**`structuredIntentDetail=stock_reduce_overview`**；**`querySemanticParse.metric.stockReduceType=ALL`** |
| **`AiRunState`** | **`departmentId=null`**（**不**作单店 SQL 锚点）；**`distributerId=2`**（与 §7.3.2 占位一致）；**`groupStockReduceQuery=true`** — **须**与 **`StockReduceQueryToolExecutor#buildHarnessToolArgs`** 集团分支一致（**C-24 STORE 为 `false`**）；**`resolvedQueryContext`** 与 **`StockReducePlannerExecutionContext.resolvedQueryContext`** **同一引用** |
| **`StockReducePlannerReadRequest`** | **`scopeType=GROUP`**；**`queryDepartmentIds=[1,3]`**；**`targetStoreDepartmentId=null`**；**`reduceType=ALL`**；**`totalsBasis=CALENDAR_NATURAL_DAY`** |
| **诚实摘要** | **`REAL_BRIDGE_HYDRATED_STOCK_REDUCE_GROUP_TOOL_OK`** / **`REAL_BRIDGE_HYDRATED_STOCK_REDUCE_GROUP_TOOL_DEGRADED`** |
| **Replay 观测**（根摘要） | **`harnessStockReduceGroupVisibleStoreRootDepartmentIds`**；**`harnessStockReduceQueryGrandTotalFourTypes`** / 分型合计 / **`harnessStockReduceTotalsBasis`**；**`harnessStockReduceFocusRowsSize`** 等（见 GraphCase） |

**注册**：**`AiHarnessBuiltinCases`** + **`isPlannerExecutorMockHarnessCase`**；**`AiHarnessReplayPlannerExecutorMock`**；**`AiHarnessReplayService#resolveReplayMode`** → **`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER`**（与 C-24 同族）。

---

### 7.4 当前范围与限制（**C-24** STORE · **C-46** GROUP 切片）

| 项 | 说明 |
|----|------|
| **组织** | **C-24**：**STORE** 单店（AAA）。**C-46**：独立 Harness 探测 **GROUP** 双店（**不**替代 C-24 回归）。 |
| **语义** | 均验证 **`stock_reduce_overview`**（`STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY`），非生产耗用/废弃/损失/退货单独 wire。 |
| **口径** | Harness 路径 **`totalsBasis = CALENDAR_NATURAL_DAY`**（四类自然日合计）；**未**验证 `DAILY_REVENUE_DAYS_ONLY` 旧径。 |
| **未验证（两 case 外）** | 单类型筛选；商品金额/次数/**重量**排行等其它 Harness 变体。 |
| **计划第 2 步** | **`RecommendationPlannerMockAgentAdapter`** 仍为 **mock**，非生产建议链路。 |
| **主链路** | **未**接 **`MasterBusinessAgent`**；**未**改 Resolver / Composer **主逻辑**。 |

---

## 8. 参考路径（源码）

- `com.nongxinle.ai.agent.business.StockReduceAgent`
- `com.nongxinle.ai.graph.business.StockReduceQueryToolExecutor`
- `com.nongxinle.ai.tool.business.StockReduceQueryTool`
- `com.nongxinle.ai.graph.business.toolrequest.BusinessToolExecutionRequestResolver#buildStockReduceRequestContext`
- `com.nongxinle.ai.graph.business.StockReduceAnswerPlanBuilder`
- `com.nongxinle.ai.dto.business.StockReduceAnswerPlan`
- `com.nongxinle.service.GbDepartmentGoodsStockReduceService`
- `com.nongxinle.ai.conversation.AiQuerySemanticLexicon`（出库 wire 常量）

---

## 9. 后续 Adapter 模板：**`DishProfitPlannerRealReadBridge`**

对齐 **采购 / 营收 / 出库** Hydrated RealBridge 模式：**`DishProfitPlannerRealReadBridge#readWithExecutionContext`** 应 **复用既有生产链路** — `BusinessToolExecutionRequestResolver` 侧 **`buildDishProfitRequestContext`**（或等价解析入口）、**`DishProfitQueryToolExecutor`**（或当前 Agent 使用的执行器）、**`AiBusinessToolIds.DISH_PROFIT_ANALYSIS`** 对应 **Tool**、**`DishProfitAnswerPlanBuilder#attachIfApplicable`**（或项目内等价 AnswerPlan 附着）。**禁止**：Bridge 内 **新写 SQL**、直连 Mapper/Service 绕过 Tool、对用户原文做 contains/regex。Harness：**独立 `caseId` + Hydrated GraphCase** 物化最小 **`AiRunState` + `AiResolvedQueryContext` + ExecutionContext + ReadRequest**，经 **`AiHarnessReplayService`** 注入 Spring Bean（对称 C-24）。

---

**文档版本**：**C-20～C-22** 已落地（含 RealBridge 骨架）；**C-24** Hydrated RealBridge **已实装且 curl 已验收**（§7.3.0）；**C-46** GROUP Hydrated 单域探测 **已注册**（§7.3.10）。  
**下一阶**：其它排行 / 单类型等变体（独立 caseId）；**DishProfit** 见 **§9**。
