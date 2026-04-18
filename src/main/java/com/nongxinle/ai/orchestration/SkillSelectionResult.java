package com.nongxinle.ai.orchestration;

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
}
