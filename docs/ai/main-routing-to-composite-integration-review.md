# 主入口路由链路梳理：用户话 → 语义解析 → `AiResolvedQueryContext` → DataPlanner / Tool / Master → Composite（Gate/SHADOW）

> **性质**：只读代码梳理；**不修改** Java/test/SQL/Resolver/Master/Composer。  
> **范围**：普通生产入口 **`POST /ai/runs`**（`AiRunController`）与现行 Business Graph。  
> **关联设计**：[`business-question-routing-d2-design.md`](./business-question-routing-d2-design.md)、[`main-business-routing-and-composite-integration-map.md`](./main-business-routing-and-composite-integration-map.md)、[`business-diagnosis-production-gate-design.md`](./business-diagnosis-production-gate-design.md)。

---

## 0. 端到端总览（顺序）

```
POST /ai/runs
  → AiRunController#createRun
  → AiRunService#startRun
       → AiUserContextResolver#resolve
       → AiResolvedQueryContextResolver#resolve   ← LLM 语义 + 多轮合并
       → AiRunState（挂 resolvedQueryContext）
       → BusinessDiagnosisCompositeProductionGate.evaluate → state.businessDiagnosisCompositeGateResult  （仅观测）
       → Session 注册 + 异步 executeRun
  → AiRunService#executeRun
       → AiGraphRunner#runBusinessGraph
            → BusinessScopeIntersectNode → BusinessTimeWindowNode → BusinessDataPlannerNode → …
            → BusinessToolExecutionNode（含 MasterBusinessAgent 四口尝试）
            → … → StubAnswerComposerNode（legacy 终稿）
       → maybeExecuteShadowCompositePlanner（SHADOW 且 Gate 放行 …）→ Composite tryExecute（**不**改 finalAnswerText）
```

图的节点顺序以 `AiBusinessGraphConfig#businessAgentNodes` 为准；上表只标出与「路由 / Tool / Master」强相关的段。

---

## 1. 「第一轮用户去哪儿」——LLM 语义判断的代码入口在哪里？

**结论**：主入口在 **`AiResolvedQueryContextResolver#resolve`**：在启用 `ai.agent.querySemanticLlm.enabled` 时构建 `SemanticParserInput`，调用 **`AiQuerySemanticLlmParser#parse(SemanticParserInput)`**（v2，prompt `SEMANTIC_QUERY_PARSER_V2`）；采纳失败则回退 **`parseUserQuestion(String)`**（v1）。  
该调用链在 **`AiRunService#startRun`** 里通过 `resolvedQueryContextResolver.resolve(runId, req, uc)` **同步完成**，早于 Graph、早于 `BusinessDataPlannerNode`。

| 环节 | 类与方法 | 说明 |
|------|-----------|------|
| HTTP | `com.nongxinle.controller.AiRunController#createRun` | `POST` 映射在类级 `@RequestMapping("ai/runs")` 上，即 **`/ai/runs`**。 |
| Run 启动 | `com.nongxinle.ai.platform.AiRunService#startRun` | 第 112～113 行：`userContextResolver` → **`resolvedQueryContextResolver.resolve(...)`**。 |
| 解析总控 | `com.nongxinle.ai.resolver.AiResolvedQueryContextResolver#resolve` | 组装 v2 输入后调用 `querySemanticLlmParser.parse(v2In)`；v2 后经若干 **Normalizer**、`AiQuerySemanticV2DishProfitGate`，再走 **`trySemanticAdoption`**；失败则 v1 `parseUserQuestion`。 |
| LLM 调用 | `com.nongxinle.ai.semantic.AiQuerySemanticLlmParser#parse` | `LlmGateway.chatSimple(systemPrompt, userPayload)`，`userPayload` 为 `SemanticParserInput` 的 JSON；类注释写明 **禁止产出 SQL/可执行 ID**，门店等由 Resolver 映射为权限内 ID。 |

