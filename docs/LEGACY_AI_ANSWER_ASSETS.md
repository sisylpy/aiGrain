> 所有涉及 AI 多智能体、经营分析、采购、库存、菜品毛利、权限、组织范围、时间范围、多轮追问的改动，必须先阅读 `docs/AI_AGENT_DEVELOPMENT_GUIDE.md`。

> 任何涉及组织范围、门店、部门、集团汇总、权限范围、经营看板查询的改动，必须先阅读 `docs/DOMAIN_ORG_MODEL.md`。

# 旧版 AI 回答资产 — 索引（Legacy Index）

> **D-AI-FILE-INVENTORY-CLEANUP-P1（2026-05-20）**：本文档已**缩为索引页**。  
> **禁止**把下文或 `docs/legacy-reference/` 中的旧实现细节当作**当前现网契约**。  
> **现网契约**以 `docs/API_INTEGRATION.md`、`docs/PERMISSION_MODEL.md`、`docs/AI_HARNESS_REPLAY_CASES.md`、`src/main/java/com/nongxinle/ai/tool/business/AiBusinessToolIds.java` 为准。

---

## 现网主线（速查）

| 能力 | 现网入口 |
|------|----------|
| 语义解析 | V2-only：`query_semantic_parser.v2.md` + `semantic-output-schema.md` |
| 经营概览 | `business_overview_path` → **MULTI_AGENT 四域**（`revenue_query` + `purchase_overview` + `stock_reduce_query` + `dish_profit_analysis`） |
| 成本诊断 | `cost_diagnosis_path` → 四 Tool + `CostDiagnosisAgentNode` + `CostMarginDerivation` |
| 经营诊断 | `business_diagnosis_path` → `DiagnosisPlanBuilder` + `BusinessDiagnosisAgentV1.enrich` + `DiagnosisDeterministicRenderer` |
| 菜品毛利 / D-8 销量 | `dish_profit_analysis`（`DishProfitAnalysisTool`）；path `dish_sales_query_path` **≠** Tool id |
| 库存现量 | `warehouse_stock_overview`（wire `STOCK_QUERY` 映射到此，**非** `stock_query`） |
| 出库核销 | `stock_reduce_query` |
| 采购 | `purchase_overview` |
| 旧聊天 | `GbAiChatServiceImpl` + `ai-skill-*.md`（仍可参考话术，**非** Graph 编排源） |

---

## 已从 `src/main` 删除 — 勿恢复

| 已删 Tool id / 类 | 现网替代 | 详细说明 |
|-------------------|----------|----------|
| `purchase_query` / `PurchaseQueryTool` | `purchase_overview` | [purchase 相关 — 见 classic 与 phase2 doc] |
| `stock_query` / `StockQueryTool` | `warehouse_stock_overview` | [stock-query-tool-removed.md](legacy-reference/stock-query-tool-removed.md) |
| `dish_sales_query` / `DishSalesQueryTool` | `dish_profit_analysis`（保留 `DISH_SALES_QUERY` intent/path） | [dish-sales-query-tool-removed.md](legacy-reference/dish-sales-query-tool-removed.md) |
| `gross_margin_calculator` / `GrossMarginCalculatorTool` | `CostMarginDerivation` + `CostDiagnosisAgentNode` | [gross-margin-calculator-tool-removed.md](legacy-reference/gross-margin-calculator-tool-removed.md) |
| `business_overview_query` / `BusinessOverviewQueryTool` | MULTI 四域 + `revenue_query` | [classic-business-overview-removed.md](legacy-reference/classic-business-overview-removed.md) |
| `BusinessOverviewAgent` / `BusinessOverviewAgentNode` | `MasterBusinessAgent` + MULTI_AGENT Composer | 同上 |
| `StockReduceDeterministicRenderer` | `StockReduceAnswerPlan` + Composer Plan-first | [stock-reduce-deterministic-renderer-removed.md](legacy-reference/stock-reduce-deterministic-renderer-removed.md) |
| `BusinessDiagnosisPlan` / `BusinessDiagnosisPlanBuilder` / `BusinessDiagnosisPlanNode` | `DiagnosisPlan` + `DiagnosisPlanBuilder` + `BusinessDiagnosisAgentV1` | [business-diagnosis-plan-removed.md](legacy-reference/business-diagnosis-plan-removed.md) |
| `AiQuerySemanticTimeLexicon`、V2 `*Normalizer` / `*Gate` | V2 `time` + `semanticSlots` + Merge/Policy | [d1x-v2-only-time-source-cleanup-inventory.md](ai/d1x-v2-only-time-source-cleanup-inventory.md) |
| `metric.rankingType` 作主 wire | `semanticSlots.structuredIntentDetailWire` | [d1x-rankingtype-and-duplicate-responsibility-inventory.md](ai/d1x-rankingtype-and-duplicate-responsibility-inventory.md) |

---

## `docs/legacy-reference/` 全文索引

| 文档 | 主题 |
|------|------|
| [classic-business-overview-removed.md](legacy-reference/classic-business-overview-removed.md) | Classic 六工具经营概览链 |
| [business-diagnosis-plan-removed.md](legacy-reference/business-diagnosis-plan-removed.md) | `BusinessDiagnosisPlan` 旧诊断 DTO 链 |
| [stock-query-tool-removed.md](legacy-reference/stock-query-tool-removed.md) | `stock_query` Tool |
| [dish-sales-query-tool-removed.md](legacy-reference/dish-sales-query-tool-removed.md) | `dish_sales_query` Tool |
| [gross-margin-calculator-tool-removed.md](legacy-reference/gross-margin-calculator-tool-removed.md) | 毛利计算器 Tool |
| [stock-reduce-deterministic-renderer-removed.md](legacy-reference/stock-reduce-deterministic-renderer-removed.md) | 出库 Tool-envelope 确定性 Renderer |
| [single-agent-chat-business-logic.md](legacy-reference/single-agent-chat-business-logic.md) | 旧版单 Agent 聊天 |
| [workspace-keyword-route-and-guard.md](legacy-reference/workspace-keyword-route-and-guard.md) | 工作区关键词路由（Historical） |
| [gb_ai_conversation_type_removed.md](legacy-reference/gb_ai_conversation_type_removed.md) | DB 列 legacy |
| [gb_ai_message_type_removed.md](legacy-reference/gb_ai_message_type_removed.md) | DB 列 legacy |
| [gb_ai_memory_removed.md](legacy-reference/gb_ai_memory_removed.md) | 表 legacy |

---

## 旧版能力深读（Historical baseline，非现网设计）

| 文档 | 用途 |
|------|------|
| [dish-profit-legacy-review.md](ai/dish-profit-legacy-review.md) | 旧版单板 Agent / `GbDepFood*` 服务盘点 |
| [LEGACY_STOCK_REDUCE_ASSETS.md](LEGACY_STOCK_REDUCE_ASSETS.md) | 出库分型与旧报表口径 |

---

## 维护约定

- 新增「已删除」能力：**只**在 `docs/legacy-reference/` 增 `*-removed.md`，本页表格加一行链接。  
- **不要**在本文件恢复长篇实现细节。  
- 进度勾选见 `docs/TODO_MULTI_AGENT.md`。
