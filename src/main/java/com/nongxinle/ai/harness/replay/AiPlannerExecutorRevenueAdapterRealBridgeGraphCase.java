package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.planner.PlannerExecutionPlan;
import com.nongxinle.ai.planner.PlannerExecutorResult;
import com.nongxinle.ai.planner.PlannerFailureStrategy;
import com.nongxinle.ai.planner.PlannerRevenueExecutionContext;
import com.nongxinle.ai.planner.PlannerStep;
import com.nongxinle.ai.planner.PlannerStepMockExecutionStatus;
import com.nongxinle.ai.planner.RecommendationPlannerMockAgentAdapter;
import com.nongxinle.ai.planner.RevenuePlannerAgentAdapter;
import com.nongxinle.ai.planner.RevenuePlannerReadRequest;
import com.nongxinle.ai.planner.RevenuePlannerVisibleStore;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_CORE}：注入
 * {@link com.nongxinle.ai.planner.RevenuePlannerRealReadBridge}（真实 Tool 链）。默认计划故意<strong>不</strong>物化
 * {@code AiRunState} / {@code AiResolvedQueryContext}，Harness 应诚实得到
 * {@code ADAPTER_NO_RUN_STATE} 等降级；本地 curl 可在装配完整上下文后验证 OK。
 */
public final class AiPlannerExecutorRevenueAdapterRealBridgeGraphCase {

    public static final String CASE_ID = AiHarnessBuiltinCases.PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_CORE;

    /**
     * 摘要字段：默认 Harness 快照未 Hydrate runState / resolvedQueryContext，
     * 真实桥会在边界校验处降级（非 Fake）。
     */
    public static final String HARNESS_HONESTY_INCOMPLETE_CONTEXT = "REAL_BRIDGE_HARNESS_INCOMPLETE_CONTEXT";

    public static final String EXAMPLE_USER_MESSAGE =
            "Harness revenue real-bridge case（需 Hydrate 执行上下文；默认降级诚实）";

    public static final String PLAN_ID = "plan-revenue-adapter-real-bridge-v1";
    public static final String PLAN_TYPE = "PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_CORE_V1";
    public static final String FINAL_ANSWER_PLAN_TYPE = "MOCK_RECOMMENDATION_AFTER_REVENUE_ADAPTER_REAL_BRIDGE_V1";

    public static final String HARNESS_RESOLVED_CONTEXT_REF = "HARNESS_REVENUE_ADAPTER_REAL_BRIDGE_RESOLVED_CTX_REF";
    public static final String HARNESS_ANSWER_PLAN_REF_AFTER_REVENUE = "HARNESS_ANSWER_PLAN_REF_AFTER_REVENUE_REAL_BRIDGE";

    private AiPlannerExecutorRevenueAdapterRealBridgeGraphCase() {
    }

    public static RevenuePlannerReadRequest buildFullHarnessRevenueReadRequest() {
        return RevenuePlannerReadRequest.builder()
                .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                .timeStart(LocalDate.of(2026, 5, 1))
                .timeEnd(LocalDate.of(2026, 5, 14))
                .timeLabel("2026-05-01..2026-05-14 (Harness real bridge)")
                .scopeType("STORE")
                .visibleStores(
                        List.of(
                                RevenuePlannerVisibleStore.builder()
                                        .departmentId(7001L)
                                        .displayLabel("Harness Visible Store A")
                                        .build(),
                                RevenuePlannerVisibleStore.builder()
                                        .departmentId(7002L)
                                        .displayLabel("Harness Visible Store B")
                                        .build()))
                .queryDepartmentIds(List.of(7001L, 7002L))
                .targetStoreDepartmentId(7001L)
                .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_REVENUE)
                .build();
    }

    public static PlannerExecutionPlan buildPlan() {
        RevenuePlannerReadRequest revenueSlice = buildFullHarnessRevenueReadRequest();
        PlannerRevenueExecutionContext revenueExec =
                PlannerRevenueExecutionContext.builder()
                        .runState(null)
                        .runStateRef("HARNESS_REAL_BRIDGE_RUN_STATE_NOT_MATERIALIZED")
                        .resolvedQueryContext(null)
                        .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                        .userId(1L)
                        .departmentId(7001L)
                        .distributerId(null)
                        .conversationId("0")
                        .runId("9000000")
                        .plannerReadRequest(revenueSlice)
                        .build();
        List<PlannerStep> steps =
                List.of(
                        PlannerStep.builder()
                                .stepId("step_revenue_adapter_real")
                                .stepName("revenue_overview_month")
                                .order(1)
                                .targetAgent(RevenuePlannerAgentAdapter.TARGET_AGENT)
                                .targetTool(RevenuePlannerAgentAdapter.TARGET_TOOL)
                                .inputSummary("营收只读（RevenuePlannerRealReadBridge + revenue_query）")
                                .expectedOutput("OK when execution context hydrated; else honest DEGRADED")
                                .acceptanceCriteria("PlannerExecutionPlan.revenueExecutionContext + revenueReadRequest (no back-ref)")
                                .mockExecutionStatus(null)
                                .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_REVENUE)
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
                .revenueReadRequest(revenueSlice)
                .revenueExecutionContext(revenueExec)
                .finalAnswerPlanType(FINAL_ANSWER_PLAN_TYPE)
                .build();
    }

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
        root.put("plannerRevenueAdapterHonesty", HARNESS_HONESTY_INCOMPLETE_CONTEXT);
        root.put(
                "plannerRevenueAdapterNote",
                "revenue_query chain wired via RevenuePlannerRealReadBridge; default plan lacks AiRunState/"
                        + "AiResolvedQueryContext — hydrate for local OK replay");
        return root;
    }
}
