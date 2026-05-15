package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.agent.business.BusinessAgentNames;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeAnswerPlan;
import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeComposeResult;
import com.nongxinle.ai.graph.business.scope.BusinessScopeResolutionSupport;
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
import com.nongxinle.ai.planner.PlannerStepMockExecutionStatus;
import com.nongxinle.ai.planner.PlannerStepResult;
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
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE}（C-48）：与 C-35
 * {@link AiPlannerExecutorBusinessDiagnosisCompositeAllRealGraphCase} 同六步；四数据域复用 C-44～C-47 的 GROUP Hydrated
 * 物化方式；诊断确定性 compose；建议 mock。**不接** Master / LLM；**不**改四条 Tool。
 *
 * @see AiPlannerExecutorRevenueAdapterGroupHydratedGraphCase
 * @see AiPlannerExecutorPurchaseAdapterGroupHydratedGraphCase
 * @see AiPlannerExecutorStockReduceAdapterGroupHydratedGraphCase
 * @see AiPlannerExecutorDishProfitAdapterGroupHydratedGraphCase
 */
public final class AiPlannerExecutorBusinessDiagnosisCompositeGroupGraphCase {

    public static final String CASE_ID =
            AiHarnessBuiltinCases.PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE;

    /**
     * 写入营收 {@link com.nongxinle.ai.context.AiResolvedOrgScope#getScopeName()}，供 {@link
     * com.nongxinle.ai.planner.BusinessDiagnosisCompositeAnswerPlanBuilder} 生成集团口径 {@code summaryText}（避免仅
     * {@code unknown_scope} / 单店 dept 标签）。
     */
    public static final String GROUP_COMPOSITE_SCOPE_NAME = "当前可见门店 AAA、汀兰餐厅（集团口径）";

    public static final String PLANNER_COMPOSITE_HONESTY_GROUP_ALL_DATA_REAL_DIAGNOSIS_DETERMINISTIC =
            "COMPOSITE_GROUP_ALL_DATA_REAL_DIAGNOSIS_DETERMINISTIC";

    public static final String PLANNER_COMPOSITE_NOTE_GROUP =
            "group composite; four group hydrated adapters invoked; diagnosis deterministic; recommendation mock";

    public static final String PLAN_ID = "plan-business-diagnosis-composite-group-all-data-real-v1";
    public static final String PLAN_TYPE = "PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE_V1";

    private static final long HARNESS_SYNTHETIC_RUN_ID = 9_000_070L;

    private AiPlannerExecutorBusinessDiagnosisCompositeGroupGraphCase() {
    }

