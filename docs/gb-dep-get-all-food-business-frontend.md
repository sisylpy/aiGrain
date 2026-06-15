# 部门菜品接口 `POST /gbdepfood/depGetAllFood` — 经营分析字段说明（给前端）

当请求同时传入 **`startDate`、`stopDate`、`disId`、`depFatherId`**（均为有效值）时，接口在原有菜品列表基础上增加**经营分析**数据；用于门店菜品统计、周销量、标价收入、成本与毛利率、区间损耗率等页面。此时 **`data` 数组按区间内总销量降序**；**销量为 0 的菜品仍返回主档信息，但不查询配方行/配料**（`gbDistributerFoodEntity.gbdisFoodGoodsEntities` 为空数组）。

未传齐上述四个参数时，行为与旧版一致：**不返回**下文中的顶层扩展字段，且 **`gbDfBusinessInsight`** 为 `null`。

---

## 1.0 `businessInsightSummary`（顶部统计块）

对 **`data` 中每一行** 的 `gbDfBusinessInsight` 做**算术加总**（不是按行毛利率再平均），再算全列表的**综合毛利率**：

| 字段 | 说明 |
|------|------|
| `dishRowCount` | 参与汇总的行数（有 `gbDfBusinessInsight` 的行，四参齐全时与列表行数一致）。 |
| `totalActualRevenue` | Σ `actualRevenue`（元，保留 2 位小数字符串）。 |
| `totalActualCostAmount` | Σ `actualCostAmount`（仅 type1 生产口径，与 `report.salesDishRows` 一致）。 |
| `totalActualCostTotalAmount123` | Σ `actualCostTotalAmount123`（**type1+2+3** 摊销后的整菜区间实际成本合计，与配料分析/看板整菜金额口径一致）。 |
| `totalTheoryCostAmount` | Σ `theoryCostAmount`。 |
| `blendedGrossMarginRateOnListPrice` | **综合实际毛利率（%）**，固定两位小数字符串：`(Σ revenue − Σ actual) ÷ Σ revenue × 100`；`revenue` 全为 0 且成本全为 0 时为 `"0.00"`；有成本无收入时为 `null`。 |
| `blendedGrossMarginRateTheoryOnListPrice` | **综合理论毛利率（%）**；空值规则同上。 |
| `wasteLossRatioInOutbound123` 等 | 与根级 **`scopeOutboundSubtotals`** 中同名字段**相同**；**损耗率**为百分数字符串、两位小数（与成本报表一致）。 |

**说明**：顶部 **损耗率** 是**部门+区间出库**口径（与成本报表一致），不是「按每道菜毛利率加权」；列表行的毛利率是**按菜标价收入**口径。

---

## 1. 响应根结构（与 `R` 常规字段并列）

| 字段 | 类型 | 说明 |
|------|------|------|
| **`data`** | `GbDepFoodEntity[]` | 部门菜品列表（与原先一致，含 `gbDistributerFoodEntity`、配方行等）。 |
| **`businessInsightSummary`** | `object` | **列表顶部汇总**（对当前 `data` 中每条 `gbDfBusinessInsight` 的标价收入、实际/理论成本求和后，再算**综合毛利率**；出库损耗率与 `scopeOutboundSubtotals` 同源字段在此块重复挂载，便于一块展示）。见 **1.0**。 |
| **`scopeOutboundSubtotals`** | `object` | 本统计区间、本部门范围内的**出库金额汇总**（与 `gbDishCostAnalysis/report` 同源）。见下表。 |
| **`weekdayLegend`** | `object` | 周几编号到中文：**`"0"`→周日**，`"1"`～`"6"`→周一～周六（与 `gb_dfs_revenue_weekday` 一致）。 |
| **`scopeDepIds`** | `number[]` | 参与销量统计的**子部门**门店 id 列表（与成本报表解析范围一致）。 |
| **`bossColumnHintsZh`** | `object` | 老板可读列说明；其中 **`scopeOutboundSubtotals`** 子对象对应下面金额字段的白话解释。 |
| **`insightStartDate`** | `string` | 本次经营分析开始日 `yyyy-MM-dd`。 |
| **`insightStopDate`** | `string` | 本次经营分析结束日 `yyyy-MM-dd`。 |

### 1.1 `scopeOutboundSubtotals` 子字段

| 字段 | 说明 |
|------|------|
| `subtotalProduceType1` | type=**1**（生产）出库金额合计（元）；**按菜分摊的成本、配料均价只基于这一类**。 |
| `subtotalWasteType2` | type=**2**（损耗）出库金额合计（元）。 |
| `subtotalLossType3` | type=**3**（损失）出库金额合计（元）。 |
| `subtotalOutbound123` | type **1+2+3** 出库金额合计（元），不含退货；作**损耗率分母**。 |
| `wasteLossAmountType23` | type **2+3** 金额合计（元），作**损耗率分子**。 |
| `wasteLossRatioInOutbound123` | **损耗率（%）** = `(2+3) ÷ (1+2+3) × 100`，固定两位小数字符串（如 `"5.23"` 表示 5.23%）；分母为 0 时为 `"0.00"`。 |

