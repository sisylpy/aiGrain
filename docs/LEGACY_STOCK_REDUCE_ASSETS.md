# 旧版出库 / 核销 / 成本消耗业务资产盘点（对齐多智能体 Harness）

> **阶段说明**：本文仅做代码与口径盘点、与 `AiResolvedQueryContext` 的对齐说明及迁移建议；**不包含**当期 Tool / Graph / 前端改动清单的执行项。确认后再进入接入开发。

---

## 1. 权威数据源与类型枚举

### 1.1 主表

| 资源 | 说明 |
|------|------|
| 表 `gb_department_goods_stock_reduce` | 部门商品库存扣减（核销流水） |
| 核心字段 | `gb_dgsr_subtotal`（金额）、`gb_dgsr_weight`、`gb_dgsr_type`、`gb_dgsr_date`、`gb_dgsr_gb_department_id`、`gb_dgsr_gb_department_father_id`、`gb_dgsr_gb_distributer_id`、`gb_dgsr_gb_dis_goods_id` 等 |

### 1.2 类型与语义（代码真值）

定义见 `GbConstants.StockReduceType` 与 Mapper 中的 `CASE WHEN gb_dgsr_type = …`：

| type | 常量 | 业务含义（技术） |
|------|------|------------------|
| 1 | `PRODUCTION` | 生产/生产成本扣库（菜品成本分析、按菜分摊的「生产」出库**仅取 type1**） |
| 2 | `WASTE` | 废弃（文档/界面常表述为「损耗」类出库之一） |
| 3 | `LOSS` | 损失（文档/界面常表述为「报损」） |
| 4 | `RETURN` | 退货 |
| 5 | `STARS` | 其它（一般不进入常规四类汇总 SQL，需按需核对） |

**重要：「出品」vs「生产耗用」**

- **数据库侧没有**与 type1 并列的独立「出品」类型。
- **库房工具文案**将 type1 金额称为「核销侧**出品**」（见 `WarehouseStockOverviewTool` 汇总模板：`核销侧出品约 %.2f 元，损耗 … 报损 … 退货 …`），与 `produceTotal` / `produceAmount` 同源。
- **`ai-skill-cost.md`** 将 type1 写成「生产/成本（PRODUCTION）」。
- **结论**：旧版成熟能力是建立在 **四类 type（1～4）** 上的；若产品要坚持「生产耗用金额」与「出品金额」**两行互不重复**的数字，需要先定业务拆分规则（或通过别的表/字段），**不能**仅依赖当前四类汇总再拆出第五个独立台账。

---

## 2. 旧版出库 / 核销相关代码位置

### 2.1 Service（聚合与业务）

| 类 | 作用 |
|----|------|
| `GbDepartmentGoodsStockReduceService` / `GbDepartmentGoodsStockReduceServiceImpl` | 核销统计总入口：`queryReduceAllTypesTotal`、`queryReduceAllTypesTotalOnDailyRevenueDays`、按 type 汇总、Top 商品、按商品分摊列表等 |
| `GbDepartmentGoodsStockReduceCostQueryService` / `Impl` | **商品成本汇总与分页**：`buildGoodsCostStatistics`（汇总 1+2+3，`allTotal` **不含退货**）、`buildGoodsCostPage`（按 cost/sales/loss/waste 排序维度） |
| `GbDepartmentGoodsStockReduceWithDayDataService` | 与日维度结合的 reduce 展示（Controller `getGoodsReduceWithDayData`） |
| `GbDepartmentGoodsStockReducePurFenxiService` | 采购与出库对齐分析（`getGbPurGoodsFenxi`） |
| `GbDepartmentGoodsStockLedgerServiceImpl` | 入库后记账、`produce` / `loss` / `waste` / `return` 写核销实体 |
| `GbAiDailyRevenueDashboardServiceImpl` | 经营看板 `buildStatsDashboard`：**生产成本、损耗成本、损失成本、退货成本、制作成本合计**等中文键 Flatten，核销使用 `queryReduceAllTypesTotalOnDailyRevenueDays` |
| `GbDishCostAnalysisServiceImpl` | **菜品成本 / 出库分析**：`scopeOutboundSubtotals`（type1/2/3 金额、损耗率 (2+3)/(1+2+3)×100）；按菜分摊以 **type1** 为主，type2/3 按比例摊（见 `docs/gb-dish-cost-allocation-model.md`） |
| `GbDepFoodBusinessInsightServiceImpl` | 经营透视：标价收入 vs **type1+2+3 出库**等综合毛利率口径 |
| `GbAiChatServiceImpl` | **单板 Agent**：构造事实块、集团门店毛利块、出库字段说明（如 `gb_dgsr_stock_nx_supplier_id` 与供货商关系）、技能路由提示 |

