package com.nongxinle.ai.planner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 单次 PlannerExecutor 运行的可追溯快照（Replay §9）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerExecutorTrace {

    /** 执行时的计划快照 */
    private PlannerExecutionPlan plan;
    @Builder.Default
    private List<PlannerStepResult> stepResults = new ArrayList<>();
    /** status == DEGRADED 的步骤 id */
    @Builder.Default
    private List<String> degradedSteps = new ArrayList<>();
    @Builder.Default
    private List<String> usedAgents = new ArrayList<>();
    @Builder.Default
    private List<String> usedTools = new ArrayList<>();
    /** 下游 Composer / AnswerPlan 类型占位，C-2 可为 null */
    private String finalAnswerPlanType;
    private PlannerFailureStrategy appliedFailureStrategy;
    private PlannerStepStatus overallStatus;
    /** ASK_CLARIFICATION 占位，C-2 恒为 false */
    @Builder.Default
    private boolean clarificationRequested = false;
}
