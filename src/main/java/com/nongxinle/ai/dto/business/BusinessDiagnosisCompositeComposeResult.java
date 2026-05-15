package com.nongxinle.ai.dto.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C-51：{@link com.nongxinle.ai.planner.BusinessDiagnosisCompositeReadonlyComposer} 输出；仅用于结构化终稿与 Harness 观测，
 * 不接 Master 主链路。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessDiagnosisCompositeComposeResult {

    /**
     * 用户可见终稿正文（优先等同 {@link BusinessDiagnosisCompositeAnswerPlan#getSummaryText()}；无则保守拼装）。
     */
    private String finalAnswerText;

    @Builder.Default
    private List<String> suggestedNextQuestions = new ArrayList<>();

    private BusinessDiagnosisCompositeRiskLevel riskLevel;

    private String scopeLabel;
    private String timeLabel;

    /** 同 {@link BusinessDiagnosisCompositeAnswerPlan#getType()}。 */
    private String answerPlanType;

    @Builder.Default
    private Map<String, Object> debug = new LinkedHashMap<>();
}
