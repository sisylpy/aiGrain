package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.planner.DishProfitPlannerAgentAdapter;
import com.nongxinle.ai.planner.DishProfitPlannerExecutionContext;
import com.nongxinle.ai.planner.DishProfitPlannerReadRequest;
import com.nongxinle.ai.planner.DishProfitPlannerVisibleStore;
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
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_CORE}：注入
 * {@link com.nongxinle.ai.planner.DishProfitPlannerRealReadBridge}（Harness {@code new}）。默认计划故意<strong>不</strong>物化
 * {@code AiRunState} / {@code AiResolvedQueryContext}，应诚实得到
 * {@link com.nongxinle.ai.planner.DishProfitPlannerRealReadBridge#ERROR_NO_RUN_STATE} 等降级（**不**调用
 * {@code DishProfitQueryToolExecutor}）。
 */
public final class AiPlannerExecutorDishProfitAdapterRealBridgeGraphCase {

    public static final String CASE_ID = AiHarnessBuiltinCases.PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_CORE;

    public static final String HARNESS_HONESTY_INCOMPLETE_CONTEXT = "REAL_BRIDGE_HARNESS_INCOMPLETE_CONTEXT";

    public static final String EXAMPLE_USER_MESSAGE =
            "Harness dish-profit real-bridge skeleton case（需 Hydrate 执行上下文；默认降级诚实）";

    public static final String PLAN_ID = "plan-dish-profit-adapter-real-bridge-v1";
    public static final String PLAN_TYPE = "PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_CORE_V1";
    public static final String FINAL_ANSWER_PLAN_TYPE = "MOCK_RECOMMENDATION_AFTER_DISH_PROFIT_ADAPTER_REAL_BRIDGE_V1";

    public static final String HARNESS_RESOLVED_CONTEXT_REF = "HARNESS_DISH_PROFIT_ADAPTER_REAL_BRIDGE_RESOLVED_CTX_REF";
    public static final String HARNESS_ANSWER_PLAN_REF_AFTER_DISH_PROFIT =
            "HARNESS_ANSWER_PLAN_REF_AFTER_DISH_PROFIT_REAL_BRIDGE";

    private AiPlannerExecutorDishProfitAdapterRealBridgeGraphCase() {
    }

    public static DishProfitPlannerReadRequest buildFullHarnessDishProfitReadRequest() {
        return DishProfitPlannerReadRequest.builder()
                .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                .timeStart(LocalDate.of(2026, 5, 1))
                .timeEnd(LocalDate.of(2026, 5, 14))
                .timeLabel("2026-05-01..2026-05-14 (Harness dish-profit real bridge skeleton)")
                .scopeType("STORE")
                .visibleStores(
                        List.of(
                                DishProfitPlannerVisibleStore.builder()
                                        .departmentId(7301L)
                                        .displayLabel("Harness DishProfit RealBridge Visible Store A")
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
        DishProfitPlannerExecutionContext dishExec =
                DishProfitPlannerExecutionContext.builder()
                        .runState(null)
                        .runStateRef("HARNESS_DISH_PROFIT_REAL_BRIDGE_RUN_STATE_NOT_MATERIALIZED")
                        .resolvedQueryContext(null)
                        .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                        .userId(1L)
                        .departmentId(7301L)
                        .distributerId(null)
                        .conversationId("0")
                        .runId("9000003")
                        .plannerReadRequest(slice)
                        .build();
        List<PlannerStep> steps =
                List.of(
                        PlannerStep.builder()
                                .stepId("step_dish_profit_adapter_real")
                                .stepName("dish_profit_overview_month")
                                .order(1)
                                .targetAgent(DishProfitPlannerAgentAdapter.TARGET_AGENT)
                                .targetTool(DishProfitPlannerAgentAdapter.TARGET_TOOL)
                                .inputSummary(
                                        "菜品毛利只读（DishProfitPlannerRealReadBridge skeleton — no Tool call yet）")
                                .expectedOutput(
                                        "OK when execution context hydrated + bridge wired; else honest DEGRADED")
                                .acceptanceCriteria(
                                        "PlannerExecutionPlan.dishProfitExecutionContext + dishProfitReadRequest "
                                                + "(no back-ref)")
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
                .dishProfitExecutionContext(dishExec)
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
        root.put("plannerDishProfitAdapterHonesty", HARNESS_HONESTY_INCOMPLETE_CONTEXT);
        root.put(
                "plannerDishProfitAdapterNote",
                "DishProfitPlannerRealReadBridge C-27 skeleton; no DishProfitQueryToolExecutor; default plan lacks "
                        + "hydrated AiRunState/AiResolvedQueryContext — honest DEGRADED");
        return root;
    }
}
