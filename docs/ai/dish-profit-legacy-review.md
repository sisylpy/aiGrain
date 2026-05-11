# 旧版单板 Agent：菜品毛利 / 菜品成本能力复盘

> **文档目的**：为后续「菜品毛利 / 菜品成本分析」向新 Harness、多智能体架构迁移做基线说明。  
> **范围**：基于当前仓库中**已存在的**旧版服务、接口、Skill 与文档整理；**不新增代码与设计**，第五节起仅为**建议方案**（未实现）。  
> **出库四类口径（与现网定义一致）**：  
> **type1** = 生产耗用（制作菜品正常消耗）；**type2** = 废弃 / 过保鲜期废弃；**type3** = 损耗 / 丢失、破损、自然损耗（口语「报损」多指本类）；**type4** = 退货。  
> 旧代码注释、个别历史文档仍可能出现「损耗/报损」与 type 编号混用，迁移时应以 **业务口径 + `GbConstants.StockReduceType` / `LEGACY_STOCK_REDUCE_ASSETS.md`** 为准。

---

## 1. 旧版相关文件清单

### 1.1 Service（核心业务）

| 文件 | 职责 |
|------|------|
| `GbDepFoodBusinessInsightService` / `GbDepFoodBusinessInsightServiceImpl` | **菜品经营透视 / 列表级毛利**：调用 `GbDishCostAnalysisService.buildReport(salesDish)` 取每菜 `theoryCostAmount`、`actualCostAmount` 等，与 `gb_dep_food`、`gb_dep_food_sales` 销量/标价收入对齐；汇总 `blendedGrossMarginRateOnListPrice`、`grossMarginLevel`、周拆分销量、`scopeOutboundSubtotals` 等。新 Graph 的 **`DishProfitAnalysisTool`** 直接复用 **`buildInsight`**。 |
| `GbDishCostAnalysisService` / `GbDishCostAnalysisServiceImpl` | **菜品成本 / 出库分析主流程**：`buildReport`（`salesDish` / `outboundQty`）、`buildIngredientAnalysisReport`、`buildOutboundIngredientAnalysisReport`、`buildDishIngredientDashboard`；配方合并、按 `sumNeed` 分摊 **type1** 出库、`type2`/`type3` 按 share 摊到菜与料、`utilizationRate`（仅 **type1 分子**）等。 |
| `GbDepFoodService` / `GbDepFoodSalesService` | 部门菜品主数据、区间销量聚合（与报表 scope 一致）。 |
| `GbDepartmentGoodsStockReduceService` | **出库扣减汇总**：如 `queryProductionReduceAggByDisGoods`（**仅 type1**）、以及 1+2+3 等聚合，供成本报表与区间损耗率。 |
| `GbAiChatServiceImpl`（节选） | 旧**聊天 Agent**：`appendDishCostAnalysisFactSummary` 一类方法将 **月度 `GbDishCostAnalysisService` 结果**提炼为对话事实摘要（与新版 Graph 分流，但数据源同源）。 |

### 1.2 Mapper / XML

| 文件 | 职责 |
|------|------|
| `src/main/resources/mapper/GbDepartmentGoodsStockReduceMapper.xml` | 出库按商品/类型汇总：含 **生产成本按商品汇总**（菜品成本分析：**type1** 用于 `W_g`）、**生产+损耗+损失（1+2+3）** 等（均价、分摊、区间结构）。菜品成本**不单独**使用独立 `GbDishCostAnalysisMapper`——逻辑主要在 **Service + 本 Mapper**。 |
| 其它（销售/配方/部门） | 销量、配方、部门树等通过各 Entity 对应 Mapper（如 `GbDepFoodSales*`、`GbDepartment*` 等），以 `GbDishCostAnalysisServiceImpl` 实际调用为准。 |

### 1.3 Controller（旧版 HTTP 入口）