---

## 2. 每条菜品 `GbDepFoodEntity` 上新增 / 含义有变化的字段

### 2.1 `gbDfBusinessInsight`（`object | null`）

仅在上文「四参齐全」时有值；为**该行菜品**在区间内的经营指标（数值多为**字符串**，便于展示与精度一致）。

| 字段 | 说明 |
|------|------|
| **`weekdayQty`** | `object`，键为 **`"0"`～`"6"`** 的字符串，值为该天销量字符串。**`0` = 周日**。 |
| **`soldPortionsUnassignedWeekday`** | 有销量但 **`gb_dfs_revenue_weekday` 为空或不在 0～6** 的份数，仍计入总销量；周几分桶里体现不到的部分。 |
| **`soldPortionsTotal`** | 区间内**总销量**（周几分桶之和 + 未分配周几部分）。 |
| **`listPrice`** | 部门菜品标价（来自 `gb_dep_food.gb_df_food_price`，与实体上一致）。 |
| **`actualRevenue`** | **标价收入** = `soldPortionsTotal × listPrice`（元，字符串）。 |
| **`actualCostAmount`** | **实际成本（元）**：与 `gbDishCostAnalysis/report` 中 `salesDishRows` 同 `foodId` 的 **`actualCostAmount`** 一致；仅 **type=1 生产**出库分摊（区间合计）。 |
| **`theoryCostAmount`** | **理论成本（元）**：同上报表 **`theoryCostAmount`**（销售子表用量等「该用多少料」口径）。 |
| **`grossMarginRateOnListPrice`** | **type1 实际毛利率（%）**，固定两位小数：`(actualRevenue − actualCostAmount) ÷ actualRevenue × 100`；无标价收入且成本均为 0 时为 `"0.00"`，有成本无收入时为 `null`。 |
| **`actualCostPerPortion123`** | **单份实际成本（元/份，type1+2+3）**：与 `gbDishCostAnalysis/ingredientAnalysis` 整菜行 **`actualCostPerPortion`** 同口径；由 `GbDishCostAnalysisService#getDishActualCostPerPortion123ByFoodIds` 批量计算。 |
| **`actualCostTotalAmount123`** | **实际分摊总金额（元，type1+2+3）**：`actualCostPerPortion123 × soldPortionsTotal`（与看板整菜「单份实际×份数」一致）；**区别于** `actualCostAmount`（仅 type1 生产、`report` 同源）。 |
| **`blendedGrossMarginRateOnListPrice`** | **单菜实际毛利率（%）**：`(部门标价 listPrice − actualCostPerPortion123) ÷ listPrice × 100`（标价用 **`gb_dep_food.gb_df_food_price`** 解析）；无有效标价且单份成本也为 0 时为 `"0.00"`，否则无标价但有成本时为 `null`。与顶部 **`businessInsightSummary.blendedGrossMarginRateOnListPrice`**（列表汇总、type1 口径）**含义不同**，勿混用。 |
| **`grossMarginRateTheoryOnListPrice`** | **理论成本毛利率（%）**：`(actualRevenue − theoryCostAmount) ÷ actualRevenue × 100`；空值规则同 `grossMarginRateOnListPrice`。 |
| **`foodId`** | 批发商侧菜品 id（`gb_df_food_id`）。 |
| **`gbDepFoodId`** / **`gbDfDepId`** | 部门菜品主键、部门 id，便于与列表行对应。 |

无销售、无成本报表行时，上述金额类多为 `"0"`，毛利率类多为 `"0.00"`；有收入无成本时分母大于 0 会得到接近 `"100.00"` 的字符串。

### 2.2 `gbDfSalesAmount`（`string`）

- **四参齐全**：与 **`gbDfBusinessInsight.soldPortionsTotal`** 一致，为**子部门 scope** 下的总销量（与成本分析对齐）。  
- **四参未齐**：仍为旧逻辑——在仅有日期与 `disId`、`depFatherId` 时，按销售表 **`gb_dfs_dep_father_id`** 汇总销量（与经营分析 scope 可能不同，属兼容行为）。

### 2.3 菜品名称 `gbDfFoodName`

有批发商菜品主档且 **`gbDistributerFoodEntity.gbDfFoodName`** 非空时，会用其**覆盖**部门菜品展示名称，保证列表与经营块名称一致。

---

## 3. 与其它接口的关系

- **`gbDishCostAnalysis/report`**：`actualCostAmount`、`theoryCostAmount`、分摊口径与 **`scopeOutboundSubtotals`** 与之一致；本接口把其中**按菜**部分挂到 `gbDfBusinessInsight`，并把区间损耗汇总提到根上。  
- 前端若已接成本分析报表，可直接复用 **`bossColumnHintsZh`** 做列头提示。

---

## 4. 错误处理

四参齐全但参数非法（如日期为空字符串经校验失败）时，可能返回 **`code != 0`** 与 **`msg`**，与原先独立经营接口一致；此时无 `data` 扩展字段约定，以前端 `msg` 提示为准。
