package com.nongxinle.ai.planner;

import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeAnswerPlan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 单步执行结果（C-2：mock SUCCESS / SKIPPED，不接真实调用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerStepResult {

    private String stepId;
    private PlannerStepStatus status;
    private String errorMessage;
    private String degradedReason;
    @Builder.Default
    private List<String> usedAgents = new ArrayList<>();
    @Builder.Default
    private List<String> usedTools = new ArrayList<>();

    private BusinessDiagnosisCompositeAnswerPlan businessDiagnosisCompositeAnswerPlan;
}
