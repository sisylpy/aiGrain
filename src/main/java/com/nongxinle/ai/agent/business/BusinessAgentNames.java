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

    private BusinessAgentNames() {
    }
}
