# D-4 Purchase Batch 4：双域风险 AnswerPlan / 诚实降级设计

本文档仅记录设计与分阶段落地建议，**不包含实现代码**，不替代既有 `AiQuerySemanticLexicon`、Prompt、MergeHelper 等契约文档。

---

## 1. 背景

- **Batch 1**：基础采购路由与专线能力已收口，采购概览与 `purchase_overview` 工具链对齐。
- **Batch 2**：采购异常子口径（单价/频次/数量/突增等）语义契约与 Lexicon 封闭 wire 已收口，`metric.rankingType` 与 `PurchaseAnswerPlan` 子类型可对齐。
- **Batch 3**：**采购 + 出库 / 库存风险**双域语义已收口：`effectiveIntentCode = BUSINESS_DIAGNOSIS`、`effectivePathCode = business_diagnosis_path`、编排上期望 `PurchaseAgent` + `StockReduceAgent` 与 `purchase_overview` + `stock_reduce_query`，四条封闭 wire（`purchase_stock_reduce_mismatch` 等）已写入 Prompt 与 Lexicon。
- **Batch 4（本文）**：语义已能稳定落到诊断 path，但 **PRIMARY 执行链**上的 **AnswerPlan 承载、Tool 参数贯通、终稿 Composer** 与 **数据诚实边界** 仍需单独设计，避免 LLM 在缺失证据时编造跨域结论。

---

## 2. 当前链路事实（评审结论摘要）

以下内容来自对现有 Java 链路的只读梳理，作为 Batch 4 设计前提。

- **语义侧**：`BUSINESS_DIAGNOSIS` + 期望 **Purchase + StockReduce** 双域编排已可由解析层稳定输出（Batch 3）。
- **规划侧**：`BusinessDataPlannerNode` 在 `business_diagnosis_path` 上 **`applyBusinessDiagnosisBranch` 按权限扩展工具**，并不根据 `selectedAgents` 裁剪为「仅两工具」；权限允许时 **`dish_profit_analysis`、`revenue_query` 仍可能进入 `dataPlanTools`**。
- **执行侧**：`BusinessToolExecutionNode` 按 **`dataPlanTools`** 顺序执行；`state.getToolResults()` 的 key 至少包括 **`purchase_overview`**、**`stock_reduce_query`**，权限允许时还可能包括 **`revenue_query`**、**`dish_profit_analysis`**。
- **Tool 参数侧（与 Batch 3 wire 的间隙）**：经营诊断 path 上，采购与出库 Tool 的 narrative 参数在现有实现中仍可能被固化为 **overview/summary 类模式**，**Batch 3 四条风险 wire 尚未贯通到 Tool 参数层**；即 Resolved 上的 `structuredIntentDetail` 与 Tool 入参可能不一致。
- **终稿侧**：在常见账号（含 `stockReduceQueryPath` 与诊断 path 同时置位）下，`finalAnswer` **多由 `StubAnswerComposerNode` + LLM + `BusinessDiagnosisPlan` 载荷**生成；`DiagnosisPlan` 的确定性宣读路径可能因 **`shouldPreferDiagnosisPlanInComposer` 对 `stockReduceQueryPath` 的排除**而不优先。

### 2.1 Phase 1 数据可行性评审（只读梳理结论）

基于当前 **`purchase_overview` + `stock_reduce_query`** 的 tool 负载与既有 AnswerPlan 衍生物，**不足以稳定构建「商品级、可核对」的 `PurchaseRiskAnswerPlan`**。结论如下；**Phase 1 必须按本节降级**，不得隐含「数据已齐备」。

| 维度 | 结论 |
|------|------|
| **采购侧** | 存在商品 Top 列表（金额/频次/单价排名等），但 **缺少稳定 `goodsId`**；排名 map 中 **无可靠单独的采购数量、单位、均价列**（金额以 `purchaseSubtotal` 等形式存在；`purchaseTimes` 等指标语义与「件数」不对齐）；**无 `lastPurchaseDate`**。 |
| **出库侧** | 在 **经营诊断 + overview / summary 类 narrative** 下，**经常只有类型汇总与合计**，**不保证**存在可与采购侧逐行对齐的 **商品级 rows**；商品 Top 仅在特定 narrative（如结构化商品出库排名）下才填充。 |
| **Join** | 两边 **不能稳定按 `goodsId` join**；出库金额 Top 行常无 id，采购侧无 id，**最多只能按 `goodsName` ↔ `name` 弱匹配**，同名不同 SKU、别名均会误配。**Phase 1 不做强 join**。 |
| **库存与效期** | 当前双 tool **不提供** **`lastStockReduceDate`**、**库存快照**、**批次库存**、**保鲜期/效期**；无法在现有数据上断言真实积压或快过期。 |

