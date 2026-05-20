# stock_query / StockQueryTool 已移除

> **D-CLEAN-STOCK-QUERY-P2**（2026-05-20）  
> **勿与现网语义混淆**：语义 wire **`"STOCK_QUERY"`** 在 merge 层仍映射到 **`WAREHOUSE_STOCK_OVERVIEW` + `warehouse_stock_overview_path`**；仅独立 Tool id **`stock_query`** 删除。

## 删除项

| 项 | 说明 |
|----|------|
| `src/main/java/.../StockQueryTool.java` | 独立库存快照 Tool |
| Tool id **`stock_query`** | `AiBusinessToolIds` / Planner / `toolResults` 键 |
| `toolResults["stock_query"]` fallback | Composer / Renderer **不再**读取（**D-CLEAN-WAREHOUSE-P1B**） |
| `stockSnapshotHasSignal` 等拼库房 fallback | 见 `inventory-domain-capability-matrix.md` |

## 现网替代

| 能力 | 现网 Tool id | path |
|------|--------------|------|
| 库存现量 / 库房概览 | **`warehouse_stock_overview`** | `warehouse_stock_overview_path` |
| 出库 / 核销 | **`stock_reduce_query`** | `stock_reduce_query_path` |

**`WarehouseStockOverviewTool`** 聚合：库存种数/金额/重量、区间入库、核销分型汇总、低库存/高库存等列表；SSE **`answer_delta.data.warehouseOverview`**。

## 禁止

- DataPlanner / Composer / Harness **不得**再编排或断言 **`stock_query`**
- 脚本 **不得**恢复 `stock_query` / `stockSnapshotHasSignal` fallback（见 `scripts/gen_deterministic_renderer.py` 头注释）

## 相关

- [stock-reduce-deterministic-renderer-removed.md](stock-reduce-deterministic-renderer-removed.md)（出库 Renderer fallback，非本 Tool）
- [inventory-domain-capability-matrix.md](../ai/inventory-domain-capability-matrix.md)（现网库存域能力表）
