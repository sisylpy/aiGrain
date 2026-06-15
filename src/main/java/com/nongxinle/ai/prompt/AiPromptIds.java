package com.nongxinle.ai.prompt;

/**
 * 集中声明 Harness 可读写的 promptId；与 {@code src/main/resources/ai-prompts/} 下文件名一一对应。
 */
public final class AiPromptIds {

    private AiPromptIds() {
    }

    /** v2：输入为结构化 JSON（含 previousTurn、visibleStores）；生产语义解析唯一 prompt。 */
    public static final String SEMANTIC_QUERY_PARSER_V2 = "semantic.query_parser.v2";

    /** v1：SemanticIntake — 话术规范化 + 一级业务域选择 + 多问题识别。 */
    public static final String SEMANTIC_INTAKE_V1 = "semantic.intake.v1";

    public static final String COMPOSER_COST_DIAGNOSIS_V1 = "composer.cost_diagnosis.v1";
    public static final String COMPOSER_REVENUE_OVERVIEW_V1 = "composer.revenue_overview.v1";
    public static final String COMPOSER_PURCHASE_OVERVIEW_V1 = "composer.purchase_overview.v1";
    public static final String COMPOSER_STOCK_REDUCE_V1 = "composer.stock_reduce.v1";
    public static final String COMPOSER_DISH_PROFIT_V1 = "composer.dish_profit.v1";
    public static final String COMPOSER_DIAGNOSIS_V1 = "composer.diagnosis.v1";
    public static final String COMPOSER_DIAGNOSIS_STORE_PRIORITY_V1 = "composer.diagnosis_store_priority.v1";
    public static final String COMPOSER_WAREHOUSE_V1 = "composer.warehouse.v1";

    /** 菜单经营专家表达层：只读 AnswerPlan / card payload，不读 toolResults。 */
    public static final String COMPOSER_MENU_EXPERT_RUNTIME_V1 = "composer.menu_expert.runtime.v1";

    /** 经营概览营业额卡：菜品销量原因 Agent（只读 fact pack）。 */
    public static final String BUSINESS_OVERVIEW_DISH_SALES_REASON_AGENT_V1 =
            "business-overview-dish-sales-reason-agent.v1";

    /** v2：本期 vs 约 30 天基线，解释营业额高/低原因。 */
    public static final String BUSINESS_OVERVIEW_DISH_SALES_REASON_AGENT_V2 =
            "business-overview-dish-sales-reason-agent.v2";

    /** 店长工作记录：书面整理 + 一级分类（单次 LLM）。 */
    public static final String WORK_RECORD_POLISH_CLASSIFY_V1 = "workrecord.polish_classify.v1";
}
