# Agents（领域 Agent 索引 · 轻量）

本目录用于 **按领域归类说明**，便于与 Skills / Protocols 交叉引用；**不是** Agent 托管平台。

**事实来源（现网）**：`com.nongxinle.ai.agent.business.*`、`AiBusinessToolIds`、`docs/PERMISSION_MODEL.md`。  
**勿**把 `out/replay-*` JSON 或历史 demo 当作当前 Tool 表。

## 现网 Business Agent ↔ Tool（`BUSINESS_CHAT`）

| Agent（编排名） | 典型 Tool id | 典型 path / intent |
|-----------------|--------------|-------------------|
| **RevenueAgent** | `revenue_query` | `REVENUE_OVERVIEW` / `revenue_overview_path` |
| **PurchaseAgent** | `purchase_overview` | `PURCHASE_OVERVIEW` / `purchase_overview_path` |
| **WarehouseStockAgent** | `warehouse_stock_overview` | `WAREHOUSE_STOCK_OVERVIEW` / `warehouse_stock_overview_path` |
| **StockReduceAgent** | `stock_reduce_query` | `STOCK_REDUCE_QUERY` / `stock_reduce_query_path` |
| **DishProfitAgent** | `dish_profit_analysis` | `DISH_PROFIT` / `dish_profit_path`；**D-8** 亦服务 `DISH_SALES_QUERY` / `dish_sales_query_path`（同一 Tool） |
| **CostDiagnosisAgent**（节点，非 Tool id） | 四 Tool 链 + 内部 **`CostMarginDerivation`** | `COST_DIAGNOSIS` / `cost_diagnosis_path`：`revenue_query` → `purchase_overview` → `stock_reduce_query` → `dish_profit_analysis` |
| **Master / 经营综合** | 四域并列 | `BUSINESS_OVERVIEW` / `business_overview_path` → **MULTI_AGENT** 四 Tool（**非** classic 六工具链） |
| **Master / 经营诊断** | 采购 + 出库 + 菜品等（按矩阵） | `BUSINESS_DIAGNOSIS` / `business_diagnosis_path` |

**库存边界**：现量/库房 → **`warehouse_stock_overview`**；出库/核销 → **`stock_reduce_query`**。

## Historical removed（勿写入「当前 Agent 可用 Tool」）

| 已删 | 替代 |
|------|------|
| `purchase_query` / **PurchaseQueryTool** | `purchase_overview` |
| `stock_query` / **StockQueryTool** | `warehouse_stock_overview` |
| `dish_sales_query` / **DishSalesQueryTool** | `dish_profit_analysis`（保留 D-8 intent/path） |
| `gross_margin_calculator` / **GrossMarginCalculatorTool** | **CostMarginDerivation** + **CostDiagnosisAgent** |
| `business_overview_query` / **BusinessOverviewQueryTool** | MULTI 四域 + `revenue_query` |
| **classic business overview**（`BusinessOverviewAgentNode` 六工具序） | 见 `docs/AI_MAINLINE_INDEX.md` |

## 本目录专题

| 文档 | 说明 |
|------|------|
| （采购锚 execution 见 Skills） | Planner / Executor 以 **`purchase_overview`** 为主 Tool，见 [`purchase-answer-plan.md`](../purchase-answer-plan.md) |

新增领域 Agent 说明时，在本目录增加短文 md，更新上表；契约交叉引用见 `docs/AI_MAINLINE_INDEX.md`、`docs/ai/semantic-allowed-output-contract-design.md`、`docs/ai/result-anchor-protocol.md`。