### 2.2 Mapper（SQL 真源）

| 资源 | 说明 |
|------|------|
| `GbDepartmentGoodsStockReduceMapper.java` + `GbDepartmentGoodsStockReduceMapper.xml` | `queryReduceAllTypesTotal`、`queryReduceAllTypesTotalOnDailyRevenueDays`（**仅统计存在日营收记录的自然日**）、`queryReduceAllTypesTotalForRetailDepartmentFathers`（**多门店父部门 in**，且 join `gb_department` 限制 `department_type in (1,11)`）、按父门店分组的 grouped 汇总、`queryReduceAggByDisGoodsByType`、`queryStockSubtotalTopTimes` 等 |

### 2.3 REST / Controller

| 入口 | 说明 |
|------|------|
| `GbDepartmentGoodsStockReduceController` | `/getGbGoodsCostStatistics`、`/getGoodsCostBySearchDate`、`getGoodsReduceWithDayData`、`getGbPurGoodsFenxi`、`deleteReduceItem`；注释指向 legacy 完整实现文件 |
| `GbDistributerPurchaseGoodsController` | 套餐内按需调用 `queryReduceTypeCount`、`queryReduceAllTypesTotal`、`queryStockSubtotalTopTimes`（**按 type 拆分 Top 商品**）等 |
| `GbDishCostAnalysisController` | `/report`、`/ingredientAnalysis` 等，出库金额排序、损耗率与同表同源 |
| `GbAiDailyRevenueController` | 文档声明：`departmentId` 为 **父部门/餐厅 ID**，与日营收 `department_id`、核销 **`gb_dgsr_gb_department_father_id`** 一致 |

### 2.4 工具类与实体

| 资源 | 说明 |
|------|------|
| `GbDepartmentGoodsStockReduceSupport` | `buildReduceCostQueryMap`（`disId`、日期、`depId` **或** `depType=门店类型`）、金额解析 |
| `GbDepartmentGoodsStockReduceEntity` | ORM 实体 |
| `entity/GbDepartmentEntity` | 历史上有 `wasteReduceList` 等关联字段 |

### 2.5 单板 Agent 文档与 Skill

| 资源 | 说明 |
|------|------|
| `src/main/resources/ai-skill-cost.md` | 成本总控：固定成本三件套的苏格拉底规则、**`gb_department_goods_stock_reduce`** type1～4 含义、稀疏数据禁忌、篇幅限制 |
| `docs/LEGACY_AI_ANSWER_ASSETS.md` | 旧链路中枢 `GbAiChatServiceImpl`、看板、`StockReduceQueryTool` 与 Cost 链对齐说明 |
| `docs/gb-dish-cost-allocation-model.md` | **出库分摊模型**（W_g type1，type2/3 分摊规则） |
| `docs/gb-dep-get-all-food-business-frontend.md` | `scopeOutboundSubtotals` 与顶层损耗率口径 |
| `docs/AI_QUERY_SEMANTIC_LEXICON.md` | 核销/出库词条 **占位**：后续词典细化 |
| `docs/gb-chain-org-and-ai-scope.md` | 集团估算毛利率：净营收 − **四类出库金额之和** |

### 2.6 当前 Harness 已接入点（对齐用，非本期改造）

| 资源 | 说明 |
|------|------|
| `StockReduceQueryTool` | `stock_reduce_query`：当前主要调 `queryReduceAllTypesTotalOnDailyRevenueDays`，**单体 father + matchDailyRevenueDepartmentId**，与旧看板核销口径一件事 |
| `WarehouseStockOverviewTool` | `warehouse_stock_overview_path`：集团/门店汇总文案里**已含**「出品/损耗/报损/退货」四段金额，且已接 `AiResolvedQueryContext` 可见门店等 |
| `CostDiagnosisAgentNode` | 消费 Tool 汇总：生产相关合计、损耗占比、数据不足 **`data_incomplete`**、建议话术 |
| `GrossMarginCalculatorTool` | 与「核销全 0」时的可靠性保护（见 `TODO_MULTI_AGENT` / 文档） |

---

## 3. 旧版指标口径（怎么统计）

