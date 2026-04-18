package com.nongxinle.ai.orchestration;

import java.util.Collections;
import java.util.Map;

/**
 * 主模型在回复末尾输出的 skill_handoff JSON，经校验后的载荷。
 *
 * @param toSkill 逻辑技能 id：cost | revenue | data_extractor | dish_sales
 */
public record SkillHandoffPayload(
        String toSkill,
        String reason,
        Map<String, Object> carryOver
) {
    public SkillHandoffPayload {
        toSkill = toSkill != null ? toSkill.trim().toLowerCase() : "";
        reason = reason != null ? reason.trim() : "";
        carryOver = carryOver != null ? carryOver : Collections.emptyMap();
    }

    public String toSkillFilesCsv() {
        return switch (toSkill) {
            case "cost" -> "ai-skill-cost.md";
            case "revenue" -> "ai-skill-revenue-boost.md";
            case "data_extractor" -> "ai-skill-data-extractor.md";
            case "dish_sales" -> "ai-skill-revenue-boost.md";
            default -> "ai-skill-revenue-boost.md";
        };
    }
}
