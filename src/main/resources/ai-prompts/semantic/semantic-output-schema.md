# Semantic Query Parser — V2 输出 JSON 契约

**用途：** 生产语义解析唯一 prompt 为 **`query_semantic_parser.v2.md`**（`semantic.query_parser.v2`）。本文定义 **LLM 须输出的 JSON 字段名、嵌套结构与枚举**，供 **`AiQuerySemanticParseResultJsonParser`** 解析。

**非 runtime 说明：**
- 不存在 v1 单字符串 user 入口；v2 未收养时走 clarification / frame validation，**不回退** v1 parser。
- LLM 输出的 **`time`** 块经 **`SemanticTimeContractCheck.reconcileTimePartForContract`** 仅在缺 **`startDate`/`endDate`** 时做有限补齐（`DEFAULT_MONTH_TO_DATE` / `INHERITED_PREVIOUS`），再 **`check`** 结构自洽；**PASS** 后写入 **`AiResolvedTimeWindow`** 与 **`effectiveTimeWindowSource`**；**FAIL** 进入 Resolver 澄清。Java **不**据 `timeType` 重算日期、**不**读 `time.reason` 或用户原文时间词。**Historical removed**：`AiMultiTurnTimeWindowPolicy#finalizeTimeWindow`。
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

`BUSINESS_OVERVIEW`, `REVENUE_OVERVIEW`, `PURCHASE_OVERVIEW`, `WAREHOUSE_STOCK_OVERVIEW`, `STOCK_REDUCE_QUERY`, `DISH_PROFIT`, `DISH_SALES_QUERY`, `DISH_COST_ANALYSIS`, `MENU_OPERATION`, `COST_DIAGNOSIS`, `BUSINESS_DIAGNOSIS`, …

域分工与互斥规则见 **v2 prompt** 各域专节（DISH_PROFIT vs COST_DIAGNOSIS、**MENU_OPERATION vs BUSINESS_OVERVIEW vs DISH_PROFIT/DISH_SALES**、库存现量 vs 出库核销、双域诊断等）。

---

## time 对象

| 键 | 说明 |
|----|------|
| `timeType` | TODAY, YESTERDAY, THIS_MONTH, LAST_MONTH, THIS_QUARTER, LAST_QUARTER, ROLLING_7, CUSTOM, … |
| `startDate`, `endDate` | **每轮必填** ISO 日期；由 LLM 从 **`currentUserMessage` 中的时间表达** 换算（Java 不解析用户原文） |
| `timeSource` | **`CURRENT_MESSAGE_EXPLICIT`** \| **`INHERITED_PREVIOUS`** \| **`DEFAULT_MONTH_TO_DATE`**（兼容别名 `CURRENT_MESSAGE` → 显式） |
| `needInheritFromPrevious` | true 表示声明沿用上一轮时间（须与 `timeSource=INHERITED_PREVIOUS` 一致） |
| `reason` | 简短说明（Harness 观测）；**须与 `timeSource` 一致，禁止自相矛盾** |

### timeSource / timeAction 选用（硬规则）

| 场景 | `timeSource` | 顶层 `timeAction` | 典型 `timeType` / 区间 |
|------|--------------|-------------------|------------------------|
| `currentUserMessage` **含明确时间词**（今天/昨天/本月/上个月/本季度/上个季度/本周/近7天/具体日期等） | **`CURRENT_MESSAGE_EXPLICIT`** | **`NEW` 或 `OVERRIDE`** | 与话术一致的 `timeType` + 对应 `startDate`/`endDate` |
| 本句**未提时间**，且有 `previousTurn` 可继承 | **`INHERITED_PREVIOUS`** | **`INHERIT_PREVIOUS`** | 与上一轮相同的起止日；`needInheritFromPrevious=true` |
| 本句**未提时间**，且**无可继承**（首轮） | **`DEFAULT_MONTH_TO_DATE`** | **`NEW`** | `THIS_MONTH`：月初 1 日～`today`；`needInheritFromPrevious=false` |