### 3.1 生产 / 「出品」（type1）

- **金额**：对区间内 `gb_dgsr_type=1` 的 `gb_dgsr_subtotal` 求和（或 ROUND 后与 Mapper 一致）。
- **使用场景**：经营看板「生产成本」「生产核销日均」；菜品理论/实际成本中 **按菜分摊的均价与重量主要基于 type1**（`GbDishCostAnalysisServiceImpl`）。
- **产品话术**：库房链路称「核销侧出品」= **produce 合计**。

### 3.2 损耗（type2）

- **金额**：`type=2` 的 subtotal 求和。
- **损耗率（常见）**：在 `scopeOutboundSubtotals` 等处为 **`(type2+type3)/(type1+type2+type3)×100%`**（**不含退货 type4**，见 Controller / 报表注释）。

### 3.3 报损（type3）

- **金额**：`type=3` subtotal 求和；与 type2 一起进入「损耗废弃」类表述（看板：`损耗成本`、`损失成本`、`损耗废弃合计`）。

### 3.4 退货（type4）

- **金额**：`type=4` subtotal 求和；看板单独「退货成本」；**制作成本合计**在 Dashboard 实现里为 type1+2+3，**总成本**可再含退货（见 `GbAiDailyRevenueDashboardServiceImpl` 中 `productionCost` / `totalCost`）。

### 3.5 总出库 / 总核销（老板问「出库多少钱」时的技术候选）

| 口径 | 计算 | 典型用途 |
|------|------|----------|
| **1+2+3** | 不含退货 | 菜品侧「出库结构」、损耗率分母、部分毛利估算分子 |
| **1+2+3+4** | 含退货 | 集团诊断块里「出库成本小计」、与净营收做粗毛利（见 `GbAiChatServiceImpl` 及 `gb-chain-org-and-ai-scope`） |
| **仅「有日营收日」** | `queryReduceAllTypesTotalOnDailyRevenueDays` | **单店经营看板**与当前 `StockReduceQueryTool` 默认路径，避免无营业日记账的噪声日 |
| **多店父部门** | `queryReduceAllTypesTotalForRetailDepartmentFathers` | **集团多门店** reduce 汇总（已在 `GbAiChatServiceImpl` 等使用）；**不**等同「日营收日」过滤，需与产品确认是否要与单店看板统一 |

### 3.6 维度能力（是否已有旧逻辑）

| 维度 | 旧能力 |
|------|--------|
| **商品** | `queryStockSubtotalTopTimes`、`queryReduceAggByDisGoodsByType`、`queryGoodsCostGoodsPageWithDetails`、菜品报表 `outboundQty` / sortBy 出库金额 |
| **门店** | `departmentFatherId`、`depId`、`depType`（门店类型）、集团 `departmentFatherIds` 列表；按父门店 **group** 的 Mapper |
| **部门（子部门）** | `searchDepId` → `depId` 精确到子部门（商品成本 API） |
| **供货商 / 批次** | 单板事实块强调 `gb_dgsr_stock_nx_supplier_id`（来自入库批次）；**完整「按供货商聚合 Top」**依赖更多组装，Skill 中提示「仅 ID 维度」 |
| **菜品关联** | 通过 `GbDishCostAnalysisService` / `GbDepFoodBusinessInsightService` 分摊，不是 reduce 单表直接 group by 菜 |

### 3.7 排行类

- **商品维度**：`queryStockSubtotalTopTimes`（可带 type）；`GbDistributerPurchaseGoodsController` 对 produce/loss/waste **分别 Top**。
- **出库次数**：`queryReduceTypeCount`、明细条数语义；是否与「排行」合一需看 Mapper 定义。
- **重量**：多条 `queryReduce*WeightTotal` /  agg 中带 `weightSum`。

---

## 4. 旧版单板 Agent 回答风格（出库 / 成本 / 核销）

依据 `ai-skill-cost.md`、`GbAiChatServiceImpl` 注入模板、`AiCostDiagnosisResult` / `CostDiagnosisAgentNode`：

