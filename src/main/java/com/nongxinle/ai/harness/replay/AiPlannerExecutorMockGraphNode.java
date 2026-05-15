package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.planner.PlannerExecutor;
import com.nongxinle.ai.planner.PlannerExecutorResult;

/**
 * Mock 图入口节点：仅调用 {@link PlannerExecutor}，不接入生产 GraphRunner / MasterBusinessAgent。
 */
public final class AiPlannerExecutorMockGraphNode {

    private AiPlannerExecutorMockGraphNode() {
    }

    public static PlannerExecutorResult run(PlannerExecutor executor) {
        return run(executor, AiHarnessBuiltinCases.PLANNER_EXECUTOR_MOCK_CORE);
    }

    public static PlannerExecutorResult run(PlannerExecutor executor, String harnessCaseId) {
        return executor.execute(AiPlannerExecutorMockGraphCase.planForHarnessCase(harnessCaseId));
    }
}
