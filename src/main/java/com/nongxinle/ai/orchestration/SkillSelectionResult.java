package com.nongxinle.ai.orchestration;

import java.util.Locale;

/**
 * 第一步「选技能」解析后的统一结果（供编排层记录来源与置信度）。
 */
public record SkillSelectionResult(
        String skillsCsv,
        String costFacet,
        boolean broadQuestion,
        Double confidence,
        boolean llmStructuredOk,
        ChatRouteSource routeSource
) {
    public SkillSelectionResult withRouteSource(ChatRouteSource source) {
        return new SkillSelectionResult(skillsCsv, costFacet, broadQuestion, confidence, llmStructuredOk, source);
    }

    /**
     * costFacet 仅在选了 ai-skill-cost.md 时有意义；模型误填时清零以免日志与下游语义误导。
     */
    public SkillSelectionResult withNormalizedCostFacet() {
        String csv = skillsCsv != null ? skillsCsv.toLowerCase(Locale.ROOT) : "";
        if (!csv.contains("ai-skill-cost.md") && costFacet != null) {
            return new SkillSelectionResult(skillsCsv, null, broadQuestion, confidence, llmStructuredOk, routeSource);
        }
        return this;
    }
}