`AiResolvedQueryIntent` 类注释也写明：**主链路内容由 `AiQuerySemanticParseResult` 合并得到，不再对用户消息做关键词路由**（`fromUserMessage` 等已废弃/空实现）。

---

## 2. `intentCode`、`pathCode`、`structuredIntentDetail`、`effectiveIntentCode`、`effectivePathCode` 在哪里产生、在哪里使用？

### 2.1 产生（写入 `AiResolvedQueryContext`）

| 字段 | 产生阶段 | 机制要点 |
|------|-----------|----------|
| **`queryIntent.intentCode` / `pathCode` / `structuredIntentDetail`** | Resolver 内 **`trySemanticAdoption` → `AiQuerySemanticLlmMergeHelper.mergeIntent(...)`** | 必须能合并出 **非空 `pathCode`** 才会 `SemanticAdoption` 成功；合并结果作为 **本轮 stem**，再经 **`AiFollowUpResolver`** 与上轮记忆合并得到 **`queryIntent`**。 |
| **`effectiveIntentCode` / `effectivePathCode`** | 同一 Resolver 构建 **`AiResolvedQueryContext`** 时 **`followUp.getEffectiveIntentCode()/getEffectivePathCode()`** | 与多轮 **`AiFollowUpResolution`** 对齐；供日志、Gate、Master 门闸等 **统一读「有效路由」**。 |
| **`querySemanticParse`** | 采纳的 **`AiQuerySemanticParseResult`**（v2 优先，失败 v1） | 含 orchestration 候选、metric、时间等；Resolver 还会把 orchestration 扁平字段写到 context（`orchestrationTaskMode`、`orchestrationMultiAgentRequired` 等）。 |

特殊：**`BusinessDataPlannerNode`** 在经营诊断分支 **`applyBusinessDiagnosisBranch` 成功后**会调用 **`syncResolvedQueryContextToBusinessDiagnosis`**，**就地修正** `queryIntent` 与 context 上的 **`PATH_BUSINESS_DIAGNOSIS` / `BUSINESS_DIAGNOSIS`**（及缺省 `structuredIntentDetail`），保证 Harness/后续读到的 effective 与Planner 一致。

### 2.2 使用（消费方摘要）

| 消费方 | 使用方式 |
|--------|-----------|
| **`BusinessDataPlannerNode#run`** | 优先 **`effectivePathCode`**（若未处于语义澄清），否则回退 **`queryIntent.pathCode`**；据此设置 **`revenueOverviewPath`、`purchaseOverviewPath`、`stockReduceQueryPath`、`dishProfitPath`、`businessOverviewPath`、`businessDiagnosisPath`** 等布尔位与 **`dataPlanTools`**。 |
| **`BusinessDiagnosisCompositeProductionGate#evaluate`** | 读 **`effectiveIntentCode` / `effectivePathCode`**、`queryIntent.getStructuredIntentDetail()`（经 Lexicon canonical）、**`timeWindow`**、**`needSemanticClarification`** 等；**不**改 `AiRunState`、**不**调用 Master/Tool/PlannerExecutor。 |
| **`MasterBusinessAgent`** | **四域批量编排**门闸读 **`state` + `resolvedQueryContext`**：`eligibleForBusinessOverviewMultiAgentOrchestration` 要求 `business_overview` **或** `business_diagnosis` 路径且 effective intent/path 匹配，且 **`orchestrationTaskMode == MULTI_AGENT`** 或 **`orchestrationMultiAgentRequired`** 或语义里 `orchestrationDecisionCandidate` 等价条件。单域专线如 **`tryOrchestrateRevenueOverview`** 另有 **`eligibleForMasterRevenueOverview`**（计划仅含 `REVENUE_QUERY` 等）。 |
| **子 Agent / Tool 请求** | `BusinessToolExecutionRequestResolver`、各 `*ToolExecutor` 从 **`AiResolvedQueryContext`** 取时间、范围、structured wire 等（不单列类名，原则为 **只读 context**）。 |

---

## 3. 单域问题如何进入 `revenue` / `purchase` / `stock_reduce` / `dish_profit`？

