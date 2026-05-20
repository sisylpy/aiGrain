# gross_margin_calculator / GrossMarginCalculatorTool 已移除

> **归类**：成本诊断链毛利推导收口（**D-CLEAN-GROSS-MARGIN-P2A/P2B**）。  
> **不属于** [classic business overview 删除](classic-business-overview-removed.md) 六工具链条目本身，但 classic 链曾编排本 Tool。

## 删除项

| 路径 | 说明 |
|------|------|
| `src/main/java/com/nongxinle/ai/tool/business/GrossMarginCalculatorTool.java` | 纯推导 Tool，读 `revenue_query` / `dish_profit_analysis` / `stock_reduce_query` 快照 |
| `AiBusinessToolIds.GROSS_MARGIN_CALCULATOR` | Tool id 常量 |
| `BusinessToolExecutionNode` | `GROSS_MARGIN_CALCULATOR` 分支（`ARG_INPUT_SNAPSHOT`） |
| `AiPermissionGuard` / `AiAnswerBoundary` | `gross_margin_calculator` 权限与文案映射 |
| `GrossMarginCalculatorToolMissingOutboundTest` | 核销全 0 保护改由 `CostDiagnosisAgentNodeDataIncompleteScenarioTest` 覆盖 |

## 现网成本链（`cost_diagnosis_path`）

| 步骤 | Tool / Agent |
|------|----------------|
| 1 | `revenue_query` |
| 2 | `purchase_overview` |
| 3 | `stock_reduce_query` |
| 4 | `dish_profit_analysis` |
| — | **`CostDiagnosisAgentNode`**：门店粗估毛利率由 **`CostMarginDerivation.derive`** 内部计算，**不写回** `toolResults` |

**不得**再编排、注册或产生 SSE `tool_started`/`tool_finished` 的 `gross_margin_calculator`。

## 与菜品毛利语义的区别

| 概念 | 说明 |
|------|------|
| `STRUCTURED_DISH_GROSS_MARGIN_QUERY` / `dish_gross_margin_query` | **仍有效**：菜品毛利专线语义 wire，走 `dish_profit_path` + `dish_profit_analysis` |
| `gross_margin_calculator` Tool | **Historical removed**：门店级成本诊断粗估毛利，非单菜查询 |

## 变更记录

| 日期 | 说明 |
|------|------|
| 2026-05-20 | **P2A**：`DEFAULT_COST_INSIGHT_TOOLS` 移除第 5 步；`CostDiagnosisAgentNode` 支持无 Tool 时 `CostMarginDerivation` 回退 |
| 2026-05-20 | **P2B/FINAL**：删除 Tool 实现与全链路引用；毛利仅内部推导 |
