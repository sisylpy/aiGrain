package com.nongxinle.ai.planner;

import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeAnswerPlan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 单步执行出参：由 {@link PlannerStepExecutor} 返回，由 {@link PlannerExecutor} 转为 {@link PlannerStepResult}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerStepExecutionResponse {

    private PlannerStepStatus status;
    private String errorMessage;
    private String degradedReason;
    @Builder.Default
    private List<String> usedAgents = new ArrayList<>();
    @Builder.Default
    private List<String> usedTools = new ArrayList<>();

    /** C-37：经营诊断 Composite 确定性 AnswerPlan（仅诊断 compose 步等注入）。 */
    private BusinessDiagnosisCompositeAnswerPlan businessDiagnosisCompositeAnswerPlan;
}
