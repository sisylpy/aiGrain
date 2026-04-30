# 单菜配料看板接口说明

面向**独立页面**：单道菜的配料明细、成本结构占比（环形图数据）、按月成本趋势（折线图数据）及综合建议文案。  
分摊与 type1/2/3 约定与 **`POST /gbDishCostAnalysis/ingredientAnalysis`**、文档 **`docs/gb-dish-cost-allocation-model.md`** 一致。

---

## 1. 接口概览

| 项目 | 说明 |
|------|------|
| **路径** | `POST /gbDishCostAnalysis/dishIngredientDashboard` |
| **Controller** | `GbDishCostAnalysisController#dishIngredientDashboard` |
| **Service** | `GbDishCostAnalysisService#buildDishIngredientDashboard` |

外层响应与项目惯例一致：`R` 中 `code`（成功为 `0`）、`data`（成功时为本接口负载）、`msg`（可选）。

---

## 2. 请求参数（表单 POST）

| 字段 | 必填 | 说明 |
|------|------|------|
| `startDate` | 是 | 主统计区间起点，`yyyy-MM-dd`（可与更长字符串截取前 10 位解析） |
| `endDate` | 是 | 主统计区间终点，`yyyy-MM-dd` |
| `disId` | 是 | 批发商 ID |
| `depFatherId` | 是 | 父部门 ID；与 `ingredientAnalysis` 相同，用于解析门店子部门范围（`searchDepId` 在此接口固定为不按单店筛，等价于按父级下门店汇总） |
| `foodId` | 是 | **`gb_distributer_food.gb_distributer_food_id`**（菜品主键，非部门菜 id） |
| `trendStartDate` | 否 | 趋势曲线起点；省略时取 **`endDate` 往前 5 个自然月** 与主区间求交后的起点 |
| `trendEndDate` | 否 | 趋势曲线终点；省略时为 **`endDate`**；与主区间求交，不会超过 `endDate` |
| `trendGranularity` | 否 | 仅支持 **`month`**（自然月）；省略视为 `month`；其它值返回 `code != 0` |
| `primaryDisGoodsId` | 否 | 趋势曲线聚焦的**分销商商品 id**（`gb_distributer_goods`）；省略时取主区间内 **`actualCostPerPortion` 最大** 的配料行 |

**参数校验失败**：返回 `R.error(-1, message)`，`data` 无业务负载。

---

## 3. 响应 `data` 顶层字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `startDate` / `endDate` | string | 主区间（与请求一致，已 trim） |
| `trendStartDate` / `trendEndDate` | string | 实际用于趋势计算的区间（与主区间及请求趋势参数求交后） |
| `trendGranularity` | string | 当前固定为 `month` |
| `disId` / `depFatherId` | number | 与请求一致 |
| **`dish`** | object | 整菜汇总（见 §4） |
| **`ingredientRows`** | array | 配料行：在 `ingredientAnalysis` 同口径字段基础上**扩展**看板字段（见 §5） |
| **`costStructure`** | object | 成本结构占比（环形图，见 §6） |
| **`costTrend`** | object | 成本趋势（折线图，见 §7） |
| **`scopeOutboundSubtotals`** | object | 与主区间、`reduceParams` 一致的区间 type1/2/3 出库金额小计及损耗率（与 `POST /gbDishCostAnalysis/report` 中 `scopeOutboundSubtotals` 同源结构） |
| **`summarySuggestionZh`** | string | 综合建议文案（规则生成，供页面底部展示） |
| **`disclaimerZh`** | string | 与 `/ingredientAnalysis` 相同的分摊口径免责声明 |

---

## 4. `dish`（整菜块）

**与 `ingredientRows[]` 的区分**：`dish` 为**整菜**汇总（标价/份、整菜理论·实际成本/份、净毛利等）；`ingredientRows` 上同名毛利率字段为**配料行**口径（含区间综合、按料摊标价份额等），二者不可混用。

