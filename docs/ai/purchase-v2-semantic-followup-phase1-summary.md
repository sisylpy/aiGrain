# 采购阶段 1A 收口说明 — V2 semanticSlots / Follow-up / Registry

> **维护目的**：固定「阶段 1A」验收边界与已通过语义链路的对照表，避免与执行层（Tool / SQL / Composer）问题混写。  
> **相关索引**：[`business-capability-registry.md`](./business-capability-registry.md)、[`follow-up-drilldown-matrix.md`](./follow-up-drilldown-matrix.md)、[`follow-up-action-protocol.md`](./follow-up-action-protocol.md)、[`result-anchor-protocol.md`](./result-anchor-protocol.md)。

---

## 1. 阶段定位（1A）

| 在范围内 | 不在范围内（后续阶段） |
|----------|------------------------|
| V2 语义层输出、`semanticSlots` 与前帧合并 | `purchase_overview` 返回行数、SQL 是否命中、明细是否为空是否「业务正确」 |
| `CurrentSemanticFrame`、帧校验 **`CurrentSemanticFrameValidator`** | AnswerPlan **Composer** 文案、前台最终展示 |
| **`anchorPolicy`** 与 `resultAnchorsSummary` / 无实体锚边界 | 前台是否按 **`SOURCE_BREAKDOWN`** 等结构消费子字段 |
| **`matchedCapabilityId`**、**`purchaseAnswerPlanType`**（及 Harness 摘要中与上列一致的探针） | `metric.rankingType` 旧字段瘦身（仅记债，本阶段不删） |

**验收载体（示例）**：单域采购 Graph 与专项 Case（如 `PURCHASE_SUPPLIER_RANKING_DRILLDOWN_GOODS_UNIT_PRICE_3`、`PURCHASE_GOODS_RANKING_DRILLDOWN_SUPPLIER_UNIT_PRICE_2`、`PURCHASE_GOODS_RANKING_SOURCE_BREAKDOWN_2`、供货商渠道 overview→明细 Case、`V2_SEMANTIC_MAINLINE_CORE_10` 中与采购/多域切换相关的 Resolver 探针等 —— **1A 只读语义 + Registry + Plan 类型，不断言行级事实是否饱满**。

---

## 2. 已通过链路（对照表）

下列均为 **1A 语义 / Registry / `purchaseAnswerPlanType` 层面**已跑通或设计锁定且与 Harness 预期对齐的链路。**`structuredIntentDetail`** 列与 **Lexicon 常量**一致时可用蛇形字面（如 `purchase_source_goods_query` ↔ `STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY`）。

### 2.1 供货商金额排行 → 时间接力「上个月呢」

| 轮次 | 用户问题（示意） | 关键 `semanticSlots`（概括） | `anchorPolicy` | `structuredIntentDetailWire` / 顶层 `structuredIntentDetail` | `detailWanted` | `matchedCapabilityId`（若适用） | `purchaseAnswerPlanType` |
|------|------------------|-------------------------------|----------------|------------------------------------------------------------------|----------------|----------------------------------|---------------------------|
| 1 | 哪个供货商订货金额最高？/ 本月供货商采购排行？ | `queryObject=SUPPLIER`，`operation=RANKING`，`metric=PURCHASE_AMOUNT`，`sourceFacet=SUPPLIER_PURCHASE` | 独立问法：`IGNORE_PREVIOUS_ANCHOR` | `supplier_amount_ranking` | — | — | `PURCHASE_SUPPLIER_AMOUNT_RANKING` |
| 2 | 上个月呢？ | 同上排行语义；时间窗平移 | **`USE_PREVIOUS_ANCHOR`**（承接排行语境 + 实体维度不变） | 仍为 **`supplier_amount_ranking`** | — | — | `PURCHASE_SUPPLIER_AMOUNT_RANKING` |

**参考**：`follow-up-action-protocol.md`（「上个月呢」不降级 wire）；Harness：`PURCHASE_SUPPLIER_RANKING_DRILLDOWN_GOODS_UNIT_PRICE_3` 前两轮时间窗与排行类型。

---

### 2.2 商品采购金额排行（供应商侧 Top 商品）