| 文件 | 职责 |
|------|------|
| `GbDishCostAnalysisController` | **`POST /gbDishCostAnalysis/report`**、`/ingredientAnalysis`、`/outboundIngredientAnalysis`、`/dishIngredientDashboard`**：前台配料分析、按商品出库、单菜看板。 |
| `GbDepFoodController`（及关联） | **`POST /gbdepfood/depGetAllFood`** 等：部门菜品列表；**四参齐**时挂 `gbDfBusinessInsight`、`ingredientAnalysisRows`、配方行出库统计（与成本服务对齐）。 |
| `GbAiChatController` | 旧版 AI 聊天入口；`topics` 中含 **`dish-profit`** 相关引导问法（高销量利润风险、成本差额、瓶颈原料、毛利带、单菜综合毛利等）。 |

### 1.4 DTO / Entity（与毛利强相关）

| 文件 | 职责 |
|------|------|
| `GbDepFoodEntity` | 部门菜品行：标价、展示名、挂接经营分析 Map 等。 |
| `GbDistributerFoodGoodsEntity` | **配方行**：`gb_dfg_goods_amount` 等单份用量；共料分摊分母、出库均价等字段与成本报表一致。 |
| `GbDepFoodSalesEntity` / 销售子表实体 | 实销份数、销售额；子表原料用量 **`gb_dep_food_goods_sales`**。 |
| `AiDishProfitOverviewResult` / `AiDishProfitDishBrief` | **新 Graph 专用**结构化输出（SSE `dishProfitOverview`），字段源自 `buildInsight` 压缩，**不代表旧单板独有**。 |

### 1.5 Prompt / Skill Markdown

| 文件 | 职责 |
|------|------|
| `src/main/resources/ai-skill-dish-cost-diagnosis.md` | **旧 Skill：菜品成本诊断**：数据源表名、与前台 **`depGetAllFood` / `dishIngredientDashboard`** 对齐规则、**禁止心算 `blendedGrossMarginRateOnListPrice`**、T±F 父级带、`grossMarginLevel`、配料 vs 整菜字段区分等。 |
| `src/main/resources/ai-data-field-lexicon.md` | 字段语义：配料利用率 vs 出库结构「制作率」等，避免混谈。 |
| `docs/LEGACY_AI_ANSWER_ASSETS.md` | **§菜品毛利 / 菜品经营分析**：旧版能力 → 新 `dish_profit_path` 对照。 |
| `docs/gb-dish-cost-allocation-model.md` | **共料 type1 分摊**与 **type2/type3 按 share 摊**的数学定义（文中符号仍写 W2/W3「损耗/损失」，阅读时用 **type2=废弃、type3=损耗** 对照）。 |
| `docs/gb-dish-cost-analysis-frontend.md` | **`/report` 接口**字段：`theoryCostAmount`、`actualCostAmount`、`scopeOutboundSubtotals` 等。 |
| `docs/gb-dep-get-all-food-business-frontend.md` | **`depGetAllFood`** 经营分析字段。 |
| `docs/gb-dish-ingredient-dashboard-api.md` | **单菜配料看板** API 与毛利标尺字段。 |

### 1.6 旧版测试 / Harness

| 位置 | 职责 |
|------|------|
| `BusinessDataPlannerRoutingTest` | 规划器路由：「菜品毛利怎么样」→ **`dish_profit_path`** + **`DISH_PROFIT_ANALYSIS`**。 |
| `ProfitDomainRoutingRegressionTest` | **毛利 / 利润**  standalone 问法 → **`PATH_DISH_PROFIT`**，避免粘采购路径。 |
| `AiPermissionGuardTest` | **`dish_profit_analysis`** 双权限与角色拒答。 |
| `docs/AI_HARNESS_REPLAY_CASES.md` | **Replay D**：菜品毛利 → 时间 → 单品 等多轮样例。 |

### 1.7 新 Graph（已接旧服务，便于对照「旧能力」）

| 文件 | 职责 |
|------|------|
| `DishProfitAnalysisTool` | 调用 **`GbDepFoodBusinessInsightService#buildInsight`**，注入 `ARG_RESOLVED_DEPARTMENT_IDS` 等与 **`AiResolvedQueryContext`** 对齐。 |
| `DishProfitAgentNode` | SSE 与文案编排；Permission 边界说明。 |
| `StubAnswerComposerNode` | **`DISH_PROFIT_COMPOSER_SYSTEM`**：基于结构化 `dishProfitOverview` 生成回复。 |

---

