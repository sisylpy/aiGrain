# Skill：Supplier Drilldown（供货商排行 → 商品明细）

**对应协议版本**：D-13.1（封版） · CaseId `PURCHASE_SUPPLIER_RANKING_DRILLDOWN_GOODS_UNIT_PRICE_3`  

**一键 probe**：`scripts/harness/probe-supplier-drilldown.sh`

---

## 能处理的问题

1. **供应商采购金额排行**（默认本月或语义给定时间窗下的供货商渠道排行）。
2. **上一轮 Top 供应商的商品明细**（继承上一轮的 **供货商对象** 与 **时间口径**，切换为 goods query）。
3. **商品单价明细**（在用户明确要求单价/明细时，`detailWanted=GOODS_UNIT_PRICE`；若工具行数据暂无单价字段，由 Composer 宣读 Plan 行并声明「暂缺单价」，**不重算**。）

---

## 输入协议（摘要 / Resolver 层）

| 字段 | 含义 |
|------|------|
| `timeWindow` | `AiResolvedTimeWindow`：起止日、`INHERITED_PREVIOUS` 等与 `effectiveTimeWindowSource` 对齐。 |
| `orgScope` | `AiResolvedOrgScope` / `AiResolvedDataScope`：集团 / 门店可见范围等。 |
| `purchaseSourceType` | 如 `ALL`、`SUPPLIER_PURCHASE`、`SELF_PURCHASE`；下钻 goods 时在供货商场景归一为 `SUPPLIER_PURCHASE`。 |
| `followUpAction` | 如 `OBJECT_DRILLDOWN`（对象下钻）；纯「上个月呢」为时间承接，不与此混用。 |
| `followUpTargetEntity` | `followUpTargetEntityType` + `followUpTargetEntityName`（如 `SUPPLIER` + 具体供货商名）。 |
| `detailWanted` | 映射字段 `followUpDetailWanted`，如 `GOODS_UNIT_PRICE`。 |
| `resultAnchors` | 上一轮 `PurchaseAnswerPlan` 产出的 `AiResultAnchor[]`，含 `entityType`、`entityName`、`sourcePlanType`、`rank` 等。 |

Wire 级意图见摘要 **`structuredIntentDetailWire`**（人类可读枚举见 `structuredIntentDetail`）。

---

## 可调用 Tool / Plan

| 层级 | 标识 | 说明 |
|------|------|------|
| Tool（业务） | `purchase_overview` | 采购侧主查询入口；下钻不随意新增 Tool。 |
| 结构化意图（wire） | `purchase_source_goods_query` | 供货商渠道商品明细查询（与 lexicon 常量一致）。 |
| AnswerPlan 类型 | `PURCHASE_SUPPLIER_AMOUNT_RANKING` | 供货商金额排行。 |
| AnswerPlan 类型 | `PURCHASE_SUPPLIER_GOODS_DETAIL` | Top 供货商商品明细（宣读 focus/secondary 行，不重算）。 |

Planner / Builder 选型须与 **`PurchaseAnswerPlanBuilder.resolvePlanType`** 一致；Replay 探针 **`AiHarnessReplayContextProbes.resolvePurchasePlanType`** 与之对齐。

---

## 输出

| 输出 | 说明 |
|------|------|
| `PurchaseAnswerPlan` | 含 `planType`、`focusRows` / `secondaryRows`、`resultAnchors`（由已有 Tool 结果映射，不重算 SQL）。 |
| `resultAnchors` | 供下一轮 Resolver 识别 `SUPPLIER` + `PURCHASE_SUPPLIER_AMOUNT_RANKING` 等。 |
| Harness debug | 顶层：`followUpAction`、`followUpDetailWanted`、`structuredIntentDetailWire`、`purchaseSourceType`、`purchaseAnswerPlanType`、`harnessReplayPurchaseAnswerPlanType`、`resultAnchorsCount` / `previousTurnSummary` 等（见 `AiHarnessResolvedContextSummarizer`）。 |

---

## 禁止

- **不让 LLM 自由写 SQL**；业务口径以既有 Mapper / Tool 为准。
- **不在 Composer 硬编码整条业务答案**；确定性宣读仅基于 Plan 已有字段与 Resolver 给出的对象名/时间话术。
- **不重新计算业务数**（不在 Harness / Composer 层另算金额、单价排行等）。

---

## 后续（非本 Skill）

- **D-13.2 STORE anchor**：「哪个门店问题最大 → 具体是什么问题？」等在 **D-13.1 全量 replay 稳定** 后再拆协议与 Skill 文档。
