package com.nongxinle.ai.semantic.intake;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticIntakeResult {

    private SemanticIntakeStatus status;
    private SemanticIntakeQuestionMode questionMode;
    private SemanticIntakeNormalizationType normalizationType;
    private String canonicalUserQuery;
    private Boolean isFollowUp;
    private Boolean usedPreviousContext;
    private String primaryDomain;
    private List<String> candidateDomains;
    private String routeType;
    private Double confidence;
    private Boolean needClarification;
    private String clarificationQuestion;
    private String reason;
    private List<SemanticIntakeSubQuestion> subQuestions;

    private String promptId;
    private String llmRawText;
    private String parseError;

    private Boolean intakeRepairAttempted;
    private Boolean intakeRepairSuccess;
    private String intakeRepairReason;

    public static SemanticIntakeResult invalid(String reason, String promptId, String raw, String parseError) {
        return SemanticIntakeResult.builder()
                .status(SemanticIntakeStatus.INVALID)
                .promptId(promptId)
                .llmRawText(raw)
                .parseError(parseError)
                .reason(reason)
                .needClarification(false)
                .build();
    }

    public Map<String, Object> toDebugMap() {
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("promptId", promptId);
        m.put("llmRawText", llmRawText);
        m.put("parseError", parseError);
        if (Boolean.TRUE.equals(intakeRepairAttempted)) {
            m.put("intakeRepairAttempted", true);
            m.put("intakeRepairSuccess", Boolean.TRUE.equals(intakeRepairSuccess));
            m.put("intakeRepairReason", intakeRepairReason);
        }
        return m.isEmpty() ? null : m;
    }
}
