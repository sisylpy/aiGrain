package com.nongxinle.ai.workrecord;

import com.nongxinle.entity.GbWorkRecordCategoryEntity;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 校验 LLM 结构化输出；不做 NL/关键词分类 fallback。分类 code/name 以 ACTIVE 白名单为准。
 */
public final class WorkRecordLlmOutputGuard {

    private WorkRecordLlmOutputGuard() {
    }

    public record GuardedCategory(
            Long categoryId,
            String categoryCode,
            String categoryName,
            String categoryDecision,
            String suggestedCategoryName) {
    }

    public static GuardResult validate(
            WorkRecordLlmResult llm,
            Map<Long, GbWorkRecordCategoryEntity> activeById,
            GbWorkRecordCategoryEntity otherCategory) {

        if (llm == null) {
            return GuardResult.failure("llm_result_null");
        }
        if (!StringUtils.hasText(llm.getPolishedContent())) {
            return GuardResult.failure("polished_content_empty");
        }
        String decision = normalizeDecision(llm.getCategoryDecision());
        if (!StringUtils.hasText(decision)) {
            return GuardResult.failure("category_decision_invalid");
        }
        switch (decision) {
            case WorkRecordConstants.DECISION_EXISTING -> {
                if (llm.getSelectedCategoryId() == null) {
                    return GuardResult.failure("category_id_missing");
                }
                GbWorkRecordCategoryEntity cat = activeById.get(llm.getSelectedCategoryId());
                if (cat == null) {
                    return GuardResult.failure("category_not_allowed");
                }
                return GuardResult.success(
                        new GuardedCategory(
                                cat.getGbWrcId(),
                                cat.getGbWrcCode(),
                                cat.getGbWrcName(),
                                WorkRecordConstants.DECISION_EXISTING,
                                null));
            }
            case WorkRecordConstants.DECISION_SUGGEST_NEW -> {
                if (otherCategory == null) {
                    return GuardResult.failure("other_category_missing");
                }
                if (!StringUtils.hasText(llm.getSuggestedCategoryName())) {
                    return GuardResult.failure("suggested_category_name_missing");
                }
                String suggested = llm.getSuggestedCategoryName().trim();
                return GuardResult.success(
                        new GuardedCategory(
                                otherCategory.getGbWrcId(),
                                otherCategory.getGbWrcCode(),
                                otherCategory.getGbWrcName(),
                                WorkRecordConstants.DECISION_SUGGEST_NEW,
                                suggested));
            }
            case WorkRecordConstants.DECISION_OTHER -> {
                if (otherCategory == null) {
                    return GuardResult.failure("other_category_missing");
                }
                return GuardResult.success(
                        new GuardedCategory(
                                otherCategory.getGbWrcId(),
                                otherCategory.getGbWrcCode(),
                                otherCategory.getGbWrcName(),
                                WorkRecordConstants.DECISION_OTHER,
                                null));
            }
            default -> {
                return GuardResult.failure("category_decision_invalid");
            }
        }
    }

    private static String normalizeDecision(String raw) {
        return raw == null ? null : raw.trim().toUpperCase();
    }

    public record GuardResult(boolean ok, GuardedCategory category, String errorCode) {

        public static GuardResult success(GuardedCategory category) {
            return new GuardResult(true, category, null);
        }

        public static GuardResult failure(String errorCode) {
            return new GuardResult(false, null, errorCode);
        }
    }
}