**Phase 1 因此不能承诺（不得在 summary / risk 表述中当作已证实结论输出）：**

- 商品级 **采购 − 出库** 差额；
- **出库占采购** 比例（单品或可靠对齐维度）；
- **长期未出库天数**；
- **真实库存积压**；
- **快过期 / 临期** 判断。

**Phase 1 应交付的形态**见第 4.5 节「诚实降级版风险提示」。

---

## 3. 为什么现有 Plan 不够

| 载体 | 问题（相对双域采购风险） |
|------|---------------------------|
| **BusinessDiagnosisCompositeAnswerPlan** | 面向 **四域 Composite**（营收 / 采购 / 出库 / 菜品毛利骨架），与 Gate、Harness/Shadow 观测链路强相关，**不适合**作为 PRIMARY 上「仅采购 + 出库」的轻量一等对象。 |
| **PurchaseAnswerPlan** | **单域采购**；`resolvePlanType` 未识别 Batch 3 四条双域 risk wire，多回落为泛化 overview，**无法表达「采购 vs 核销」对照行**。 |
| **StockReduceAnswerPlan** | **单域出库/核销**；wire 来自出库语义，**不能单独承载**「采购 + 出库脱节」的并列证据与缺口声明。 |
| **BusinessDiagnosisPlan** | 由 Tool + 菜品透视等拼装，**缺少**与四条 **`purchase_*_risk` wire 一一对应的结构化 risk 区块**（如行级对照、证据覆盖、诚实降级字段）；现有采购/出库 risk -append 多为粗粒度 findings。 |

因此需要 **一块专门的、只读的、面向双轨 tool 结果与既有子 AnswerPlan 的轻量 Plan**，在 Composer 层可被 **确定性优先**消费。

---

## 4. 建议新增轻量 `PurchaseRiskAnswerPlan`

### 4.1 设计原则

- **只读拼装**：从 `toolResults`、可选的 `PurchaseAnswerPlan` / `StockReduceAnswerPlan` 及 Resolved 上下文**派生**，**不查库、不新写 SQL**（实现阶段在 `Builder` 中落实）。
- **诚实优先**：字段允许 **null / 缺失**；`dataCoverage` 与 `limitations` 必须能支撑「能说什么 / 不能说什么」。
- **与 Composite 解耦**：不依赖 `BusinessDiagnosisCompositeAnswerPlan` 或 `BusinessDiagnosisCompositeReadonlyComposer` 的 PRIMARY 路径。

### 4.2 建议字段

| 字段 | 说明 |
|------|------|
| **type** | 四类风险常量之一（见第 5 节）。 |
| **scopeLabel** | 与现有 AnswerPlan 一致的可读范围（如与 `ResolvedQueryContext` 横幅对齐）。 |
| **timeLabel** | 统计窗可读描述。 |
| **summary** | 短摘要；**仅**由已填充的 rows / coverage / 诚实规则拼接，禁止臆造指标。 |
| **riskLevel** | 枚举或字符串（如 INFO / WARN / NOTICE）；与「证据充分度」挂钩，非单纯业务严重度。 |
| **rows** | 商品（或 SKU）维度对照行列表（完整形态见 4.3）。**Phase 1**：可为 **空**、**仅采购侧片段** 或 **仅出库侧片段**；**不得**为强 join 后的「对账行」伪装成事实。 |
| **dataCoverage** | 布尔位：当前回合**是否具备**某类数据；**false 时对应口径不得在 summary 中断言**。 |
| **limitations** | 字符串列表：本回合无法完成的判断及原因（如缺库存快照、缺批次、缺日期列等）。 |
| **suggestedNextQuestions** | 在用户可理解前提下建议的追问或补数方向（可选）。 |

