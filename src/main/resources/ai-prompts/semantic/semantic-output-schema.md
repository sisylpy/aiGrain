# Semantic Query Parser — V2 输出 JSON 契约

**用途：** 生产语义解析唯一 prompt 为 **`query_semantic_parser.v2.md`**（`semantic.query_parser.v2`）。本文定义 **LLM 须输出的 JSON 字段名、嵌套结构与枚举**，供 **`AiQuerySemanticParseResultJsonParser`** 解析。

**非 runtime 说明：**
- 不存在 v1 单字符串 user 入口；v2 未收养时走 clarification / frame validation，**不回退** v1 parser。
- LLM 输出的 **`time`** 块（`startDate` / `endDate` / `timeSource` / `timeType`）经 **`SemanticTimeContractCheck`** 校验；**PASS** 后直接采用为 **`AiResolvedTimeWindow`** 与 **`effectiveTimeWindowSource`**；**FAIL** 进入 Resolver 澄清。Java 不对用户话术做时间词解析，**Historical removed**：`AiMultiTurnTimeWindowPolicy#finalizeTimeWindow`。
- **`structuredIntentDetailWire`**（canonical wire）与 **`semanticSlots`** 为结构化主语义；**`metric.rankingType`** 为 **deprecated / compat / debug** 观测字段，**不得**参与服务端主 wire、path 或 AnswerPlan（**D-1X-D3-RANKINGTYPE-FINAL** 已收口）。

**域内业务规则（采购矩阵、库存/出库、编排等）以 v2 prompt 专节为准；本文仅列通用字段与 D-13 槽位形状。**

---

## 契约治理 · Wire / semanticSlots 登记规则

下列规则适用于 **v2 Prompt、`semantic-output-schema.md`、Java 执行链、Matrix、AnswerPlan、Composer** 的协同维护；避免「文档写了、代码没接」或「Prompt 发明 wire、Java 不认识」的漂移。

### 主语义依据

| 层级 | 权威 |
|------|------|
| **主语义** | 顶层 **`semanticSlots`**（含 **`structuredIntentDetailWire`**、`queryObject` / `operation` / `metric` / `sourceFacet` / `anchorPolicy` / `detailWanted`）→ Lexicon canonical → `queryIntent.structuredIntentDetail` |
| **compat / debug only** | **`metric.rankingType`**、部分 **`metric.stockReduceType`** / **`metric.purchaseSourceType`**：**deprecated 观测字段**；**不得**参与服务端 wire 推断、path 路由或 AnswerPlan 主判断（见 [`docs/ai/semantic-allowed-output-contract-design.md`](../../../../docs/ai/semantic-allowed-output-contract-design.md)、[`docs/ai/semantic-contract-strict-mode-plan.md`](../../../../docs/ai/semantic-contract-strict-mode-plan.md)） |

### Prompt 不得发明未登记 wire

- **`query_semantic_parser.v2.md`** 与 LLM 输出中的 **`structuredIntentDetailWire` / `structuredIntentDetail`** 必须使用 **`AiQuerySemanticLexicon`** 中已有常量或本文档 / 各域 **`domain capability matrix / answer-plan docs`** 已列出的 canonical wire。
- **禁止**输出 Java Merge / Matrix / AnswerPlan **未登记** 的蛇形 wire；若产品需要新口径，先走下方登记清单，**再**改 Prompt 专节。

### 新 wire 进入生产前同步清单（7 步）

新增或变更一条 **生产 wire** 时，须在同一变更集或连续 PR 内对齐：

| # | 工件 | 说明 |
|---|------|------|
| 1 | **`AiQuerySemanticLexicon.java`** | 增加 `STRUCTURED_*` 常量；必要时补 canonical 别名映射 |
| 2 | **`semantic-output-schema.md`** | 域内白名单 / 枚举表增补（本文） |
| 3 | **对应 `docs/ai/domain capability matrix / answer-plan docs`** | 矩阵行：首轮 / 追问、`knownGap` 标注 |
| 4 | **对应 `*SemanticCapabilityMatrix.java`** | 可解析、可挂 AnswerPlan；`MATRIX_WIRE_MISSING` 行为明确 |
| 5 | **对应 `*AnswerPlan` / `*AnswerPlanBuilder`** | `planType` 与 wire 映射 |
| 6 | **Composer / `*DeterministicRenderer` / `StubAnswerComposerNode`** | 有 Plan 须有**专用宣读分支**；禁止仅 generic fallback |
| 7 | **Harness** | 新增或更新 replay case；若暂不实现须写 **`knownGap`** 与文档 **Planned/Gap**，**不得**让 Prompt 当作已支持能力输出 |

