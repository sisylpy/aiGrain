package com.nongxinle.ai.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查询意图：<b>主链路内容由</b> {@link com.nongxinle.ai.semantic.AiQuerySemanticParseResult}
 * 合并得到；此类仅承载常量与数据结构，不再对用户消息做关键词路由。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiResolvedQueryIntent {

    public static final String BUSINESS_OVERVIEW = "BUSINESS_OVERVIEW";
    public static final String PURCHASE_OVERVIEW = "PURCHASE_OVERVIEW";
    public static final String WAREHOUSE_STOCK_OVERVIEW = "WAREHOUSE_STOCK_OVERVIEW";
    public static final String DISH_PROFIT = "DISH_PROFIT";
    /** 菜品销量/销售额排行等（D-8）；语义契约与执行链路分阶段落地。 */
    public static final String DISH_SALES_QUERY = "DISH_SALES_QUERY";
    /** 单菜菜品成本+销售分析（{@code dish_cost_analysis} Tool）。 */
    public static final String DISH_COST_ANALYSIS = "DISH_COST_ANALYSIS";
    /** 菜单经营顾问（老板经营建议层；与 DISH_PROFIT 独立）。 */
    public static final String MENU_OPERATION = "MENU_OPERATION";
    public static final String COST_DIAGNOSIS = "COST_DIAGNOSIS";
    public static final String STOCK_REDUCE_QUERY = "STOCK_REDUCE_QUERY";
    public static final String BUSINESS_DIAGNOSIS = "BUSINESS_DIAGNOSIS";
    public static final String REVENUE_OVERVIEW = "REVENUE_OVERVIEW";

    public static final String PATH_BUSINESS_OVERVIEW = "business_overview_path";
    public static final String PATH_PURCHASE_OVERVIEW = "purchase_overview_path";
    public static final String PATH_WAREHOUSE_STOCK = "warehouse_stock_overview_path";
    public static final String PATH_DISH_PROFIT = "dish_profit_path";
    public static final String PATH_DISH_SALES_QUERY = "dish_sales_query_path";
    public static final String PATH_DISH_COST_ANALYSIS = "dish_cost_analysis_path";
    /** 菜单经营顾问专线（P1-A/P1-B）。 */
    public static final String PATH_MENU_OPERATION = "menu_operation_path";
    public static final String PATH_COST_DIAGNOSIS = "cost_diagnosis_path";
    public static final String PATH_STOCK_REDUCE_QUERY = "stock_reduce_query_path";
    public static final String PATH_BUSINESS_DIAGNOSIS = "business_diagnosis_path";
    public static final String PATH_REVENUE_OVERVIEW = "revenue_overview_path";

    private String intentCode;
    private String pathCode;
    private String topic;

    private boolean inheritedFromPreviousTurn;
    private String inheritedFromIntentCode;

    private String structuredIntentDetail;
    private String purchaseSourceType;

    /** @deprecated Java 关键词不再解析用户消息；请使用语义 LLM 合并结果（通常为空草稿）。 */
    @Deprecated
    public static AiResolvedQueryIntent fromUserMessage(String rawMessage) {
        return AiResolvedQueryIntent.builder().build();
    }


}
