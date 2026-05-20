# D-PURCHASE-DRILLDOWN-MATRIX-CONTRACT-P0 — 采购下钻矩阵契约

> **目的**：把采购 GOODS 锚 4 轮下钻（及 Phase 1 已注册能力）整理为 **Harness Engineering 矩阵契约**，约束 LLM / Canonical / Validator / Registry / PlanBuilder 分工，防止 Java 退化为语义二次解析器。  
> **范围**：文档与结构梳理；**不新增** Java 特判、业务能力、SQL/Tool/Composer/TurnMemory 变更。  
> **相关**：[follow-up-drilldown-matrix.md](./follow-up-drilldown-matrix.md)（实体+detailWanted 映射）、[purchase-v2-semantic-followup-phase1-summary.md](./purchase-v2-semantic-followup-phase1-summary.md)（1A 验收对照）、[business-capability-registry.md](./business-capability-registry.md)。

---

## 1. 维度定义（Matrix Axes）

| 维度 | 枚举值 | 含义 |
|------|--------|------|
| **anchorType**（上一轮结果锚） | `GOODS` / `SUPPLIER` / `STORE` / `NONE` | 来自 `resultAnchorsSummary` / TurnMemory；矩阵 **前提**，不是 LLM 独立槽 |
| **queryObject** | `GOODS` / `SUPPLIER` / `PURCHASE_ORDER` / `STORE` | 本轮问法主对象 |
| **operation** | `RANKING` / `BREAKDOWN` / `DETAIL` / `COMPARE` / `TREND` / `SUMMARY` | 动作类型；排行首轮常用 `RANKING`，拆桶/明细追问常用 `BREAKDOWN` 或 `DETAIL` |
| **metric** | `PURCHASE_AMOUNT` / `PURCHASE_COUNT` / `PURCHASE_QUANTITY` / `UNIT_PRICE` | 排序或聚合度量 |
| **sourceFacet** | `ALL` / `SELF_PURCHASE` / `SUPPLIER_PURCHASE` | 采购来源口径 |
| **detailWanted** | `SOURCE_BREAKDOWN` / `SUPPLIER_BREAKDOWN` / `SUPPLIER_UNIT_PRICE` / `GOODS_DETAIL` / `GOODS_UNIT_PRICE` | 追问明细契约键；与 Tool `purchaseFollowUpDetailWanted` 对齐 |
| **anchorPolicy** | `USE_PREVIOUS_ANCHOR` / `IGNORE_PREVIOUS_ANCHOR` / `REQUIRE_CLARIFICATION` | 是否继承上轮锚点（文档 alias：`NEW_ANCHOR` ≈ `IGNORE_PREVIOUS_ANCHOR`，`NONE` ≈ 首轮无继承语境） |
| **structuredIntentDetailWire** | 如 `purchase_source_goods_query`、`supplier_amount_ranking` | 结构化子口径 wire；与 slots **必须自洽** |

**矩阵匹配键（Registry）**：`priorFramePlanType` + `anchorType`（唯一锚）+ 本轮 **canonical 后** slots 组合 → `capabilityId` → `targetPurchasePlanType`。

---

## 2. GOODS 锚点下钻矩阵（Phase 1 — 已实现）

**入口帧**（首轮产出 GOODS 锚）：`PURCHASE_GOODS_AMOUNT_RANKING` / `PURCHASE_GOODS_COUNT_RANKING`（`anchorPolicy=IGNORE_PREVIOUS_ANCHOR`，wire=`purchase_goods_amount_ranking` 等）。

**4 轮标准链路**（Harness 严格 Case：`DRILLDOWN_PURCHASE_MATRIX_P1`；文档行号 **R0–R3** 对应探针 **R1–R4**）：