| 特点 | 说明 |
|------|------|
| **先数字后观点** | 成本 Skill 要求先照抄注入块已有数字（营收、核销行数、type1 金额等），再分析及提问 |
| **固定成本门禁** | 月租、月工资、其它固定开支不全时：**不做完整利润表**、不展开定量利润结论 |
| **数据稀疏** | 流水天数过少、核销 0 行但语境应有数 → **苏格拉底澄清**，禁止危言耸听 |
| **老板侧重点** | 四类（或 1+2+3）金额与结构、与日营收对照、入库—核销链路是否连续 |
| **异常与建议** | 采购有但核销偏低、损耗+废弃占比高、毛利率不可靠（数据不足）；建议核对入库/核销明细、班次档口拆解等（新链路 `CostDiagnosisAgentNode` 已继承类似判断） |
| **权限 / 范围** | 旧注入按「展开的部门节点」列表控制查询范围（Chat 拼装）；新店/父子部门二选一归属在 Skill 文案中有说明。**集团 / 门店**在 `GbAiChatServiceImpl` 有专门的集团诊断段落（净营收 − 出库四类和） |

---

## 5. 与当前 Harness 公共上下文对齐（必选字段）

新Dedicated 出库链路设计时应**只读**：

| 字段 | 用途 |
|------|------|
| `state.getResolvedQueryContext()` | 单一入口 |
| `orgScope.visibleStores` | **展示**：门店根名称列表（AAA、汀兰餐厅等） |
| `dataScope.resolveStoreRootDepartmentIds()` / `visibleStoreRootIds` | **门店根 ID** 列表，用于多店 IN `father_id` 类 SQL |
| `dataScope.resolveSqlQueryDepartmentIds()` / `effectiveSqlDepartmentIds` | **实际 SQL 记账/查询部门**（可含子部门），与展示分离 |
| `timeWindow.startDate` / `endDate` | 与工具 `startDate`/`stopDate` 一致 |
| `queryIntent` / `effectivePathCode` | 意图与 path，避免与采购/库存/经营混链 |

**必须遵守的产品规则**（与现有文档一致）：

- **展示**用门店根；**查询**可展开子部门，但字段命名要与「门店列表」区分。
- **`queryDepartmentIds` / sqlQueryDepartmentIds** 不得直接当「门店名称列表」展示给用户。

**集团 / 门店 / 库房 / 采购员（查数策略摘要）**

| 角色语境 | 建议对齐的旧 SQL 能力 |
|----------|----------------------|
| **集团（多门店根）** | `queryReduceAllTypesTotalForRetailDepartmentFathers(departmentFatherIds + disId + 区间)`；与「仅日营收日」口径差异需在回答或 debug 标明 |
| **单店（门店根）** | `departmentFatherId` + `queryReduceAllTypesTotalOnDailyRevenueDays`（与看板、`StockReduceQueryTool` 一致） |
| **点名子部门** | 参考 `GbDepartmentGoodsStockReduceSupport.buildReduceCostQueryMap` 的 **`depId`** 分支而非仅 `depType` |
| **库房** | `WarehouseStockOverviewTool` 已合并多维度库存+核销；与「纯出库问答」可选用同一 reduce 数据源但入口不同 |
| **采购员** | 旧链常收敛为采购+核销视角（`PERMISSION_MODEL` / `CostInsightIntentConvergence`）；出库专线需单独避免被采购话术误吸 |

---

## 6. Harness 当前缺口（相对旧版成熟度）

| 缺口 | 说明 |
|------|------|
| **Intent / Path** | 无独立 **`stock_reduce_overview_path`**（或等价）稳定路由时，「出库多少钱」易被 `looksLikeCostInsight`（含出库/核销）吃进成本主链而非专用 Tool |
| **StockReduceQueryTool** | **未**对齐 `AiResolvedQueryContext` 的集团 `visibleStores` / 多 father 聚合；**未**调用 `queryReduceAllTypesTotalForRetailDepartmentFathers`（接口层 Service 甚至可能尚未封装） |
| **结构化子意图** | 词典中对出库分型、排行、overview 仍为占位（见 `AI_QUERY_SEMANTIC_LEXICON`） |
| **多轮** | `FollowUpPathKind` / 追问快照尚无出库专线，时间问题、换店、`全部门店呢` 等需与采购链同等对待 |
| **调试面板** | 需在 summarizer 中显式暴露出库结构化口径（等价 `structuredIntentDetail` 或别名键） |
| **「出品」单列** | 旧数据只有 type1；若 UI 要写五行（生产耗用、出品、…）需产品定义或接纳「出品≡type1 展示名」 |

---

## 7. 建议迁移方案（供下一阶段开发，本文不执行）

