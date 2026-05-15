package com.nongxinle.ai.planner;

import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeAnswerPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * C-35：经营诊断 Composite — 四数据步真实 Registry（{@code revenue_query}、{@code purchase_overview}、
 * {@code stock_reduce_query}、{@code dish_profit_analysis}）；{@code step_diagnosis_compose} /
 * {@code step_recommendation} 仍 {@link MockPlannerStepExecutor}。
 * <p>
 * <strong>不</strong>解析用户原文；匹配键仅 {@link PlannerStep#getStepId()} 与 {@link PlannerStep#getTargetTool()}。
 * </p>
 */
public final class CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor implements PlannerStepExecutor {

    /** 与 Composite 计划及 C-30 设计 {@code stepId} 对齐。 */
    public static final String COMPOSITE_STEP_ID_DISH_PROFIT_HYDRATED = "step_dish_profit_hydrated";

    /** 与 Harness GraphCase {@code MOCK_TOOL_DIAGNOSIS_COMPOSE} 对齐； planner 层不依赖 harness 包。 */
    public static final String COMPOSITE_MOCK_TOOL_DIAGNOSIS_COMPOSE = "mock_diagnosis_compose";

    private static final String COMPOSITE_STEP_ID_DIAGNOSIS_COMPOSE = "step_diagnosis_compose";

    private final PlannerStepExecutor adapterExecutor;

    public CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor(PlannerStepExecutor adapterExecutor) {
        this.adapterExecutor = Objects.requireNonNull(adapterExecutor, "adapterExecutor");
    }

    @Override
    public PlannerStepExecutionResponse execute(PlannerStepExecutionRequest request) {
        Objects.requireNonNull(request, "request");
        PlannerStep step = request.getStep();
        if (isCompositeRevenueRealHydratedStep(step)
                || isCompositePurchaseRealHydratedStep(step)
                || isCompositeStockReduceRealHydratedStep(step)
                || isCompositeDishProfitRealHydratedStep(step)) {
            return Objects.requireNonNull(adapterExecutor.execute(request), "adapterExecutor returned null");
        }
        if (isCompositeDiagnosisComposeMockStep(step)) {
            return diagnosisComposeSkeletonResponse(step, request);
        }
        return MockPlannerStepExecutor.INSTANCE.execute(request);
    }

    /**
     * C-37：确定性 Composite AnswerPlan 骨架；{@link PlannerStep#getMockExecutionStatus()} 等仍控制是否 SUCCESS；
     * {@code usedTools} 保持 mock 诚实（{@link #COMPOSITE_MOCK_TOOL_DIAGNOSIS_COMPOSE}）。
     */
    private static PlannerStepExecutionResponse diagnosisComposeSkeletonResponse(
            PlannerStep step, PlannerStepExecutionRequest request) {
        PlannerStepMockExecutionStatus mock =
                step.getMockExecutionStatus() != null
                        ? step.getMockExecutionStatus()
                        : PlannerStepMockExecutionStatus.SUCCESS;
        if (mock != PlannerStepMockExecutionStatus.SUCCESS) {
            return MockPlannerStepExecutor.INSTANCE.execute(request);
        }
        BusinessDiagnosisCompositeAnswerPlan plan = BusinessDiagnosisCompositeAnswerPlanBuilder.build(request);
        List<String> agents = new ArrayList<>();
        if (step.getTargetAgent() != null && !step.getTargetAgent().isEmpty()) {
            agents.add(step.getTargetAgent());
        }
        return PlannerStepExecutionResponse.builder()
                .status(PlannerStepStatus.SUCCESS)
                .errorMessage(null)
                .degradedReason(null)
                .usedAgents(agents)
                .usedTools(List.of(COMPOSITE_MOCK_TOOL_DIAGNOSIS_COMPOSE))
                .businessDiagnosisCompositeAnswerPlan(plan)
                .build();
    }

    private static boolean isCompositeDiagnosisComposeMockStep(PlannerStep step) {
        if (step == null || step.getStepId() == null) {
            return false;
        }
        if (!COMPOSITE_STEP_ID_DIAGNOSIS_COMPOSE.equals(step.getStepId().trim())) {
            return false;
        }
        String tool = trimToNull(step.getTargetTool());
        return COMPOSITE_MOCK_TOOL_DIAGNOSIS_COMPOSE.equals(tool);
    }

    private static boolean isCompositeRevenueRealHydratedStep(PlannerStep step) {
        if (step == null || step.getStepId() == null) {
            return false;
        }
        if (!CompositeBusinessDiagnosisRevenuePurchaseHybridPlannerStepExecutor.COMPOSITE_STEP_ID_REVENUE_HYDRATED.equals(
                step.getStepId().trim())) {
            return false;
        }
        String tool = trimToNull(step.getTargetTool());
        return RevenuePlannerAgentAdapter.TARGET_TOOL.equals(tool);
    }

    private static boolean isCompositePurchaseRealHydratedStep(PlannerStep step) {
        if (step == null || step.getStepId() == null) {
            return false;
        }
        if (!CompositeBusinessDiagnosisRevenuePurchaseHybridPlannerStepExecutor.COMPOSITE_STEP_ID_PURCHASE_HYDRATED
                .equals(step.getStepId().trim())) {
            return false;
        }
        String tool = trimToNull(step.getTargetTool());
        return PurchasePlannerAgentAdapter.TARGET_TOOL.equals(tool);
    }

    private static boolean isCompositeStockReduceRealHydratedStep(PlannerStep step) {
        if (step == null || step.getStepId() == null) {
            return false;
        }
        if (!CompositeBusinessDiagnosisRevenuePurchaseStockHybridPlannerStepExecutor.COMPOSITE_STEP_ID_STOCK_REDUCE_HYDRATED
                .equals(step.getStepId().trim())) {
            return false;
        }
        String tool = trimToNull(step.getTargetTool());
        return StockReducePlannerAgentAdapter.TARGET_TOOL.equals(tool);
    }

    private static boolean isCompositeDishProfitRealHydratedStep(PlannerStep step) {
        if (step == null || step.getStepId() == null) {
            return false;
        }
        if (!COMPOSITE_STEP_ID_DISH_PROFIT_HYDRATED.equals(step.getStepId().trim())) {
            return false;
        }
        String tool = trimToNull(step.getTargetTool());
        return DishProfitPlannerAgentAdapter.TARGET_TOOL.equals(tool);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