| # | 用户场景（示意） | detailWanted | Expected semanticSlots（canonical 后） | capabilityId | targetPlanType |
|---|------------------|--------------|------------------------------------------|--------------|----------------|
| **R0** / 探针 R1 | 商品采购金额/数量排行 | — | `queryObject=GOODS`, `operation=RANKING`, `metric=PURCHASE_AMOUNT\|PURCHASE_COUNT`, `sourceFacet=ALL`, `anchorPolicy=IGNORE_PREVIOUS_ANCHOR`, wire=`purchase_goods_amount_ranking` | — | `PURCHASE_GOODS_AMOUNT_RANKING` |
| **R1** / 探针 R2 | **第一名是谁供的** / 自采还是供货商供 | `SOURCE_BREAKDOWN` | `queryObject=GOODS`, `operation=**BREAKDOWN**`, `metric=PURCHASE_AMOUNT\|PURCHASE_QUANTITY`, `sourceFacet=**ALL**`, `anchorPolicy=**USE_PREVIOUS_ANCHOR**`, `detailWanted=SOURCE_BREAKDOWN`, wire=**`purchase_source_goods_query`** | `purchase.goods_anchor.source_breakdown` | `PURCHASE_GOODS_SOURCE_BREAKDOWN` |
| **R2** / 探针 R3 | **这个商品每个供货商分别采购了多少** | `SUPPLIER_BREAKDOWN` | `queryObject=GOODS`, `operation=BREAKDOWN\|DETAIL`, `metric=PURCHASE_AMOUNT\|PURCHASE_QUANTITY\|PURCHASE_COUNT`, `sourceFacet=**SUPPLIER_PURCHASE**`, `anchorPolicy=USE_PREVIOUS_ANCHOR`, `detailWanted=SUPPLIER_BREAKDOWN`, wire=`purchase_source_goods_query` | `purchase.goods_anchor.supplier_breakdown` | `PURCHASE_SUPPLIER_GOODS_DETAIL` |
| **R3** / 探针 R4 | **哪个供货商单价最高** | `SUPPLIER_UNIT_PRICE` | `queryObject=**SUPPLIER**`, `operation=RANKING\|BREAKDOWN\|DETAIL`, `metric=**UNIT_PRICE**`, `sourceFacet=SUPPLIER_PURCHASE`, `anchorPolicy=USE_PREVIOUS_ANCHOR`, `detailWanted=SUPPLIER_UNIT_PRICE`, wire=`purchase_source_goods_query` | `purchase.goods_anchor.supplier_unit_price` | `PURCHASE_SUPPLIER_GOODS_DETAIL` |

### 2.0 三句勿混（GOODS 锚续问 disambiguation）

| 用户问法（示意） | **必须** detailWanted | 关键槽位 | **禁止**误落 |
|------------------|----------------------|----------|--------------|
| 「第一名是谁供的？」「自采还是供货商供？」 | `SOURCE_BREAKDOWN` | `sourceFacet=ALL`, `metric=PURCHASE_AMOUNT\|PURCHASE_QUANTITY`, `operation=BREAKDOWN` | **`SUPPLIER_UNIT_PRICE`**、**`supplier_amount_ranking`**、**`PURCHASE_AMOUNT` 充当单价** |
| 「每个供货商分别采购多少？」 | `SUPPLIER_BREAKDOWN` | `sourceFacet=SUPPLIER_PURCHASE`, `metric=PURCHASE_AMOUNT\|PURCHASE_QUANTITY\|PURCHASE_COUNT` | **`SOURCE_BREAKDOWN`**、**`sourceFacet=ALL`** |
| 「哪个供货商单价最高？」 | `SUPPLIER_UNIT_PRICE` | `queryObject=SUPPLIER`, `metric=UNIT_PRICE`, `operation=RANKING` | **`supplier_amount_ranking`** wire、**`metric=PURCHASE_AMOUNT`** |

**结构性违例（Validator 拒收，禁止 silent canonical 成单价）**：`detailWanted=SUPPLIER_UNIT_PRICE` 但 `metric` **不含** `UNIT_PRICE`（例如仍为 `PURCHASE_AMOUNT`）→ `MATRIX_VALIDATION_SUPPLIER_UNIT_PRICE_INCOMPLETE` / Registry 不匹配；**不得**服务端读原文「谁供的」等关键词纠偏。

**Harness 严格预期（`DRILLDOWN_PURCHASE_MATRIX_P1`）**：

