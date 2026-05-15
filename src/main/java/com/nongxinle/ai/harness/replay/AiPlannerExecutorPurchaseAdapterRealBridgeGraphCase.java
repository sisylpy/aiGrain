package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.planner.PlannerExecutionPlan;
import com.nongxinle.ai.planner.PlannerExecutorResult;
import com.nongxinle.ai.planner.PlannerFailureStrategy;
import com.nongxinle.ai.planner.PlannerStep;
import com.nongxinle.ai.planner.PlannerStepMockExecutionStatus;
import com.nongxinle.ai.planner.PurchasePlannerAgentAdapter;
import com.nongxinle.ai.planner.PurchasePlannerExecutionContext;
import com.nongxinle.ai.planner.PurchasePlannerReadRequest;
import com.nongxinle.ai.planner.PurchasePlannerRealReadBridge;
import com.nongxinle.ai.planner.PurchasePlannerVisibleStore;
import com.nongxinle.ai.planner.RecommendationPlannerMockAgentAdapter;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_CORE}：注入
 * {@link PurchasePlannerRealReadBridge}。默认计划故意<strong>不</strong>物化 {@code AiRunState} /
 * {@code AiResolvedQueryContext}，Harness 应诚实得到 {@link PurchasePlannerRealReadBridge#ERROR_NO_RUN_STATE} 等降级
 * （**不**触达 {@code PurchaseOverviewToolExecutor}）。若计划物化完整上下文（见 Hydrated case），同一 Bridge 会走真实 Tool。
 */
public final class AiPlannerExecutorPurchaseAdapterRealBridgeGraphCase {

    public static final String CASE_ID = AiHarnessBuiltinCases.PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_CORE;

    public static final String HARNESS_HONESTY_INCOMPLETE_CONTEXT = "REAL_BRIDGE_HARNESS_INCOMPLETE_CONTEXT";

    public static final String EXAMPLE_USER_MESSAGE =
            "Harness purchase real-bridge skeleton case（需 Hydrate 执行上下文；默认降级诚实）";

    public static final String PLAN_ID = "plan-purchase-adapter-real-bridge-v1";
    public static final String PLAN_TYPE = "PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_CORE_V1";
    public static final String FINAL_ANSWER_PLAN_TYPE = "MOCK_RECOMMENDATION_AFTER_PURCHASE_ADAPTER_REAL_BRIDGE_V1";

    public static final String HARNESS_RESOLVED_CONTEXT_REF = "HARNESS_PURCHASE_ADAPTER_REAL_BRIDGE_RESOLVED_CTX_REF";
    public static final String HARNESS_ANSWER_PLAN_REF_AFTER_PURCHASE = "HARNESS_ANSWER_PLAN_REF_AFTER_PURCHASE_REAL_BRIDGE";

    private AiPlannerExecutorPurchaseAdapterRealBridgeGraphCase() {
    }

    public static PurchasePlannerReadRequest buildFullHarnessPurchaseReadRequest() {
        return PurchasePlannerReadRequest.builder()
                .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                .timeStart(LocalDate.of(2026, 5, 1))
                .timeEnd(LocalDate.of(2026, 5, 14))
                .timeLabel("2026-05-01..2026-05-14 (Harness purchase real bridge skeleton)")
                .scopeType("STORE")
                .visibleStores(
                        List.of(
                                PurchasePlannerVisibleStore.builder()
                                        .departmentId(7101L)
                                        .displayLabel("Harness Purchase Visible Store A")
                                        .build(),
                                PurchasePlannerVisibleStore.builder()
                                        .departmentId(7102L)
                                        .displayLabel("Harness Purchase Visible Store B")
                                        .build()))
                .queryDepartmentIds(List.of(7101L, 7102L))
                .targetStoreDepartmentId(7101L)
                .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_PURCHASE)
                .build();
    }

    public static PlannerExecutionPlan buildPlan() {
        PurchasePlannerReadRequest slice = buildFullHarnessPurchaseReadRequest();
        PurchasePlannerExecutionContext purchaseExec =
                PurchasePlannerExecutionContext.builder()
                        .runState(null)
                        .runStateRef("HARNESS_PURCHASE_REAL_BRIDGE_RUN_STATE_NOT_MATERIALIZED")
                        .resolvedQueryContext(null)
                        .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                        .userId(1L)
                        .departmentId(7101L)
                        .distributerId(null)
                        .conversationId("0")
                        .runId("9000001")
                        .plannerReadRequest(slice)
                        .build();
        List<PlannerStep> steps =
                List.of(
                        PlannerStep.builder()
                                .stepId("step_purchase_adapter_real")
                                .stepName("purchase_overview_month")
                                .order(1)
                                .targetAgent(PurchasePlannerAgentAdapter.TARGET_AGENT)
                                .targetTool(PurchasePlannerAgentAdapter.TARGET_TOOL)
                                .inputSummary("采购只读（PurchasePlannerRealReadBridge skeleton — no Tool call yet）")
                                .expectedOutput("OK when execution context hydrated + bridge wired; else honest DEGRADED")
                                .acceptanceCriteria(
                                        "PlannerExecutionPlan.purchaseExecutionContext + purchaseReadRequest (no back-ref)")
                                .mockExecutionStatus(null)
                                .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_PURCHASE)
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
                .purchaseReadRequest(slice)
                .purchaseExecutionContext(purchaseExec)
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
        root.put("harnessReplayMode", AiHarnessReplayMode.PLANNER_EXECUTOR_PURCHASE_ADAPTER.name());
        root.put("plannerPurchaseAdapterHonesty", HARNESS_HONESTY_INCOMPLETE_CONTEXT);
        root.put(
                "plannerPurchaseAdapterNote",
                "PurchasePlannerRealReadBridge skeleton; no PurchaseOverviewToolExecutor in C-17; default plan lacks "
                        + "hydrated AiRunState/AiResolvedQueryContext — honest DEGRADED");
        return root;
    }
}