### 4.3 `rows` 行结构建议（目标形态；Phase 2+ 数据齐备后逐步填满）

> **Phase 1**：不追求下表每行、每列可填；以 **单侧列表 + 汇总 + 覆盖声明** 为主（见 4.5）。下表描述 **长期目标**，避免实现阶段误以为 Phase 1 必须产出完整对照行。

| 子字段 | 说明 |
|--------|------|
| **goodsName** | 商品名称（来自_tool 已有维度；无法对齐时可为 null 并记入 limitations）。 |
| **purchaseQuantity** | 采购数量（若 tool 未提供数量口径则为 null）。 |
| **purchaseAmount** | 采购金额（若仅有金额则 quantity 可空）。 |
| **stockReduceQuantity** | 出库/核销侧数量（若 tool 仅有金额则可为 null）。 |
| **stockReduceAmount** | 出库/核销侧金额。 |
| **quantityGap** | 可推导则填；**不可推导则为 null**（不得用 LLM 补）。 |
| **reduceToPurchaseRatio** | 如 `stockReduceAmount / purchaseAmount`；分母为 0 或缺失则 **null**。 |
| **lastPurchaseDate** | 仅当 tool 或下游明确提供日期列时填充；否则 null。 |
| **lastStockReduceDate** | 同上。 |
| **riskReason** | 短句，**仅能引用行内非空字段与 type 语义**（模板化/规则生成优先）。 |

### 4.4 `dataCoverage` 建议

| 键 | 含义 |
|----|------|
| **hasPurchaseData** | `purchase_overview` 成功且存在可用聚合或商品行。 |
| **hasStockReduceData** | `stock_reduce_query` 成功且存在可用聚合或商品行。 |
| **hasPerGoodsPurchaseRows** | **（建议 Phase 1 显式化）** 是否存在 **可展示的商品级采购 Top 行**（与仅有订单级汇总区分）。 |
| **hasPerGoodsOutboundRows** | **（建议 Phase 1 显式化）** 是否存在 **商品级出库 Top/排名行**（诊断 overview narrative 下常为 false）；与仅有类型汇总区分。 |
| **hasInventorySnapshotData** | 是否在本轮 **toolResults** 中具备可引用的库存快照（如 `stock_query` / `warehouse_stock_overview` 成功且结构可读）；**默认可为 false**。 |
| **hasFreshnessShelfLifeData** | 是否具备保鲜期、效期等字段；**默认可为 false**。 |
| **hasBatchStockData** | 是否具备批次级库存；**默认可为 false**。 |

### 4.5 Phase 1：诚实降级版风险提示（可落地范围）

Phase 1 的 `PurchaseRiskAnswerPlan` **仅**承诺以下内容；与第 6 节诚实规则一并执行。

| 能力 | Phase 1 行为 |
|------|----------------|
| **展示** | **采购侧**：仅展示 tool 中已有的 **商品 Top 列表**（如金额/频次/单价排名等，**不得虚构列**）。**出库侧**：优先展示 **汇总**（类型合计、总出库等 tool 已提供的聚合）；若 narrative 下存在 **出库商品 Top**，则 **如有则展示**，否则在 `dataCoverage` 中标记无商品级出库行。 |
| **dataCoverage** | **必须**显式填充（见 4.4）：至少区分「有无可用采购商品行」「有无可用出库聚合」「有无可用出库商品行」「有无库存快照/保鲜期/批次」等；与事实不一致的键不得标为 true。 |
| **limitations** | **必须**列出本回合无法完成的判断及原因（例如：无稳定商品 id、无 join、无末次日期、无库存快照等）。 |
| **Join** | **不做强 join**；不产出「采购 + 出库」合并对照表作为事实陈述。若需并列展示，仅允许 **并列两段事实**（采购 Top 一段、出库汇总一段），并声明 **未做 SKU 级核对**。 |
| **措辞** | **不说**确定 **积压**、**快过期**、**一定滞销**；仅允许 **「当前数据不足以认定…」**、**「可能存在…风险，需结合库存/效期数据核对」** 等 **疑似 / 提示** 级别表述，且须与 `limitations` 一致。 |
| **riskLevel** | 与 **证据充分度** 挂钩；数据单侧或双侧缺口大时，**宜为 INFO / NOTICE**，避免 WARN 暗示已证实。 |