**共同点**：均由 **`effectivePathCode`**（及 `inBusinessChat`、非澄清）在 **`BusinessDataPlannerNode`** 中选定分支。**`business_diagnosis` → `dish_profit` → `stock_reduce` → `revenue` → `purchase`** 等 intent 之间的 **互斥顺序**见该节点内 **`dishProfitIntent` / `stockReduceStandaloneIntent` / `revenueStandaloneIntent` / `purchaseOverviewOnlyIntent`** 等布尔变量的组合逻辑。

| 目标域 | `AiResolvedQueryIntent` 常量（path） | Planner 行为摘要 |
|--------|--------------------------------------|------------------|
| **营收** | `PATH_REVENUE_OVERVIEW` | `revenueStandaloneIntent` → **`applyRevenueOverviewQuestionBranch`**，设 `revenueOverviewPath`，`dataPlanTools` 含 `REVENUE_QUERY` 等。 |
| **采购** | `PATH_PURCHASE_OVERVIEW` | `purchaseOverviewOnlyIntent` → **`applyPurchaseOverviewQuestionBranch`**。 |
| **出库/核销** | `PATH_STOCK_REDUCE_QUERY` | `stockReduceStandaloneIntent` → **`applyStockReduceQuestionBranch`**。 |
| **菜品毛利** | `PATH_DISH_PROFIT` | `dishProfitIntent` → **`dishProfitPath(true)`**，工具列表 **`DEFAULT_DISH_PROFIT_TOOLS`**。 |

随后在 **`BusinessToolExecutionNode`**：

- 先无条件（按实现顺序）调用 **`MasterBusinessAgent`** 的 **`tryOrchestrateBusinessOverviewMultiAgent`**、**`tryOrchestrateRevenueOverview`**、**`tryOrchestratePurchaseOverview`**、**`tryOrchestrateStockReduceQuery`**、**`tryOrchestrateDishProfitAnalysis`**；
- 若某域 **已由 Master 路径执行工具**（对应 `*ToolExecutedByMasterPath`）或 **命中四域批量 `businessOverviewMultiBatch`**，则从 `state.getToolResults()` **剔除**相应 key，后续 **for 循环按计划 toolId 执行时避免重复**（见 `BusinessToolExecutionNode#run` 中 `legacyRevenueSkipped` / `businessOverviewMultiBatch` 逻辑）。

因此：**单域**时通常只有 **对应 Master 尝试 + 计划内 Tool 循环** 其一实际落数据，具体以各 `eligibleFor*` 门闸为准。

---

## 4. `business_overview` / `business_diagnosis`：多 Agent（四域）与 legacy 如何分支？

### 4.1 Resolver 侧「推一把」MULTI_AGENT

在 **`AiResolvedQueryContextResolver`** 中，当 **effectivePath** 为 **`business_diagnosis_path`** 或（**`BUSINESS_OVERVIEW` + `business_overview_path` + 结构化表面为四域 orchestration**）时，会把 **`orchestrationTaskMode`** 置为 **`MULTI_AGENT`** 且 **`orchestrationMultiAgentRequired = true`**（若 LLM 尚未给出），以便与 Master / DataPlanner 对齐。

### 4.2 DataPlanner：经营概览工具列表

- **`overviewIntent`**（`PATH_BUSINESS_OVERVIEW`）且非角色收敛到采购/库房时：设 **`businessOverviewPath(true)`**。  
- 若 **`resolvedContextOrchestrationMultiAgentOverview(rCtx)`** 为真（`orchestrationTaskMode == MULTI_AGENT` 或 `orchestrationMultiAgentRequired`），则 **`buildBusinessOverviewMultiAgentToolsPermissionFiltered`** 按权限组装 **四域工具子集**（可能少于四域）；否则使用 **`DEFAULT_BUSINESS_OVERVIEW_TOOLS`**（legacy 默认套餐）。  
- 注释强调：**固定四域能力与顺序，不根据用户原文删减域**，仅权限裁剪。