## 2. 旧版核心概念（沿用现有命名）

以下均为旧版报表 / `buildInsight` 已出现的概念，**不重新设计**。

| 概念 | 说明（旧版语境） |
|------|------------------|
| 菜品销售数量 | 区间内实销份数，来自销售聚合（如与 `soldPortions` / 经营行总份数一致）。 |
| 菜品销售金额 / 实收 | 与销售口径一致的销售金额字段（具体字段名见列表行）。 |
| 菜品标价收入 | **`listPriceRevenue`**：通常 **标价（部门行）× 实销份数**，用于毛利率分母（以服务端实现为准）。 |
| 菜品配方 / BOM | `gb_distributer_food_goods`：每道菜-原料 **单份用量**（多行同料合并）。 |
| 每份理论用料 / 理论用量 | 单份配方用量合计；整菜理论用量可表示为 **份数 × 单份用量**（配料行 `theoryOutboundQtyByRecipe` 等）。 |
| 理论成本 | 报表行 **`theoryCostAmount`**：配方侧 × 扣库均价等（与 `GbDishCostAnalysisServiceImpl` 一致）。 |
| 实际出库用量 / 金额（到菜） | **type1** 分摊重量 `alloc1`；配料行另有 **type2+type3** 按 share 摊；金额 × 均价。 |
| 实际生产耗用成本 | 偏 **type1** 维度成本（与「仅生产」利用率一致）。 |
| 废弃 / 损耗 / 退货成本 | 在 **配料分析** 中通过 **type2、type3、type4** 摊销进入 `actualWasteUsage`、`actualLossUsage` 等（以接口为准）。 |
| 实际成本（整菜） | **`actualCostAmount`**：报表整菜行；**单份全量 1+2+3** 见 **`actualCostPerPortion123`** / **`actualCostTotalAmount123`**。 |
| 理论毛利 / 实际毛利 | **`listPriceRevenue − theoryCost`** / **`listPriceRevenue − actualCost`**（金额）；行内再换算毛利率字符串）。 |
| 理论毛利率 / 实际毛利率 | **`grossMarginRateTheoryOnListPrice`**（偏理论成本）、**`blendedGrossMarginRateOnListPrice`**（**权威综合实际**，对标价收入，对应 **type1+2+3 成本**）；另 **`grossMarginRateOnListPrice`** 多与 **仅 type1** 展示相关，**与 T±F 对拍的是 `blended…`**（见 Skill）。 |
| 成本差异 | 如 **`diffCostAmount`**、**`absDiffCostAmountSum`**、配料 **`recipeTheoryQtyVsOutboundAllocDiff`** 等。 |
| 利用率 | 配料行 **`utilizationRate`**：**仅 type1 分摊 ÷ 理论用量**（不含 type2、3 进分子，`disclaimerZh`）。 |
| 异常排行 / 毛利排行 | 报表排序键：销量、**每份成本差异**、**单份实际成本**、价差绝对值等（见 Controller `sortBy`）。 |
| 父级毛利带 / 档位 | **`grossMarginStandardTarget`、浮动 F、三档 `grossMarginLevel`**（`IN_BAND` / `ABOVE` / `BELOW` / `UNKNOWN`）。 |

---

## 3. 旧版「理论成本」怎么算（据现有实现与文档）

**数据主链**：

- **配方**：`gb_distributer_food_goods`（`gb_dfg_food_id`、**`gb_dfg_goods_amount` 单份用量**、关联 `disGoodsId`）。
- **销量与标价**：`gb_dep_food_sales`、部门菜品 **`gb_dep_food`**（标价/份用于 **`listPriceRevenue`**）。
- **扣减与均价**：`gb_department_goods_stock_reduce`；**菜品成本报表中「按菜/按料均价」核心基于 type1（生产）出库**，理论金额用扣库均价链路（详见 `GbDishCostAnalysisServiceImpl`）。

**核心关系（与 `docs/gb-dish-cost-analysis-frontend.md` / 配料行一致）**：

- **配方理论用量（单菜单料）**：  
  `theoryOutboundQtyByRecipe ≈ soldPortions × recipeUnitPerDish`（合并同料多行 `u`）。
