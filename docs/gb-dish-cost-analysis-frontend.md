# 菜品成本 / 出库分析 — 接口说明（新结构）

路径不变：`POST /gbDishCostAnalysis/report`。前端可全新对接，**不再使用**旧的 `mode=usage|purchase` 及旧字段名。

---

## 1. 请求参数（表单 POST）

| 字段 | 必填 | 说明 |
|------|------|------|
| `startDate` | 是 | `yyyy-MM-dd` |
| `stopDate` | 是 | `yyyy-MM-dd` |
| `disId` | 是 | 批发商 ID |
| `searchDepId` | 否 | 部门；`-1` 或省略：按门店类型汇总（与成本统计一致） |
| `depFatherId` | 否 | 与 `searchDepId=-1` 联用：限定子部门父级 |
| **`reportKind`** | 否 | **`salesDish`**（默认）— 以**销售菜品**为主；**`outboundQty`** — 以**出库数量**（分销商商品）为主 |

`reportKind` 大小写不敏感；非法值返回 `code != 0`。

---

## 2. 响应 `data` 公共字段

| 字段 | 说明 |
|------|------|
| `reportKind` | 本次使用的枚举：`salesDish` 或 `outboundQty` |
| `startDate` / `stopDate` / `disId` / `searchDepId` / `depFatherId` | 与请求一致 |
| **`salesDishRows`** | `reportKind=salesDish` 时为数组；`outboundQty` 时为 `null` |
| **`outboundGoodsRows`** | `reportKind=outboundQty` 时为数组；`salesDish` 时为 `null` |

---

## 3. `reportKind = salesDish`（以销售菜品为主）

主表：**`salesDishRows[]`**，一行一道菜。

### 3.1 `salesDishRows[]` 菜品行

| 字段 | 类型 | 说明 |
|------|------|------|
| `foodId` | number | 菜品 ID |
| `foodName` | string | 菜品名称 |
| `soldPortions` | string | 本期销售份数 |
| `theoryInboundQtyTotal` | string | **按配方推算**的理论用量合计：`gb_distributer_food_goods`（`gb_dfg_food_id`=本菜）每条有效配方的 **`gb_dfg_goods_amount`（单份）× `soldPortions`** 再**逐行相加**（斤、升等不同单位在数值上直接相加，为粗算展示） |
| **`actualInboundQtyTotal`** | string | **销售子表实际原料用量合计**：`gb_dep_food_sales`（`gb_dfs_food_id`=本菜、本期、部门范围）关联 **`gb_dep_food_goods_sales`**，对各 `gb_dfgs_dis_goods_id` 的 **`gb_dfgs_goods_amount`** 汇总后再**按料加总**（与 `ingredientRows[].theoryQtyFromSales` 逐料之和一致；**不是**生产成本出库分摊 `outboundAllocatedQtyTotal`） |
| `outboundAllocatedQtyTotal` | string | 生产成本出库按销售子表理论量比例摊到本菜的用量合计 |
| `theoryCostAmount` | string | 理论成本（元，扣库均价推算） |
| `actualCostAmount` | string | 实际成本（元） |
| `diffCostAmount` | string | 实际 − 理论（元） |
| `absDiffCostAmountSum` | string | 按原料绝对差异金额之和（排序用 `sortKey` 同源） |
| **`bottle`** | object | **瓶颈汇总**（原分散在顶层的份数、原料 id、摊销出库、主档鲜品/规格等，见下表） |
| `sortKey` | string | 排序权重（与 `absDiffCostAmountSum` 或偏差逻辑一致） |
| `hint` | string | 经营提示 |
| **`ingredientRows`** | array | **本菜配料**（按配方中该料**首次出现**顺序；同一 `disGoodsId` 多行配方合并为一行） |

#### `salesDishRows[].bottle`（瓶颈）

