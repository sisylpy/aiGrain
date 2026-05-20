# D-DISH-PROFIT-DRILLDOWN-MATRIX-CONTRACT-P1 — 菜品毛利下钻矩阵契约

> **目的**：把现网已支持的菜品毛利 structured wire → AnswerPlan、DISH `resultAnchors` 与 DISH 锚追问，整理为 **Harness Engineering 矩阵契约**；对齐采购矩阵分工，防止 Java 退化为语义二次解析器。  
> **范围（P1）**：文档 + `DishProfitDrilldownMatrix` 只读骨架；**不新增**业务能力、Tool、SQL、Composer、时间合同；**不改** `DishProfitAgentNode` 挂载逻辑（P2 再迁）。  
> **相关**：[follow-up-drilldown-matrix.md](./follow-up-drilldown-matrix.md)（D-13.3B）、[dish-profit-domain-capability-matrix.md](./dish-profit-domain-capability-matrix.md)、[purchase-drilldown-matrix-contract.md](./purchase-drilldown-matrix-contract.md)（结构参考）、[dish-sales-domain-capability-matrix.md](./dish-sales-domain-capability-matrix.md)（销量独立域）。

---

## 1. 职责边界

| 层 | 职责 |
|----|------|
| **LLM / semanticSlots** | 输出 `structuredIntentDetailWire`、`queryObject`、`operation`、`metric`、`detailWanted`、`anchorPolicy` 等 |
| **Lexicon canonical** | 别名归一到 canonical wire（如 `dish_gross_profit_rate_ranking_low` → `dish_profit_ranking_low_margin`） |
| **`DishProfitDrilldownMatrix`（P1）** | 登记 wire → planType、emit anchor 白名单、DISH 锚追问行；**不读用户原文、不调 LLM** |
| **`DishProfitAgentNode`（现网执行）** | 仍负责工具快照排序与 AnswerPlan 挂载；P2 再按矩阵 row 驱动 |
| **`BusinessCapabilityRegistry`** | 可选：`phase1PurchaseWithDish()` 提供 `dish.dish_anchor.ingredient_breakdown` 只读匹配；默认消费者仍为 `phase1PurchaseOnly()` |
| **`DishSalesAnswerPlanBuilder`** | **独立域**；销量排行不并入本矩阵 |

---

## 2. 维度定义（Matrix Axes）

| 维度 | 枚举值 | 含义 |
|------|--------|------|
| **anchorType**（上一轮结果锚） | `DISH` / `STORE` / `INGREDIENT` / `NONE` | 来自 `resultAnchors` / TurnMemory；矩阵前提，非 LLM 独立槽 |
| **queryObject** | `DISH` / `INGREDIENT` / `STORE` | 本轮问法主对象 |
| **operation** | `RANKING` / `DETAIL` / `BREAKDOWN` / `OVERVIEW` | 动作类型 |
| **metric** | `GROSS_MARGIN_RATE` / `ACTUAL_COST` / `THEORETICAL_COST` / `COST_GAP` / `INGREDIENT_COST` | 排序或关切度量 |
| **detailWanted** | `INGREDIENT_COST_BREAKDOWN` / `PROFIT_REASON` / `COST_DETAIL` | 追问明细契约键（Harness 别名验收含 `DISH_COST_COMPONENTS` ↔ `INGREDIENT_COST_BREAKDOWN`） |
| **anchorPolicy** | `IGNORE_PREVIOUS_ANCHOR` / `USE_PREVIOUS_ANCHOR` | 是否继承上轮 DISH 锚 |
| **structuredIntentDetailWire** | 如 `dish_profit_ranking_low_margin`、`dish_ingredient_cost_breakdown` | 结构化子口径 wire |

**矩阵匹配键（追问 Registry）**：`priorFramePlanType`（DISH anchor source 白名单）+ 唯一 `DISH` 锚 + 本轮 canonical slots + `detailWanted` + wire → `capabilityId` → `targetDishProfitPlanType`。

---

## 3. First-turn rows（Phase 1 — 现网已挂载）

以下行 **DONE（现网）**：`DishProfitAgentNode#tryAttachDishProfitAnswerPlan` 已挂载对应 planType。

| rowId | structuredIntentDetailWire | targetPlanType | queryObject | operation | metric | emit DISH anchor |
|-------|----------------------------|----------------|-------------|-----------|--------|------------------|
| **DP-R0a** | `dish_profit_ranking_low_margin` | `DISH_LOWEST_MARGIN` | DISH | RANKING | GROSS_MARGIN_RATE | **是** |
| **DP-R0b** | `dish_profit_ranking_high_margin` | `DISH_HIGHEST_MARGIN` | DISH | RANKING | GROSS_MARGIN_RATE | **是** |
| **DP-R0c** | `dish_actual_cost_ranking_high` | `DISH_HIGHEST_ACTUAL_COST` | DISH | RANKING | ACTUAL_COST | **是** |
| **DP-R0d** | `dish_gap_ranking_max` | `DISH_COST_GAP` | DISH | RANKING | COST_GAP | **是** |
| **DP-R0e** | `dish_low_profit_reason` | `DISH_PROFIT_REASON` | DISH | DETAIL | PROFIT_REASON | **是** |
| **DP-R0f** | `dish_theoretical_cost` | `DISH_THEORETICAL_COST` | DISH | DETAIL | THEORETICAL_COST | **是** |
| **DP-R0g** | `dish_actual_outbound_cost` | `DISH_ACTUAL_OUTBOUND_COST` | DISH | DETAIL | ACTUAL_COST | **是** |
| **DP-R0h** | `dish_gross_margin_query` | `DISH_PROFIT_RATE` | DISH | DETAIL | GROSS_MARGIN_RATE | **是** |
| **DP-R0i** | `dish_cost_gap` | `DISH_COST_GAP` | DISH | DETAIL | COST_GAP | **是** |
| **DP-R0j** | `dish_ingredient_cost_breakdown` | `DISH_INGREDIENT_COST_BREAKDOWN` | INGREDIENT | BREAKDOWN | INGREDIENT_COST | **否** |