### Planned / Gap 与 Prompt 的关系

- 若 **schema 或 v2 专节已写**、但 **Matrix 行标 `knownGap` 或 Java 未挂 Plan**，该 wire 在 Prompt 中应标注为 **Planned/Gap** 或 **勿作为默认输出**，避免模型稳定产出「假闭环」JSON。
- Harness **strict** 失败（如 `MATRIX_WIRE_MISSING`）优于生产环境 silent 降级到错误话术。

### 相关索引（勿新建独立治理文件）

- 主链与契约索引：[`docs/ai/semantic-allowed-output-contract-design.md`](../../../../docs/ai/semantic-allowed-output-contract-design.md)  
- Plan-first / fallback：[`docs/ai/harness-composer-architecture.md`](../../../../docs/ai/harness-composer-architecture.md) §2.7  
- Strict 模式：[`docs/ai/semantic-contract-strict-mode-plan.md`](../../../../docs/ai/semantic-contract-strict-mode-plan.md)

---

## 顶层字段（必须输出）

| 字段 | 类型 | 说明 |
|------|------|------|
| `isFollowUp` | boolean | 接续上一轮同一话题的简短追问为 true |
| `intentAction` | NEW \| INHERIT_PREVIOUS \| OVERRIDE | 业务主线相对上一轮 |
| `timeAction` | NEW \| INHERIT_PREVIOUS \| OVERRIDE | 时间窗相对上一轮 |
| `scopeAction` | NEW \| INHERIT_PREVIOUS \| OVERRIDE | 组织/可见范围相对上一轮 |
| `metricAction` | NEW \| INHERIT_PREVIOUS \| OVERRIDE | 指标子口径相对上一轮 |
| `intent` | enum | 见下节 |
| `domain` | string \| null | 业务域标签；采购填 **PURCHASE** |
| `confidence` | number | 0.0～1.0 |
| `time` | object | 见「time 对象」 |
| `requestedScope` | object | 见「requestedScope 对象」 |
| `metric` | object | 见「metric 对象」 |
| `semanticSlots` | object \| null | 见「D-13 semanticSlots」；采购排行/总览等场景**必填** |
| `mentionedDishName` | string \| null | 用户口述单道菜名 |
| `needClarification` | boolean | 信息不足时为 true |
| `clarificationQuestion` | string \| null | |
| `reason` | string \| null | 不给 ID |
| `orchestrationDecisionCandidate` | object | v2 编排；键见 v2 prompt「OrchestrationDecision」专节 |

布尔为小写 `true`/`false`；日期为 **yyyy-MM-dd**。

### intent 枚举（节选）

`BUSINESS_OVERVIEW`, `REVENUE_OVERVIEW`, `PURCHASE_OVERVIEW`, `WAREHOUSE_STOCK_OVERVIEW`, `STOCK_REDUCE_QUERY`, `DISH_PROFIT`, `DISH_SALES_QUERY`, `COST_DIAGNOSIS`, `BUSINESS_DIAGNOSIS`, …

**禁止**将 **`COMPARE_STORE`** 作为最终 `intent` 输出（已废弃；服务端 `mapLlmIntent` 不路由）。多店对比须直接输出上表业务域 + 完整 **`semanticSlots`**（见 v2「双店/多店对比」专节）。

域分工与互斥规则见 **v2 prompt** 各域专节（DISH_PROFIT vs COST_DIAGNOSIS、库存现量 vs 出库核销、双域诊断等）。

---

## time 对象

| 键 | 说明 |
|----|------|
| `timeType` | TODAY, YESTERDAY, THIS_MONTH, LAST_MONTH, THIS_QUARTER, LAST_QUARTER, ROLLING_7, CUSTOM, … |
| `startDate`, `endDate` | **每轮必填** ISO 日期；LLM 负责从自然语言换算 |
| `timeSource` | **`CURRENT_MESSAGE_EXPLICIT`** \| **`INHERITED_PREVIOUS`** \| **`DEFAULT_MONTH_TO_DATE`**（兼容别名 `CURRENT_MESSAGE` → 显式） |
| `needInheritFromPrevious` | true 表示声明沿用上一轮时间（须与 `timeSource=INHERITED_PREVIOUS` 一致） |
| `reason` | 简短说明（Harness 观测） |

