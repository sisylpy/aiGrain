package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
import com.nongxinle.ai.planner.FakeStockReducePlannerReadBridge;
import com.nongxinle.ai.planner.PlannerExecutionPlan;
import com.nongxinle.ai.planner.PlannerExecutorResult;
import com.nongxinle.ai.planner.PlannerFailureStrategy;
import com.nongxinle.ai.planner.PlannerStep;
import com.nongxinle.ai.planner.PlannerStepMockExecutionStatus;
import com.nongxinle.ai.planner.RecommendationPlannerMockAgentAdapter;
import com.nongxinle.ai.planner.StockReducePlannerAgentAdapter;
import com.nongxinle.ai.planner.StockReducePlannerReadRequest;
import com.nongxinle.ai.planner.StockReducePlannerVisibleStore;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_FAKE_OK_CORE}：计划级 {@link StockReducePlannerReadRequest} +
 * {@link FakeStockReducePlannerReadBridge}（Harness-only；非真实 {@code StockReduceQueryToolExecutor} / DB）。
 */
public final class AiPlannerExecutorStockReduceAdapterFakeOkGraphCase {

    public static final String CASE_ID = AiHarnessBuiltinCases.PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_FAKE_OK_CORE;

    public static final String EXAMPLE_USER_MESSAGE =
            "Harness stock-reduce fake-OK adapter case — not routed from user text; sample input only";

    public static final String PLAN_ID = "plan-stock-reduce-adapter-fake-ok-v1";
    public static final String PLAN_TYPE = "PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_FAKE_OK_CORE_V1";
    public static final String FINAL_ANSWER_PLAN_TYPE = "MOCK_RECOMMENDATION_AFTER_STOCK_REDUCE_ADAPTER_FAKE_OK_V1";

    public static final String HARNESS_RESOLVED_CONTEXT_REF = "HARNESS_STOCK_REDUCE_ADAPTER_FAKE_OK_RESOLVED_CTX_REF";
    public static final String HARNESS_ANSWER_PLAN_REF_AFTER_STOCK_REDUCE =
            "HARNESS_ANSWER_PLAN_REF_AFTER_STOCK_REDUCE_FAKE_OK";

    private AiPlannerExecutorStockReduceAdapterFakeOkGraphCase() {
    }

    public static StockReducePlannerReadRequest buildFullHarnessStockReduceReadRequest() {
        return StockReducePlannerReadRequest.builder()
                .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                .timeStart(LocalDate.of(2026, 5, 1))
                .timeEnd(LocalDate.of(2026, 5, 14))
                .timeLabel("2026-05-01..2026-05-14 (Harness fake stock reduce)")
                .scopeType("STORE")
                .visibleStores(
                        List.of(
                                StockReducePlannerVisibleStore.builder()
                                        .departmentId(7201L)
                                        .displayLabel("Harness StockReduce Visible Store A")
                                        .build()))
                .queryDepartmentIds(List.of(7201L))
                .targetStoreDepartmentId(7201L)
                .reduceType(StockReduceAnswerPlan.REDUCE_TYPE_ALL)
                .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY)
                .totalsBasis("CALENDAR_NATURAL_DAY")
                .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_STOCK_REDUCE)
                .build();
    }

    public static PlannerExecutionPlan buildPlan() {
        StockReducePlannerReadRequest slice = buildFullHarnessStockReduceReadRequest();
        List<PlannerStep> steps =
                List.of(
                        PlannerStep.builder()
                                .stepId("step_stock_reduce_adapter")
                                .stepName("stock_reduce_overview_month")
                                .order(1)
                                .targetAgent(StockReducePlannerAgentAdapter.TARGET_AGENT)
                                .targetTool(StockReducePlannerAgentAdapter.TARGET_TOOL)
                                .inputSummary("出库/核销只读（Fake ReadBridge 闭环）")
                                .expectedOutput("结构化 OK（合成数据）")
                                .acceptanceCriteria("stockReduceReadRequest 自计划注入")
                                .mockExecutionStatus(null)
                                .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_STOCK_REDUCE)
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
                .stockReduceReadRequest(slice)
                .finalAnswerPlanType(FINAL_ANSWER_PLAN_TYPE)
                .build();
    }

    public static Map<String, Object> toHarnessSummary(
            PlannerExecutorResult result, String replayMessage, long runId, long conversationId) {
        Map<String, Object> root =
                new LinkedHashMap<>(
                        AiPlannerExecutorMockGraphCase.toHarnessSummary(
                                result, replayMessage, runId, conversationId, CASE_ID));
        root.put("harnessReplayMode", AiHarnessReplayMode.PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER.name());
        root.put("plannerStockReduceAdapterHonesty", FakeStockReducePlannerReadBridge.HARNESS_HONESTY_FAKE_READ_BRIDGE_OK);
        root.put(
                "plannerStockReduceAdapterNote",
                "harness_fake_read_bridge_only; synthetic four-type totals; not real SQL or StockReduceQueryToolExecutor");
        return root;
    }
}