| 轮次 | 问句 | `matchedCapabilityId` | `harnessReplayPurchaseAnswerPlanType` | `needSemanticClarification` |
|------|------|------------------------|----------------------------------------|----------------------------|
| R1 | 这个月采购最多的商品是什么？ | — | `PURCHASE_GOODS_AMOUNT_RANKING` | — |
| R2 | 第一名是谁供的？ | `purchase.goods_anchor.source_breakdown` | `PURCHASE_GOODS_SOURCE_BREAKDOWN` | **false** |
| R3 | 这个商品每个供货商分别采购了多少？ | `purchase.goods_anchor.supplier_breakdown` | `PURCHASE_SUPPLIER_GOODS_DETAIL` | **false** |
| R4 | 哪个供货商单价最高？ | `purchase.goods_anchor.supplier_unit_price` | `PURCHASE_SUPPLIER_GOODS_DETAIL` | **false** |

R1 须沉淀 `resultAnchors`（类型 `GOODS`，Harness 仅断言 count≥1，不断言具体 `GOODS#` id）。

### 2.1 R1 — SOURCE_BREAKDOWN（来源拆桶）

| 字段 | 期望值 |
|------|--------|
| 场景 | 「第一名是谁供的」「自采和供货商各多少」 |
| anchorType 前提 | 唯一 `GOODS` 锚（排行 Top1 或已沉淀锚） |
| operation | `BREAKDOWN`（合同允许 LLM 输出 `DETAIL`，canonical **同义归一** → `BREAKDOWN`） |
| sourceFacet | `ALL` |
| wire | `purchase_source_goods_query`（**不得**仍为 `supplier_amount_ranking`） |
| PlanBuilder | 必须沉淀 `GOODS` `resultAnchors`（`sourcePlanType=PURCHASE_GOODS_SOURCE_BREAKDOWN`）；无数据也保留锚 |

### 2.2 R2 — SUPPLIER_BREAKDOWN（各供货商采购额/量）

| 字段 | 期望值 |
|------|--------|
| 场景 | 「每个供货商分别采购多少」「这个商品每个供货商分别采购了多少」 |
| anchorType 前提 | 唯一 `GOODS` 锚（可来自 R0/R1/R2 自身 Detail 轮） |
| operation | `BREAKDOWN` 或 `DETAIL` |
| sourceFacet | `SUPPLIER_PURCHASE` |
| metric | 含 `PURCHASE_AMOUNT` / `PURCHASE_QUANTITY` / `PURCHASE_COUNT` |
| PlanBuilder | `PURCHASE_SUPPLIER_GOODS_DETAIL`；**必须**沉淀 `GOODS` 锚供续问 |

### 2.3 R3 — SUPPLIER_UNIT_PRICE（各供货商单价 / 最高单价）

| 字段 | 期望值 |
|------|--------|
| 场景 | 「哪个供货商单价最高」「各供货商单价多少」 |
| anchorType 前提 | 唯一 `GOODS` 锚 |
| queryObject | `SUPPLIER`（在 GOODS 锚语境下问供货商行） |
| metric | `UNIT_PRICE` |
| **结构性违例** | `USE_PREVIOUS_ANCHOR` + GOODS 锚 + wire=`supplier_amount_ranking` → **矩阵禁止**（全局供货商金额榜，非 GOODS 锚单价明细） |
| PlanBuilder | 同 `PURCHASE_SUPPLIER_GOODS_DETAIL`；无数据 `GOODS_SUPPLIER_UNIT_PRICE_NO_DATA`；保留 GOODS 锚 |

### 2.4 sourceFacet 口径说明（GOODS 锚链路）

`sourceFacet` 表示**本轮查询的数据边界**（纳入哪些采购来源），与 Tool 聚合口径、Composer 拆行方式一致。**不等于**「输出里只能有一行」：部分 detailWanted 会在 **`sourceFacet=ALL` 的输入边界下，仍按来源维度拆成多行输出**。

