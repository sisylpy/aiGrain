package com.nongxinle.ai.planner;

import java.util.Objects;

/**
 * C-32：**经营诊断 Composite** 单步混合执行 — {@code step_revenue_hydrated} 且
 * {@link RevenuePlannerAgentAdapter#TARGET_TOOL} 走真实 {@link RevenuePlannerAgentAdapter}；其余步走
 * {@link MockPlannerStepExecutor}（mock_* / 建议占位）。
 * <p>
 * <strong>不</strong>解析用户原文；匹配键仅 {@link PlannerStep#getStepId()} 与 {@link PlannerStep#getTargetTool()}。
 * </p>
 */
public final class CompositeBusinessDiagnosisRevenueHybridPlannerStepExecutor implements PlannerStepExecutor {

    /** 与 Composite 计划对齐；仅当 {@code targetTool = revenue_query} 时走真实营收 Adapter。 */
    public static final String COMPOSITE_STEP_ID_REVENUE_HYDRATED = "step_revenue_hydrated";

    private final PlannerStepExecutor revenueAdapterExecutor;

    public CompositeBusinessDiagnosisRevenueHybridPlannerStepExecutor(PlannerStepExecutor revenueAdapterExecutor) {
        this.revenueAdapterExecutor =
                Objects.requireNonNull(revenueAdapterExecutor, "revenueAdapterExecutor");
    }

    @Override
    public PlannerStepExecutionResponse execute(PlannerStepExecutionRequest request) {
        Objects.requireNonNull(request, "request");
        PlannerStep step = request.getStep();
        if (isCompositeRevenueRealHydratedStep(step)) {
            return Objects.requireNonNull(
                    revenueAdapterExecutor.execute(request), "revenueAdapterExecutor returned null");
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

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
