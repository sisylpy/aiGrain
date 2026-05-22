# 组合型经营诊断 Composite Plan

> **读者**：Harness / Planner 工程师。  
> **现网**：六步 **`PlannerExecutionPlan`**（**`CompositeBusinessDiagnosisStepIds`**）+ **`PlannerExecutor`** + 四域 RealBridge + **`BusinessDiagnosisCompositeAnswerPlanBuilder`**（C-49）+ **`BusinessDiagnosisCompositeReadonlyComposer`**（C-51）已落地。  
> **生产**：**`BusinessDiagnosisCompositeProductionGate`** + **`BusinessDiagnosisCompositeExecutionService`**（**`HARNESS_ONLY` / `SHADOW`**，默认 **`shadow.enabled=false`**）；**不替换**生产 **`finalAnswerText`**（见 execution 设计文档）。  
> **Harness 主验收**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE`**（C-35 STORE）、**`…_GROUP_CORE`**（C-48）、**`…_STOCK_DEGRADED_CORE`**（C-42）；**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_CORE`**（C-31 全 MOCK）已移除。  
> **局部待做**：**`step_recommendation`** 仍为 mock；**PRIMARY** 替换终稿、**C-66** dashboard 见路线图。  
> **交叉引用**：**[`planner-executor-v1-design.md`](./planner-executor-v1-design.md)** §27、**[`business-diagnosis-composite-group-design.md`](./business-diagnosis-composite-group-design.md)**、**[`business-diagnosis-production-gate-design.md`](./business-diagnosis-production-gate-design.md)**、**[`business-diagnosis-production-composite-execution-design.md`](./business-diagnosis-production-composite-execution-design.md)**。

---

## 1. 目标与问题形态

设计一个 **固定多步** `PlannerExecutionPlan`，在 **同一 STORE、同一时间窗** 下顺序（或文档预留并行策略占位）拉齐四域读数，再经 **诊断汇总步（mock / skeleton）** 与 **建议步（mock）** 输出可 Replay 的 trace 与统一的 **`finalAnswerPlanType`**，以支撑用户问法例如：

- 「**AAA 这个月经营哪里有问题？**」
- 「**AAA 这个月成本为什么高？**」
- 「**AAA 这个月毛利下降，原因是什么？**」

**原则**：计划由 **模板与配置** 固化，**不**使用 LLM 自由规划步骤；**不**在 Composite 层解析 `userMessage` 做 **contains / regex** 路由（意图仍由 **`AiResolvedQueryContextResolver`** 及上游语义层给出；本设计假设进入 Executor 时 **已有** 与诊断兼容的 **`AiResolvedQueryContext`** 或 Harness **同构物化**）。

---

## 2. caseId **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_CORE`**（Historical / Retired candidate，P1-B B1）

| 常量名 | 值 | 状态 |
|--------|-----|------|
| **Composite Core** | **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_CORE`** | **Historical / Retired candidate** — 非当前主验收；**B2** 可删 |

**C-31 Removed（P1-B Final）**：**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_CORE`** 与 **`AiPlannerExecutorBusinessDiagnosisCompositeGraphCase`** 已删；勿再 curl。

**当前主验收 caseId（P1-B）**：

| caseId | 阶段 |
|--------|------|
| **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE`** | C-35 STORE strict |
| **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE`** | C-48 GROUP strict |
| **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_STOCK_DEGRADED_CORE`** | C-42 出库降级 |
| **`DISH_PROFIT_MATRIX_P1`** | GRAPH 菜品下钻矩阵（非 PlannerExecutor 短路） |

**Replay 诚实字段**（根摘要，与 **`toHarnessSummary`** 一致）：

| 字段 | 值（C-31） |
|------|------------|
| **`plannerCompositeHonesty`** | **`COMPOSITE_SKELETON_ONLY`** |
| **`plannerCompositeNote`** | **`skeleton only; real hydrated adapters not invoked`** |

---

## 2.1～2.3 C-32 / C-33 / C-34（Historical / Retired）

| 阶段 | 原 caseId | 状态 | 替代验收 |
|------|-----------|------|----------|
| **C-32** | `PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_CORE` | **Retired**（P1-A） | **C-35** ALL_REAL 或 **C-48** GROUP |
| **C-33** | `PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_CORE` | **Retired** | 同上 |
| **C-34** | `PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_STOCK_CORE` | **Retired** | 同上；出库降级见 **C-42** STOCK_DEGRADED |

已删除：`CompositeBusinessDiagnosisRevenueHybridPlannerStepExecutor`、`CompositeBusinessDiagnosisRevenuePurchaseHybridPlannerStepExecutor`、`CompositeBusinessDiagnosisRevenuePurchaseStockHybridPlannerStepExecutor` 及对应 GraphCase。Hydrated 步 **`stepId`** 上提至 **`CompositeBusinessDiagnosisStepIds`**。

---

## 2.4 caseId **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE`**（C-35）

