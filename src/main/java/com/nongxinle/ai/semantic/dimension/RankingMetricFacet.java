package com.nongxinle.ai.semantic.dimension;

/** 裸维度切换目标排行指标面（Catalog contract 派生依据）。 */
public enum RankingMetricFacet {
    SOLD_PORTIONS,
    SALES_AMOUNT,
    ACTUAL_COST,
    GROSS_MARGIN_RATE,
    /** 菜品毛利额（标价收入−实际成本，元）。 */
    GROSS_PROFIT_AMOUNT
}