| 字段 | 类型 | 说明 |
|------|------|------|
| `soldPortions` | string | 本菜本期实销份数（与菜品行 `soldPortions` 相同） |
| `supportedPortions` | string | 整菜在「有生产成本出库」的原料上可支撑份数取 min（原 `supportedPortionsBottleneck`） |
| `disGoodsId` | number / null | 瓶颈分销商商品 ID（原 `bottleneckGoodsId`） |
| `goodsName` | string / null | 瓶颈原料名称（与 `ingredientRows` 同 `disGoodsId` 的合并名称一致；无瓶颈时为 `null`） |
| `theoryQtyFromSales` | string / null | 瓶颈料在本菜销售子表中的用量（与对应配料行 `theoryQtyFromSales` 一致；无瓶颈时为 `null`） |
| `theoryOutboundQtyByRecipe` | string / null | 瓶颈料按配方推算用量：实销×合并单份用量（与对应配料行一致；无瓶颈时为 `null`） |
| `theorySalesCostAmount` | string / null | **仅瓶颈料** `disGoodsId`：子表用量×均价（元），与同料 `ingredientRows[].salesIngredientCostAmount` 一致；无瓶颈为 `null` |
| `recipeSalesCostAmount` | string / null | **仅瓶颈料**：实销×配方用量×均价（元），与同料 `recipeTheoryIngredientCostAmount` 一致；无瓶颈为 `null` |
| `outboundAllocatedCostAmount` | string / null | **仅瓶颈料**：摊给出库×均价（元），与同料 `outboundAllocatedIngredientCostAmount` 一致；无瓶颈为 `null` |
| `soldVsSupportedPortionDiff` | string | 实销份数 − `supportedPortions`（可正可负） |
| `recipeSalesVsOutboundCostDiff` | string / null | 瓶颈料：`recipeSalesCostAmount` − `outboundAllocatedCostAmount`（元）；无瓶颈为 `null` |
| `theoryQtyFromSalesVsOutboundAllocDiff` | string / null | 瓶颈料：**子表用量 − 摊销出库斤数**（`theoryQtyFromSales` 与 `outboundAllocatedQty` 数值差，`plainQty` 尺度）；无瓶颈为 `null` |
| `recipeTheoryQtyVsOutboundAllocDiff` | string / null | 瓶颈料：**配方推算用量 − 摊销出库斤数**；无瓶颈为 `null` |
| `outboundAllocatedQty` | string / null | 瓶颈料摊给本菜的出库数量（原 `bottleneckOutboundAllocatedQty`） |
| `gbDgGoodsStandardname` 等 | 同配料行 | 瓶颈料在 `gb_distributer_goods` 的主档字段，与 `ingredientRows` 中 `putDisGoodsProfileFields` 一致（含 `gbDgGoodsFileImg`）；无瓶颈或无主档时为 `null` |

白话说明见响应 `bossColumnHintsZh.bottle`。

### 3.2 `ingredientRows[]`（配料行）

| 字段 | 说明 |
|------|------|
| `disGoodsId` | 分销商商品 ID |
| `goodsName` | 名称 |
| `recipeUnitPerDish` | 本菜该料单份配方用量合计（多行同料则 u 相加） |
| `theoryQtyFromSales` | **`gb_dep_food_goods_sales`** 中本菜、本料用量汇总（多条销售子表行相加；入库数量用 `coerceDecimal` 解析，避免 Double 误差） |
| **`theoryOutboundQtyByRecipe`** | **实销份数 × `recipeUnitPerDish`**（按配方推算的本菜该料理论用量，便于与 `theoryQtyFromSales` 对照；若子表只录了「1」而配方推算为「1.6」，此处会体现配方侧） |
| `outboundAllocatedQty` | 该料上本菜分得的出库量（无本期该料出库时为 `"0"`） |
| `supportedPortionsThisGood` | 仅该料维度可支撑份数（多条同料取 min；无出库时可 `"0"`） |
| `salesIngredientCostAmount` / `recipeTheoryIngredientCostAmount` / `outboundAllocatedIngredientCostAmount` | string | 元，子表用量×均价、配方推算×均价、摊销出库×均价 |
| `recipeSalesVsOutboundCostDiff` | string | `recipeTheoryIngredientCostAmount` − `outboundAllocatedIngredientCostAmount`（元） |
| `soldVsSupportedPortionDiff` | string | 本菜实销份数 − 本行 `supportedPortionsThisGood`（可正可负） |
| `recipeTheoryQtyVsOutboundAllocDiff` | string | `theoryOutboundQtyByRecipe` − `outboundAllocatedQty`（`plainQty`） |
| **`gbDgGoodsStandardname`** | **`gb_distributer_goods`**：规格名称（字符串） |
| **`gbDgControlFresh`** | 是否管控鲜度（整数，与商品主档一致） |
| **`gbDgFreshWarnHour`** | 鲜品预警时长（字符串，库字段原样） |
| **`gbDgFreshWasteHour`** | 鲜品报废/浪费相关时长（字符串） |
| **`gbDgGoodsStandardWeight`** | 商品标准重量说明（字符串） |