| 常量名 | 值 |
|--------|-----|
| **Composite + 四数据域全真实** | **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE`** |

**C-35 已注册**：**`AiHarnessBuiltinCases`**、**`isPlannerExecutorMockHarnessCase`**；**`AiHarnessReplayPlannerExecutorMock`** 专用分支（须 Spring **四** Bean：营收 / 采购 / 出库 / 菜品毛利 RealBridge）；**`CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor`** + **`AiPlannerExecutorBusinessDiagnosisCompositeAllRealGraphCase#buildPlan`**；**`AiHarnessReplayService#resolveReplayMode`** 将该 caseId 归为 **`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER`**（与菜品 Adapter 系摘要一致）。

**Replay 诚实字段**：

| 字段 | 值（C-35） |
|------|------------|
| **`plannerCompositeHonesty`** | **`COMPOSITE_ALL_DATA_REAL_DIAGNOSIS_MOCK`** |
| **`plannerCompositeNote`** | **`revenue, purchase, stock_reduce and dish_profit real hydrated adapters invoked; diagnosis/recommendation remain mock`** |

**trace `usedTools`（典型 SUCCESS）**：含 **`revenue_query`**、**`purchase_overview`**、**`stock_reduce_query`**、**`dish_profit_analysis`** + **`mock_diagnosis_compose`**、**`mock_build_recommendation_plan`**。

**`harnessReplayMode`**：**`PLANNER_EXECUTOR_MOCK`**（P1-B Final）。

**诊断 / 建议两步（C-35 及全部 Composite Harness）**：**`targetAgent`** 可与生产枚举对齐（如 **`business_diagnosis_v1`**），但 **仍为 `MockPlannerStepExecutor` 合成 trace**；**`usedTools`** 恒为 **`mock_*`**，**不**表示已调用生产 LLM 或真实业务 Action — 详见 **`inputSummary` / `acceptanceCriteria`**。

**C-37（本仓库已实装）**：**`step_diagnosis_compose`** 在 **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE`** 下由 **`CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor`** 调用 **`BusinessDiagnosisCompositeAnswerPlanBuilder`**（输入 **`PlannerStepExecutionRequest`**：`planSnapshot`、`priorStepResults`、`degradedStepsSoFar`、各域 **`AiRunState`** 挂载；**不**读 DB、**不**调 LLM）。**`usedTools`** 仍仅 **`mock_diagnosis_compose`**（诚实口径）。Harness 根摘要可选见 **`businessDiagnosisAnswerPlanType`**、**`businessDiagnosisRiskLevel`**、**`businessDiagnosisDataCoverage`**、**`businessDiagnosisCompositeAnswerPlan`**、**`businessDiagnosisSummaryText`**、**`businessDiagnosisSuggestedNextQuestions`**；**`step_recommendation`** 仍为 **`MockPlannerStepExecutor`**。

**C-38（summary 映射增强）**：同一 Builder **优先 AnswerPlan / overview**，**否则**从 **`toolResults`** 解析与生产 Tool 一致的数据形状（营收 **`data.totalRevenue`** / **`storeRevenueRanking`**，采购 **`purchaseOverview`**，出库 inner totals，菜品 **`data.businessInsightSummary`** 等）；缺失写入 **`debug.mappingNotes`**，**不用 0 伪装未知**。

**C-39（最小确定性 `diagnosisSignals`）**：在 **C-38.2** 四域 summary + **`dataCoverage`** 上只做 **保守、可审计** 规则（采购/出库 vs 营收、菜品负毛利率、数据覆盖缺口等）；**`revenueWeakSignal` 恒 `null`**（无同比/环比不接）；**`riskLevel`**：**任一步 `success=false` → `INSUFFICIENT_DATA`**；否则任一对 **`WARNING` 信号 → `MEDIUM`**（实现上承载「warning 档位」）；**仅 `NOTICE` 信号 → `NORMAL_OBSERVATION`**（本仓库 **无** `NOTICE_OBSERVATION` 枚举值）。详见 **`business-diagnosis-answer-plan-design.md` §8.8**。

**C-40（确定性中文 `summaryText`）**：**`BusinessDiagnosisCompositeAnswerPlan.summaryText`** — **短段落**、**仅**拼接已有字段；Harness 根 **`businessDiagnosisSummaryText`** / **`businessDiagnosisSuggestedNextQuestions`**；**不**调 LLM；**不**写「经营无问题」。详见 **`business-diagnosis-answer-plan-design.md` §8.9**。

---

## 2.5 caseId **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_STOCK_DEGRADED_CORE`**（C-42）

