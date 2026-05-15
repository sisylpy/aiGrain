package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.agent.business.BusinessAgentNames;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.planner.CompositeBusinessDiagnosisRevenueHybridPlannerStepExecutor;
import com.nongxinle.ai.planner.PlannerExecutionPlan;
import com.nongxinle.ai.planner.PlannerExecutorResult;
import com.nongxinle.ai.planner.PlannerFailureStrategy;
import com.nongxinle.ai.planner.PlannerRevenueExecutionContext;
import com.nongxinle.ai.planner.PlannerStep;
import com.nongxinle.ai.planner.PlannerStepMockExecutionStatus;
import com.nongxinle.ai.planner.RevenuePlannerAgentAdapter;
import com.nongxinle.ai.planner.RevenuePlannerReadRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_CORE}（C-32）：
 * {@link CompositeBusinessDiagnosisRevenueHybridPlannerStepExecutor} — 仅
 * {@code step_revenue_hydrated} 真实走 {@link com.nongxinle.ai.planner.RevenuePlannerRealReadBridge} /
 * {@code revenue_query}；采购 / 出库 / 菜品 / 诊断 / 建议仍为 mock。
 *
 * @see AiPlannerExecutorBusinessDiagnosisCompositeGraphCase
 */
public final class AiPlannerExecutorBusinessDiagnosisCompositeRevenueGraphCase {

    public static final String CASE_ID = AiHarnessBuiltinCases.PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_CORE;

    /** 与 {@link AiPlannerExecutorRevenueAdapterRealBridgeHydratedGraphCase} / Replay 首轮 runId 对齐。 */
    private static final long HARNESS_SYNTHETIC_RUN_ID = 9_000_000L;

    public static final String PLANNER_COMPOSITE_HONESTY_REVENUE_REAL_ONLY = "COMPOSITE_REVENUE_REAL_ONLY";

    public static final String PLANNER_COMPOSITE_NOTE_REVENUE_REAL =
            "revenue real hydrated adapter invoked; purchase/stock/dish/diagnosis/recommendation remain mock";

    public static final String PLAN_ID = "plan-business-diagnosis-composite-revenue-v1";
    public static final String PLAN_TYPE = "PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_CORE_V1";

    private AiPlannerExecutorBusinessDiagnosisCompositeRevenueGraphCase() {
    }

