# 旧单 Agent Chat 业务口径备忘（已移除实现）

> **来源**：原 `GbAiChatServiceImpl` + Skill 路由 + classpath `ai-skill-*.md` + DeepSeek 单轮/双轮编排（代码已删除）。  
> **目的**：记录曾注入对话的事实口径，便于对照多智能体主链（Business Graph、Tool、AnswerPlan）是否已覆盖。

## 汇总表

| 主题 | 原实现位置（概念） | 涉及表 / 实体（典型） | 新 Harness 覆盖情况 |
|------|-------------------|----------------------|---------------------|
| 菜品成本诊断 | Skill `ai-skill-dish-cost-diagnosis` + 事实拼装 | `gb_dep_food_sales`、`gb_dish_cost_analysis`（经 `GbDishCostAnalysisService`）、出库 reduce、配料配方 | **DishProfit** / **Cost** 相关 Graph 节点与 Tool（见 `DishProfitAgent`、`CostDiagnosisAgentNode`、`WarehouseStockOverviewTool` 等） |
| 采购结构 / 自采 / 供货商 | `appendPurchaseSupplyMixSummary` 等 | `gb_distributer_purchase_goods`、供货商字段 | **`PurchaseOverviewTool`**、Revenue/Purchase Graph；采购方式拆分见 `docs/API_INTEGRATION.md` |
| 库存核销 / 出库四类 | 成本 Skill 事实块 | `gb_department_goods_stock_reduce`、nx 供货商回填 | **`StockReduceQueryTool`**、`StockReduceAgent` |
| 集团连锁诊断 | 集团诊断段落（净营收 − 出库等） | 多部门营收、`queryReduceAllTypesTotalForRetailDepartmentFathers` | **经营概览 / 诊断 Composite** 与 visibleStores；细节以当前 Tool 为准 |
| 指标目录 metric_id | `AiMetricCatalog` + `MetricExecutionContextFactory`（已删） | YAML `ai-metrics/catalog-v1.yaml`（保留为文档） | **无** 自动 Java 装载；事实走 Graph Tool |
| 时间窗口（规则 + LLM JSON） | `AiUserQueryTimeWindowResolver`、`AiUserQueryTimeWindowLlmParser` | 无表；驱动查库起止日 | **`AiResolvedQueryContext.timeWindow`** + `BusinessTimeWindowNode`（旧 `AiTimePromptGuide` 已删） |
| 会话结束总结 / 记忆 | `endConversation`、`summarizeConversation`、~~`GbAiMemoryService`~~（已删） | ~~`gb_ai_memory`~~、~~`gb_ai_message_memory_extracted`~~（已删） | **未迁**：若需「关会话总结」在 Run 或独立任务重做；参见 `docs/legacy-reference/gb_ai_memory_removed.md` |
| 静态推荐主题卡片 | `GbAiChatController#getRecommendedTopics` | 无 | **未迁**：若产品仍要首页卡片，由前端静态配置或新 BFF 提供 |

## 计算公式与字段（高精摘要，无大段代码）

- **菜品理论/实际成本、差额、排序键**：由 `GbDishCostAnalysisService` 月报与销量行 JOIN，单菜层面有 theoryCost / actualCost / diffCost、人均本（perPortion）等；旧 Chat 将这些压成可读段落。
- **配料 recipe vs 出库分摊**：理论用量来自配方 `recipeUnitPerDish × soldPortions`；出库取 reduce 分摊量；recipe 与出库差异、成本差用于点名「问题原料」。
- **采购方式桶**：`purchase_type=5` 或 (`=1` 且 `purchase_nx_supplier_id` 正) → 供货商订货；`type=1` 且 nx 空或 -1 → 自采；排除退货类型 9。
- **低毛利 / 带内带外**：对照父分类配置的目标毛利带（见旧 skill 文案与 `GrossMarginStandardDisplay` 同类逻辑）；新链在 DishProfit / Composer 中带结构化意图。

## 后续迁移建议

1. 产品若仍需「关会话自动总结」，定义新 API 或 Hook，避免恢复单 Agent。
2. `catalog-v1.yaml` 可按 Tool 一条条重写 `datasource`，或删除文件（当前仅作文档）。
3. ~~`GbAiMemoryService` / `gb_ai_memory`~~ 已删除；持久化追问上下文以 `GbAiConversationTurnMemory*` 与快照为准。
