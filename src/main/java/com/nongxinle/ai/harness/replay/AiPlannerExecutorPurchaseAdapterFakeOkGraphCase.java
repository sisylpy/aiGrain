package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.planner.FakePurchasePlannerReadBridge;
import com.nongxinle.ai.planner.PlannerExecutionPlan;
import com.nongxinle.ai.planner.PlannerExecutorResult;
import com.nongxinle.ai.planner.PlannerFailureStrategy;
import com.nongxinle.ai.planner.PlannerStep;
import com.nongxinle.ai.planner.PlannerStepMockExecutionStatus;
import com.nongxinle.ai.planner.PurchasePlannerAgentAdapter;
import com.nongxinle.ai.planner.PurchasePlannerReadRequest;
import com.nongxinle.ai.planner.PurchasePlannerVisibleStore;
import com.nongxinle.ai.planner.RecommendationPlannerMockAgentAdapter;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_PURCHASE_ADAPTER_FAKE_OK_CORE}：计划级 {@link PurchasePlannerReadRequest} +
 * {@link FakePurchasePlannerReadBridge}（Harness-only；非真实 {@code PurchaseOverviewToolExecutor} / DB）。
 */
public final class AiPlannerExecutorPurchaseAdapterFakeOkGraphCase {

    public static final String CASE_ID = AiHarnessBuiltinCases.PLANNER_EXECUTOR_PURCHASE_ADAPTER_FAKE_OK_CORE;

    public static final String EXAMPLE_USER_MESSAGE =
            "Harness purchase fake-OK adapter case — not routed from user text; sample input only";

    public static final String PLAN_ID = "plan-purchase-adapter-fake-ok-v1";
    public static final String PLAN_TYPE = "PLANNER_EXECUTOR_PURCHASE_ADAPTER_FAKE_OK_CORE_V1";
    public static final String FINAL_ANSWER_PLAN_TYPE = "MOCK_RECOMMENDATION_AFTER_PURCHASE_ADAPTER_FAKE_OK_V1";

    public static final String HARNESS_RESOLVED_CONTEXT_REF = "HARNESS_PURCHASE_ADAPTER_FAKE_OK_RESOLVED_CTX_REF";
    public static final String HARNESS_ANSWER_PLAN_REF_AFTER_PURCHASE = "HARNESS_ANSWER_PLAN_REF_AFTER_PURCHASE_FAKE_OK";

    private AiPlannerExecutorPurchaseAdapterFakeOkGraphCase() {
    }

    public static PurchasePlannerReadRequest buildFullHarnessPurchaseReadRequest() {
        return PurchasePlannerReadRequest.builder()
                .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                .timeStart(LocalDate.of(2026, 5, 1))
                .timeEnd(LocalDate.of(2026, 5, 14))
                .timeLabel("2026-05-01..2026-05-14 (Harness fake purchase)")
                .scopeType("STORE")
                .visibleStores(
                        List.of(
                                PurchasePlannerVisibleStore.builder()
                                        .departmentId(7101L)
                                        .displayLabel("Harness Purchase Visible Store A")
                                        .build()))
                .queryDepartmentIds(List.of(7101L))
                .targetStoreDepartmentId(7101L)
                .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_ALL)
                .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY)
                .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_PURCHASE)
                .build();
    }

    public static PlannerExecutionPlan buildPlan() {
        PurchasePlannerReadRequest slice = buildFullHarnessPurchaseReadRequest();
        List<PlannerStep> steps =
                List.of(
                        PlannerStep.builder()
                                .stepId("step_purchase_adapter")
                                .stepName("purchase_overview_month")
                                .order(1)
                                .targetAgent(PurchasePlannerAgentAdapter.TARGET_AGENT)
                                .targetTool(PurchasePlannerAgentAdapter.TARGET_TOOL)
                                .inputSummary("采购只读（Fake ReadBridge 闭环）")
                                .expectedOutput("结构化 OK（合成数据）")
                                .acceptanceCriteria("purchaseReadRequest 自计划注入")
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
        root.put("plannerPurchaseAdapterHonesty", FakePurchasePlannerReadBridge.HARNESS_HONESTY_FAKE_READ_BRIDGE_OK);
        root.put(
                "plannerPurchaseAdapterNote",
                "harness_fake_read_bridge_only; synthetic purchaseAmount/count; not real SQL or PurchaseOverviewToolExecutor");
        return root;
    }
}