- **理论成本（整菜或行内）**：  
  由各料理论用量 × 原料单价（扣库/报表均价口径）加成，汇总为行的 **`theoryCostAmount`**（服务端完整规则见 `GbDishCostAnalysisServiceImpl`，含合并配方与部门 scope）。
- **标价收入**：  
  `listPriceRevenue = totalQty × unitPrice`（部门标价口径，`GbDepFoodBusinessInsightServiceImpl` 自销量与部门菜品单价取得）。
- **理论毛利率（对标价）**：  
  `grossMarginRateTheoryOnListPrice = (listPriceRevenue − theoryCost) ÷ listPriceRevenue`（收入≤0 时按实现返回 `null` 或 `"0.00"`）。

> **说明**：若口头简化「理论用量 = 销量 × 每份配方用量」在**单料**上成立；**整菜理论成本**为**各料理论成本之和**（已含合并配方与报表实现细节），以 **`theoryCostAmount`** 为准。

---

## 4. 旧版「实际出库成本」怎么算

**数据来源**：

- 出库主表 **`gb_department_goods_stock_reduce`**，按 **`gb_dgsr_type`** 区分 **type1～type4**（语义见节首定义）。
- **按商品汇总**：`GbDepartmentGoodsStockReduceMapper` / `GbDepartmentGoodsStockReduceService`（如 **`queryProductionReduceAggByDisGoods`** 仅 **type1**）。

**与菜品/门店/时间关联**：

- 统计区间 **[startDate, stopDate]**，部门 scope 由 **`depFatherId` / `searchDepId` / `subDepId`** 及 **`scopeDepartmentIdsAllowFilter`**（区域与 AI scope 求交）解析，与 **`GbDishCostAnalysisService`** 内 **`resolveScopeDepIds`** 一致。

**分摊（与 `docs/gb-dish-cost-allocation-model.md` 一致）**：

- **type1（生产）重量 `W_g`**：按 **`alloc1_{i,g} = W_g × needThis_{i,g} / sumNeed_g`**，其中 **`needThis_{i,g} = q_i × dishU_{i,g}`**，**`sumNeed_g = Σ_i needThis_{i,g}`**（主分支）。
- **type2、type3**：  
  `share_{i,g} = alloc1_{i,g} / W_g`，**`alloc2 = share × W2_g`**，**`alloc3 = share × W3_g`**（与 type1 同一 share）。
- **整菜实际成本（元）**：配料 **`actualUsage`（约 type1+2+3）** 合价；**`actualCostPerPortion`** / **`actualCostPerPortion123`** 为 **单份 type1+2+3** 口径（见 `GbDishCostAnalysisService` 注释）。

**是否只算 type1**：

- **理论成本、配料均价、利用率分子**：以 **type1** 为主口径。
- **「实际成本」整菜/综合毛利率 `blendedGrossMarginRateOnListPrice`**：对 (**标价收入 − type1+2+3 摊销总成本**) / 标价收入（见 `buildInsight` 与 Skill：**与 T±F 对拍用 `blended…`**）。
- **区间整体结构**：**`scopeOutboundSubtotals`** 给出 **type1/2/3 金额** 与 **`wasteLossRatioInOutbound123` = (2+3)/(1+2+3)**（**与单菜行的「利用率」不同指标**）。

**废弃、损耗是否纳入「菜品成本」**：

- **纳入**：单份 **实际成本（1+2+3）**、配料行 **`actualUsage`**；  
- **不纳入利用率分子**：**`utilizationRate` 仅 type1 / `actualProduceUsage`**。

---

## 5. 旧版「理论 vs 实际」差异

| 维度 | 旧版实现要点 |
|------|----------------|
| 理论用量 vs 实际出库 | **`theoryOutboundQtyByRecipe` vs `outboundAllocatedQty`（type1）**；另 **`theoryQtyFromSales`** 与销售子表对照。 |
| 理论成本 vs 实际成本 | **`theoryCostAmount` vs `actualCostAmount`**；**`diffCostAmount`**。 |
| 差异金额 / 比例 | **`recipeSalesVsOutboundCostDiff`**、**`absDiffCostAmountSum`**、**`usageDeviationPercent`**（字段以接口为准）。 |
| 利用率 | **`utilizationRate = actualProduceUsage / theoryUsage`**（**仅生产进分子**）。 |
| 异常规则 | 排序 **`diff` / `actualCost`**；**`grossMarginLevel` vs T±F**；Skill 中「爆款 + 大偏差」「BELOW + 不低销量」等。 |

