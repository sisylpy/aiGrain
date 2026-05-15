package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.agent.business.BusinessAgentNames;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.planner.CompositeBusinessDiagnosisRevenuePurchaseHybridPlannerStepExecutor;
import com.nongxinle.ai.planner.PlannerExecutionPlan;
import com.nongxinle.ai.planner.PlannerExecutorResult;
import com.nongxinle.ai.planner.PlannerFailureStrategy;
import com.nongxinle.ai.planner.PlannerRevenueExecutionContext;
import com.nongxinle.ai.planner.PlannerStep;
import com.nongxinle.ai.planner.PlannerStepMockExecutionStatus;
import com.nongxinle.ai.planner.PurchasePlannerAgentAdapter;
import com.nongxinle.ai.planner.PurchasePlannerExecutionContext;
import com.nongxinle.ai.planner.PurchasePlannerReadRequest;
import com.nongxinle.ai.planner.RevenuePlannerAgentAdapter;
import com.nongxinle.ai.planner.RevenuePlannerReadRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_CORE}（C-33）：
 * {@link CompositeBusinessDiagnosisRevenuePurchaseHybridPlannerStepExecutor} — 营收 + 采购 Hydrated RealBridge；
 * 出库 / 菜品 / 诊断 / 建议仍为 mock。
 *
 * @see AiPlannerExecutorBusinessDiagnosisCompositeRevenueGraphCase
 */
public final class AiPlannerExecutorBusinessDiagnosisCompositeRevenuePurchaseGraphCase {

    public static final String CASE_ID =
            AiHarnessBuiltinCases.PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_CORE;

    private static final long HARNESS_SYNTHETIC_RUN_ID = 9_000_000L;

    public static final String PLANNER_COMPOSITE_HONESTY_REVENUE_PURCHASE_REAL_ONLY =
            "COMPOSITE_REVENUE_PURCHASE_REAL_ONLY";

    public static final String PLANNER_COMPOSITE_NOTE_REVENUE_PURCHASE_REAL =
            "revenue and purchase real hydrated adapters invoked; stock/dish/diagnosis/recommendation remain mock";

    public static final String PLAN_ID = "plan-business-diagnosis-composite-revenue-purchase-v1";
    public static final String PLAN_TYPE = "PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_CORE_V1";

    private AiPlannerExecutorBusinessDiagnosisCompositeRevenuePurchaseGraphCase() {
    }

