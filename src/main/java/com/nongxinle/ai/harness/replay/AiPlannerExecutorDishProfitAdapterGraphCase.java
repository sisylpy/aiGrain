package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.planner.DishProfitPlannerAgentAdapter;
import com.nongxinle.ai.planner.PlannerExecutionPlan;
import com.nongxinle.ai.planner.PlannerExecutorResult;
import com.nongxinle.ai.planner.PlannerFailureStrategy;
import com.nongxinle.ai.planner.PlannerStep;
import com.nongxinle.ai.planner.PlannerStepMockExecutionStatus;
import com.nongxinle.ai.planner.RecommendationPlannerMockAgentAdapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_CORE}：菜品毛利 ADAPTER + 建议 mock；
 * 不注入 {@link com.nongxinle.ai.planner.DishProfitPlannerReadBridge} → 首步诚实 {@code DEGRADED}。
 */
public final class AiPlannerExecutorDishProfitAdapterGraphCase {

    public static final String CASE_ID = AiHarnessBuiltinCases.PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_CORE;

    public static final String EXAMPLE_USER_MESSAGE =
            "Harness dish-profit adapter case — not routed from user text; sample input only";

    public static final String PLAN_ID = "plan-dish-profit-adapter-core-v1";
    public static final String PLAN_TYPE = "PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_CORE_V1";
    public static final String FINAL_ANSWER_PLAN_TYPE = "MOCK_RECOMMENDATION_AFTER_DISH_PROFIT_ADAPTER_V1";

    public static final String HARNESS_RESOLVED_CONTEXT_REF = "HARNESS_DISH_PROFIT_ADAPTER_RESOLVED_CTX_REF";
    public static final String HARNESS_ANSWER_PLAN_REF_AFTER_DISH_PROFIT =
            "HARNESS_ANSWER_PLAN_REF_DISH_PROFIT_STEP1_UNSATISFIED";

    private AiPlannerExecutorDishProfitAdapterGraphCase() {
    }

    public static PlannerExecutionPlan buildPlan() {
        List<PlannerStep> steps = new ArrayList<>();
        steps.add(
                PlannerStep.builder()
                        .stepId("step_dish_profit_adapter")
                        .stepName("dish_profit_overview_month")
                        .order(1)
                        .targetAgent(DishProfitPlannerAgentAdapter.TARGET_AGENT)
                        .targetTool(DishProfitPlannerAgentAdapter.TARGET_TOOL)
                        .inputSummary("菜品毛利只读（Adapter 边界）")
                        .expectedOutput("DishProfitAnswerPlan 或显式降级")
                        .acceptanceCriteria("resolvedQueryContextRef 必填；readBridge TODO")
                        .mockExecutionStatus(null)
                        .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_DISH_PROFIT)
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

    public static Map<String, Object> toHarnessSummary(
            PlannerExecutorResult result, String replayMessage, long runId, long conversationId) {
        Map<String, Object> root =
                new LinkedHashMap<>(
                        AiPlannerExecutorMockGraphCase.toHarnessSummary(
                                result, replayMessage, runId, conversationId, CASE_ID));
        root.put("harnessReplayMode", AiHarnessReplayMode.PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER.name());
        root.put("plannerDishProfitAdapterHonesty", DishProfitPlannerAgentAdapter.HONESTY_MARKER);
        root.put(
                "plannerDishProfitAdapterNote",
                "read_bridge_null_in_harness; no DB or DishProfitQueryToolExecutor; first step degradedReason shows ADAPTER_NO_REAL_CONTEXT");
        return root;
    }
}
