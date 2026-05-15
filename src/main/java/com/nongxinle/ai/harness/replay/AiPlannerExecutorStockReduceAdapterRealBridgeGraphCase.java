package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
import com.nongxinle.ai.planner.PlannerExecutionPlan;
import com.nongxinle.ai.planner.PlannerExecutorResult;
import com.nongxinle.ai.planner.PlannerFailureStrategy;
import com.nongxinle.ai.planner.PlannerStep;
import com.nongxinle.ai.planner.PlannerStepMockExecutionStatus;
import com.nongxinle.ai.planner.RecommendationPlannerMockAgentAdapter;
import com.nongxinle.ai.planner.StockReducePlannerAgentAdapter;
import com.nongxinle.ai.planner.StockReducePlannerExecutionContext;
import com.nongxinle.ai.planner.StockReducePlannerReadRequest;
import com.nongxinle.ai.planner.StockReducePlannerVisibleStore;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_CORE}：注入
 * {@link com.nongxinle.ai.planner.StockReducePlannerRealReadBridge}。默认计划故意<strong>不</strong>物化
 * {@code AiRunState} / {@code AiResolvedQueryContext}，Harness 应诚实得到
 * {@link com.nongxinle.ai.planner.StockReducePlannerRealReadBridge#ERROR_NO_RUN_STATE} 等降级（**不**调用
 * {@code StockReduceQueryToolExecutor}）。
 */
public final class AiPlannerExecutorStockReduceAdapterRealBridgeGraphCase {

    public static final String CASE_ID = AiHarnessBuiltinCases.PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_CORE;

    public static final String HARNESS_HONESTY_INCOMPLETE_CONTEXT = "REAL_BRIDGE_HARNESS_INCOMPLETE_CONTEXT";

    public static final String EXAMPLE_USER_MESSAGE =
            "Harness stock-reduce real-bridge skeleton case（需 Hydrate 执行上下文；默认降级诚实）";

    public static final String PLAN_ID = "plan-stock-reduce-adapter-real-bridge-v1";
    public static final String PLAN_TYPE = "PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_CORE_V1";
    public static final String FINAL_ANSWER_PLAN_TYPE = "MOCK_RECOMMENDATION_AFTER_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_V1";

    public static final String HARNESS_RESOLVED_CONTEXT_REF = "HARNESS_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_RESOLVED_CTX_REF";
    public static final String HARNESS_ANSWER_PLAN_REF_AFTER_STOCK_REDUCE =
            "HARNESS_ANSWER_PLAN_REF_AFTER_STOCK_REDUCE_REAL_BRIDGE";

    private AiPlannerExecutorStockReduceAdapterRealBridgeGraphCase() {
    }

    public static StockReducePlannerReadRequest buildFullHarnessStockReduceReadRequest() {
        return StockReducePlannerReadRequest.builder()
                .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                .timeStart(LocalDate.of(2026, 5, 1))
                .timeEnd(LocalDate.of(2026, 5, 14))
                .timeLabel("2026-05-01..2026-05-14 (Harness stock-reduce real bridge skeleton)")
                .scopeType("STORE")
                .visibleStores(
                        List.of(
                                StockReducePlannerVisibleStore.builder()
                                        .departmentId(7201L)
                                        .displayLabel("Harness StockReduce RealBridge Visible Store A")
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
        StockReducePlannerExecutionContext stockExec =
                StockReducePlannerExecutionContext.builder()
                        .runState(null)
                        .runStateRef("HARNESS_STOCK_REDUCE_REAL_BRIDGE_RUN_STATE_NOT_MATERIALIZED")
                        .resolvedQueryContext(null)
                        .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                        .userId(1L)
                        .departmentId(7201L)
                        .distributerId(null)
                        .conversationId("0")
                        .runId("9000002")
                        .plannerReadRequest(slice)
                        .build();
        List<PlannerStep> steps =
                List.of(
                        PlannerStep.builder()
                                .stepId("step_stock_reduce_adapter_real")
                                .stepName("stock_reduce_overview_month")
                                .order(1)
                                .targetAgent(StockReducePlannerAgentAdapter.TARGET_AGENT)
                                .targetTool(StockReducePlannerAgentAdapter.TARGET_TOOL)
                                .inputSummary(
                                        "出库/核销只读（StockReducePlannerRealReadBridge skeleton — no Tool call yet）")
                                .expectedOutput(
                                        "OK when execution context hydrated + bridge wired; else honest DEGRADED")
                                .acceptanceCriteria(
                                        "PlannerExecutionPlan.stockReduceExecutionContext + stockReduceReadRequest "
                                                + "(no back-ref)")
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
                .stockReduceExecutionContext(stockExec)
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
        root.put("harnessReplayMode", AiHarnessReplayMode.PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER.name());
        root.put("plannerStockReduceAdapterHonesty", HARNESS_HONESTY_INCOMPLETE_CONTEXT);
        root.put(
                "plannerStockReduceAdapterNote",
                "StockReducePlannerRealReadBridge C-22 skeleton; no StockReduceQueryToolExecutor; default plan lacks "
                        + "hydrated AiRunState/AiResolvedQueryContext — honest DEGRADED");
        return root;
    }
}
