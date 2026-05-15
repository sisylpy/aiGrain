package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.planner.PlannerExecutionPlan;
import com.nongxinle.ai.planner.PlannerExecutorResult;
import com.nongxinle.ai.planner.PlannerFailureStrategy;
import com.nongxinle.ai.planner.PlannerStep;
import com.nongxinle.ai.planner.PlannerStepMockExecutionStatus;
import com.nongxinle.ai.planner.RecommendationPlannerMockAgentAdapter;
import com.nongxinle.ai.planner.RevenuePlannerAgentAdapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_REVENUE_ADAPTER_CORE}：两步（营收 ADAPTER + 建议 mock），
 * 不跑 Master / 生产 Graph；{@link RevenuePlannerAgentAdapter} 本轮不注入 {@link com.nongxinle.ai.planner.RevenuePlannerReadBridge}。
 */
public final class AiPlannerExecutorRevenueAdapterGraphCase {

    public static final String CASE_ID = AiHarnessBuiltinCases.PLANNER_EXECUTOR_REVENUE_ADAPTER_CORE;

    public static final String EXAMPLE_USER_MESSAGE =
            "Harness revenue adapter case（非正文路由；示例输入）";

    public static final String PLAN_ID = "plan-revenue-adapter-core-v1";
    public static final String PLAN_TYPE = "PLANNER_EXECUTOR_REVENUE_ADAPTER_CORE_V1";
    public static final String FINAL_ANSWER_PLAN_TYPE = "MOCK_RECOMMENDATION_AFTER_REVENUE_ADAPTER_V1";

    /** 句柄占位：Adapter 仍因 readBridge=null 不走真实查库。 */
    public static final String HARNESS_RESOLVED_CONTEXT_REF = "HARNESS_REVENUE_ADAPTER_RESOLVED_CTX_REF";

    public static final String HARNESS_ANSWER_PLAN_REF_AFTER_REVENUE = "HARNESS_ANSWER_PLAN_REF_STEP1_UNSATISFIED";

    private AiPlannerExecutorRevenueAdapterGraphCase() {
    }

    public static PlannerExecutionPlan buildPlan() {
        List<PlannerStep> steps = new ArrayList<>();
        steps.add(
                PlannerStep.builder()
                        .stepId("step_revenue_adapter")
                        .stepName("revenue_overview_month")
                        .order(1)
                        .targetAgent(RevenuePlannerAgentAdapter.TARGET_AGENT)
                        .targetTool(RevenuePlannerAgentAdapter.TARGET_TOOL)
                        .inputSummary("营收只读（Adapter 边界）")
                        .expectedOutput("DailyRevenueAnswerPlan 或显式降级")
                        .acceptanceCriteria("resolvedQueryContextRef 必填；readBridge TODO")
                        .mockExecutionStatus(null)
                        .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_REVENUE)
                        .build());
        steps.add(
                PlannerStep.builder()
                        .stepId("step_recommendation_mock")
                        .stepName("recommendation_three")
                        .order(2)
                        .targetAgent(RecommendationPlannerMockAgentAdapter.TARGET_AGENT)
                        .targetTool(RecommendationPlannerMockAgentAdapter.TARGET_TOOL)
                        .inputSummary("mock 建议步")
                        .expectedOutput("RecommendationPlan 占位")
                        .acceptanceCriteria("mock 成功")
                        .mockExecutionStatus(PlannerStepMockExecutionStatus.SUCCESS)
                        .build());

        return PlannerExecutionPlan.builder()
                .planId(PLAN_ID)
                .planType(PLAN_TYPE)
                .steps(steps)
                .failureStrategy(PlannerFailureStrategy.CONTINUE_WITH_DEGRADED)
                .resolvedContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                .finalAnswerPlanType(FINAL_ANSWER_PLAN_TYPE)
                .build();
    }

    /**
     * 在 {@link AiPlannerExecutorMockGraphCase#toHarnessSummary} 基础上增加 C-7 诚实标记（不声称真实查库成功）。
     */
    public static Map<String, Object> toHarnessSummary(
            PlannerExecutorResult result,
            String replayMessage,
            long runId,
            long conversationId) {
        Map<String, Object> root =
                new LinkedHashMap<>(
                        AiPlannerExecutorMockGraphCase.toHarnessSummary(
                                result, replayMessage, runId, conversationId, CASE_ID));
        root.put("harnessReplayMode", AiHarnessReplayMode.PLANNER_EXECUTOR_REVENUE_ADAPTER.name());
        root.put("plannerRevenueAdapterHonesty", RevenuePlannerAgentAdapter.HONESTY_MARKER);
        root.put(
                "plannerRevenueAdapterNote",
                "read_bridge_null_in_harness; no DB success claimed; see first step degradedReason");
        return root;
    }
}