### 4.3 Master：`tryOrchestrateBusinessOverviewMultiAgent`

- **`eligibleForBusinessOverviewMultiAgentOrchestration`**：`state` 上 **`businessOverviewPath` 或 `businessDiagnosisPath`** 且 **effective intent/path** 分别为 **BUSINESS_OVERVIEW** 或 **BUSINESS_DIAGNOSIS**，再加上 **MULTI_AGENT**（context 字段或语义 `orchestrationDecisionCandidate`）。  
- 满足时按固定顺序跑 **Revenue → Purchase → StockReduce → DishProfit** 四个 **`BusinessSubAgent`**，并把结果写入 `AiRunState`（与 Tool 信封协同）；**不满足**则返回 **not_eligible**，由后续 **legacy Tool 循环**补数。

**诊断专有**：`BusinessDataPlannerNode` 中的 **`applyBusinessDiagnosisBranch`** 配置诊断用工具列表（采购 + 出库 + 菜品 + 有条件的营收），成功时 **`syncResolvedQueryContextToBusinessDiagnosis`** 将 context 对齐到 **`BUSINESS_DIAGNOSIS`**。

---

## 5. Composite Gate（C-53）与 SHADOW（C-60～C-63）：接在什么位置？是否只观察、是否改变原路由？

### 5.1 Gate（生产 Gate）

- **位置**：`AiRunService#startRun`（及 Harness 同步入口）在 **`newRunStateFromResolved`** 之后立即调用 **`recordCompositeProductionGateObservation`**。  
- **实现**：`BusinessDiagnosisCompositeProductionGate.evaluate(resolvedQueryContext, state, productionEnabled)`。  
- **契约**（类注释）：**只读**结构化字段；**不接** Master、**不跑** PlannerExecutor、**不执行** Tool、**不调** LLM、**不改** `AiRunState`；仅由 `AiRunService` 把 **`BusinessDiagnosisCompositeGateResult`** 挂到 state。  
- **对路由的影响**：**不**修改 `effectivePathCode` / `dataPlanTools`；**`BusinessDataPlannerNode`** **不**读 Gate 结果选路。即：**不改变原路由**。

### 5.2 SHADOW Composite 执行

- **位置**：`AiRunService#executeRun` 在 **`graphRunner.runBusinessGraph`** **成功返回后**调用 **`maybeExecuteShadowCompositePlanner(endedState)`**。  
- **条件**（语义归纳）：配置 **`ai.composite.businessDiagnosis.productionEnabled`**、`executionMode == SHADOW`、Gate **`allowed`**、**`ShadowPolicy.evaluate` 放行**等。  
- **契约**：`BusinessDiagnosisCompositeExecutionService.tryExecute` **仅接受** `HARNESS_ONLY` 与 **`SHADOW`**；传入 **PRIMARY** 等会在该方法入口得到 **`executed=false`**（不执行 Composite 主体）。执行路径内跑 PlannerExecutor + RealBridge，但 **普通 Run 不写入 `finalAnswerText`**；可填充 **`compositeShadow*`** 观测字段。  

因此：**Gate + SHADOW** 在当前代码中均为 **并行观测或 shadow 执行**，**不替代** legacy Composer 终稿。

### 5.3 Harness

- **`executeBusinessGraphSyncForHarness`** 在图完成后 **`maybeExecuteHarnessCompositePlanner`**，仅 **`HARNESS_ONLY`** 模式调用 **`tryExecute`**，同样 **不改 `finalAnswerText`**（与设计文档一致）。

---

## 6. 若未来 Composite 「入主回答」，最小接入点建议（不涉及用户原文正则、且避免重复四域 Tool）

**本文档不写具体 PR，仅给出与现行结构对齐的挂载点与设计约束。**

### 6.1 推荐挂载点（与现网相位一致）