| 轮次 | 用户问题（示意） | 关键 `semanticSlots` | `anchorPolicy` | `structuredIntentDetailWire` | `detailWanted` | `matchedCapabilityId` | `purchaseAnswerPlanType` |
|------|------------------|---------------------|----------------|------------------------------|----------------|----------------------|---------------------------|
| 1 | 本月采购金额最高的商品？/ 商品采购金额排行 | `queryObject=GOODS`，`operation=RANKING`，`metric=PURCHASE_AMOUNT`，`sourceFacet=ALL`（句内未收窄时） | `IGNORE_PREVIOUS_ANCHOR` | **`purchase_goods_amount_ranking`** | — | — | `PURCHASE_GOODS_AMOUNT_RANKING` |

（后续可接 D-13.4 供货商单价追问，见 2.5 的商品锚分支；排行单独成条时以上表为准。）

---

### 2.3 营业额语境 →「那采购呢」（域切换 + 继承时间与范围）

| 轮次 | 用户问题（示意） | 关键 `semanticSlots` / 路径 | `anchorPolicy` | `structuredIntentDetailWire` | `detailWanted` | `matchedCapabilityId` | `purchaseAnswerPlanType` |
|------|------------------|----------------------------|----------------|------------------------------|----------------|----------------------|---------------------------|
| 1 | 双店营业额排行 / 营收排行问法 | **intent/path**：`REVENUE_OVERVIEW` · `revenue_overview_path`；采购不适用 | — | `revenue_store_amount_ranking` 等 | — | — | （`DailyRevenueAnswerPlan`，非采购 Registry） |
| 2 | **那采购呢？** | **切换** `PURCHASE_OVERVIEW` · `purchase_overview_path`；**scope / time** 多继承上一轮 | 按 Resolver 合并结果（常与 **USE**/继承一致） | **`purchase_store_amount_ranking`**（与双店 scope 对齐时） | — | — | **`PURCHASE_STORE_AMOUNT_RANKING`** |

**参考**：`business-question-routing-d2-design.md` §5 域切换；Harness：`V2_SEMANTIC_MAINLINE_CORE_10` 中 **r3→r4**（营收门店排行 → 采购门店排行）为同构 Resolver 探针（非字面「那采购呢」一句，但 **域切换 + 继承** 与产品设计一致）。

---

### 2.4 商品锚点 → 来源拆桶（`SOURCE_BREAKDOWN`）

| 轮次 | 用户问题（示意） | 关键 `semanticSlots` | `anchorPolicy` | `structuredIntentDetailWire` | `detailWanted` | `matchedCapabilityId` | `purchaseAnswerPlanType` |
|------|------------------|---------------------|----------------|------------------------------|----------------|----------------------|---------------------------|
| 1 | 商品采购金额排行（产出 **GOODS#** 锚） | 同 **2.2** | `IGNORE_PREVIOUS_ANCHOR` | `purchase_goods_amount_ranking` | — | — | `PURCHASE_GOODS_AMOUNT_RANKING` |
| 2 | 自采和供货商各多少？/ 来源拆桶类问法 | `queryObject=GOODS`，`operation=BREAKDOWN`，`sourceFacet=ALL`，承接 GOODS 锚 | **`USE_PREVIOUS_ANCHOR`** | **`purchase_source_goods_query`** | **`SOURCE_BREAKDOWN`** | **`purchase.goods_anchor.source_breakdown`** | **`PURCHASE_GOODS_SOURCE_BREAKDOWN`** |

**参考**：Harness `PURCHASE_GOODS_RANKING_SOURCE_BREAKDOWN_2`。

---

### 2.5 供应商锚点 → 商品明细 / 单价（`GOODS_DETAIL` / `GOODS_UNIT_PRICE`）

| 轮次 | 用户问题（示意） | 关键 `semanticSlots` | `anchorPolicy` | `structuredIntentDetailWire` | `detailWanted` | `matchedCapabilityId` | `purchaseAnswerPlanType` |
|------|------------------|---------------------|----------------|------------------------------|----------------|----------------------|---------------------------|
| 1 | 供货商金额排行（**SUPPLIER#** 或 Top 供货商语境） | `queryObject=SUPPLIER`，`operation=RANKING`，`supplier_amount_ranking` | 首轮：`IGNORE_PREVIOUS_ANCHOR` | `supplier_amount_ranking` | — | — | `PURCHASE_SUPPLIER_AMOUNT_RANKING` |
| 2 | 采购了哪些商品？单价分别是多少？ | `queryObject=GOODS`，`operation=DETAIL`；承接 **SUPPLIER** 锚 | **`USE_PREVIOUS_ANCHOR`** | **`purchase_source_goods_query`** | **`GOODS_DETAIL`** 或 **`GOODS_UNIT_PRICE`**（按句意） | **`purchase.supplier_anchor.goods_detail`** | **`PURCHASE_SUPPLIER_GOODS_DETAIL`** |

