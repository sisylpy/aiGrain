package com.nongxinle.ai.planner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@link PlannerAgentAdapter} 入参边界（C-6）。
 * <p>
 * <strong>禁止</strong>将用户聊天原文作为主要输入；真实实现必须依赖 {@link #resolvedQueryContextRef} 及结构化
 * {@link #answerPlanRef}、{@link #revenueReadRequest}、{@link #purchaseReadRequest}、{@link #stockReduceReadRequest}、
 * {@link #dishProfitReadRequest}
 * （由解析/编排注入）。本包不依赖 Resolver 具体类型。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerAgentAdapterRequest {

    private PlannerStep step;
    private PlannerFailureStrategy effectiveFailureStrategy;
    private String planId;
    private String planType;

    /**
     * 与 {@code AiResolvedQueryContext} 或其快照 id 对齐；adapter 路由与权限应基于此，而非用户原文。
     */
    private String resolvedQueryContextRef;

    /**
     * 可选：当前或前序 {@code AnswerPlan} / 子计划引用，供 adapter 只读拼装。
     */
    private String answerPlanRef;

    /** 可选：营收只读结构化切片（与 {@link PlannerStepExecutionRequest#getRevenueReadRequest()} 同源）。 */
    private RevenuePlannerReadRequest revenueReadRequest;

    /** 可选：营收真实读运行时上下文（与 {@link PlannerStepExecutionRequest#getRevenueExecutionContext()} 同源）。 */
    private PlannerRevenueExecutionContext revenueExecutionContext;

    /** 可选：采购只读切片（与 {@link PlannerStepExecutionRequest#getPurchaseReadRequest()} 同源，C-16）。 */
    private PurchasePlannerReadRequest purchaseReadRequest;

    /** 可选：采购执行上下文（与 {@link PlannerStepExecutionRequest#getPurchaseExecutionContext()} 同源，C-16）。 */
    private PurchasePlannerExecutionContext purchaseExecutionContext;

    /** 可选：出库/核销只读切片（与 {@link PlannerStepExecutionRequest#getStockReduceReadRequest()} 同源，C-21）。 */
    private StockReducePlannerReadRequest stockReduceReadRequest;

    /** 可选：出库/核销执行上下文（与 {@link PlannerStepExecutionRequest#getStockReduceExecutionContext()} 同源，C-21）。 */
    private StockReducePlannerExecutionContext stockReduceExecutionContext;

    /** 可选：菜品毛利只读切片（与 {@link PlannerStepExecutionRequest#getDishProfitReadRequest()} 同源，C-26）。 */
    private DishProfitPlannerReadRequest dishProfitReadRequest;

    /** 可选：菜品毛利执行上下文（与 {@link PlannerStepExecutionRequest#getDishProfitExecutionContext()} 同源，C-26）。 */
    private DishProfitPlannerExecutionContext dishProfitExecutionContext;

    public static PlannerAgentAdapterRequest fromPlannerStepExecution(PlannerStepExecutionRequest r) {
        if (r == null) {
            return PlannerAgentAdapterRequest.builder().build();
        }
        return PlannerAgentAdapterRequest.builder()
                .step(r.getStep())
                .effectiveFailureStrategy(r.getEffectiveFailureStrategy())
                .planId(r.getPlanId())
                .planType(r.getPlanType())
                .resolvedQueryContextRef(r.getResolvedQueryContextRef())
                .answerPlanRef(r.getAnswerPlanRef())
                .revenueReadRequest(r.getRevenueReadRequest())
                .revenueExecutionContext(r.getRevenueExecutionContext())
                .purchaseReadRequest(r.getPurchaseReadRequest())
                .purchaseExecutionContext(r.getPurchaseExecutionContext())
                .stockReduceReadRequest(r.getStockReduceReadRequest())
                .stockReduceExecutionContext(r.getStockReduceExecutionContext())
                .dishProfitReadRequest(r.getDishProfitReadRequest())
                .dishProfitExecutionContext(r.getDishProfitExecutionContext())
                .build();
    }
}
