# AI Harness — Replay Cases（第二阶段稳定性）

**D-12 长期最小门卫**：一键回放与 PASS 规则见 **`docs/D12_HARNESS_MINIMAL_GATES.md`**，本地脚本 **`scripts/harness/run-minimal-gates.sh`**。**Minimal Gates v1** 已通过上述脚本内 **7** 个内置 case（全部 PASS），可作为日常改动前后的**基础门卫**（不替代生产全链路与 D-11 权限 spot）。**D-13 Permission Spot Gates**（**v1** 已在本地 **`AUTO_PASS` 收口**，四 persona）：**`docs/D13_PERMISSION_SPOT_GATES.md`** · **`scripts/harness/run-permission-spot-gates.sh`**。

本文件为多轮链路 **replay / 断言** 的用例草稿：每一步给出 **预期语义字段**，便于 Harness、日志对照与回归。

- **占位门店名**：`AAA`、`汀兰餐厅` 等为示例；实际断言应以环境内 **`gb_department` 门店根名称** 与权限可见列表为准。
- **日期**：`startDate` / `endDate` 为 **`yyyy-MM-dd`**，与 `AiResolvedTimeWindow`、`AiRunState#stat*` 对齐。内置用例 `PURCHASE_MULTITURN_1` 根据 **`frozenClockDate`**（语义「今天」锚点）计算「本月至此日」起止以及「上个月」闭合区间。
- **开关**：`GET /api/ai/runs/{runId}` 始终含 **`harnessDebug.debugContextEnabled`**（与运行时 `ai.harness.debug-context-enabled` 一致）。若为 `true`，另含 **`harnessDebug.resolvedQueryContextPresent`**；仅当为 `true` 且内存态有 `resolvedQueryContext` 时才有 **`harnessDebug.resolvedQueryContextSummary`**。（本地联调见 `application-local.properties`：`ai.harness.debug-context-enabled=true`。）
- **Replay 接口**：`POST /api/ai/harness/replay`；需 **`ai.harness.replay-enabled=true`**（本地 profile 已默认开启）。全路径带 `server.servlet.context-path=/api` 时为 **`/api/ai/harness/replay`**。
- **经营类阶段 1B（仅 Resolver 摘要 · `dryRunStage=RESOLVED_CONTEXT_ONLY`）**：预期字段与十条最小矩阵见 **`docs/ai/business-phase1b-semantic-harness-matrix.md`**；内置 **`caseId`** **`BUSINESS_SEMANTIC_1B_RESOLVED_CONTEXT`**（`AiHarnessBuiltinCases` / `messagesBusinessSemantic1bResolvedContext()`；服务端对本 `caseId` 默认 `dryRunStage=RESOLVED_CONTEXT_ONLY`）。
- **出库 / 核销阶段 1C（仅 Resolver 摘要 · `dryRunStage=RESOLVED_CONTEXT_ONLY`）**：矩阵见 **`docs/ai/stock-reduce-phase1c-semantic-harness-matrix.md`**；内置 **`caseId`** **`STOCK_REDUCE_SEMANTIC_1C_RESOLVED_CONTEXT`**（`messagesStockReduceSemantic1cResolvedContext()`；默认同上；R15 可能 **`purchase_slow_moving_risk`** / **`purchase_stock_reduce_mismatch`** **AnyOf**，见矩阵 §5）。

### 阶段 2：Tool Request / SQL 入参层

- **计划新增 `dryRunStage=TOOL_REQUEST_ONLY`**（方案 A，**尚未实现**）：Graph 跑至 DataPlanner + `BusinessToolExecutionRequestResolver` + `build*ToolArgs`，在 **`Tool.execute` 之前截断**。
- **只验**：`plannedToolArgsByToolId`、`*RequestContext` / `*RequestResolutionDebug`、`ToolRequest.args`（日期、scope、`purchaseSourceFocus` / `purchaseNarrativeMode` / `stockReduceNarrativeMode`、集团聚合旗标等）。
- **不验**：`Tool.execute` 返回行、SQL 真实结果、**`AnswerPlan`**、**`Composer`** / `answerPreview`、前端。
- **详细矩阵**：**`docs/ai/phase2-tool-request-harness-matrix.md`**（与 **`docs/ai/phase2-tool-request-sql-input-plan.md`** 配套）。
- **第一批计划覆盖**：**2A 采购**（`PURCHASE_TOOL_REQUEST_2A_CORE_5`）、**2B 营业额/经营**（`BUSINESS_TOOL_REQUEST_2B_CORE_5`）、**2C 出库**（`STOCK_REDUCE_TOOL_REQUEST_2C_CORE_4`）；跨域 **2D** 暂列不实现。

---

## 核心回归必跑 Case

以下两类均为 **核心回归**：**(A)** **`replayMode` 省略或 Resolver-only** —— **v2 + Resolver + Harness 摘要**；**(B)** **`replayMode=GRAPH_RUN`**（或对特定 `caseId` 服务端默认 Graph）—— **同步业务图 + Tool + Master + Summarizer**。  
**Diagnosis v1**、**BusinessOverview 四域 MultiAgent**，以及 **四条单域 Agent** 的 **`GRAPH_RUN` 核心用例**（`REVENUE_AGENT_GRAPH_CORE` / `PURCHASE_AGENT_GRAPH_CORE` / `STOCK_REDUCE_AGENT_GRAPH_CORE` / `DISH_PROFIT_AGENT_GRAPH_CORE`）已纳入 **(B)**，与 **V2_SEMANTIC_MAINLINE_CORE_10**（**(A)**）并行作为阶段收口必跑项。

变更 **QuerySemanticParser（v2）**、**Resolver**、**FollowUp Rewrite**、**AnswerPlan wire**、**MasterBusinessAgent**、**BusinessToolExecutionNode**、**Graph 节点** 等相关代码时，应按改动范围 **至少** 跑通表中对应 Case（见下节 **「必须跑本 Case 前的改动范围」** 与各 Case 详解）。

**语义主链（P4-G）**：生产 **V2-only**（`semantic.query_parser.v2`）。主断言 **`semanticSlots`**（含 `queryObject` / `operation` / `metric` / `sourceFacet` / `detailWanted` / `anchorPolicy` / `structuredIntentDetailWire`）、**`semanticContractValidation.matchedContractId`**、**`executionIntentType`** / **`executionDetailWanted`** / **`focusEntity*`** / **`resultAnchors`**。FollowUp **Rewrite**（`LlmFollowUpQueryRewriter`）保留；**无**旧 execution arg/payload 主链。`metric.rankingType` 仅 Parser **debug** 字段，**不作** wire / execution 路由。

**前台 Run Debug / Replay 面板（P4-G3）**：GET Run 与 SSE `answer_delta` 的 **`resolvedQueryContextSummary`** 与 replay 每轮同源。UI 分组、已删字段清单、推荐读取路径见 **`docs/api/frontend-api-contract.md` §7.12～§7.13**。**勿**再展示 Drilldown / FollowUp Detail / `rankingType` 主 wire 分组。

### Replay 断言契约（Current — D-CLEAN-DOCS-REPLAY-CONTRACT-FINAL）

**优先断言（与现网 Resolver / Graph 一致）**

| 字段 / 探针 | 用途 |
|-------------|------|
| **`effectiveIntentCode`** | 收养后 intent |
| **`effectivePathCode`** | 收养后 path |
| **`semanticSlots`**（含 **`structuredIntentDetailWire`**、`queryObject` / `operation` / `metric` / `sourceFacet` / `detailWanted` / `anchorPolicy`） | V2 槽位主依据 |
| **`semanticContractValidation.matchedContractId`** | Step 2 合同命中 |
| **`executionIntentType` / `executionDetailWanted` / `focusEntity*`** | 采购等 anchor execution 观测 |
| **`resultAnchors`** | 上轮结果实体锚（GOODS# / SUPPLIER# 等） |
| **`structuredIntentDetail`** / Harness **`rawStructuredIntentDetail`** | canonical wire（摘要来自 slots / `currentTurnStructuredIntentDetailWire`，**非** rankingType） |
| **`selectedTools` / `usedTools`** | Tool 规划结果 |
| **AnswerPlan 探针**（如 `harnessReplay*AnswerPlanType`） | 消费层 planType |
| **`startDate` / `endDate` / `effectiveTimeWindowSource` / v2 `timeAction`** | 时间窗（V2 + Policy） |

**仅 debug / compat（不得作为唯一 PASS 条件）**

