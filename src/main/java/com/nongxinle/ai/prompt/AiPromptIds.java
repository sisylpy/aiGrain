package com.nongxinle.ai.prompt;

/**
 * 集中声明 Harness 可读写的 promptId；与 {@code src/main/resources/ai-prompts/} 下文件名一一对应。
 */
public final class AiPromptIds {

    private AiPromptIds() {
    }

    /** v2：输入为结构化 JSON（含 previousTurn、visibleStores）；生产语义解析唯一 prompt。 */
    public static final String SEMANTIC_QUERY_PARSER_V2 = "semantic.query_parser.v2";

    /** v1：省略追问补全为完整问句；输出 completedUserQuery 或 clarificationQuestion。 */
    public static final String FOLLOWUP_QUERY_REWRITER_V1 = "semantic.followup_query_rewriter.v1";

    public static final String COMPOSER_COST_DIAGNOSIS_V1 = "composer.cost_diagnosis.v1";
    public static final String COMPOSER_REVENUE_OVERVIEW_V1 = "composer.revenue_overview.v1";
    public static final String COMPOSER_PURCHASE_OVERVIEW_V1 = "composer.purchase_overview.v1";
    public static final String COMPOSER_STOCK_REDUCE_V1 = "composer.stock_reduce.v1";
    public static final String COMPOSER_DISH_PROFIT_V1 = "composer.dish_profit.v1";
    public static final String COMPOSER_DIAGNOSIS_V1 = "composer.diagnosis.v1";
    public static final String COMPOSER_DIAGNOSIS_STORE_PRIORITY_V1 = "composer.diagnosis_store_priority.v1";
    public static final String COMPOSER_WAREHOUSE_V1 = "composer.warehouse.v1";
}
