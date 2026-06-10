package com.nongxinle.ai.semantic.intake;

import com.nongxinle.ai.semantic.intake.llm.LlmSemanticIntakeParsed;
import org.springframework.util.StringUtils;

import java.util.List;

/** Intake {@code contextRelation} 枚举与字段一致性校验；不读 {@code reason} / 用户原文。 */
public final class SemanticIntakeContextRelationSupport {

    private SemanticIntakeContextRelationSupport() {}

    public static void collectContextRelationProtocolErrors(
            LlmSemanticIntakeParsed parsed, List<String> errors) {
        if (parsed == null || errors == null) {
            return;
        }
        String relation = parsed.getContextRelation();
        if (!StringUtils.hasText(relation)) {
            return;
        }
        if (!SemanticIntakeContextRelation.isKnown(relation)) {
            errors.add(
                    "contextRelation: got \""
                            + relation
                            + "\", allowed: NEW_CAPABILITY, CONTEXT_CONTINUATION");
            return;
        }
        String normalized =
                SemanticIntakeContextRelation.normalize(relation);
        if (SemanticIntakeContextRelation.NEW_CAPABILITY.name().equals(normalized)
                && (parsed.isFollowUp() || parsed.isUsedPreviousContext())) {
            errors.add(
                    "contextRelation=NEW_CAPABILITY requires isFollowUp=false and "
                            + "usedPreviousContext=false");
        }
    }
}