**Builder 输入优先级（设计约定）**：以 **`toolResults` 解析结果为第一事实源**；`PurchaseAnswerPlan` / `StockReduceAnswerPlan` 仅作 **时间窗、scope、兜底摘要** 补充，**不得**假设子 Plan 已含完整商品 Top（overview 路径下子 Plan 常为汇总行）。

---

## 5. 四类风险 `type`

与 Lexicon / Prompt 中 **`metric.rankingType`（蛇形 wire）** 对齐的 **Plan 层类型常量**建议如下（实现时可映射自 canonical wire）：

| Plan `type` 常量建议 | 对应语义（与 Batch 3 一致） |
|----------------------|--------------------------------|
| **PURCHASE_STOCK_REDUCE_MISMATCH** | `purchase_stock_reduce_mismatch`：采购多、出库少；买得多用得少；进货多消耗少。 |
| **PURCHASE_SLOW_MOVING_RISK** | `purchase_slow_moving_risk`：采购后长期无出库 / 未核销；买回来一直没用。 |
| **PURCHASE_INVENTORY_OVERSTOCK_RISK** | `purchase_inventory_overstock_risk`：积压 / 库存压力过大（在**无快照**时降级为疑似表述）。 |
| **PURCHASE_FRESHNESS_RISK** | `purchase_freshness_risk`：快过期 / 新鲜度风险（在**无保鲜期/批次**时降级为风险提示）。 |

说明：Plan 层 type 可用 **枚举大写** 与 DTO 惯例一致；与 wire 的映射在 Builder 单点维护即可。**Phase 1** 中 `type` 仍用于 **路由展示模板与用户预期对齐**，但 **不得**因 type 名称而在无证据时输出第 2.1 节禁止的那类断言（须遵守 4.5、第 6 节）。

---

## 6. 诚实降级规则

以下规则应在 **Builder** 与 **确定性 Composer** 中共同遵守；**禁止**让 LLM 根据类型名称编造未出现在 `toolResults` / rows 中的数值或日期。

**Phase 1 附加（与 2.1、4.5 一致）：** 在缺失稳定 `goodsId`、缺失单品出库行、或仅弱匹配名称时，**禁止**输出商品级差额、比例、未出库天数、积压与临期的 **确定性结论**；**禁止**将采购 Top 与出库汇总 **拼接叙述为** 已完成对账。

1. **没有库存快照**（`hasInventorySnapshotData = false`）：**不得**断言「一定积压」「库存多少」；仅允许 **「疑似积压 / 需结合库存核对」** 等表述，并在 `limitations` 中写明缺库存证据。
2. **没有保鲜期 / 批次库存**（`hasFreshnessShelfLifeData`、`hasBatchStockData` 为 false）：**不得**断言「将在某日过期」或精确认定效期；仅允许 **「新鲜度/效期风险需补数据」** 类提示。
3. **没有最近采购 / 最近出库日期**：**不得**断言「已连续 N 天未出库」；可提示 **「无法在现有数据中计算末次日期」**。
4. **出库 / 核销未及时录入**：在 `limitations` 中可列为 **系统性说明**（与业务现实一致时），避免把数据缺口误读为经营事实。
5. **任一关键字段缺失**：对应 **ratio / gap / 日期** 置 null；summary 与 `riskReason` **不得**用自然语言虚构补齐。

---

## 7. 最小接入点建议（实现阶段参考）

| 接入点 | 建议 |
|--------|------|
| **AiRunState** | 新增 **`purchaseRiskAnswerPlan`**（或与现有命名一致的轻量 DTO 引用），与 `purchaseAnswerPlan`、`stockReduceAnswerPlan` 并列。 |
| **BusinessToolExecutionNode** | 在 **`finally`** 块中，于现有 `PurchaseAnswerPlanBuilder` / `StockReduceAnswerPlanBuilder.attachIfApplicable` **之后**（或同层）调用 **`PurchaseRiskAnswerPlanBuilder.attachIfApplicable(state)`**；Builder **只读** `toolResults` 与已有子 AnswerPlan，**不查库**。 |
| **PurchaseRiskAnswerPlanBuilder** | 根据 `ResolvedQueryContext` 中 **canonical 四条 wire** 决定是否构建；非四条 wire **不挂载**或挂载空壳并 debug 标记（实现时二选一）。**Phase 1** 产出须符合 **4.5**（诚实降级），不追求 4.3 完整矩阵。 |
| **StubAnswerComposerNode** | 当 **`business_diagnosis_path`** 且 **`structuredIntentDetail`** 为四条 **purchase risk wire 之一** 时，**优先**读取 **`purchaseRiskAnswerPlan`**；建议 **先走确定性渲染**（不调用 LLM 或仅极短包装），避免跨域编造。 |
| **BusinessDiagnosisCompositeReadonlyComposer** | **暂不接**；双域 PRIMARY 与 Composite 终稿分离。 |
| **Gate / Shadow / PRIMARY** | **本轮设计不改**；后续若 Composite 需消费该 Plan，再单独立项。 |