| 字段 | 说明 |
|------|------|
| **`metric.rankingType`** | LLM 可输出；Harness 可记录；**deprecated/debug**；**不得**单独断言其等于 canonical wire 而忽略 slots；**前台勿作主调试字段** |
| **`stockReduceType`**（metric facet） | Tool 过滤 / debug；非 wire 权威 |

**已删 wire（Replay / Debug 勿再断言或展示）**：`diagnosisDrilldownMatrixRowId`、`purchaseGoodsSupplierDrilldown` / `purchaseGoodsDrilldownTarget*` / `purchaseSupplierDrilldown*`、`followUpDetailWanted` / `followUpAction` / `followUpTargetEntity*` / `followUpSourcePlanType` / `followUpRegistryQueryMode`、`DrilldownMatrix` —— 替代字段见 **`docs/api/frontend-api-contract.md` §7.12**。

**历史 JSON**：`out/` 下 replay 抓包为旧抓包，字段集合可能含已删 Tool id 或旧 wire 主断言；**以本文档 + 内置 case expected + `docs/ai/semantic-allowed-output-contract-design.md` / `docs/ai/semantic-contract-strict-mode-plan.md` 现网契约为准**。

| `caseId` | 说明 |
|---------|------|
| **`BUSINESS_SEMANTIC_1B_RESOLVED_CONTEXT`** | **经营类 1B（13 轮 · 默认 `RESOLVED_CONTEXT_ONLY`）**：语义矩阵 R01–R10（R08–R10 为 2+2+2 轮）；内置预期仅校验 **intent / path / wire（含 R06 `*AnyOf`）/ 时间窗与 v2 `timeAction` / 多店 scope 探针** 等，不比 Tool / `AnswerPlan` 行集 / Composer。须传入与 `AiHarnessBuiltinCases#messagesBusinessSemantic1bResolvedContext()` 一致的 **`messages`**；矩阵见 **`docs/ai/business-phase1b-semantic-harness-matrix.md`**。 |
| **`STOCK_REDUCE_SEMANTIC_1C_RESOLVED_CONTEXT`** | **出库 / 核销 1C（18 轮 · 默认 `RESOLVED_CONTEXT_ONLY`）**：矩阵 R01–R15（R11–R13 各 2 轮）；仅断言 **effective intent/path、canonical wire、`stockReduceType`（debug）、`timeSource`/`timeAction`、scope/多店、多轮继承**；R14/R15 为 **`BUSINESS_DIAGNOSIS`**，**不得**期望 **`STOCK_REDUCE_QUERY` / `stock_reduce_query_path`**；R15 wire **AnyOf**（`purchase_slow_moving_risk` \| `purchase_stock_reduce_mismatch`）。可比 **`AiHarnessBuiltinCases#messagesStockReduceSemantic1cResolvedContext()`**；矩阵 **`docs/ai/stock-reduce-phase1c-semantic-harness-matrix.md`**。 |
| **`V2_SEMANTIC_MAINLINE_CORE_10`** | **核心回归必跑**：固化已通过验收的真实问句顺序；**不跑完整 Graph / Tool**，仅验证 **v2 解析 + Resolver + FollowUp Rewrite + Harness 摘要** 与关键 AnswerPlan / **`structuredIntentDetail`** 探针。**当前仍必须通过**。修改 **`QuerySemanticParser`**、Resolver、FollowUp Rewrite、TimeWindow、OrgScope、**`MasterBusinessAgent`**、**`BusinessToolExecutionNode`**、**AnswerPlan wire** 后，应 **优先** 跑通本 Case 再合。**语义覆盖**：十条轮次交织 **四条单领域 Agent**（营收 / 采购 / 出库核销 / 菜品毛利）的 **语义入口**及经营概览问法等；**不等价** Master 全链路调度断言 —— **真实 Master 编排与时序仍须 `POST /api/ai/runs` 验证**。 |
| **`DISH_PROFIT_RANKING_TO_NAMED_DISH_FOLLOWUP_2`** | **专项（2 轮）**：上一轮「上个月哪个菜毛利率最低？」须落 **`dish_profit_ranking_low_margin`** / 探针 **`DISH_LOWEST_MARGIN`**；追问「核桃芽菜西芹毛利怎么样？」须在**无本句时间词**时 **`effectiveTimeWindowSource=INHERITED_PREVIOUS`**，且 **`structuredIntentDetailWire=dish_gross_margin_query`**、**`querySemanticV2MetricAction=OVERRIDE`**、**`dishProfitMetricType=GROSS_MARGIN`**、**`harnessReplayDishProfitAnswerPlanType=DISH_PROFIT_RATE`** —— **不得**再继承上轮排行口径（与 V2 Case 前两轮等价，便于 CI 中单跑）。 |
| **`BUSINESS_DIAGNOSIS_V1_CORE_3`** | **DiagnosisAgent v1（3 轮 · `GRAPH_RUN` 默认）**：固化「集团本月问诊 → AAA 单店成本偏高 → 双店并排原因」。断言 **`effectiveIntentCode=BUSINESS_DIAGNOSIS`**、**`business_diagnosis_path`**、**`orchestrationTaskMode=MULTI_AGENT`**、四域 **`consumedAnswerPlans`**、`answerPreview` 含「经营诊断」、单店轮 **`businessDiagnosisPlan.dataCompleteness.revenue=OK`** 等（**不比** Composer 正文长文）。详解 **Case Diagnosis v1**。 |
| **`BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3`** | **BusinessOverview 四域 MultiAgent（3 轮，`GRAPH_RUN` 默认）**：固化「这个月经营怎么样 → 那上个月 → 双店对比」真实问法；断言 **`effectiveIntentCode=BUSINESS_OVERVIEW`**、**`orchestrationTaskMode=MULTI_AGENT`**、**`businessOverviewSuccessfulDomains`** ⊇ revenue/purchase/stockReduce/dishProfit、四类 **`consumedAnswerPlans`**、**`missingAnswerPlans=[]`**、**`answerPreview`** 经营概览话术且不含旧 AiBusinessOverviewResult fallback / 误诊「经营诊断·证据型」、第 3 轮 **`multiStoreScopeApplied`** + **`queryStoreIds` ⊇ {1,3}** + **`scopeLabel`** 含占位店名。详见 **Case BusinessOverview MultiAgent**。 |
| **`REVENUE_AGENT_GRAPH_CORE`** | **营业额单域（3 轮 · `GRAPH_RUN` 默认）**：`这个月营业额多少？` → `上个月呢？` → `AAA 这个月营业额多少？`。断言 **`REVENUE_OVERVIEW`** / **`revenue_overview_path`**，**非** `BUSINESS_OVERVIEW` / `BUSINESS_DIAGNOSIS`；**`usedTools` ⊇ `revenue_query`**、**`masterRevenueToolResultSuccess`**、**`DailyRevenueAnswerPlan`** 消费镜像、**`answerPreview`** 含营业额语义、第 3 轮 **`queryStoreIds` ⊇ {1}**（AAA）。 |
| **`PURCHASE_AGENT_GRAPH_CORE`** | **采购单域（3 轮 · `GRAPH_RUN` 默认）**：采购金额三连问同上结构。断言 **`PURCHASE_OVERVIEW`** / **`purchase_overview_path`**、**`purchase_overview` tool**、**`PurchaseAnswerPlan`**、**`answerPreview`** 含采购、第 3 轮 **STORE / `queryStoreIds` ⊇ {1}**。 |
| **`PURCHASE_PERIOD_GOODS_LIST_1`** | **原料采购清单（1 轮 · `GRAPH_RUN` 默认）**：`昨天买了什么？` → **`selectedContractId=purchase.period_goods_list`** / wire **`purchase_period_goods_list`**；**`purchaseSourceType=ALL`**；**`harnessReplayPurchaseAnswerPlanType=PURCHASE_PERIOD_GOODS_DETAIL`**；**`executionIntentType=EXEC_PERIOD_GOODS_LIST`**（不以 **`executionDetailWanted`** 断言）；**STORE / `queryStoreIds` ⊇ {3}**（汀兰餐厅，与当前权限环境一致）；**不得**在最终选中 wire/plan 上误落 **`purchase_overview_summary`**；时间窗为 **`frozenClockDate` 前一日**；**`answerPreview`** 含原料采购 / 详见下方卡片。 |
| **`PURCHASE_PERIOD_GOODS_LIST_SELF_1`** | **自采原料清单（1 轮 · `GRAPH_RUN`）**：`昨天自采了什么？` → **`purchase.period_goods_list.self`**；**`purchaseSourceType=SELF_PURCHASE`**；同 **`PURCHASE_GOODS_DETAIL_CARD`**；**STORE / `queryStoreIds` ⊇ {3}**；**不得**在最终选中路径上误落 **`purchase_overview_summary`** / **`purchase.self_overview`**。 |
| **`PURCHASE_PERIOD_GOODS_LIST_SUPPLIER_1`** | **供货商订货清单（1 轮 · `GRAPH_RUN`）**：`昨天订货了什么？` → **`purchase.period_goods_list.supplier`**；**`purchaseSourceType=SUPPLIER_PURCHASE`**；同 **`PURCHASE_GOODS_DETAIL_CARD`**；**STORE / `queryStoreIds` ⊇ {3}**；**不得**在最终选中路径上误落 **`purchase_overview_summary`** / **`purchase.supplier_overview`**。 |
| **`STOCK_REDUCE_AGENT_GRAPH_CORE`** | **出库核销单域（3 轮 · `GRAPH_RUN` 默认）**。断言 **`STOCK_REDUCE_QUERY`** / **`stock_reduce_query_path`**、**`stock_reduce_query` tool**、**`StockReduceAnswerPlan`**、**`answerPreview`** 含出库或核销、第 3 轮 **`queryStoreIds` ⊇ {1}**。 |
| **`DISH_PROFIT_AGENT_GRAPH_CORE`** | **菜品毛利单域（3 轮 · `GRAPH_RUN` 默认）**：`上个月哪个菜毛利率最低？` → `核桃芽菜西芹毛利怎么样？` → `这个月哪个菜毛利率最高？`。第 2 轮同 **`DISH_PROFIT_RANKING_TO_NAMED_DISH_FOLLOWUP_2`**（**`dish_gross_margin_query` / `OVERRIDE` metric / `GROSS_MARGIN`**）；第 3 轮 **高毛利排行**（**`dish_profit_ranking_high_margin`**，**`RANKING_HIGH_MARGIN`**，**`mentionedDishName` 须空**），**`querySemanticV2MetricAction=OVERRIDE`**（不继承单菜追问口径）。 |
| **`DISH_PROFIT_ACTUAL_COST_RANKING_1`** | **实际成本最高排行（1 轮 · `GRAPH_RUN` 默认）**：`上个月成本最高的是什么菜？` → **`primaryDomain=DISH_PROFIT`**、**`selectedContractId=dish_profit.ranking_high_actual_cost`**、wire **`dish_actual_cost_ranking_high`**、**`operation=RANKING`**、**`metric=ACTUAL_COST`**、**`harnessReplayDishProfitAnswerPlanType=DISH_HIGHEST_ACTUAL_COST`**；**不得** **`DISH_COST`** / **`MISSING_SELECTED_CONTRACT_ID`**。 |
| **`DISH_PROFIT_HIGH_PROFIT_AMOUNT_RANKING_1`** | **利润额最高排行（1 轮 · `GRAPH_RUN` 默认）**：`这个月哪个菜最挣钱？` → **`selectedContractId=dish_profit.ranking_high_profit_amount`**、wire **`dish_profit_ranking_high_profit_amount`**、**`metric=GROSS_PROFIT_AMOUNT`**、**`harnessReplayDishProfitAnswerPlanType=DISH_HIGHEST_PROFIT_AMOUNT`**、**`sortKey=grossProfitAmount`**；**不得** **`dish_profit_ranking_high_margin`** / **`DISH_HIGHEST_MARGIN`**（与「毛利率最高」互斥）。 |
| **`DISH_SALES_TO_COST_DIMENSION_SWITCH_2`** | **维度切换（2 轮）**：`销量高` → `成本呢` → 第 2 轮须 **`DISH_PROFIT`** + **`dish_profit.ranking_high_actual_cost`** + **`IGNORE_PREVIOUS_ANCHOR`**；**`mentionedDishName` 须空**；**不得** **`DISH_COST`** / **`dish_cost.single_dish_analysis`**。 |
| **`DISH_SALES_TO_MARGIN_DIMENSION_SWITCH_2`** | **维度切换（2 轮）**：`销量高` → `毛利呢` → 第 2 轮须 **`DISH_PROFIT`** 毛利排行 + 时间继承；**不得**单菜 **`DISH_COST`**。 |
| **`DISH_PROFIT_COST_TO_SALES_DIMENSION_SWITCH_2`** | **维度切换（2 轮）**：`上个月成本最高的是什么菜？` → `销量呢` → 第 2 轮须 **`DISH_SALES`** 销量排行 + 时间继承；**不得** **`DISH_COST`**。 |
| **`DISH_PROFIT_MARGIN_TO_SALES_DIMENSION_SWITCH_2`** | **维度切换（2 轮）**：`上个月毛利最高的是什么菜？` → `销量呢` → 第 2 轮须 **`dish_sales.count_ranking_high`** + **`IGNORE_PREVIOUS_ANCHOR`**。 |
| **`DISH_SALES_TO_AMOUNT_DIMENSION_SWITCH_2`** | **维度切换（2 轮）**：`销量高` → `销售额呢` → 第 2 轮须 **`dish_sales.amount_ranking_high`** + **`SALES_AMOUNT`** + **`IGNORE_PREVIOUS_ANCHOR`**。 |
| **`DISH_NAMED_DISH_COST_SINGLE_1`** | **点名菜成本（1 轮）**：`酸奶碗成本呢` → 仍须 **`DISH_COST`** + **`dish_cost.single_dish_analysis`** + **`mentionedDishName=酸奶碗`**。 |
| **（手动 · P1 已通过）** **`DISH_PROFIT_PRESCRIPTION_P1`** | **单菜利润处方 P1（3 条单轮 · `GRAPH_RUN`）**：处方主问句 / 55% 目标毛利 / 成本旧路径不回归。须 **`userId=3`** + **`scopeMode=GROUP`**（D-11 权限）；**不得**用 `userId=1` 跑处方双 Tool。断言 **`dish.profit.prescription.v1`**、**`dish_profit_prescription`**、**`DISH_PROFIT_PRESCRIPTION_CARD`**、**`answerPreview` 不含英文 knownGap code**；成本问句仍 **`DISH_COST_ANALYSIS_CARD`**。完整探针表见 **`docs/ai/dish-profit-prescription-p1-acceptance.md`**。 |

