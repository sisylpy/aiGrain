# D-13 Follow-up Drilldown Matrix（追问下钻映射表）

> **目的**：用 **`followUpTargetEntityType`（锚点实体）+ `followUpDetailWanted`（要的明细类型）** 统一映射到 **既有 Tool / Agent / AnswerPlan**，避免每种自然语言问法单独造一条下钻链路或新 Tool。  
> **协议背景**：`followUpAction`、`detailWanted`、Resolver 规则与 Replay 见 **[follow-up-action-protocol.md](./follow-up-action-protocol.md)**；锚点字段见 **[result-anchor-protocol.md](./result-anchor-protocol.md)**。

---

## 1. 已实现行（DONE / PHASE_1_DONE）

| Milestone | targetEntityType | detailWanted | example user question（示意） | preferred existing tool / agent | output AnswerPlan（主类型） | implementation status |
|-----------|------------------|--------------|-------------------------------|----------------------------------|-----------------------------|----------------------|
| **D-13.1** | `SUPPLIER` | `GOODS_UNIT_PRICE` | 「采购了哪些商品？单价分别是多少？」（承接 Top 供货商 + 时间窗） | **`PurchaseAgent`** 专线；**`purchase_overview`**（`PURCHASE_OVERVIEW`）；结构化 wire **`purchase_source_goods_query`** | `PurchaseAnswerPlan` **`TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL`** | **DONE**（CaseId：`PURCHASE_SUPPLIER_RANKING_DRILLDOWN_GOODS_UNIT_PRICE_3`） |
| **D-13.2** | `STORE` | `STORE_RISK_REASONS` | 「具体是什么问题？」（承接门店优先级排行里的 Top 门店） | **经营诊断**路径 **`BusinessDiagnosisAgentV1`**；多 Agent 场景下与 **`PURCHASE_OVERVIEW` / `STOCK_REDUCE_QUERY` / `DISH_PROFIT_ANALYSIS`** 等已挂载计划协同；结构化 wire **`store_risk_reasons_drilldown`** | **`DiagnosisPlan`**（门店原因下钻问法类型见 `DIAGNOSIS_QUESTION_STORE_RISK_REASONS`） | **DONE**（CaseId：`BUSINESS_STORE_PRIORITY_DRILLDOWN_REASONS_3`） |
| **D-13.3B** | `DISH` | `INGREDIENT_COST_BREAKDOWN`（等价别名验收含 `DISH_COST_COMPONENTS`） | 「具体是哪些原料拖累了毛利？」（承接低毛利排行中的菜品） | **`DishProfitAgent`** 专线；**`dish_profit_analysis`** + **`dish_ingredient_cost_breakdown`**；结构化 wire **`dish_ingredient_cost_breakdown`** | `DishProfitAnswerPlan` **`TYPE_DISH_INGREDIENT_COST_BREAKDOWN`** | **DONE**（CaseId：`DISH_LOW_MARGIN_DRILLDOWN_INGREDIENT_COST_2`） |
| **D-13.4（Phase 1）** | `GOODS` | `SUPPLIER_UNIT_PRICE` | 「主要是哪些供货商供的？各供货商单价多少？」（承接 GOODS 金额/排行锚点，**商品为锚**） | **`PurchaseAgent`** 专线；**`purchase_overview`**；结构化 **`purchase_source_goods_query`** + **`purchaseSourceType=SUPPLIER_PURCHASE`**；`followUpDetailWanted=SUPPLIER_UNIT_PRICE`，锚点字段对齐 **`result-anchor-protocol`** | `PurchaseAnswerPlan` **`TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL`**（供货商拆行 + 无数据原因码 + 自采口径轻量探针与 Harness 摘要摊平） | **PHASE_1_DONE**（`overallPass=true`；CaseId：`PURCHASE_GOODS_RANKING_DRILLDOWN_SUPPLIER_UNIT_PRICE_2`） |

说明：

- **`followUpAction`**：D-13.1 / D-13.3B 的 Harness 验收中 `OBJECT_DRILLDOWN` 与 `DETAIL_DRILLDOWN` 可等价（见 follow-up 文档与内置预期）；**D-13.4 Phase 1** 第二轮验收为 **`OBJECT_DRILLDOWN`** + **`GOODS`** + **`SUPPLIER_UNIT_PRICE`**。
- **D-13.2** 第三轮显式期望 **`STORE_RISK_REASONS`**（与内置 `AiHarnessBuiltinCases.expectationsBusinessStorePriorityDrilldownReasons3` 一致）。

### 1.1 锚点与口径：无数据时的公共表述原则

当 **`followUpTargetEntityType` 等锚点已指向具体对象**（例如 **`GOODS`** 且已有商品名或 `disGoodsId`），但 **当前所选业务口径下查不到明细** 时：

1. **不得**将情况表述成「对象不存在」或与之等价的误导（锚点存在 ≠ 对象不存在）。
2. **不得**为「补数据」而 **自动切换** `purchaseSourceType` 或其它口径；用户仍停留在所选口径上，系统只陈述 **当前口径无记录 / 无明细**（例如稳定原因码 **`NO_SUPPLIER_PURCHASE_FOR_FOCUSED_GOODS`** 及对应 Composer 文案）。
3. **应当**说明 **当前口径下暂无数据**；在具备 **事实证据**（如对 **其它口径** 的轻量探针命中）时，**可以**提示用户 **可到该口径继续追问**（例如自采单价），提示为 **可选下一步**，而非 silently 改线。

---

## 2. 复用导向行（REUSE_READY / LATER）

