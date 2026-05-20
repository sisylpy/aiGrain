# StockReduceDeterministicRenderer 已移除（出库 Composer 收口尾巴）

> **归类**：**stock_reduce legacy renderer 清理**（与 `StockReduceAnswerPlan` / D-2H Composer 主线对齐）。  
> **不属于** [classic business overview 删除](classic-business-overview-removed.md)（P1A–P1F）；勿写入 classic overview 已删清单。

## 删除项

| 路径 | 说明 |
|------|------|
| `src/main/java/com/nongxinle/ai/composer/renderer/StockReduceDeterministicRenderer.java` | 从 `stock_reduce_query` Tool envelope 拼确定性兜底正文 |
| `DeterministicAnswerRenderer.renderStockReduceToolFallback` | 仅委托上述 Renderer；已一并移除 wiring |

## 删除前引用（HEAD）

| 层级 | 引用方 |
|------|--------|
| 直接 | `DeterministicAnswerRenderer`（字段注入 + `renderStockReduceToolFallback`） |
| 间接 | `StubAnswerComposerNode`：当 `stock_reduce_query_path` 且 AnswerPlan 宣读为空时调用 `renderStockReduceToolFallback` |

**结论**：删除前 **并非** `src/main` 零引用（存在 1 直接 + 1 间接调用链）；删除后 `src/main` 对类名 **零引用**。

## 删除后主线（当前）

| 场景 | Composer 路径 |
|------|----------------|
| `STOCK_REDUCE_QUERY` / `stock_reduce_query_path` | `isStockReduceComposerMainline` → `composeStockReduceDeterministicFromAnswerPlan`；无计划时 `composeStockReduceNoPlanFallback`（**不再**读 Tool envelope 拼长文） |
| `BUSINESS_OVERVIEW` MULTI 四域汇总 | `composeStockReduceDeterministicFromAnswerPlan(state.getStockReduceAnswerPlan(), …)` |
| `cost_insight` / `cost_diagnosis` | `renderCostFallback(AiCostDiagnosisResult)`；**不**经过本 Renderer |
| `warehouse_stock` | `renderWarehouseStockFallback`；库房结构化里可 **只读** `STOCK_REDUCE_QUERY` 指标行，**不**依赖本 Renderer |
| `business_diagnosis` | `DiagnosisPlan` + `DiagnosisDeterministicRenderer`；出库事实来自 `StockReduceAnswerPlan` 挂载，**不**依赖本 Renderer |

契约与冻结说明见：`docs/ai/stock-reduce-answer-plan.md`（2026-05-12 阶段冻结）。

## 与 P0（D-CLEAN-GRAPH-P0）边界

**P0 仅包含** stub / 未接入主线的删除：

- `EchoContextTool`
- `CostInsightAgent`（stub bean）
- `BusinessDiagnosisAgent`（stub bean）
- `BusinessAgentNames` 中对应常量、`AiPermissionGuard` / `AiAnswerBoundary` 中 `echo_context` 等

**P0 不包含**本 Renderer。`BUSINESS_DIAGNOSIS` / `COST_INSIGHT` 在 `src/main` 中的大量命中为 **活跃主线**（`BusinessDiagnosisAgentV1`、`CostDiagnosisAgentNode`、`FollowUpPathKind` 等），**不是** P0 删除失败。

## 残留 / 后续（非阻塞）

| 项 | 说明 |
|----|------|
| `scripts/gen_deterministic_renderer.py` | **已清理（D-CLEAN-STOCK-RENDERER-SCRIPT-FIX）**：raw tool fallback 模板已移除；**禁止**重新生成 `StockReduceDeterministicRenderer` / `renderStockReduceToolFallback` / `stockReduceQueryDeterministicFallback` |
| `src/test` | 无对 `StockReduceDeterministicRenderer` 的引用 |

## 是否需要恢复

**不需要**。出库正文已由 `StockReduceAnswerPlan` + `StubAnswerComposerNode.composeStockReduceDeterministicFromAnswerPlan` 承担；无计划时的 Tool-envelope 长文兜底与「仅朗读 AnswerPlan」冻结目标冲突，属 intentional 行为收窄。
