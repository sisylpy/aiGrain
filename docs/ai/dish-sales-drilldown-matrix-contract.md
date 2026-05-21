# DishSales 菜品销量下钻矩阵契约（Phase 1）

> **契约交叉引用**：wire 登记七步见 [`semantic-output-schema.md`](../../src/main/resources/ai-prompts/semantic/semantic-output-schema.md)；八域成熟度见 [`phase1-semantic-mainline-acceptance-summary.md`](./phase1-semantic-mainline-acceptance-summary.md) §4；Composer/fallback 见 [`harness-composer-architecture.md`](./harness-composer-architecture.md) §2.7。

> 执行链：`semanticSlots` / `structuredIntentDetailWire` → **DishSalesDrilldownMatrix** → Tool `dish_profit_analysis` → **DishSalesAnswerPlan** → Composer 只读 Plan（无 toolResults 拼事实）。

## 矩阵行（A–J）

| 行 | 问法示例 | wire（canonical） | planType | knownGap |
|----|----------|-------------------|----------|----------|
| DS-A | 这个月哪个菜卖得最好？ | `dish_sales_count_ranking_high` | `DISH_SALES_COUNT_RANKING_HIGH` | — |
| DS-B | 哪个菜销量最高？ | 同 DS-A | 同 DS-A | — |
| DS-C | 哪个菜销量最低？ | `dish_sales_count_ranking_low` | `DISH_SALES_COUNT_RANKING_LOW` | — |
| DS-D | 核桃芽菜西芹这个月卖了多少份？ | `dish_sales_single_dish` | `DISH_SALES_SINGLE_DISH` | — |
| DS-E | AAA 门店哪个菜卖得最多？ | `dish_sales_store_ranking` | `DISH_SALES_COUNT_RANKING_HIGH`（STORE scope） | — |
| DS-F | AAA 门店核桃芽菜西芹卖了多少？ | `dish_sales_store_single_dish` | `DISH_SALES_SINGLE_DISH`（STORE scope） | — |
| DS-G | 那上个月呢？ | 时间追问；继承域/菜名/门店，**时间切上月** | 继承排行/单菜 planType | Harness 禁止 `INHERITED_PREVIOUS` |
| DS-H | 那哪个菜最高？ | `dish_sales_count_ranking_high`（RANKING_FOLLOWUP） | `DISH_SALES_COUNT_RANKING_HIGH` | — |
| DS-I | 那毛利呢？ | `dish_gross_margin_query`（跨域） | — | `DISH_SALES_CROSS_DOMAIN_DISH_PROFIT_NOT_IN_P1` |
| DS-J | 菜品销量趋势怎么样？ | `dish_sales_trend` | — | `DISH_SALES_TREND_SERIES_NOT_IMPLEMENTED` |

## Wire 别名（`AiQuerySemanticLexicon`）

- `dish_sales_overview` → `dish_sales_count_ranking_high`
- `dish_sales_ranking_high` → `dish_sales_count_ranking_high`
- `dish_sales_ranking_low` → `dish_sales_count_ranking_low`

## 追问规则

1. **时间（G）**：`TIME_FOLLOWUP`；继承业务域与菜名/门店 anchor；**不得**用 `INHERITED_PREVIOUS` 沿用本月。
2. **排行（H）**：`RANKING_FOLLOWUP`；继承上一轮时间与门店范围；切到销量排行 wire；**不得**错误继承上一轮单菜 wire。
3. **跨域毛利（I）**：仍在 `dish_sales_query_path` 时若 wire 为毛利类 → knownGap，不假装 DishSales 已处理。
4. **趋势（J）**：无日序列 planType → knownGap，Composer 仅宣读 limitations。

## Composer（Plan-first）

- **事实源**：`DishSalesAnswerPlan` + Tool `dish_profit_analysis` 信封；Composer 只宣读 Plan，**不**拼 `toolResults` Top3。
- **无 Plan**：固定 no-plan；**不**恢复已删 `*DeterministicRenderer` / `AnswerComposerPayloadFactory`。
- **knownGap**（如 DS-I/J）：为 **能力边界**，须暴露 `dishSalesKnownGap`，**不是** Harness 假成功。

## Harness

- CaseId：`DISH_SALES_MATRIX_P1`
- 脚本：`bash scripts/harness/replay-dish-sales-matrix-p1.sh`（footer：`caseId` / `overallPass` / `failureCount`）
- **本地 replay 状态（2026-05）**：曾因 **数据库连接不稳定** 暂缓本地验收；代码层语义/路由待网络与 DB 稳定后 **单独重跑**，不视为业务回退。

## Debug 字段（摘要顶层）

- `dishSalesMatrixRowId`
- `dishSalesMatrixWireMissing`
- `dishSalesStructuredIntentDetailWire`
- `dishSalesAnswerPlanType`
- `dishSalesKnownGap`