### GRAPH_RUN 主干变更（Diagnosis / BusinessOverview 四域）

若改动 **经营诊断**（`business_diagnosis_path`）、**经营概览四域 MultiAgent**（`business_overview_path` + 四专线 Tool）、**AnswerPlan 消费链**、**`AiRunService` Harness 图执行**、**Resolver 内与 `orchestrationTaskMode` 相关的 canonical 对齐** 等 —— 除 **V2_SEMANTIC_MAINLINE_CORE_10** 外，须 **追加** 跑通 **`BUSINESS_DIAGNOSIS_V1_CORE_3`**、**`BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3`**（**`GRAPH_RUN`**，见 **「GRAPH_RUN 核心回归」** 小节）。

**必须跑本 Case 前的改动范围**（至少跑一次 `POST /api/ai/harness/replay`，建议 `frozenClockDate`=`2026-05-13`、`strictStoreSqlMatch`=`false`，与下文 Case V2 一节一致）：

1. **QuerySemanticParser**（含 v2 prompt / 解析与采纳策略）  
2. **Resolver**（`AiResolvedQueryContextResolver` 等）  
3. **FollowUp Rewrite**（`LlmFollowUpQueryRewriter` / 多轮问句补全）  
4. **TimeWindow**（Resolver 时间窗 + `BusinessTimeWindowNode` 镜像）
5. **OrgScope**（多轮组织范围、`AiMultiTurnOrgScopePolicy`）  
6. **AnswerPlan wire**（与 Harness 摘要中 `harnessReplay*` / `structuredIntentDetail` 探针对齐的路径）  
7. **`MasterBusinessAgent`**（四条专线调度 / fallback / legacy skip / debug 扁平字段）  
8. **`BusinessToolExecutionNode`**（Master 入口合并、`toolResults` 剥离与 legacy 衔接）

**本 Case 覆盖概览**：菜品毛利排行 → 单菜追问 → 多店营收·采购·出库 / …（前 **2** 轮对 **「毛利率最低排行→点名菜名追问单菜毛利」** 有硬断言：**第 2 轮**须要 **`effectiveTimeWindowSource=INHERITED_PREVIOUS`**、**`mentionedDishName`**、**`structuredIntentDetailWire=dish_gross_margin_query`**、**`querySemanticV2MetricAction=OVERRIDE`**、**`dishProfitMetricType=GROSS_MARGIN`**、探针 **`harnessReplayDishProfitAnswerPlanType=DISH_PROFIT_RATE`**，**不得**仍为 **`dish_profit_ranking_low_margin` / `DISH_LOWEST_MARGIN`**）；包含 **v2 时间继承**、**scope / 多店 harness 继承与释放** 等链路。