下列行 **不新增专用下钻 Tool**，优先通过 **同一采购明细能力**、**既有 Agent + 现有 Tool** 或 **扩展 planType / wire / Resolver 条件** 落地。

| targetEntityType | detailWanted（建议枚举名） | example user question（示意） | preferred existing tool / agent | output AnswerPlan（方向） | implementation status |
|------------------|--------------------------|-------------------------------|----------------------------------|---------------------------|----------------------|
| `GOODS` | `SUPPLIER_UNIT_PRICE` | （与 §1 **D-13.4 Phase 1** 同一能力） | 见 **§1 已实现行**；**PHASE_1_DONE**，CaseId：`PURCHASE_GOODS_RANKING_DRILLDOWN_SUPPLIER_UNIT_PRICE_2` | `TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL` | **DONE / PHASE_1_DONE**（本节不重复展开） |
| `SUPPLIER` | `GOODS_UNIT_PRICE` | （同 D-13.1） | 同上 | `TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL` | **DONE**（见上表） |
| `STORE` | `PURCHASE_SUMMARY` / `PURCHASE_DETAIL` | 「这家店采购金额多少？/ 采购具体问题是什么？」 | **复用 `PurchaseAgent` + `PURCHASE_OVERVIEW`** | `PurchaseAnswerPlan` | **REUSE_READY**（需 STORE 锚 + 路径与清单对齐） |
| `STORE` | `STOCK_REDUCE_SUMMARY` / `STOCK_REDUCE_DETAIL` | 「这家店出库/核销怎么样？」 | **复用出库专线 + `STOCK_REDUCE_QUERY`**（StockReduceAgent / 现有出库图） | `StockReduceAnswerPlan` | **REUSE_READY** |
| `STORE` | `DISH_PROFIT_SUMMARY` / `DISH_MARGIN_RANKING` | 「这家店菜品毛利怎么样？」 | **复用 `DishProfitAgent` + `DISH_PROFIT_ANALYSIS`** | `DishProfitAnswerPlan` | **REUSE_READY** |
| `DISH` | `INGREDIENT_COST_BREAKDOWN` | （同 D-13.3B） | `DISH_PROFIT_ANALYSIS` + `DISH_INGREDIENT_COST_BREAKDOWN` | `TYPE_DISH_INGREDIENT_COST_BREAKDOWN` | **DONE** |
| `GOODS` | `INGREDIENT_LINK`（占位） | （从菜品原料行跳到「商品采购/出库」追问） | **后续**：原料明细行产出 **`GOODS` 锚点** 后，**复用** 上表 `GOODS` + 采购/出库 `detailWanted` | 采购或出库 AnswerPlan | **LATER**（锚点与 TurnMemory 契约需单独立项） |

---

## 3. 与「少造 Tool」强相关的复用原则

1. **供应商 → 商品/单价** 与 **商品 → 供应商/单价**：应复用 **同一套采购明细查询能力**（`purchase_overview` / `PurchaseAnswerPlanBuilder` 族），仅切换 **锚点实体类型**与 **查询切片**，不各造一个 one-off Tool。
2. **门店 → 采购问题**：复用 **`PurchaseAgent` + `PURCHASE_OVERVIEW`**。
3. **门店 → 出库/核销问题**：复用 **StockReduce 专线 + `STOCK_REDUCE_QUERY`**。
4. **门店 → 菜品毛利问题**：复用 **`DishProfitAgent` + `DISH_PROFIT_ANALYSIS`**。
5. **菜品原料行 → 商品纵深**：设计上原料行应能落 **`GOODS` 实体锚**（或等价可解析 ID），以便后续追问 **复用采购/出库** 行；**不先加新下钻 Tool**，先补锚点与矩阵行。
6. **`detailWanted` 枚举**：新增取值前须在本矩阵登记一行，并标注 **REUSE_READY** 拟复用的 Tool/Plan，避免语义漂移。
7. **锚点存在但当前口径无数据**：遵守 **§1.1**，避免「对象不存在」表述与自动切口径；有跨口径证据时再提示可选追问路径。

---

## 4. 结论（流程纪律）

- **后续每新增一种下钻意图**（新问法、新实体组合、新 wire）：
  1. **先查本矩阵**；
  2. **能映射到已有 Tool + Agent + AnswerPlan 变体** → 只扩 Resolver / 语义 / planType，**不新加 Tool**；
  3. 仅当矩阵中明确为 **`NEED_TOOL`**（本表暂未单列；新增前需在矩阵中增补一行并论证无法复用）时，才引入新 Tool。
- 文档维护：实现状态从 **REUSE_READY** → **DONE**（或分阶段里程碑下的 **`PHASE_1_DONE`** 等）时，补上 **Milestone / CaseId / 主要 wire**，并与 **`follow-up-action-protocol.md`**、**`result-anchor-protocol.md`** 交叉引用。

---

## 5. 相关索引

| 资源 | 说明 |
|------|------|
| [follow-up-action-protocol.md](./follow-up-action-protocol.md) | D-13 动作与 `detailWanted` 约定 |
| [result-anchor-protocol.md](./result-anchor-protocol.md) | `SUPPLIER` / `STORE` / `DISH` / `GOODS` 锚点 |
| [protocols/d13-1-supplier-drilldown-closure.md](./protocols/d13-1-supplier-drilldown-closure.md) | D-13.1 封版 |
| [skills/supplier-drilldown-skill.md](./skills/supplier-drilldown-skill.md) | D-13.t Skill 样板（供货商 → 商品单价） |