| 阶段 / detailWanted | semanticSlots `sourceFacet` | 数据边界含义 | 预期输出形态 |
|---------------------|----------------------------|--------------|--------------|
| **R0** GOODS ranking | `ALL` | **自采 + 供货商订货合计**（与排行 Top 商品口径一致） | 商品排行列表 |
| **R1** `SOURCE_BREAKDOWN` | `ALL` | 查询边界仍为全来源合计 | **拆成两桶**：`SELF_PURCHASE` 金额/量 + `SUPPLIER_PURCHASE` 金额/量（非 ALL 单行） |
| **R2** `SUPPLIER_BREAKDOWN` | `SUPPLIER_PURCHASE` | **仅**供货商订货部分 | 按供货商分行（金额/量） |
| **R3** `SUPPLIER_UNIT_PRICE` | `SUPPLIER_PURCHASE` | **仅**供货商订货部分 | 按供货商分行（单价） |

**要点（契约层，LLM / Validator / PlanBuilder / Composer 共同遵守）：**

1. **GOODS ranking 默认 `sourceFacet=ALL`**：表示排行与首轮锚点基于「自采 + 供货商订货」合计，**不得**在 R0 误用 `SELF_PURCHASE` 或 `SUPPLIER_PURCHASE` 缩小口径（除非用户明确只问单渠道，且另有渠道矩阵能力）。
2. **`SOURCE_BREAKDOWN` 输入 `ALL`、输出分桶**：slots 保持 `sourceFacet=ALL`；Tool/Plan 应答须**显式拆分**自采与供货商两路，而非把 ALL 当作单行聚合答案。
3. **`SUPPLIER_BREAKDOWN` / `SUPPLIER_UNIT_PRICE` 必须 `sourceFacet=SUPPLIER_PURCHASE`**：只分析供货商订货；**不得**用 `ALL` 或 `SELF_PURCHASE` 槽位表达「各供货商」或「供货商单价」问法（见 §2.0）。
4. **仅自采、无供货商订货时**（该 GOODS 无 supplier 行数据）：
   - **`SOURCE_BREAKDOWN`**：仍应正常回答 — 自采金额 > 0，供货商金额为 **0**（或等价空桶）；**保留 GOODS 锚**，供后续续问。
   - **`SUPPLIER_BREAKDOWN` / `SUPPLIER_UNIT_PRICE`**：应走 **no-data** 路径（如 `GOODS_SUPPLIER_BREAKDOWN_NO_DATA` / `GOODS_SUPPLIER_UNIT_PRICE_NO_DATA`），并在答案中说明**该商品没有供货商订货记录**；**仍保留 GOODS 锚**（P1C），但**不得**静默 fallback 成 SOURCE_BREAKDOWN 或排行。
5. **禁止伪造供货商行**：**不允许**把 `SELF_PURCHASE`（自采）**伪造成**一个 supplier 实体行（例如虚构「自采供货商」、把自采金额塞进 `SUPPLIER_PURCHASE` 下的 supplier 列表）。自采只出现在 **SOURCE_BREAKDOWN 的自采桶**；供货商明细/单价列表**仅**含真实供货商订货记录。

---

## 3. SUPPLIER 锚点下钻矩阵

| 场景 | detailWanted | anchorType 前提 | capabilityId | targetPlanType | 状态 |
|------|--------------|-----------------|--------------|----------------|------|
| 采购了哪些商品 / 商品清单 | `GOODS_DETAIL` | 唯一 `SUPPLIER` 锚（供货商金额排行 Top1） | `purchase.supplier_anchor.goods_detail` | `PURCHASE_SUPPLIER_GOODS_DETAIL` | **Supported** |
| 商品单价分别是多少 | `GOODS_UNIT_PRICE` | 同上 | `purchase.supplier_anchor.goods_detail` | `PURCHASE_SUPPLIER_GOODS_DETAIL` | **Supported** |
| 渠道 overview 后「定了什么东西」 | `GOODS_DETAIL` | `NONE`（无实体锚，渠道继承） | `purchase.supplier_channel.goods_detail` | `PURCHASE_SUPPLIER_GOODS_DETAIL` | **Supported** |
| 这个供货商**主要**供了哪些商品（强调排序/Top） | `GOODS_*` + ranking 语义 | `SUPPLIER` | — | — | **Future** |
| 这个供货商**哪个商品**采购最多 | `GOODS_AMOUNT_RANKING`（占位） | `SUPPLIER` | — | — | **Future** |
| 这个供货商给**哪个门店**供得最多 | `STORE_BREAKDOWN`（占位） | `SUPPLIER` | — | — | **Future** |
| SUPPLIER 锚 + `TREND` / `COMPARE` | — | — | — | — | **Future**（本轮不实现） |

