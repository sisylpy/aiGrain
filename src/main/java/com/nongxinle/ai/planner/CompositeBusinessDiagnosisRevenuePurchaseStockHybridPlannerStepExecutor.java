package com.nongxinle.ai.planner;

import java.util.Objects;

/**
 * C-34：经营诊断 Composite — {@code step_revenue_hydrated} + {@code revenue_query}、
 * {@code step_purchase_hydrated} + {@code purchase_overview}、{@code step_stock_reduce_hydrated} +
 * {@code stock_reduce_query} 走真实 Registry；其余步 {@link MockPlannerStepExecutor}。
 * <p>
 * <strong>不</strong>解析用户原文；匹配键仅 {@link PlannerStep#getStepId()} 与 {@link PlannerStep#getTargetTool()}。
 * </p>
 */
public final class CompositeBusinessDiagnosisRevenuePurchaseStockHybridPlannerStepExecutor
        implements PlannerStepExecutor {

    /** 与 Composite 计划对齐；{@code targetTool = stock_reduce_query} 时走出库 Adapter。 */
    public static final String COMPOSITE_STEP_ID_STOCK_REDUCE_HYDRATED = "step_stock_reduce_hydrated";

    private final PlannerStepExecutor adapterExecutor;

    public CompositeBusinessDiagnosisRevenuePurchaseStockHybridPlannerStepExecutor(
            PlannerStepExecutor adapterExecutor) {
        this.adapterExecutor = Objects.requireNonNull(adapterExecutor, "adapterExecutor");
    }

    @Override
    public PlannerStepExecutionResponse execute(PlannerStepExecutionRequest request) {
        Objects.requireNonNull(request, "request");
        PlannerStep step = request.getStep();
        if (isCompositeRevenueRealHydratedStep(step)
                || isCompositePurchaseRealHydratedStep(step)
                || isCompositeStockReduceRealHydratedStep(step)) {
            return Objects.requireNonNull(adapterExecutor.execute(request), "adapterExecutor returned null");
        }
        return MockPlannerStepExecutor.INSTANCE.execute(request);
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
        if (!COMPOSITE_STEP_ID_STOCK_REDUCE_HYDRATED.equals(step.getStepId().trim())) {
            return false;
        }
        String tool = trimToNull(step.getTargetTool());
        return StockReducePlannerAgentAdapter.TARGET_TOOL.equals(tool);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