正文详解、消息列表与 JSON 请求示例见下文 **「Case V2 — v2 主语义 10 轮」**。

### GRAPH_RUN 核心回归：覆盖范围与历史问题保护（阶段收口）

| `caseId` | 覆盖范围（摘要） | 主要防止的回退 |
|---------|------------------|----------------|
| **`BUSINESS_DIAGNOSIS_V1_CORE_3`** | 证据型经营诊断 **MULTI_AGENT**；集团 → 单店 AAA → 双店并排「原因」；**四域** AnswerPlan 消费与摘要字段 | `business_diagnosis_path` 未跑齐四域专线；单店 **scope / SQL 部门**未落地；**`businessDiagnosisPlan`** 误报 MISSING；多店 **queryStoreIds / scopeLabel** 失效 |
| **`BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3`** | **BUSINESS_OVERVIEW** + **`business_overview_path`** 下 **四域汇总**；本月 MTD →「那上个月」→ 双店对比；时间与多店继承 | MultiAgent **未真正调度四域**；**DishProfitAnswerPlan** 缺席；旧 **`AiBusinessOverviewResult`** fallback 话术抢占 **`answerPreview`**；多店对比 **未继承**上月窗；**queryStoreIds** 未含占位 `{1,3}`；**`orchestrationTaskMode`** 与真实四域执行不一致（须稳定 **`MULTI_AGENT`**） |
| **`REVENUE_AGENT_GRAPH_CORE`** | **REVENUE_OVERVIEW** 专线 + **`revenue_query`**；多轮时间窗与 AAA 单店缩窄 | 单域误收成 **经营概览 / 诊断**；**营业额** Tool 或 AnswerPlan 链路断裂；AAA 轮 **scope** 未落地 **STORE** |
| **`PURCHASE_AGENT_GRAPH_CORE`** | **PURCHASE_OVERVIEW** + **`purchase_overview`** | 同上（采购专项） |
| **`STOCK_REDUCE_AGENT_GRAPH_CORE`** | **STOCK_REDUCE_QUERY** + **`stock_reduce_query`** | 同上（出库核销专项） |
| **`DISH_PROFIT_AGENT_GRAPH_CORE`** | **DISH_PROFIT**；排行 → 点名菜毛利 → **本月**最高毛利排行 | 排行→点菜名 **未 OVERRIDE metric**；第 3 轮 **未切** **高毛利排行** 或 **误继承** 单菜 wire / **rankingType** |

上述 **`GRAPH_RUN`** Case 均通过 **`POST /api/ai/harness/replay`**，`caseId` 如上；建议 **`strictStoreSqlMatch=false`**（环境门店 ID 与文档占位不一致时）；**`frozenClockDate`** 与内置 **`LocalDateAnchor`** 对齐（文档示例多为 **`2026-05-13` / `2026-05-14`**）。**`overallPass=true`** 且各轮 **`failedFields=[]`** 为 CI / 合入前验收口径。

### D-10：多轮对话 GRAPH_RUN · 全局状态探针（2026-05-15 收口）

本小节固化 **Harness 在长会话末尾**仍可读的 **GraphRun / RunState 层探针**（主要来自 **`AiRunState`** 合并进 **`harnessDebug.resolvedQueryContextSummary`**；精简视图见 Replay 响应每轮 **`probe`**：`AiHarnessReplayProbeView`，与摘要同源）。

**语义主线（第 N 轮，示例为长会话末尾「双店对比 + 归因」）**：

- **`effectiveIntentCode`** = **`BUSINESS_DIAGNOSIS`**，`effectivePathCode` = **`business_diagnosis_path`**
- **`structuredIntentDetail` / wire**（摘要键名以运行时为准）：**`business_store_status_compare_diagnosis`**
- **`orchestrationTaskMode`**：**`MULTI_AGENT`**

**已验收摘录（负责任意长会话 GRAPH_RUN JSON 对齐用；环境数据可变，探针取值须自洽）**：

| 探针键（摘要 / probe） | 健康含义（本类问法） |
|------------------------|----------------------|
| **`businessDiagnosisPath`** | **`true`**：`business_diagnosis_path` 与 RunState 一致 |
| **`dataPlanTools`** | 数组含 **`revenue_query`**、**`purchase_overview`**、**`stock_reduce_query`**、**`dish_profit_analysis`**（四域齐全；缺一优先查权限裁剪或非 Multi 编排，`MasterBusinessAgent` 亦有对应自检文案） |
| **`diagnosisPlanExists`** / **`diagnosisPlanPresent`** | **`true`**：**`AiRunState#getDiagnosisPlan()`** 非空（**推荐** Replay 断言键） |
| **`diagnosisPlanType`** | 如 **`OVERALL_BUSINESS_DIAGNOSIS`**（`DiagnosisPlan.planType`） |
| **`businessDiagnosisPlanExists`** | **Deprecated compat**：与 **`diagnosisPlanExists`** 同义；**非**已删 `BusinessDiagnosisPlan` DTO |
| **`harnessReplayBusinessDiagnosisPlanType`** | **Deprecated compat**：与 **`diagnosisPlanType`** 同源（Explorer **`probe`** 前缀） |
| **`businessStoreCompareEvidenceRowsLen`** / **`harnessReplayStoreCompareEvidenceRowsLen`** | 双店并排时为 **`2`**（与对比门店数一致；键名两处为 Summarizer 摊平惯例） |
| **`finalAnswerTextBlank`** | **`false`**：Composer 终稿非空 |
| **`needSemanticClarification`** / **`needClarification`** | **`false`**：非澄清岔路 |
| **`permissionDenials`** | **`null`** 或空数组：无 Tool 权限拒绝积压（**D-11** 将强化角色场景下本字段与 SQL 的一致性） |

**最小回归命令（合入 / 改过 Graph、Composer、Summarizer、权限条带时请至少跑与本改动相交的子集）**：

1. **`V2_SEMANTIC_MAINLINE_CORE_10`**（Resolver-only）：语义主轴与时间/范围继承 —— **不可替代** GRAPH_RUN。**`replayMode` 省略**即可。
2. **`BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3`**（默认 **`GRAPH_RUN`**）：四域 **`BUSINESS_OVERVIEW`**。
3. **`BUSINESS_DIAGNOSIS_V1_CORE_3`**（默认 **`GRAPH_RUN`**）：三问法中含 **「AAA 和汀兰餐厅哪个经营情况更好，原因是什么？」** → 与上表 **Phase 2A 门店对比**探针对齐。
4. **四条单域** **`REVENUE_AGENT_GRAPH_CORE`**、**`PURCHASE_AGENT_GRAPH_CORE`**、**`STOCK_REDUCE_AGENT_GRAPH_CORE`**、**`DISH_PROFIT_AGENT_GRAPH_CORE`**（各默认 **`GRAPH_RUN`**，各 **3** 轮）：防单域误收成经营诊断 / 概览。

**一键请求体模版**（与上文各 Case 详解一致；按需改 **`caseId`**）：

```json
{
  "userId": 1,
  "distributerId": 2,
  "scopeMode": "GROUP",
  "frozenClockDate": "2026-05-14",
  "caseId": "BUSINESS_DIAGNOSIS_V1_CORE_3",
  "strictStoreSqlMatch": false,
  "replayMode": "GRAPH_RUN",
  "messages": [
    "这个月哪里有问题？",
    "AAA 门店这个月成本为什么偏高？",
    "AAA 和汀兰餐厅哪个经营情况更好，原因是什么？"
  ]
}
```

**长期门禁结论（团队约定）**：**是的** —— **`V2_SEMANTIC_MAINLINE_CORE_10`** 与 **`BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3`**、**`BUSINESS_DIAGNOSIS_V1_CORE_3`**、**四条单域 3 轮 `*_AGENT_GRAPH_CORE`** **并行**作为合入主链前的 **最小门卫集**。其中 **V2** 断言 **语义 + Harness 摘要**，**GRAPH_RUN Cases** 断言 **Tool / AnswerPlan / 探针**；改过 **Case V2 §说明**中所列模块时仍以 **「先 V2，再按需加 GRAPH_RUN」** 为准（见 **`docs/ai/master-business-agent-design.md`**）。

### D-11：权限边界（final spot check 收口 · 2026-05-15）