**本句同时含时间词与业务实体**（如「这个月烩菜卖得怎么样」「AAA 这个月哪个菜卖得最好」）：时间仍走第一行；**不得**因存在 `previousTurn`、跨域或 `intentAction`/`scopeAction=INHERIT_PREVIOUS` 而令 **`timeAction=INHERIT_PREVIOUS`**。

**`time.reason` 须与 `timeSource` 一致（观测字段，但禁止自相矛盾）：**

| `timeSource` | 允许的 `time.reason` 语义 |
|--------------|---------------------------|
| `DEFAULT_MONTH_TO_DATE` | 未指定时间；默认本月至今；default_month_to_date |
| `CURRENT_MESSAGE_EXPLICIT` | 本句明确时间词 / 指定区间（如「本句指定上个月」） |
| `INHERITED_PREVIOUS` | 本句未再提时间；沿用上一轮区间 |

**仅改时间的多轮接力（如「上个月呢」「上个季度呢」→ Intake 规范化为「上个月{对象}…」「上个季度{对象}…」）：**

- 视为 **`CURRENT_MESSAGE_EXPLICIT`** + **`timeAction=OVERRIDE`**（或 `NEW`），**不得** `INHERIT_PREVIOUS`。
- `timeType` 须与本句时间词一致（「上个月」→ `LAST_MONTH` / `PREVIOUS_MONTH`，完整自然月起止日；「上个季度」→ `LAST_QUARTER`，完整自然季起止日）。
- 业务槽位/合同/菜名可沿 `previousTurn` 继承，**时间窗必须按本句重算**，不得复制上一轮日期。

**季度 timeType 与起止日（须与输入 `today` 对齐，服务端 Java 按自然季边界校验）：**

| 用户话术 | `timeType` | `startDate` | `endDate` |
|----------|------------|-------------|-----------|
| 这个季度/本季度 | `THIS_QUARTER` | 当季 1 日 | `today`（非季末） |
| 上个季度/上季度 | `LAST_QUARTER` | 上季 1 日 | 上季末日 |

自然季：Q1=1–3月，Q2=4–6月，Q3=7–9月，Q4=10–12月。示例（`today=2026-05-26`）：`THIS_QUARTER` → `2026-04-01`～`2026-05-26`；`LAST_QUARTER` → `2026-01-01`～`2026-03-31`。

**禁止：** 把「上个季度」写成 rolling 3 个月或复制 Intake 中错误的「YYYY年M月～YYYY年M月」区间；`THIS_QUARTER` 的 `endDate` 不得为季末未来日。

**禁止：**

- 用户**未说任何时间词**时输出 **`CURRENT_MESSAGE_EXPLICIT`**（首轮「{菜名}成本怎么样」等须用 **`DEFAULT_MONTH_TO_DATE`**）。
- 用「默认今天 / 未指定时间 / 默认本月至今」兜底却标 **`CURRENT_MESSAGE_EXPLICIT`**（应使用 **`DEFAULT_MONTH_TO_DATE`** 或 **`INHERITED_PREVIOUS`**）。
- **`time.reason`** 写「未指定时间 / 默认本月至今」等与 **`CURRENT_MESSAGE_EXPLICIT`** 同时出现。
- **`CURRENT_MESSAGE_EXPLICIT`** 与 **`timeAction=INHERIT_PREVIOUS`** 同时出现。
- 本句含新时间词（本月/这个月/上个月等）却 **`timeAction=INHERIT_PREVIOUS`** 或 **`timeSource=INHERITED_PREVIOUS`**（即使跨域、即使 `intentAction`/`scopeAction` 为 `INHERIT_PREVIOUS`）。
- 本句含新时间词却 **`timeType`/`startDate`/`endDate` 仍等于上一轮**。