| 常量名 | 值 |
|--------|-----|
| **Composite + 出库 Harness 降级** | **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_STOCK_DEGRADED_CORE`** |

**目的**：专项验证 **一单域降级**（`step_stock_reduce_hydrated` = **DEGRADED**）时 **`BusinessDiagnosisCompositeAnswerPlan`**、**`dataCoverage`**、**`riskLevel`**、**`summaryText`**、**`dataIncompleteSignal`** 仍诚实 — **不假 SUCCESS**、**不编造出库数值**、**不把 unknown 当 0**。

**已注册**：**`AiHarnessBuiltinCases`**、**`isPlannerExecutorMockHarnessCase`**；**`AiHarnessReplayPlannerExecutorMock`** 专用分支（须与 ALL_REAL 相同 **四** Bean；出库 Bridge 仍注入但 **本 case 不调用** **`stock_reduce_query`**）；**`CompositeBusinessDiagnosisStockDegradedHarnessHybridPlannerStepExecutor`**（仅 Harness）**包装** **`PlannerAgentAdapterStepExecutor`** + **`AiPlannerExecutorBusinessDiagnosisCompositeStockDegradedGraphCase#buildPlan`**；**`AiHarnessReplayService#resolveReplayMode`** 与同系 **DISH_PROFIT_ADAPTER** 一致。

**Replay 诚实字段**：

| 字段 | 值（C-42） |
|------|------------|
| **`plannerCompositeHonesty`** | **`COMPOSITE_STOCK_DEGRADED_DIAGNOSIS_DETERMINISTIC`** |
| **`plannerCompositeNote`** | **`stock_reduce degraded intentionally for harness; revenue/purchase/dish real; diagnosis deterministic; recommendation mock`** |

**执行语义**：营收 / 采购 / 菜品步与 ALL_REAL **相同**（真实 **`revenue_query`** / **`purchase_overview`** / **`dish_profit_analysis`**）；**`step_stock_reduce_hydrated`** **不调**适配器，返回 **`PlannerStepStatus.DEGRADED`**，**`usedTools=[]`**，**`degradedReason`** 可读（见 **`CompositeBusinessDiagnosisStockDegradedHarnessHybridPlannerStepExecutor.HARNESS_STOCK_DEGRADED_REASON`**）；全 trace **`overallStatus=DEGRADED`**（**`CONTINUE_WITH_DEGRADED`**）。**`BusinessDiagnosisCompositeAnswerPlanBuilder`**：**`dataCoverage.STOCK_REDUCE.success=false`**、**`realToolInvoked=false`**；**`stockReduceSummary=null`**；**`dataIncompleteSignal`** **WARNING**；**`riskLevel=INSUFFICIENT_DATA`**；**`summaryText`** 明示 **出库/核销未完整读取**（**不**宣称四类均已读、**不**写无来源的出库 0）。

---

## 2.6 caseId **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE`**（C-43 规格 + **C-48 实装**）

| 常量名 | 值 |
|--------|-----|
| **Composite + GROUP 多门店** | **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE`** |

**C-48 已注册**：**`AiHarnessBuiltinCases`**、**`isPlannerExecutorMockHarnessCase`**；**`AiHarnessReplayPlannerExecutorMock`**（须与 **C-35** 相同 **四** Bean：营收 / 采购 / 出库 / 菜品 **`*PlannerRealReadBridge`**）；**`CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor`** + **`AiPlannerExecutorBusinessDiagnosisCompositeGroupGraphCase#buildPlan`**；**`AiHarnessReplayService#resolveReplayMode`** 归入 **`PLANNER_EXECUTOR_MOCK`**（与 **ALL_REAL** 同系）。四 **`ExecutionContext`** 由 **`PlannerCompositeHarnessContext.*Group`** 构建；**`resolvedContextRef`** 指向营收 GROUP ref。

