> 所有涉及 AI 多智能体、经营分析、采购、库存、菜品毛利、权限、组织范围、时间范围、多轮追问的改动，必须先阅读 `docs/AI_AGENT_DEVELOPMENT_GUIDE.md`。

> 任何涉及组织范围、门店、部门、集团汇总、权限范围、经营看板查询的改动，必须先阅读 `docs/DOMAIN_ORG_MODEL.md`。

# AI 主链与 Tool 索引

> **权威契约**：`docs/API_INTEGRATION.md`、`docs/PERMISSION_MODEL.md`、`docs/AI_HARNESS_REPLAY_CASES.md`、`src/main/java/com/nongxinle/ai/tool/business/AiBusinessToolIds.java`  
> **语义合同**：`docs/ai/semantic-allowed-output-contract-design.md`、`docs/ai/semantic-contract-strict-mode-plan.md`

---

## 现网主链（速查）

| 能力 | 现网入口 |
|------|----------|
| 语义解析 | `query_semantic_parser.v2.md` → `semanticSlots` → `SemanticContractValidator`（observe-only） |
| 追问改写 | `LlmFollowUpQueryRewriter`（`followup_query_rewriter.v1.md`） |
| 锚点 execution | `PurchaseSemanticExecutionIntentResolver` + `resultAnchors` / `executionDetailWanted` |
| 经营概览 | `business_overview_path` → MULTI_AGENT 四域（`revenue_query` + `purchase_overview` + `stock_reduce_query` + `dish_profit_analysis`） |
| 成本诊断 | `cost_diagnosis_path` → 四 Tool + `CostDiagnosisAgentNode` + `CostMarginDerivation` |
| 经营诊断 | `business_diagnosis_path` → `DiagnosisPlanBuilder` + `BusinessDiagnosisAgentV1` + `DiagnosisDeterministicRenderer` |
| 菜品毛利 / D-8 销量 | `dish_profit_analysis`；path `dish_sales_query_path` **≠** Tool id |
| 库存现量 | `warehouse_stock_overview`（wire `STOCK_QUERY`） |
| 出库核销 | `stock_reduce_query` |
| 采购 | `purchase_overview` |
| 商品目录 AI（非经营 Graph） | `GbAiGoodsAddServiceImpl` + `ai-skill-goods-catalog-*.md` |

---

## Composite / Planner / SHADOW（Harness 旁路，非生产终稿主链）

| 项 | 说明 |
|----|------|
| **PlannerExecutor** | Harness **组合执行基础设施**（多步 `PlannerExecutionPlan`、`StepResult`、`degradedSteps`）；详见 [`docs/ai/planner-executor-v1-design.md`](ai/planner-executor-v1-design.md)。 |
| **四域 Adapter** | Revenue / Purchase / StockReduce / DishProfit **RealReadBridge** 经 Adapter 调既有 Tool + AnswerPlan；**不负责 SQL / 生产 Composer 终稿**。 |
| **BusinessDiagnosis Composite** | 六步 Composite 计划 + **`BusinessDiagnosisCompositeAnswerPlan`**；生产入口 **`BusinessDiagnosisCompositeProductionGate`**。 |
| **HARNESS_ONLY** | 仅 Harness **`GRAPH_RUN`** 下执行 Composite Planner（`compositeBusinessDiagnosisExecutionMode=HARNESS_ONLY`）。 |
| **SHADOW** | 普通 **`POST /api/ai/runs`** 在 Gate 允许时可 **旁路**执行 Composite + Readonly Composer；默认 **`shadow.enabled=false`**。 |
| **终稿边界** | **`SHADOW` / `HARNESS_ONLY` 不替换 `finalAnswerText` / `answerPreview`**，除非显式切换 PRIMARY 策略。旁路正文见 **`compositeFinalAnswerText`** 等 SSE 观测字段。 |
| **生产主链不变** | 经营者可见答案仍以 **semantic contract → Tool → AnswerPlan → `StubAnswerComposerNode` / 域 DeterministicRenderer** 为准（见 [`docs/ai/harness-composer-architecture.md`](ai/harness-composer-architecture.md)）。 |

设计索引：[`docs/ai/business-diagnosis-production-gate-design.md`](ai/business-diagnosis-production-gate-design.md)、[`docs/ai/business-diagnosis-production-composite-execution-design.md`](ai/business-diagnosis-production-composite-execution-design.md)、[`docs/ai/main-business-routing-and-composite-integration-map.md`](ai/main-business-routing-and-composite-integration-map.md)。

---

## 已下线 id → 现网替代

| 已下线 Tool id / 类 | 现网替代 |
|---------------------|----------|
| `purchase_query` / `PurchaseQueryTool` | `purchase_overview` |
| `stock_query` / `StockQueryTool` | `warehouse_stock_overview` |
| `dish_sales_query` / `DishSalesQueryTool` | `dish_profit_analysis`（保留 `DISH_SALES_QUERY` / `dish_sales_query_path`） |
| `gross_margin_calculator` / `GrossMarginCalculatorTool` | `CostMarginDerivation` + `CostDiagnosisAgentNode` |
| `business_overview_query` / `BusinessOverviewQueryTool` | MULTI 四域 + `revenue_query` |
| `BusinessOverviewAgent` / `BusinessOverviewAgentNode` | `MasterBusinessAgent` + MULTI_AGENT Composer |
| `StockReduceDeterministicRenderer` | `StockReduceAnswerPlan` + Composer Plan-first |
| `BusinessDiagnosisPlan` / `BusinessDiagnosisPlanBuilder` / `BusinessDiagnosisPlanNode` | `DiagnosisPlan` + `DiagnosisPlanBuilder` + `BusinessDiagnosisAgentV1` |
| `AiQuerySemanticTimeLexicon`、V2 `*Normalizer` / `*Gate` | V2 `time` + `semanticSlots`（见 `docs/ai/semantic-allowed-output-contract-design.md`） |
| `metric.rankingType` 作主 wire | `semanticSlots.structuredIntentDetailWire`（见 `docs/ai/semantic-allowed-output-contract-design.md`、`docs/ai/semantic-contract-strict-mode-plan.md`） |
| `WorkspaceRouterService` / `AiWorkspaceAccessGuard` | `AiUserContextResolver` + `AiPermissionGuard` |
| `GbAiChatServiceImpl` / `GbAiChatController`、经营类 `ai-skill-*.md`（cost / procurement / dish-cost 等） | **`POST /api/ai/runs`** Graph + `ai-prompts/composer/*.v1.md` |

---

## 维护

- 新增下线 Tool：在上表增加一行替代关系。  
- 实现进度：`docs/TODO_MULTI_AGENT.md`。