1. **复用优先级**  
   - **金额_truth**：继续以 `GbDepartmentGoodsStockReduceService` + 现有 Mapper 为唯一真相；禁止重复造 SQL。  
   - **单店对齐看板**：`queryReduceAllTypesTotalOnDailyRevenueDays`。  
   - **集团多店**：`queryReduceAllTypesTotalForRetailDepartmentFathers`，并在 UX/debug 标明是否与「日营收日」筛选一致。

2. **Tool 演进**  
   - 扩展 `StockReduceQueryTool`（或拆子 Tool）入参：`groupAggregation`、`departmentFatherIds`（来自 **`visibleStores`/`store roots`**，不是把 `sqlQueryDepartmentIds` 当店名）、`disId`、`narrativeMode`（overview / 单 type / ranking）。  
   - 排行：优先复用 `queryReduceAggByDisGoodsByType` 多 type 合并或 `queryStockSubtotalTopTimes`，**部门条件**必须与 `dataScope` 一致。

3. **Graph**  
   - `BusinessDataPlannerNode`：在成本链之前识别**专用出库问法**（与 `WarehouseStockOverviewTool` 的「库存怎么样」分流）。  
   - `BusinessToolExecutionNode`：为 `stock_reduce_query` 注入与 `purchase_overview` 同级的 **scope banner + visibleStores**（展示），SQL 用 `dataScope`。

4. **文档 / 词典**  
   - 更新 `AI_QUERY_SEMANTIC_LEXICON` 出库段；与 `ai-skill-cost.md` type 命名对齐，并说明「出品」= 产品对 type1 的称呼。

---

## 8. 需要避免的坑

1. **两套「总出库」**：1+2+3 与 1+2+3+4、以及「仅日营收日」vs「全日历区间」——回答前必须锁定一种并在 debug 可视。  
2. **父子部门**：只挂子部门不挂 father 时的覆盖方式，Skill 与 `buildReduceCostQueryMap` 已提示，新链也要一致。  
3. **损耗率分母**：文档与报表常为 **不含退货** 的 1+2+3；老板口头「出库」可能含退货——需话术或结构化字段区分。  
4. **菜品毛利 vs 出库专线**：毛利与理论量用的是 **type1 为主 + type2/3 分摊规则**；简问「出库多少钱」不必走全量 `GbDishCostAnalysisService`。  
5. **集团经营看板**：收入已有多店 rollup，**核销/利润率**在 `TODO_MULTI_AGENT` 中仍部分为「不适用」——新出库链不要默认复用 `business_overview_query` 的未完成部分而不自知。  
6. **queryDepartmentIds**：仅作 SQL 展开列表，**禁止**在文案中假装成「门店清单」。

---

## 9. 验收对照（读本文后应能回答）

| 问题 | 答案要点 |
|------|----------|
| 旧单板有哪些成熟能力？ | 四类 type 汇总、日营收日过滤、多店 father 汇总、商品 Top/分页、菜品分摊与损耗率、经营看板中文指标、Chat 事实块 + cost skill 规则 |
| Harness 缺什么？ | 专用 path/多轮/集团 reduce SQL 接入 resolved context、排行与结构化意图、与 cost 链抢意图的优先级 |
| 下一步先动哪？ | 以 **`StockReduceQueryTool` + `BusinessDataPlannerNode` + `BusinessToolExecutionNode`** 为主，Service 层补齐 `ForRetailDepartmentFathers` 封装（若尚未暴露） |
| 集团/门店/库房/采购员怎么查？ | 见 **§5** 表格：多 father、单 father+日营收日、库房走 `WarehouseStockOverviewTool`、采购员注意意图收敛与权限 |

---

## 10. 参考索引（快速跳转）

- `GbConstants.StockReduceType`  
- `GbDepartmentGoodsStockReduceMapper.xml`（`queryReduceAllTypesTotal*`、`queryReduceAggByDisGoodsByType`、`queryStockSubtotalTopTimes`）  
- `GbAiDailyRevenueDashboardServiceImpl#buildStatsDashboard`  
- `GbDepartmentGoodsStockReduceCostQueryServiceImpl`  
- `GbDishCostAnalysisController`、`docs/gb-dish-cost-allocation-model.md`  
- `WarehouseStockOverviewTool`（集团汇总 produce/waste/loss/return）  
- `GbAiChatServiceImpl`（集团诊断、事实块、`queryReduceAllTypesTotalForRetailDepartmentFathers`）  
- `docs/LEGACY_AI_ANSWER_ASSETS.md`、`src/main/resources/ai-skill-cost.md`