**Replay 诚实字段（C-48）**：

| 字段 | 值 |
|------|-----|
| **`plannerCompositeHonesty`** | **`COMPOSITE_GROUP_ALL_DATA_REAL_DIAGNOSIS_DETERMINISTIC`** |
| **`plannerCompositeNote`** | **`group composite; four group hydrated adapters invoked; diagnosis deterministic; recommendation mock`** |

**trace `usedTools`（典型四数据步 SUCCESS）**：**`revenue_query`**、**`purchase_overview`**、**`stock_reduce_query`**、**`dish_profit_analysis`** + **`mock_diagnosis_compose`**、**`mock_build_recommendation_plan`**。**`harnessReplayMode`**：**`PLANNER_EXECUTOR_MOCK`**（P1-B Final）。

**根摘要（与 C-35 同构 + GROUP 增量）**：**`businessDiagnosisSummaryText`**、**`businessDiagnosisRiskLevel`**、**`businessDiagnosisDataCoverage`**、**`businessDiagnosisCompositeAnswerPlan`**、**`visibleStoreRootDepartmentIds`**（默认 **`[1,3]`** 或由营收 **`AiResolvedQueryContext`** 提取）。**`summaryText`**：**GROUP 口径**（**`BusinessDiagnosisCompositeAnswerPlanBuilder`** 在 **`orgScope.scopeType=GROUP`** 时保守拼接；**不**复制 STORE **`scopeLabel`**）。**不**影响 **`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE`**。

**权威规格**：**[`business-diagnosis-composite-group-design.md`](./business-diagnosis-composite-group-design.md)**（含 **§10 C-49** curl 快照与已知限制）。

### C-49（文档收口）

- **`BusinessDiagnosisCompositeAnswerPlanBuilder#BUILDER_VERSION=C-49`**；**`debug.mappingNotes`** 仍 **`phase=C-38.2_zero_vs_missing`**、**`signalsPhase=C-39_minimal_deterministic`**、**`summaryPhase=C-40_deterministic_zh`**。
- **curl 典型 SUCCESS**：**`overallStatus=SUCCESS`**；四域 **`usedTools`** = **`revenue_query` / `purchase_overview` / `stock_reduce_query` / `dish_profit_analysis`**；**`visibleStoreRootDepartmentIds=[1,3]`**（或等价提取）；**`dataCoverage`** 四域 **`success=true` `realToolInvoked=true`**；**`summaryText`** GROUP 口径。
- **限制**：营收 **`totalRevenue`** 集团口径待确认；采购明细少不编排行；菜品 **`AGGREGATED_DISH_PORTFOLIO_FALLBACK`** 保守；建议 **mock**；未接 Master / 前台 / LLM — 详见 **[`business-diagnosis-composite-group-design.md`](./business-diagnosis-composite-group-design.md) §10**。

### C-50 / C-51（Composite Readonly Composer — 已实装）

- **`BusinessDiagnosisCompositeReadonlyComposer`** 只读 **`BusinessDiagnosisCompositeAnswerPlan`**；**不调 LLM**、**不重读 `toolResults`**。契约：**[`business-diagnosis-composer-readonly-design.md`](./business-diagnosis-composer-readonly-design.md)**。

### C-52（生产入口 Composite Gate — 已实装）

- **`BusinessDiagnosisCompositeProductionGate`** 在 **`AiRunService#startRun`** 写入 **`businessDiagnosisCompositeGateResult`**；**只读**结构化 intent/path/scope。**权威**：[**`business-diagnosis-production-gate-design.md`**](./business-diagnosis-production-gate-design.md)。

### C-57～C-63（生产执行编排 — 已实装）

- **`BusinessDiagnosisCompositePlanFactory`** + **`BusinessDiagnosisCompositeExecutionService`**；**`HARNESS_ONLY`**（Harness）与 **`SHADOW`**（普通 Run，**`ShadowPolicy`**）已接线。**权威**：[**`business-diagnosis-production-composite-execution-design.md`**](./business-diagnosis-production-composite-execution-design.md)。

## 3. 步骤一览（至少 6 步）