**服务端：** LLM JSON 经 **`reconcileTimePartForContract`** 仅在缺起止日时补齐（`DEFAULT_MONTH_TO_DATE` / `INHERITED_PREVIOUS`），再 **`check`**；**PASS** 写入 **`AiResolvedTimeWindow`**；**FAIL**（`timeSource`/`timeType`/日期/`timeAction` 结构性矛盾，如 `TIME_TYPE_DATE_MISMATCH`）→ **`needSemanticClarification`**，不查数。显式时间（`CURRENT_MESSAGE_EXPLICIT`）的 **`startDate`/`endDate` 必须由 V2 给出**，Java 不据 `timeType` 重算。

---

## requestedScope 对象

| 键 | 说明 |
|----|------|
| `requestedScopeType` | GROUP, STORE, REGION, DEPARTMENT, WAREHOUSE, PURCHASER, USER |
| `mentionedStoreName` | 单店口述名 |
| `mentionedStoreNames` | 多店对比时 string 数组；**单店也应用数组**（如 `["AAA"]`） |
| `mentionedDepartmentName`, `mentionedWarehouseName` | 短语，无 ID |
| `scopeSource` | 如 CURRENT_MESSAGE, INHERITED_PREVIOUS |
| `needInheritFromPrevious` | boolean |

**门店 scope 与 store 合同（硬规则）**：当选中 `dish_sales.store_count_ranking`、`dish_sales.store_single_dish`、`revenue.single_store_overview` 等**须点名门店**的 ACTIVE 合同时，`currentUserMessage` 含门店口述名 → **`mentionedStoreNames` 或 `mentionedStoreName` 必填**，`requestedScopeType=STORE`，`scopeSource=CURRENT_MESSAGE`。禁止只选 store 合同却不输出门店槽位。

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
| `structuredIntentDetailWire` | **可选 debug**：canonical 蛇形 wire；服务端 contract-locked 后以 `selectedContractId` 对应 entry 的 `wire` 为准，LLM 输出不一致不阻断主链 |
| `selectedContractId` | **P4-J2**：当 Step2 提供 `allowedContracts` 时必填；从 `allowedContracts[].contractId` 精确选取 |
| `answerPlanType` | 可选；须与所选 contract entry 一致 |
| `mentionedDishName` | string \| null；**requiresAnchor=DISH** 的单菜合同须填用户口述菜名（与顶层 `mentionedDishName` 二选一或并存） |
| `requestedTargetGrossMarginRate` | string \| null；用户**明确口述**目标毛利率百分比（如 `55` 表示 55%）；仅 `dish.profit.prescription.v1` 等定价处方合同使用；**禁止** Java 从原文 regex 解析 |

**DISH 锚点 / `mentionedDishName`（硬规则）**

| 场景 | 要求 |
|------|------|
| 所选 contract **`requiresAnchor=true` 且 `anchorType=DISH`** | **`mentionedDishName` 必填**（顶层和/或 `semanticSlots`，至少一处） |
| **`currentUserMessage` 含具体菜名** | 必须写入 `mentionedDishName`；**禁止**留空后依赖 Java 猜菜名 |
| 本句已含菜名 | **`anchorPolicy` 应为 `IGNORE_PREVIOUS_ANCHOR`**（或当前句锚点）；**禁止**无上轮 DISH 锚点时仍 `USE_PREVIOUS_ANCHOR` |
| 本句无菜名、承接上一轮同一道菜 | 可 `USE_PREVIOUS_ANCHOR` + 继承 `previousTurn.mentionedDishName` / resultAnchors |

示例：`currentUserMessage=烩菜卖得怎么样` → `selectedContractId=dish_sales.single_dish`，`mentionedDishName=烩菜`，`anchorPolicy=IGNORE_PREVIOUS_ANCHOR`。