| 字段 | 类型 | 说明 |
|------|------|------|
| `foodId` | number | 同请求 `foodId` |
| `foodName` | string | 来自 `gb_distributer_food` 菜名 |
| `listPricePerPortion` | string | 分销商标价 `gb_df_food_price`（库字段原样，可为空字符串） |
| `salesPortions` | string | 主区间内实销份数（整数份数字符串） |
| `salesAmount` | string | 主区间内实收销售额合计（两位小数） |
| `salesUnitPrice` | string | 实收均价（两位小数） |
| `theoryCostPerPortion` | string | 整菜理论成本/份 |
| `actualCostPerPortion` | string | 整菜实际成本/份（type1+2+3 摊销金额合计÷份数，与配料分析整菜行一致） |
| `diffCostPerPortion` | string | 实际 − 理论，每份（两位小数）；**不是**「标价 − 实际」净毛利 |
| **`comprehensiveGrossMarginRateOnListPrice`** | string / null | **区间综合毛利率（展示）**：`(Σ 报表内标价收入 − 区间 type1+2+3 出库金额) ÷ Σ 标价收入`，与 `ingredientRows[].comprehensiveGrossMarginRateOnListPrice`、经营分析 `businessInsightSummary` 同源；固定两位小数字符串，规则同 `marginRateOnListPriceString` |
| **`grossMarginRateOnListPrice`** | string / null | **标价 vs 仅 type1 生产实摊/份**（与部门经营分析单菜 `grossMarginRateOnListPrice` 同思路）：`(listPricePerPortion − Σ 配料 produceCostPerPortion) ÷ listPricePerPortion`。若无法给出有效值且标价&gt;0，回退为与 **`grossMarginRateOnListPriceUsingActual123`** 相同算式 |
| **`blendedGrossMarginRateOnListPrice`** | string / null | **实际毛利率（标价 vs type1+2+3 摊销）**：`(listPricePerPortion − actualCostPerPortion) ÷ listPricePerPortion`，与 **`grossMarginRateOnListPriceUsingActual123`** **同值**（便于按「blended / 综合实际」命名展示） |
| **`grossMarginRateOnListPriceUsingActual123`** | string / null | 与 **`blendedGrossMarginRateOnListPrice`** 相同：`(listPricePerPortion − actualCostPerPortion) ÷ listPricePerPortion` |
| **`grossMarginRateTheoryOnListPrice`** | string / null | **配料毛利率·理论（整菜）**：`(listPricePerPortion − theoryCostPerPortion) ÷ listPricePerPortion` |
| **`blendedGrossMarginRateTheoryOnListPrice`** | string / null | 与 **`grossMarginRateTheoryOnListPrice`** 同值（别名） |
| **`netGrossProfitPerPortion`** | string | **净菜品毛利/份（元）**：标价&gt;0 时为 `listPricePerPortion − actualCostPerPortion`（两位小数）；**无标价**时为 `0 − actualCostPerPortion`（即 `-actualCostPerPortion`） |
| **`grossMarginStandardTarget`** 等 / **`grossMarginLevel`** | 与 `depGetAllFood` 的 `gbDfBusinessInsight` 同源：直接父级 `gb_distributer_food` 上配置的 T、F 与三档 `IN_BAND` / `ABOVE` / `BELOW` / `UNKNOWN`（对比 `blendedGrossMarginRateOnListPrice` 与 T±F 带） |

以上毛利率字段均为**不含 `%` 后缀的两位小数字符串**（如 `"12.34"` 表示 12.34%），与 wxml 自行拼接 `%` 的写法一致。

---

## 5. `ingredientRows[]`（配料行）

### 5.1 与 `/ingredientAnalysis` 中 `salesDishRows[].ingredientRows` 一致的字段

包括但不限于：`disGoodsId`、`gbDgGoodsName`、主档鲜品/规格字段、`recipeUnitPerDish`、`theoryUsage`、`salesUsageFromOrders`、`actualUsage`、`actualProduceUsage`、`actualWasteUsage`、`actualLossUsage`、`allocatedOutboundPerSoldPortion`、`produceAllocatedPerSoldPortion`、`theoryCostPerPortion`、`actualCostPerPortion`、`produceCostPerPortion`、`wasteCostPerPortion`、`lossCostPerPortion`、`lossAndWasteCostPerPortion`、`utilizationRate`、`utilization`、`unitPrice`。

**配料行上的三种毛利率**（与 §4 `dish` 不同）：`comprehensiveGrossMarginRateOnListPrice`（**整段报表区间**分子分母，各行**相同**）、`blendedGrossMarginRateTheoryOnListPrice`（**整菜**理论毛利率字符串，各行**相同**）、`blendedGrossMarginRateOnListPrice`（**按该行理论成本占整菜理论成本**摊标价收入后，与该行 **type1** 实摊比较）。详见 `GbDishCostAnalysisServiceImpl#buildIngredientAnalysisDishRow` 与 `GbDepFoodBusinessInsightServiceImpl` 说明。

### 5.2 本看板**扩展**字段（仅本接口写入）

| 字段 | 类型 | 说明 |
|------|------|------|
| **`usageDeviationRatio`** | string | 用量偏差比例：`(actualUsage − theoryUsage) ÷ theoryUsage`；`theoryUsage` 为 0 时为 `"0"` 去尾零展示 |
| **`usageDeviationPercent`** | string | 上式×100，**固定两位小数**（如 `"30.00"` 表示 30%） |
| **`usageStatus`** | string | `NORMAL` 或 `ABNORMAL`；当 **\|usageDeviationRatio\| > 0.15** 时为 `ABNORMAL` |
| **`costShareOfDishActualPercent`** | string | **成本占比**：该行 `actualCostPerPortion` ÷ 本菜所有配料行 `actualCostPerPortion` 之和 ×100，两位小数 |
| **`listPriceRevenueAllocatedPerPortion`** | string | 本菜标价收入（份数×`gb_df_food_price`）按该行理论成本占整菜理论成本占比摊到**每份**的标价份额（元，两位小数） |
| **`grossProfitContributionPerPortion`** | string | **毛利贡献（元/份）**：`listPriceRevenueAllocatedPerPortion` − `produceCostPerPortion`（生产 type1 实摊/份） |
| **`suggestionLevel`** | string | `FOCUS` \| `OPTIMIZE` \| `NORMAL`；规则：异常且成本占比≥25% 为 `FOCUS`；异常或占比≥15% 为 `OPTIMIZE`；否则 `NORMAL` |
| **`suggestionZh`** | string | 与 `suggestionLevel` 配套的简短中文操作建议 |