---

## 4. STORE / BUSINESS 锚点下钻矩阵

| 场景 | 状态 |
|------|------|
| 门店采购汇总 / 明细 (`PURCHASE_SUMMARY` / `PURCHASE_DETAIL`) | **Future** — 见 follow-up-drilldown-matrix REUSE_READY |
| 门店 → 出库 / 核销 / 毛利 | **Future** — 非采购 Registry Phase 1 |
| 经营诊断 `STORE` + `STORE_RISK_REASONS` | **已实现**（诊断域，非本采购矩阵） |
| STORE 锚 + `TREND` / `COMPARE` | **Future** |

---

## 5. Java 职责边界

### 5.1 LLM（`query_semantic_parser.v2.md`）

- 根据用户问题 + 上一轮锚点输出完整 `semanticSlots`。
- **必须**输出：`anchorPolicy`、`queryObject`、`operation`、`metric`、`sourceFacet`、`detailWanted`（`purchase_source_goods_query` wire 下必填）。
- **必须**遵守 GOODS 锚矩阵：R1 用 `BREAKDOWN`+`ALL`；R2 用 `SUPPLIER_PURCHASE`+`SUPPLIER_BREAKDOWN`；R3 用 `UNIT_PRICE`+`SUPPLIER_UNIT_PRICE`，**不得**输出 `supplier_amount_ranking`。

### 5.2 Semantic Canonical（`CurrentSemanticFrame` + `AiQuerySemanticLexicon`）

**只允许：**

| 类型 | 示例 |
|------|------|
| 大小写 / 分隔符归一 | `source_facet` → `SOURCE_FACET` |
| 枚举别名 | wire 别名表、`detailWanted` 合法化 |
| 合同内等价 | `DETAIL` + `SOURCE_BREAKDOWN` → `operation=BREAKDOWN`（**仅**矩阵 `SOURCE_BREAKDOWN` 行 `operationCanonicalFrom/To` + `PurchaseDrilldownMatrix.canonicalOperation` / `applySourceBreakdownOperationCanonical`） |
| 结构性 wire 违例（矩阵条件） | GOODS 锚 + `USE_PREVIOUS` + `supplier_amount_ranking` + `UNIT_PRICE` 信号 → 整包纠为 R3 槽位（`applySupplierUnitPriceCanonical`，见 §2.3） |

**禁止：**

- 读中文原句重判业务意图
- 为单条 Harness 问法硬编码
- slots 不完整时「猜」用户想法
- 无 `UNIT_PRICE` / `SUPPLIER_UNIT_PRICE` 信号时，把任意槽位改成单价帧

**审计要求**：凡突变 sem 的规则，应有 **debug reason**（当前部分缺失，见 §7）。

### 5.3 Validator（`CurrentSemanticFrameValidator`）

- 判断 canonical 后 slots **是否符合矩阵**（`PurchaseDrilldownMatrix.frameMatchesRow` / `operationAccepted`；形状 + 锚唯一性 + wire 合法集）。
- GOODS 锚三行（`SOURCE_BREAKDOWN` / `SUPPLIER_BREAKDOWN` / `SUPPLIER_UNIT_PRICE`）**前置**矩阵门禁；**无**手写 metric / `sourceFacet=ALL` / wire fallback。
- 不符合 → **具体 clarification reason**（如 `MATRIX_VALIDATION_SOURCE_BREAKDOWN_INCOMPLETE`、`MATRIX_VALIDATION_SUPPLIER_UNIT_PRICE_INCOMPLETE`、`REGISTRY_NO_MATCH`）。
- **不**无限兜底；**不**突变 sem。

### 5.4 BusinessCapabilityRegistry

- **矩阵匹配**：`priorFramePlanType` + anchor + slot 镜像 → `capabilityId` / `targetPurchasePlanType`。
- `semanticMirrorsMatch*` 与 `PurchaseFollowUpSlotSignals.isComplete*` ** intentionally 相似**（形状镜像 vs 门禁），不做 NL。
- **不做** canonical、不读用户原话。

### 5.5 PurchaseFollowUpSlotSignals