**服务端协议搬运（非业务推断）**：若 LLM 将 `selectedContractId` / 槽位字段误放在 **JSON 顶层**（与 `semanticSlots` 同级），Parser 会 deterministic 搬入 `semanticSlots`；`semanticSlots` 内已有值优先。顶层 `mentionedDishName` 保留，并复制到 slots（若 slots 缺失）。

**库房现量（contract-first，`primaryDomain=WAREHOUSE`）：**

- **`allowedContracts`（ACTIVE，可选为 `selectedContractId`）**：`warehouse.overview`、`warehouse.goods_amount_ranking_high`、`warehouse.goods_amount_ranking_low`、`warehouse.store_amount_ranking`、`warehouse.single_store_overview`（wire 见各 entry）。
- **`warehouse.goods_amount_ranking_low`**：wire=`goods_stock_amount_ranking_low`，**仅**账面剩余库存**金额**升序排行；**禁止**用于库存偏少/报警/缺货/临期/补货问法。
- **`knownGapContracts`（禁止选为 selectedContractId）**：`warehouse.out_of_stock`、`warehouse.near_expiry`。
- **`plannedContracts`（禁止选为 selectedContractId）**：`warehouse.stock_replenishment_needed`、`warehouse.stock_overstock_risk`、`warehouse.store_stock_item_count_ranking`、`warehouse.warehouse_stock_item_count_ranking`。

**Lexicon 历史 wire（勿与 ACTIVE 混用；以 `allowedContracts` 为准）：**  
`warehouse_stock_overview`、`warehouse_stock_amount_ranking`、`goods_stock_amount_ranking_low`、`store_stock_amount_ranking` 等 — **禁止**在此 path 下输出出库域 `stock_reduce_*` wire。

**禁止**将 **`COMPARE_STORE`** 作为最终 `intent` 输出（已废弃；服务端 `mapLlmIntent` 不路由）。多店对比须直接输出上表业务域 + 完整 **`semanticSlots`**（见 v2「双店/多店对比」专节）。

**MenuOperation wire 白名单（`MENU_OPERATION` / `menu_operation_path`）：**  
`menu_operation_overview`, `menu_dish_high_sales_low_profit`, `menu_action_recommendation` — **禁止**在此 path 下输出 `dish_profit_*` / `dish_sales_*` wire；execution wire 以 **`selectedContractId` 对应 entry** 为准。

**菜品毛利 wire 白名单（`DISH_PROFIT` / `dish_profit_path`）：**  
`dish_profit_ranking_low_margin`, `dish_profit_ranking_high_margin`, `dish_profit_ranking_high_profit_amount`, `dish_profit_ranking_low_profit_amount`, `dish_gross_margin_query`, `dish_ingredient_cost_breakdown`, `dish_actual_cost_ranking_high`, `dish_actual_cost_ranking_low`, `dish_theoretical_cost_ranking_high`, `dish_theoretical_cost_ranking_low`, `dish_gap_ranking_max`, `dish_theoretical_cost`, `dish_actual_outbound_cost`, `dish_cost_gap`, `dish_low_profit_reason` — **禁止**在此 path 下填采购/出库 wire。

**菜品成本 wire 白名单（`DISH_COST_ANALYSIS` / `dish_cost_analysis_path`）：**  
`dish_cost_analysis`（合同 `dish_cost.single_dish_analysis`：单菜成本/配料/实际 vs 理论成本）；`dish_profit_prescription`（合同 `dish.profit.prescription.v1`：单菜定价/毛利处方/配方优化/建议售价）；`dish_ingredient_cover_days`（合同 `dish.ingredient_cover_days.v1`：单菜配料可支撑天数）。三合同**互斥**，由 `selectedContractId` 决定，**禁止** alias 到 `menu.dish.single_analysis.v1`。

**canonical wire 白名单** 与 **`AiQuerySemanticLexicon`** / v2 prompt「structuredIntentDetailWire 白名单」一致。

**采购槽位完整性：** 须给出 queryObject / operation / metric / sourceFacet / anchorPolicy；追问还须 detailWanted + wire。**禁止**仅用 `metric.rankingType` 代替 slots。

