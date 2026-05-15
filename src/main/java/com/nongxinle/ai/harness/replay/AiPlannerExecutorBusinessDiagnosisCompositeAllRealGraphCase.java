package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.agent.business.BusinessAgentNames;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeAnswerPlan;
import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeComposeResult;
import com.nongxinle.ai.planner.BusinessDiagnosisCompositeReadonlyComposer;
import com.nongxinle.ai.planner.CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor;
import com.nongxinle.ai.planner.CompositeBusinessDiagnosisRevenuePurchaseHybridPlannerStepExecutor;
import com.nongxinle.ai.planner.CompositeBusinessDiagnosisRevenuePurchaseStockHybridPlannerStepExecutor;
import com.nongxinle.ai.planner.DishProfitPlannerAgentAdapter;
import com.nongxinle.ai.planner.DishProfitPlannerExecutionContext;
import com.nongxinle.ai.planner.DishProfitPlannerReadRequest;
import com.nongxinle.ai.planner.PlannerExecutionPlan;
import com.nongxinle.ai.planner.PlannerExecutorResult;
import com.nongxinle.ai.planner.PlannerFailureStrategy;
import com.nongxinle.ai.planner.PlannerRevenueExecutionContext;
import com.nongxinle.ai.planner.PlannerStep;
import com.nongxinle.ai.planner.PlannerStepResult;
import com.nongxinle.ai.planner.PlannerStepMockExecutionStatus;
import com.nongxinle.ai.planner.PurchasePlannerAgentAdapter;
import com.nongxinle.ai.planner.PurchasePlannerExecutionContext;
import com.nongxinle.ai.planner.PurchasePlannerReadRequest;
import com.nongxinle.ai.planner.RevenuePlannerAgentAdapter;
import com.nongxinle.ai.planner.RevenuePlannerReadRequest;
import com.nongxinle.ai.planner.StockReducePlannerAgentAdapter;
import com.nongxinle.ai.planner.StockReducePlannerExecutionContext;
import com.nongxinle.ai.planner.StockReducePlannerReadRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE}（C-35）：四域 Hydrated
 * RealBridge（营收 / 采购 / 出库 / 菜品毛利）；诊断 / 建议仍为 mock（无 LLM / 无生产 Action）。
 *
 * @see AiPlannerExecutorBusinessDiagnosisCompositeRevenuePurchaseStockGraphCase
 */
public final class AiPlannerExecutorBusinessDiagnosisCompositeAllRealGraphCase {

    public static final String CASE_ID = AiHarnessBuiltinCases.PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE;

    private static final long HARNESS_SYNTHETIC_RUN_ID = 9_000_000L;

    public static final String PLANNER_COMPOSITE_HONESTY_ALL_DATA_REAL_DIAGNOSIS_MOCK =
            "COMPOSITE_ALL_DATA_REAL_DIAGNOSIS_MOCK";

    public static final String PLANNER_COMPOSITE_NOTE_ALL_DATA_REAL =
            "revenue, purchase, stock_reduce and dish_profit real hydrated adapters invoked; "
                    + "diagnosis/recommendation remain mock";

    public static final String PLAN_ID = "plan-business-diagnosis-composite-all-data-real-v1";
    public static final String PLAN_TYPE = "PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE_V1";

    private AiPlannerExecutorBusinessDiagnosisCompositeAllRealGraphCase() {
    }

    /** 四域 Hydrated 与各单域 Hydrated GraphCase 同构；诊断 / 建议两步 mock。 */
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

        StockReducePlannerReadRequest stockSlice =
                AiPlannerExecutorStockReduceAdapterRealBridgeHydratedGraphCase.buildFullHarnessStockReduceReadRequest();
        AiResolvedQueryContext stockRq =
                AiPlannerExecutorStockReduceAdapterRealBridgeHydratedGraphCase.buildHydratedResolvedQueryContext();
        AiRunState stockRun =
                AiPlannerExecutorStockReduceAdapterRealBridgeHydratedGraphCase.buildHydratedRunState(stockRq);
        StockReducePlannerExecutionContext stockExec =
                StockReducePlannerExecutionContext.builder()
                        .runState(stockRun)
                        .resolvedQueryContext(stockRq)
                        .resolvedQueryContextRef(
                                AiPlannerExecutorStockReduceAdapterRealBridgeHydratedGraphCase.HARNESS_RESOLVED_CONTEXT_REF)
                        .userId(1L)
                        .departmentId(
                                AiPlannerExecutorStockReduceAdapterRealBridgeHydratedGraphCase.HARNESS_STORE_DEPARTMENT_ID)
                        .distributerId(
                                AiPlannerExecutorStockReduceAdapterRealBridgeHydratedGraphCase
                                        .HARNESS_STOCK_REDUCE_DISTRIBUTER_ID)
                        .conversationId("0")
                        .runId(Long.toString(HARNESS_SYNTHETIC_RUN_ID))
                        .plannerReadRequest(stockSlice)
                        .build();

