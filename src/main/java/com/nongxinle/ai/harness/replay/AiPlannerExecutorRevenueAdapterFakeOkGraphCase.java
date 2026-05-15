package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.planner.FakeRevenuePlannerReadBridge;
import com.nongxinle.ai.planner.PlannerExecutionPlan;
import com.nongxinle.ai.planner.PlannerExecutorResult;
import com.nongxinle.ai.planner.PlannerFailureStrategy;
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
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_REVENUE_ADAPTER_FAKE_OK_CORE}：计划级注入完整 {@link RevenuePlannerReadRequest} +
 * {@link FakeRevenuePlannerReadBridge}，验证 ADAPTER 成功闭环（<strong>非</strong>真实库 / Tool）。
 */
public final class AiPlannerExecutorRevenueAdapterFakeOkGraphCase {

    public static final String CASE_ID = AiHarnessBuiltinCases.PLANNER_EXECUTOR_REVENUE_ADAPTER_FAKE_OK_CORE;

    public static final String EXAMPLE_USER_MESSAGE =
            "Harness revenue fake-OK adapter case（非正文路由；示例输入）";

    public static final String PLAN_ID = "plan-revenue-adapter-fake-ok-v1";
    public static final String PLAN_TYPE = "PLANNER_EXECUTOR_REVENUE_ADAPTER_FAKE_OK_CORE_V1";
    public static final String FINAL_ANSWER_PLAN_TYPE = "MOCK_RECOMMENDATION_AFTER_REVENUE_ADAPTER_FAKE_OK_V1";

    /** 与 {@link #buildFullHarnessRevenueReadRequest()} 内 ref 对齐。 */
    public static final String HARNESS_RESOLVED_CONTEXT_REF = "HARNESS_REVENUE_ADAPTER_FAKE_OK_RESOLVED_CTX_REF";

    public static final String HARNESS_ANSWER_PLAN_REF_AFTER_REVENUE = "HARNESS_ANSWER_PLAN_REF_AFTER_REVENUE_FAKE_OK";

    private AiPlannerExecutorRevenueAdapterFakeOkGraphCase() {
    }

    public static RevenuePlannerReadRequest buildFullHarnessRevenueReadRequest() {
        return RevenuePlannerReadRequest.builder()
                .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                .timeStart(LocalDate.of(2026, 5, 1))
                .timeEnd(LocalDate.of(2026, 5, 14))
                .timeLabel("2026-05-01..2026-05-14 (Harness fake)")
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
        List<PlannerStep> steps =
                List.of(
                        PlannerStep.builder()
                                .stepId("step_revenue_adapter")
                                .stepName("revenue_overview_month")
                                .order(1)
                                .targetAgent(RevenuePlannerAgentAdapter.TARGET_AGENT)
                                .targetTool(RevenuePlannerAgentAdapter.TARGET_TOOL)
                                .inputSummary("营收只读（Fake ReadBridge 闭环）")
                                .expectedOutput("结构化 OK（合成数据）")
                                .acceptanceCriteria("revenueReadRequest 自计划注入")
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
        root.put("plannerRevenueAdapterHonesty", FakeRevenuePlannerReadBridge.HARNESS_HONESTY_FAKE_READ_BRIDGE_OK);
        root.put(
                "plannerRevenueAdapterNote",
                "harness_fake_read_bridge_only; synthetic revenueAmount/storeRows; not real SQL or production Tool");
        return root;
    }
}