说明：

- `dish_gap_ranking_max` 与 `dish_cost_gap` 均映射 `DISH_COST_GAP`（排行 max gap vs 单菜差额）。
- `DISH_INGREDIENT_COST_BREAKDOWN` 首轮可直接问（DP-R0j），也可作为 DISH 锚追问（§4）；**均不 emit DISH anchor**。
- **未登记**于首轮矩阵：`dish_profit_overview`（overview + portfolio fallback）、`BUSINESS_DIAGNOSIS_DISH_OVERVIEW`、`AGGREGATED_DISH_PORTFOLIO_FALLBACK`。

Java 常量：`DishProfitDrilldownMatrix#firstTurnRows()` / `#findFirstTurnRowByWire(String)`。

---

## 4. DISH anchor follow-up rows（Phase 1 — 现网已闭环）

| rowId | 场景（示意） | detailWanted | anchor 前提 | anchorPolicy | wire | capabilityId | targetPlanType | 状态 |
|-------|--------------|--------------|-------------|--------------|------|--------------|----------------|------|
| **DP-R1** | 「具体是哪些原料拖累了毛利？」 | `INGREDIENT_COST_BREAKDOWN` | 唯一 `DISH` 锚；prior planType ∈ anchor source 白名单 | `USE_PREVIOUS_ANCHOR` | `dish_ingredient_cost_breakdown` | `dish.dish_anchor.ingredient_breakdown` | `DISH_INGREDIENT_COST_BREAKDOWN` | **DONE**（CaseId：`DISH_LOW_MARGIN_DRILLDOWN_INGREDIENT_COST_2`） |

**priorFramePlanTypes（anchor source 白名单）**：

`DISH_LOWEST_MARGIN`、`DISH_HIGHEST_MARGIN`、`DISH_HIGHEST_ACTUAL_COST`、`DISH_COST_GAP`、`DISH_PROFIT_REASON`、`DISH_THEORETICAL_COST`、`DISH_ACTUAL_OUTBOUND_COST`、`DISH_PROFIT_RATE`

Java：`DishProfitDrilldownMatrix#DISH_ANCHOR_INGREDIENT_BREAKDOWN`、`#findDishAnchorFollowUpRow(...)`、`#followUpSlotMatchesRow(...)`。

Registry（可选）：`BusinessCapabilityRegistry#phase1PurchaseWithDish()` / `#phase1DishOnly()` — **不改变** `#phase1PurchaseOnly()` 默认消费者。

---

## 5. GAP / Future（**不得**标 DONE）

| 项 | 现状 | 矩阵状态 |
|----|------|----------|
| `dish_actual_cost_ranking_low` | Lexicon ✓；AgentNode **无**专用 AnswerPlan | **GAP** |
| `dish_theoretical_cost_ranking_high` / `_low` | Lexicon ✓；AgentNode **无**专用 AnswerPlan | **GAP** |
| 按 **毛利额** 排行 | wire / planType **皆无** | **Future** |
| `STORE` 锚 → 店内菜品毛利 | follow-up-drilldown REUSE_READY | **Future** |
| 原料行 `GOODS` 锚 → 采购追问 | 锚点协议未闭环 | **Future** |
| **DishSales** 追问 | 应走独立 DishSales Matrix | **Future** |
| `CurrentSemanticFrameValidator` dish 形状门禁 | P1 **未接** | **Future（P2+）** |
| `DishProfitAgentNode` wire if 链 → 矩阵驱动挂载 | 执行点仍散落 | **Future（P2）** |

---

## 6. Java 只读 API（P1）

| 方法 | 用途 |
|------|------|
| `findFirstTurnRowByWire(String wire)` | canonical wire → 首轮 row |
| `findFirstTurnRowByPlanType(String planType)` | planType → 首轮 row |
| `targetPlanTypeForWire(String wire)` | wire → targetPlanType |
| `findDishAnchorFollowUpRow(detailWanted, wire, priorFramePlanType)` | 追问 row 查找 |
| `followUpSlotMatchesRow(BusinessFollowUpSlot, row)` | Registry 槽位形状匹配 |
| `isDishAnchorSourcePlanType(String planType)` | 可承接原料追问的 prior planType |
| `emitsDishResultAnchor(String planType)` | 是否 emit DISH anchor |
| `dishAnchorSourcePlanTypes()` | anchor source 白名单 Set |
| `capabilityIdForDishAnchorFollowUp(String detailWanted)` | 追问 capabilityId |

`DishProfitAnswerPlan#isDishDrilldownAnchorSourcePlanType` / `#planTypeEmitsResultAnchor` **委托**至矩阵（行为与 P0 白名单一致）。

---

## 7. 相关索引

| 资源 | 说明 |
|------|------|
| [follow-up-action-protocol.md](./follow-up-action-protocol.md) | `followUpAction` / `detailWanted` |
| [result-anchor-protocol.md](./result-anchor-protocol.md) | DISH anchor 字段 |
| [business-capability-registry.md](./business-capability-registry.md) | Registry 分工 |
| `DishProfitDrilldownMatrix.java` | P1 矩阵实现 |
| `DishProfitAgentNode.java` | 现网执行挂载（P2 迁移候选） |