- 帧完整性（`isComplete*`）、有效追问门禁（`isEffectiveStructuralPurchaseFollowUp`）。
- **`resolveSlotDetailWanted`**：LLM **未输出** `detailWanted` 时，按 framePlan + slots **推断** detail（历史推断层，非矩阵唯一真理源）。
- **不**突变 sem；推断结果应与矩阵一致，否则应倾向 Validator 澄清。

### 5.6 PurchaseAnswerPlanBuilder

- 根据 `targetPurchasePlanType` + Tool payload 生成 plan。
- 每个 **可继续追问** 的 plan **必须**沉淀 `resultAnchors`（P1C：`PURCHASE_SUPPLIER_GOODS_DETAIL` no-data 也保留 GOODS 锚）。
- **不**读用户原话；**不**做 semantic canonical。
- **P1A 未矩阵化**：`isGoodsSourceBreakdownIntent` / `isGoodsSupplierBreakdownFollowUpIntent` / `isGoodsSupplierUnitPriceFollowUpIntent` 仍为 **平行 detailWanted 契约**（与矩阵语义对齐但未调用 `PurchaseDrilldownMatrix`）；**P1B** 再评估是否路由矩阵行 `targetPurchasePlanType`。

### 5.7 推荐管线顺序

```
LLM parse
  → CurrentSemanticFrame.canonicalizePurchaseFollowUp (突变，仅此一处)
  → CurrentSemanticFrame.buildFrame (只读)
  → CurrentSemanticFrameValidator.validate
  → PurchaseFollowUpSlotSignals.resolveSlotDetailWanted (仅补缺失 detail)
  → BusinessCapabilityRegistry.match
  → PurchaseAnswerPlanBuilder
```

---

## 6. 代码审计（P0 — 只读基准；P1A 收口见 §6.F）

### 6.A 合理的合同归一

| 位置 | 规则 | 矩阵依据 |
|------|------|----------|
| `PurchaseDrilldownMatrix.SOURCE_BREAKDOWN` 行 | `operationCanonicalFrom=DETAIL` → `operationCanonicalTo=BREAKDOWN` | §2.1 |
| `PurchaseDrilldownMatrix.applySourceBreakdownOperationCanonical` + `canonicalOperation` | 同上，突变 sem / buildFrame 双通道，**同一矩阵行** | §2.1 |
| `AiQuerySemanticLexicon.canonicalOperation` | 转发 `PurchaseDrilldownMatrix.canonicalOperation` | §2.1 |
| `AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire` / `canonicalDetailWanted` | wire、detail 别名 | 枚举层 |
| `PurchaseDrilldownMatrix.shouldCanonicalSupplierAmountToUnitPrice` + `applySupplierUnitPriceCanonical` | USE + 唯一 GOODS 锚 + `supplier_amount_ranking` wire/rankingType + `UNIT_PRICE` 信号 | §2.3 |
| `CurrentSemanticFrameValidator` | 三行 `frameMatchesRow` / `operationAccepted`（P1A 已去重） | 矩阵形状 |
| `BusinessCapabilityRegistry.matchGoodsAnchor*` | 三行 GOODS 锚 capability | §2 表 |
| `PurchaseAnswerPlanBuilder.buildGoodsSourceBreakdownAnchor` / supplier detail anchors | 锚沉淀 + no-data 保留 | P1C 契约 |

### 6.B 像业务语义二次猜测（风险）

| 位置 | 规则 | 风险说明 |
|------|------|----------|
| `PurchaseFollowUpSlotSignals.resolveSlotDetailWanted` | 整段 framePlan + slots 推断 detail | LLM 漏槽时的 **推断层**；与矩阵双轨 |
| `PurchaseFollowUpSlotSignals.semanticGoodsRankingToSupplierUnitPrice` | 委托 `PurchaseDrilldownMatrix.hasUnitPriceContractSignal` | 推断 SUPPLIER_UNIT_PRICE，缺显式 detail 时补 |
| `suppressSlotForAggregateMoneyQuestionAfterSupplierRanking` | 供货商排行后聚合金额抑制 | 槽位+frame 推断，非矩阵表驱动 |