补充：**`DiagnosisPlanBuilder` / `BusinessDiagnosisPlan` 并存关系**  
- 第一阶段可 **不修改** `DiagnosisPlan` 聚合逻辑，避免波及追问与 enrich。  
- 若未来要在诊断卡片中展示双域风险，再评估是否将 `PurchaseRiskAnswerPlan` 摘要并入证据行（第 8 节 Phase 4）。

---

## 8. 分阶段落地

| 阶段 | 目标 | 说明 |
|------|------|------|
| **Phase 1** | **诚实降级版风险提示** | 新增 DTO + Builder（实现阶段）：**只读** `toolResults` 为主；产出 **采购 Top + 出库汇总 +（如有）出库 Top**；**强制** `dataCoverage` / `limitations`；**不做强 join**；不承诺第 2.1 节所列禁止项；Composer 可不接或仅 fallback。**不实现完整 4.3 对照行矩阵。** |
| **Phase 2** | **商品级对账能力前置条件 + Tool/合约扩展** | 在具备下节 **前置条件** 后，再追求 4.3 行级字段、比例与更接近设计初衷的风险叙述；含 **改 Tool 输出 / SQL / narrative** 的独立变更与评审。 |
| **Phase 3** | **确定性 Composer** | 在 `StubAnswerComposerNode` 固化分支：**四条 wire → 确定性全文**（或 `DeterministicAnswerRenderer` 新方法），LLM 仅作可选润色开关（默认关）。 |
| **Phase 4** | **与 BusinessDiagnosisPlan / Composite 关系** | 评估 `BusinessDiagnosisPlan` 是否引用 Plan 摘要以防 duplicate；Composite/Gate 是否需识别「双域 risk」白名单（默认 **不做**，直到 PRIMARY 稳定）。 |

### 8.1 Phase 2 前置条件（数据与工具）

在 Phase 2 中若要将 Plan 提升为 **稳定商品级 `PurchaseRiskAnswerPlan`**（含可对账行、更可信的风险表述），建议至少满足：

1. **统一商品键**：相关 Tool（或统一 enrich 层）在 **商品级 rows** 上提供一致的 **`goodsId`（或与 `disGoodsId` 对齐的稳定 id）**，采购与出库侧 **均可关联**。
2. **`purchase_overview`（商品维度）**：除名称外，建议提供 **采购数量、金额、单位** 等约定字段，以及 **`lastPurchaseDate`（最近采购日期）**（或等价、可追溯列）。
3. **`stock_reduce_query`**：在 **风险场景 / 约定 narrative** 下，保证可提供 **商品级出库数量、金额**，以及 **`lastStockReduceDate`（最近出库日期）**（或等价列），而非仅汇总。
4. **库存与临期**：若产品上要断言 **真实积压** 或 **快过期**，需额外具备 **库存快照**、**批次库存**、**保鲜期/效期** 等数据来源（本设计不限定具体 tool 名称，但须在 `dataCoverage` 中可判定为 true）。

未满足以上条件前，实现与文案应 **停留在 Phase 1 诚实降级范围**。

---

## 9. 文档维护

- **依赖**：Batch 3 语义契约（Prompt v1/v2、`AiQuerySemanticLexicon` 四条 wire）；**Phase 1 数据可行性**以第 2.1 节为权威摘要。  
- **非目标**：本文不定义 SQL 实现细节；Tool 字段以 Phase 2 前置条件（8.1）为产品输入清单，具体 schema 在扩展阶段单独立项。