---

## 6. `costStructure`（成本结构占比）

| 字段 | 类型 | 说明 |
|------|------|------|
| `basis` | string | 固定为 `actualCostPerPortion`，表示占比按**单份实际成本（type1+2+3）**计算 |
| `totalCostPerPortion` | string | 各配料行 `actualCostPerPortion` 之和（两位小数），应与 `dish.actualCostPerPortion` 一致 |
| `otherMergeThresholdPercent` | string | 合并为「其他」的阈值，固定 **`5.00`**（小于该占比的配料合并） |
| **`segments`** | array | 扇区列表；先按单份成本降序，**占比 < 5%** 的配料合并为一项 |

### 6.1 `segments[]` 元素

| 字段 | 类型 | 说明 |
|------|------|------|
| `disGoodsId` | number | 分销商商品 id；合并项无单料 id |
| `name` | string | 配料名称 |
| `costPerPortion` | string | 该扇区单份成本（两位小数） |
| `sharePercent` | string | 占 `totalCostPerPortion` 的百分比，两位小数 |
| `key` | string | 仅合并行存在，固定 **`OTHER`** |
| `name`（合并行） | string | 固定 **`其他`** |

---

## 7. `costTrend`（成本趋势）

| 字段 | 类型 | 说明 |
|------|------|------|
| `granularity` | string | `month` |
| `primaryDisGoodsId` | number / null | 曲线聚焦的配料 id；无有效配料时为 `null` |
| `primaryGoodsName` | string / null | 聚焦配料名称（主档或配料行回退） |
| **`points`** | array | **每个自然月**一点（在 `trendStartDate`～`trendEndDate` 与主区间交集中；单月内再按自然月裁切起止） |
| **`changeSummaryZh`** | string | 根据**最后两个月** `primaryIngredientCostPerPortion` 环比生成的中文短句 |

### 7.1 `points[]` 元素

| 字段 | 类型 | 说明 |
|------|------|------|
| `periodLabel` | string | 自然月标签，如 `2025-04` |
| `periodStart` | string | 该点在区间内使用的起始日 `yyyy-MM-dd` |
| `periodEnd` | string | 该点在区间内使用的结束日 `yyyy-MM-dd` |
| `soldPortions` | string | 该月子区间内本菜实销份数 |
| **`primaryIngredientCostPerPortion`** | string | 该月聚焦配料的 **`actualCostPerPortion`**（与当月 `ingredientAnalysis` 同行口径一致） |

**性能说明**：趋势按自然月**逐月**重复加载分摊数据；单月失败时该月跳过并打日志。最多处理 **18 个自然月**，超出部分截断。

---

## 8. `scopeOutboundSubtotals`

与 `POST /gbDishCostAnalysis/report` 返回的 `data.scopeOutboundSubtotals` 字段一致，例如：

- `subtotalProduceType1` / `subtotalWasteType2` / `subtotalLossType3`
- `subtotalOutbound123` / `wasteLossAmountType23`
- `wasteLossRatioInOutbound123`（百分数字符串，两位小数，含义见 `bossColumnHintsZh`）

---

## 9. 请求示例（curl）

```bash
curl -s -X POST 'http://<host>/gbDishCostAnalysis/dishIngredientDashboard' \
  -d 'startDate=2025-04-01' \
  -d 'endDate=2025-06-30' \
  -d 'disId=1' \
  -d 'depFatherId=10' \
  -d 'foodId=123' \
  -d 'trendStartDate=2025-04-01' \
  -d 'trendEndDate=2025-06-30' \
  -d 'trendGranularity=month' \
  -d 'primaryDisGoodsId=1001'
```

省略趋势相关参数时，趋势区间按 §2 默认规则与主区间求交。

---

## 10. 与相关接口的关系

| 接口 | 关系 |
|------|------|
| `POST /gbDishCostAnalysis/ingredientAnalysis` | 全量按菜列表；本接口为**单菜**聚合看板，配料行底层同源 |
| `POST /gbDishCostAnalysis/report` | `scopeOutboundSubtotals` 结构复用 |
| `POST /gbdepfood/depGetAllFood` | 列表中带 `ingredientAnalysisRows` 时仍为嵌入列表；**独立页推荐直接调本接口** |

---

## 11. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-04-25 | 初版；§4 整菜展示毛利率/净毛利/高毛利；§5.1 与 `dish` 口径区分 |
