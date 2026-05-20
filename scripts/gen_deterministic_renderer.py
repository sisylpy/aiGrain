#!/usr/bin/env python3
"""
DEPRECATED — do not run to regenerate DeterministicAnswerRenderer.

Composer / Renderer 已收口为 AnswerPlan-first 手写维护；本脚本不再从 StubAnswerComposerNode
按行号抽取 Tool fallback 块覆盖 DeterministicAnswerRenderer。

=== FORBIDDEN TO REINTRODUCE ===
- StockReduceDeterministicRenderer, renderStockReduceToolFallback
- renderRevenueEnvelopeFallback, revenueOverviewDeterministicFallback
- WarehouseDeterministicRenderer, renderWarehouseStockFallback
- renderPurchaseCostFallback, renderBusinessOverviewFallback, renderDishProfitFallback (overview/tool)
- stock_query, purchase_query, dish_sales_query, gross_margin_calculator, business_overview_query
- LLM + Tool fallback 公共入口（COMPOSER_GENERIC_CHAT_V1 / COMPOSER_WAREHOUSE_V1 等）

See docs/legacy-reference/stock-reduce-deterministic-renderer-removed.md
"""
import sys

print(
    "gen_deterministic_renderer.py is deprecated. "
    "DeterministicAnswerRenderer is hand-maintained (AnswerPlan-first).",
    file=sys.stderr,
)
sys.exit(1)
