# 营业额 / 营收 Matrix P1 契约（Harness Engineering）

实现：`RevenueDrilldownMatrix` / `RevenueDrilldownMatrixRow`  
Replay case：`REVENUE_MATRIX_P1`

## 矩阵行

| rowId | wire | planType | 场景 | knownGap |
|-------|------|----------|------|----------|
| RV-A | `revenue_overview_summary` | `REVENUE_OVERVIEW` | A 本月总览 | — |
| RV-B | `revenue_store_amount_ranking` | `REVENUE_STORE_AMOUNT_RANKING` | B 门店排行 | — |
| RV-C | `revenue_single_store_overview` | `REVENUE_OVERVIEW` | C 单店 AAA | — |
| RV-D | `revenue_store_compare` | `REVENUE_STORE_AMOUNT_RANKING` | D 两店对比 | `REVENUE_STORE_COMPARE_NOT_PAIRWISE_ONLY_RANKING` |
| RV-E | （同 RV-A） | `REVENUE_OVERVIEW` | E 上月总览 | — |
| RV-F | `revenue_overview_summary` + 时间追问 | `REVENUE_OVERVIEW` | F 那上个月呢 | — |
| RV-G | `revenue_store_amount_ranking` + 排行追问 | `REVENUE_STORE_AMOUNT_RANKING` | G 那哪个门店最高 | — |
| RV-H | `revenue_period_compare` | `REVENUE_OVERVIEW` | H 本月和上月比 | `REVENUE_PERIOD_COMPARE_MO_M_NOT_IMPLEMENTED` |
| RV-I | `revenue_daily_amount_ranking` | `REVENUE_DAILY_AMOUNT_RANKING` | I 哪天最高 | `REVENUE_DAILY_RANKING_ARGMAX_DATE_MISSING` |
| RV-J | `revenue_trend` | `REVENUE_OVERVIEW` | J 趋势 | `REVENUE_TREND_SERIES_NOT_IMPLEMENTED` |

## 追问

- **F**：继承业务域 Revenue 与集团/门店 scope；**时间必须切到上月**（Harness 断言 `p0`–`p1` + `CURRENT_MESSAGE_EXPLICIT` / `SEMANTIC_EXPLICIT`，**禁止** `INHERITED_PREVIOUS` 沿用上一轮本月窗）；wire 保持 `revenue_overview_summary`；不得继承 D 的 compare / ranking wire。
- **G**：继承 F 的时间范围；wire 切门店排行；不得把 compare wire 带进排行轮。

## Composer（Plan-first）

- **事实源**：`RevenueAnswerPlan` + Tool `revenue_query` 信封；Composer 只展开 Plan 内字段与 `limitations`，**不**从 `toolResults` 拼排行/金额。
- **无 Plan**：固定 no-plan（`StubAnswerComposerNode`），**不**恢复 `renderRevenueEnvelopeFallback` / raw tool fallback。
- **knownGap**：上表 `knownGap` 列为 **P1 能力边界**（路由/wire 可对、执行或宣读为 gap），Harness 须断言 gap 存在且 preview **不**假装已实现。

## 本地 Replay

- CaseId：`REVENUE_MATRIX_P1`；脚本：`bash scripts/harness/replay-revenue-matrix-p1.sh`（footer 输出 `caseId` / `overallPass` / `failureCount`）。

## knownGap 说明

- **D**：Tool 仅 `storeRevenueRanking`，无两店 pairwise compare 专链。
- **H**：无 `period_compare` planType / 双窗 SQL。
- **I**：`rawStats.max_daily_revenue` 无 argmax 日历日（Builder 已 `businessDateKnown=false`）。
- **J**：无日序列 trend planType。
