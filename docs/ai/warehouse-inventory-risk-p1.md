# 库存 P1：风险列表 + 单菜配料可支撑天数

## 能力一：库存报警 / 缺货风险列表

| 项 | 说明 |
|---|---|
| 合同 | `warehouse.inventory_risk_list`（WH-F） |
| Wire | `warehouse_stock_low_risk` |
| Tool | `warehouse_inventory_risk_list` |
| AnswerPlan | `WAREHOUSE_LOW_STOCK_RISK` |
| Card | `WAREHOUSE_INVENTORY_RISK_LIST_CARD` |

**数据来源（复用现有 SQL，不新造口径）：**

- 当前库存：`GbDepartmentGoodsStockService#queryGoodsStockListForMendianPeriod`
- 区间消耗：`GbDepartmentGoodsStockService#queryProductionReduceAggByDisGoods`
- 可支撑天数：`restWeight ÷ (weightSum / windowDays)`；`coverDays < 7` 或 `restWeight ≤ 1` 斤入榜

**Known gaps（P1 诚实降级）：**

- `NEAR_EXPIRY`：仍 Intake 澄清，无保质期批次字段
- 严格零库存口径、安全库存规则、订货习惯模型：未接入
- `fresh_warn_hour` 未用于报警阈值

**禁止：** `warehouse.goods_amount_ranking_low`（WH-C）不得承接偏少/报警问法。

## 能力二：单菜配料可支撑天数

| 项 | 说明 |
|---|---|
| 合同 | `dish.ingredient_cover_days.v1`（DC-C） |
| Wire | `dish_ingredient_cover_days` |
| Tool | `dish_cost_analysis`（`GbDishCostAnalysisService#buildIngredientAnalysisReport`） |
| AnswerPlan | `DISH_INGREDIENT_COVER_DAYS` |
| Card | `DISH_INGREDIENT_COVER_DAYS_CARD` |

**计算（AnswerPlan 层，不重算 SQL）：**

- 日均销量 = 区间销量 ÷ 天数（`rawReportSummary` 起止日）
- 配料可支撑天数 = `supportedPortionsThisGood ÷ 日均销量`
- 整菜天数 = 最短板配料；瓶颈名来自 `ingredientRows` 或 `salesDishRows[].bottle`

**Known gaps：**

- 未用鲜品保质期批次做天数上限
- 区间无销量时无法算天数（`no_sales_in_window_cannot_compute_cover_days`）

**Composer：** 仅短导语；事实在 Card `payload`。