以上五个字段来自分销商商品主档；无对应主档时均为 `null`。

---

## 4. `reportKind = outboundQty`（以出库数量为主）

主表：**`outboundGoodsRows[]`**，一行一种**本期有生产成本出库**的商品（`W_g > 0`），按出库量降序。

### 4.1 `outboundGoodsRows[]` 商品行

| 字段 | 说明 |
|------|------|
| `disGoodsId` | 分销商商品 ID |
| `goodsName` | 名称 |
| **`gbDgGoodsStandardname`** / **`gbDgControlFresh`** / **`gbDgFreshWarnHour`** / **`gbDgFreshWasteHour`** / **`gbDgGoodsStandardWeight`** | 与 `ingredientRows` 同源，来自 **`gb_distributer_goods`**；无主档时均为 `null` |
| `outboundQtyTotal` | 本期该料生产成本出库合计 |
| **`theoryOutboundQtyByRecipeTotal`** | **按实销×配方推算**的本料理论用量合计：报表内各菜 **实销份数 × 本菜该料单份用量** 之和，便于与 `outboundQtyTotal` 对照 |
| **`theoryQtyFromSalesRecordsTotal`** | **`gb_dep_food_goods_sales`** 汇总得到的本料理论用量合计（与 `salesDish` 中销售子表口径一致） |
| **`linkingDishSoldPortionsTotal`** | **`linkingDishRows`** 中各菜 `soldPortions` 的合计（头表实销份数之和，便于与单行核对） |
| **`linkingDishRows`** | 报表内配方含该料且 u>0 的菜品 |

### 4.2 `linkingDishRows[]`

| 字段 | 说明 |
|------|------|
| `foodId` / `foodName` | 菜品 |
| `soldPortions` | 本期实销份数 |
| `outboundQtyAllocatedToDish` | 本菜在该料上分得的出库量（实销占比或 Σu 回退） |
| `supportedPortionsOnThisGoodOnly` | 仅该料上的可支撑份数 |
| `recipeUnitOnDish` | 本菜该料单份配方用量合计 |
| **`theoryOutboundQtyByRecipe`** | **本菜实销份数 × `recipeUnitOnDish`**（按配方推算的本菜对该料理论用量） |
| **`theoryQtyFromSalesRecords`** | 销售子表 **`gb_dep_food_goods_sales`** 中本菜该料理论用量（无录入则为 `"0"`） |

有出库但无菜品使用该料时，`linkingDishRows` 为 `[]`。

---

## 5. 口径说明

- 理论用量：`gb_dep_food_goods_sales`；**按菜分摊的出库重量/金额、均价**：仅 **`gb_dgsr_type = 1`（生产）**，`queryProductionReduceAggByDisGoods`。
- **区间损耗（老板看整体）**：`data.scopeOutboundSubtotals` — type1/2/3 金额小计与 `wasteLossRatioInOutbound123` = (2+3)/(1+2+3)×100，**百分数字符串、两位小数**（如 `"5.23"` 表示 5.23%）；与单菜成本口径分离。全量 1+2+3 按商品汇总仍可用 `queryProduceLossWasteReduceAggByDisGoods`（其它报表）。
- 共料分摊、可支撑份数：`Q_g>0` 时 `W×q/Q` 再 ÷`u`；`Q_g=0` 时回退 `W/S`；整菜瓶颈仅 `W_g>0` 的原料参与 min。

---

## 6. 后端索引

- `com.nongxinle.controller.GbDishCostAnalysisController`
- `com.nongxinle.service.impl.GbDishCostAnalysisServiceImpl`
