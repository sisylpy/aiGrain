package com.nongxinle.ai.orchestration;

import com.nongxinle.ai.time.AiUserQueryTimeWindowLlmParser;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 第一步「选技能」解析后的统一结果（供编排层记录来源与置信度）。
 */
public final class SkillSelectionResult {
    private final String skillsCsv;
    private final String costFacet;
    private final boolean broadQuestion;
    private final Double confidence;
    private final boolean llmStructuredOk;
    private final ChatRouteSource routeSource;
    private final List<String> suggestedMetricIds;
    /** 首轮 skill-selection JSON 中的 statTime；可与专用时间 DeepSeek 口径一致，缺省为 null。 */
    private final AiUserQueryTimeWindowLlmParser.LlmTimeOutcome skillPhaseStatTime;

    public SkillSelectionResult(String skillsCsv, String costFacet, boolean broadQuestion, Double confidence,
                                boolean llmStructuredOk, ChatRouteSource routeSource, List<String> suggestedMetricIds,
                                AiUserQueryTimeWindowLlmParser.LlmTimeOutcome skillPhaseStatTime) {
        this.skillsCsv = skillsCsv;
        this.costFacet = costFacet;
        this.broadQuestion = broadQuestion;
        this.confidence = confidence;
        this.llmStructuredOk = llmStructuredOk;
        this.routeSource = routeSource;
        this.suggestedMetricIds = suggestedMetricIds == null ? List.of() : List.copyOf(suggestedMetricIds);
        this.skillPhaseStatTime = skillPhaseStatTime;
    }

    public String skillsCsv() {
        return skillsCsv;
    }

    public String costFacet() {
        return costFacet;
    }

    public boolean broadQuestion() {
        return broadQuestion;
    }

    public Double confidence() {
        return confidence;
    }

    public boolean llmStructuredOk() {
        return llmStructuredOk;
    }

    public ChatRouteSource routeSource() {
        return routeSource;
    }

    public List<String> suggestedMetricIds() {
        return suggestedMetricIds;
    }

    public AiUserQueryTimeWindowLlmParser.LlmTimeOutcome skillPhaseStatTime() {
        return skillPhaseStatTime;
    }

    public SkillSelectionResult withRouteSource(ChatRouteSource source) {
        return new SkillSelectionResult(skillsCsv, costFacet, broadQuestion, confidence, llmStructuredOk, source,
                suggestedMetricIds, skillPhaseStatTime);
    }

    public SkillSelectionResult withSkillPhaseStatTime(AiUserQueryTimeWindowLlmParser.LlmTimeOutcome statTime) {
        return new SkillSelectionResult(skillsCsv, costFacet, broadQuestion, confidence, llmStructuredOk, routeSource,
                suggestedMetricIds, statTime);
    }

    /**
     * costFacet 仅在选了 ai-skill-cost.md 时有意义；模型误填时清零以免日志与下游语义误导。
     */
    public SkillSelectionResult withNormalizedCostFacet() {
        String csv = skillsCsv != null ? skillsCsv.toLowerCase(Locale.ROOT) : "";
        if (!csv.contains("ai-skill-cost.md") && costFacet != null) {
            return new SkillSelectionResult(skillsCsv, null, broadQuestion, confidence, llmStructuredOk, routeSource,
                    suggestedMetricIds, skillPhaseStatTime);
        }
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SkillSelectionResult that = (SkillSelectionResult) o;
        return broadQuestion == that.broadQuestion
                && llmStructuredOk == that.llmStructuredOk
                && Objects.equals(skillsCsv, that.skillsCsv)
                && Objects.equals(costFacet, that.costFacet)
                && Objects.equals(confidence, that.confidence)
                && routeSource == that.routeSource
                && Objects.equals(suggestedMetricIds, that.suggestedMetricIds)
                && Objects.equals(skillPhaseStatTime, that.skillPhaseStatTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(skillsCsv, costFacet, broadQuestion, confidence, llmStructuredOk, routeSource,
                suggestedMetricIds, skillPhaseStatTime);
    }

    @Override
    public String toString() {
        return "SkillSelectionResult[skillsCsv=" + skillsCsv
                + ", costFacet=" + costFacet
                + ", broadQuestion=" + broadQuestion
                + ", confidence=" + confidence
                + ", llmStructuredOk=" + llmStructuredOk
                + ", routeSource=" + routeSource
                + ", suggestedMetricIds=" + suggestedMetricIds
                + ", skillPhaseStatTime=" + skillPhaseStatTime
                + "]";
    }
}
