package com.nongxinle.ai.planner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * C-53：Composite <b>旁路观测 Gate</b> 判定结果；只读上下文产出，不修改 Run、不调 Tool/LLM。
 *
 * <p>BusinessDiagnosisComposite 当前仅服务 {@link BusinessDiagnosisCompositeExecutionMode#SHADOW} /
 * {@link BusinessDiagnosisCompositeExecutionMode#HARNESS_ONLY} 旁路观测链；<strong>不属于</strong> Master Graph 主回答链；
 * <strong>不替换</strong> {@link com.nongxinle.ai.core.AiRunState#getFinalAnswerText()}；
 * <strong>不负责</strong>生产用户正文。{@link BusinessDiagnosisCompositeExecutionMode#PRIMARY} 为预留/未接生产主链。</p>
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