| 顺序（建议） | `stepId` | 模式 | 复用能力 |
|:--:|----------|------|----------|
| 1 | **`step_revenue_hydrated`** | **ADAPTER** + **RevenuePlannerAgentAdapter** + **RevenuePlannerRealReadBridge**（Bean） | 与 **`PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`** 同构 Hydrated 上下文 → **`revenue_query`** |
| 2 | **`step_purchase_hydrated`** | **ADAPTER** + **PurchasePlannerAgentAdapter** + **PurchasePlannerRealReadBridge**（Bean） | 与 **`PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`** 同构 → **`purchase_overview`** |
| 3 | **`step_stock_reduce_hydrated`** | **ADAPTER** + **StockReducePlannerAgentAdapter** + **StockReducePlannerRealReadBridge**（Bean） | 与 **`PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`** 同构 → **`stock_reduce_query`** |
| 4 | **`step_dish_profit_hydrated`** | **ADAPTER** + **DishProfitPlannerAgentAdapter** + **DishProfitPlannerRealReadBridge**（Bean） | 与 **`PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_CORE`** 同构 → **`dish_profit_analysis`** |
| 5 | **`step_diagnosis_compose`** | **MOCK** 或 **专用 Skeleton Executor**（**不调用 LLM**，**不做复杂推理**） | 仅 **聚合** 前四步已写入 **`AiRunState`** 的 AnswerPlan / `toolResults` **摘要** |
| 6 | **`step_recommendation`** | **MOCK**（如 **`RecommendationPlannerMockAgentAdapter`** 同类） | 占位建议文案或结构化占位；**不**触发真实 action |

**C-31 + C-31.1（当前 Harness）**：上表六 **`stepId`** 已固化，**但前四步均为 `PlannerStepMockExecutionStatus.SUCCESS` MOCK**，**命名含 `hydrated` 仅表达未来接线意图**；**前四步 `targetTool` = `mock_revenue_hydrated_adapter` … `mock_dish_profit_hydrated_adapter`（C-31.1）**，故 **`plannerExecutorTrace.usedTools` / 各步 `stepResults.usedTools`** **不**出现 **`revenue_query` / `purchase_overview` / `stock_reduce_query` / `dish_profit_analysis`**；真实 Tool id 仅写在 **`inputSummary` / `acceptanceCriteria`**。**`step_diagnosis_compose`** 使用 **`mock_diagnosis_compose`**；**`step_recommendation`** 使用既有 **`mock_build_recommendation_plan`**。**C-32～C-34**：**Retired**（见 **§2.1～2.3**）。**C-35**（**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE`**）：前四步均为生产 Tool id（四 Hydrated RealBridge）；**`step_diagnosis_compose`** / **`step_recommendation`** **`mock_*`**（见 **§2.4**）。**C-42**（**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_STOCK_DEGRADED_CORE`**）：六步同构，**出库步 Harness 故意 DEGRADED**；余三数据域真实（见 **§2.5**）。

**步序说明**：文档默认 **顺序执行** 以便依赖清晰的 `AiRunState` 突变；若未来实装并行四数据步，须在 **失败策略** 与 **trace** 上显式定义合并规则，**C-30 不展开实现**。

---

## 4. 每步输入：统一 STORE + `timeWindow` + 上下文

四步 **Hydrated Adapter** **共享**下列约束（与各自单域 Hydrated GraphCase **同构**；Composite 在 **一次 `AiRunState` 生命周期** 内递增写入 `toolResults` / AnswerPlan 附着）：

| 维度 | 设计取值（v1 Composite） |
|------|---------------------------|
| **组织** | **`AiResolvedOrgScope.SCOPE_STORE`**；**`currentStoreDepartmentId` / `requestDepartmentId` = 1**；可见门店 **AAA**（`storeDepartmentId = 1`） |
| **时间** | **同一个** **`AiResolvedTimeWindow`**（`startDate` / `endDate` / `timeLabel`）；四域 Tool 使用同一窗口 |
| **上下文** | **同一个** **`AiResolvedQueryContext`** 实例（或 **按字段等价拷贝** 的不可变快照）；**`dataScope`** 按域补齐为各 Hydrated 设计所述（`queryStoreIds`、`expandedSqlDepartmentIds` 等），保证各域 SQL IN 列表非空 |
| **`AiRunState`** | **单一** **`runId` / `conversationId` / `userId` / `departmentId` / `distributerId`**；**`resolvedQueryContext`** 指向上述对象；**各域 path 旗标**（如 `dishProfitPath`）按单域 Hydrated 要求置位，**互斥域 path 保持 false**（与现有 Adapter 契约一致） |
| **用户句** | Composite **不**新增原文解析；Harness 可仅用占位 `userMessage` **不参与路由** |

