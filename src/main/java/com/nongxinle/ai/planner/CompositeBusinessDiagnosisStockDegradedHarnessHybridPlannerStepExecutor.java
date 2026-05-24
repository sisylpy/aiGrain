package com.nongxinle.ai.planner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * C-42 **Harness-only partial hybrid**：与 {@link CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor} 同构，但
 * {@code step_stock_reduce_hydrated} <strong>不</strong>调用真实 {@code stock_reduce_query}，固定返回
 * {@link PlannerStepStatus#DEGRADED}，用于验证 Composite 降级诚实性。营收 / 采购 / 菜品仍走注入的
 * {@link PlannerStepExecutor}（真实 Bridge）；诊断 compose / 建议仍为 mock。
 * <p><strong>不是</strong>生产主链；<strong>仅</strong>用于 {@code PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_STOCK_DEGRADED_CORE}。
 * 生产 SHADOW / ALL_REAL 路径使用 {@link CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor}。</p>
 * <p><strong>Harness-only executor：仅用于 PlannerExecutor 降级回放/验证，不参与生产 SHADOW 全真路径，不得接入生产主回答链。</strong></p>
 */
public final class CompositeBusinessDiagnosisStockDegradedHarnessHybridPlannerStepExecutor implements PlannerStepExecutor {

    /** 可读降级原因（Replay / dataCoverage.degradedReason）。 */
    public static final String HARNESS_STOCK_DEGRADED_REASON =
            "harness_intentional_stock_reduce_degraded; stock_reduce_query not invoked; "
                    + "revenue/purchase/dish_profit remain real hydrated; diagnosis deterministic; recommendation mock (C-42)";

    private final PlannerStepExecutor adapterExecutor;

    public CompositeBusinessDiagnosisStockDegradedHarnessHybridPlannerStepExecutor(PlannerStepExecutor adapterExecutor) {
        this.adapterExecutor = Objects.requireNonNull(adapterExecutor, "adapterExecutor");
    }

    @Override
    public PlannerStepExecutionResponse execute(PlannerStepExecutionRequest request) {
        Objects.requireNonNull(request, "request");
        PlannerStep step = request.getStep();
        if (isCompositeStockReduceRealHydratedStep(step)) {
            return stockDegradedHarnessResponse(step);
        }
        return new CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor(adapterExecutor).execute(request);
    }

    private static PlannerStepExecutionResponse stockDegradedHarnessResponse(PlannerStep step) {
        List<String> agents = new ArrayList<>();
        if (step != null && step.getTargetAgent() != null && !step.getTargetAgent().isEmpty()) {
            agents.add(step.getTargetAgent());
        }
        return PlannerStepExecutionResponse.builder()
                .status(PlannerStepStatus.DEGRADED)
                .errorMessage(null)
                .degradedReason(HARNESS_STOCK_DEGRADED_REASON)
                .usedAgents(agents)
                .usedTools(List.of())
                .businessDiagnosisCompositeAnswerPlan(null)
                .build();
    }

    private static boolean isCompositeStockReduceRealHydratedStep(PlannerStep step) {
        if (step == null || step.getStepId() == null) {
            return false;
        }
        if (!CompositeBusinessDiagnosisStepIds.COMPOSITE_STEP_ID_STOCK_REDUCE_HYDRATED
                .equals(step.getStepId().trim())) {
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
