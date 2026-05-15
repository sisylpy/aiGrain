package com.nongxinle.ai.planner;

/**
 * C-5：与 {@link PlannerStep#getTargetTool()} / Agent 命名对齐的<strong>文档与常量键</strong>，
 * 供后续真实 {@link PlannerStepExecutor} 实现做路由表；<strong>本类无任何业务调用</strong>。
 * <p>
 * 与线上 Tool id 的细节差异（例如营收 query vs overview）见设计文档 §C-5 对照表。
 * </p>
 */
public final class PlannerAdapterToolKeys {

    public static final String REVENUE_OVERVIEW = "revenue_overview";
    public static final String PURCHASE_OVERVIEW = "purchase_overview";
    public static final String STOCK_REDUCE_QUERY = "stock_reduce_query";
    public static final String DISH_PROFIT_ANALYSIS = "dish_profit_analysis";
    public static final String BUSINESS_DIAGNOSIS_V1 = "business_diagnosis_v1";
    public static final String RECOMMENDATION_PLANNER_V1 = "recommendation_planner_v1";

    private PlannerAdapterToolKeys() {
    }
}