**门禁结论**：**已通过** D-11 **最小门卫** spot check（角色 × 营业额 / 采购 / 库存 / 诊断 / 跨店可见性）。产品与契约仍以 **`docs/PERMISSION_MODEL.md`**、**`docs/DOMAIN_ORG_MODEL.md`** 为准；**`sqlQueryDepartmentIds`（及等价 `queryDepartmentIds`）** 与 **`finalAnswerText`** 不得泄露无权限门店。

**Final Frozen Role Fixture**（Replay 须与目标库 **`gb_department_user`** 一致；详解与 JSON 模板见 **`docs/ai/d11-permission-frozen-role-fixtures.md`**）：

| Persona | `userId` | `scopeMode` | `departmentId` | 说明 |
|---------|----------|-------------|-----------------|------|
| 集团管理员 | **3** | **`GROUP`** | （可选） | 集团会话 |
| 门店采购（`STORE_PURCHASER`） | **2** | **`STORE`** | **3** | **汀兰餐厅** |
| 库房（`WAREHOUSE_MANAGER`） | **1** | **`STORE`** | **1** | **AAA** |
| AAA 店长（`STORE_MANAGER`） | **4** | **`STORE`** | **1** | **AAA** |

**已验收的最小门卫（摘要）**：

- **集团管理员**：可看多门店 **排行 / 对比**（在数据与权限允许时）。
- **采购员**：采购可看；问营业额为 **权限提示**；**不得**再出现「库房端可继续询问…」（须为 **采购视角** follow-up）。
- **库房员**：库存 / 出库核销可看；营业额 **权限提示**；**经营诊断** **降级为库房视角**。
- **店长**：单店可看；跨店或不可见门店须有 **权限提示**；单店诊断终稿 **本店口径**（**不得**误称「集团口径」）。

**公共修复点（收口备忘）**：Composer **权限拒绝短路**；**`business_diagnosis` 权限降级 Renderer**；**`STORE_PURCHASER` · revenue denied follow-up** 文案；STORE scope **「集团口径」→「本店口径」** 的 Renderer 补丁（见 **`d11-permission-frozen-role-fixtures.md`** §公共修复点）。

**非阻塞 polish**：STORE scope 单店诊断中避免 **「其它门店怎么样」** 类标题，后续可改为 **「当前权限范围内」**（不阻塞冻结）。

---

## 自动化 Replay 接口

**请求** `POST /api/ai/harness/replay`（`Content-Type: application/json`）

| 字段 | 说明 |
|------|------|
| `userId` | 必填；须能在 `gb_department_user` 解析出 admin（与正式 Run 一致） |
| `departmentId` / `distributerId` | 与正式 Run 一致 |
| `scopeMode` | 可选；**集团多轮 Case 1 建议显式传 `GROUP`**（若仅传 `departmentId` 而不传 `scopeMode`，会话创建规则与 `AiRunService` 相同：有 `departmentId` 会走 **STORE** 会话，易与集团预期不符） |
| `frozenClockDate` | 可选，`yyyy-MM-dd`；不传则用 JVM 当天，断言不稳定 |
| `caseId` | 可选：`PURCHASE_MULTITURN_1`（7 轮）、`MULTI_STORE_PUBLIC_SCOPE_BLOCK3`（3 轮，多门店公共范围）、`MULTI_STORE_GLOBAL_LINKS_CONFIRM_5`（5 轮）、`V2_SEMANTIC_MAINLINE_CORE_10`（10 轮，v2 主语义固化）、**`DISH_PROFIT_RANKING_TO_NAMED_DISH_FOLLOWUP_2`**（2 轮，毛利率最低排行→点名单菜毛利继承窗）、**`DISH_PROFIT_ACTUAL_COST_RANKING_1`**（1 轮，实际成本最高排行）、**`DISH_PROFIT_HIGH_PROFIT_AMOUNT_RANKING_1`**（1 轮，利润额/最挣钱排行）、**`BUSINESS_DIAGNOSIS_V1_CORE_3`**（3 轮，DiagnosisAgent v1）、**`BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3`**（3 轮，BusinessOverview 四域 `GRAPH_RUN`）、**`REVENUE_AGENT_GRAPH_CORE`** / **`PURCHASE_AGENT_GRAPH_CORE`** / **`STOCK_REDUCE_AGENT_GRAPH_CORE`** / **`DISH_PROFIT_AGENT_GRAPH_CORE`**（各 3 轮，单域 `GRAPH_RUN` 默认）；或由 `frozenClockDate` 推导日期 |
| `replayMode` | 可选；**`GRAPH_RUN`**（同步跑业务图 + Harness 摘要）或 Resolver-only。**`BUSINESS_DIAGNOSIS_V1_CORE_3`**、**`BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3`**、**`REVENUE_AGENT_GRAPH_CORE`**、**`PURCHASE_AGENT_GRAPH_CORE`**、**`STOCK_REDUCE_AGENT_GRAPH_CORE`**、**`DISH_PROFIT_AGENT_GRAPH_CORE`**、**`DISH_PROFIT_ACTUAL_COST_RANKING_1`**、**`DISH_PROFIT_HIGH_PROFIT_AMOUNT_RANKING_1`** 在 **未指定** `replayMode` 时 **默认 `GRAPH_RUN`** |
| `expectations` | 可选；与 `messages` 等长的自定义预期，**优先于** `caseId` |
| `messages` | 必填；多轮问句顺序 |
| `strictStoreSqlMatch` | 默认 `true`；`false` 时跳过 `visibleStoreRootIds` / `expandedSqlDepartmentIds` / **`queryStoreIds` 整表相等** 等强校验（库与占位 ID 不一致时用）；**仍**断言 **`queryStoreIdsMustContain` 子集**、`visibleStoreRootCountMin`、`querySemanticEffectiveMentionedStoreNames`（若内置预期含）等 |

Replay **断言门店 visible 范围**时请以 **`visibleStoreRootIds` / `visibleStores`** 为准；**不要**把 **`sqlQueryDepartmentIds`（及 `queryDepartmentIds` / `effectiveSqlDepartmentIds`）**当成「门店列表」——其中含子部门，故常见 `storeRoot=[3]` 而 SQL 列表为 `[3,4]`。

- `conversationId`：本次新开会话（每请求一条，避免污染线上会话）
- `overallPass`：所有带断言的轮次均通过
- `frozenClockDate`：实际使用的锚点日
- `rounds[]`：每轮含 `roundIndex`、`message`、`runId`（合成 id）、`conversationId`、`resolvedQueryContextSummary`、`pass`、`failedFields[]`

**`failedFields` 项**（`AiHarnessMismatch`）：`type`（`AiHarnessFailureType` 枚举）、`field`、`expected`、`actual`。示例：第 6 轮 `PURCHASE_SOURCE_MISMATCH`，`field=purchaseSourceType`，`expected=SUPPLIER_PURCHASE`，`actual=SELF_PURCHASE`。

**请求示例**（与 Case 1 对齐；`frozenClockDate` 与文档表一致时「本月」为 2026-05-01～2026-05-11）：

```json
{
  "userId": 1,
  "departmentId": 1,
  "distributerId": 2,
  "scopeMode": "GROUP",
  "frozenClockDate": "2026-05-11",
  "caseId": "PURCHASE_MULTITURN_1",
  "strictStoreSqlMatch": false,
  "messages": [
    "这个月采购多少钱？",
    "上个月呢？",
    "AAA 呢？",
    "自采购呢？",
    "汀兰餐厅呢？",
    "供货商订货呢？",
    "哪个供货商金额最高？"
  ]
}
```

本阶段 **不跑 Graph / DeepSeek**，仅验证解析与 Harness 摘要字段。

---

## Case 1 — 采购金额多轮追问（集团管理员 `admin=0`）

**身份 / 初始参数（与正式联调一致）**：`userId=1`，`departmentId=1`，`distributerId=2`，**会话请使用集团模式**（建议请求体带 `"scopeMode": "GROUP"`）。

**对话**：  
`这个月采购多少钱？` → `上个月呢？` → `AAA 呢？` → `自采购呢？` → `汀兰餐厅呢？` → `供货商订货呢？` → `哪个供货商金额最高？`

**当以 `frozenClockDate = 2026-05-11` 为锚点时，各轮典型预期如下**：