    /**
     * 营收 / 采购 Hydrated 上下文分别与同域 Hydrated GraphCase 同构；后四步 mock。
     */
    public static PlannerExecutionPlan buildPlan() {
        RevenuePlannerReadRequest revenueSlice =
                AiPlannerExecutorRevenueAdapterRealBridgeHydratedGraphCase.buildFullHarnessRevenueReadRequest();
        AiResolvedQueryContext revenueRq =
                AiPlannerExecutorRevenueAdapterRealBridgeHydratedGraphCase.buildHydratedResolvedQueryContext();
        AiRunState revenueRun =
                AiPlannerExecutorRevenueAdapterRealBridgeHydratedGraphCase.buildHydratedRunState(revenueRq);
        PlannerRevenueExecutionContext revenueExec =
                PlannerRevenueExecutionContext.builder()
                        .runState(revenueRun)
                        .resolvedQueryContext(revenueRq)
                        .resolvedQueryContextRef(
                                AiPlannerExecutorRevenueAdapterRealBridgeHydratedGraphCase.HARNESS_RESOLVED_CONTEXT_REF)
                        .userId(1L)
                        .departmentId(
                                AiPlannerExecutorRevenueAdapterRealBridgeHydratedGraphCase.HARNESS_STORE_DEPARTMENT_ID)
                        .distributerId(null)
                        .conversationId("0")
                        .runId(Long.toString(HARNESS_SYNTHETIC_RUN_ID))
                        .plannerReadRequest(revenueSlice)
                        .build();

        PurchasePlannerReadRequest purchaseSlice =
                AiPlannerExecutorPurchaseAdapterRealBridgeHydratedGraphCase.buildFullHarnessPurchaseReadRequest();
        AiResolvedQueryContext purchaseRq =
                AiPlannerExecutorPurchaseAdapterRealBridgeHydratedGraphCase.buildHydratedResolvedQueryContext();
        AiRunState purchaseRun =
                AiPlannerExecutorPurchaseAdapterRealBridgeHydratedGraphCase.buildHydratedRunState(purchaseRq);
        PurchasePlannerExecutionContext purchaseExec =
                PurchasePlannerExecutionContext.builder()
                        .runState(purchaseRun)
                        .resolvedQueryContext(purchaseRq)
                        .resolvedQueryContextRef(
                                AiPlannerExecutorPurchaseAdapterRealBridgeHydratedGraphCase.HARNESS_RESOLVED_CONTEXT_REF)
                        .userId(1L)
                        .departmentId(
                                AiPlannerExecutorPurchaseAdapterRealBridgeHydratedGraphCase.HARNESS_STORE_DEPARTMENT_ID)
                        .distributerId(
                                AiPlannerExecutorPurchaseAdapterRealBridgeHydratedGraphCase.HARNESS_PURCHASE_DISTRIBUTER_ID)
                        .conversationId("0")
                        .runId(Long.toString(HARNESS_SYNTHETIC_RUN_ID))
                        .plannerReadRequest(purchaseSlice)
                        .build();

        List<PlannerStep> steps = new ArrayList<>();
        steps.add(
                PlannerStep.builder()
                        .stepId(CompositeBusinessDiagnosisRevenuePurchaseHybridPlannerStepExecutor
                                .COMPOSITE_STEP_ID_REVENUE_HYDRATED)
                        .stepName("revenue_hydrated_real")
                        .order(1)
                        .targetAgent(BusinessAgentNames.REVENUE_OVERVIEW)
                        .targetTool(RevenuePlannerAgentAdapter.TARGET_TOOL)
                        .inputSummary(
                                "STORE AAA 营收：Hydrated RevenuePlannerRealReadBridge → revenue_query（C-33）")
                        .expectedOutput("DailyRevenueAnswerPlan / toolResults[revenue_query] 真实链路")
                        .acceptanceCriteria("真实 revenue_query；失败仅诚实 DEGRADED，不假 SUCCESS")
                        .mockExecutionStatus(null)
                        .answerPlanRef(
                                AiPlannerExecutorRevenueAdapterRealBridgeHydratedGraphCase
                                        .HARNESS_ANSWER_PLAN_REF_AFTER_REVENUE)
                        .build());
        steps.add(
                PlannerStep.builder()
                        .stepId(CompositeBusinessDiagnosisRevenuePurchaseHybridPlannerStepExecutor
                                .COMPOSITE_STEP_ID_PURCHASE_HYDRATED)
                        .stepName("purchase_hydrated_real")
                        .order(2)
                        .targetAgent(PurchasePlannerAgentAdapter.TARGET_AGENT)
                        .targetTool(PurchasePlannerAgentAdapter.TARGET_TOOL)
                        .inputSummary(
                                "STORE AAA 采购：Hydrated PurchasePlannerRealReadBridge → purchase_overview（C-33）")
                        .expectedOutput("PurchaseAnswerPlan / purchase_overview 真实链路")
                        .acceptanceCriteria("真实 purchase_overview；失败仅诚实 DEGRADED，不假 SUCCESS")
                        .mockExecutionStatus(null)
                        .answerPlanRef(
                                AiPlannerExecutorPurchaseAdapterRealBridgeHydratedGraphCase
                                        .HARNESS_ANSWER_PLAN_REF_AFTER_PURCHASE)
                        .build());
        steps.add(
                step(
                        "step_stock_reduce_hydrated",
                        "stock_reduce_hydrated_skeleton",
                        3,
                        BusinessAgentNames.STOCK_REDUCE_QUERY,
                        AiPlannerExecutorBusinessDiagnosisCompositeGraphCase.MOCK_TOOL_STOCK_REDUCE_HYDRATED_ADAPTER,
                        "STORE AAA 出库/核销（mock，C-33）",
                        "stock_reduce summary 占位",
                        "本步未执行 stock_reduce_query（C-33）"));
        steps.add(
                step(
                        "step_dish_profit_hydrated",
                        "dish_profit_hydrated_skeleton",
                        4,
                        BusinessAgentNames.DISH_PROFIT_ANALYSIS,
                        AiPlannerExecutorBusinessDiagnosisCompositeGraphCase.MOCK_TOOL_DISH_PROFIT_HYDRATED_ADAPTER,
                        "STORE AAA 菜品毛利（mock，C-33）",
                        "dish_profit summary 占位",
                        "本步未执行 dish_profit_analysis（C-33）"));
        steps.add(
                PlannerStep.builder()
                        .stepId("step_diagnosis_compose")
                        .stepName("diagnosis_compose_skeleton")
                        .order(5)
                        .targetAgent(AiPlannerExecutorMockGraphCase.MOCK_AGENT_DIAGNOSIS)
                        .targetTool(AiPlannerExecutorBusinessDiagnosisCompositeGraphCase.MOCK_TOOL_DIAGNOSIS_COMPOSE)
                        .inputSummary(
                                "聚合多域 summary → DiagnosisPlan（mock，C-33；Harness mock：无 LLM / 无生产诊断；"
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
                        "建议生成（mock，C-33；Harness mock：无真实 Action；targetAgent 仅为占位名）",
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
                .purchaseReadRequest(purchaseSlice)
                .purchaseExecutionContext(purchaseExec)
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
        root.put("plannerCompositeHonesty", PLANNER_COMPOSITE_HONESTY_REVENUE_PURCHASE_REAL_ONLY);
        root.put("plannerCompositeNote", PLANNER_COMPOSITE_NOTE_REVENUE_PURCHASE_REAL);
        return root;
    }
}