**各步 `PlannerAgentAdapterRequest`**：分别携带 **对应域** 的 **`…ExecutionContext`** + **`…ReadRequest`**（结构与 **`AiPlannerExecutor*HydratedGraphCase`** 一致），并从 **共享** `AiRunState` / `AiResolvedQueryContext` **引用**同一 scope / time。

**C-43（GROUP）**：上表为 **STORE v1** 取值；**`scopeType=GROUP`**、**`visibleStores`（含 AAA `id=1`、汀兰餐厅 `id=3`）**、**`departmentId` 不误作唯一查询门店**、**`dataScope` 多店 IN**、path 旗标 **按 Tool 真实语义** 见 **[`business-diagnosis-composite-group-design.md`](./business-diagnosis-composite-group-design.md)**。

## 5. 每步输出 → 诊断汇总（revenue / purchase / stock_reduce / dish_profit summary）

诊断步（**`step_diagnosis_compose`**）的 **输入** 设计为 **只读** 前四步产物，**禁止**假装缺失数据存在：

| 来源步 | 写入 RunState / 附着（事实源） | 进入诊断的 **summary** 形态（设计） |
|--------|----------------------------------|--------------------------------------|
| **Revenue** | **`DailyRevenueAnswerPlan`**（或等价营收计划）+ `toolResults[revenue_query]` 信封 | **`revenueSummary`**：营业额汇总、门店/窗口说明、`dataCompleteness`、**降级/空** 时显式 **MISSING** |
| **Purchase** | **`PurchaseAnswerPlan`** + `toolResults[purchase_overview]` | **`purchaseSummary`**：采购 overview 核心数、完整性标记 |
| **StockReduce** | **`StockReduceAnswerPlan`** + `toolResults[stock_reduce_query]` | **`stockReduceSummary`**：出库/核销 overview 核心数、完整性标记 |
| **DishProfit** | **`DishProfitAnswerPlan`** + `toolResults[dish_profit_analysis]` | **`dishProfitSummary`**：毛利概览汇总行、组合口径、**不完整成本**等标记 |

**C-36（目标态 DTO）**：四域 **`revenueSummary` … `dishProfitSummary`** 在 **`BusinessDiagnosisCompositeAnswerPlan`** 中 **并排**定义（**[`business-diagnosis-answer-plan-design.md`](./business-diagnosis-answer-plan-design.md)**）；**`diagnosisSignals`**（**`revenueWeakSignal`** 等）与 **`dataCoverage`**（每域 **`success` / `realToolInvoked` / `stepId` / `usedTool` / `degradedReason`**）与 trace **机械对齐**；缺失域 **不编造**，**`riskLevel`** 仅基于已有数据。**C-37** 再将 **`step_diagnosis_compose`** 输出 **挂接到** 该 DTO 或 **`DiagnosisPlan`** 并存。

**与现有契约对齐**：**[`diagnosis-answer-plan.md`](./diagnosis-answer-plan.md)** — **DiagnosisPlanBuilder 只读各域 AnswerPlan**，**不**从原始 Tool JSON 重算；Composite 的 **`step_diagnosis_compose`** 在语义上等价于 **「轻量、无 LLM」** 的 Builder **草稿**：输出结构建议沿用 **`DiagnosisPlan`** 的子集：**`summary`、`evidenceRows`（域级引用）、`diagnosisLevel`、`debug.dataAvailability`**（四域 **OK / PARTIAL / MISSING / FAILED**）。

**C-30 skeleton 不做**：LLM 归因、多跳因果链、跨域自动裁决「主因」；仅 **结构化占位** + **诚实空位**。

---

## 6. 失败与降级策略