| 问句 | `effectiveIntentCode` | `effectivePathCode` | `effectiveTimeWindowSource`（典型） | 时间区间 | `scopeType` | `visibleStoreRootIds`（占位） | `purchaseSourceType` | `structuredIntentDetail` | `mentionedStore` |
|------|----------------------|---------------------|--------------------------------------|----------|-------------|-------------------------------|---------------------|--------------------------|------------------|
| 这个月采购多少钱？ | `PURCHASE_OVERVIEW` | `purchase_overview_path` | `CURRENT_MESSAGE_EXPLICIT` 或 `DEFAULT_MONTH_TO_DATE` | `2026-05-01`～`2026-05-11` | `GROUP` | `[1,3]` | `null` | `null` | `null` |
| 上个月呢？ | `PURCHASE_OVERVIEW` | `purchase_overview_path` | `CURRENT_MESSAGE_EXPLICIT` 或 `TIME_SHIFT` | `2026-04-01`～`2026-04-30` | `GROUP` | `[1,3]` | `null` | `null` | `null` |
| AAA 呢？ | `PURCHASE_OVERVIEW` | `purchase_overview_path` | `INHERITED_PREVIOUS` | 同上上月 | `STORE` | `[1]` | `null` | `null` | `AAA` |
| 自采购呢？ | `PURCHASE_OVERVIEW` | `purchase_overview_path` | `INHERITED_PREVIOUS` | 同上 | `STORE` | `[1]` | `SELF_PURCHASE` | `null` | `AAA` |
| 汀兰餐厅呢？ | `PURCHASE_OVERVIEW` | `purchase_overview_path` | `INHERITED_PREVIOUS` | 同上 | `STORE` | `[3]` | **`null`**（仅切店、未声明自采/供货商渠道时不继承上一轮来源） | `purchase_overview_summary`（典型） | `汀兰餐厅` |
| 供货商订货呢？ | `PURCHASE_OVERVIEW` | `purchase_overview_path` | `INHERITED_PREVIOUS` | 同上 | `STORE` | `[3]` | `SUPPLIER_PURCHASE` | `null` | `汀兰餐厅` |
| 哪个供货商金额最高？ | `PURCHASE_OVERVIEW` | `purchase_overview_path` | `INHERITED_PREVIOUS` | 同上 | `STORE` | `[3]` | **`null`**（排行语义不收窄为单一渠道统计） | **`supplier_amount_ranking`** | `汀兰餐厅` |

**重点回归（断言失败时会映射到 `AiHarnessFailureType`）**：

- 「汀兰餐厅呢？」等**仅切换门店**、未再声明自采/供货商渠道时：`purchaseSourceType` 应为 **`null`**（全口径采购），**不得**继承上一轮「自采呢？」的 `SELF_PURCHASE`。
- 「供货商订货呢？」**不得**判成 `SELF_PURCHASE`（应为 `SUPPLIER_PURCHASE`）。
- 「哪个供货商金额最高？」应带上 `structuredIntentDetail=supplier_amount_ranking`，且 **不得**沿用上一轮 `purchaseSourceType=SUPPLIER_PURCHASE`（统计侧仍按采购概览聚合；供货商 Top 仅在真实 `nx_supplier_id>0` 上排行）。
- **不得**因为是集团账号就始终在 `GROUP` 范围不回缩门店：点到具体店后应为 `STORE` + 单根 `visibleStoreRootIds`。
- `visibleStoreRootIds`**只含门店根**（示例 `[1,3]`、`[1]`、`[3]`），子部门只允许出现在 **`effectiveSqlDepartmentIds`**（等价 `sqlQueryDepartmentIds`），不得混进门店展示字段。

枚举 **`AiHarnessFailureType`**：`INTENT_MISMATCH`，`PATH_MISMATCH`，`TIME_WINDOW_MISMATCH`，`TIME_SOURCE_MISMATCH`，`SCOPE_TYPE_MISMATCH`，`STORE_SCOPE_MISMATCH`，`DEPARTMENT_SCOPE_MISMATCH`，`PURCHASE_SOURCE_MISMATCH`，`TOOL_ARGUMENT_MISMATCH`，`SQL_RESULT_MISMATCH`，`COMPOSER_TEXT_MISMATCH`（前两阶段以后项预留）。

---

## Case Diagnosis v1 — `BUSINESS_DIAGNOSIS_V1_CORE_3`

**意图**：Regression 固化 **DiagnosisAgent v1** 已验收的三类真实问法。**默认 `replayMode=GRAPH_RUN`**：同步跑 **业务图 + Master 四域编排 + Summarizer**；断言以 **`resolvedQueryContextSummary`**（含 **`consumedAnswerPlans`、`orchestrationTaskMode`** 等）为准。**不比** Composer 终稿全文。

### 三类问题（messages 顺序）

1. 「这个月哪里有问题？」→ **集团**经营诊断（`MULTI_AGENT` + 四域 `consumedAnswerPlans`、`answerPreview` 含「经营诊断」、`missingAnswerPlans=[]`）。
2. 「AAA 门店这个月成本为什么偏高？」→ **`STORE`** 收窄 + `mentionedStore`/SQL 门禁 + `businessDiagnosisPlan.dataCompleteness.revenue=OK`（Replay **契约回填**）；**禁止**摘要 JSON 中出现典型 Tool 参数失败片段及「先补全日营业额或营收数据」类 action hint。
3. 「AAA 和汀兰餐厅哪个经营情况更好，原因是什么？」→ **多店并排**（`multiStoreScopeApplied=true`，`queryStoreIds` ⊇ `{1,3}`，`scopeLabel` 同时含占位店名），`answerPreview` **不得**为「经营概览·四域汇总」话术。

### 保护的历史退化点（节选）

| 退化 | 契约/断言关注点 |
|------|------------------|
| `business_diagnosis_path` 下四域 **`supports=false` 或未选齐四专线 tool** | `orchestrationSelectedTools` 须含 `BUSINESS_OVERVIEW_MULTI_AGENT_DOMAIN_TOOLS` 四类；缺失则 Replay 不写 `consumedAnswerPlans`。 |
| **AAA** 语义识别到但 **scope 未落地** | 第 2 轮：`queryScopeKind/queryScopeMode`、`queryStoreIds` ⊇ `{1}`、`resolvedVisibleStoreRootIds` ⊇ `{1}`。 |
| **visibleStores 只有名称无 id** 导致候选空 | Resolver 仍可产出 `queryStoreIds` / `expandedSqlDepartmentIds`；与 `resolved*` 镜像对齐。 |
| **`patchResolvedQueryContextAfterRunIntersect`** 擦空单店 **visibleStores** | `resolvedVisibleStoreRootIds` 从 **DataScope** 回填（无 RunState 时）。 |
| 旧 **`businessDiagnosisPlan.revenue=MISSING`** 误报 | 单店四域 **MULTI_AGENT** + 非空 SQL 展开时 Replay 注入 **`dataCompleteness.revenue=OK`**，Comparator 对齐。 |

### 请求示例

```json
{
  "userId": 1,
  "distributerId": 2,
  "scopeMode": "GROUP",
  "frozenClockDate": "2026-05-14",
  "caseId": "BUSINESS_DIAGNOSIS_V1_CORE_3",
  "strictStoreSqlMatch": false,
  "messages": [
    "这个月哪里有问题？",
    "AAA 门店这个月成本为什么偏高？",
    "AAA 和汀兰餐厅哪个经营情况更好，原因是什么？"
  ]
}
```

**说明**：`strictStoreSqlMatch=false` 时仍会对 **`queryStoreIdsMustContain` / `resolved*MustContain` 等子集断言**（见 `AiHarnessReplayExpectedRound`）；与「跳过整表相等」互不矛盾。

---

## Case BusinessOverview MultiAgent — `BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3`

**意图**：Regression 固化 **BusinessOverview** 已通过验收的三轮真实问法；**默认** **`replayMode`** 为 **`GRAPH_RUN`**（与 `AiHarnessReplayService` 对齐），摘要含 **`AiRunState`** 下的 **`consumedAnswerPlans`、`answerPreview`、Master debug 扁平字段**。

### 三类问题（`messages` 顺序）

