package com.nongxinle.ai.planner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@link PlannerExecutor#execute(PlannerExecutionPlan)} 的返回封装。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerExecutorResult {

    private PlannerExecutorTrace trace;
    /** 与 {@link PlannerExecutorTrace#overallStatus} 一致：无 FAILED 即为 true */
    private boolean ok;
}
