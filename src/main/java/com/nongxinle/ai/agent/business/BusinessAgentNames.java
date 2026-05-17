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

    /** 旧版六工具经营看板链（非 MULTI_AGENT 四域） */
    public static final String BUSINESS_OVERVIEW = "business_overview";

    /** 四域 / 经营诊断 Harness 编排体（{@link com.nongxinle.ai.agent.business.BusinessDiagnosisAgent}） */
    public static final String BUSINESS_DIAGNOSIS = "business_diagnosis";

    /** 库存概览专线（{@link AiBusinessToolIds#WAREHOUSE_STOCK_OVERVIEW}） */
    public static final String WAREHOUSE_STOCK = "warehouse_stock";

    /** 供货/供货商专项分析（与采购 Tool 同源，收窄意图） */
    public static final String SUPPLIER_ANALYSIS = "supplier_analysis";

    /** 成本洞察链（revenue + purchase + stock_reduce + dish_sales + gross_margin） */
    public static final String COST_INSIGHT = "cost_insight";

    private BusinessAgentNames() {
    }
}