**参考**：D-13.1；Harness `PURCHASE_SUPPLIER_RANKING_DRILLDOWN_GOODS_UNIT_PRICE_3` 第三轮。

**商品为锚、问各供应商单价**（D-13.4）：`detailWanted=**SUPPLIER_UNIT_PRICE**`，`matchedCapabilityId=**purchase.goods_anchor.supplier_unit_price**`，计划类型仍为 **`PURCHASE_SUPPLIER_GOODS_DETAIL`** —— 与上表同一 Plan 族，锚维度不同。

---

### 2.6 供货商渠道无实体锚 →「定了什么东西？」（渠道 goods detail）

| 轮次 | 用户问题（示意） | 关键 `semanticSlots` | `anchorPolicy` | `structuredIntentDetailWire` | `detailWanted` | `matchedCapabilityId` | `purchaseAnswerPlanType` |
|------|------------------|---------------------|----------------|------------------------------|----------------|----------------------|---------------------------|
| 1 | 上个月在供货商订货金额多少？ | `queryObject=SUPPLIER`，`operation=SUMMARY`，`sourceFacet=SUPPLIER_PURCHASE` | 独立问法：`IGNORE_PREVIOUS_ANCHOR` | **`purchase_source_amount_query`** | — | — | **`PURCHASE_SUPPLIER_OVERVIEW`** |
| 2 | 定了什么东西？/ 订了哪些商品？ | `queryObject=GOODS`，`operation=DETAIL`，`metric=PURCHASE_AMOUNT`，`sourceFacet=SUPPLIER_PURCHASE` | **`IGNORE_PREVIOUS_ANCHOR`**（**无** `SUPPLIER#`/`GOODS#` 不得假 `USE`） | **`purchase_source_goods_query`** | **`GOODS_DETAIL`** | **`purchase.supplier_channel.goods_detail`** | **`PURCHASE_SUPPLIER_GOODS_DETAIL`** |

**参考**：`BusinessCapabilityRegistry` 中 **`purchase.supplier_channel.goods_detail`**；Harness：`expectationsPurchaseSupplierChannelOverviewGoodsDetail2`（对应 `STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY` → `STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY`）。

---

## 3. 当前不处理的遗留问题（执行 / 产品层）

以下内容 **不改变 1A「语义 + Registry + Plan 类型已通过」的结论**；不得在 1A 文档中写成「业务上 0 行即正确」。

| 现象 | 归属 |
|------|------|
| `PURCHASE_SUPPLIER_OVERVIEW` 上 **`supplierPurchaseAmount` 有值**，但紧随的 **`PURCHASE_SUPPLIER_GOODS_DETAIL` 下行数为 0**、Harness 上出现 **`TOOL_PAYLOAD_EMPTY`** 等 | **执行层**：Tool 入参分支、`purchaseOverview` drilldown 激活条件、与主查询时间窗/来源一致性等 —— **待执行层排查**，非 V2/Registry 验收项。 |
| 真实前台 run 上语义与 **`SOURCE_BREAKDOWN`** 已对齐，但 **回答未按拆桶结构消费** | **AnswerPlan / Composer / 前台** 联调。 |
| 渠道无锚场景下，文案仍出现「上文锚定的供货商」等 **与事实不符** 的指代 | **Composer 表达阶段** 修正。 |
| 顶层 **`metric.rankingType`** 与槽位并存、后续字段瘦身 | 技术债登记；**不在阶段 1A 删除**。 |

---

## 4. 下一阶段建议

| 阶段 | 范围建议 |
|------|----------|
| **1B** | **出库 / 核销 / 废弃 / 损失** 等 **`RESOLVED_CONTEXT_ONLY`** 语义层最小矩阵（intent / path / `semanticSlots` / follow-up 与 Registry 预占位）。 |
| **1C** | **菜品毛利** **`RESOLVED_CONTEXT_ONLY`** 语义层最小矩阵（与现有 `DISH_PROFIT` path 对齐）。 |
| **2+** | 回到 **采购执行层**：Tool、SQL、明细行、AnswerPlan 行集、Composer 与高优前台缺陷。 |

---

## 5. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-18 | 初版：阶段 1A 边界、六类已通过链路表、遗留问题与 1B/1C/2+ 建议。 |
