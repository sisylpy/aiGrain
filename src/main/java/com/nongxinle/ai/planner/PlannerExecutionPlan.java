package com.nongxinle.ai.planner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次 Run 的 Planner 执行计划（模板产物；C-2 仅为 DTO）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerExecutionPlan {

    private String planId;
    private String planType;
    @Builder.Default
    private List<PlannerStep> steps = new ArrayList<>();
    @Builder.Default
    private PlannerFailureStrategy failureStrategy = PlannerFailureStrategy.CONTINUE_WITH_DEGRADED;
    /** 预留：与 ResolvedQueryContext 对齐的摘要或 hash，供 Replay */
    private String resolvedContextRef;
    /**
     * 可选：与本计划对齐的营收只读切片（由 Graph / 编排注入）；供 {@link RevenuePlannerAgentAdapter}，无用户原文字段。
     */
    private RevenuePlannerReadRequest revenueReadRequest;

    /**
     * 可选：营收真实读运行时上下文（C-12）；可持有 {@link #revenueReadRequest}，但 {@link RevenuePlannerReadRequest}
     * <strong>不得</strong>反向引用本对象。
     */
    private PlannerRevenueExecutionContext revenueExecutionContext;

    /**
     * 可选：与本计划对齐的采购只读切片；供 {@link PurchasePlannerAgentAdapter}，无用户原文字段（C-16）。
     */
    private PurchasePlannerReadRequest purchaseReadRequest;

    /**
     * 可选：采购真实读运行时上下文骨架（C-16）；当前不接 {@link com.nongxinle.ai.graph.business.PurchaseOverviewToolExecutor}。
     */
    private PurchasePlannerExecutionContext purchaseExecutionContext;

    /**
     * 可选：与本计划对齐的出库/C核销只读切片；供 {@link StockReducePlannerAgentAdapter}，无用户原文字段（C-21）。
     */
    private StockReducePlannerReadRequest stockReduceReadRequest;

    /**
     * 可选：出库/核销真实读运行时上下文骨架（C-21）；不接 {@link com.nongxinle.ai.graph.business.StockReduceQueryToolExecutor}。
     */
    private StockReducePlannerExecutionContext stockReduceExecutionContext;

    /**
     * 可选：与本计划对齐的菜品毛利只读切片；供 {@link DishProfitPlannerAgentAdapter}，无用户原文字段（C-26）。
     */
    private DishProfitPlannerReadRequest dishProfitReadRequest;

    /**
     * 可选：菜品毛利真实读运行时上下文骨架（C-26）；不接 {@link com.nongxinle.ai.graph.business.DishProfitQueryToolExecutor}。
     */
    private DishProfitPlannerExecutionContext dishProfitExecutionContext;

    /**
     * 下游 AnswerPlan / Composer 类型占位；Executor 会抄入 {@link PlannerExecutorTrace#getFinalAnswerPlanType()}。
     */
    private String finalAnswerPlanType;
}
