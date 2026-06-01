package com.nongxinle.ai.semantic.intake;

import com.nongxinle.ai.semantic.SemanticLlmFailureClassification;
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
    /**
     * 库房库存问法结构化语义（Intake v1 可选字段）：{@code SHORTAGE_OR_ALERT} /
     * {@code NEAR_EXPIRY} / {@code EXPLICIT_AMOUNT_RANKING_LOW}。Java 仅做枚举校验与边界执行。
     */
    private String warehouseInventorySemantics;
    private List<SemanticIntakeSubQuestion> subQuestions;

    private String promptId;
    private String llmRawText;
    private String parseError;

    private Boolean intakeRepairAttempted;
    private Boolean intakeRepairSuccess;
    private String intakeRepairReason;

    /** 基础设施失败码：{@link com.nongxinle.ai.semantic.SemanticLlmFailureClassification}。 */
    private String failureCode;
    /** 失败阶段：SEMANTIC_INTAKE。 */
    private String failureStage;

    public static SemanticIntakeResult invalid(String reason, String promptId, String raw, String parseError) {
        SemanticIntakeResult result =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.INVALID)
                        .promptId(promptId)
                        .llmRawText(raw)
                        .parseError(parseError)
                        .reason(reason)
                        .needClarification(false)
                        .build();
        SemanticLlmFailureClassification.enrichIntakeFailureMeta(result);
        return result;
    }

    public Map<String, Object> toDebugMap() {
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("promptId", promptId);
        m.put("llmRawText", llmRawText);
        m.put("parseError", parseError);
        m.put("failureCode", failureCode);
        m.put("failureStage", failureStage);
        if (Boolean.TRUE.equals(intakeRepairAttempted)) {
            m.put("intakeRepairAttempted", true);
            m.put("intakeRepairSuccess", Boolean.TRUE.equals(intakeRepairSuccess));
            m.put("intakeRepairReason", intakeRepairReason);
        }
        return m.isEmpty() ? null : m;
    }
}
