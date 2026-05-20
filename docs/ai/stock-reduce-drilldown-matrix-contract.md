# 出库 / 核销 Matrix P1 契约（Harness Engineering）

实现：`StockReduceDrilldownMatrix` / `StockReduceDrilldownMatrixRow`  
Replay case：`STOCK_REDUCE_MATRIX_P1`

## 矩阵行

| rowId | wire | planType | 场景 |
|-------|------|----------|------|
| SR-A | `stock_reduce_overview` | `STOCK_REDUCE_OVERVIEW` | A / H |
| SR-B | `store_outbound_amount_ranking` | `STOCK_REDUCE_STORE_AMOUNT_RANKING` | B |
| SR-C | `produce_consume` | `STOCK_REDUCE_PRODUCTION_OVERVIEW` | C |
| SR-D | `waste` | `STOCK_REDUCE_WASTE_OVERVIEW` | D / I |
| SR-E | `loss` | `STOCK_REDUCE_LOSS_OVERVIEW` | E / J |
| SR-F | `return` | `STOCK_REDUCE_RETURN_OVERVIEW` | F |
| SR-G | `goods_outbound_ranking` | `STOCK_REDUCE_GOODS_AMOUNT_RANKING` | 全类型商品金额排行 |
| SR-GW | `goods_outbound_ranking` + TYPE2 facet | `STOCK_REDUCE_GOODS_AMOUNT_RANKING` | G / K；**knownGap** |
| SR-I | `waste`（facet 切换） | `STOCK_REDUCE_WASTE_OVERVIEW` | I |
| SR-J | `loss`（facet 切换） | `STOCK_REDUCE_LOSS_OVERVIEW` | J |

## knownGap

`GOODS_WASTE_RANKING_TYPE2_SQL_NOT_FILTERED`：语义要求废弃商品排行，但 `stock_reduce_query` harness SQL 未按 type2 过滤 `topGoodsOutboundBySubtotal`。

## Composer（Plan-first）

- **事实源**：`StockReduceAnswerPlan` + Tool `stock_reduce_query`；Composer 只宣读 Plan 与 `limitations`。
- **无 Plan**：`composeStockReduceNoPlanFallback`；**不**恢复 `StockReduceDeterministicRenderer` / `renderStockReduceToolFallback`（见 `docs/legacy-reference/stock-reduce-deterministic-renderer-removed.md`）。
- **knownGap**（如 SR-GW）：语义/wire 可对但 SQL/宣读标 gap，**不是**假成功。

## 本地 Replay

- CaseId：`STOCK_REDUCE_MATRIX_P1`；脚本：`bash scripts/harness/replay-stock-reduce-matrix-p1.sh`

## 追问

- I/J：继承时间/门店范围；wire 切到 `waste` / `loss`；不得继承上一轮排行 wire。
- K：wire=`goods_outbound_ranking` + `metric.stockReduceType=TYPE2`；须暴露 knownGap。
