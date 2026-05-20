# Skills（能力样板索引 · 轻量）

「Skill」在此仓库中指 **固定输入输出契约 + 可调 Tool/Plan 组合 + Harness 观测**，对标 Claude Code 的可文档化能力单元；**无** Skill 商店、**无** 前端配置页。

**现网 Business Tool id**（Skill 文档中引用 Tool 时须与此一致）：`revenue_query`、`purchase_overview`、`warehouse_stock_overview`、`stock_reduce_query`、`dish_profit_analysis`。成本链另含 **CostDiagnosisAgent** + **CostMarginDerivation**（**无**独立 `gross_margin_calculator` Tool）。

**勿**把 `out/replay-*` 抓包或历史 harness 输出当作当前 Skill 契约；验收以 `docs/API_INTEGRATION.md`、`docs/PERMISSION_MODEL.md`、`docs/ai/phase2-tool-request-sql-input-plan.md` 及最新 harness **expected** 为准。

## Skill 文档

| Skill 文档 | 说明 | 现网 Tool |
|------------|------|-----------|
| [supplier-drilldown-skill.md](./supplier-drilldown-skill.md) | 供货商金额排行 → Top 供货商商品/单价明细（**D-13.1 封版**） | **`purchase_overview`** |

## Historical removed（Skill 层勿再编排）

`purchase_query`、`stock_query`、`dish_sales_query`、`gross_margin_calculator`、`business_overview_query` 及对应 `*QueryTool` / `*CalculatorTool` / **BusinessOverviewQueryTool** 已删。索引见 `docs/legacy-reference/*-removed.md`。

新增 Skill：在本目录增加 `*-skill.md`，更新上表，并在 `docs/ai/protocols/README.md` 或相关协议中链回。
