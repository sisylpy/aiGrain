package com.nongxinle.ai.prompt;

/**
 * 集中声明 Harness 可读写的 promptId；与 {@code src/main/resources/ai-prompts/} 下文件名一一对应。
 */
public final class AiPromptIds {

    private AiPromptIds() {
    }

    public static final String SEMANTIC_QUERY_PARSER_V1 = "semantic.query_parser.v1";

    /** v2：输入为结构化 JSON（含 previousTurn、visibleStores）；生产主链路仍以 v1 为主，待 Resolver 切换。 */
    public static final String SEMANTIC_QUERY_PARSER_V2 = "semantic.query_parser.v2";

    public static final String COMPOSER_COST_DIAGNOSIS_V1 = "composer.cost_diagnosis.v1";
    public static final String COMPOSER_BUSINESS_OVERVIEW_V1 = "composer.business_overview.v1";
    public static final String COMPOSER_REVENUE_OVERVIEW_V1 = "composer.revenue_overview.v1";
    public static final String COMPOSER_PURCHASE_OVERVIEW_V1 = "composer.purchase_overview.v1";
    public static final String COMPOSER_STOCK_REDUCE_V1 = "composer.stock_reduce.v1";
    public static final String COMPOSER_DISH_PROFIT_V1 = "composer.dish_profit.v1";
    public static final String COMPOSER_DIAGNOSIS_V1 = "composer.diagnosis.v1";
    public static final String COMPOSER_DIAGNOSIS_STORE_PRIORITY_V1 = "composer.diagnosis_store_priority.v1";
    public static final String COMPOSER_WAREHOUSE_V1 = "composer.warehouse.v1";
    public static final String COMPOSER_GENERIC_CHAT_V1 = "composer.generic_chat.v1";
}