| 范围 | 策略 | 说明 |
|------|------|------|
| **计划级 `failureStrategy`**（四数据步） | **`CONTINUE_WITH_DEGRADED`**（建议） | 任一域 Tool **失败 / 权限 / 空信封** → 该步 **`DEGRADED`/`FAILED`**（按现有 Executor 规则），**后续步仍执行**（除非实装时显式改为 **FAIL_FAST**，**C-30 默认建议 CONTINUE**） |
| **`step_diagnosis_compose`** | 若 **四域均无可读 AnswerPlan / 关键摘要全 MISSING** → **`DEGRADED`** | **不**伪造数字；**`diagnosisLevel`** 对应 **NOTICE** 或 **不可判定**，并在 **`debug`** 标明 **数据不足** |
| **`step_recommendation`** | MOCK：**可**随整体 **`overallStatus`** 降级仍为 **SUCCESS**（占位文本）或 **DEGRADED**（实装约定二选一）；**禁止**输出看似来自缺失域的 **具体** 金额/排行 |
| **诚实性** | **禁止假装缺失数据存在** | 与单域 **`REAL_BRIDGE_HYDRATED_*_DEGRADED`** 精神一致；Composite 根级可增加 **`plannerBusinessDiagnosisHonesty`**（实装时定义），**C-30 仅预留命名空间** |

---

## 7. Trace 与 `finalAnswerPlanType`

一次 Composite 执行后 **`PlannerExecutorTrace`**（及 Harness Replay 根对象）**应包含**：

| 字段 | 要求 |
|------|------|
| **逐步 `stepResults`** | 每步 **`PlannerStepStatus`**（SUCCESS / DEGRADED / FAILED）、**`degradedReason`**（如有） |
| **`usedAgents`** | 含 **`BusinessAgentNames`** 维度上四域 Agent + 诊断/建议占位标识（与现 trace 对齐） |
| **`usedTools`** | **全链路 Hydrated Composite（C-30 目标态）**：成功路径 **至少**包含 **`revenue_query`**、**`purchase_overview`**、**`stock_reduce_query`**、**`dish_profit_analysis`**（与单域一致）；失败步 **不**伪造 usage。**`PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_CORE`（C-31.1 skeleton）**：trace **仅** echo **`mock_*_hydrated_adapter`** / **`mock_diagnosis_compose`** / **`mock_build_recommendation_plan`** — **不**声称上述生产 Tool 已执行 |
| **`degradedSteps`** | **stepId 列表**，与单域 Composite 行为一致 |
| **`plannerExecutorTrace.overallStatus`** | **`SUCCESS`** 仅当 **产品定义的「可接受」** 条件满足（建议：**诊断步未 FAILED** 且 **非「全部 MISSING 却报 SUCCESS」**）；**DEGRADED** 为常见诚实结果 |
| **`finalAnswerPlanType`** | **`BUSINESS_DIAGNOSIS_COMPOSITE`**（字符串常量，与 Composer / Debug 面板对齐；若与 **`diagnosis-answer-plan.md`** 中 **`planType`** 枚举冲突，实装时 **新增枚举项** **`BUSINESS_DIAGNOSIS_COMPOSITE`** 或 **`OVERALL_BUSINESS_DIAGNOSIS_COMPOSITE`** — **C-30 取用户指定字面 `BUSINESS_DIAGNOSIS_COMPOSITE`**） |

**Trace 体量**：继承 **`PlannerExecutor#sanitizePlanForTrace`** 原则；**不**输出完整 **`AiRunState` / `AiResolvedQueryContext`** 大对象。

---

## 8. 实装阶段前置条件（ checklist）

1. **~~C-31~~ Removed（P1-B Final）**：全 MOCK Composite case 已删；主验收见 C-35 / C-48 / C-42。  
2. **`AiHarnessReplayService` + `AiHarnessReplayPlannerExecutorMock`**：**C-32～C-35** 已按 Composite case **分档注入** RealBridge（**C-35** 须 **四** Bean 非空）；与「单次短路可注入四 Bean」设计一致。  
3. **`PlannerExecutionPlan`**：**`failureStrategy`**、六步 **`PlannerStepExecutionRequest`**、各步 **`executionMode`** — **C-31**：计划级 **`CONTINUE_WITH_DEGRADED`**、`finalAnswerPlanType=BUSINESS_DIAGNOSIS_COMPOSITE`、六 **`stepId`** 已固化。  
4. **诊断 skeleton**：Java 类占位 **只聚合** AnswerPlan，**无 LLM** — **C-31**：**`step_diagnosis_compose`** 仍为 **MOCK SUCCESS** + **`answerPlanRef`** / **`expectedOutput`** 占位。→ **C-36 ✓**：**[`business-diagnosis-answer-plan-design.md`](./business-diagnosis-answer-plan-design.md)** 定义目标 **`BusinessDiagnosisCompositeAnswerPlan`**；**C-37**：Deterministic compose + DTO 映射。  
5. **Composer（Composite）**：**C-50** 契约 — **只读** **`BusinessDiagnosisCompositeAnswerPlan`**（见 **`business-diagnosis-composer-readonly-design.md`**）；**C-51** Java skeleton。**C-37** 起 Builder 已物化该 DTO；**禁止** Composer **重扫** **`toolResults`**。

