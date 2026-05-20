package com.nongxinle.ai.planner;

/**
 * Composite 六步计划中<strong>数据域 Hydrated 步</strong>的 {@code stepId} 契约（C-30 设计对齐）。
 * <p>供 {@link CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor}、
 * {@link BusinessDiagnosisCompositePlanFactory}、{@link BusinessDiagnosisCompositeAnswerPlanBuilder}、
 * Harness GraphCase（ALL_REAL / GROUP / STOCK_DEGRADED）共用；<strong>不</strong>依赖已退役的 C-32～C-34 分步 Hybrid 执行器类。</p>
 */
public final class CompositeBusinessDiagnosisStepIds {

    /** {@code targetTool = revenue_query} 时走营收 Adapter。 */
    public static final String COMPOSITE_STEP_ID_REVENUE_HYDRATED = "step_revenue_hydrated";

    /** {@code targetTool = purchase_overview} 时走采购 Adapter。 */
    public static final String COMPOSITE_STEP_ID_PURCHASE_HYDRATED = "step_purchase_hydrated";

    /** {@code targetTool = stock_reduce_query} 时走出库 Adapter。 */
    public static final String COMPOSITE_STEP_ID_STOCK_REDUCE_HYDRATED = "step_stock_reduce_hydrated";

    private CompositeBusinessDiagnosisStepIds() {
    }
}
