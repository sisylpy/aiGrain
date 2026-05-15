package com.nongxinle.ai.planner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * C-53：Composite 生产 Gate 判定结果；只读上下文产出，不修改 Run、不调 Tool/LLM。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessDiagnosisCompositeGateResult {

    /** STORE / GROUP 多店 Harness 口径建议；未通过 Gate 时为 {@link #NONE}。 */
    public enum RecommendedCaseKind {
        STORE,
        GROUP,
        NONE
    }

    private boolean allowed;

    private BusinessDiagnosisCompositeGateReasonCode reasonCode;

    /** 短说明（可调式）；禁止拼接用户原文。 */
    private String reason;

    /** 来自 {@link com.nongxinle.ai.context.AiResolvedOrgScope#getScopeType()} 的镜像。 */
    private String scopeType;

    /**
     * 仅 {@link #allowed}{@code true} 时非空，值为
     * {@link com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeAnswerPlan#TYPE_BUSINESS_DIAGNOSIS_COMPOSITE}。
     */
    private String finalAnswerPlanType;

    private RecommendedCaseKind recommendedCaseKind;

    @Builder.Default
    private Map<String, Object> debug = new LinkedHashMap<>();
}