    /**
     * Hydrated 营收上下文与 {@link AiPlannerExecutorRevenueAdapterRealBridgeHydratedGraphCase} 同构；其余五步与
     * C-31.1 skeleton 一致（mock_*）。
     */
    public static PlannerExecutionPlan buildPlan() {
        RevenuePlannerReadRequest revenueSlice =
                AiPlannerExecutorRevenueAdapterRealBridgeHydratedGraphCase.buildFullHarnessRevenueReadRequest();
        AiResolvedQueryContext rq =
                AiPlannerExecutorRevenueAdapterRealBridgeHydratedGraphCase.buildHydratedResolvedQueryContext();
        AiRunState runState =
                AiPlannerExecutorRevenueAdapterRealBridgeHydratedGraphCase.buildHydratedRunState(rq);
        PlannerRevenueExecutionContext revenueExec =
                PlannerRevenueExecutionContext.builder()
                        .runState(runState)
                        .resolvedQueryContext(rq)
                        .resolvedQueryContextRef(
                                AiPlannerExecutorRevenueAdapterRealBridgeHydratedGraphCase.HARNESS_RESOLVED_CONTEXT_REF)
                        .userId(1L)
                        .departmentId(AiPlannerExecutorRevenueAdapterRealBridgeHydratedGraphCase.HARNESS_STORE_DEPARTMENT_ID)
                        .distributerId(null)
                        .conversationId("0")
                        .runId(Long.toString(HARNESS_SYNTHETIC_RUN_ID))
                        .plannerReadRequest(revenueSlice)
                        .build();

        List<PlannerStep> steps = new ArrayList<>();
        steps.add(
                PlannerStep.builder()
                        .stepId(CompositeBusinessDiagnosisRevenueHybridPlannerStepExecutor.COMPOSITE_STEP_ID_REVENUE_HYDRATED)
                        .stepName("revenue_hydrated_real")
                        .order(1)
                        .targetAgent(BusinessAgentNames.REVENUE_OVERVIEW)
                        .targetTool(RevenuePlannerAgentAdapter.TARGET_TOOL)
                        .inputSummary(
                                "STORE AAA 营收：Hydrated RevenuePlannerRealReadBridge → revenue_query（C-32）；后续步仍为 mock")
                        .expectedOutput("DailyRevenueAnswerPlan / toolResults[revenue_query] 由真实链路写入（成功或诚实降级）")
                        .acceptanceCriteria("真实 revenue_query；失败仅 DEGRADED/FAILED→吸收为 DEGRADED，不假 SUCCESS")
                        .mockExecutionStatus(null)
                        .answerPlanRef(
                                AiPlannerExecutorRevenueAdapterRealBridgeHydratedGraphCase
                                        .HARNESS_ANSWER_PLAN_REF_AFTER_REVENUE)
                        .build());
        steps.add(
                step(
                        "step_purchase_hydrated",
                        "purchase_hydrated_skeleton",
                        2,
                        BusinessAgentNames.PURCHASE_OVERVIEW,
                        AiPlannerExecutorBusinessDiagnosisCompositeGraphCase.MOCK_TOOL_PURCHASE_HYDRATED_ADAPTER,
                        "STORE AAA 采购（mock）；未来接真实 purchase_overview",
                        "purchase summary 占位",
                        "本步未执行 purchase_overview（C-32）"));
        steps.add(
                step(
                        "step_stock_reduce_hydrated",
                        "stock_reduce_hydrated_skeleton",
                        3,
                        BusinessAgentNames.STOCK_REDUCE_QUERY,
                        AiPlannerExecutorBusinessDiagnosisCompositeGraphCase.MOCK_TOOL_STOCK_REDUCE_HYDRATED_ADAPTER,
                        "STORE AAA 出库/核销（mock）；未来接真实 stock_reduce_query",
                        "stock_reduce summary 占位",
                        "本步未执行 stock_reduce_query（C-32）"));
        steps.add(
                step(
                        "step_dish_profit_hydrated",
                        "dish_profit_hydrated_skeleton",
                        4,
                        BusinessAgentNames.DISH_PROFIT_ANALYSIS,
                        AiPlannerExecutorBusinessDiagnosisCompositeGraphCase.MOCK_TOOL_DISH_PROFIT_HYDRATED_ADAPTER,
                        "STORE AAA 菜品毛利（mock）；未来接真实 dish_profit_analysis",
                        "dish_profit summary 占位",
                        "本步未执行 dish_profit_analysis（C-32）"));
        steps.add(
                PlannerStep.builder()
                        .stepId("step_diagnosis_compose")
                        .stepName("diagnosis_compose_skeleton")
                        .order(5)
                        .targetAgent(AiPlannerExecutorMockGraphCase.MOCK_AGENT_DIAGNOSIS)
                        .targetTool(AiPlannerExecutorBusinessDiagnosisCompositeGraphCase.MOCK_TOOL_DIAGNOSIS_COMPOSE)
                        .inputSummary(
                                "聚合四域 summary → DiagnosisPlan（mock，C-32；Harness mock：无 LLM / 无生产诊断；"
                                        + "targetAgent 仅为占位名）")
                        .expectedOutput("summary / answerPlanRef 占位")
                        .acceptanceCriteria("mock only；usedTools=mock_diagnosis_compose；非生产诊断")
                        .mockExecutionStatus(PlannerStepMockExecutionStatus.SUCCESS)
                        .answerPlanRef(AiPlannerExecutorBusinessDiagnosisCompositeGraphCase.DIAGNOSIS_ANSWER_PLAN_REF)
                        .build());
        steps.add(
                step(
                        "step_recommendation",
                        "recommendation_three_skeleton",
                        6,
                        AiPlannerExecutorMockGraphCase.MOCK_AGENT_RECOMMENDATION,
                        AiPlannerExecutorMockGraphCase.MOCK_TOOL_RECOMMENDATION,
                        "建议生成（mock，C-32；Harness mock：无真实 Action；targetAgent 仅为占位名）",
                        "RecommendationPlan 占位",
                        "mock only；usedTools=mock_build_recommendation_plan"));

        return PlannerExecutionPlan.builder()
                .planId(PLAN_ID)
                .planType(PLAN_TYPE)
                .steps(steps)
                .failureStrategy(PlannerFailureStrategy.CONTINUE_WITH_DEGRADED)
                .resolvedContextRef(
                        AiPlannerExecutorRevenueAdapterRealBridgeHydratedGraphCase.HARNESS_RESOLVED_CONTEXT_REF)
                .revenueReadRequest(revenueSlice)
                .revenueExecutionContext(revenueExec)
                .finalAnswerPlanType(AiPlannerExecutorBusinessDiagnosisCompositeGraphCase.FINAL_ANSWER_PLAN_TYPE)
                .build();
    }

    private static PlannerStep step(
            String stepId,
            String stepName,
            int order,
            String targetAgent,
            String targetTool,
            String inputSummary,
            String expectedOutput,
            String acceptanceCriteria) {
        return PlannerStep.builder()
                .stepId(stepId)
                .stepName(stepName)
                .order(order)
                .targetAgent(targetAgent)
                .targetTool(targetTool)
                .inputSummary(inputSummary)
                .expectedOutput(expectedOutput)
                .acceptanceCriteria(acceptanceCriteria)
                .mockExecutionStatus(PlannerStepMockExecutionStatus.SUCCESS)
                .build();
    }

    public static Map<String, Object> toHarnessSummary(
            PlannerExecutorResult result, String replayMessage, long runId, long conversationId) {
        LinkedHashMap<String, Object> root =
                new LinkedHashMap<>(
                        AiPlannerExecutorMockGraphCase.toHarnessSummary(
                                result, replayMessage, runId, conversationId, CASE_ID));
        root.put("harnessReplayMode", AiHarnessReplayMode.PLANNER_EXECUTOR_REVENUE_ADAPTER.name());
        root.put("plannerCompositeHonesty", PLANNER_COMPOSITE_HONESTY_REVENUE_REAL_ONLY);
        root.put("plannerCompositeNote", PLANNER_COMPOSITE_NOTE_REVENUE_REAL);
        return root;
    }
}