### C-31 与 C-30 设计差异（刻意）

| 项 | C-30 设计（目标态） | C-31 实装（当前） |
|----|---------------------|-------------------|
| 前四步 | ADAPTER + 各 Hydrated RealBridge | **MOCK**；**`AiPlannerExecutorBusinessDiagnosisCompositeGraphCase`**：`targetTool` = **`mock_*_hydrated_adapter`**（C-31.1），**不经** 各 `*QueryToolExecutor`；生产 Tool id 仅作文本说明 |
| **`step_diagnosis_compose`** | 聚合 RunState 摘要 | **MOCK SUCCESS**；**`mock_diagnosis_compose`** 仅占位 trace **`usedTools`** |
| 诚实标记 | 预留 `plannerBusinessDiagnosisHonesty` | 根级 **`plannerCompositeHonesty` / `plannerCompositeNote`** |

---

## 9. 当前不做（冻结边界）

| 不做项 | 说明 |
|--------|------|
| **接 Master 生产主链路** | Composite 仅 Harness / Planner 编排 **或** 平行 Graph；**不**改 Master 调度表 |
| **GROUP 多门店** | **仅 STORE 单店**（`departmentId=1` / **AAA**） |
| **前台** | **不**做 UI |
| **LLM 自由规划** | 步骤列表 **固定**；**不**让模型新增/删步 |
| **真实 action** | **无**通知 / 调价 / 下单 / 删数 |
| **新 SQL** | **四域仅复用已通过 Hydrated RealBridge**（**C-31 甚至不调 Tool**） |
| **改 Resolver / Composer 主逻辑** | **C-30/C-31** 文档与 Harness；全链路实装另案评审 |
| **用户原文 contains/regex** | **禁止**在 Composite 层新增 |
| **C-31**：**真实 Hydrated Adapter / Tool** | 见 **`plannerCompositeNote`** |

---

## 10. 参考与版本

| 文档 | 用途 |
|------|------|
| [`planner-executor-v1-design.md`](./planner-executor-v1-design.md) | PlannerExecutor、失败策略、§12 四条 Hydrated |
| [`next-business-capability-roadmap.md`](./next-business-capability-roadmap.md) | **D-1**：下一阶段 **业务能力** **P0～P3**、框架「够用即止」边界、推荐主链路与 **D-2** 任务入口 |
| [`diagnosis-answer-plan.md`](./diagnosis-answer-plan.md) | DiagnosisPlan 字段、AnswerPlan 优先 |
| [`diagnosis-answer-plan.md`](./diagnosis-answer-plan.md) | DiagnosisPlan 聚合；**事实源** 以各域 AnswerPlan 为准 |
| [`business-diagnosis-answer-plan-design.md`](./business-diagnosis-answer-plan-design.md) | **C-36**： **`BusinessDiagnosisCompositeAnswerPlan`** 字段、四域 summary、**`diagnosisSignals`、`dataCoverage`**、降级与 **C-37** 映射路线；**C-50**：§8.12 |
| [`business-diagnosis-composer-readonly-design.md`](./business-diagnosis-composer-readonly-design.md) | **C-50**：Composite Composer **只读 AnswerPlan** |
| [`business-diagnosis-production-composite-execution-design.md`](./business-diagnosis-production-composite-execution-design.md) | **C-57**：Gate **`allowed`** 后 **PlanFactory / ExecutionService / executionMode**（**仅设计**） |

**文档版本**：Composite 六步计划 + Harness C-35/C-48/C-42 + **`BusinessDiagnosisCompositeAnswerPlan`** + Readonly Composer + Production Gate + **`HARNESS_ONLY`/`SHADOW`** 执行编排均已实装；建议步 mock、PRIMARY 与 C-66 dashboard 为局部待做。