**服务端：** 合同通过时 **`AiResolvedQueryContext.timeWindow`** 与 **`effectiveTimeWindowSource`** 直接采用 LLM 输出；结构不一致时 **`needSemanticClarification`**，不查数。见 **`SemanticTimeContractCheck`**。

---

## requestedScope 对象

| 键 | 说明 |
|----|------|
| `requestedScopeType` | GROUP, STORE, REGION, DEPARTMENT, WAREHOUSE, PURCHASER, USER |
| `mentionedStoreName` | 单店口述名 |
| `mentionedStoreNames` | 多店对比时 string 数组 |
| `mentionedDepartmentName`, `mentionedWarehouseName` | 短语，无 ID |
| `scopeSource` | 如 CURRENT_MESSAGE, INHERITED_PREVIOUS |
| `needInheritFromPrevious` | boolean |

---

## metric 对象

| 键 | 说明 |
|----|------|
| `primaryMetric` | 如 revenue, purchase, business_status, profit_margin |
| `rankingType` | **deprecated / debug**：蛇形 wire 字面量；与 Lexicon STRUCTURED_* **可对齐作观测**；**服务端主链不读此字段推断 wire** |
| `purchaseSourceType` | ALL, SELF_PURCHASE, SUPPLIER_PURCHASE 等 |
| `stockReduceType` | 出库子类型 |

**`rankingType` 与 `semanticSlots.structuredIntentDetailWire`：** 主语义 **必须**由 **`semanticSlots`**（含 wire + queryObject / operation / metric / sourceFacet / anchorPolicy / detailWanted）表达；`rankingType` 仅 LLM/Harness **debug**，服务端 **不**以其补 wire。详见 v2 采购专节。

---

## D-13 semanticSlots（采购等结构化域）

顶层键 **`semanticSlots`**（勿省略键名）。与 **`previousTurn.semanticSlots`** 同形。

| 键 | 枚举 / 说明 |
|----|-------------|
| `queryObject` | GOODS, SUPPLIER, STORE, DISH, ORDER, UNKNOWN |
| `operation` | SUMMARY, OVERVIEW, RANKING, BREAKDOWN, DETAIL, TREND, COMPARE, DIAGNOSIS |
| `metric` | PURCHASE_AMOUNT, PURCHASE_COUNT, PURCHASE_QUANTITY, UNIT_PRICE, UNKNOWN |
| `sourceFacet` | ALL, SELF_PURCHASE, SUPPLIER_PURCHASE, UNKNOWN |
| `anchorPolicy` | USE_PREVIOUS_ANCHOR, IGNORE_PREVIOUS_ANCHOR, REQUIRE_CLARIFICATION |
| `detailWanted` | 追问明细：**SOURCE_BREAKDOWN**、**SUPPLIER_BREAKDOWN**、GOODS_DETAIL、GOODS_UNIT_PRICE、**SUPPLIER_UNIT_PRICE** 等；须与所选 contract entry 一致 |
| `structuredIntentDetailWire` | canonical 蛇形 wire；须与 `selectedContractId` 所属 entry 一致 |
| `selectedContractId` | **P4-J2**：当 Step2 提供 `allowedContracts` 时必填；从 `allowedContracts[].contractId` 精确选取 |
| `answerPlanType` | 可选；须与所选 contract entry 一致 |

**库房现量 wire 白名单（`WAREHOUSE_STOCK_OVERVIEW` / `warehouse_stock_overview_path`）：**  
`warehouse_stock_overview`, `warehouse_stock_low_risk`, `warehouse_stock_replenishment_needed`, `warehouse_stock_overstock_risk`, `store_stock_amount_ranking`, `store_stock_item_count_ranking`, `warehouse_stock_amount_ranking`, `warehouse_stock_item_count_ranking` — **禁止**在此 path 下输出出库域 `stock_reduce_*` wire。

