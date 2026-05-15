package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.planner.DishProfitPlannerAgentAdapter;
import com.nongxinle.ai.planner.DishProfitPlannerReadRequest;
import com.nongxinle.ai.planner.DishProfitPlannerVisibleStore;
import com.nongxinle.ai.planner.FakeDishProfitPlannerReadBridge;
import com.nongxinle.ai.planner.PlannerExecutionPlan;
import com.nongxinle.ai.planner.PlannerExecutorResult;
import com.nongxinle.ai.planner.PlannerFailureStrategy;
import com.nongxinle.ai.planner.PlannerStep;
import com.nongxinle.ai.planner.PlannerStepMockExecutionStatus;
import com.nongxinle.ai.planner.RecommendationPlannerMockAgentAdapter;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_FAKE_OK_CORE}：计划级 {@link DishProfitPlannerReadRequest} +
 * {@link FakeDishProfitPlannerReadBridge}（Harness-only；非真实 {@code DishProfitQueryToolExecutor} / DB）。
 */
public final class AiPlannerExecutorDishProfitAdapterFakeOkGraphCase {

    public static final String CASE_ID = AiHarnessBuiltinCases.PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_FAKE_OK_CORE;

    public static final String EXAMPLE_USER_MESSAGE =
            "Harness dish-profit fake-OK adapter case — not routed from user text; sample input only";

    public static final String PLAN_ID = "plan-dish-profit-adapter-fake-ok-v1";
    public static final String PLAN_TYPE = "PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_FAKE_OK_CORE_V1";
    public static final String FINAL_ANSWER_PLAN_TYPE = "MOCK_RECOMMENDATION_AFTER_DISH_PROFIT_ADAPTER_FAKE_OK_V1";

    public static final String HARNESS_RESOLVED_CONTEXT_REF = "HARNESS_DISH_PROFIT_ADAPTER_FAKE_OK_RESOLVED_CTX_REF";
    public static final String HARNESS_ANSWER_PLAN_REF_AFTER_DISH_PROFIT =
            "HARNESS_ANSWER_PLAN_REF_AFTER_DISH_PROFIT_FAKE_OK";

    private AiPlannerExecutorDishProfitAdapterFakeOkGraphCase() {
    }

    public static DishProfitPlannerReadRequest buildFullHarnessDishProfitReadRequest() {
        return DishProfitPlannerReadRequest.builder()
                .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                .timeStart(LocalDate.of(2026, 5, 1))
                .timeEnd(LocalDate.of(2026, 5, 14))
                .timeLabel("2026-05-01..2026-05-14 (Harness fake dish profit)")
                .scopeType("STORE")
                .visibleStores(
                        List.of(
                                DishProfitPlannerVisibleStore.builder()
                                        .departmentId(7301L)
                                        .displayLabel("Harness DishProfit Visible Store A")
                                        .build()))
                .queryDepartmentIds(List.of(7301L))
                .targetStoreDepartmentId(7301L)
                .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_OVERVIEW)
                .mentionedDishName(null)
                .dishProfitMetricType("OVERVIEW")
                .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_DISH_PROFIT)
                .build();
    }

    public static PlannerExecutionPlan buildPlan() {
        DishProfitPlannerReadRequest slice = buildFullHarnessDishProfitReadRequest();
        List<PlannerStep> steps =
                List.of(
                        PlannerStep.builder()
                                .stepId("step_dish_profit_adapter")
                                .stepName("dish_profit_overview_month")
                                .order(1)
                                .targetAgent(DishProfitPlannerAgentAdapter.TARGET_AGENT)
                                .targetTool(DishProfitPlannerAgentAdapter.TARGET_TOOL)
                                .inputSummary("菜品毛利只读（Fake ReadBridge 闭环）")
                                .expectedOutput("结构化 OK（合成数据）")
                                .acceptanceCriteria("dishProfitReadRequest 自计划注入")
                                .mockExecutionStatus(null)
                                .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_DISH_PROFIT)
                                .build(),
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
                .dishProfitReadRequest(slice)
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
        root.put(
                "plannerDishProfitAdapterHonesty", FakeDishProfitPlannerReadBridge.HARNESS_HONESTY_FAKE_READ_BRIDGE_OK);
        root.put(
                "plannerDishProfitAdapterNote",
                "harness_fake_read_bridge_only; listPriceRevenue aggregate semantics; not real SQL or DishProfitQueryToolExecutor");
        return root;
    }
}
