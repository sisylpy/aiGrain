# 库房库存现量 Matrix P1 契约（Harness Engineering）

> **契约交叉引用**：wire 登记七步见 [`semantic-output-schema.md`](../../src/main/resources/ai-prompts/semantic/semantic-output-schema.md)；八域成熟度见 [`phase1-semantic-mainline-acceptance-summary.md`](./phase1-semantic-mainline-acceptance-summary.md) §4；Composer/fallback 见 [`harness-composer-architecture.md`](./harness-composer-architecture.md) §2.7。

实现：`WarehouseDrilldownMatrix` / `WarehouseDrilldownMatrixRow`  
Replay case：`WAREHOUSE_MATRIX_P1`

## 矩阵行

| rowId | wire | planType | stockFacet | 场景 | knownGap |
|-------|------|----------|------------|------|----------|
| WH-A | `warehouse_stock_overview` | `WAREHOUSE_STOCK_OVERVIEW` | OVERVIEW | A 现在库存怎么样 | — |
| WH-B | `warehouse_stock_amount_ranking` | `WAREHOUSE_GOODS_AMOUNT_RANKING_HIGH` | GOODS_RANKING_HIGH | B 哪个商品库存最多 | — |
| WH-C | `goods_stock_amount_ranking_low` | `WAREHOUSE_GOODS_AMOUNT_RANKING_LOW` | GOODS_RANKING_LOW | C 哪个商品库存最少 | — |
| WH-D | `store_stock_amount_ranking` | `WAREHOUSE_STORE_AMOUNT_RANKING` | STORE_RANKING | D 哪个门店库存最多 | — |
| WH-E | `warehouse_stock_overview` | `WAREHOUSE_STOCK_OVERVIEW` | OVERVIEW | E AAA 门店库存 | — |
| WH-F | `warehouse_stock_low_risk` | `WAREHOUSE_LOW_STOCK_RISK` | LOW_STOCK | F 有没有缺货 | `WAREHOUSE_OUT_OF_STOCK_STRICT_NOT_SUPPORTED` |
| WH-G | `warehouse_near_expiry` | `WAREHOUSE_STOCK_OVERVIEW` | NEAR_EXPIRY | G 有没有临期 | `WAREHOUSE_NEAR_EXPIRY_NOT_IN_TOOL` |
| WH-H | `warehouse_stock_amount_ranking` + 追问 | `WAREHOUSE_GOODS_AMOUNT_RANKING_HIGH` | GOODS_RANKING_HIGH | H 那哪个商品最多 | — |
| WH-I | `warehouse_stock_overview` + 门店追问 | `WAREHOUSE_STOCK_OVERVIEW` | OVERVIEW | I 那 AAA 呢 | — |

## 追问

- **H**：继承域与 scope/时间；切商品金额排行 wire；不得把上一轮门店排行 wire 带进本轮。
- **I**：继承时间；切单店总览 wire；不得继承 H 的商品排行 wire。

## Tool / Plan / Composer（Plan-first）

- Tool：`WAREHOUSE_STOCK_OVERVIEW`（参数沿用现有 dept/dis/start/stop/groupAgg）。
- Plan：`WarehouseAnswerPlanBuilder` 只读 `warehouseOverview` 信封；商品排行来自 `goodsStockAmountRanking` / `goodsStockAmountRankingAsc`（由 `_byGoods` 派生，无额外 SQL）。
- **Composer**：只读 `WarehouseAnswerPlan`；无 Plan 时固定 no-plan，**不**读 `toolResults` 拼 Top3。
- **已移除**：`WarehouseDeterministicRenderer`、`renderWarehouseStockFallback`、`AnswerComposerPayloadFactory` 库房 payload 拼装（**禁止**恢复）。

## 本地 Replay

- CaseId：`WAREHOUSE_MATRIX_P1`；脚本：`bash scripts/harness/replay-warehouse-matrix-p1.sh`（footer：`caseId` / `overallPass` / `failureCount`）

## knownGap

- **F**：`lowStockItems` 为账面偏低启发式，非严格缺货口径。
- **G**：无保质期/临期字段与专链 SQL。
- Harness 须断言 `warehouseKnownGap`（或同类字段）**存在**，preview **不得**掩盖为已实现能力。