1. **PRIMARY 仅在 legacy 图完成之后决策**  
   - 沿用 **`executeRun`** 末尾、`maybeExecuteShadowCompositePlanner` **附近**分叉：若模式为 **`PRIMARY`**、Gate **`allowed`**、**`tryExecute` 成功**，再考虑把 **`AiRunState#finalAnswerText`**（及 SSE payload）替换为 Composite **`composeResult.finalAnswerText`**。  
   - **理由**：与当前 SHADOW **同一语义锚点**，避免在第一轮 Resolver 后再加一套异步竞态。

2. **Gate 与路由仍只依赖 `AiResolvedQueryContext`**  
   - **不得**在新分支上对 **`rawUserInput`** / **`normalizedUserInput`** 做 **contains/regex** 选路；**必须**继续使用 **`effective*` + `structuredIntentDetail` + Lexicon canonical**（与 `BusinessDiagnosisCompositeProductionGate` 一致）。

3. **先要扩展 `BusinessDiagnosisCompositeExecutionService.tryExecute` accept PRIMARY**  
   - 现行对 **mode** 的白色列表 **不含 PRIMARY**，PRIMARY 须在 **Execution 层**显式支持与观测字段，再在 `AiRunService` 放开调用。

### 6.2 避免「四域 Tool 跑两次」

现行 SHADOW：**legacy 已完整跑** `BusinessToolExecutionNode` **与** Composite 内 **PlannerRealBridge**；若 PRIMARY 直接叠加，会形成 **双倍 IO**。

**可选方向（由产品/里程碑选其一，均需改代码时单独评审）**：

- **Planner 短路（DataPlanner）**：在 **`BusinessDataPlannerNode`** 开头增加 **PRIMARY 且 Gate 已预放行**（注意 Gate 仍在 `startRun` 早于 Planner，可选用 **上一轮缓存的 gate 结果** 或重复 evaluate 只读）时，将 **四域 tool id** 从 **`dataPlanTools`** 移除，仅保留 Compose 所需的非重复数据来源；Composite 作为主回答。**不得**用词面匹配删减工具，只能依赖 **`AiResolvedQueryContext` 上与 Gate 白名单一致的字段**。  
- **Tool 节点兜底跳过**：在 **`BusinessToolExecutionNode`** 根据 **同一 structured 条件**跳过已纳入 Composite 的 tool 调用；风险是 **状态字段**（如各类 `AnswerPlan`）必须与 Composite 接管顺序一致，否则 Composer 仍读 legacy 空Plan。

**不推荐**：在 **`AiRunService`** 仅用字符串判断跳过整图——与「禁止用户原文路由」冲突且难与 DataPlanner 状态一致。

---

## 7. 关键类路径速查

| 主题 | 类 |
|------|-----|
| HTTP 入口 | `com.nongxinle.controller.AiRunController` |
| Run 编排 | `com.nongxinle.ai.platform.AiRunService` |
| 解析器 | `com.nongxinle.ai.resolver.AiResolvedQueryContextResolver` |
| 语义 LLM | `com.nongxinle.ai.semantic.AiQuerySemanticLlmParser` |
| Intent 合并 | `com.nongxinle.ai.semantic.AiQuerySemanticLlmMergeHelper` |
| 上下文 DTO | `com.nongxinle.ai.context.AiResolvedQueryContext`，`AiResolvedQueryIntent` |
| DataPlanner | `com.nongxinle.ai.graph.business.BusinessDataPlannerNode` |
| Tool / Master | `com.nongxinle.ai.graph.business.BusinessToolExecutionNode`，`com.nongxinle.ai.agent.business.MasterBusinessAgent` |
| Composite Gate | `com.nongxinle.ai.planner.BusinessDiagnosisCompositeProductionGate` |
| Composite Execute | `com.nongxinle.ai.planner.BusinessDiagnosisCompositeExecutionService` |
| Graph 配置 | `com.nongxinle.ai.config.AiBusinessGraphConfig` |

---

## 8. 版本

| 版本 | 日期 | 说明 |
|------|------|------|
| v1 | 2026-05-14 | 首次代码梳理；与仓库阅读时源码一致 |
