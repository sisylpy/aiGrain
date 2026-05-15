package com.nongxinle.ai.planner;

import java.util.Objects;

/**
 * C-33：经营诊断 Composite — {@code step_revenue_hydrated} + {@code revenue_query} 与
 * {@code step_purchase_hydrated} + {@code purchase_overview} 走真实 Registry；其余步
 * {@link MockPlannerStepExecutor}。
 * <p>
 * <strong>不</strong>解析用户原文；匹配键仅 {@link PlannerStep#getStepId()} 与 {@link PlannerStep#getTargetTool()}。
 * </p>
 */
public final class CompositeBusinessDiagnosisRevenuePurchaseHybridPlannerStepExecutor
        implements PlannerStepExecutor {

    /** 与 Composite 计划对齐；{@code targetTool = revenue_query} 时走营收 Adapter。 */
    public static final String COMPOSITE_STEP_ID_REVENUE_HYDRATED = "step_revenue_hydrated";

    /** 与 Composite 计划对齐；{@code targetTool = purchase_overview} 时走采购 Adapter。 */
    public static final String COMPOSITE_STEP_ID_PURCHASE_HYDRATED = "step_purchase_hydrated";

    private final PlannerStepExecutor adapterExecutor;

    public CompositeBusinessDiagnosisRevenuePurchaseHybridPlannerStepExecutor(
            PlannerStepExecutor adapterExecutor) {
        this.adapterExecutor = Objects.requireNonNull(adapterExecutor, "adapterExecutor");
    }

    @Override
    public PlannerStepExecutionResponse execute(PlannerStepExecutionRequest request) {
        Objects.requireNonNull(request, "request");
        PlannerStep step = request.getStep();
        if (isCompositeRevenueRealHydratedStep(step) || isCompositePurchaseRealHydratedStep(step)) {
            return Objects.requireNonNull(adapterExecutor.execute(request), "adapterExecutor returned null");
        }
        return MockPlannerStepExecutor.INSTANCE.execute(request);
    }

    private static boolean isCompositeRevenueRealHydratedStep(PlannerStep step) {
        if (step == null || step.getStepId() == null) {
            return false;
        }
        if (!COMPOSITE_STEP_ID_REVENUE_HYDRATED.equals(step.getStepId().trim())) {
            return false;
        }
        String tool = trimToNull(step.getTargetTool());
        return RevenuePlannerAgentAdapter.TARGET_TOOL.equals(tool);
    }

    private static boolean isCompositePurchaseRealHydratedStep(PlannerStep step) {
        if (step == null || step.getStepId() == null) {
            return false;
        }
        if (!COMPOSITE_STEP_ID_PURCHASE_HYDRATED.equals(step.getStepId().trim())) {
            return false;
        }
        String tool = trimToNull(step.getTargetTool());
        return PurchasePlannerAgentAdapter.TARGET_TOOL.equals(tool);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