**旧版是否有「实际/理论比」**：配料分析用 **利用率** 与 **用量偏差** 等，**不是**简单的「全店同一原料上每菜 alloc1/理论相同 → 利用率相同」——见 **`gb-dish-cost-allocation-model.md` §6、§FAQ**。

---

## 6. 旧版单板 Agent 支持过哪些问题（已支持 / 疑似）

**已明确挂 Skill / 接口能力支撑的**：

- 哪道菜赚钱/亏钱、配料是否超、**分类毛利标准 / 红绿档**（`grossMarginLevel`、T±F）。  
- **列表**：高/低毛利、成本偏差 Top（`absDiffCostAmountSum` 等）。  
- **单菜**：理论/实际成本每份、配料谁的成本最高、瓶颈原料、偏差最大配料。

**与示例问法对齐（需数据齐全）**：

- 水煮鱼毛利、理论成本、实际成本、配料成本最高、哪道菜毛利最高/最低、成本异常、理论 vs 实际差、**本月/上月/门店切换**——旧 **HTTP** 由用户选筛选条件；**旧聊天**靠 `GbAiChatServiceImpl` 注入摘要；**新 Graph** 已部分支持 **「这个月菜品毛利」+ 多轮时间/门店**（见 `TODO_MULTI_AGENT`、`AI_HARNESS_REPLAY_CASES`）。

---

## 7. 旧版逻辑复用分类

### 7.1 可直接复用

- **`GbDepFoodBusinessInsightService#buildInsight`**：集团/门店合并菜品透视、与报表对齐的 **`blendedGrossMarginRateOnListPrice`**。  
- **`GbDishCostAnalysisService`**：`buildReport` / `ingredientAnalysis` / `dishIngredientDashboard` 全链路。  
- **`GbDepartmentGoodsStockReduceMapper.xml`**：type1 与 1+2+3 聚合 SQL（**不改口径前提下**）。  
- **`ai-skill-dish-cost-diagnosis.md`**：业务约束与**禁止心算毛利率**的条文可迁移为 Composer / 文档。  
- **DTO 字段语义**：`docs/gb-dish-cost-analysis-frontend.md`、`gb-dish-ingredient-dashboard-api.md`。

### 7.2 需改造后复用

- 旧聊天里 **写死月份/父部门** 的摘要 → 改为统一 **`timeWindow`、`orgScope`**。  
- **只认 `queryDepartmentIds` 展示门店** → 必须改为 **`visibleStores` 展示、queryIds 仅 SQL**（见 §8）。  
- **集团多轮范围继承**：旧 HTTP 无会话；需 **`AiConversationTurnMemory` + FollowUp** 对齐 `TODO` 已述规则。

### 7.3 不建议原样复用

- 过长、易被用户视为「开发味」的旧回答模板（若有）。  
- 历史文档中 **type2/type3 口头混称**（迁移到新文案：**废弃 / 损耗**）。  
- 将 **成本诊断** 与 **菜品毛利透视** 混在一条模糊路由（现规划已 **`dish_profit_path` 先于泛泛「毛利」**）。  
- 无 **Harness Debug**（`structuredIntentDetail` / `stockReduceType` 等）的旧调试方式。

---

## 8. 与新 Harness 对齐方案（仅方案，无代码）

接入时统一以 **`AiRunState.getResolvedQueryContext()`** 为准：

| 字段 | 用法 |
|------|------|
| **`timeWindow`** | `startDate` / `endDate` / 标签 → 传入 `buildInsight` / `buildReport`。 |
| **`orgScope.visibleStores`** | **用户可见门店列表、文案枚举**；**禁止**用 departmentIds 当「门店名列表」。 |
| **`dataScope.resolveSqlQueryDepartmentIds()`** / **`effectiveSqlDepartmentIds`** | **仅作为 SQL IN 条件**（与现 `ARG_RESOLVED_DEPARTMENT_IDS`、日营收展开策略一致）。 |
| **`visibleStoreRootIds`** | 门店根集合，与集团合并、菜谱合并口径对齐（见 `DOMAIN_ORG_MODEL.md`、`DishProfitAnalysisTool` 日志）。 |
| **`mentionedStore`** | 多轮点名门店追问时的显式店名（若有）。 |