1. 「这个月经营得怎么样？」—— 本月 MTD：`orchestrationTaskMode=MULTI_AGENT`，**四域 batch** 提交成功探针，`businessOverviewSuccessfulDomains` 含 **`revenue` / `purchase` / `stockReduce` / `dishProfit`**，四类 AnswerPlan **`consumed`**，`missingAnswerPlans=[]`，`answerPreview` 须含 **「经营概览」**（或 **「经营概览·四域汇总」**），且 **不得**含旧 AiBusinessOverviewResult fallback（如「经营看板未返回有效统计」等内置禁串）。
2. 「那上个月呢？」—— **上个自然月闭合区间**（`frozenClockDate=2026-05-14` 时为 `2026-04-01`～`2026-04-30`）；时间来源 **`effectiveTimeWindowSource`** 断言为 **`INHERITED_PREVIOUS`、`CURRENT_MESSAGE_EXPLICIT`、`SEMANTIC_EXPLICIT`、`TIME_SHIFT` 任一**，且 **`不得` 为 `DEFAULT_MONTH_TO_DATE`**；四域 **`consumed` / domains** 与同轮门禁与第 1 轮一致，`answerPreview` 仍须为经营概览口径。
3. 「AAA 和汀兰餐厅哪个经营情况好？」—— **继承**第 2 轮时间窗；**多店**：`multiStoreScopeApplied=true`，`queryStoreIds` ⊇ `{1,3}`（子集断言，与占位 ID 对齐），`scopeLabel`/`multiStoreMatchedStores` 含 **AAA、汀兰餐厅**；`answerPreview` **须**含「经营概览」类话术且 **禁止**误判为「**经营诊断·证据型**」。

### 请求示例

```json
{
  "userId": 1,
  "distributerId": 2,
  "scopeMode": "GROUP",
  "frozenClockDate": "2026-05-14",
  "caseId": "BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3",
  "strictStoreSqlMatch": false,
  "messages": [
    "这个月经营得怎么样？",
    "那上个月呢？",
    "AAA 和汀兰餐厅哪个经营情况好？"
  ]
}
```

（显式传入 `"replayMode": "GRAPH_RUN"` 亦可；未传时服务端对上述 `caseId` 已默认 Graph 同步跑。）

---

## Case 2 — 多门店名并排对比（公共 Harness 能力：`MULTI_STORE_PUBLIC_SCOPE_BLOCK3`）

两行店名额（示例 `AAA` + `汀兰餐厅`）应走 **集团子集**，不得误收成 **`STORE`** 单店。**第 2、3 轮**不要求完整 AnswerPlan，仅 Harness 公共层：`querySemanticEffectiveMentionedStoreNames`、`queryStoreIds`（占位，受 `strictStoreSqlMatch` 控制）、以及 `visibleStoreRootIds.size ≥ 2`。

**会话**：集团与 Case 1 相同占位（示例 `scopeMode`=`GROUP`）。

**消息（顺序）**：`AAA 和汀兰餐厅哪个营业额高？` → `AAA 和汀兰餐厅哪个采购金额高？` → `AAA 和汀兰餐厅哪个出库金额高？`

以 **`strictStoreSqlMatch = true`** 且库树与占位一致时：

| Round | `effectiveIntentCode` | `effectivePathCode` | `scopeType` | 其它 |
|------|-----------------------|---------------------|-------------|------|
| 1 | `REVENUE_OVERVIEW` | `revenue_overview_path` | `GROUP` | `structuredIntentDetail` wire：`revenue_store_amount_ranking`；`visibleStoreRootIds` `[1,3]`；`expandedSqlDepartmentIds` `[1,2,5,3,4]`；`queryStoreIds` `[1,3]`；`revenueAnswerPlanType` Replay 常为 `null` 时 Harness 用 **path + wire** 探针等价 `REVENUE_STORE_AMOUNT_RANKING` |
| 2 | `PURCHASE_OVERVIEW` | `purchase_overview_path` | `GROUP` | `querySemanticEffectiveMentionedStoreNames` 含两处店名；`queryStoreIds` `[1,3]` |
| 3 | `STOCK_REDUCE_QUERY` | `stock_reduce_query_path` | `GROUP` | 同上语义店名；`queryStoreIds` `[1,3]` |

**请求示例**：

```json
{
  "userId": 1,
  "departmentId": 1,
  "distributerId": 2,
  "scopeMode": "GROUP",
  "frozenClockDate": "2026-05-13",
  "caseId": "MULTI_STORE_PUBLIC_SCOPE_BLOCK3",
  "strictStoreSqlMatch": true,
  "messages": [
    "AAA 和汀兰餐厅哪个营业额高？",
    "AAA 和汀兰餐厅哪个采购金额高？",
    "AAA 和汀兰餐厅哪个出库金额高？"
  ]
}
```

---

## Case V2 — v2 主语义 10 轮（`V2_SEMANTIC_MAINLINE_CORE_10`）

> **核心回归必跑**：变更 QuerySemanticParser、Resolver、FollowUp、TimeWindow、OrgScope、**MasterBusinessAgent**、**BusinessToolExecutionNode**、AnswerPlan wire 相关逻辑前，须先跑通本 Case；说明与覆盖面（含 **Replay 不跑完整 Graph / Tool**、**真实 Run** 仍须验证 Master）见上文 **「核心回归必跑 Case」**。

固化已通过验收的真实问句顺序，回归 **v2 解析 + Resolver + Harness 摘要**（**不跑完整 Graph / Tool**）。建议 **`frozenClockDate": "2026-05-13"`**，**`strictStoreSqlMatch": false`**（用店名与 `visibleStoreRootCountMin`，避免环境门店 ID 漂移）。

**消息顺序（10 条）**：

1. 上个月哪个菜毛利率最低？  
2. 核桃芽菜西芹毛利怎么样？  
3. AAA 和汀兰餐厅哪个营业额高？  
4. 那采购呢？  
5. 那出库呢？  
6. AAA 和汀兰餐厅哪个出库金额高？  
7. 这个月经营得怎么样？  
8. 那上个月呢？  
9. AAA 和汀兰餐厅哪个经营情况好？  
10. 这个月营业额多少？  

**通用断言（每轮）**：`semanticAdoptedFrom=v2`、`semanticFallbackUsed=false`、`querySemanticV2ParseMissing=false`；摘要 JSON 不含 `v2_no_routable_path` / `Placeholder` / `empty_llm_response`；`querySemanticV2` 与 `querySemanticV2InputPreview` 树内不得出现键 `queryStoreIds`、`departmentIds`、`expandedSqlDepartmentIds`。

**Replay 无 RunState 时的 AnswerPlan 口径**：摘要中增加 `harnessReplay*` 探针（与 `PurchaseAnswerPlanBuilder` / `StockReduceAnswerPlanBuilder` / `DailyRevenueAnswerPlanBuilder` / 菜品毛利 **wire→计划类型** 规则一致），用于断言 `DISH_LOWEST_MARGIN`、`DISH_PROFIT_RATE`（单菜综合毛利率口径）、`PURCHASE_STORE_AMOUNT_RANKING`、`STOCK_REDUCE_STORE_AMOUNT_RANKING` 等，而非依赖 Tool 产物。

---

### Case V2a — 菜品毛利排行 → 点名单菜（`DISH_PROFIT_RANKING_TO_NAMED_DISH_FOLLOWUP_2`）

可与 **Case V2** 同一套 Harness 断言字段；仅需 **两条消息**，适于专项回归与本问题验收对齐。

**消息**：`上个月哪个菜毛利率最低？` → `核桃芽菜西芹毛利怎么样？`

| Round | `effectiveIntentCode` | `effectivePathCode` | `effectiveTimeWindowSource`（典型） | 其它关键 Harness 断言 |
|------|-----------------------|---------------------|-------------------------------------|-------------------------|
| 1 | `DISH_PROFIT` | `dish_profit_path` | `CURRENT_MESSAGE_EXPLICIT` 或 `SEMANTIC_EXPLICIT` | **`structuredIntentDetailWire`**（摘要）= `dish_profit_ranking_low_margin`；`dishProfitMetricType=RANKING_LOW_MARGIN`；`harnessReplayDishProfitAnswerPlanType=DISH_LOWEST_MARGIN` |
| 2 | `DISH_PROFIT` | `dish_profit_path` | **`INHERITED_PREVIOUS`**（对本句无新时间词） | **`mentionedDishName=核桃芽菜西芹`**；**`querySemanticV2TimeAction=INHERIT_PREVIOUS`**；**`querySemanticV2MetricAction=OVERRIDE`**；**`structuredIntentDetailWire=dish_gross_margin_query`**；`dishProfitMetricType=GROSS_MARGIN`；**`harnessReplayDishProfitAnswerPlanType=DISH_PROFIT_RATE`** |

```json
{
  "userId": 1,
  "departmentId": 1,
  "distributerId": 2,
  "scopeMode": "GROUP",
  "frozenClockDate": "2026-05-13",
  "caseId": "DISH_PROFIT_RANKING_TO_NAMED_DISH_FOLLOWUP_2",
  "strictStoreSqlMatch": false,
  "messages": [
    "上个月哪个菜毛利率最低？",
    "核桃芽菜西芹毛利怎么样？"
  ]
}
```

---

