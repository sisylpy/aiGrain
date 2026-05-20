# dish_sales_query / DishSalesQueryTool 已移除

> **归类**：D-8 销量域 Tool 收口（**D-CLEAN-DISH-SALES-P2**）。  
> **勿与现网语义混淆**：**`DISH_SALES_QUERY`**（intent）与 **`dish_sales_query_path`**（path）**保留**；仅独立 Tool id **`dish_sales_query`** 删除。

## 删除项

| 路径 / 常量 | 说明 |
|-------------|------|
| `src/main/java/.../DishSalesQueryTool.java` | 独立菜品销量 Tool |
| Tool id **`dish_sales_query`** | `AiBusinessToolIds` / Planner / `toolResults` 键 |
| `toolResults["dish_sales_query"]` fallback | Composer / 成本链 **不再** 读取 |

## 现网 D-8 与成本链

| 层 | 现网 |
|----|------|
| **Intent** | **`DISH_SALES_QUERY`**（`AiResolvedQueryIntent`） |
| **Path** | **`dish_sales_query_path`**（`PATH_DISH_SALES_QUERY`） |
| **执行 Tool** | **`dish_profit_analysis`**（`DishProfitAnalysisTool`） |
| **AnswerPlan** | **`DishSalesAnswerPlan`**（D-8 专线）；成本链第 4 步同 Tool，挂载 **`DishProfitAnswerPlan`** / 成本诊断 |

**不得**在 DataPlanner、`DEFAULT_COST_INSIGHT_TOOLS` 或 SSE `tool_*` 中恢复 **`dish_sales_query`**。

## 与菜品毛利语义的区别

| 概念 | 说明 |
|------|------|
| **`dish_profit_path` / `DISH_PROFIT`** | 菜品毛利/成本专线 |
| **`dish_sales_query_path` / `DISH_SALES_QUERY`** | 菜品销量/销售额排行专线（**path 名保留**） |
| **`dish_sales_query` Tool** | **Historical removed** |

## 变更记录

| 日期 | 说明 |
|------|------|
| 2026-05-20 | **D-CLEAN-COST-P1**：成本链第 4 步 **`dish_sales_query` → `dish_profit_analysis`** |
| 2026-05-20 | **D-CLEAN-DISH-SALES-P2**：删除 **`DishSalesQueryTool`**；D-8 亦只执行 **`dish_profit_analysis`** |