### 6.C 应迁到矩阵表 / 配置式判断

| 重复/散落 | 建议 |
|-----------|------|
| DETAIL 接受：`Lexicon` + `Validator` + `Registry.followUpSlotMatchesRow` + `Signals.slotsInferRowShape` | 已由 `SOURCE_BREAKDOWN.operationAccepted` + canonical 覆盖（P1A Validator 已读矩阵） |
| `USE_PREVIOUS` + `supplier_amount_ranking` | 矩阵 `applySupplierUnitPriceCanonical`（仅 UNIT_PRICE 信号） |
| `PurchaseAnswerPlanBuilder.isGoods*Intent` | **P1B**：可选 `findByDetailWanted` + `targetPurchasePlanType` |

### 6.D 可保留但应加 debug reason

| 位置 | 建议 reason 码 |
|------|----------------|
| `applySourceBreakdownOperationCanonical` | `MATRIX_CANONICAL_SOURCE_BREAKDOWN_DETAIL_TO_BREAKDOWN`（已实现） |
| `applySupplierUnitPriceCanonical` | `MATRIX_CANONICAL_SUPPLIER_AMOUNT_TO_SUPPLIER_UNIT_PRICE`（已实现） |

### 6.E 建议后续删除或改为 validation clarification

| 规则 | 建议 |
|------|------|
| `semanticGoodsRankingToSupplierUnitPrice` 推断 duplicate canonical | **长期**：LLM 必填 detail；推断仅测试兼容 |
| `resolveSlotDetailWanted` 对 GOODS 锚三路的 slot 推断 | **长期**：LLM 必填 detail；推断仅测试兼容，或移出 hot path |
| `PurchaseAnswerPlanBuilder.isGoods*Intent` 平行契约 | **P1B** 再评估矩阵化 |

### 6.F P1A 收口状态（已实现）

| 项 | 状态 |
|----|------|
| `PurchaseFollowUpSlotSignals.unitPriceCueForFollowUpProtocol` | **已删除**（原 0 调用；不参与 canonical） |
| DETAIL→BREAKDOWN | **矩阵 `SOURCE_BREAKDOWN` 行**统一定义；Lexicon / buildFrame / `applySourceBreakdownOperationCanonical` 均读同一行 |
| `CurrentSemanticFrameValidator` | **已去重**：三行仅 `frameMatchesRow` / `operationAccepted`；**无** SOURCE_BREAKDOWN 手写 metric / `sourceFacet=ALL` / wire fallback |
| SOURCE_BREAKDOWN 不匹配 | 返回 `MATRIX_VALIDATION_SOURCE_BREAKDOWN_INCOMPLETE` |
| `PurchaseAnswerPlanBuilder` | **未动**；仍为平行契约，P1B 再评估 |

---

## 7. P1 收敛建议（P1A 已部分落地，见 §6.F）

1. **矩阵 SSOT**：`PurchaseDrilldownMatrix` / `PurchaseDrilldownMatrixRow` 已承载 §2 三类 GOODS 锚行 + canonical 入口（P1A Validator 已读矩阵）。
2. **Registry / Signals** 已共用 `frameMatchesRow` / `followUpSlotMatchesRow` / `slotsInferRowShape`；**P1B**：`PurchaseAnswerPlanBuilder.isGoods*Intent` 可选矩阵化。
3. **禁止**在 `CurrentSemanticFrame` 新增 if/else；新契约 → 先改 **本文档 + prompt**，再改矩阵行。
4. LLM 持续违例 → **frame_validation 澄清**，而非 Java 补丁。

---

## 8. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-20 | P0：GOODS 锚 4 轮矩阵、维度定义、Java 职责、代码审计、P1 收敛建议 |
| 2026-05-20 | §2.4：GOODS 锚链路 `sourceFacet` 口径（ALL 合计 vs 输出分桶 vs SUPPLIER_PURCHASE 边界；仅自采 no-data 规则） |
| 2026-05-20 | **P1A**：删除 `unitPriceCueForFollowUpProtocol`；Validator 三行仅 `frameMatchesRow`；新增 `MATRIX_VALIDATION_SOURCE_BREAKDOWN_INCOMPLETE`；PlanBuilder 留 P1B |