        DishProfitPlannerReadRequest dishSlice =
                AiPlannerExecutorDishProfitAdapterRealBridgeHydratedGraphCase.buildFullHarnessDishProfitReadRequest();
        AiResolvedQueryContext dishRq =
                AiPlannerExecutorDishProfitAdapterRealBridgeHydratedGraphCase.buildHydratedResolvedQueryContext();
        AiRunState dishRun =
                AiPlannerExecutorDishProfitAdapterRealBridgeHydratedGraphCase.buildHydratedRunState(dishRq);
        DishProfitPlannerExecutionContext dishExec =
                DishProfitPlannerExecutionContext.builder()
                        .runState(dishRun)
                        .resolvedQueryContext(dishRq)
                        .resolvedQueryContextRef(
                                AiPlannerExecutorDishProfitAdapterRealBridgeHydratedGraphCase.HARNESS_RESOLVED_CONTEXT_REF)
                        .userId(1L)
                        .departmentId(
                                AiPlannerExecutorDishProfitAdapterRealBridgeHydratedGraphCase.HARNESS_STORE_DEPARTMENT_ID)
                        .distributerId(
                                AiPlannerExecutorDishProfitAdapterRealBridgeHydratedGraphCase.HARNESS_DISH_PROFIT_DISTRIBUTER_ID)
                        .conversationId("0")
                        .runId(Long.toString(HARNESS_SYNTHETIC_RUN_ID))
                        .plannerReadRequest(dishSlice)
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
                        .inputSummary("STORE AAA 营收：Hydrated RevenuePlannerRealReadBridge → revenue_query（C-35）")
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
                        .inputSummary("STORE AAA 采购：Hydrated PurchasePlannerRealReadBridge → purchase_overview（C-35）")
                        .expectedOutput("PurchaseAnswerPlan / purchase_overview 真实链路")
                        .acceptanceCriteria("真实 purchase_overview；失败仅诚实 DEGRADED，不假 SUCCESS")
                        .mockExecutionStatus(null)
                        .answerPlanRef(
                                AiPlannerExecutorPurchaseAdapterRealBridgeHydratedGraphCase
                                        .HARNESS_ANSWER_PLAN_REF_AFTER_PURCHASE)
                        .build());
        steps.add(
                PlannerStep.builder()
                        .stepId(CompositeBusinessDiagnosisRevenuePurchaseStockHybridPlannerStepExecutor
                                .COMPOSITE_STEP_ID_STOCK_REDUCE_HYDRATED)
                        .stepName("stock_reduce_hydrated_real")
                        .order(3)
                        .targetAgent(StockReducePlannerAgentAdapter.TARGET_AGENT)
                        .targetTool(StockReducePlannerAgentAdapter.TARGET_TOOL)
                        .inputSummary(
                                "STORE AAA 出库/核销：Hydrated StockReducePlannerRealReadBridge → stock_reduce_query（C-35）")
                        .expectedOutput("StockReduceAnswerPlan / stock_reduce_query 真实链路")
                        .acceptanceCriteria("真实 stock_reduce_query；失败仅诚实 DEGRADED，不假 SUCCESS")
                        .mockExecutionStatus(null)
                        .answerPlanRef(
                                AiPlannerExecutorStockReduceAdapterRealBridgeHydratedGraphCase
                                        .HARNESS_ANSWER_PLAN_REF_AFTER_STOCK_REDUCE)
                        .build());
        steps.add(
                PlannerStep.builder()
                        .stepId(CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor
                                .COMPOSITE_STEP_ID_DISH_PROFIT_HYDRATED)
                        .stepName("dish_profit_hydrated_real")
                        .order(4)
                        .targetAgent(DishProfitPlannerAgentAdapter.TARGET_AGENT)
                        .targetTool(DishProfitPlannerAgentAdapter.TARGET_TOOL)
                        .inputSummary(
                                "STORE AAA 菜品毛利：Hydrated DishProfitPlannerRealReadBridge → dish_profit_analysis（C-35）")
                        .expectedOutput("DishProfitAnswerPlan / dish_profit_analysis 真实链路")
                        .acceptanceCriteria("真实 dish_profit_analysis；失败仅诚实 DEGRADED，不假 SUCCESS")
                        .mockExecutionStatus(null)
                        .answerPlanRef(
                                AiPlannerExecutorDishProfitAdapterRealBridgeHydratedGraphCase
                                        .HARNESS_ANSWER_PLAN_REF_AFTER_DISH_PROFIT)
                        .build());
        steps.add(
                PlannerStep.builder()
                        .stepId("step_diagnosis_compose")
                        .stepName("diagnosis_compose_skeleton")
                        .order(5)
                        .targetAgent(AiPlannerExecutorMockGraphCase.MOCK_AGENT_DIAGNOSIS)
                        .targetTool(
                                CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor
                                        .COMPOSITE_MOCK_TOOL_DIAGNOSIS_COMPOSE)
                        .inputSummary(
                                "C-37：四域 stepResults + 各域 AiRunState 已物化 summary → BusinessDiagnosisCompositeAnswerPlan（确定性骨架）；"
                                        + "无 LLM；usedTools 仍 mock_diagnosis_compose")
                        .expectedOutput("BusinessDiagnosisCompositeAnswerPlan JSON 可序列化；dataCoverage 四域")
                        .acceptanceCriteria(
                                "确定性 compose；usedTools=mock_diagnosis_compose；非生产诊断推理")
                        .mockExecutionStatus(PlannerStepMockExecutionStatus.SUCCESS)
                        .answerPlanRef(AiPlannerExecutorBusinessDiagnosisCompositeGraphCase.DIAGNOSIS_ANSWER_PLAN_REF)
                        .build());
        steps.add(
                mockTailStep(
                        "step_recommendation",
                        "recommendation_three_skeleton",
                        6,
                        AiPlannerExecutorMockGraphCase.MOCK_AGENT_RECOMMENDATION,
                        AiPlannerExecutorMockGraphCase.MOCK_TOOL_RECOMMENDATION,
                        "建议生成（Harness mock：无真实 Action/通知/调价/下单；targetAgent 仅为占位名，C-35）",
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
                .stockReduceReadRequest(stockSlice)
                .stockReduceExecutionContext(stockExec)
                .dishProfitReadRequest(dishSlice)
                .dishProfitExecutionContext(dishExec)
                .finalAnswerPlanType(AiPlannerExecutorBusinessDiagnosisCompositeGraphCase.FINAL_ANSWER_PLAN_TYPE)
                .build();
    }

    private static PlannerStep mockTailStep(
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
        root.put("harnessReplayMode", AiHarnessReplayMode.PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER.name());
        root.put("plannerCompositeHonesty", PLANNER_COMPOSITE_HONESTY_ALL_DATA_REAL_DIAGNOSIS_MOCK);
        root.put("plannerCompositeNote", PLANNER_COMPOSITE_NOTE_ALL_DATA_REAL);
        if (result != null && result.getTrace() != null && result.getTrace().getStepResults() != null) {
            for (PlannerStepResult sr : result.getTrace().getStepResults()) {
                if (sr == null || sr.getStepId() == null) {
                    continue;
                }
                if (!"step_diagnosis_compose".equals(sr.getStepId().trim())) {
                    continue;
                }
                BusinessDiagnosisCompositeAnswerPlan ap = sr.getBusinessDiagnosisCompositeAnswerPlan();
                if (ap == null) {
                    break;
                }
                root.put("businessDiagnosisAnswerPlanType", ap.getType());
                root.put(
                        "businessDiagnosisRiskLevel",
                        ap.getRiskLevel() != null ? ap.getRiskLevel().name() : null);
                root.put("businessDiagnosisDataCoverage", ap.getDataCoverage());
                root.put("businessDiagnosisCompositeAnswerPlan", JSON.parseObject(JSON.toJSONString(ap)));
                root.put("businessDiagnosisSummaryText", ap.getSummaryText());
                root.put("businessDiagnosisSuggestedNextQuestions", ap.getSuggestedNextQuestions());
                BusinessDiagnosisCompositeComposeResult composeResult =
                        BusinessDiagnosisCompositeReadonlyComposer.compose(ap);
                root.put("businessDiagnosisFinalAnswerText", composeResult.getFinalAnswerText());
                root.put("businessDiagnosisComposerVersion", BusinessDiagnosisCompositeReadonlyComposer.COMPOSER_VERSION);
                break;
            }
        }
        return root;
    }
}