**菜品毛利槽位完整性（Phase 1 矩阵）：** 排行 / 单菜 / DISH 锚原料构成须输出完整 **`semanticSlots`** + **`structuredIntentDetailWire`**；**`metric.rankingType` 仅 debug**，服务端 **不以之写 wire**。缺 wire → **`MATRIX_WIRE_MISSING`**，strict harness 失败。

**双域采购↔出库风险 vs 采购商品明细（勿混 wire）：**

| 场景 | `intent` | `structuredIntentDetailWire` |
|------|----------|------------------------------|
| 采购多但出库少 / 买得多没怎么用 / **最近采购多但出库少** | `BUSINESS_DIAGNOSIS` | `purchase_stock_reduce_mismatch` |
| 采购了但没有核销 / 采购后长期未核销 | `BUSINESS_DIAGNOSIS` | `purchase_slow_moving_risk`（或对照句式用 `purchase_stock_reduce_mismatch`） |
| **退货金额/退货多少/退库**（出库 type4，非采购） | `STOCK_REDUCE_QUERY` | `return`（合同 `stock_reduce.return_overview`） |
| 商品**出库金额**排行（明确金额） | `STOCK_REDUCE_QUERY` | `goods_outbound_ranking` |
| 商品出库**数量/次数/用得最多/出库最多**（无金额词，P1） | `STOCK_REDUCE_QUERY` | **须** `needClarification`；对照 `knownGapContracts.stock_reduce.goods_count_ranking`；**禁止** `goods_amount_ranking` |
| 泛化**出库有没有异常**（P1） | `STOCK_REDUCE_QUERY` | **须** `needClarification`（禁止 `waste`/`overview` 凑合） |
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
| `DISH_SALES_QUERY` / `dish_sales_query_path`（语义 wire，非 Tool id） | 排行：`["dish_profit_analysis"]`；单菜合同 `dish_sales.single_dish` / `dish_sales.store_single_dish`：`["dish_sales_analysis_card"]` |
| `DISH_COST_ANALYSIS` / `dish_cost_analysis_path` | 成本卡 `dish_cost.single_dish_analysis`：`["dish_cost_analysis"]`；利润处方 `dish.profit.prescription.v1`：`["dish_profit_analysis","dish_cost_analysis"]`（顺序固定；服务端按 contract 取交集） |
| `MENU_OPERATION` / `menu_operation_path` | `["dish_profit_analysis"]` |
| `COST_DIAGNOSIS` / `cost_diagnosis_path` | `revenue_query`, `purchase_overview`, `stock_reduce_query`, `dish_profit_analysis`（四 Tool；毛利由服务端推导） |
| `BUSINESS_OVERVIEW` MULTI 四域 | 同上四 Tool |
| 采购+出库双域 `BUSINESS_DIAGNOSIS` 风险 | `purchase_overview`, `stock_reduce_query` |

**Historical removed（禁止输出）**：`purchase_query`, `stock_query`, `dish_sales_query`, `gross_margin_calculator`, `business_overview_query`, `purchase_anomaly_query` 等未注册 Tool id。

---

## 禁止出现在输出中的键

`queryStoreIds`, `queryRealDepartmentIds`, `expandedSqlDepartmentIds`, `storeToDepartmentIds`, `queryDistributerId`, `distributerId`, `departmentIds`，及任意数值型部门/门店 ID、SQL。

**禁止回显 User 输入结构**：`allowedOutputContract`, `allowedContracts`, `visibleStores`, `previousTurn`, `semanticRoute`, `currentUserMessage`, `today`（这些是 Parser **输入**键，不是 V2 **输出** schema）。

---

## 归档说明

历史单串 user 形态曾见 **`query_semantic_parser.v1.md`**（**D-CLEAN-V1 已从生产 prompt 目录删除**；Git 历史可检索，勿作字段契约来源）。
