package com.nongxinle.ai.agent.business;

/**
 * {@link BusinessSubAgent#agentName()} 的稳定常量。
 */
public final class BusinessAgentNames {

    public static final String REVENUE_OVERVIEW = "revenue_overview";
    public static final String PURCHASE_OVERVIEW = "purchase_overview";
    /** 与 {@link com.nongxinle.ai.tool.business.AiBusinessToolIds#STOCK_REDUCE_QUERY} 对齐 */
    public static final String STOCK_REDUCE_QUERY = "stock_reduce_query";

    /** 与 {@link com.nongxinle.ai.tool.business.AiBusinessToolIds#DISH_PROFIT_ANALYSIS} 对齐 */
    public static final String DISH_PROFIT_ANALYSIS = "dish_profit_analysis";

    /** 库存概览专线（{@link com.nongxinle.ai.tool.business.AiBusinessToolIds#WAREHOUSE_STOCK_OVERVIEW}） */
    public static final String WAREHOUSE_STOCK = "warehouse_stock";

    /** 供货/供货商专项分析（与采购 Tool 同源，收窄意图） */
    public static final String SUPPLIER_ANALYSIS = "supplier_analysis";

    private BusinessAgentNames() {
    }
}
