package com.nongxinle.ai.followup;

/** 上一轮经营主线类型；用于与其它域话题冲突校验。 */
public enum FollowUpPathKind {
    DISH_PROFIT,
    BUSINESS_OVERVIEW,
    WAREHOUSE_STOCK,
    COST_INSIGHT,
    PURCHASE_COST,
    /** {@link com.nongxinle.ai.core.AiRunState#isPurchaseOverviewPath} 采购概览专线（非成本洞察混链）。 */
    PURCHASE_OVERVIEW,
    /** {@link com.nongxinle.ai.context.AiResolvedQueryIntent#PATH_REVENUE_OVERVIEW} 日营业额 / 营收专线。 */
    REVENUE_OVERVIEW,
    /** {@link com.nongxinle.ai.context.AiResolvedQueryIntent#PATH_STOCK_REDUCE_QUERY} 出库/核销基础查询专线。 */
    STOCK_REDUCE_QUERY,
    /** {@link com.nongxinle.ai.context.AiResolvedQueryIntent#PATH_BUSINESS_DIAGNOSIS} 经营诊断编排。 */
    BUSINESS_DIAGNOSIS,
}