**硬性产品规则**：**`queryDepartmentIds` 只用于查数**；**展示用 `visibleStores`**（与出库/经营链路一致）。

---

## 9. 建议的新 intent / path / `structuredIntentDetail`（仅建议）

> 以下为迁移迭代参考，**当前仓库可能已有** `DISH_PROFIT`、`PATH_DISH_PROFIT`；**不必重复造名**，优先与 `AiResolvedQueryIntent` 现常量对齐后再细分。

| 建议 | 说明 |
|------|------|
| `intent` | 维持或细化 **`DISH_PROFIT`** / **`DISH_PROFIT_ANALYSIS`**（与现代码一致则不改名）。 |
| `path` | 维持 **`dish_profit_path`**（或文档化等价名 **`dish_profit_analysis_path`**）。 |
| `structuredIntentDetail`（示例） | **`DISH_PROFIT_OVERVIEW`**（总览）；**`DISH_THEORETICAL_COST`**；**`DISH_ACTUAL_OUTBOUND_COST`**；**`DISH_COST_GAP`**；**`DISH_PROFIT_RANKING`**；**`DISH_COST_ABNORMAL_RANKING`**；**`DISH_SALES_RANKING`**；**`DISH_INGREDIENT_COST_BREAKDOWN`**。若旧版已有 wire 命名（如与 Tool id 对齐），**优先复用旧命名**。 |

---

## 10. 第一批建议迁移问法（最小集）

1. 这个月菜品毛利怎么样？  
2. 上个月呢？  
3. AAA 呢？ / 汀兰餐厅呢？（**门店/组织继承**）  
4. 水煮鱼理论成本是多少？  
5. 水煮鱼实际出库成本是多少？（**整单 1+2+3 单份**语义要说清）  
6. 水煮鱼毛利率是多少？（明确 **`blendedGrossMarginRateOnListPrice`** vs 理论）  
7. 哪个菜品毛利最低？  
8. 哪个菜品实际成本最高？  
9. 哪个菜品理论和实际差异最大？  

---

## 附录 A：旧版核心类速查

- **`GbDepFoodBusinessInsightServiceImpl`**：透视总装。  
- **`GbDishCostAnalysisServiceImpl`**：成本/分摊/报表。  
- **`GbDishCostAnalysisController`**：REST 入口。  
- **`ai-skill-dish-cost-diagnosis.md`**：Skill 约束。  
- **`DishProfitAnalysisTool` + `DishProfitAgentNode`**（新 Graph 已接旧服务）。

---

## 附录 B：复盘结论摘要（便于评审）

| 项 | 结论 |
|----|------|
| **理论成本口径** | 配方（`gb_distributer_food_goods`）× 销量 × 均价链 → **`theoryCostAmount`**；标价 **`listPriceRevenue`**。 |
| **实际出库成本口径** | **type1** 按 **`sumNeed` 分摊**；**type2+type3** 按 **type1 share** 摊；**综合实际毛利**用 **type1+2+3 总成本** vs 标价。 |
| **毛利率公式** | **理论毛利率** ≈ `(listPriceRevenue − theoryCost) / listPriceRevenue`；**综合实际毛利率**（权威）**`blendedGrossMarginRateOnListPrice`** = `(listPriceRevenue − actualCost123) / listPriceRevenue`（实现中为金额汇后计算，**禁止心算**）。 |
| **可直接复用** | **`buildInsight`、`GbDishCostAnalysisService`、StockReduce Mapper 聚合、现有 REST 文档与 Skill 约束。** |
| **接入 `AiResolvedQueryContext` 点** | **时间窗、visibleStores vs SQL ids、集团合并、多轮继承**（§8）。 |

---

*文档版本：与仓库当前代码、文档一致；后续改造请同步更新本节。*