```json
{
  "userId": 1,
  "departmentId": 1,
  "distributerId": 2,
  "scopeMode": "GROUP",
  "frozenClockDate": "2026-05-13",
  "caseId": "V2_SEMANTIC_MAINLINE_CORE_10",
  "strictStoreSqlMatch": false,
  "messages": [
    "上个月哪个菜毛利率最低？",
    "核桃芽菜西芹毛利怎么样？",
    "AAA 和汀兰餐厅哪个营业额高？",
    "那采购呢？",
    "那出库呢？",
    "AAA 和汀兰餐厅哪个出库金额高？",
    "这个月经营得怎么样？",
    "那上个月呢？",
    "AAA 和汀兰餐厅哪个经营情况好？",
    "这个月营业额多少？"
  ]
}
```

若占位 ID 与真实库不一致，`strictStoreSqlMatch` 设 `false`：仍断言店名、`scopeType`、`visibleStoreRootCountMin`≥2、`intent/path`。

---

## 字段说明（ Harness 对齐 `harnessDebug.resolvedQueryContextSummary`）

| 字段 | 含义 |
|------|------|
| `effectiveIntentCode` | `AiResolvedQueryContext#getEffectiveIntentCode()` |
| `effectivePathCode` | `AiResolvedQueryContext#getEffectivePathCode()` |
| `effectiveTimeWindowSource` | `INHERITED_PREVIOUS` / `CURRENT_MESSAGE_EXPLICIT` / `DEFAULT_MONTH_TO_DATE` 等 |
| `startDate` / `endDate` | 本轮有效统计窗（闭合区间） |
| `scopeType` | `AiResolvedOrgScope#scopeType`：`GROUP` / `STORE` / … |
| `visibleStores` | 可见门店根列表 `{ storeDepartmentId, storeName }`（展示口径） |
| `visibleStoreIds` | 与本轮 `AiResolvedDataScope#getVisibleStoreIds()` 对齐 |
| **`visibleStoreRootIds`** | 门店根部门 ID 列表（`AiResolvedDataScope#getVisibleStoreRootIds()`） |
| `childDepartmentIds` | 由门店根展开出的直属子部门 ID（扁平） |
| `sqlQueryDepartmentIds` | 实际 SQL IN 用的部门 ID（门店根 ∪ 直属子部门等）；**不单算「几家店」** |
| `storeToChildDepartmentIds` | 门店根 → 子部门映射（Harness 中用字符串键，如 `"1":[2,5]`） |
| `visibleWarehouseIds` | 库房场景的库房部门 ID |
| `explicitChildDepartmentIds` | 用户点名子部门（当前多为空，预留） |
| `queryScopeMode` | 如 `STORE_ROOTS_AND_DIRECT_CHILDREN`、`WAREHOUSE_DEPARTMENT`、`EMPTY` |
| **`queryLevel`** | **别名**：与 `queryScopeMode` 相同 |
| `queryDepartmentIds` | **遗留别名**，内容与 `sqlQueryDepartmentIds` 相同 |
| `purchaseSourceType` | `SELF_PURCHASE` / `SUPPLIER_PURCHASE` 或 **`null`**（全口径采购） |
| `structuredIntentDetail` | 如 `supplier_amount_ranking`（供货商/供应商采购金额排行类追问） |
| **`effectiveIntentSource`** | 对齐 `AiResolvedQueryContext#getEffectiveIntentSource()` |
| **`effectiveScopeSource`** | 对齐 `AiResolvedQueryContext#getEffectiveScopeSource()` |
| **`effectiveTimeWindowSource`** | 时间锚来源（如 `INHERITED_PREVIOUS`、`CURRENT_MESSAGE_EXPLICIT`） |
| **`querySemanticV2MetricAction`** | v2 LLM `metricAction` 合并入上下文后的顶层摘要（Harness），排行→点菜名毛利追问须为 **`OVERRIDE`** 等 |

`followUpResolution.followUpType` 可对齐日志：`TIME_SHIFT`、`STORE_SCOPE_FOLLOW_UP`、`PURCHASE_DETAIL_FOLLOW_UP` 等（可选写入 Harness 断言）。

---

## Replay A — 采购金额多轮（简表）

口径与 **Case 1**、`caseId=PURCHASE_MULTITURN_1` 一致；以下仅作速查，细则与 API 见上文 **Case 1** 与 **`POST /api/ai/harness/replay`**。

## Replay B — 经营情况 → 时间 → 门店切换

| Step | User text | Expected `effectiveIntentCode` | Expected `effectivePathCode` | `effectiveTimeWindowSource` | Notes |
|------|-----------|-------------------------------|------------------------------|-----------------------------|--------|
| 1 | 经营情况怎么样？ / 生意怎么样？ | `BUSINESS_OVERVIEW` | `business_overview_path` | `DEFAULT` / 显式本月 | |
| 2 | 上个月呢？ | `BUSINESS_OVERVIEW` | `business_overview_path` | `CURRENT_MESSAGE_EXPLICIT` | `LAST_MONTH` |
| 3 | AAA 呢？ | `BUSINESS_OVERVIEW` | `business_overview_path` | 多为继承 | 门店收窄为 AAA |
| 4 | 汀兰餐厅呢？ | `BUSINESS_OVERVIEW` | `business_overview_path` | 继承 | `visibleStores` 为汀兰（若别名可解析） |

`purchaseSourceType`：整链 **`null`**（非采购路径）。

---

## Replay C — 库存 → 门店 → 低库存追问

| Step | User text | Expected `effectiveIntentCode` | Expected `effectivePathCode` |
|------|-----------|-------------------------------|------------------------------|
| 1 | 库存情况怎么样？ | `WAREHOUSE_STOCK_OVERVIEW` | `warehouse_stock_overview_path` |
| 2 | AAA 呢？ | `WAREHOUSE_STOCK_OVERVIEW` | `warehouse_stock_overview_path` |
| 3 | 低库存呢？ | 仍为库房/库存语义（Planner 用词略同时以 **effectiveIntentCode/path** + DataPlanner 标志为准） | `warehouse_stock_overview_path` 或等价收敛 |

断言时建议一并核对 **`workspaceMode`** 与 DataPlanner 选中的 tool id（见 SSE / trace），本条仅锁 **intent/path/scope/time**。

---

## Replay D — 菜品毛利 → 时间 → 单品

| Step | User text | Expected `effectiveIntentCode` | Expected `effectivePathCode` |
|------|-----------|-------------------------------|------------------------------|
| 1 | 菜品毛利怎么样？ | `DISH_PROFIT` | `dish_profit_path` |
| 2 | 上个月呢？ | `DISH_PROFIT` | `dish_profit_path` |
| 3 | 某个菜品呢？（点名具体菜名） | `DISH_PROFIT` | `dish_profit_path` |

另：**上一轮为「毛利率最低/最高排行」、本轮仅点菜名问毛利且无新时间词**时，`structuredIntentDetailWire` **不得**继续为排行口径；应答 **`dish_gross_margin_query`**，且 `querySemanticV2MetricAction` **应为 `OVERRIDE`**。内置 **`DISH_PROFIT_RANKING_TO_NAMED_DISH_FOLLOWUP_2`** 与 **Case V2** 前两轮已覆盖。

`purchaseSourceType`：整链 **`null`**。

---

## Unknown semantic 采集（Historical removed）

**D-AI-FILE-INVENTORY-CLEANUP-P1（2026-05-20）**：已删除未接线的 **`AiHarnessUnknownPurchaseSemanticLogger`** 与配置项 **`ai.harness.unknown-purchase-semantic-log-enabled`**。采购短追问语义扩充请走 **V2 `semanticSlots` + `AiQuerySemanticLexicon`** 正常迭代，勿恢复专用 Logger。

---

## 单菜利润处方 P1（后端已收口）

**验收文档**：**`docs/ai/dish-profit-prescription-p1-acceptance.md`**（2026-05-27，`userId=3` + `GROUP` full probe PASS）。

**P1 不含**：最新采购价、外部市场价、跨店菜品排名（用户正文为中文说明；Harness 可看 `dishProfitPrescriptionKnownGaps`）。

**前台待办**：渲染 **`DISH_PROFIT_PRESCRIPTION_CARD`**（§ **`docs/api/frontend-api-contract.md` §7.14**）。

---

## 后续（非本轮）

- 已提供 **`POST /api/ai/harness/replay`** 做解析链专用回归；与「每步 POST run + 拉 SSE」互补。
- 不要将本摘要用于正式用户界面；生产环境默认 **`ai.harness.debug-context-enabled=false`**、**`ai.harness.replay-enabled=false`**。