**菜品毛利 wire 白名单（`DISH_PROFIT` / `dish_profit_path`）：**  
`dish_profit_ranking_low_margin`, `dish_profit_ranking_high_margin`, `dish_gross_margin_query`, `dish_ingredient_cost_breakdown`, `dish_actual_cost_ranking_high`, `dish_actual_cost_ranking_low`, `dish_theoretical_cost_ranking_high`, `dish_theoretical_cost_ranking_low`, `dish_gap_ranking_max`, `dish_theoretical_cost`, `dish_actual_outbound_cost`, `dish_cost_gap`, `dish_low_profit_reason` — **禁止**在此 path 下填采购/出库 wire。

**canonical wire 白名单** 与 **`AiQuerySemanticLexicon`** / v2 prompt「structuredIntentDetailWire 白名单」一致。

**采购槽位完整性：** 须给出 queryObject / operation / metric / sourceFacet / anchorPolicy；追问还须 detailWanted + wire。**禁止**仅用 `metric.rankingType` 代替 slots。

**菜品毛利槽位完整性（Phase 1 矩阵）：** 排行 / 单菜 / DISH 锚原料构成须输出完整 **`semanticSlots`** + **`structuredIntentDetailWire`**；**`metric.rankingType` 仅 debug**，服务端 **不以之写 wire**。缺 wire → **`MATRIX_WIRE_MISSING`**，strict harness 失败。

**双域采购↔出库风险 vs 采购商品明细（勿混 wire）：**

| 场景 | `intent` | `structuredIntentDetailWire` |
|------|----------|------------------------------|
| 采购多但出库少 / 买得多没怎么用 / **最近采购多但出库少** | `BUSINESS_DIAGNOSIS` | `purchase_stock_reduce_mismatch` |
| 采购了但没有核销 / 采购后长期未核销 | `BUSINESS_DIAGNOSIS` | `purchase_slow_moving_risk`（或对照句式用 `purchase_stock_reduce_mismatch`） |
| 供货商渠道**定了什么货** / 商品行明细 / 来源拆桶 | `PURCHASE_OVERVIEW` | `purchase_source_goods_query`（须 `detailWanted=GOODS_DETAIL` 等） |

**禁止**把「最近采购了但没有核销的商品有哪些？」落成 **`purchase_source_goods_query`**。

**仅改时间的接力：** 当本句只含明确新时间词，须从 **`previousTurn.semanticSlots`** 逐字段继承，**禁止** `semanticSlots: null` 或 `{}`。

---

## orchestrationDecisionCandidate.selectedTools（现网 Tool 白名单）

| `intent` / 有效路径语义 | `selectedTools`（仅此表内 id） |
|-------------------------|--------------------------------|
| `REVENUE_OVERVIEW` / `revenue_overview_path` | `["revenue_query"]` |
| `PURCHASE_OVERVIEW` / `purchase_overview_path` | `["purchase_overview"]` |
| `WAREHOUSE_STOCK_OVERVIEW` / `warehouse_stock_overview_path` | `["warehouse_stock_overview"]` |
| `STOCK_REDUCE_QUERY` / `stock_reduce_query_path` | `["stock_reduce_query"]` |
| `DISH_PROFIT` / `dish_profit_path` | `["dish_profit_analysis"]` |
| `DISH_SALES_QUERY` / `dish_sales_query_path`（语义 wire，非 Tool id） | `["dish_profit_analysis"]` |
| `COST_DIAGNOSIS` / `cost_diagnosis_path` | `revenue_query`, `purchase_overview`, `stock_reduce_query`, `dish_profit_analysis`（四 Tool；毛利由服务端推导） |
| `BUSINESS_OVERVIEW` MULTI 四域 | 同上四 Tool |
| 采购+出库双域 `BUSINESS_DIAGNOSIS` 风险 | `purchase_overview`, `stock_reduce_query` |

**Historical removed（禁止输出）**：`purchase_query`, `stock_query`, `dish_sales_query`, `gross_margin_calculator`, `business_overview_query`, `purchase_anomaly_query` 等未注册 Tool id。

---

## 禁止出现在输出中的键

`queryStoreIds`, `queryRealDepartmentIds`, `expandedSqlDepartmentIds`, `storeToDepartmentIds`, `queryDistributerId`, `distributerId`, `departmentIds`，及任意数值型部门/门店 ID、SQL。

---

## 归档说明

历史单串 user 形态曾见 **`query_semantic_parser.v1.md`**（**D-CLEAN-V1 已从生产 prompt 目录删除**；Git 历史可检索，勿作字段契约来源）。