    /** 四域 GROUP Hydrated + 诊断 / 建议 mock；步 id / targetTool 与 C-35 对齐。 */
    public static PlannerExecutionPlan buildPlan() {
        RevenuePlannerReadRequest revenueSlice =
                AiPlannerExecutorRevenueAdapterGroupHydratedGraphCase.buildFullHarnessRevenueReadRequest();
        AiResolvedQueryContext revenueRq =
                AiPlannerExecutorRevenueAdapterGroupHydratedGraphCase.buildHydratedResolvedQueryContext();
        if (revenueRq.getOrgScope() != null) {
            revenueRq.getOrgScope().setScopeName(GROUP_COMPOSITE_SCOPE_NAME);
        }
        AiRunState revenueRun =
                AiPlannerExecutorRevenueAdapterGroupHydratedGraphCase.buildHydratedRunState(revenueRq);
        PlannerRevenueExecutionContext revenueExec =
                PlannerRevenueExecutionContext.builder()
                        .runState(revenueRun)
                        .resolvedQueryContext(revenueRq)
                        .resolvedQueryContextRef(
                                AiPlannerExecutorRevenueAdapterGroupHydratedGraphCase.HARNESS_RESOLVED_CONTEXT_REF)
                        .userId(1L)
                        .departmentId(null)
                        .distributerId(null)
                        .conversationId("0")
                        .runId(Long.toString(HARNESS_SYNTHETIC_RUN_ID))
                        .plannerReadRequest(revenueSlice)
                        .build();

        PurchasePlannerReadRequest purchaseSlice =
                AiPlannerExecutorPurchaseAdapterGroupHydratedGraphCase.buildFullHarnessPurchaseReadRequest();
        AiResolvedQueryContext purchaseRq =
                AiPlannerExecutorPurchaseAdapterGroupHydratedGraphCase.buildHydratedResolvedQueryContext();
        AiRunState purchaseRun =
                AiPlannerExecutorPurchaseAdapterGroupHydratedGraphCase.buildHydratedRunState(purchaseRq);
        PurchasePlannerExecutionContext purchaseExec =
                PurchasePlannerExecutionContext.builder()
                        .runState(purchaseRun)
                        .resolvedQueryContext(purchaseRq)
                        .resolvedQueryContextRef(
                                AiPlannerExecutorPurchaseAdapterGroupHydratedGraphCase.HARNESS_RESOLVED_CONTEXT_REF)
                        .userId(1L)
                        .departmentId(null)
                        .distributerId(
                                AiPlannerExecutorPurchaseAdapterGroupHydratedGraphCase.HARNESS_PURCHASE_DISTRIBUTER_ID)
                        .conversationId("0")
                        .runId(Long.toString(HARNESS_SYNTHETIC_RUN_ID))
                        .plannerReadRequest(purchaseSlice)
                        .build();

        StockReducePlannerReadRequest stockSlice =
                AiPlannerExecutorStockReduceAdapterGroupHydratedGraphCase.buildFullHarnessStockReduceReadRequest();
        AiResolvedQueryContext stockRq =
                AiPlannerExecutorStockReduceAdapterGroupHydratedGraphCase.buildHydratedResolvedQueryContext();
        AiRunState stockRun =
                AiPlannerExecutorStockReduceAdapterGroupHydratedGraphCase.buildHydratedRunState(stockRq);
        StockReducePlannerExecutionContext stockExec =
                StockReducePlannerExecutionContext.builder()
                        .runState(stockRun)
                        .resolvedQueryContext(stockRq)
                        .resolvedQueryContextRef(
                                AiPlannerExecutorStockReduceAdapterGroupHydratedGraphCase.HARNESS_RESOLVED_CONTEXT_REF)
                        .userId(1L)
                        .departmentId(null)
                        .distributerId(
                                AiPlannerExecutorStockReduceAdapterGroupHydratedGraphCase
                                        .HARNESS_STOCK_REDUCE_DISTRIBUTER_ID)
                        .conversationId("0")
                        .runId(Long.toString(HARNESS_SYNTHETIC_RUN_ID))
                        .plannerReadRequest(stockSlice)
                        .build();

        DishProfitPlannerReadRequest dishSlice =
                AiPlannerExecutorDishProfitAdapterGroupHydratedGraphCase.buildFullHarnessDishProfitReadRequest();
        AiResolvedQueryContext dishRq =
                AiPlannerExecutorDishProfitAdapterGroupHydratedGraphCase.buildHydratedResolvedQueryContext();
        AiRunState dishRun =
                AiPlannerExecutorDishProfitAdapterGroupHydratedGraphCase.buildHydratedRunState(dishRq);
        DishProfitPlannerExecutionContext dishExec =
                DishProfitPlannerExecutionContext.builder()
                        .runState(dishRun)
                        .resolvedQueryContext(dishRq)
                        .resolvedQueryContextRef(
                                AiPlannerExecutorDishProfitAdapterGroupHydratedGraphCase.HARNESS_RESOLVED_CONTEXT_REF)
                        .userId(1L)
                        .departmentId(null)
                        .distributerId(
                                AiPlannerExecutorDishProfitAdapterGroupHydratedGraphCase.HARNESS_DISH_PROFIT_DISTRIBUTER_ID)
                        .conversationId("0")
                        .runId(Long.toString(HARNESS_SYNTHETIC_RUN_ID))
                        .plannerReadRequest(dishSlice)
                        .build();

        List<PlannerStep> steps = new ArrayList<>();
        steps.add(
                PlannerStep.builder()
                        .stepId(CompositeBusinessDiagnosisRevenuePurchaseHybridPlannerStepExecutor
                                .COMPOSITE_STEP_ID_REVENUE_HYDRATED)
                        .stepName("revenue_hydrated_real_group")
                        .order(1)
                        .targetAgent(BusinessAgentNames.REVENUE_OVERVIEW)
                        .targetTool(RevenuePlannerAgentAdapter.TARGET_TOOL)
                        .inputSummary(
                                "GROUP（AAA、汀兰餐厅）：Hydrated RevenuePlannerRealReadBridge → revenue_query（C-48）")
                        .expectedOutput("DailyRevenueAnswerPlan / revenue_query 多店口径")
                        .acceptanceCriteria("真实 revenue_query；失败诚实 DEGRADED；不假单店 AAA 成功")
                        .mockExecutionStatus(null)
                        .answerPlanRef(
                                AiPlannerExecutorRevenueAdapterGroupHydratedGraphCase
                                        .HARNESS_ANSWER_PLAN_REF_AFTER_REVENUE)
                        .build());
        steps.add(
                PlannerStep.builder()
                        .stepId(CompositeBusinessDiagnosisRevenuePurchaseHybridPlannerStepExecutor
                                .COMPOSITE_STEP_ID_PURCHASE_HYDRATED)
                        .stepName("purchase_hydrated_real_group")
                        .order(2)
                        .targetAgent(PurchasePlannerAgentAdapter.TARGET_AGENT)
                        .targetTool(PurchasePlannerAgentAdapter.TARGET_TOOL)
                        .inputSummary(
                                "GROUP（groupPurchaseOverview）：Hydrated PurchasePlannerRealReadBridge → purchase_overview（C-48）")
                        .expectedOutput("PurchaseAnswerPlan / purchase_overview 多店口径")
                        .acceptanceCriteria("真实 purchase_overview；失败诚实 DEGRADED")
                        .mockExecutionStatus(null)
                        .answerPlanRef(
                                AiPlannerExecutorPurchaseAdapterGroupHydratedGraphCase
                                        .HARNESS_ANSWER_PLAN_REF_AFTER_PURCHASE)
                        .build());
        steps.add(
                PlannerStep.builder()
                        .stepId(CompositeBusinessDiagnosisRevenuePurchaseStockHybridPlannerStepExecutor
                                .COMPOSITE_STEP_ID_STOCK_REDUCE_HYDRATED)
                        .stepName("stock_reduce_hydrated_real_group")
                        .order(3)
                        .targetAgent(StockReducePlannerAgentAdapter.TARGET_AGENT)
                        .targetTool(StockReducePlannerAgentAdapter.TARGET_TOOL)
                        .inputSummary(
                                "GROUP（groupStockReduceQuery）：Hydrated StockReducePlannerRealReadBridge → stock_reduce_query（C-48）")
                        .expectedOutput("StockReduceAnswerPlan / stock_reduce_query 多店口径")
                        .acceptanceCriteria("真实 stock_reduce_query；失败诚实 DEGRADED")
                        .mockExecutionStatus(null)
                        .answerPlanRef(
                                AiPlannerExecutorStockReduceAdapterGroupHydratedGraphCase
                                        .HARNESS_ANSWER_PLAN_REF_AFTER_STOCK_REDUCE)
                        .build());
        steps.add(
                PlannerStep.builder()
                        .stepId(CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor
                                .COMPOSITE_STEP_ID_DISH_PROFIT_HYDRATED)
                        .stepName("dish_profit_hydrated_real_group")
                        .order(4)
                        .targetAgent(DishProfitPlannerAgentAdapter.TARGET_AGENT)
                        .targetTool(DishProfitPlannerAgentAdapter.TARGET_TOOL)
                        .inputSummary(
                                "GROUP（GROUP_MANAGER user + dishProfitPath）：Hydrated DishProfitPlannerRealReadBridge →"
                                        + " dish_profit_analysis（C-48）")
                        .expectedOutput("DishProfitAnswerPlan / dish_profit_analysis 多店口径")
                        .acceptanceCriteria("真实 dish_profit_analysis；失败诚实 DEGRADED；不假单店")
                        .mockExecutionStatus(null)
                        .answerPlanRef(
                                AiPlannerExecutorDishProfitAdapterGroupHydratedGraphCase
                                        .HARNESS_ANSWER_PLAN_REF_AFTER_DISH_PROFIT)
                        .build());
        steps.add(
                PlannerStep.builder()
                        .stepId("step_diagnosis_compose")
                        .stepName("diagnosis_compose_deterministic")
                        .order(5)
                        .targetAgent(AiPlannerExecutorMockGraphCase.MOCK_AGENT_DIAGNOSIS)
                        .targetTool(
                                CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor
                                        .COMPOSITE_MOCK_TOOL_DIAGNOSIS_COMPOSE)
                        .inputSummary(
                                "C-48 GROUP：四域 priorStepResults + 各域 AiRunState → BusinessDiagnosisCompositeAnswerPlan（确定性）；"
                                        + "usedTools=mock_diagnosis_compose")
                        .expectedOutput("BusinessDiagnosisCompositeAnswerPlan；dataCoverage 四域；GROUP summary 保守")
                        .acceptanceCriteria("确定性 compose；无 LLM")
                        .mockExecutionStatus(PlannerStepMockExecutionStatus.SUCCESS)
                        .answerPlanRef(AiPlannerExecutorBusinessDiagnosisCompositeGraphCase.DIAGNOSIS_ANSWER_PLAN_REF)
                        .build());
        steps.add(
                PlannerStep.builder()
                        .stepId("step_recommendation")
                        .stepName("recommendation_mock")
                        .order(6)
                        .targetAgent(AiPlannerExecutorMockGraphCase.MOCK_AGENT_RECOMMENDATION)
                        .targetTool(AiPlannerExecutorMockGraphCase.MOCK_TOOL_RECOMMENDATION)
                        .inputSummary("建议 mock（C-48 GROUP；无生产 Action）")
                        .expectedOutput("RecommendationPlan 占位")
                        .acceptanceCriteria("mock_build_recommendation_plan")
                        .mockExecutionStatus(PlannerStepMockExecutionStatus.SUCCESS)
                        .build());

        return PlannerExecutionPlan.builder()
                .planId(PLAN_ID)
                .planType(PLAN_TYPE)
                .steps(steps)
                .failureStrategy(PlannerFailureStrategy.CONTINUE_WITH_DEGRADED)
                .resolvedContextRef(
                        AiPlannerExecutorRevenueAdapterGroupHydratedGraphCase.HARNESS_RESOLVED_CONTEXT_REF)
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

    public static Map<String, Object> toHarnessSummary(
            PlannerExecutorResult result, String replayMessage, long runId, long conversationId) {
        LinkedHashMap<String, Object> root =
                new LinkedHashMap<>(
                        AiPlannerExecutorMockGraphCase.toHarnessSummary(
                                result, replayMessage, runId, conversationId, CASE_ID));
        root.put("harnessReplayMode", AiHarnessReplayMode.PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER.name());
        root.put("plannerCompositeHonesty", PLANNER_COMPOSITE_HONESTY_GROUP_ALL_DATA_REAL_DIAGNOSIS_DETERMINISTIC);
        root.put("plannerCompositeNote", PLANNER_COMPOSITE_NOTE_GROUP);
        root.put("visibleStoreRootDepartmentIds", List.of(1, 3));
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

    /**
     * @param executedPlan 与 {@link com.nongxinle.ai.planner.PlannerExecutor#execute} 传入实例相同，用于提取可见门店根 id。
     */
    public static Map<String, Object> toHarnessSummary(
            PlannerExecutorResult result,
            String replayMessage,
            long runId,
            long conversationId,
            PlannerExecutionPlan executedPlan) {
        Map<String, Object> root = toHarnessSummary(result, replayMessage, runId, conversationId);
        if (executedPlan != null && executedPlan.getRevenueExecutionContext() != null) {
            List<Integer> roots =
                    BusinessScopeResolutionSupport.extractVisibleStoreRootDepartmentIds(
                            executedPlan.getRevenueExecutionContext().getResolvedQueryContext());
            root.put("visibleStoreRootDepartmentIds", new ArrayList<>(roots));
        }
        return root;
    }
}
