package com.nongxinle.ai.prompt;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * promptId → classpath 路径（无前缀）；实际加载由 {@link AiPromptService} 完成。
 */
@Component
public class AiPromptRegistry {

    private final Map<String, String> idToClasspathResource;

    public AiPromptRegistry() {
        Map<String, String> m = new HashMap<>();
        m.put(AiPromptIds.SEMANTIC_QUERY_PARSER_V1, "ai-prompts/semantic/query_semantic_parser.v1.md");
        m.put(AiPromptIds.SEMANTIC_QUERY_PARSER_V2, "ai-prompts/semantic/query_semantic_parser.v2.md");

        m.put(AiPromptIds.COMPOSER_COST_DIAGNOSIS_V1, "ai-prompts/composer/cost_diagnosis.v1.md");
        m.put(AiPromptIds.COMPOSER_BUSINESS_OVERVIEW_V1, "ai-prompts/composer/business_overview.v1.md");
        m.put(AiPromptIds.COMPOSER_REVENUE_OVERVIEW_V1, "ai-prompts/composer/revenue_overview.v1.md");
        m.put(AiPromptIds.COMPOSER_PURCHASE_OVERVIEW_V1, "ai-prompts/composer/purchase_overview.v1.md");
        m.put(AiPromptIds.COMPOSER_STOCK_REDUCE_V1, "ai-prompts/composer/stock_reduce.v1.md");
        m.put(AiPromptIds.COMPOSER_DISH_PROFIT_V1, "ai-prompts/composer/dish_profit.v1.md");
        m.put(AiPromptIds.COMPOSER_DIAGNOSIS_V1, "ai-prompts/composer/diagnosis.v1.md");
        m.put(AiPromptIds.COMPOSER_DIAGNOSIS_STORE_PRIORITY_V1, "ai-prompts/composer/diagnosis_store_priority.v1.md");
        m.put(AiPromptIds.COMPOSER_WAREHOUSE_V1, "ai-prompts/composer/warehouse.v1.md");
        m.put(AiPromptIds.COMPOSER_GENERIC_CHAT_V1, "ai-prompts/composer/generic_chat.v1.md");

        m.put("common.answer_style_rules.v1", "ai-prompts/common/answer_style_rules.v1.md");
        m.put("common.numeric_safety_rules.v1", "ai-prompts/common/numeric_safety_rules.v1.md");
        m.put("common.no_calculation_rules.v1", "ai-prompts/common/no_calculation_rules.v1.md");

        this.idToClasspathResource = Collections.unmodifiableMap(m);
    }

    public String resolveClasspathRelativePath(String promptId) {
        if (promptId == null || promptId.isBlank()) {
            return null;
        }
        return idToClasspathResource.get(promptId.trim());
    }

    /** 是否在登记范围内（文件仍可能缺失，由加载阶段校验）。 */
    public boolean isRegistered(String promptId) {
        return resolveClasspathRelativePath(promptId) != null;
    }
}
